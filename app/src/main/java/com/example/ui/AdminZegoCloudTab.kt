package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.model.LivePresenceManager
import com.example.model.RepositoryManager
import com.example.zego.DynamicZegoLiveConfig
import com.example.zego.SocialIndiaZegoConfig
import com.example.zego.ZegoKeyProfile
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

@Composable
fun AdminZegoCloudTab(
    onNavigateToDiagnostics: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val liveConfig by SocialIndiaZegoConfig.liveConfig.collectAsStateWithLifecycle()
    val activeLives by LivePresenceManager.activeLives.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        SocialIndiaZegoConfig.startObserving()
        SocialIndiaZegoConfig.fetchLatestConfig()
    }

    // Add / Hot-Swap Key Profile Form State
    var keyLabelInput by remember { mutableStateOf("") }
    var appIdInput by remember(liveConfig.appId) { mutableStateOf(liveConfig.appId.toString()) }
    var appSignInput by remember(liveConfig.appSign) { mutableStateOf(liveConfig.appSign) }
    var quotaMinsInput by remember(liveConfig.monthlyFreeQuotaMinutes) { mutableStateOf(liveConfig.monthlyFreeQuotaMinutes.toString()) }
    var isSavingConfig by remember { mutableStateOf(false) }

    // Follower Gate Form State
    var minFollowersInput by remember(liveConfig.minFollowersToGoLive) { mutableStateOf(liveConfig.minFollowersToGoLive.toString()) }
    var vipUsernameInput by remember { mutableStateOf("") }
    var vipStatusFeedback by remember { mutableStateOf("") }
    var isUpdatingVip by remember { mutableStateOf(false) }

    // Live Preview Customizer Form State
    var previewTitleInput by remember(liveConfig.previewBannerTitle) { mutableStateOf(liveConfig.previewBannerTitle) }
    var previewSubtitleInput by remember(liveConfig.previewBannerSubtitle) { mutableStateOf(liveConfig.previewBannerSubtitle) }
    var previewImageUrlInput by remember(liveConfig.previewBannerImageUrl) { mutableStateOf(liveConfig.previewBannerImageUrl) }

    var isClearingZombies by remember { mutableStateOf(false) }

    fun persistConfig(updated: DynamicZegoLiveConfig, successMsg: String) {
        if (isSavingConfig) return
        isSavingConfig = true
        scope.launch {
            try {
                val res = SocialIndiaZegoConfig.saveConfigToFirestore(updated)
                if (res.isSuccess) {
                    Toast.makeText(context, successMsg, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Save failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isSavingConfig = false
            }
        }
    }

    val quotaProgress = (liveConfig.usedMinutesThisMonth / liveConfig.monthlyFreeQuotaMinutes.coerceAtLeast(1))
        .toFloat()
        .coerceIn(0f, 1f)
    val quotaColor = when {
        quotaProgress >= 0.95f -> Color(0xFFFF1744)
        quotaProgress >= 0.80f -> Color(0xFFFF9100)
        else -> Color(0xFF00E676)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. MASTER REAL-TIME LIVE CONTROL CARD
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF141726),
            border = BorderStroke(
                1.dp,
                if (liveConfig.isLiveEnabled) Color(0xFF00E676).copy(alpha = 0.45f)
                else Color(0xFFFF1744).copy(alpha = 0.45f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ZEGO Live Streaming Engine",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "Real-time Cloud Sync • Instant Key Rotation",
                            color = Color(0xFFA0A8C2),
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = liveConfig.isLiveEnabled,
                        onCheckedChange = { enabled ->
                            persistConfig(
                                liveConfig.copy(isLiveEnabled = enabled),
                                if (enabled) "Public Live Streaming ENABLED" else "Public Live Streaming PAUSED"
                            )
                        },
                        modifier = Modifier.testTag("admin_live_master_switch")
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AdminLiveMetricPill(
                        label = "ACTIVE LIVES",
                        value = "${activeLives.size}",
                        accent = Color(0xFFFF2A6D),
                        modifier = Modifier.weight(1f)
                    )
                    AdminLiveMetricPill(
                        label = "MIN FOLLOWERS",
                        value = "${liveConfig.minFollowersToGoLive}",
                        accent = Color(0xFF00E5FF),
                        modifier = Modifier.weight(1f)
                    )
                    AdminLiveMetricPill(
                        label = "QUALITY",
                        value = liveConfig.streamQuality.replace("_", " "),
                        accent = Color(0xFFFFD700),
                        modifier = Modifier.weight(1f)
                    )
                }

                val maskedSign = if (liveConfig.appSign.length > 14) {
                    "${liveConfig.appSign.take(8)}••••••${liveConfig.appSign.takeLast(6)}"
                } else liveConfig.appSign
                ZegoStatusRow("Active App ID", "${liveConfig.appId}", Color(0xFF00E5FF))
                ZegoStatusRow("Active App Sign", maskedSign, Color(0xFFB8C0D8))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (isClearingZombies) return@OutlinedButton
                            isClearingZombies = true
                            scope.launch {
                                try {
                                    val cleared = LivePresenceManager.forceEndAllActiveLives()
                                    Toast.makeText(
                                        context,
                                        "Cleared $cleared stuck/active live session(s)",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } finally {
                                    isClearingZombies = false
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("admin_clear_stuck_lives_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.6f))
                    ) {
                        Icon(
                            Icons.Default.CleaningServices,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isClearingZombies) "Clearing..." else "Clear Stuck Lives",
                            color = Color(0xFFFF5252),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onNavigateToDiagnostics,
                        modifier = Modifier.weight(0.8f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B3252))
                    ) {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Diagnostics", fontSize = 12.sp)
                    }
                }
            }
        }

        // 2. 10,000-MINUTE FREE PLAN QUOTA & MULTI-KEY SWITCHER
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, quotaColor.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "10,000-Min Free Plan & Key Rotation",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Auto-tracks live minutes; switch AppID/Sign anytime with 1 tap",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = {
                            val resetProfiles = liveConfig.keyProfiles.map { p ->
                                if (p.id == liveConfig.activeProfileId) p.copy(usedMinutes = 0.0) else p
                            }
                            persistConfig(
                                liveConfig.copy(
                                    usedMinutesThisMonth = 0.0,
                                    keyProfiles = resetProfiles
                                ),
                                "Monthly quota counter reset to 0 mins"
                            )
                        },
                        modifier = Modifier.testTag("admin_reset_quota_button")
                    ) {
                        Text("Reset", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${String.format("%.1f", liveConfig.usedMinutesThisMonth)} / ${liveConfig.monthlyFreeQuotaMinutes} mins used",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = quotaColor
                        )
                        Text(
                            text = "${(quotaProgress * 100).toInt()}%",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = quotaColor
                        )
                    }
                    LinearProgressIndicator(
                        progress = { quotaProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(50)),
                        color = quotaColor,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                    if (quotaProgress >= 0.80f) {
                        Text(
                            text = if (quotaProgress >= 0.95f)
                                "Quota nearly full! Activate a backup ZEGO App ID below immediately."
                            else
                                "80%+ of 10,000 free minutes used — add or switch to a new ZEGO key soon.",
                            color = quotaColor,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Saved ZEGO Key Profiles
                if (liveConfig.keyProfiles.isNotEmpty()) {
                    Text(
                        text = "Saved ZEGO Accounts (${liveConfig.keyProfiles.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    liveConfig.keyProfiles.forEach { profile ->
                        val isActive = profile.id == liveConfig.activeProfileId ||
                            (profile.appId == liveConfig.appId && profile.appSign == liveConfig.appSign)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isActive) Color(0xFF00E676).copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (isActive) Color(0xFF00E676) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = profile.label,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isActive) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF00E676))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("ACTIVE", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                            }
                                        }
                                    }
                                    Text(
                                        text = "AppID: ${profile.appId} • ${String.format("%.0f", profile.usedMinutes)}/${profile.monthlyQuotaMinutes} mins",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (!isActive) {
                                        Button(
                                            onClick = {
                                                persistConfig(
                                                    liveConfig.copy(
                                                        activeProfileId = profile.id,
                                                        appId = profile.appId,
                                                        appSign = profile.appSign,
                                                        monthlyFreeQuotaMinutes = profile.monthlyQuotaMinutes,
                                                        usedMinutesThisMonth = profile.usedMinutes
                                                    ),
                                                    "Switched live engine to ${profile.label}!"
                                                )
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Activate", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (liveConfig.keyProfiles.size > 1 && !isActive) {
                                        IconButton(
                                            onClick = {
                                                val remaining = liveConfig.keyProfiles.filterNot { it.id == profile.id }
                                                persistConfig(
                                                    liveConfig.copy(keyProfiles = remaining),
                                                    "Removed ${profile.label}"
                                                )
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Delete ZEGO Key",
                                                tint = Color(0xFFFF5252),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                // Add / Replace ZEGO AppID & AppSign Form
                Text(
                    text = "Set / Add New ZEGO App ID & App Sign",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                OutlinedTextField(
                    value = keyLabelInput,
                    onValueChange = { keyLabelInput = it },
                    label = { Text("Account Label (e.g. Backup Plan #2)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_zego_label_input"),
                    shape = RoundedCornerShape(12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = appIdInput,
                        onValueChange = { appIdInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("ZEGO App ID") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("admin_zego_appid_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = quotaMinsInput,
                        onValueChange = { quotaMinsInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Plan Mins") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.9f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                OutlinedTextField(
                    value = appSignInput,
                    onValueChange = { appSignInput = it.trim() },
                    label = { Text("ZEGO App Sign (64-char hex key)") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_zego_appsign_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Button(
                    onClick = {
                        val parsedId = appIdInput.trim().toLongOrNull()
                        val cleanedSign = appSignInput.trim()
                        val parsedQuota = quotaMinsInput.trim().toIntOrNull()?.coerceAtLeast(100) ?: 10000
                        if (parsedId == null || parsedId <= 0L) {
                            Toast.makeText(context, "Enter a valid numeric ZEGO App ID", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (cleanedSign.length < 32) {
                            Toast.makeText(context, "App Sign must be at least 32 characters", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val label = keyLabelInput.trim().ifBlank { "ZEGO Plan (${parsedId.toString().takeLast(4)})" }
                        val existingMatch = liveConfig.keyProfiles.find { it.appId == parsedId }
                        val profileId = existingMatch?.id ?: "zego_${UUID.randomUUID().toString().take(8)}"
                        val newProfile = ZegoKeyProfile(
                            id = profileId,
                            label = label,
                            appId = parsedId,
                            appSign = cleanedSign,
                            monthlyQuotaMinutes = parsedQuota,
                            usedMinutes = if (existingMatch != null) existingMatch.usedMinutes else 0.0
                        )
                        val nextProfiles = liveConfig.keyProfiles.filterNot { it.id == profileId } + newProfile
                        persistConfig(
                            liveConfig.copy(
                                activeProfileId = profileId,
                                appId = parsedId,
                                appSign = cleanedSign,
                                keyProfiles = nextProfiles,
                                monthlyFreeQuotaMinutes = parsedQuota,
                                usedMinutesThisMonth = newProfile.usedMinutes
                            ),
                            "ZEGO App ID & Key synced live to all users!"
                        )
                        keyLabelInput = ""
                    },
                    enabled = !isSavingConfig,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("admin_save_zego_key_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2A6D))
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSavingConfig) "Syncing..." else "Save & Activate ZEGO Key Live",
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // 3. FOLLOWER ELIGIBILITY GATE & VIP OVERRIDE CONTROLLER
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF00E5FF))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Live Follower Gate & VIP Access", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        Text(
                            "Decide minimum followers needed to Go Live (locked popup shown below limit)",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Quick Follower Threshold Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0, 50, 100, 500, 1000, 5000, 10000).forEach { preset ->
                        val selected = minFollowersInput.toIntOrNull() == preset
                        FilterChip(
                            selected = selected,
                            onClick = { minFollowersInput = preset.toString() },
                            label = {
                                Text(
                                    text = if (preset == 0) "Open (0)" else "$preset+",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = minFollowersInput,
                        onValueChange = { minFollowersInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Minimum Followers Required") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("admin_min_followers_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Button(
                        onClick = {
                            val req = minFollowersInput.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
                            persistConfig(
                                liveConfig.copy(minFollowersToGoLive = req),
                                "Minimum followers to Go Live set to $req"
                            )
                        },
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("admin_save_min_followers_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }

                FeatureFlagSwitch(
                    title = "Verified Creators Bypass Gate",
                    subtitle = "Blue-tick verified creators can Go Live at any follower count",
                    checked = liveConfig.verifiedBypassFollowerGate
                ) { bypass ->
                    persistConfig(
                        liveConfig.copy(verifiedBypassFollowerGate = bypass),
                        if (bypass) "Verified creators bypass enabled" else "Verified creators must meet follower limit"
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                // Per-User VIP Whitelist / Restrict
                Text("Per-User Live VIP / Restrict Override", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                OutlinedTextField(
                    value = vipUsernameInput,
                    onValueChange = { vipUsernameInput = it.trim() },
                    label = { Text("Enter @username (without @)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_vip_username_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                fun applyUserLiveOverride(overrideVal: Boolean?, label: String) {
                    val target = vipUsernameInput.trim().removePrefix("@")
                    if (target.isBlank()) {
                        Toast.makeText(context, "Enter a username first", Toast.LENGTH_SHORT).show()
                        return
                    }
                    if (isUpdatingVip) return
                    isUpdatingVip = true
                    scope.launch {
                        try {
                            val snap = RepositoryManager.firestore.collection("users")
                                .whereEqualTo("username", target)
                                .limit(1)
                                .get()
                                .await()
                            val doc = snap.documents.firstOrNull()
                            if (doc == null) {
                                vipStatusFeedback = "User @$target not found"
                            } else {
                                if (overrideVal == null) {
                                    doc.reference.update("liveAccessOverride", FieldValue.delete()).await()
                                } else {
                                    doc.reference.set(mapOf("liveAccessOverride" to overrideVal), SetOptions.merge()).await()
                                }
                                vipStatusFeedback = "@$target -> $label"
                                Toast.makeText(context, vipStatusFeedback, Toast.LENGTH_SHORT).show()
                            }
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (e: Exception) {
                            vipStatusFeedback = "Error: ${e.message}"
                        } finally {
                            isUpdatingVip = false
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { applyUserLiveOverride(true, "VIP Live Access Granted") },
                        enabled = !isUpdatingVip,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("admin_grant_live_vip_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
                    ) {
                        Text("Grant VIP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { applyUserLiveOverride(false, "Live Access Restricted") },
                        enabled = !isUpdatingVip,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFFF5252))
                    ) {
                        Text("Restrict", color = Color(0xFFFF5252), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = { applyUserLiveOverride(null, "Default Follower Rule") },
                        enabled = !isUpdatingVip
                    ) {
                        Text("Reset", fontSize = 12.sp)
                    }
                }
                if (vipStatusFeedback.isNotBlank()) {
                    Text(
                        text = vipStatusFeedback,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 4. LIVE PREVIEW BANNER & STUDIO CUSTOMIZER
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Live Streaming Preview & Studio Customizer", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Text(
                    "Change the Live Preview banner, title, image & stream features in real time",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Live Preview Card (shows Admin how users see it)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(118.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF280B3B), Color(0xFF141834), Color(0xFF0B253A))
                            )
                        )
                        .border(1.dp, Color(0xFFFF2A6D).copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                ) {
                    if (previewImageUrlInput.isNotBlank()) {
                        AsyncImage(
                            model = previewImageUrlInput,
                            contentDescription = "Live Preview Banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f))
                        )
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF2A6D))
                            )
                            Text(
                                text = "LIVE PREVIEW • ${liveConfig.streamQuality.replace("_", " ")}",
                                color = Color(0xFFFF80AB),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(
                            text = previewTitleInput.ifBlank { "Social India Live Studio" },
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = previewSubtitleInput.ifBlank { "Go Live in Real-Time HD with Fans & Gifts" },
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                OutlinedTextField(
                    value = previewTitleInput,
                    onValueChange = { previewTitleInput = it },
                    label = { Text("Live Preview Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = previewSubtitleInput,
                    onValueChange = { previewSubtitleInput = it },
                    label = { Text("Live Preview Subtitle") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = previewImageUrlInput,
                    onValueChange = { previewImageUrlInput = it.trim() },
                    label = { Text("Preview Banner Image URL (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Button(
                    onClick = {
                        persistConfig(
                            liveConfig.copy(
                                previewBannerTitle = previewTitleInput.trim().ifBlank { "Social India Live Studio" },
                                previewBannerSubtitle = previewSubtitleInput.trim().ifBlank { "Go Live in Real-Time HD with Fans, 3D Gifts & Co-Hosts" },
                                previewBannerImageUrl = previewImageUrlInput.trim()
                            ),
                            "Live Preview banner updated for all users!"
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("admin_save_preview_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Update Live Preview Banner", fontWeight = FontWeight.Bold)
                }

                // Quality Selector
                Text("Default Broadcast Resolution", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("SD_480P", "HD_720P", "FHD_1080P").forEach { q ->
                        val isSelected = liveConfig.streamQuality == q
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                persistConfig(liveConfig.copy(streamQuality = q), "Stream quality set to ${q.replace("_", " ")}")
                            },
                            label = { Text(q.replace("_", " "), fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                FeatureFlagSwitch(
                    title = "Multi-Guest Co-Hosting",
                    subtitle = "Allow hosts to invite audience members on-stage",
                    checked = liveConfig.allowCoHosting
                ) { v -> persistConfig(liveConfig.copy(allowCoHosting = v), "Co-Hosting updated") }

                FeatureFlagSwitch(
                    title = "Live 3D Gifts & Coins",
                    subtitle = "Enable real-time animated gifts inside Live rooms",
                    checked = liveConfig.allowLiveGifts
                ) { v -> persistConfig(liveConfig.copy(allowLiveGifts = v), "Live Gifts updated") }

                FeatureFlagSwitch(
                    title = "Live Audience Comments",
                    subtitle = "Allow real-time chat messages during broadcasts",
                    checked = liveConfig.allowLiveComments
                ) { v -> persistConfig(liveConfig.copy(allowLiveComments = v), "Live Comments updated") }

                FeatureFlagSwitch(
                    title = "Studio Beauty & Video Filters",
                    subtitle = "Enable camera enhancement effects for Live Hosts",
                    checked = liveConfig.allowBeautyFilters
                ) { v -> persistConfig(liveConfig.copy(allowBeautyFilters = v), "Beauty Filters updated") }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AdminLiveMetricPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text(label, color = accent, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, letterSpacing = 0.6.sp)
        }
    }
}

@Composable
private fun ZegoStatusRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.5.sp, color = Color(0xFF9EA6C0))
        Text(value, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
private fun FeatureFlagSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
            Text(subtitle, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
