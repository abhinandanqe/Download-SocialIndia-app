package com.example.ui

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LeaderboardEntry
import com.example.model.RepositoryManager
import com.example.repository.ProCreatorSuiteRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorLeaderboardScreen(
    onBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToDailyRewards: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserId = remember { RepositoryManager.getCurrentUserId() }

    var selectedCategory by remember { mutableStateOf("EARNERS") } // EARNERS, SUPPORTERS, TRENDING
    var selectedPeriod by remember { mutableStateOf("WEEKLY") } // WEEKLY, MONTHLY
    var entries by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isClaimingBonus by remember { mutableStateOf(false) }

    fun loadData(forceRefresh: Boolean = false) {
        scope.launch {
            isLoading = true
            entries = ProCreatorSuiteRepository.fetchLeaderboard(
                category = selectedCategory,
                period = selectedPeriod,
                forceRefresh = forceRefresh
            )
            isLoading = false
        }
    }

    LaunchedEffect(selectedCategory, selectedPeriod) {
        loadData(forceRefresh = false)
    }

    val myEntry = remember(entries, currentUserId) {
        entries.firstOrNull { it.userId == currentUserId }
    }

    Scaffold(
        containerColor = Color(0xFF090910),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Creator Leaderboard",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Weekly Crown Badges & Coin Rewards",
                            color = Color(0xFFFFD700),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("leaderboard_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { loadData(forceRefresh = true) },
                        modifier = Modifier.testTag("leaderboard_refresh_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF090910))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        ) {
            // Period & Category Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple("EARNERS", "Top Earners", Icons.Default.MonetizationOn),
                    Triple("SUPPORTERS", "Top Gifters", Icons.Default.CardGiftcard),
                    Triple("TRENDING", "Trending", Icons.Default.LocalFireDepartment)
                ).forEach { (key, label, icon) ->
                    val selected = selectedCategory == key
                    val bg by animateColorAsState(
                        targetValue = if (selected) Color(0xFFFFB300) else Color(0xFF161624),
                        label = "lb_cat_$key"
                    )
                    Surface(
                        onClick = { selectedCategory = key },
                        shape = RoundedCornerShape(14.dp),
                        color = bg,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("leaderboard_cat_$key")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        ) {
                            Icon(
                                icon,
                                contentDescription = label,
                                tint = if (selected) Color.Black else Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = label,
                                color = if (selected) Color.Black else Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("WEEKLY" to "This Week", "MONTHLY" to "This Month").forEach { (key, label) ->
                        val active = selectedPeriod == key
                        FilterChip(
                            selected = active,
                            onClick = { selectedPeriod = key },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF282040),
                                selectedLabelColor = Color(0xFFFFD700),
                                containerColor = Color(0xFF14141F),
                                labelColor = Color(0xFFB4B4C8)
                            ),
                            modifier = Modifier.testTag("leaderboard_period_$key")
                        )
                    }
                }

                TextButton(
                    onClick = onNavigateToDailyRewards,
                    modifier = Modifier.testTag("leaderboard_spin_shortcut")
                ) {
                    Text(
                        text = "🎁 Daily Spin",
                        color = Color(0xFF00F0FF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFFFD700))
                }
            } else if (entries.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = "No rankings",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Leaderboard is warming up!",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Post Reels, send gifts, or claim Daily Spin to climb into the Top 3 Crown Podium.",
                            color = Color(0xFF9A9AB2),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Top 3 Crown Podium
                    item(key = "podium") {
                        LeaderboardPodiumSection(
                            topThree = entries.take(3),
                            onUserClick = onNavigateToProfile
                        )
                    }

                    // Claim Weekly Bonus Banner
                    item(key = "claim_reward_card") {
                        val myRank = myEntry?.rank ?: (entries.size + 1)
                        val rewardCoins = when (myRank) {
                            1 -> 50
                            2 -> 30
                            3 -> 20
                            in 4..10 -> 10
                            else -> 5
                        }
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF281D07), Color(0xFF1F122B))
                                    ),
                                    RoundedCornerShape(20.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (myEntry != null) "Your Rank: #${myEntry.rank} 🏆" else "Active Creator Bonus 🪙",
                                        color = Color(0xFFFFD700),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Claim +$rewardCoins weekly bonus coins & badge boost",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 11.sp
                                    )
                                }
                                Button(
                                    onClick = {
                                        if (isClaimingBonus) return@Button
                                        isClaimingBonus = true
                                        scope.launch {
                                            val res = ProCreatorSuiteRepository.claimWeeklyLeaderboardBonus(currentUserId, myRank)
                                            isClaimingBonus = false
                                            res.onSuccess { bonus ->
                                                Toast.makeText(context, "🎉 Claimed +$bonus Weekly Coins!", Toast.LENGTH_LONG).show()
                                                loadData(forceRefresh = true)
                                            }.onFailure { err ->
                                                Toast.makeText(context, err.message ?: "Already claimed", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    enabled = !isClaimingBonus,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                                    modifier = Modifier.testTag("claim_weekly_bonus_btn")
                                ) {
                                    Text(
                                        text = if (isClaimingBonus) "..." else "Claim +$rewardCoins",
                                        color = Color.Black,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    // Rank 4+ List
                    val remaining = if (entries.size > 3) entries.drop(3) else emptyList()
                    itemsIndexed(remaining, key = { _, e -> e.userId }) { _, entry ->
                        val isMe = entry.userId == currentUserId
                        Surface(
                            onClick = { onNavigateToProfile(entry.userId) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isMe) Color(0xFF1F1A33) else Color(0xFF13131E),
                            border = BorderStroke(
                                1.dp,
                                if (isMe) Color(0xFFFFD700).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.06f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("leaderboard_row_${entry.rank}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "#${entry.rank}",
                                    color = if (entry.rank <= 10) Color(0xFFFFD700) else Color(0xFF8E8EA8),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.width(34.dp)
                                )
                                CosmeticAvatarFrameRing(
                                    frameId = entry.equippedFrameId,
                                    avatarUrl = entry.avatarUrl,
                                    size = 42.dp,
                                    contentDescription = entry.username
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "@${entry.username}",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (entry.isVerified) {
                                            VerificationBadge(
                                                isVerified = true,
                                                badgeUrl = entry.verificationBadgeUrl,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        if (entry.supporterBadge == "TOP_SUPPORTER") {
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = Color(0xFF00F0FF).copy(alpha = 0.16f)
                                            ) {
                                                Text(
                                                    text = "TOP 10",
                                                    color = Color(0xFF00F0FF),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = entry.metricLabel,
                                        color = Color(0xFFA0A0B8),
                                        fontSize = 12.sp
                                    )
                                }
                                Icon(
                                    Icons.Default.WorkspacePremium,
                                    contentDescription = "Rank Badge",
                                    tint = if (entry.rank <= 10) Color(0xFFFFD700) else Color(0xFF4B4B66),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardPodiumSection(
    topThree: List<LeaderboardEntry>,
    onUserClick: (String) -> Unit
) {
    val first = topThree.getOrNull(0)
    val second = topThree.getOrNull(1)
    val third = topThree.getOrNull(2)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF211738), Color(0xFF11111C))
                )
            )
            .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.25f), RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (second != null) {
                PodiumSlot(
                    entry = second,
                    medalEmoji = "🥈",
                    accentColor = Color(0xFFC0C0D0),
                    avatarSize = 54.dp,
                    pedestalHeight = 58.dp,
                    onClick = { onUserClick(second.userId) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (first != null) {
                PodiumSlot(
                    entry = first,
                    medalEmoji = "👑",
                    accentColor = Color(0xFFFFD700),
                    avatarSize = 68.dp,
                    pedestalHeight = 80.dp,
                    onClick = { onUserClick(first.userId) },
                    modifier = Modifier.weight(1.15f)
                )
            }
            if (third != null) {
                PodiumSlot(
                    entry = third,
                    medalEmoji = "🥉",
                    accentColor = Color(0xFFCD7F32),
                    avatarSize = 50.dp,
                    pedestalHeight = 46.dp,
                    onClick = { onUserClick(third.userId) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PodiumSlot(
    entry: LeaderboardEntry,
    medalEmoji: String,
    accentColor: Color,
    avatarSize: androidx.compose.ui.unit.Dp,
    pedestalHeight: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = medalEmoji, fontSize = if (entry.rank == 1) 24.sp else 18.sp)
        Spacer(modifier = Modifier.height(4.dp))
        CosmeticAvatarFrameRing(
            frameId = entry.equippedFrameId.ifBlank { if (entry.rank == 1) "frame_royal_gold" else "frame_none" },
            avatarUrl = entry.avatarUrl,
            size = avatarSize,
            contentDescription = entry.username
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = entry.username,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.isVerified) {
                Spacer(modifier = Modifier.width(3.dp))
                VerificationBadge(isVerified = true, badgeUrl = entry.verificationBadgeUrl, modifier = Modifier.size(12.dp))
            }
        }
        Text(
            text = entry.metricLabel,
            color = accentColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .height(pedestalHeight)
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(accentColor.copy(alpha = 0.35f), accentColor.copy(alpha = 0.08f))
                    )
                )
                .border(
                    1.dp,
                    accentColor.copy(alpha = 0.55f),
                    RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "#${entry.rank}",
                color = accentColor,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp
            )
        }
    }
}
