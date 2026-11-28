package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.model.PaymentProductType
import com.example.model.RepositoryManager
import com.example.repository.EntitlementRepository
import com.example.repository.ExclusiveContentRepository
import com.example.repository.ExclusivePurchaseRecord
import com.example.repository.ExclusiveUnlockResult
import com.example.repository.VaultMediaPayload
import com.example.repository.isExclusiveStatusPending
import com.example.repository.isExclusiveStatusUnlocked
import com.example.ui.components.RealUpiPaymentBottomSheet
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val ExclusiveGoldStart = Color(0xFFFFD54F)
val ExclusiveGoldMid = Color(0xFFFFB300)
val ExclusiveGoldEnd = Color(0xFFFF8F00)

val ExclusiveGoldBrush = Brush.horizontalGradient(
    colors = listOf(ExclusiveGoldStart, ExclusiveGoldMid, ExclusiveGoldEnd)
)

fun formatCountdownRemaining(expiresAt: Long, nowMs: Long = System.currentTimeMillis()): String {
    if (expiresAt <= 0L) return "48h window"
    val diff = (expiresAt - nowMs).coerceAtLeast(0L)
    if (diff == 0L) return "Expiring — auto-refund queued"
    val hours = diff / (1000L * 60L * 60L)
    val mins = (diff / (1000L * 60L)) % 60L
    return "${hours}h ${mins}m left"
}

/**
 * Small gold "Exclusive" badge shown to creators on their own exclusive content
 * or as a compact unlocked indicator.
 */
@Composable
fun ExclusiveGoldBadge(
    label: String = "Exclusive",
    priceInr: Double? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1A1405).copy(alpha = 0.86f),
        border = BorderStroke(1.dp, ExclusiveGoldMid.copy(alpha = 0.8f)),
        shadowElevation = 4.dp,
        modifier = modifier.testTag("exclusive_gold_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WorkspacePremium,
                contentDescription = "Exclusive Content",
                tint = ExclusiveGoldStart,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = if (priceInr != null && priceInr >= 10.0) "$label • ₹${priceInr.toInt()}" else label,
                color = ExclusiveGoldStart,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }
    }
}

/**
 * Compact lock pill for Profile Grid / Reels Grid thumbnails.
 */
@Composable
fun ExclusiveGridLockBadge(
    priceInr: Double,
    isOwner: Boolean,
    isUnlocked: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        isOwner -> Color(0xFF1E1603).copy(alpha = 0.88f)
        isUnlocked -> Color(0xFF062E20).copy(alpha = 0.88f)
        else -> Color(0xFF14111A).copy(alpha = 0.88f)
    }
    val tint = when {
        isOwner -> ExclusiveGoldStart
        isUnlocked -> Color(0xFF34D399)
        else -> ExclusiveGoldStart
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(0.8.dp, tint.copy(alpha = 0.7f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = when {
                    isOwner -> Icons.Default.WorkspacePremium
                    isUnlocked -> Icons.Default.LockOpen
                    else -> Icons.Default.Lock
                },
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = when {
                    isOwner -> "₹${priceInr.toInt()}"
                    isUnlocked -> "Unlocked"
                    else -> "₹${priceInr.toInt()}"
                },
                color = tint,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Shimmer skeleton loader used while vault media or unlocked library loads.
 */
@Composable
fun ExclusiveSkeletonBox(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 14
) {
    val infiniteTransition = rememberInfiniteTransition(label = "exclusive_shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(Color(0xFF232330).copy(alpha = alpha))
    )
}

/**
 * Instagram-grade Locked Exclusive Overlay shown over ONLY the pre-generated blurred preview image.
 * Real media URL is NEVER loaded or passed here until Creator approves.
 */
@Composable
fun ExclusiveLockedOverlay(
    blurredPreviewUrl: String,
    creatorUsername: String,
    creatorAvatarUrl: String,
    teaserCaption: String,
    priceInr: Double,
    contentTypeLabel: String = "Exclusive Content",
    compact: Boolean = false,
    purchaseStatus: String? = null,
    expiresAt: Long = 0L,
    onUnlockClick: () -> Unit,
    onReportClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isPendingApproval = isExclusiveStatusPending(purchaseStatus)
    val isRefundedOrRejected = purchaseStatus.equals("rejected", true) ||
        purchaseStatus.equals("refunded", true)

    Box(
        modifier = modifier
            .background(Color(0xFF0E0D14))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onUnlockClick()
            },
        contentAlignment = Alignment.Center
    ) {
        if (blurredPreviewUrl.isNotBlank()) {
            AsyncImage(
                model = blurredPreviewUrl,
                contentDescription = "Locked exclusive preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(26.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF1F162B), Color(0xFF13111C), Color(0xFF2B1D0E))
                        )
                    )
            )
        }

        val hasRenderEffectBlur = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (hasRenderEffectBlur) {
                            listOf(
                                Color.Black.copy(alpha = 0.56f),
                                Color.Black.copy(alpha = 0.78f),
                                Color.Black.copy(alpha = 0.88f)
                            )
                        } else {
                            listOf(
                                Color.Black.copy(alpha = 0.84f),
                                Color.Black.copy(alpha = 0.90f),
                                Color.Black.copy(alpha = 0.95f)
                            )
                        }
                    )
                )
        )

        // Top-left exclusive pill
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.62f),
            border = BorderStroke(
                1.dp,
                if (isPendingApproval) Color(0xFFF59E0B).copy(alpha = 0.8f) else ExclusiveGoldMid.copy(alpha = 0.65f)
            ),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = if (isPendingApproval) Icons.Default.HourglassTop else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isPendingApproval) Color(0xFFFBBF24) else ExclusiveGoldStart,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (isPendingApproval) "Waiting for Approval" else contentTypeLabel,
                    color = if (isPendingApproval) Color(0xFFFBBF24) else ExclusiveGoldStart,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (onReportClick != null) {
            IconButton(
                onClick = onReportClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(48.dp)
                    .testTag("exclusive_report_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = "Report Exclusive Content",
                    tint = Color.White.copy(alpha = 0.78f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Center Glass Card
        Column(
            modifier = Modifier
                .padding(horizontal = if (compact) 16.dp else 24.dp)
                .widthIn(max = 326.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF14131D).copy(alpha = 0.88f))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            (if (isPendingApproval) Color(0xFFF59E0B) else ExclusiveGoldStart).copy(alpha = 0.6f),
                            Color.White.copy(alpha = 0.14f)
                        )
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(horizontal = 20.dp, vertical = if (compact) 16.dp else 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 48.dp else 58.dp)
                    .clip(CircleShape)
                    .background(
                        (if (isPendingApproval) Color(0xFFF59E0B) else ExclusiveGoldMid).copy(alpha = 0.18f)
                    )
                    .border(
                        1.5.dp,
                        if (isPendingApproval) Color(0xFFFBBF24) else ExclusiveGoldStart,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPendingApproval) Icons.Default.HourglassTop else Icons.Default.Lock,
                    contentDescription = if (isPendingApproval) "Waiting for Creator Approval" else "Locked",
                    tint = if (isPendingApproval) Color(0xFFFBBF24) else ExclusiveGoldStart,
                    modifier = Modifier.size(if (compact) 24.dp else 28.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (creatorAvatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = creatorAvatarUrl,
                        contentDescription = creatorUsername,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .border(1.dp, ExclusiveGoldStart, CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2B2416))
                            .border(1.dp, ExclusiveGoldStart, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = creatorUsername.take(1).uppercase().ifEmpty { "C" },
                            color = ExclusiveGoldStart,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "@${creatorUsername.ifBlank { "creator" }}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isPendingApproval) {
                Text(
                    text = "Payment done - waiting for creator approval",
                    color = Color(0xFFFBBF24),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.testTag("exclusive_pending_approval_text")
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${formatCountdownRemaining(expiresAt)} • Auto-refunded to your wallet if not approved within 48 hours.",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 11.5.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF59E0B).copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable { onUnlockClick() }
                        .testTag("exclusive_pending_status_button")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = "Paid ₹${priceInr.toInt()} • View Status",
                            color = Color(0xFFFBBF24),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Text(
                    text = teaserCaption.ifBlank { "Unlock to view this exclusive content in full HD" },
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = if (compact) 12.sp else 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                if (isRefundedOrRejected) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Previous request was refunded to your wallet.",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ExclusiveGoldBrush)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onUnlockClick()
                        }
                        .testTag("unlock_exclusive_cta"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = Color(0xFF1A1200),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Unlock ₹${priceInr.toInt()}",
                            color = Color(0xFF1A1200),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Smooth Bottom Sheet for unlocking Exclusive Posts, Reels, or Stories.
 * Supports atomic Wallet escrow AND verified UPI / Razorpay payment with mandatory 48h Creator Approval.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExclusiveUnlockBottomSheet(
    contentId: String,
    contentType: String,
    creatorId: String,
    creatorUsername: String,
    creatorAvatarUrl: String,
    title: String,
    teaserCaption: String,
    blurredThumbnailUrl: String,
    priceInr: Double,
    onDismiss: () -> Unit,
    onUnlocked: (VaultMediaPayload?) -> Unit,
    onPendingApproval: (ExclusivePurchaseRecord) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val buyerId = RepositoryManager.getCurrentUserId()

    val purchaseStatusMap by EntitlementRepository.purchaseStatusMapFlow.collectAsStateWithLifecycle()
    val livePurchaseRecord = purchaseStatusMap[contentId]
    var localPendingRecord by remember(contentId) { mutableStateOf<ExclusivePurchaseRecord?>(null) }
    val activeRecord = livePurchaseRecord ?: localPendingRecord

    val userHistory by remember(buyerId) {
        ExclusiveContentRepository.observeUserExclusiveHistory(buyerId)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    var walletBalance by remember { mutableStateOf<Double?>(null) }
    var isProcessingWallet by remember { mutableStateOf(false) }
    var showUpiGateway by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(buyerId) {
        walletBalance = ExclusiveContentRepository.getUserWalletBalance(buyerId)
        ExclusiveContentRepository.processExpiredExclusiveRequests(buyerId)
    }

    // Auto-reveal if creator approves while sheet is open
    LaunchedEffect(activeRecord?.status) {
        if (isExclusiveStatusUnlocked(activeRecord?.status)) {
            val media = ExclusiveContentRepository.resolveUnlockedMedia(contentId, forceRefresh = true)
            onUnlocked(media)
        }
    }

    val isAlreadyPending = isExclusiveStatusPending(activeRecord?.status)
    val isAlreadyUnlocked = isExclusiveStatusUnlocked(activeRecord?.status)
    val typeLabel = when (contentType.uppercase()) {
        "REEL", "VIDEO" -> "Exclusive Reel"
        "STORY" -> "Exclusive Story"
        else -> "Exclusive Post"
    }

    if (showUpiGateway && !isAlreadyPending && !isAlreadyUnlocked) {
        RealUpiPaymentBottomSheet(
            productType = PaymentProductType.INDIVIDUAL_POST_UNLOCK,
            amountRupees = priceInr,
            title = "Unlock $typeLabel",
            subtitle = "@$creatorUsername • Creator Approval (48h)",
            creatorId = creatorId,
            contentId = contentId,
            onDismiss = { showUpiGateway = false },
            onPaymentVerified = { order ->
                showUpiGateway = false
                isProcessingWallet = true
                scope.launch {
                    val res = ExclusiveContentRepository.recordVerifiedGatewayUnlock(
                        buyerId = buyerId,
                        creatorId = creatorId,
                        contentId = contentId,
                        contentType = contentType,
                        title = title,
                        teaserCaption = teaserCaption,
                        blurredThumbnailUrl = blurredThumbnailUrl,
                        creatorUsername = creatorUsername,
                        creatorAvatar = creatorAvatarUrl,
                        priceInr = priceInr,
                        transactionRef = order.providerTxnId.ifBlank { order.orderId }
                    )
                    isProcessingWallet = false
                    when (res) {
                        is ExclusiveUnlockResult.Success -> {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onUnlocked(res.unlockedMedia)
                        }
                        is ExclusiveUnlockResult.PendingApproval -> {
                            localPendingRecord = res.record
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPendingApproval(res.record)
                            Toast.makeText(
                                context,
                                "Payment done - waiting for creator approval",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        is ExclusiveUnlockResult.Failure -> {
                            errorMessage = res.message
                        }
                        is ExclusiveUnlockResult.InsufficientWallet -> Unit
                    }
                }
            },
            onUtrSubmitted = { _, _ ->
                showUpiGateway = false
                Toast.makeText(
                    context,
                    "Payment submitted for verification. Creator will receive your request once verified.",
                    Toast.LENGTH_LONG
                ).show()
                onDismiss()
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isProcessingWallet) onDismiss() },
        containerColor = Color(0xFF12121A),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Creator + Price Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .border(2.dp, ExclusiveGoldBrush, CircleShape)
                        .padding(2.dp)
                ) {
                    if (creatorAvatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = creatorAvatarUrl,
                            contentDescription = creatorUsername,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color(0xFF2B2416)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = creatorUsername.take(1).uppercase().ifEmpty { "C" },
                                color = ExclusiveGoldStart,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = typeLabel,
                        color = ExclusiveGoldStart,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "@${creatorUsername.ifBlank { "creator" }}",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (teaserCaption.isNotBlank()) {
                        Text(
                            text = teaserCaption,
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ExclusiveGoldMid.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, ExclusiveGoldMid.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "₹${priceInr.toInt()}",
                        color = ExclusiveGoldStart,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }

            if (isAlreadyPending) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF261C06),
                    border = BorderStroke(1.2.dp, Color(0xFFF59E0B).copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.HourglassTop, contentDescription = null, tint = Color(0xFFFBBF24))
                            Text(
                                text = "Payment done - waiting for creator approval",
                                color = Color(0xFFFBBF24),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Text(
                            text = "@$creatorUsername has received your request. Content unlocks live on your screen as soon as they approve (${formatCountdownRemaining(activeRecord?.expiresAt ?: 0L)}). If declined or unapproved after 48h, ₹${priceInr.toInt()} is automatically refunded to your Wallet.",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = ExclusiveGoldMid, contentColor = Color(0xFF1A1200)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Got It", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1A1A26),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "How Exclusive Unlock works",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        ExclusivePerkRow(Icons.Default.VerifiedUser, "Payment held securely until @$creatorUsername approves (48h window)")
                        ExclusivePerkRow(Icons.Default.Hd, "Instant full HD unblurred reveal on creator approval")
                        ExclusivePerkRow(Icons.Default.CurrencyRupee, "100% automatic wallet refund if rejected or no response in 48h")
                    }
                }

                val activeErrorMessage = errorMessage
                if (activeErrorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFF4B4B).copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Color(0xFFFF4B4B).copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(18.dp))
                            Text(activeErrorMessage, color = Color(0xFFFF9E9E), fontSize = 12.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }

                val balance = walletBalance
                if (balance == null) {
                    ExclusiveSkeletonBox(modifier = Modifier.fillMaxWidth().height(52.dp))
                } else {
                    val hasEnoughWallet = balance >= priceInr
                    Button(
                        onClick = {
                            if (!hasEnoughWallet) {
                                errorMessage = "Wallet balance is ₹${String.format(Locale.US, "%.2f", balance)}. Use UPI / Razorpay below or add funds."
                                return@Button
                            }
                            errorMessage = null
                            isProcessingWallet = true
                            scope.launch {
                                when (
                                    val res = ExclusiveContentRepository.unlockWithWallet(
                                        buyerId = buyerId,
                                        creatorId = creatorId,
                                        contentId = contentId,
                                        contentType = contentType,
                                        title = title,
                                        teaserCaption = teaserCaption,
                                        blurredThumbnailUrl = blurredThumbnailUrl,
                                        creatorUsername = creatorUsername,
                                        creatorAvatar = creatorAvatarUrl,
                                        priceInr = priceInr
                                    )
                                ) {
                                    is ExclusiveUnlockResult.Success -> {
                                        isProcessingWallet = false
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onUnlocked(res.unlockedMedia)
                                    }
                                    is ExclusiveUnlockResult.PendingApproval -> {
                                        isProcessingWallet = false
                                        localPendingRecord = res.record
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onPendingApproval(res.record)
                                        Toast.makeText(
                                            context,
                                            "Payment done - waiting for creator approval",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    is ExclusiveUnlockResult.InsufficientWallet -> {
                                        isProcessingWallet = false
                                        walletBalance = res.currentBalance
                                        errorMessage = "Insufficient wallet balance (₹${String.format(Locale.US, "%.2f", res.currentBalance)}). Pay with UPI below."
                                    }
                                    is ExclusiveUnlockResult.Failure -> {
                                        isProcessingWallet = false
                                        errorMessage = res.message
                                    }
                                }
                            }
                        },
                        enabled = !isProcessingWallet,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasEnoughWallet) ExclusiveGoldMid else Color(0xFF262635),
                            contentColor = if (hasEnoughWallet) Color(0xFF1A1200) else Color.White.copy(alpha = 0.7f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("exclusive_pay_wallet_button")
                    ) {
                        if (isProcessingWallet) {
                            CircularProgressIndicator(
                                color = Color(0xFF1A1200),
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Submitting Request...", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        } else {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(19.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (hasEnoughWallet) {
                                    "Pay ₹${priceInr.toInt()} with Wallet (Bal: ₹${String.format(Locale.US, "%.0f", balance)})"
                                } else {
                                    "Wallet Balance: ₹${String.format(Locale.US, "%.0f", balance)} (Low)"
                                },
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        errorMessage = null
                        showUpiGateway = true
                    },
                    enabled = !isProcessingWallet,
                    border = BorderStroke(1.2.dp, ExclusiveGoldMid.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("exclusive_pay_upi_button")
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = ExclusiveGoldStart, modifier = Modifier.size(19.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pay ₹${priceInr.toInt()} via Razorpay / UPI",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            // Expandable Transaction History (Buyer & Creator)
            if (userHistory.isNotEmpty()) {
                TextButton(
                    onClick = { showHistory = !showHistory },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(
                        imageVector = if (showHistory) Icons.Default.ExpandLess else Icons.Default.History,
                        contentDescription = null,
                        tint = ExclusiveGoldStart,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (showHistory) "Hide Exclusive History" else "Exclusive Transaction History (${userHistory.size})",
                        color = ExclusiveGoldStart,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                AnimatedVisibility(visible = showHistory) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val fmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
                        userHistory.take(8).forEach { tx ->
                            val isCreatorRow = tx.creatorId == buyerId
                            val statusColor = when {
                                isExclusiveStatusUnlocked(tx.status) -> Color(0xFF34D399)
                                isExclusiveStatusPending(tx.status) -> Color(0xFFFBBF24)
                                else -> Color(0xFF38BDF8)
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1A1A26),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isCreatorRow) "Request from @${tx.buyerUsername.ifBlank { "fan" }}" else "Unlock @${tx.creatorUsername.ifBlank { "creator" }}",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${tx.contentType} • ${fmt.format(Date(tx.timestamp))}",
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 10.sp
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "₹${tx.amount.toInt()}",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = tx.status.replace('_', ' ').uppercase(Locale.US),
                                            color = statusColor,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold
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
}

@Composable
private fun ExclusivePerkRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
        Text(text, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
    }
}

/**
 * Report Exclusive Content Dialog -> writes to `exclusive_reports` for Admin moderation.
 */
@Composable
fun ExclusiveReportDialog(
    contentId: String,
    contentType: String,
    creatorId: String,
    creatorUsername: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reasons = listOf(
        "Misleading teaser or preview",
        "Inappropriate / Policy violation",
        "Spam or Scam content",
        "Copyright infringement"
    )
    var selectedReason by remember { mutableStateOf(reasons.first()) }
    var extraDetails by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        containerColor = Color(0xFF16161F),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Flag, contentDescription = null, tint = Color(0xFFFF6B6B))
                Text("Report Exclusive Content", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Our moderation team reviews reported paid content and can remove violations or issue refunds.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                reasons.forEach { r ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedReason == r) Color(0xFFFF6B6B).copy(alpha = 0.16f) else Color(0xFF20202C))
                            .clickable { selectedReason = r }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedReason == r,
                            onClick = { selectedReason = r },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFF6B6B))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(r, color = Color.White, fontSize = 13.sp)
                    }
                }
                OutlinedTextField(
                    value = extraDetails,
                    onValueChange = { if (it.length <= 200) extraDetails = it },
                    label = { Text("Additional details (Optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSubmitting = true
                    scope.launch {
                        val ok = ExclusiveContentRepository.reportExclusiveContent(
                            contentId = contentId,
                            contentType = contentType,
                            creatorId = creatorId,
                            creatorUsername = creatorUsername,
                            reason = selectedReason,
                            details = extraDetails.trim()
                        )
                        isSubmitting = false
                        Toast.makeText(
                            context,
                            if (ok) "Report submitted to Admin moderation." else "Could not submit report.",
                            Toast.LENGTH_SHORT
                        ).show()
                        onDismiss()
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52))
            ) {
                Text(if (isSubmitting) "Submitting..." else "Submit Report", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.75f))
            }
        }
    )
}
