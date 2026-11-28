package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.model.PrivacyControlsData
import com.example.model.RepositoryManager
import com.example.repository.FlagshipFeaturesRepository
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserId = RepositoryManager.getCurrentUserId()
    var settings by remember { mutableStateOf(PrivacyControlsData()) }
    var newWordInput by remember { mutableStateOf("") }

    var isPrivateAccount by remember { mutableStateOf(false) }
    var isTwoFactorEnabled by remember { mutableStateOf(false) }
    var showBlockedAccountsDialog by remember { mutableStateOf<String?>(null) } // "Blocked", "Restricted", "Muted"
    val blockedUsersList = remember { mutableStateListOf<String>() }
    val restrictedUsersList = remember { mutableStateListOf<String>() }
    val mutedUsersList = remember { mutableStateListOf<String>() }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty() && currentUserId != "anonymous_user") {
            launch {
                FlagshipFeaturesRepository.listenPrivacyControls(currentUserId).collect {
                    settings = it
                }
            }
            launch {
                try {
                    val userDoc = RepositoryManager.firestore.collection("users").document(currentUserId).get().await()
                    if (userDoc.exists()) {
                        isPrivateAccount = userDoc.getBoolean("isPrivate") ?: false
                        isTwoFactorEnabled = userDoc.getBoolean("twoFactorEnabled") ?: false
                        val blockedFromDoc = (userDoc.get("blockedUsers") as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
                        val restrictedFromDoc = (userDoc.get("restrictedUsers") as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
                        val mutedFromDoc = (userDoc.get("mutedUsers") as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
                        blockedUsersList.clear()
                        blockedUsersList.addAll(blockedFromDoc)
                        restrictedUsersList.clear()
                        restrictedUsersList.addAll(restrictedFromDoc)
                        mutedUsersList.clear()
                        mutedUsersList.addAll(mutedFromDoc)
                    }
                    val blockedSub = RepositoryManager.firestore.collection("users").document(currentUserId)
                        .collection("blockedUsers").get().await()
                    blockedSub.documents.forEach { bDoc ->
                        val label = bDoc.getString("username")?.takeIf { it.isNotBlank() } ?: bDoc.id
                        if (!blockedUsersList.contains(label)) blockedUsersList.add(label)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy & Interactions", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Account Privacy & Security (Public/Private Toggle & 2FA)
            SettingsGroupTitle("Account Privacy & Security")
            SettingsSwitchRow(
                icon = Icons.Outlined.Lock,
                title = "Private Account",
                subtitle = "Only followers you approve can see your photos, reels, and stories",
                checked = isPrivateAccount,
                onCheckedChange = { checked ->
                    isPrivateAccount = checked
                    if (currentUserId.isNotEmpty()) {
                        com.example.model.RepositoryManager.firestore.collection("users").document(currentUserId)
                            .update("isPrivate", checked)
                    }
                    Toast.makeText(context, if (checked) "Switched to Private Account" else "Switched to Public Account", Toast.LENGTH_SHORT).show()
                }
            )
            SettingsSwitchRow(
                icon = Icons.Outlined.Security,
                title = "Two-Factor Authentication (2FA)",
                subtitle = "Require a security code when logging in from an unrecognized device",
                checked = isTwoFactorEnabled,
                onCheckedChange = { checked ->
                    isTwoFactorEnabled = checked
                    if (currentUserId.isNotEmpty()) {
                        com.example.model.RepositoryManager.firestore.collection("users").document(currentUserId)
                            .update("twoFactorEnabled", checked)
                    }
                    Toast.makeText(context, if (checked) "2FA Protection Enabled" else "2FA Protection Disabled", Toast.LENGTH_SHORT).show()
                }
            )

            // Block / Restrict / Mute Management
            SettingsGroupTitle("Connections & Safety")
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    listOf(
                        Triple("Blocked Accounts", "${blockedUsersList.size} accounts", "Blocked"),
                        Triple("Restricted Accounts", "${restrictedUsersList.size} accounts", "Restricted"),
                        Triple("Muted Accounts", "${mutedUsersList.size} accounts", "Muted")
                    ).forEach { (label, countLabel, key) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showBlockedAccountsDialog = key }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(countLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            val categoryKey = showBlockedAccountsDialog
            if (categoryKey != null) {
                val activeList = when (categoryKey) {
                    "Blocked" -> blockedUsersList
                    "Restricted" -> restrictedUsersList
                    else -> mutedUsersList
                }
                AlertDialog(
                    onDismissRequest = { showBlockedAccountsDialog = null },
                    title = { Text("$categoryKey Accounts", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (activeList.isEmpty()) {
                                Text("No ${categoryKey.lowercase()} accounts.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            } else {
                                activeList.forEach { uname ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("@$uname", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                        TextButton(onClick = {
                                            activeList.remove(uname)
                                            if (currentUserId.isNotEmpty()) {
                                                val fieldName = when (categoryKey) {
                                                    "Blocked" -> "blockedUsers"
                                                    "Restricted" -> "restrictedUsers"
                                                    else -> "mutedUsers"
                                                }
                                                RepositoryManager.firestore.collection("users").document(currentUserId)
                                                    .update(fieldName, FieldValue.arrayRemove(uname))
                                                if (categoryKey == "Blocked") {
                                                    RepositoryManager.firestore.collection("users").document(currentUserId)
                                                        .collection("blockedUsers").document(uname).delete()
                                                }
                                            }
                                        }) {
                                            Text("Remove", color = Color(0xFF0095F6), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showBlockedAccountsDialog = null }) {
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            // Activity Status
            SettingsGroupTitle("Activity Status")
            SettingsSwitchRow(
                icon = Icons.Outlined.Visibility,
                title = "Show Activity Status",
                subtitle = "Allow accounts you follow to see when you are active",
                checked = settings.activityStatusEnabled,
                onCheckedChange = { checked ->
                    val updated = settings.copy(activityStatusEnabled = checked)
                    settings = updated
                    scope.launch { FlagshipFeaturesRepository.updatePrivacyControls(currentUserId, updated) }
                }
            )

            // Mentions
            SettingsGroupTitle("Mentions Controls")
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Who can @mention you", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Choose who can mention you in stories, comments, and captions.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    val mentionOptions = listOf(
                        "everyone" to "Everyone",
                        "following" to "People You Follow",
                        "nobody" to "Don't Allow Mentions"
                    )
                    for ((key, label) in mentionOptions) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val updated = settings.copy(mentionPermission = key)
                                    settings = updated
                                    scope.launch { FlagshipFeaturesRepository.updatePrivacyControls(currentUserId, updated) }
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.mentionPermission == key,
                                onClick = {
                                    val updated = settings.copy(mentionPermission = key)
                                    settings = updated
                                    scope.launch { FlagshipFeaturesRepository.updatePrivacyControls(currentUserId, updated) }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Tags
            SettingsGroupTitle("Tag Controls")
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Who can tag you", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Choose who can tag you in their photos and videos.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    val tagOptions = listOf(
                        "everyone" to "Everyone",
                        "following" to "People You Follow",
                        "nobody" to "Don't Allow Tags"
                    )
                    for ((key, label) in tagOptions) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val updated = settings.copy(tagPermission = key)
                                    settings = updated
                                    scope.launch { FlagshipFeaturesRepository.updatePrivacyControls(currentUserId, updated) }
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.tagPermission == key,
                                onClick = {
                                    val updated = settings.copy(tagPermission = key)
                                    settings = updated
                                    scope.launch { FlagshipFeaturesRepository.updatePrivacyControls(currentUserId, updated) }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Manually Approve Tags", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Review posts before they appear on your profile", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.manualTagApproval,
                            onCheckedChange = { checked ->
                                val updated = settings.copy(manualTagApproval = checked)
                                settings = updated
                                scope.launch { FlagshipFeaturesRepository.updatePrivacyControls(currentUserId, updated) }
                            }
                        )
                    }
                }
            }

            // Hidden Words & Moderation
            SettingsGroupTitle("Hidden Words & Comment Filter")
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hide Offensive Comments", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Filter comments that may be offensive or spam", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.filterOffensiveComments,
                            onCheckedChange = { checked ->
                                val updated = settings.copy(filterOffensiveComments = checked)
                                settings = updated
                                scope.launch { FlagshipFeaturesRepository.updatePrivacyControls(currentUserId, updated) }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Custom Filter Words", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newWordInput,
                            onValueChange = { newWordInput = it },
                            placeholder = { Text("Add word or phrase...", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newWordInput.isNotBlank()) {
                                    val word = newWordInput.trim().lowercase()
                                    val updatedList = (settings.customBlockedWords + word).distinct()
                                    val updated = settings.copy(customBlockedWords = updatedList)
                                    settings = updated
                                    scope.launch { FlagshipFeaturesRepository.addBlockedWord(currentUserId, word) }
                                    newWordInput = ""
                                }
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Add")
                        }
                    }

                    if (settings.customBlockedWords.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (word in settings.customBlockedWords) {
                                InputChip(
                                    selected = true,
                                    onClick = {
                                        val updatedList = settings.customBlockedWords.filter { it != word }
                                        val updated = settings.copy(customBlockedWords = updatedList)
                                        settings = updated
                                        scope.launch { FlagshipFeaturesRepository.removeBlockedWord(currentUserId, word) }
                                    },
                                    label = { Text(word, fontSize = 12.sp) },
                                    trailingIcon = {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
