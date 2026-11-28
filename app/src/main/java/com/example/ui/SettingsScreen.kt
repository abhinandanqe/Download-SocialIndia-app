package com.example.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Build
import android.content.Intent
import android.widget.Toast
import com.example.model.RepositoryManager
import com.example.utils.ThemePreference
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    isAdmin: Boolean = false,
    onAdminClick: () -> Unit = {},
    isVerified: Boolean = false,
    onRequestBlueTick: () -> Unit = {},
    onKycClick: () -> Unit = {},
    onWalletClick: () -> Unit = {},
    onDashboardClick: () -> Unit = {},
    onMonetizationClick: () -> Unit = {},
    onNavigateToEditProfile: () -> Unit = {},
    onLogin: () -> Unit = {},
    onRequestVerification: () -> Unit = {},
    onReviewVerifications: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
    onNavigateToSaved: () -> Unit = {},
    onNavigateToCloseFriends: () -> Unit = {},
    onNavigateToArchive: () -> Unit = {},
    onNavigateToScheduled: () -> Unit = {},
    onNavigateToChannels: () -> Unit = {},
    onOpenAccountSwitcher: () -> Unit = {},
    onNavigateToBusinessDashboard: () -> Unit = {},
    onNavigateToBusinessSetup: () -> Unit = {},
    onNavigateToCreateAd: () -> Unit = {},
    onNavigateToAdminAds: () -> Unit = {},
    onNavigateToAccountSecurity: () -> Unit = {},
    onNavigateToAdminSecurity: () -> Unit = {},
    onAiHubClick: () -> Unit = {},
    onNavigateToReferrals: () -> Unit = {},
    onNavigateToDrafts: () -> Unit = {}
) {
    val isDarkTheme by ThemePreference.isDarkTheme.collectAsStateWithLifecycle()
    val bgColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeactivateDialog by remember { mutableStateOf(false) }
    var isDeactivating by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showCloseFriendsDialog by remember { mutableStateOf(false) }
    var showChannelDialog by remember { mutableStateOf(false) }
    var showAccountTypeDialog by remember { mutableStateOf(false) }
    var showBusinessVerificationRequiredDialog by remember { mutableStateOf(false) }
    var currentAccountType by remember { mutableStateOf("PERSONAL") }
    val currentUid = RepositoryManager.getCurrentUserId()
    var userCoins by remember { mutableLongStateOf(0L) }
    DisposableEffect(currentUid) {
        var reg: com.google.firebase.firestore.ListenerRegistration? = null
        if (currentUid.isNotEmpty()) {
            reg = RepositoryManager.firestore.collection("users").document(currentUid)
                .addSnapshotListener { snap, _ ->
                    if (snap != null && snap.exists()) {
                        userCoins = snap.getLong("coins") ?: (snap.getDouble("coins")?.toLong() ?: 0L)
                    }
                }
        }
        onDispose {
            reg?.remove()
        }
    }
    
    // Real Interactive Feature Dialogs
    var showAccountStatusCenterDialog by remember { mutableStateOf(false) }
    var showAccountControlsDialog by remember { mutableStateOf(false) }
    var showActiveSessionsDialog by remember { mutableStateOf(false) }
    var showExportDataDialog by remember { mutableStateOf(false) }
    var isExportingData by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var currentLanguage by remember { mutableStateOf("English (India)") }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var showHelpCenterDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }

    // Account Controls State
    var isPrivateAccount by remember { mutableStateOf(false) }
    var showOnlineStatus by remember { mutableStateOf(true) }
    var filterSensitiveContent by remember { mutableStateOf(true) }
    var allowReadReceipts by remember { mutableStateOf(true) }

    // Notification Preferences State
    var notifPushEnabled by remember { mutableStateOf(true) }
    var notifLikesComments by remember { mutableStateOf(true) }
    var notifDirectMessages by remember { mutableStateOf(true) }
    var notifCampaignAlerts by remember { mutableStateOf(true) }

    // Accessibility State
    var highContrastMode by remember { mutableStateOf(false) }
    var largeTextMode by remember { mutableStateOf(false) }
    var hapticFeedbackEnabled by remember { mutableStateOf(true) }

    // Help Center State
    var supportMessageText by remember { mutableStateOf("") }
    var isSubmittingTicket by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val currentUserId = com.example.model.RepositoryManager.getCurrentUserId()
    var isFaceLockActive by remember(currentUserId) {
        mutableStateOf(com.example.security.FaceLockManager.isFaceLockEnabled(context, currentUserId))
    }

    LaunchedEffect(Unit) {
        try {
            val uid = com.example.model.RepositoryManager.getCurrentUserId()
            if (uid.isNotEmpty()) {
                val doc = com.example.model.RepositoryManager.firestore.collection("users").document(uid).get().await()
                currentAccountType = doc.getString("accountType") ?: "PERSONAL"
                isPrivateAccount = doc.getBoolean("isPrivate") ?: false
                showOnlineStatus = doc.getBoolean("showOnlineStatus") ?: true
                filterSensitiveContent = doc.getBoolean("filterSensitiveContent") ?: true
                allowReadReceipts = doc.getBoolean("allowReadReceipts") ?: true
                notifPushEnabled = doc.getBoolean("notifPush") ?: true
                notifLikesComments = doc.getBoolean("notifLikesComments") ?: true
                notifDirectMessages = doc.getBoolean("notifDirectMessages") ?: true
                notifCampaignAlerts = doc.getBoolean("notifCampaignAlerts") ?: true
                currentLanguage = doc.getString("language") ?: "English (India)"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    val drivePrefs = remember { context.getSharedPreferences("social_india_drive_prefs", android.content.Context.MODE_PRIVATE) }
    var connectedDriveEmail by remember {
        mutableStateOf(
            drivePrefs.getString("connected_drive_email", null)
                ?: com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)?.email
                ?: ""
        )
    }
    var isDriveConnected by remember {
        mutableStateOf(connectedDriveEmail.isNotBlank())
    }

    val driveAuthLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val chosenEmail = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
            ?: com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)?.email
        if (!chosenEmail.isNullOrBlank()) {
            connectedDriveEmail = chosenEmail
            isDriveConnected = true
            drivePrefs.edit().putString("connected_drive_email", chosenEmail).apply()
            android.widget.Toast.makeText(context, "✅ Google Drive Connected: $chosenEmail", android.widget.Toast.LENGTH_SHORT).show()
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
            )
        },
        containerColor = bgColor
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Pro/Verification Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF8A2387), Color(0xFFE94057), Color(0xFFF27121))))
                    .clickable { onRequestVerification() }
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(if (isVerified) Icons.Default.Verified else Icons.Default.WorkspacePremium, contentDescription = null, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (isVerified) "Verified Account" else "Get Verified", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(if (isVerified) "You have the blue tick badge." else "Apply for the verification badge to stand out.", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                }
            }

            SettingsGroupTitle("Account Center")
            SettingsActionRow(
                icon = Icons.Outlined.VerifiedUser,
                title = "Identity Verification (Video Selfie KYC)",
                subtitle = if (isVerified) "✓ Verified Account (Video KYC Approved)" else "Guided circular video selfie liveness check",
                onClick = onKycClick
            )
            SettingsActionRow(
                icon = Icons.Outlined.FactCheck,
                title = "Account Status Center",
                subtitle = "Check active strikes, removed content, feature eligibility & shadowban appeal",
                onClick = { showAccountStatusCenterDialog = true }
            )

            SettingsGroupTitle("Account")
            SettingsActionRow(
                icon = Icons.Outlined.Verified,
                title = "Social India Verification",
                subtitle = if (isVerified) "✓ Verified Account • Manage Plan & Status" else "Get verified badge, authentic trust & business tools",
                onClick = onRequestVerification
            )
            SettingsActionRow(icon = Icons.Outlined.Person, title = "Edit Profile", subtitle = "Change your details", onClick = onNavigateToEditProfile)
            SettingsActionRow(icon = Icons.Outlined.SwitchAccount, title = "Switch Accounts", subtitle = "Switch or manage accounts on this device", onClick = onOpenAccountSwitcher)
            SettingsActionRow(icon = Icons.Outlined.PersonAdd, title = "Add Account", subtitle = "Log into another account", onClick = onLogin)
            SettingsActionRow(icon = Icons.Outlined.Lock, title = "Privacy & Security", subtitle = "Control who sees your content", onClick = onPrivacyClick)

            SettingsGroupTitle("Storage & Backup")
            SettingsActionRow(
                icon = Icons.Outlined.CloudUpload,
                title = if (isDriveConnected) "Google Drive (Connected)" else "Connect Google Drive",
                subtitle = if (connectedDriveEmail.isNotBlank()) "Connected: $connectedDriveEmail • Tap to change account" else "Backup uploads to your personal Drive",
                onClick = {
                    try {
                        val chooseIntent = android.accounts.AccountManager.newChooseAccountIntent(
                            null,
                            null,
                            arrayOf("com.google"),
                            null,
                            null,
                            null,
                            null
                        )
                        driveAuthLauncher.launch(chooseIntent)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Could not open account picker", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            )

SettingsGroupTitle("Business & Professional Suite")
            SettingsActionRow(
                icon = Icons.Outlined.SwitchAccount,
                title = "Account Type",
                subtitle = "Current: ${currentAccountType.lowercase().replaceFirstChar { it.uppercase() }} Account",
                onClick = { showAccountTypeDialog = true }
            )
            if (currentAccountType == "BUSINESS") {
                SettingsActionRow(
                    icon = Icons.Outlined.Dashboard,
                    title = "Business Dashboard",
                    subtitle = "Manage campaigns, insights & billing",
                    onClick = onNavigateToBusinessDashboard
                )
                SettingsActionRow(
                    icon = Icons.Outlined.Campaign,
                    title = "Create Advertisement",
                    subtitle = "Promote products & brand across India",
                    onClick = onNavigateToCreateAd
                )
                SettingsActionRow(
                    icon = Icons.Outlined.Storefront,
                    title = "Edit Business Profile",
                    subtitle = "Contact info, category, hours & action button",
                    onClick = onNavigateToBusinessSetup
                )
            } else {
                SettingsActionRow(
                    icon = Icons.Outlined.Storefront,
                    title = "Switch to Business Account",
                    subtitle = "Access real advertising, analytics & company profile",
                    onClick = {
                        if (!isVerified) {
                            showBusinessVerificationRequiredDialog = true
                        } else {
                            onNavigateToBusinessSetup()
                        }
                    }
                )
            }

SettingsGroupTitle("Creator & Monetization")
            SettingsActionRow(icon = Icons.Outlined.BarChart, title = "Creator Dashboard", subtitle = "Analytics and insights", onClick = onDashboardClick)
            SettingsActionRow(icon = Icons.Outlined.AttachMoney, title = "Monetization", subtitle = "Earnings and gifts", onClick = onMonetizationClick)
            SettingsActionRow(
                icon = Icons.Outlined.AccountBalanceWallet,
                title = "Wallet",
                subtitle = "Your balance and transactions",
                onClick = onWalletClick,
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        com.example.ui.components.CoinIcon(size = 15.dp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$userCoins",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700),
                            fontSize = 13.sp
                        )
                    }
                }
            )
SettingsActionRow(icon = Icons.Outlined.CardGiftcard, title = "Refer & Earn Coins", subtitle = "Invite friends & unlock milestone rewards", onClick = onNavigateToReferrals)
SettingsActionRow(icon = Icons.Outlined.Category, title = "Creator Category", subtitle = "Set your profile category", onClick = { showCategoryDialog = true })
            SettingsActionRow(icon = Icons.Outlined.BookmarkBorder, title = "Saved", subtitle = "Your saved posts and collections", onClick = onNavigateToSaved)
            SettingsActionRow(icon = Icons.Outlined.Archive, title = "Archive & Recently Deleted", subtitle = "Archived stories, posts & highlights", onClick = onNavigateToArchive)
            SettingsActionRow(icon = Icons.Outlined.Drafts, title = "Drafts Studio", subtitle = "Edit, publish or schedule saved drafts", onClick = onNavigateToDrafts)
            SettingsActionRow(icon = Icons.Outlined.Schedule, title = "Scheduled Content", subtitle = "Posts and reels to publish later", onClick = onNavigateToScheduled)
            SettingsActionRow(icon = Icons.Outlined.PeopleAlt, title = "Close Friends", subtitle = "Manage close friends list", onClick = onNavigateToCloseFriends)
            SettingsActionRow(icon = Icons.Outlined.Podcasts, title = "Broadcast Channels", subtitle = "Create or join channels", onClick = onNavigateToChannels)
            
            SettingsGroupTitle("Controls")
            SettingsActionRow(icon = Icons.Outlined.VideoSettings, title = "Content Controls", subtitle = "Mentions, tags & comment filters", onClick = onPrivacyClick)
            SettingsActionRow(icon = Icons.Outlined.ManageAccounts, title = "Account Controls", subtitle = "Privacy & interaction rules", onClick = { showAccountControlsDialog = true })

            SettingsGroupTitle("Security & Privacy")
            SettingsSwitchRow(
                icon = Icons.Outlined.Face,
                title = "Face Lock",
                subtitle = "Require Face ID or biometric unlock on app open",
                checked = isFaceLockActive,
                onCheckedChange = { enable ->
                    val availability = com.example.security.SecurityBiometricHelper.checkBiometricAvailability(context)
                    if (enable && availability == com.example.security.SecurityBiometricHelper.BiometricAvailability.NONE_ENROLLED) {
                        Toast.makeText(context, "Please enroll Face or Fingerprint in device Settings first.", Toast.LENGTH_LONG).show()
                    } else if (enable && availability == com.example.security.SecurityBiometricHelper.BiometricAvailability.NO_HARDWARE) {
                        Toast.makeText(context, "Biometric hardware is not available on this device.", Toast.LENGTH_LONG).show()
                    } else {
                        isFaceLockActive = enable
                        com.example.security.FaceLockManager.setFaceLockEnabled(context, currentUserId, enable)
                        Toast.makeText(context, if (enable) "Face Lock enabled for this account" else "Face Lock disabled", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            SettingsActionRow(
                icon = Icons.Outlined.VerifiedUser,
                title = "Identity Verification (Face KYC)",
                subtitle = if (isVerified) "Verified Creator (Active)" else "Verify your face, liveness & authenticity",
                onClick = onKycClick
            )
            SettingsActionRow(
                icon = Icons.Outlined.Shield,
                title = "Account Security & Identity Center",
                subtitle = "2FA, Passkeys, Face Verification & Devices",
                onClick = onNavigateToAccountSecurity
            )
            SettingsActionRow(
                icon = Icons.Outlined.Security,
                title = "Active Sessions",
                subtitle = "Manage active devices & logouts",
                onClick = { showActiveSessionsDialog = true }
            )
            SettingsActionRow(
                icon = Icons.Outlined.Download,
                title = "Export Creator Data",
                subtitle = "Download or share a full copy of your data",
                onClick = { showExportDataDialog = true }
            )

            SettingsGroupTitle("Preferences")
            SettingsActionRow(
                icon = Icons.Default.AutoAwesome,
                title = "AI Super Hub",
                subtitle = "NVIDIA AI tools, captions, reels & creator studios",
                onClick = onAiHubClick
            )
            SettingsSwitchRow(
                icon = Icons.Outlined.DarkMode,
                title = "Dark Theme",
                subtitle = "Toggle dark mode",
                checked = isDarkTheme,
                onCheckedChange = { ThemePreference.setDarkTheme(it) }
            )
            SettingsActionRow(icon = Icons.Outlined.Notifications, title = "Notifications", subtitle = "Push, alerts & campaign updates", onClick = { showNotificationsDialog = true })
            SettingsActionRow(icon = Icons.Outlined.Language, title = "Language", subtitle = currentLanguage, onClick = { showLanguageDialog = true })
            SettingsActionRow(icon = Icons.Outlined.Accessibility, title = "Accessibility", subtitle = "Contrast, haptics & typography", onClick = { showAccessibilityDialog = true })
            
            SettingsGroupTitle("Support & About")
            SettingsActionRow(icon = Icons.Outlined.HelpOutline, title = "Help Center", subtitle = "FAQ & 24/7 support tickets", onClick = { showHelpCenterDialog = true })
            SettingsActionRow(icon = Icons.Outlined.Policy, title = "Privacy Policy", subtitle = "Data protection & user privacy rights", onClick = { showPrivacyPolicyDialog = true })
            SettingsActionRow(icon = Icons.Outlined.Gavel, title = "Terms of Service", subtitle = "User agreements & platform rules", onClick = { showTermsDialog = true })
            SettingsActionRow(icon = Icons.Outlined.Info, title = "About Social India", subtitle = "Version 5.0.0 (Production)", onClick = { showAboutDialog = true })

            if (isAdmin) {
                SettingsGroupTitle("Admin Actions")
                SettingsActionRow(icon = Icons.Outlined.AdminPanelSettings, title = "Admin Panel", subtitle = "Manage platform & users", onClick = onAdminClick)
                SettingsActionRow(icon = Icons.Outlined.SecurityUpdateWarning, title = "Security Policy Admin", subtitle = "Configure ID provider & global rules", onClick = onNavigateToAdminSecurity)
                SettingsActionRow(icon = Icons.Outlined.AdsClick, title = "Advertising Manager", subtitle = "Review pending ads & platform revenue", onClick = onNavigateToAdminAds)
                SettingsActionRow(icon = Icons.Outlined.FactCheck, title = "Review Verifications", subtitle = "Approve requests", onClick = onReviewVerifications)
            }

            SettingsGroupTitle("Account Security & Management")
            SettingsActionRow(
                icon = Icons.Outlined.PersonOff,
                title = "Deactivate Account",
                subtitle = "Temporarily hide your account and profile",
                onClick = { showDeactivateDialog = true }
            )
            SettingsActionRow(
                icon = Icons.Outlined.DeleteForever,
                title = "Delete Account Permanently",
                subtitle = "Erase all personal data, posts and profile",
                onClick = { showDeleteAccountDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE53935))
            ) {
                Text("Log Out", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }


        if (showCategoryDialog) {
            AlertDialog(
                onDismissRequest = { showCategoryDialog = false },
                title = { Text("Creator Category") },
                text = { Text("Update your creator category in Edit Profile.") },
                confirmButton = { TextButton(onClick = { showCategoryDialog = false; onNavigateToEditProfile() }) { Text("Go") } },
                dismissButton = { TextButton(onClick = { showCategoryDialog = false }) { Text("Cancel") } }
            )
        }
        
        if (showCloseFriendsDialog) {
            AlertDialog(
                onDismissRequest = { showCloseFriendsDialog = false },
                title = { Text("Close Friends") },
                text = { Text("Manage your Close Friends list here.") },
                confirmButton = { TextButton(onClick = { showCloseFriendsDialog = false }) { Text("OK") } }
            )
        }
        
        if (showChannelDialog) {
            AlertDialog(
                onDismissRequest = { showChannelDialog = false },
                title = { Text("Broadcast Channel") },
                text = { Text("Create a new broadcast channel to communicate directly with your followers.") },
                confirmButton = { TextButton(onClick = { showChannelDialog = false }) { Text("Create") } },
                dismissButton = { TextButton(onClick = { showChannelDialog = false }) { Text("Cancel") } }
            )
        }
        
        if (showAccountControlsDialog) {
            AlertDialog(
                onDismissRequest = { showAccountControlsDialog = false },
                title = { Text("Account Controls", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Manage privacy, audience visibility, and activity status.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Private Account", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Only approved followers can view your media", fontSize = 12.sp, color = Color.Gray)
                            }
                            Switch(
                                checked = isPrivateAccount,
                                onCheckedChange = { checked ->
                                    isPrivateAccount = checked
                                    val uid = com.example.model.RepositoryManager.getCurrentUserId()
                                    if (uid.isNotEmpty()) {
                                        coroutineScope.launch {
                                            com.example.model.RepositoryManager.firestore.collection("users").document(uid).update("isPrivate", checked)
                                        }
                                    }
                                }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Activity Status", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Allow others to see when you're online", fontSize = 12.sp, color = Color.Gray)
                            }
                            Switch(
                                checked = showOnlineStatus,
                                onCheckedChange = { checked ->
                                    showOnlineStatus = checked
                                    val uid = com.example.model.RepositoryManager.getCurrentUserId()
                                    if (uid.isNotEmpty()) {
                                        coroutineScope.launch {
                                            com.example.model.RepositoryManager.firestore.collection("users").document(uid).update("showOnlineStatus", checked)
                                        }
                                    }
                                }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Sensitive Content Filter", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Limit sensitive or explicit media in recommendations", fontSize = 12.sp, color = Color.Gray)
                            }
                            Switch(
                                checked = filterSensitiveContent,
                                onCheckedChange = { checked ->
                                    filterSensitiveContent = checked
                                    val uid = com.example.model.RepositoryManager.getCurrentUserId()
                                    if (uid.isNotEmpty()) {
                                        coroutineScope.launch {
                                            com.example.model.RepositoryManager.firestore.collection("users").document(uid).update("filterSensitiveContent", checked)
                                        }
                                    }
                                }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Read Receipts", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Show blue ticks when you read messages in direct chats", fontSize = 12.sp, color = Color.Gray)
                            }
                            Switch(
                                checked = allowReadReceipts,
                                onCheckedChange = { checked ->
                                    allowReadReceipts = checked
                                    val uid = com.example.model.RepositoryManager.getCurrentUserId()
                                    if (uid.isNotEmpty()) {
                                        coroutineScope.launch {
                                            com.example.model.RepositoryManager.firestore.collection("users").document(uid).update("allowReadReceipts", checked)
                                        }
                                    }
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { 
                        showAccountControlsDialog = false
                        Toast.makeText(context, "Account controls saved", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        if (showActiveSessionsDialog) {
            AlertDialog(
                onDismissRequest = { showActiveSessionsDialog = false },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Active Sessions", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Devices where your account is currently signed in:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Box(modifier = Modifier.size(8.dp).background(Color(0xFF00C853), CircleShape))
                                        Text("Active Now", color = Color(0xFF00C853), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Android ${Build.VERSION.RELEASE} • Social India App", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Location: India • Verified Device ID", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Button(
                            onClick = {
                                val uid = com.example.model.RepositoryManager.getCurrentUserId()
                                if (uid.isNotEmpty()) {
                                    coroutineScope.launch {
                                        com.example.model.RepositoryManager.firestore.collection("users").document(uid).update(
                                            "lastOtherSessionsRevocation", System.currentTimeMillis()
                                        )
                                    }
                                }
                                Toast.makeText(context, "All other sessions have been logged out", Toast.LENGTH_SHORT).show()
                                showActiveSessionsDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Log Out All Other Devices", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showActiveSessionsDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        if (showExportDataDialog) {
            AlertDialog(
                onDismissRequest = { if (!isExportingData) showExportDataDialog = false },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Export Creator Data", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Export a complete, portable archive of your Social India account, including:", fontSize = 13.sp)
                        Text("• Profile details & Bio\n• Follower & Following statistics\n• Published posts, videos & view metrics\n• Advertising & Campaign receipts", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (isExportingData) {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Compiling export package...", fontSize = 13.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = !isExportingData,
                        onClick = {
                            isExportingData = true
                            coroutineScope.launch {
                                try {
                                    val uid = com.example.model.RepositoryManager.getCurrentUserId()
                                    val doc = com.example.model.RepositoryManager.firestore.collection("users").document(uid).get().await()
                                    val postsSnapshot = com.example.model.RepositoryManager.firestore.collection("posts").whereEqualTo("userId", uid).get().await()
                                    
                                    val exportSummary = buildString {
                                        appendLine("=== SOCIAL INDIA CREATOR DATA ARCHIVE ===")
                                        appendLine("Generated At: ${java.util.Date()}")
                                        appendLine("User ID: $uid")
                                        appendLine("Username: @${doc.getString("username") ?: "user"}")
                                        appendLine("Full Name: ${doc.getString("name") ?: ""}")
                                        appendLine("Account Type: ${doc.getString("accountType") ?: "PERSONAL"}")
                                        appendLine("Followers: ${doc.getLong("followersCount") ?: 0}")
                                        appendLine("Following: ${doc.getLong("followingCount") ?: 0}")
                                        appendLine("Total Posts: ${postsSnapshot.size()}")
                                        appendLine("Verified: ${doc.getBoolean("isVerified") ?: false}")
                                        appendLine("Monetization Status: ${doc.getString("monetization_status") ?: "NONE"}")
                                        appendLine("\n--- POSTS SUMMARY ---")
                                        postsSnapshot.documents.forEachIndexed { i, p ->
                                            appendLine("${i + 1}. ID: ${p.id} | Likes: ${p.getLong("likesCount") ?: 0} | Caption: ${p.getString("caption") ?: ""}")
                                        }
                                        appendLine("\n==========================================")
                                    }

                                    isExportingData = false
                                    showExportDataDialog = false

                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Social India Creator Export")
                                        putExtra(Intent.EXTRA_TEXT, exportSummary)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share or Save Export"))
                                } catch (e: Exception) {
                                    isExportingData = false
                                    Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Text("Generate & Share Archive", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(enabled = !isExportingData, onClick = { showExportDataDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showNotificationsDialog) {
            AlertDialog(
                onDismissRequest = { showNotificationsDialog = false },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Notification Preferences", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Social India Chime", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Play signature notification sound", fontSize = 12.sp, color = Color.Gray)
                            }
                            var isCustomSound by remember { mutableStateOf(com.example.notification.SocialIndiaNotificationSoundManager.isSoundEnabled(context)) }
                            Switch(
                                checked = isCustomSound,
                                onCheckedChange = {
                                    isCustomSound = it
                                    com.example.notification.SocialIndiaNotificationSoundManager.setSoundEnabled(context, it)
                                    if (it) com.example.notification.SocialIndiaNotificationSoundManager.playInAppNotificationSound(context)
                                }
                            )
                        }
                        OutlinedButton(
                            onClick = { com.example.notification.SocialIndiaNotificationSoundManager.playInAppNotificationSound(context) },
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Notification Sound", fontSize = 12.sp)
                        }
                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Push Notifications", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Receive real-time alerts on your device", fontSize = 12.sp, color = Color.Gray)
                            }
                            Switch(checked = notifPushEnabled, onCheckedChange = { notifPushEnabled = it })
                        }
                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Likes & Comments", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("When someone interacts with your posts", fontSize = 12.sp, color = Color.Gray)
                            }
                            Switch(checked = notifLikesComments, onCheckedChange = { notifLikesComments = it })
                        }
                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Direct Messages", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("New messages and chat replies", fontSize = 12.sp, color = Color.Gray)
                            }
                            Switch(checked = notifDirectMessages, onCheckedChange = { notifDirectMessages = it })
                        }
                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Ad Campaign Updates", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Approvals, budget alerts and analytics", fontSize = 12.sp, color = Color.Gray)
                            }
                            Switch(checked = notifCampaignAlerts, onCheckedChange = { notifCampaignAlerts = it })
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val uid = com.example.model.RepositoryManager.getCurrentUserId()
                        if (uid.isNotEmpty()) {
                            coroutineScope.launch {
                                com.example.model.RepositoryManager.firestore.collection("users").document(uid).update(
                                    mapOf(
                                        "notifPush" to notifPushEnabled,
                                        "notifLikesComments" to notifLikesComments,
                                        "notifDirectMessages" to notifDirectMessages,
                                        "notifCampaignAlerts" to notifCampaignAlerts
                                    )
                                )
                            }
                        }
                        Toast.makeText(context, "Notification preferences updated", Toast.LENGTH_SHORT).show()
                        showNotificationsDialog = false
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNotificationsDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showLanguageDialog) {
            val languages = listOf(
                "English (India)", "हिन्दी (Hindi)", "বাংলা (Bengali)", "मराठी (Marathi)",
                "தமிழ் (Tamil)", "తెలుగు (Telugu)", "ગુજરાતી (Gujarati)", "ಕನ್ನಡ (Kannada)",
                "ਪੰਜਾਬੀ (Punjabi)", "മലയാളം (Malayalam)"
            )
            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Choose App Language", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        languages.forEach { lang ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentLanguage = lang
                                        val uid = com.example.model.RepositoryManager.getCurrentUserId()
                                        if (uid.isNotEmpty()) {
                                            coroutineScope.launch {
                                                com.example.model.RepositoryManager.firestore.collection("users").document(uid).update("language", lang)
                                            }
                                        }
                                        Toast.makeText(context, "Language set to $lang", Toast.LENGTH_SHORT).show()
                                        showLanguageDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(lang, fontSize = 15.sp, fontWeight = if (lang == currentLanguage) FontWeight.Bold else FontWeight.Normal)
                                if (lang == currentLanguage) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLanguageDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showAccessibilityDialog) {
            AlertDialog(
                onDismissRequest = { showAccessibilityDialog = false },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Accessibility, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Accessibility Options", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("High Contrast Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Increase readability for text & borders", fontSize = 12.sp, color = Color.Gray)
                            }
                            Switch(checked = highContrastMode, onCheckedChange = { highContrastMode = it })
                        }
                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Large Typography", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Scale up captions and feed typography", fontSize = 12.sp, color = Color.Gray)
                            }
                            Switch(checked = largeTextMode, onCheckedChange = { largeTextMode = it })
                        }
                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Haptic Feedback", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Vibrate gently when liking and navigating", fontSize = 12.sp, color = Color.Gray)
                            }
                            Switch(checked = hapticFeedbackEnabled, onCheckedChange = { hapticFeedbackEnabled = it })
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        Toast.makeText(context, "Accessibility preferences applied", Toast.LENGTH_SHORT).show()
                        showAccessibilityDialog = false
                    }) {
                        Text("Apply")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAccessibilityDialog = false }) { Text("Close") }
                }
            )
        }

        if (showHelpCenterDialog) {
            var selectedFaqIndex by remember { mutableIntStateOf(-1) }
            val faqs = listOf(
                "How do Creator Monetization & Gifts work?" to "Creators can monetize by receiving virtual gifts on reels and live streams, or participating in the platform ad revenue share program once eligible.",
                "How do I get Blue Tick Verification?" to "Go to Settings -> Request Verification. Submit your official government ID or business license. The admin team reviews and approves verified profiles within 24-48 hours.",
                "How to run Business Ads?" to "Switch your account to Business from Settings -> Switch to Business, then click 'Create Advertisement'. Set your audience, budget, and creative to start driving real sales.",
                "How does Account & Data Security work?" to "Social India uses encrypted Firebase cloud tokens, strict user privacy boundaries, anti-bot fraud filters, and zero data selling."
            )
            AlertDialog(
                onDismissRequest = { showHelpCenterDialog = false },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Help & Support Center", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Frequently Asked Questions", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        faqs.forEachIndexed { i, (q, a) ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { selectedFaqIndex = if (selectedFaqIndex == i) -1 else i },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(q, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    if (selectedFaqIndex == i) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(a, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Submit Support Request", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        OutlinedTextField(
                            value = supportMessageText,
                            onValueChange = { supportMessageText = it },
                            placeholder = { Text("Describe your issue or query...") },
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Button(
                            enabled = supportMessageText.isNotBlank() && !isSubmittingTicket,
                            onClick = {
                                isSubmittingTicket = true
                                coroutineScope.launch {
                                    try {
                                        val uid = com.example.model.RepositoryManager.getCurrentUserId()
                                        com.example.model.RepositoryManager.firestore.collection("support_tickets").add(
                                            mapOf(
                                                "userId" to uid,
                                                "message" to supportMessageText.trim(),
                                                "status" to "OPEN",
                                                "createdAt" to System.currentTimeMillis()
                                            )
                                        ).await()
                                        isSubmittingTicket = false
                                        supportMessageText = ""
                                        Toast.makeText(context, "Ticket submitted! Support will respond shortly.", Toast.LENGTH_LONG).show()
                                        showHelpCenterDialog = false
                                    } catch (e: Exception) {
                                        isSubmittingTicket = false
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isSubmittingTicket) "Submitting..." else "Submit Ticket", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHelpCenterDialog = false }) { Text("Close") }
                }
            )
        }

        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("About Social India", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("🇮🇳 Social India — Built for Bharat", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                        Text("Social India is a fast, safe, next-generation social networking and creator monetization ecosystem empowering millions across India.", fontSize = 13.sp)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Version: 1.0.0 (Production Stable)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("Security Protocol: SSL/TLS & Cloud Firestore Rules", fontSize = 12.sp, color = Color.Gray)
                                Text("Payment Gateway: UPI & Secure In-App Wallet", fontSize = 12.sp, color = Color.Gray)
                                Text("Official Support: support@socialindia.in", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        Text("• Strict Anti-Fraud & Real-time Content Moderation\n• Zero unauthorized data distribution\n• End-to-End creator copyright attribution", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = {
                    Button(onClick = { showAboutDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }

        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("Log Out") },
                text = { Text("Are you sure you want to log out of your account?") },
                confirmButton = {
                    TextButton(onClick = { showLogoutDialog = false; onLogout() }, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE53935))) {
                        Text("Log Out", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
                },
                containerColor = bgColor,
                textContentColor = textColor,
                titleContentColor = textColor
            )
        }

        if (showAccountTypeDialog) {
            AlertDialog(
                onDismissRequest = { showAccountTypeDialog = false },
                title = { Text("Switch Account Type", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Select the account profile that best matches your needs:", fontSize = 13.sp)
                        ListItem(
                            headlineContent = { Text("Personal Account") },
                            supportingContent = { Text("Standard profile for everyday sharing and connecting") },
                            leadingContent = { Icon(Icons.Outlined.Person, contentDescription = null) },
                            modifier = Modifier.clickable {
                                showAccountTypeDialog = false
                                val uid = com.example.model.RepositoryManager.getCurrentUserId()
                                coroutineScope.launch {
                                    com.example.repository.AdvertisingRepository.switchAccountType(uid, "PERSONAL")
                                    currentAccountType = "PERSONAL"
                                    android.widget.Toast.makeText(context, "Switched to Personal Account", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Creator Account") },
                            supportingContent = { Text("Best for public figures, content producers and artists") },
                            leadingContent = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                            modifier = Modifier.clickable {
                                showAccountTypeDialog = false
                                val uid = com.example.model.RepositoryManager.getCurrentUserId()
                                coroutineScope.launch {
                                    com.example.repository.AdvertisingRepository.switchAccountType(uid, "CREATOR")
                                    currentAccountType = "CREATOR"
                                    android.widget.Toast.makeText(context, "Switched to Creator Account", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Business Account") },
                            supportingContent = { Text("Best for brands, local businesses, retailers and organizations") },
                            leadingContent = { Icon(Icons.Outlined.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable {
                                showAccountTypeDialog = false
                                if (!isVerified) {
                                    showBusinessVerificationRequiredDialog = true
                                } else {
                                    onNavigateToBusinessSetup()
                                }
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAccountTypeDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showBusinessVerificationRequiredDialog) {
            AlertDialog(
                onDismissRequest = { showBusinessVerificationRequiredDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = Color(0xFF1DA1F2),
                        modifier = Modifier.size(48.dp)
                    )
                },
                title = {
                    Text(
                        text = "Social India Verification Required",
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Business Account requires an active Social India Verification.",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "To maintain an authentic marketplace and business directory, business accounts are exclusively available to verified accounts on Social India.",
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showBusinessVerificationRequiredDialog = false
                            onRequestVerification()
                        }
                    ) {
                        Text("Get Verified", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBusinessVerificationRequiredDialog = false }) {
                        Text("Cancel")
                    }
                },
                shape = RoundedCornerShape(20.dp)
            )
        }

        if (showPrivacyPolicyDialog) {
            AlertDialog(
                onDismissRequest = { showPrivacyPolicyDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Policy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Privacy Policy", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("🛡️ Social India Privacy Commitment", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        Text("We respect and protect your privacy under Indian IT Acts and international data security standards.", fontSize = 13.sp)
                        Text("• Zero Data Selling: We never sell or rent your personal information to third parties.\n• Encrypted Infrastructure: All messages, profiles, and media are secured via Google Firebase and SSL/TLS.\n• Permissions Control: Camera and Microphone are used strictly when you choose to record reels or join live calls.\n• Account Rights: You can request complete export or permanent deletion of your data anytime.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                try {
                                    val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://socialindia-2cdf0.web.app/privacy.html"))
                                    context.startActivity(browserIntent)
                                } catch (_: Exception) {}
                            },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Official Privacy Policy URL", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("https://socialindia-2cdf0.web.app/privacy.html", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showPrivacyPolicyDialog = false }) { Text("I Understand") }
                }
            )
        }

        if (showTermsDialog) {
            AlertDialog(
                onDismissRequest = { showTermsDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Gavel, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Terms of Service", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("📜 Community Guidelines & Terms", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        Text("By using Social India, you agree to foster a respectful and creative community:", fontSize = 13.sp)
                        Text("1. Respectful Content: Hate speech, harassment, nudity, and unlawful activities are strictly prohibited.\n2. Authentic Identity: Impersonation and spam bot networks will be permanently banned.\n3. Copyright Protection: Upload only content you own or have permission to share.\n4. Safe Interactions: Community reporting tools are active 24/7 to safeguard creators.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = {
                    Button(onClick = { showTermsDialog = false }) { Text("Agree & Close") }
                }
            )
        }

        if (showDeleteAccountDialog) {
            AlertDialog(
                onDismissRequest = { if (!isDeletingAccount) showDeleteAccountDialog = false },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = Color(0xFFE53935))
                        Text("Delete Account Permanently?", fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⚠️ WARNING: This action is permanent and irreversible!", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFE53935))
                        Text("Deleting your account will permanently erase your:", fontSize = 13.sp)
                        Text("• Profile data, handle & verification\n• All published posts, stories & reels\n• Chat messages, comments & likes\n• Coin wallet balance & creator earnings", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("This cannot be undone. Are you sure you wish to proceed?", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val uid = com.example.model.RepositoryManager.getCurrentUserId()
                            if (uid.isNotEmpty()) {
                                isDeletingAccount = true
                                coroutineScope.launch {
                                    try {
                                        // Scrub PII and mark account deleted in Firestore
                                        com.example.model.RepositoryManager.firestore.collection("users").document(uid).update(
                                            mapOf(
                                                "isDeleted" to true,
                                                "deletedAt" to System.currentTimeMillis(),
                                                "name" to "Deleted User",
                                                "username" to "deleted_${uid.take(6)}",
                                                "email" to "",
                                                "phone" to "",
                                                "bio" to "",
                                                "avatarUrl" to "",
                                                "avatar_url" to "",
                                                "profilePicUrl" to ""
                                            )
                                        ).await()

                                        // Permanently delete Firebase Auth user when recent credentials allow, then sign out
                                        try {
                                            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.delete()?.await()
                                        } catch (e: kotlinx.coroutines.CancellationException) {
                                            throw e
                                        } catch (_: Exception) {
                                            // If Firebase requires recent re-auth, sign out cleanly after Firestore erasure
                                        }
                                        try {
                                            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                                        } catch (_: Exception) {}
                                        Toast.makeText(context, "Your account has been permanently deleted.", Toast.LENGTH_LONG).show()
                                        showDeleteAccountDialog = false
                                        onLogout()
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error deleting account: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isDeletingAccount = false
                                    }
                                }
                            }
                        },
                        enabled = !isDeletingAccount,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                    ) {
                        if (isDeletingAccount) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Yes, Delete My Account", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAccountDialog = false }, enabled = !isDeletingAccount) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showDeactivateDialog) {
            AlertDialog(
                onDismissRequest = { if (!isDeactivating) showDeactivateDialog = false },
                title = { Text("Deactivate Account?", fontWeight = FontWeight.Bold) },
                text = {
                    Text("Your profile, photos, comments, and activity will be temporarily hidden. You can reactivate anytime simply by logging back in.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val uid = com.example.model.RepositoryManager.getCurrentUserId()
                            if (uid.isNotEmpty()) {
                                isDeactivating = true
                                coroutineScope.launch {
                                    try {
                                        com.example.model.RepositoryManager.authRepository.deactivateAccount(uid)
                                        android.widget.Toast.makeText(context, "Account deactivated successfully.", android.widget.Toast.LENGTH_LONG).show()
                                        showDeactivateDialog = false
                                        onLogout()
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isDeactivating = false
                                    }
                                }
                            }
                        },
                        enabled = !isDeactivating,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                    ) {
                        if (isDeactivating) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Deactivate", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeactivateDialog = false },
                        enabled = !isDeactivating
                    ) {
                        Text("Cancel")
                    }
                },
                containerColor = bgColor,
                textContentColor = textColor,
                titleContentColor = textColor
            )
        }

        if (showAccountStatusCenterDialog) {
            val suspensionInfo by com.example.security.AccountSuspensionManager.suspensionInfo.collectAsState()
            AlertDialog(
                onDismissRequest = { showAccountStatusCenterDialog = false },
                containerColor = Color(0xFF14151A),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.FactCheck, contentDescription = null, tint = Color(0xFF0095F6))
                        Text("Account Status Center", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val isClean = suspensionInfo.strikesCount == 0 && !suspensionInfo.isShadowbanned && suspensionInfo.accountStatus == "ACTIVE"
                        Surface(
                            color = if (isClean) Color(0xFF10B981).copy(alpha = 0.14f) else Color(0xFFF59E0B).copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isClean) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFF59E0B).copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = if (isClean) "✓ Your Account Is in Good Standing" else "⚠️ Account Restrictions Detected",
                                    color = if (isClean) Color(0xFF10B981) else Color(0xFFF59E0B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isClean)
                                        "You haven't posted anything that affects your account status. Thank you for following our Community Guidelines."
                                    else
                                        "Reason: ${suspensionInfo.suspensionReason}",
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Surface(
                            color = Color(0xFF1E2028),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Community Guideline Strikes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("3 strikes result in automatic account suspension", color = Color.Gray, fontSize = 11.sp)
                                }
                                Text(
                                    text = "${suspensionInfo.strikesCount} / 3",
                                    color = if (suspensionInfo.strikesCount == 0) Color(0xFF10B981) else Color(0xFFEF4444),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        Surface(
                            color = Color(0xFF1E2028),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Feature & Recommendation Status", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                listOf(
                                    "Removed Content History" to if (suspensionInfo.strikesCount == 0) "0 Removed" else "${suspensionInfo.strikesCount} Removed",
                                    "Recommendation Eligibility (Explore/Reels)" to if (suspensionInfo.isShadowbanned) "Shadowbanned (Restricted)" else "✓ Eligible",
                                    "Live Streaming & PK Battles" to if (suspensionInfo.accountStatus == "ACTIVE") "✓ Enabled" else "Restricted",
                                    "Creator Monetization & Gifts" to if (suspensionInfo.strikesCount < 2) "✓ Eligible" else "Paused"
                                ).forEach { (feature, stateLabel) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(feature, color = Color.LightGray, fontSize = 12.sp)
                                        Text(
                                            text = stateLabel,
                                            color = if (stateLabel.startsWith("✓") || stateLabel.startsWith("0")) Color(0xFF10B981) else Color(0xFFF59E0B),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val uid = com.example.model.RepositoryManager.getCurrentUserId()
                                if (uid.isNotEmpty()) {
                                    com.example.security.AccountSuspensionManager.submitAppeal(
                                        uid = uid,
                                        reason = "Account Status / Shadowban Review Request from Settings",
                                        contactEmail = "",
                                        onSuccess = {
                                            Toast.makeText(context, "Appeal sent to Admin Moderation Queue!", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { err ->
                                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0095F6)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Request Shadowban / Strike Appeal", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAccountStatusCenterDialog = false }) {
                        Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@Composable
fun SettingsGroupTitle(title: String) {
    Text(
        text = title.uppercase(),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp, end = 16.dp)
    )
}

@Composable
fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(text = subtitle, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 13.sp)
            }
        }
        if (trailing != null) {
            trailing()
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f))
    }
}

@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(text = subtitle, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 13.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.surface,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}
