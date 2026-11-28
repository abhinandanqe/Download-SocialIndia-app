package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.example.repository.ReferralConfig
import com.example.repository.ReferralLeaderboardEntry
import com.example.repository.ReferralRepository
import com.example.repository.UserReferralStats
import com.example.utils.SoundManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferralRewardsScreen(
    currentUserId: String,
    onBack: () -> Unit,
    onNavigateToWallet: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val config by ReferralRepository.listenReferralConfig().collectAsStateWithLifecycle(initialValue = ReferralConfig())
    val myClaims by ReferralRepository.listenUserReferralClaims(currentUserId).collectAsStateWithLifecycle(initialValue = emptyList())

    var stats by remember { mutableStateOf(UserReferralStats(userId = currentUserId)) }
    var leaderboard by remember { mutableStateOf<List<ReferralLeaderboardEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var redeemCodeInput by remember { mutableStateOf("") }
    var isRedeeming by remember { mutableStateOf(false) }
    var claimingMilestone by remember { mutableStateOf<Int?>(null) }

    fun refreshStats() {
        scope.launch {
            stats = ReferralRepository.getOrCreateUserReferralStats(currentUserId)
            leaderboard = ReferralRepository.fetchTopReferrers(8L)
            isLoading = false
        }
    }

    LaunchedEffect(currentUserId) {
        refreshStats()
    }

    val dateFormatter = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Refer & Earn Coins", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                        Text("Social India Rewards Program", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("referral_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = onNavigateToWallet,
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.padding(end = 10.dp).height(34.dp)
                    ) {
                        com.example.ui.components.CoinIcon(size = 15.dp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${stats.currentCoins}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFFFF2A5F))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero Gradient Banner
                item(key = "hero_card") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF1F1135),
                                        Color(0xFF4A154B),
                                        Color(0xFFD81B60)
                                    )
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (config.isEnabled) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFFFF5252).copy(alpha = 0.25f)
                                ) {
                                    Text(
                                        text = if (config.isEnabled) "PROGRAM LIVE" else "PAUSED BY ADMIN",
                                        color = if (config.isEnabled) Color(0xFF69F0AE) else Color(0xFFFF8A80),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.5.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Icon(
                                    Icons.Default.CardGiftcard,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD54F),
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = config.bannerTitle,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 21.sp,
                                lineHeight = 26.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = config.bannerSubtitle,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ReferralHeroMetric(
                                    label = "You Earn / Invite",
                                    value = "+${config.referrerRewardCoins} Coins",
                                    modifier = Modifier.weight(1f)
                                )
                                ReferralHeroMetric(
                                    label = "Friend Bonus",
                                    value = "+${config.refereeBonusCoins} Coins",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ReferralHeroMetric(
                                    label = "Friends Invited",
                                    value = "${stats.totalReferrals}",
                                    modifier = Modifier.weight(1f)
                                )
                                ReferralHeroMetric(
                                    label = "Referral Coins",
                                    value = "${stats.totalCoinsEarned}",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Your Unique Code & Share Card
                item(key = "my_code_card") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "Your Unique Invite Code",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                                    .border(1.dp, Color(0xFFFFD54F).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stats.referralCode.ifBlank { "GENERATING..." },
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp,
                                    letterSpacing = 1.8.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                FilledTonalIconButton(
                                    onClick = {
                                        SoundManager.playClickSound()
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("Social India Referral", stats.referralCode))
                                        Toast.makeText(context, "Referral code copied!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.testTag("copy_referral_code_button")
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Code")
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    SoundManager.playClickSound()
                                    val shareText = "Join me on Social India! Use my referral code ${stats.referralCode} in Refer & Earn to claim +${config.refereeBonusCoins} instant Social Coins! https://socialindia.app/invite/${stats.referralCode}"
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Invite Friends to Social India"))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("share_referral_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2A5F))
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Share Invite Link with Friends", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }

                // Redeem Friend's Code Card
                item(key = "redeem_card") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "Have a Friend's Invite Code?",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (stats.referredByCode.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF00C853).copy(alpha = 0.12f),
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00C853))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Redeemed code ${stats.referredByCode} — bonus credited!",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF00C853)
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "Enter a friend's code to get +${config.refereeBonusCoins} instant Social Coins.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = redeemCodeInput,
                                        onValueChange = { redeemCodeInput = it.uppercase(Locale.US).take(16) },
                                        placeholder = { Text("e.g. SI-ABHI1A2B") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).testTag("redeem_code_input"),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    Button(
                                        onClick = {
                                            if (redeemCodeInput.isBlank()) return@Button
                                            isRedeeming = true
                                            scope.launch {
                                                val res = ReferralRepository.redeemReferralCode(currentUserId, redeemCodeInput)
                                                isRedeeming = false
                                                if (res.isSuccess) {
                                                    Toast.makeText(context, res.getOrNull() ?: "Bonus claimed!", Toast.LENGTH_LONG).show()
                                                    redeemCodeInput = ""
                                                    refreshStats()
                                                } else {
                                                    Toast.makeText(context, res.exceptionOrNull()?.message ?: "Invalid code", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        enabled = !isRedeeming && config.isEnabled && redeemCodeInput.trim().length >= 4,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.height(54.dp).testTag("redeem_code_button")
                                    ) {
                                        if (isRedeeming) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                        } else {
                                            Text("Apply", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Milestone Tiers
                item(key = "milestones_header") {
                    Text(
                        text = "Creator Milestone Bonuses",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                items(ReferralRepository.defaultMilestones, key = { it.requiredInvites }) { milestone ->
                    val progress = (stats.totalReferrals.toFloat() / milestone.requiredInvites.toFloat()).coerceIn(0f, 1f)
                    val isClaimed = stats.claimedMilestones.contains(milestone.requiredInvites)
                    val isUnlocked = stats.totalReferrals >= milestone.requiredInvites

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(
                            1.dp,
                            if (isUnlocked && !isClaimed) Color(milestone.badgeColorHex) else MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(milestone.badgeColorHex).copy(alpha = 0.18f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.EmojiEvents,
                                            contentDescription = null,
                                            tint = Color(milestone.badgeColorHex),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(milestone.tierTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(
                                            text = "Invite ${milestone.requiredInvites} friends • +${milestone.bonusCoins} Bonus Coins",
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                when {
                                    isClaimed -> {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFF00C853).copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "Claimed",
                                                color = Color(0xFF00C853),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                    isUnlocked -> {
                                        Button(
                                            onClick = {
                                                claimingMilestone = milestone.requiredInvites
                                                scope.launch {
                                                    val res = ReferralRepository.claimMilestoneBonus(currentUserId, milestone)
                                                    claimingMilestone = null
                                                    if (res.isSuccess) {
                                                        Toast.makeText(context, "+${milestone.bonusCoins} Milestone Coins Claimed!", Toast.LENGTH_LONG).show()
                                                        refreshStats()
                                                    } else {
                                                        Toast.makeText(context, res.exceptionOrNull()?.message ?: "Claim failed", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            enabled = claimingMilestone != milestone.requiredInvites,
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
                                        ) {
                                            Text("Claim", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                        }
                                    }
                                    else -> {
                                        Text(
                                            text = "${stats.totalReferrals}/${milestone.requiredInvites}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                                color = Color(milestone.badgeColorHex),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }

                // Invited Friends History
                item(key = "invited_friends_header") {
                    Text(
                        text = "Your Invited Friends (${myClaims.size})",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                if (myClaims.isEmpty()) {
                    item(key = "empty_claims") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Outlined.GroupAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No friends invited yet", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    text = "Share your code above to start earning Social Coins!",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(myClaims, key = { it.id }) { claim ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (claim.refereeAvatarUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = claim.refereeAvatarUrl,
                                        contentDescription = claim.refereeUsername,
                                        modifier = Modifier.size(40.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFFF2A5F).copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(claim.refereeUsername.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Color(0xFFFF2A5F))
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("@${claim.refereeUsername}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(dateFormatter.format(Date(claim.timestamp)), fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    text = "+${claim.referrerCoins} Coins",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF00C853)
                                )
                            }
                        }
                    }
                }

                // Top Referrers Leaderboard
                if (leaderboard.isNotEmpty()) {
                    item(key = "leaderboard_header") {
                        Text(
                            text = "Top Promoters Leaderboard",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(leaderboard.withIndex().toList(), key = { it.value.userId }) { (idx, entry) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${idx + 1}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = when (idx) {
                                        0 -> Color(0xFFFFD700)
                                        1 -> Color(0xFFC0C0C0)
                                        2 -> Color(0xFFCD7F32)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.width(30.dp)
                                )
                                if (entry.avatarUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = entry.avatarUrl,
                                        contentDescription = entry.username,
                                        modifier = Modifier.size(36.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(entry.username.take(1).uppercase(), fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("@${entry.username}", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${entry.totalReferrals} invites", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    text = "${entry.totalCoinsEarned} Coins",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = Color(0xFFFFB300)
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(28.dp)) }
            }
        }
    }
}

@Composable
private fun ReferralHeroMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.13f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
    }
}
