package com.example.ui

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.model.CosmeticItem
import com.example.model.RepositoryManager
import com.example.model.UserCosmeticsState
import com.example.repository.ProCreatorSuiteRepository
import kotlinx.coroutines.launch

/**
 * Reusable Profile Theme Background Brush resolver used by ProfileScreen & Preview.
 */
fun resolveProfileThemeBrush(themeId: String): Brush? {
    val item = ProCreatorSuiteRepository.COSMETIC_CATALOG.firstOrNull { it.type == "THEME" && it.id == themeId }
        ?: return null
    return Brush.verticalGradient(
        colors = listOf(
            item.primaryColor,
            item.secondaryColor,
            Color(0xFF0A0A0F)
        )
    )
}

fun resolveProfileThemeTopColor(themeId: String): Color? {
    return ProCreatorSuiteRepository.COSMETIC_CATALOG
        .firstOrNull { it.type == "THEME" && it.id == themeId }
        ?.primaryColor
}

fun resolveCosmeticAccentColor(cosmeticId: String): Color? {
    return ProCreatorSuiteRepository.COSMETIC_CATALOG.firstOrNull { it.id == cosmeticId }?.accentColor
}

/**
 * Reusable Animated Avatar Frame Ring composable.
 * Only starts rotation animation when an equipped frame is active (zero overhead for default avatars).
 */
@Composable
fun CosmeticAvatarFrameRing(
    frameId: String,
    avatarUrl: String,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String = "User Avatar",
    onClick: (() -> Unit)? = null
) {
    val frameItem = remember(frameId) {
        ProCreatorSuiteRepository.COSMETIC_CATALOG.firstOrNull { it.type == "FRAME" && it.id == frameId }
    }
    val clickMod = if (onClick != null) modifier.clickable { onClick() } else modifier

    if (frameItem == null) {
        Box(
            modifier = clickMod
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFF1F2233)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = avatarUrl.ifBlank { "https://api.dicebear.com/7.x/avataaars/png?seed=user" },
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        }
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "avatar_frame_$frameId")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_angle"
    )

    Box(
        modifier = clickMod.size(size + 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 3.5.dp.toPx()
            rotate(degrees = angle) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            frameItem.primaryColor,
                            frameItem.secondaryColor,
                            frameItem.accentColor,
                            frameItem.primaryColor
                        )
                    ),
                    radius = (this.size.minDimension - strokePx) / 2f,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }
        AsyncImage(
            model = avatarUrl.ifBlank { "https://api.dicebear.com/7.x/avataaars/png?seed=user" },
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(2.dp, Color(0xFF0A0A0F), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(frameItem.primaryColor, frameItem.secondaryColor))
                )
                .border(1.5.dp, Color(0xFF0A0A0F), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = frameItem.emoji, fontSize = 11.sp)
        }
    }
}

/**
 * Reusable VIP Entrance / Supporter Badge pill displayed on Profile & Comments.
 */
@Composable
fun VipEntranceBadgePill(
    entranceId: String,
    modifier: Modifier = Modifier
) {
    val item = remember(entranceId) {
        ProCreatorSuiteRepository.COSMETIC_CATALOG.firstOrNull { it.type == "ENTRANCE" && it.id == entranceId }
    } ?: return

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(item.primaryColor, item.secondaryColor)))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(text = item.emoji, fontSize = 10.sp)
        Text(
            text = item.badgeLabel.ifBlank { item.name },
            color = Color.Black,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CosmeticsAndThemesStoreScreen(
    onBack: () -> Unit,
    onNavigateToDailyRewards: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserId = remember { RepositoryManager.getCurrentUserId() }
    val currentUser by RepositoryManager.currentUserState.collectAsStateWithLifecycle()

    val cosmeticsState by remember(currentUserId) {
        ProCreatorSuiteRepository.observeUserCosmetics(currentUserId)
    }.collectAsStateWithLifecycle(initialValue = UserCosmeticsState())

    var selectedTab by remember { mutableIntStateOf(0) } // 0=Frames, 1=Themes, 2=VIP Badges
    var previewItem by remember { mutableStateOf<CosmeticItem?>(null) }
    var isProcessingId by remember { mutableStateOf<String?>(null) }

    val activeType = when (selectedTab) {
        0 -> "FRAME"
        1 -> "THEME"
        else -> "ENTRANCE"
    }
    val filteredItems = remember(activeType) {
        ProCreatorSuiteRepository.COSMETIC_CATALOG.filter { it.type == activeType }
    }

    val currentPreview = previewItem
    val previewThemeId = if (currentPreview?.type == "THEME") currentPreview.id else cosmeticsState.equippedThemeId
    val previewFrameId = if (currentPreview?.type == "FRAME") currentPreview.id else cosmeticsState.equippedFrameId
    val previewEntranceId = if (currentPreview?.type == "ENTRANCE") currentPreview.id else cosmeticsState.equippedEntranceId

    val previewBgBrush = resolveProfileThemeBrush(previewThemeId) ?: Brush.verticalGradient(
        listOf(Color(0xFF171626), Color(0xFF0D0D14))
    )

    Scaffold(
        containerColor = Color(0xFF09090F),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Style & VIP Store",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Animated Frames, Profile Themes & VIP Tags",
                            color = Color(0xFFA1A1B5),
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("cosmetics_back_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    Surface(
                        onClick = onNavigateToDailyRewards,
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF231D0A),
                        border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.55f)),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("cosmetics_coins_pill")
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
                                text = "${cosmeticsState.coins}",
                                color = Color(0xFFFFD700),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF09090F))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        ) {
            // Live Profile Studio Preview Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(previewBgBrush)
                    .border(
                        1.dp,
                        (resolveCosmeticAccentColor(previewThemeId) ?: Color.White).copy(alpha = 0.28f),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CosmeticAvatarFrameRing(
                        frameId = previewFrameId,
                        avatarUrl = currentUser?.avatarUrl ?: "",
                        size = 66.dp,
                        contentDescription = "Live Avatar Frame Preview"
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "@${currentUser?.username?.ifBlank { "creator" } ?: "creator"}",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (previewEntranceId != "entrance_none") {
                                VipEntranceBadgePill(entranceId = previewEntranceId)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (currentPreview != null) {
                                "Previewing: ${currentPreview.name}"
                            } else {
                                "Live preview of your equipped look"
                            },
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.White.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = "${cosmeticsState.ownedIds.size}/${ProCreatorSuiteRepository.COSMETIC_CATALOG.size} Unlocked",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            if (previewItem != null) {
                                Surface(
                                    onClick = { previewItem = null },
                                    shape = RoundedCornerShape(50),
                                    color = Color.Black.copy(alpha = 0.35f)
                                ) {
                                    Text(
                                        text = "Reset Preview",
                                        color = Color(0xFFFFD700),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Category Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple(0, "Frames", Icons.Default.AutoAwesome),
                    Triple(1, "Themes", Icons.Default.Palette),
                    Triple(2, "VIP Tags", Icons.Default.WorkspacePremium)
                ).forEach { (idx, label, icon) ->
                    val selected = selectedTab == idx
                    val bgColor by animateColorAsState(
                        targetValue = if (selected) Color(0xFFFF2A6D) else Color(0xFF171722),
                        label = "tab_bg_$idx"
                    )
                    Surface(
                        onClick = { selectedTab = idx },
                        shape = RoundedCornerShape(14.dp),
                        color = bgColor,
                        border = BorderStroke(
                            1.dp,
                            if (selected) Color(0xFFFF5E94) else Color.White.copy(alpha = 0.10f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("cosmetics_tab_$idx")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Cosmetics Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    val isOwned = cosmeticsState.ownedIds.contains(item.id)
                    val isEquipped = when (item.type) {
                        "FRAME" -> cosmeticsState.equippedFrameId == item.id
                        "THEME" -> cosmeticsState.equippedThemeId == item.id
                        else -> cosmeticsState.equippedEntranceId == item.id
                    }
                    val isBusy = isProcessingId == item.id

                    Surface(
                        onClick = { previewItem = item },
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF14141F),
                        border = BorderStroke(
                            width = if (isEquipped || previewItem?.id == item.id) 1.5.dp else 1.dp,
                            color = when {
                                isEquipped -> Color(0xFF10B981)
                                previewItem?.id == item.id -> item.accentColor
                                else -> Color.White.copy(alpha = 0.08f)
                            }
                        ),
                        modifier = Modifier.testTag("cosmetic_card_${item.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = item.primaryColor.copy(alpha = 0.22f),
                                    modifier = Modifier.align(Alignment.TopStart)
                                ) {
                                    Text(
                                        text = item.badgeLabel,
                                        color = item.accentColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                                if (isEquipped) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Equipped",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier
                                            .size(18.dp)
                                            .align(Alignment.TopEnd)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            when (item.type) {
                                "FRAME" -> {
                                    CosmeticAvatarFrameRing(
                                        frameId = item.id,
                                        avatarUrl = currentUser?.avatarUrl ?: "",
                                        size = 54.dp,
                                        contentDescription = item.name
                                    )
                                }
                                "THEME" -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(62.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(item.primaryColor, item.secondaryColor, item.accentColor.copy(alpha = 0.45f))
                                                )
                                            )
                                            .border(1.dp, item.accentColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = item.emoji, fontSize = 24.sp)
                                    }
                                }
                                else -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(62.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        VipEntranceBadgePill(entranceId = item.id)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = item.name,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.description,
                                color = Color(0xFF9A9AB2),
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 3.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    if (isBusy) return@Button
                                    isProcessingId = item.id
                                    scope.launch {
                                        val res = ProCreatorSuiteRepository.purchaseOrEquipCosmetic(currentUserId, item)
                                        isProcessingId = null
                                        res.onSuccess { msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }.onFailure { err ->
                                            Toast.makeText(context, err.message ?: "Action failed", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                enabled = !isBusy,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = when {
                                        isEquipped -> Color(0xFF1F2937)
                                        isOwned -> Color(0xFF10B981)
                                        else -> Color(0xFFFF2A6D)
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .testTag("cosmetic_action_${item.id}")
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    val btnLabel = when {
                                        isEquipped -> "Equipped ✓"
                                        isOwned -> "Equip"
                                        else -> "🪙 ${item.priceCoins}"
                                    }
                                    Text(
                                        text = btnLabel,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
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
