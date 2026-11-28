package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.RepositoryManager
import com.example.repository.GiftRepository
import com.example.repository.LiveGiftModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ContentGiftBurstEvent(
    val id: Long = System.currentTimeMillis(),
    val emoji: String,
    val giftName: String,
    val coins: Int,
    val senderName: String,
    val creatorName: String
)

/**
 * Smooth 60fps Animated Equalizer Bars for Profile Music & Stories.
 * Uses only graphicsLayer scaleY transforms for zero layout thrashing.
 */
@Composable
fun AnimatedMusicEqualizer(
    isPlaying: Boolean,
    barColor: Color = Color(0xFF0095F6),
    modifier: Modifier = Modifier,
    barWidth: Dp = 3.dp,
    maxHeight: Dp = 14.dp
) {
    val levels = if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "eq_bars")
        val bar1 by infiniteTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(360, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar1"
        )
        val bar2 by infiniteTransition.animateFloat(
            initialValue = 0.9f,
            targetValue = 0.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(440, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar2"
        )
        val bar3 by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(310, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar3"
        )
        val bar4 by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(490, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar4"
        )
        listOf(bar1, bar2, bar3, bar4)
    } else {
        listOf(0.35f, 0.65f, 0.45f, 0.3f)
    }

    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        levels.forEach { level ->
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleY = level.coerceIn(0.2f, 1f)
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    }
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}

/**
 * Full-Screen / Card Animated Gift Burst Overlay when a user sends a Super Gift / Tip
 * on a Reel, Post, or Story. Runs at 60fps using transform & alpha only.
 */
@Composable
fun AnimatedGiftBurstOverlay(
    event: ContentGiftBurstEvent?,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(event?.id) {
        if (event != null) {
            delay(2400L)
            onFinished()
        }
    }

    AnimatedVisibility(
        visible = event != null,
        enter = fadeIn(tween(200)) + scaleIn(
            initialScale = 0.65f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ),
        exit = fadeOut(tween(350)) + scaleOut(targetScale = 1.15f),
        modifier = modifier
    ) {
        event?.let { ev ->
            val progress = remember(ev.id) { Animatable(0f) }
            LaunchedEffect(ev.id) {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(2100, easing = FastOutSlowInEasing))
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                // Floating radial emoji particles
                val offsets = remember {
                    listOf(
                        Pair(-90f, -120f), Pair(95f, -110f), Pair(-125f, -30f),
                        Pair(125f, -25f), Pair(-70f, 80f), Pair(85f, 85f),
                        Pair(0f, -155f), Pair(-45f, -95f), Pair(50f, -90f)
                    )
                }
                offsets.forEachIndexed { idx, (dx, dy) ->
                    val p = progress.value
                    Text(
                        text = if (idx % 3 == 0) "✨" else ev.emoji,
                        fontSize = if (idx % 2 == 0) 24.sp else 18.sp,
                        modifier = Modifier.graphicsLayer {
                            translationX = dx * p * 1.4f
                            translationY = dy * p * 1.4f - (p * 40f)
                            scaleX = (1.1f - p * 0.45f).coerceAtLeast(0.4f)
                            scaleY = (1.1f - p * 0.45f).coerceAtLeast(0.4f)
                            alpha = (1f - p).coerceIn(0f, 1f)
                        }
                    )
                }

                // Center Super-Gift Celebration Pill
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF14141F).copy(alpha = 0.92f),
                    border = BorderStroke(
                        1.5.dp,
                        Brush.horizontalGradient(listOf(Color(0xFFFFD700), Color(0xFFFF2A6D), Color(0xFF00E5FF)))
                    ),
                    shadowElevation = 12.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = ev.emoji,
                            fontSize = 52.sp,
                            modifier = Modifier.graphicsLayer {
                                val bounce = 1f + kotlin.math.sin(progress.value * Math.PI * 3).toFloat() * 0.14f
                                scaleX = bounce
                                scaleY = bounce
                            }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${ev.giftName} Sent!",
                            color = Color(0xFFFFD700),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "@${ev.creatorName} earned +₹${ev.coins} (${ev.coins} Coins)",
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reusable Animated Creator Super-Gift & Tip Bottom Sheet
 * Works across Reels, Posts, and Stories. Credits real creator earnings in Firestore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorGiftBottomSheet(
    creatorId: String,
    creatorUsername: String,
    contentId: String,
    contentType: String, // "REEL", "POST", "STORY"
    onDismiss: () -> Unit,
    onGiftSent: (ContentGiftBurstEvent) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val giftRepo = remember { GiftRepository() }
    val currentUser by RepositoryManager.currentUserState.collectAsStateWithLifecycle()
    val currentUid = RepositoryManager.getCurrentUserId()

    var selectedGift by remember { mutableStateOf(GiftRepository.DEFAULT_GIFTS.first()) }
    var isSending by remember { mutableStateOf(false) }
    var isClaimingDaily by remember { mutableStateOf(false) }
    var claimedDailyToday by remember { mutableStateOf(false) }
    var directCoins by remember(currentUid) { mutableStateOf<Long?>(null) }
    var directWalletCash by remember(currentUid) { mutableStateOf<Double?>(null) }

    // Direct real-time Firestore observer so exact coin balance ALWAYS matches Wallet & Profile
    DisposableEffect(currentUid) {
        if (currentUid.isEmpty()) {
            onDispose {}
        } else {
            val reg = RepositoryManager.firestore.collection("users").document(currentUid)
                .addSnapshotListener { snap, err ->
                    if (err == null && snap != null && snap.exists()) {
                        directCoins = snap.getLong("coins")
                            ?: snap.getDouble("coins")?.toLong()
                            ?: snap.getLong("coinBalance")
                            ?: 0L
                        directWalletCash = snap.getDouble("walletBalance")
                            ?: snap.getDouble("earnings")
                            ?: 0.0
                    }
                }
            onDispose { reg.remove() }
        }
    }

    val liveCoinsOnly = directCoins ?: (currentUser?.coins ?: 0L)
    val availableCoins = liveCoinsOnly + ((directWalletCash ?: currentUser?.walletBalance ?: 0.0).toLong().coerceAtLeast(0L))
    val todayKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    LaunchedEffect(currentUid, todayKey) {
        if (currentUid.isNotEmpty()) {
            try {
                val doc = RepositoryManager.firestore
                    .collection("users").document(currentUid)
                    .collection("daily_missions").document("daily_gift_bonus_$todayKey")
                    .get().await()
                claimedDailyToday = doc.exists()
            } catch (_: Exception) {}
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF13131C),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header with Creator Info + Live Coin Balance (shown ONLY when user actually has coins)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Send Super Gift 🎁",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "Support @$creatorUsername • Direct Creator Earning",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (liveCoinsOnly > 0L) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFFFD700).copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.55f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MonetizationOn,
                                contentDescription = "Coins",
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${com.example.utils.NumberFormatUtils.formatCount(liveCoinsOnly.toInt())} Coins",
                                color = Color(0xFFFFD700),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            // Free Daily Bonus Strip (so users with 0 balance can earn coins & tip immediately)
            if (!claimedDailyToday && currentUid.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF00E676).copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("claim_daily_gift_coins_button")
                        .clickable(enabled = !isClaimingDaily) {
                            isClaimingDaily = true
                            scope.launch {
                                try {
                                    val bonusCoins = 5L
                                    val userRef = RepositoryManager.firestore.collection("users").document(currentUid)
                                    val missionRef = userRef.collection("daily_missions").document("daily_gift_bonus_$todayKey")
                                    RepositoryManager.firestore.runTransaction { tx ->
                                        if (tx.get(missionRef).exists()) return@runTransaction
                                        val userDoc = tx.get(userRef)
                                        val curCoins = userDoc.getLong("coins") ?: 0L
                                        tx.update(userRef, "coins", curCoins + bonusCoins)
                                        tx.set(
                                            missionRef,
                                            mapOf(
                                                "missionId" to "daily_gift_bonus",
                                                "date" to todayKey,
                                                "rewardCoins" to bonusCoins,
                                                "claimedAt" to System.currentTimeMillis()
                                            )
                                        )
                                    }.await()
                                    claimedDailyToday = true
                                    Toast.makeText(context, "+5 Daily Coins added!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Unable to claim right now", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isClaimingDaily = false
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text("🎉", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Claim +5 Free Daily Coins",
                                    color = Color(0xFF00E676),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    "Use coins to gift creators or save in your wallet",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        if (isClaimingDaily) {
                            CircularProgressIndicator(
                                color = Color(0xFF00E676),
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text(
                                "CLAIM",
                                color = Color(0xFF00E676),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2-Row Gift Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            ) {
                items(GiftRepository.DEFAULT_GIFTS.take(8), key = { it.id }) { gift ->
                    val isSelected = selectedGift.id == gift.id
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.05f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "gift_scale"
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) gift.composeColor.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 0.8.dp,
                            color = if (isSelected) gift.composeColor else Color.White.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clickable { selectedGift = gift }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = gift.emoji, fontSize = 26.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = gift.name,
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "🪙 ${gift.coins}",
                                color = Color(0xFFFFD700),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Send Super Gift Button
            Button(
                onClick = {
                    if (currentUid.isEmpty()) {
                        Toast.makeText(context, "Please sign in to send gifts", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (creatorId == currentUid) {
                        Toast.makeText(context, "You cannot gift your own content", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isSending = true
                    scope.launch {
                        val senderName = currentUser?.username?.ifBlank { RepositoryManager.currentUsername } ?: "Fan"
                        val senderAvatar = currentUser?.avatarUrl?.ifBlank { RepositoryManager.currentUserAvatar } ?: ""
                        val res = giftRepo.sendContentGift(
                            senderId = currentUid,
                            senderUsername = senderName,
                            senderAvatar = senderAvatar,
                            creatorId = creatorId,
                            contentId = contentId,
                            contentType = contentType,
                            gift = selectedGift
                        )
                        isSending = false
                        res.onSuccess {
                            onGiftSent(
                                ContentGiftBurstEvent(
                                    emoji = selectedGift.emoji,
                                    giftName = selectedGift.name,
                                    coins = selectedGift.coins,
                                    senderName = senderName,
                                    creatorName = creatorUsername
                                )
                            )
                            onDismiss()
                        }.onFailure { err ->
                            Toast.makeText(
                                context,
                                err.message ?: "Not enough coins! Claim daily bonus or top up wallet.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                enabled = !isSending,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("send_super_gift_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFF2A6D), Color(0xFFFF8F00), Color(0xFFFFD700))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Text(
                            text = "Send ${selectedGift.emoji} ${selectedGift.name} (${selectedGift.coins} Coins • ₹${selectedGift.coins})",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
