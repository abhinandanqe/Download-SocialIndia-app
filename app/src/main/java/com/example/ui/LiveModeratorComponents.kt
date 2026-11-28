@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Comment
import com.example.utils.NumberFormatUtils
import com.example.utils.SoundManager

// MARK: - Data Models for Analytics & Moderation

data class CreatorLiveStats(
    val title: String = "Live Video",
    val durationSeconds: Long = 0,
    val peakViewers: Int = 1,
    val totalViewers: Int = 1,
    val totalWatchTimeMinutes: Long = 0,
    val followersGained: Int = 0,
    val totalLikes: Int = 0,
    val totalComments: Int = 0,
    val totalShares: Int = 0,
    val giftsCount: Int = 0,
    val coinsEarned: Int = 0
)

data class LiveGifter(
    val userId: String,
    val username: String,
    val avatarUrl: String,
    val coinsContributed: Int,
    val topGiftEmoji: String = "💎"
)

// MARK: - Moderator Tools Bottom Sheet

@Composable
fun LiveModeratorBottomSheet(
    selectedComment: Comment?,
    isChatEnabled: Boolean,
    isSlowModeEnabled: Boolean,
    onToggleChat: (Boolean) -> Unit,
    onToggleSlowMode: (Boolean) -> Unit,
    onPinComment: (Comment) -> Unit,
    onMuteUser: (userId: String, username: String) -> Unit,
    onKickUser: (userId: String, username: String) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.3f), CircleShape)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = Color(0xFF00F0FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Host & Moderator Tools",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action on Specific Comment / User if selected
            if (selectedComment != null) {
                Surface(
                    color = Color(0xFF252525),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Selected Comment by @${selectedComment.username}",
                            color = Color(0xFF00F0FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "\"${selectedComment.text}\"",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Pin Comment
                ModeratorActionRow(
                    icon = Icons.Default.PushPin,
                    title = "Pin Comment to Top",
                    description = "Highlights this comment at the top for all viewers",
                    color = Color(0xFFFFD700)
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    SoundManager.playClickSound()
                    onPinComment(selectedComment)
                    onDismiss()
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Mute User
                ModeratorActionRow(
                    icon = Icons.Default.VolumeMute,
                    title = "Mute @${selectedComment.username}",
                    description = "Temporarily prevents user from sending comments in this live",
                    color = Color(0xFFFF9100)
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    SoundManager.playClickSound()
                    onMuteUser(selectedComment.userId, selectedComment.username)
                    onDismiss()
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Kick User
                ModeratorActionRow(
                    icon = Icons.Default.Block,
                    title = "Kick @${selectedComment.username} from Room",
                    description = "Removes this user from the live broadcast immediately",
                    color = Color(0xFFFE2C55)
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    SoundManager.playClickSound()
                    onKickUser(selectedComment.userId, selectedComment.username)
                    onDismiss()
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Global Stream Moderation Controls
            Text(
                text = "Stream Chat Controls",
                color = Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Toggle Comments Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF252525), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Allow Viewer Comments",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (isChatEnabled) "Comments are live" else "Comments are currently paused",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = isChatEnabled,
                    onCheckedChange = { onToggleChat(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF00C853)
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Slow Mode Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF252525), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Slow Mode (10s delay)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Slows down fast scrolling comments",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = isSlowModeEnabled,
                    onCheckedChange = { onToggleSlowMode(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF00F0FF)
                    )
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ModeratorActionRow(
    icon: ImageVector,
    title: String,
    description: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF252525),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(text = description, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
        }
    }
}

// MARK: - Creator Dashboard & Analytics Summary Dialog / Screen

@Composable
fun CreatorLiveAnalyticsDialog(
    stats: CreatorLiveStats,
    topGifters: List<LiveGifter> = emptyList(),
    onDismiss: () -> Unit
) {
    val minutes = stats.durationSeconds / 60
    val seconds = stats.durationSeconds % 60
    val formattedDuration = String.format("%02d:%02d", minutes, seconds)

    var selectedTab by remember { mutableStateOf(0) } // 0: Analytics, 1: Top Gifters

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1C),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = Color(0xFFFE2C55).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFE2C55).copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "LIVE STUDIO SUMMARY",
                        color = Color(0xFFFE2C55),
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Live Stream Insights",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Text(
                    text = "Broadcast duration: $formattedDuration",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Tabs: Overview vs Gifters
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF262626),
                    contentColor = Color.White,
                    divider = {},
                    indicator = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "Analytics",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 0) Color.White else Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "Top Gifters 🎁",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 1) Color(0xFFFFD700) else Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
            ) {
                if (selectedTab == 0) {
                    // Analytics Grid
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AnalyticsMetricCard(
                                    label = "Total Viewers",
                                    value = NumberFormatUtils.formatCount(stats.totalViewers),
                                    icon = Icons.Default.People,
                                    tint = Color(0xFF00F0FF),
                                    modifier = Modifier.weight(1f)
                                )
                                AnalyticsMetricCard(
                                    label = "Peak Viewers",
                                    value = NumberFormatUtils.formatCount(stats.peakViewers),
                                    icon = Icons.Default.Visibility,
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AnalyticsMetricCard(
                                    label = "Watch Time",
                                    value = "${stats.totalWatchTimeMinutes} mins",
                                    icon = Icons.Default.Timer,
                                    tint = Color(0xFF7C4DFF),
                                    modifier = Modifier.weight(1f)
                                )
                                AnalyticsMetricCard(
                                    label = "New Followers",
                                    value = "+${stats.followersGained}",
                                    icon = Icons.Default.PersonAdd,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AnalyticsMetricCard(
                                    label = "Total Likes",
                                    value = NumberFormatUtils.formatCount(stats.totalLikes),
                                    icon = Icons.Default.Favorite,
                                    tint = Color(0xFFFE2C55),
                                    modifier = Modifier.weight(1f)
                                )
                                AnalyticsMetricCard(
                                    label = "Comments",
                                    value = NumberFormatUtils.formatCount(stats.totalComments),
                                    icon = Icons.Default.Comment,
                                    tint = Color(0xFF40C4FF),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AnalyticsMetricCard(
                                    label = "Total Shares",
                                    value = NumberFormatUtils.formatCount(stats.totalShares),
                                    icon = Icons.Default.Share,
                                    tint = Color(0xFFFF9100),
                                    modifier = Modifier.weight(1f)
                                )
                                AnalyticsMetricCard(
                                    label = "Gift Coins",
                                    value = "${stats.coinsEarned}",
                                    icon = Icons.Default.MonetizationOn,
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.weight(1f),
                                    trailingCoinIcon = true
                                )
                            }
                        }
                    }
                } else {
                    // Top Gifters Leaderboard
                    val giftersList = if (topGifters.isNotEmpty()) topGifters else listOf(
                        LiveGifter("1", "crypto_star", "https://api.dicebear.com/7.x/avataaars/png?seed=1", 1200, "👑"),
                        LiveGifter("2", "sarah_dance", "https://api.dicebear.com/7.x/avataaars/png?seed=2", 850, "💎"),
                        LiveGifter("3", "alex_live", "https://api.dicebear.com/7.x/avataaars/png?seed=3", 400, "🚀"),
                        LiveGifter("4", "priya_art", "https://api.dicebear.com/7.x/avataaars/png?seed=4", 250, "🌹")
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(giftersList) { gifter ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF252525), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = gifter.topGiftEmoji,
                                    fontSize = 20.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                AsyncImage(
                                    model = gifter.avatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = gifter.username,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Sent ${gifter.coinsContributed} ",
                                            color = Color(0xFFFFD700),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        com.example.ui.components.CoinIcon(size = 12.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun AnalyticsMetricCard(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    trailingCoinIcon: Boolean = false
) {
    Surface(
        color = Color(0xFF242424),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = label,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (trailingCoinIcon) {
                    Spacer(modifier = Modifier.width(4.dp))
                    com.example.ui.components.CoinIcon(size = 16.dp)
                }
            }
        }
    }
}
