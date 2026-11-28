package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notification.SocialIndiaNotificationManager
import com.example.notification.SocialIndiaNotificationPreferences
import com.example.notification.SocialIndiaNotificationSoundManager

/**
 * Full Notification Settings Experience for Social India.
 * Allows granular control over Push, In-App alerts, Sounds (Social India chime/Default/Silent),
 * Message Previews, Vibration, Badges, and individual category preferences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var pushEnabled by remember { mutableStateOf(SocialIndiaNotificationPreferences.isPushEnabled(context)) }
    var inAppEnabled by remember { mutableStateOf(SocialIndiaNotificationPreferences.isInAppEnabled(context)) }
    var soundMode by remember { mutableStateOf(SocialIndiaNotificationPreferences.getSoundMode(context)) }
    var vibrationEnabled by remember { mutableStateOf(SocialIndiaNotificationPreferences.isVibrationEnabled(context)) }
    var previewEnabled by remember { mutableStateOf(SocialIndiaNotificationPreferences.isMessagePreviewEnabled(context)) }
    var badgeEnabled by remember { mutableStateOf(SocialIndiaNotificationPreferences.isBadgeEnabled(context)) }
    var msgSoundsEnabled by remember { mutableStateOf(com.example.utils.MessengerSoundManager.isMessageSoundsEnabled(context)) }
    var globalMuteEnabled by remember { mutableStateOf(com.example.utils.MessengerSoundManager.isGlobalMute(context)) }

    // Categories
    var likesEnabled by remember { mutableStateOf(SocialIndiaNotificationPreferences.isCategoryEnabled(context, "likes")) }
    var commentsEnabled by remember { mutableStateOf(SocialIndiaNotificationPreferences.isCategoryEnabled(context, "comments")) }
    var followsEnabled by remember { mutableStateOf(SocialIndiaNotificationPreferences.isCategoryEnabled(context, "follows")) }
    var mentionsEnabled by remember { mutableStateOf(SocialIndiaNotificationPreferences.isCategoryEnabled(context, "mentions")) }
    var messagesEnabled by remember { mutableStateOf(SocialIndiaNotificationPreferences.isCategoryEnabled(context, "messages")) }
    var liveEnabled by remember { mutableStateOf(SocialIndiaNotificationPreferences.isCategoryEnabled(context, "live")) }
    var creatorEnabled by remember { mutableStateOf(SocialIndiaNotificationPreferences.isCategoryEnabled(context, "creator")) }
    var securityEnabled by remember { mutableStateOf(SocialIndiaNotificationPreferences.isCategoryEnabled(context, "security")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notification Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            // Section: MASTER CONTROLS
            SettingSectionHeader("GENERAL NOTIFICATIONS")

            SwitchSettingItem(
                title = "Push Notifications",
                subtitle = "Receive notifications on this device",
                icon = Icons.Outlined.NotificationsActive,
                checked = pushEnabled,
                onCheckedChange = {
                    pushEnabled = it
                    SocialIndiaNotificationPreferences.setPushEnabled(context, it)
                }
            )

            SwitchSettingItem(
                title = "In-App Alerts",
                subtitle = "Show notification banners while using the app",
                icon = Icons.Outlined.AppShortcut,
                checked = inAppEnabled,
                onCheckedChange = {
                    inAppEnabled = it
                    SocialIndiaNotificationPreferences.setInAppEnabled(context, it)
                }
            )

            SwitchSettingItem(
                title = "Notification Badge",
                subtitle = "Show unread badges on app icon & tabs",
                icon = Icons.Outlined.MarkChatUnread,
                checked = badgeEnabled,
                onCheckedChange = {
                    badgeEnabled = it
                    SocialIndiaNotificationPreferences.setBadgeEnabled(context, it)
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

            // Section: SOUND & HAPTICS
            SettingSectionHeader("SOUND & HAPTICS")

            Text(
                text = "Notification Sound",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val soundOptions = listOf("social_india" to "Social India", "default" to "Default", "silent" to "Silent")
                soundOptions.forEach { (mode, label) ->
                    val isSelected = soundMode == mode
                    OutlinedButton(
                        onClick = {
                            soundMode = mode
                            SocialIndiaNotificationPreferences.setSoundMode(context, mode)
                            SocialIndiaNotificationManager.setupNotificationChannels(context)
                            if (mode == "social_india") {
                                SocialIndiaNotificationSoundManager.playInAppNotificationSound(context)
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            // Preview Sound button
            if (soundMode == "social_india") {
                TextButton(
                    onClick = { SocialIndiaNotificationSoundManager.playInAppNotificationSound(context) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Preview Social India Chime", fontSize = 12.sp)
                }
            }

            SwitchSettingItem(
                title = "Vibration",
                subtitle = "Vibrate on notifications and haptics",
                icon = Icons.Outlined.Vibration,
                checked = vibrationEnabled,
                onCheckedChange = {
                    vibrationEnabled = it
                    SocialIndiaNotificationPreferences.setVibrationEnabled(context, it)
                    SocialIndiaNotificationManager.setupNotificationChannels(context)
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

            // Section: MESSENGER SOUNDS & PRIVACY
            SettingSectionHeader("MESSENGER CHAT & SOUNDS")

            SwitchSettingItem(
                title = "In-Chat Message Sounds",
                subtitle = "Subtle sound for outgoing & incoming chat messages",
                icon = Icons.Outlined.VolumeUp,
                checked = msgSoundsEnabled,
                onCheckedChange = {
                    msgSoundsEnabled = it
                    com.example.utils.MessengerSoundManager.setMessageSoundsEnabled(context, it)
                }
            )

            SwitchSettingItem(
                title = "Global Mute",
                subtitle = "Silence all in-app sounds and message alerts completely",
                icon = Icons.Outlined.VolumeOff,
                checked = globalMuteEnabled,
                onCheckedChange = {
                    globalMuteEnabled = it
                    com.example.utils.MessengerSoundManager.setGlobalMute(context, it)
                }
            )

            SwitchSettingItem(
                title = "Show Message Preview",
                subtitle = "Display sender and message text in notifications",
                icon = Icons.Outlined.Visibility,
                checked = previewEnabled,
                onCheckedChange = {
                    previewEnabled = it
                    SocialIndiaNotificationPreferences.setMessagePreviewEnabled(context, it)
                    com.example.utils.MessengerSoundManager.setPreviewEnabled(context, it)
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

            // Section: ACTIVITY CATEGORIES
            SettingSectionHeader("ACTIVITY NOTIFICATIONS")

            SwitchSettingItem(
                title = "Likes & Reactions",
                subtitle = "When people like your posts, reels, or stories",
                icon = Icons.Outlined.FavoriteBorder,
                checked = likesEnabled,
                onCheckedChange = {
                    likesEnabled = it
                    SocialIndiaNotificationPreferences.setCategoryEnabled(context, "likes", it)
                }
            )

            SwitchSettingItem(
                title = "Comments & Replies",
                subtitle = "When someone comments on your content",
                icon = Icons.Outlined.ChatBubbleOutline,
                checked = commentsEnabled,
                onCheckedChange = {
                    commentsEnabled = it
                    SocialIndiaNotificationPreferences.setCategoryEnabled(context, "comments", it)
                }
            )

            SwitchSettingItem(
                title = "New Followers & Requests",
                subtitle = "When someone follows you or requests to follow",
                icon = Icons.Outlined.PersonAdd,
                checked = followsEnabled,
                onCheckedChange = {
                    followsEnabled = it
                    SocialIndiaNotificationPreferences.setCategoryEnabled(context, "follows", it)
                }
            )

            SwitchSettingItem(
                title = "Mentions & Tags",
                subtitle = "When you are mentioned in posts, stories, or comments",
                icon = Icons.Outlined.AlternateEmail,
                checked = mentionsEnabled,
                onCheckedChange = {
                    mentionsEnabled = it
                    SocialIndiaNotificationPreferences.setCategoryEnabled(context, "mentions", it)
                }
            )

            SwitchSettingItem(
                title = "Direct & Group Messages",
                subtitle = "Alerts for incoming chat messages",
                icon = Icons.Outlined.Mail,
                checked = messagesEnabled,
                onCheckedChange = {
                    messagesEnabled = it
                    SocialIndiaNotificationPreferences.setCategoryEnabled(context, "messages", it)
                }
            )

            SwitchSettingItem(
                title = "Live Broadcasts",
                subtitle = "When accounts you follow start streaming live",
                icon = Icons.Outlined.LiveTv,
                checked = liveEnabled,
                onCheckedChange = {
                    liveEnabled = it
                    SocialIndiaNotificationPreferences.setCategoryEnabled(context, "live", it)
                }
            )

            SwitchSettingItem(
                title = "Creator & Milestones",
                subtitle = "Insights, reach milestones, and monetization updates",
                icon = Icons.Outlined.StarOutline,
                checked = creatorEnabled,
                onCheckedChange = {
                    creatorEnabled = it
                    SocialIndiaNotificationPreferences.setCategoryEnabled(context, "creator", it)
                }
            )

            SwitchSettingItem(
                title = "Security & Login Alerts",
                subtitle = "New logins, password updates, and session changes",
                icon = Icons.Outlined.Shield,
                checked = securityEnabled,
                onCheckedChange = {
                    securityEnabled = it
                    SocialIndiaNotificationPreferences.setCategoryEnabled(context, "security", it)
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingSectionHeader(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun SwitchSettingItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}
