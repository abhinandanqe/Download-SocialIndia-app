package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.repository.ReferralConfig
import com.example.repository.ReferralRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AdminReferralTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val liveConfig by ReferralRepository.listenReferralConfig().collectAsStateWithLifecycle(initialValue = ReferralConfig())
    val allClaims by ReferralRepository.listenAllReferralClaimsForAdmin(50L).collectAsStateWithLifecycle(initialValue = emptyList())

    var isEnabled by remember(liveConfig.updatedAt) { mutableStateOf(liveConfig.isEnabled) }
    var referrerCoinsInput by remember(liveConfig.updatedAt) { mutableStateOf(liveConfig.referrerRewardCoins.toString()) }
    var refereeCoinsInput by remember(liveConfig.updatedAt) { mutableStateOf(liveConfig.refereeBonusCoins.toString()) }
    var dailyLimitInput by remember(liveConfig.updatedAt) { mutableStateOf(liveConfig.dailyReferralLimit.toString()) }
    var bannerTitleInput by remember(liveConfig.updatedAt) { mutableStateOf(liveConfig.bannerTitle) }
    var bannerSubtitleInput by remember(liveConfig.updatedAt) { mutableStateOf(liveConfig.bannerSubtitle) }
    var isSaving by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val totalCoinsDistributed = remember(allClaims) {
        allClaims.sumOf { it.referrerCoins + it.refereeCoins }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "referral_summary") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = Color(0xFFFFD54F))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Referral & Coin Rewards Control", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Total Claims", color = Color.Gray, fontSize = 11.sp)
                            Text("${allClaims.size}", color = Color(0xFF00E5FF), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Coins Awarded", color = Color.Gray, fontSize = 11.sp)
                            Text("$totalCoinsDistributed", color = Color(0xFF00E676), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        }
                    }
                }
            }
        }

        item(key = "referral_config_form") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1F2E))
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
                        Column {
                            Text("Referral Program Active", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Enable or pause code redemptions globally", color = Color.Gray, fontSize = 12.sp)
                        }
                        Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = referrerCoinsInput,
                            onValueChange = { referrerCoinsInput = it.filter { ch -> ch.isDigit() }.take(6) },
                            label = { Text("Referrer Coins") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = refereeCoinsInput,
                            onValueChange = { refereeCoinsInput = it.filter { ch -> ch.isDigit() }.take(6) },
                            label = { Text("Friend Bonus") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = dailyLimitInput,
                        onValueChange = { dailyLimitInput = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("Daily Invite Limit per User") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = bannerTitleInput,
                        onValueChange = { bannerTitleInput = it.take(80) },
                        label = { Text("Promo Banner Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = bannerSubtitleInput,
                        onValueChange = { bannerSubtitleInput = it.take(150) },
                        label = { Text("Promo Banner Subtitle") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            isSaving = true
                            scope.launch {
                                val updated = ReferralConfig(
                                    isEnabled = isEnabled,
                                    referrerRewardCoins = referrerCoinsInput.toLongOrNull() ?: 100L,
                                    refereeBonusCoins = refereeCoinsInput.toLongOrNull() ?: 50L,
                                    dailyReferralLimit = dailyLimitInput.toIntOrNull() ?: 20,
                                    bannerTitle = bannerTitleInput,
                                    bannerSubtitle = bannerSubtitleInput
                                )
                                val res = ReferralRepository.saveReferralConfig(updated)
                                isSaving = false
                                if (res.isSuccess) {
                                    Toast.makeText(context, "Referral settings saved!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Save failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52))
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Referral Settings", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item(key = "claims_ledger_header") {
            Text(
                text = "Recent Referral Claims (${allClaims.size})",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        if (allClaims.isEmpty()) {
            item(key = "no_claims_admin") {
                Text("No referral claims recorded yet.", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            items(allClaims, key = { it.id }) { claim ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "@${claim.refereeUsername} joined via @${claim.referrerUsername}",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Code: ${claim.referralCode} • ${dateFormatter.format(Date(claim.timestamp))}",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            text = "+${claim.referrerCoins}/${claim.refereeCoins}",
                            color = Color(0xFF00E676),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}
