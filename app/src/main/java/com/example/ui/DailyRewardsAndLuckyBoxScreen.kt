package com.example.ui

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.DailyRewardsState
import com.example.model.LuckyCoinBox
import com.example.model.RepositoryManager
import com.example.repository.ProCreatorSuiteRepository
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyRewardsAndLuckyBoxScreen(
    onBack: () -> Unit,
    onNavigateToCosmetics: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserId = remember { RepositoryManager.getCurrentUserId() }
    val currentUser by RepositoryManager.currentUserState.collectAsStateWithLifecycle()

    val rewardsState by remember(currentUserId) {
        ProCreatorSuiteRepository.observeDailyRewardsState(currentUserId)
    }.collectAsStateWithLifecycle(initialValue = DailyRewardsState())

    val activeLuckyBoxes by remember {
        ProCreatorSuiteRepository.observeActiveLuckyBoxes()
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val wheelRotation = remember { Animatable(0f) }
    var isSpinning by remember { mutableStateOf(false) }
    var lastWonBanner by remember { mutableStateOf<String?>(null) }
    var isClaimingStreak by remember { mutableStateOf(false) }
    var showDropBoxDialog by remember { mutableStateOf(false) }
    var claimingBoxId by remember { mutableStateOf<String?>(null) }

    val segments = ProCreatorSuiteRepository.SPIN_WHEEL_SEGMENTS

    Scaffold(
        containerColor = Color(0xFF090910),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Daily Rewards & Spin",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Streak Coins • Lucky Spin • Treasure Drops",
                            color = Color(0xFFA0A0B8),
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("daily_rewards_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    Surface(
                        onClick = onNavigateToCosmetics,
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF231D0A),
                        border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.6f)),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Default.MonetizationOn,
                                contentDescription = "Coins",
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${rewardsState.coins}",
                                color = Color(0xFFFFD700),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF090910))
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        ) {
            val currentWonBanner = lastWonBanner
            if (currentWonBanner != null) {
                item(key = "won_banner") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF122C1F),
                        border = BorderStroke(1.dp, Color(0xFF10B981))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = currentWonBanner,
                                color = Color(0xFF6EE7B7),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { lastWonBanner = null }) {
                                Text("Nice!", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 1. 7-Day Escalating Daily Check-In Streak Card
            item(key = "streak_card") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF131320),
                    border = BorderStroke(1.dp, Color(0xFFFF6D00).copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.LocalFireDepartment,
                                    contentDescription = "Streak",
                                    tint = Color(0xFFFF6D00),
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = "7-Day Check-In Streak",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "Day ${rewardsState.streakDay}/7 completed • Escalating coins",
                                        color = Color(0xFFA0A0B8),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ProCreatorSuiteRepository.DAILY_STREAK_REWARDS.forEachIndexed { idx, reward ->
                                val dayNum = idx + 1
                                val isClaimed = dayNum <= rewardsState.streakDay
                                val isNext = dayNum == (if (rewardsState.streakDay >= 7) 1 else rewardsState.streakDay + 1) && rewardsState.canClaimCheckInToday
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            when {
                                                isClaimed -> Color(0xFF173125)
                                                isNext -> Color(0xFF31210A)
                                                else -> Color(0xFF1B1B2B)
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            when {
                                                isClaimed -> Color(0xFF10B981)
                                                isNext -> Color(0xFFFFD700)
                                                else -> Color.White.copy(alpha = 0.08f)
                                            },
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(vertical = 8.dp, horizontal = 2.dp)
                                ) {
                                    Text(
                                        text = "D$dayNum",
                                        color = if (isNext) Color(0xFFFFD700) else Color(0xFFB0B0C8),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    if (isClaimed) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Claimed",
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "+$reward",
                                            color = if (dayNum == 7) Color(0xFFFFD700) else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                if (!RepositoryManager.isRealUserLoggedIn()) {
                                    Toast.makeText(context, "Please sign in first to claim daily rewards", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (isClaimingStreak || !rewardsState.canClaimCheckInToday) return@Button
                                isClaimingStreak = true
                                scope.launch {
                                    val res = ProCreatorSuiteRepository.claimDailyCheckIn(currentUserId)
                                    isClaimingStreak = false
                                    res.onSuccess { (day, coins) ->
                                        lastWonBanner = "🔥 Day $day Check-In Complete! +$coins Coins added!"
                                    }.onFailure { err ->
                                        Toast.makeText(context, err.message ?: "Check-in failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = rewardsState.canClaimCheckInToday && !isClaimingStreak,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF6D00),
                                disabledContainerColor = Color(0xFF262636)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("claim_daily_streak_btn")
                        ) {
                            Text(
                                text = if (rewardsState.canClaimCheckInToday) {
                                    "Claim Day ${if (rewardsState.streakDay >= 7) 1 else rewardsState.streakDay + 1} Reward 🪙"
                                } else {
                                    "Checked In Today ✓ Come Back Tomorrow"
                                },
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // 2. Pro Interactive Spin Wheel
            item(key = "spin_wheel") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF131320),
                    border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🎡 Lucky Creator Spin Wheel",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "1 Free Daily Spin — Win up to 500 Coins!",
                            color = Color(0xFFB4B4CC),
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Box(
                            modifier = Modifier.size(256.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(240.dp)) {
                                val sweep = 360f / segments.size
                                val radius = size.minDimension / 2f
                                val center = Offset(size.width / 2f, size.height / 2f)

                                // Outer glowing ring
                                drawCircle(
                                    brush = Brush.sweepGradient(
                                        listOf(Color(0xFFFFD700), Color(0xFFFF2A6D), Color(0xFF00F0FF), Color(0xFFFFD700))
                                    ),
                                    radius = radius + 6.dp.toPx()
                                )

                                rotate(degrees = wheelRotation.value, pivot = center) {
                                    segments.forEachIndexed { idx, (coins, segColor) ->
                                        val startAngle = idx * sweep - 90f
                                        drawArc(
                                            color = segColor,
                                            startAngle = startAngle,
                                            sweepAngle = sweep,
                                            useCenter = true,
                                            topLeft = Offset.Zero,
                                            size = Size(size.width, size.height)
                                        )

                                        // Draw coin label inside segment
                                        val midRad = Math.toRadians((startAngle + sweep / 2f).toDouble())
                                        val textRadius = radius * 0.66f
                                        val tx = center.x + (textRadius * cos(midRad)).toFloat()
                                        val ty = center.y + (textRadius * sin(midRad)).toFloat()
                                        drawContext.canvas.nativeCanvas.apply {
                                            val paint = android.graphics.Paint().apply {
                                                color = android.graphics.Color.WHITE
                                                textSize = 32f
                                                isFakeBoldText = true
                                                textAlign = android.graphics.Paint.Align.CENTER
                                                setShadowLayer(4f, 0f, 2f, android.graphics.Color.BLACK)
                                            }
                                            drawText("$coins", tx, ty + 11f, paint)
                                        }
                                    }
                                }

                                // Center hub
                                drawCircle(color = Color(0xFF10101A), radius = 26.dp.toPx())
                                drawCircle(color = Color(0xFFFFD700), radius = 21.dp.toPx())
                            }

                            // Top Triangle Pointer
                            Canvas(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .size(26.dp)
                            ) {
                                val path = Path().apply {
                                    moveTo(size.width / 2f, size.height)
                                    lineTo(0f, 0f)
                                    lineTo(size.width, 0f)
                                    close()
                                }
                                drawPath(path = path, color = Color(0xFFFFD700))
                            }

                            Text(
                                text = "GO",
                                color = Color.Black,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (!RepositoryManager.isRealUserLoggedIn()) {
                                    Toast.makeText(context, "Please sign in first to spin the wheel", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (isSpinning || !rewardsState.canSpinToday) return@Button
                                isSpinning = true
                                val targetIdx = Random.nextInt(segments.size)
                                val wonCoins = segments[targetIdx].first
                                val sweep = 360f / segments.size
                                val targetSliceCenter = targetIdx * sweep + sweep / 2f
                                val currentMod = ((wheelRotation.value % 360f) + 360f) % 360f
                                val desiredAngleMod = (360f - targetSliceCenter + 360f) % 360f
                                val delta = ((desiredAngleMod - currentMod) + 360f) % 360f
                                val finalTarget = wheelRotation.value + (360f * 6f) + delta

                                scope.launch {
                                    wheelRotation.animateTo(
                                        targetValue = finalTarget,
                                        animationSpec = tween(durationMillis = 3600, easing = FastOutSlowInEasing)
                                    )
                                    val res = ProCreatorSuiteRepository.claimSpinWheelReward(currentUserId, wonCoins)
                                    isSpinning = false
                                    res.onSuccess { awarded ->
                                        lastWonBanner = "🎉 JACKPOT SPIN! You won +$awarded Coins!"
                                    }.onFailure { err ->
                                        Toast.makeText(context, err.message ?: "Spin failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = rewardsState.canSpinToday && !isSpinning,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF8B5CF6),
                                disabledContainerColor = Color(0xFF262636)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("spin_wheel_btn")
                        ) {
                            Text(
                                text = when {
                                    isSpinning -> "Spinning..."
                                    rewardsState.canSpinToday -> "SPIN NOW (FREE) 🎰"
                                    else -> "Spin Used Today ✓ Come Back Tomorrow"
                                },
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // 3. Live Lucky Coin Boxes Section (Drop & Claim!)
            item(key = "lucky_box_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "🎁 Live Lucky Coin Boxes",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Tap any creator box to grab instant random coins",
                            color = Color(0xFF9A9AB2),
                            fontSize = 11.sp
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            if (!RepositoryManager.isRealUserLoggedIn()) {
                                Toast.makeText(context, "Please sign in first to drop a Lucky Box", Toast.LENGTH_SHORT).show()
                            } else {
                                showDropBoxDialog = true
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFFFFD700),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("drop_lucky_box_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Drop Box", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Drop Box", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }
            }

            if (activeLuckyBoxes.isEmpty()) {
                item(key = "empty_boxes") {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF14141F),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Redeem,
                                contentDescription = "No Lucky Boxes",
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Be the first to drop a Lucky Treasure Box!",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Share coins with fans & boost your profile visibility.",
                                color = Color(0xFF9A9AB2),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(activeLuckyBoxes, key = { it.id }) { box ->
                    val alreadyClaimed = box.claimedBy[currentUserId]
                    val isBusy = claimingBoxId == box.id
                    LuckyCoinBoxCard(
                        box = box,
                        claimedByMe = alreadyClaimed,
                        isBusy = isBusy,
                        onClaim = {
                            if (isBusy || alreadyClaimed != null) return@LuckyCoinBoxCard
                            claimingBoxId = box.id
                            scope.launch {
                                val res = ProCreatorSuiteRepository.claimLuckyCoinBox(box.id, currentUserId)
                                claimingBoxId = null
                                res.onSuccess { amt ->
                                    lastWonBanner = "🎁 Lucky Box Opened! Grabbed +$amt Coins from @${box.creatorUsername}!"
                                }.onFailure { err ->
                                    Toast.makeText(context, err.message ?: "Could not claim", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showDropBoxDialog) {
        DropLuckyCoinBoxDialog(
            availableCoins = rewardsState.coins,
            onDismiss = { showDropBoxDialog = false },
            onConfirmDrop = { title, coinsPool, maxWinners ->
                scope.launch {
                    val res = ProCreatorSuiteRepository.dropLuckyCoinBox(
                        creatorId = currentUserId,
                        creatorUsername = currentUser?.username?.ifBlank { "Creator" } ?: "Creator",
                        creatorAvatar = currentUser?.avatarUrl ?: "",
                        isVerified = currentUser?.isVerified ?: false,
                        title = title,
                        totalCoins = coinsPool,
                        maxClaimants = maxWinners
                    )
                    res.onSuccess {
                        showDropBoxDialog = false
                        Toast.makeText(context, "🎁 Lucky Box dropped for $maxWinners fans!", Toast.LENGTH_LONG).show()
                    }.onFailure { err ->
                        Toast.makeText(context, err.message ?: "Failed to drop box", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

@Composable
private fun LuckyCoinBoxCard(
    box: LuckyCoinBox,
    claimedByMe: Int?,
    isBusy: Boolean,
    onClaim: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF151524),
        border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("lucky_box_card_${box.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFFFB300), Color(0xFFFF3D00)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CardGiftcard, contentDescription = "Lucky Box", tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = box.title,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "by @${box.creatorUsername}",
                        color = Color(0xFFB0B0C8),
                        fontSize = 11.sp
                    )
                    if (box.isCreatorVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        VerificationBadge(isVerified = true, modifier = Modifier.size(12.dp))
                    }
                }
                Text(
                    text = "🪙 ${box.remainingCoins}/${box.totalCoins} left • ${box.claimedBy.size}/${box.maxClaimants} claimed",
                    color = Color(0xFFFFD700),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Button(
                onClick = onClaim,
                enabled = claimedByMe == null && !isBusy,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD700),
                    disabledContainerColor = Color(0xFF233229)
                ),
                modifier = Modifier.testTag("claim_box_btn_${box.id}")
            ) {
                Text(
                    text = when {
                        isBusy -> "..."
                        claimedByMe != null -> "+$claimedByMe ✓"
                        else -> "Grab"
                    },
                    color = if (claimedByMe != null) Color(0xFF6EE7B7) else Color.Black,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun DropLuckyCoinBoxDialog(
    availableCoins: Long,
    onDismiss: () -> Unit,
    onConfirmDrop: (title: String, coinsPool: Int, maxWinners: Int) -> Unit
) {
    var title by remember { mutableStateOf("Fan Appreciation Drop 🎁") }
    var selectedPool by remember { mutableIntStateOf(50) }
    var selectedWinners by remember { mutableIntStateOf(5) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151524),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFD0D0E0),
        title = {
            Text("Drop a Lucky Coin Box 🎁", fontWeight = FontWeight.ExtraBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Your balance: 🪙 $availableCoins coins",
                    color = Color(0xFFFFD700),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Box Message") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Coin Pool:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(50, 100, 250, 500).forEach { pool ->
                        FilterChip(
                            selected = selectedPool == pool,
                            onClick = { selectedPool = pool },
                            label = { Text("$pool") }
                        )
                    }
                }
                Text("Max Lucky Claimants:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 20, 30).forEach { w ->
                        FilterChip(
                            selected = selectedWinners == w,
                            onClick = { selectedWinners = w },
                            label = { Text("$w") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmDrop(title, selectedPool, selectedWinners) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700))
            ) {
                Text("Drop ($selectedPool 🪙)", color = Color.Black, fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFFB0B0C8))
            }
        }
    )
}
