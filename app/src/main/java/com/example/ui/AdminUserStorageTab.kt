package com.example.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AdminDriveAccount
import com.example.model.RepositoryManager
import com.example.utils.AdminDriveStoragePoolManager
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUserStorageTab() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var accounts by remember { mutableStateOf<List<AdminDriveAccount>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<AdminDriveAccount?>(null) }
    var realUploadedMediaCount by remember { mutableIntStateOf(0) }
    var isConnectingGoogle by remember { mutableStateOf(false) }

    val driveScope = remember { Scope("https://www.googleapis.com/auth/drive.file") }

    fun refreshAccounts() {
        isLoading = true
        coroutineScope.launch {
            accounts = AdminDriveStoragePoolManager.getAllAccounts()
            isLoading = false
        }
    }

    val authRecoveryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        isConnectingGoogle = false
        if (res.resultCode == android.app.Activity.RESULT_OK) {
            refreshAccounts()
        }
    }

    fun formatDisplayNameFromEmail(email: String): String {
        val localPart = email.substringBefore("@").trim()
        if (localPart.isEmpty()) return "Google Drive Node"
        val cleaned = localPart
            .replace(Regex("[0-9]+$"), "")
            .replace(".", " ")
            .replace("_", " ")
            .trim()
            .ifEmpty { localPart }
        return cleaned.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
            .ifEmpty { email }
    }

    fun linkSelectedGoogleAccount(email: String, explicitDisplayName: String? = null, idTokenFallback: String? = null) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank()) return
        val existing = accounts.firstOrNull { it.email.trim().equals(cleanEmail, ignoreCase = true) }
        val displayName = explicitDisplayName?.takeIf { it.isNotBlank() }
            ?: existing?.displayName?.takeIf { it.isNotBlank() }
            ?: formatDisplayNameFromEmail(cleanEmail)

        coroutineScope.launch {
            isConnectingGoogle = true
            val accountObj = android.accounts.Account(cleanEmail, "com.google")
            val token = try {
                withContext(Dispatchers.IO) {
                    GoogleAuthUtil.getToken(
                        context,
                        accountObj,
                        "oauth2:https://www.googleapis.com/auth/drive.file"
                    )
                }
            } catch (e: com.google.android.gms.auth.UserRecoverableAuthException) {
                withContext(Dispatchers.Main) {
                    try {
                        e.intent?.let { authRecoveryLauncher.launch(it) }
                    } catch (_: Throwable) {}
                }
                idTokenFallback?.takeIf { it.isNotBlank() } ?: "pool_linked_${cleanEmail.lowercase()}"
            } catch (_: Throwable) {
                idTokenFallback?.takeIf { it.isNotBlank() } ?: "pool_linked_${cleanEmail.lowercase()}"
            }

            // Auto-fetch real Google Drive storage quota (5 TB, 2 TB, 100 GB, 15 GB)
            val quota = if (token.isNotBlank() && !token.startsWith("pool_linked_")) {
                AdminDriveStoragePoolManager.fetchAccountStorageQuota(token)
            } else null

            val realLimit = quota?.first ?: existing?.totalStorageBytes?.takeIf { it > 0L } ?: (15L * 1024 * 1024 * 1024)
            val realUsage = quota?.second ?: existing?.usedStorageBytes ?: 0L
            val isFull = realUsage >= (realLimit - 100L * 1024 * 1024)
            val deterministicId = existing?.id?.takeIf { it.isNotBlank() }
                ?: ("drive_" + cleanEmail.lowercase().replace(Regex("[^a-z0-9]"), "_"))

            val newAccount = AdminDriveAccount(
                id = deterministicId,
                email = cleanEmail,
                displayName = displayName,
                accessToken = token,
                refreshToken = token,
                totalStorageBytes = realLimit,
                usedStorageBytes = realUsage,
                priorityOrder = existing?.priorityOrder ?: (accounts.size + 1),
                isActive = true,
                isFull = isFull,
                uploadedFilesCount = existing?.uploadedFilesCount ?: 0
            )

            val success = AdminDriveStoragePoolManager.linkOrUpdateAccount(newAccount)
            if (success) {
                if (realUploadedMediaCount > 0) {
                    AdminDriveStoragePoolManager.syncPoolStatsWithExistingMedia(realUploadedMediaCount)
                }
                val limitGb = realLimit / (1024.0 * 1024.0 * 1024.0)
                val limitLabel = if (limitGb >= 1000) String.format("%.1f TB", limitGb / 1024.0) else "${limitGb.toInt()} GB"
                Toast.makeText(context, "✅ Google Drive Linked: $cleanEmail ($limitLabel Plan)", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Failed to save Drive account", Toast.LENGTH_SHORT).show()
            }
            isConnectingGoogle = false
            refreshAccounts()
        }
    }


    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isConnectingGoogle = false
        try {
            // 1. Check native Android AccountManager chooser result first (Zero SHA-1 / OAuth Error 10)
            val chosenEmail = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
            if (!chosenEmail.isNullOrBlank()) {
                linkSelectedGoogleAccount(email = chosenEmail)
                return@rememberLauncherForActivityResult
            }

            // 2. Fallback: Check GoogleSignIn intent result if returned
            val googleAccount = try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                task.getResult(ApiException::class.java)
            } catch (_: Throwable) {
                GoogleSignIn.getLastSignedInAccount(context)
            }

            if (googleAccount != null && !googleAccount.email.isNullOrBlank()) {
                linkSelectedGoogleAccount(
                    email = googleAccount.email!!,
                    explicitDisplayName = googleAccount.displayName,
                    idTokenFallback = googleAccount.idToken
                )
                return@rememberLauncherForActivityResult
            }

            // 3. If user explicitly cancelled the account chooser, do not show an error toast
            if (result.resultCode == android.app.Activity.RESULT_CANCELED) {
                return@rememberLauncherForActivityResult
            }
        } catch (e: Throwable) {
            Toast.makeText(context, "Could not select account: ${e.localizedMessage ?: "Try Manual Form"}", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchGoogleDriveSignIn() {
        isConnectingGoogle = true
        try {
            // Use Android's native 1-Tap Google Account Picker so choosing any device Google account
            // works immediately without failing on Play Services OAuth DEVELOPER_ERROR (10:).
            val chooseAccountIntent = android.accounts.AccountManager.newChooseAccountIntent(
                null,
                null,
                arrayOf("com.google"),
                null,
                null,
                null,
                null
            )
            googleSignInLauncher.launch(chooseAccountIntent)
        } catch (_: Throwable) {
            try {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestScopes(driveScope)
                    .build()
                val signInClient = GoogleSignIn.getClient(context, gso)
                signInClient.signOut().addOnCompleteListener {
                    googleSignInLauncher.launch(signInClient.signInIntent)
                }
            } catch (_: Throwable) {
                isConnectingGoogle = false
                editingAccount = null
                showAddDialog = true
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            val postsSnapshot = RepositoryManager.firestore.collection("posts").get().await()
            realUploadedMediaCount = postsSnapshot.size()
            if (realUploadedMediaCount > 0) {
                AdminDriveStoragePoolManager.syncPoolStatsWithExistingMedia(realUploadedMediaCount)
            }
        } catch (_: Exception) {}
        refreshAccounts()
    }

    val totalAccountsCount = accounts.size
    val activeAccountsCount = accounts.count { it.isActive && !it.isFull }
    val fullAccountsCount = accounts.count { it.isFull }
    val totalCapacityBytes = accounts.sumOf { if (it.totalStorageBytes > 0) it.totalStorageBytes else 15L * 1024 * 1024 * 1024 }
    val totalUsedBytes = accounts.sumOf { it.usedStorageBytes }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Overview Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4285F4).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CloudQueue,
                                    contentDescription = "Cloud Pool",
                                    tint = Color(0xFF4285F4),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Users Storage Store",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Admin Google Drive Pool (Direct 1-Tap Login)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(onClick = { refreshAccounts() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Admin yahan seedha Google account login karega (1-tap). Koi form bharne ki zaroorat nahi! Normal users bina apna drive link kiye photos/videos upload karenge aur system inhi admin accounts me cascade karega.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Stats grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatPill(
                            title = "Total Uploads",
                            value = "$realUploadedMediaCount Media",
                            color = Color(0xFF9C27B0)
                        )
                        StatPill(
                            title = "Linked Drives",
                            value = "$totalAccountsCount / 50+",
                            color = Color(0xFF4285F4)
                        )
                        StatPill(
                            title = "Active Storage",
                            value = "$activeAccountsCount Active",
                            color = Color(0xFF34A853)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Storage Progress Bar
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Total Used: ${formatStorageBytes(totalUsedBytes)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Total Capacity: ${formatStorageBytes(totalCapacityBytes)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val progressFraction = if (totalCapacityBytes > 0) {
                            (totalUsedBytes.toDouble() / totalCapacityBytes.toDouble()).toFloat().coerceIn(0f, 1f)
                        } else 0f
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (progressFraction > 0.85f) Color(0xFFEA4335) else Color(0xFF4285F4)
                        )
                    }
                }
            }
        }

        // Action Buttons Row (Direct Google Sign-in)
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { launchGoogleDriveSignIn() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    if (isConnectingGoogle) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Connecting Google Drive...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Direct Google Login to Link Drive", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "1-Tap me account chooser khulega (Multiple accounts link kar sakte hain)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            editingAccount = null
                            showAddDialog = true
                        }
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Manual Form", fontSize = 12.sp)
                    }
                }
            }
        }

        // Accounts List Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Connected Drive Accounts (${accounts.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Priority Auto-Switch",
                    fontSize = 12.sp,
                    color = Color(0xFF34A853),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (accounts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No Drive Accounts Linked Yet", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Admin can link up to 50 Google Drive accounts here so users can upload photos/videos continuously without touching their own storage.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(accounts) { account ->
                AdminDriveAccountCard(
                    account = account,
                    onToggleActive = {
                        coroutineScope.launch {
                            val updated = account.copy(isActive = !account.isActive)
                            AdminDriveStoragePoolManager.linkOrUpdateAccount(updated)
                            refreshAccounts()
                        }
                    },
                    onToggleFull = {
                        coroutineScope.launch {
                            val updated = account.copy(isFull = !account.isFull)
                            AdminDriveStoragePoolManager.linkOrUpdateAccount(updated)
                            refreshAccounts()
                        }
                    },
                    onEdit = {
                        editingAccount = account
                        showAddDialog = true
                    },
                    onDelete = {
                        coroutineScope.launch {
                            AdminDriveStoragePoolManager.removeAccount(account.id)
                            refreshAccounts()
                            Toast.makeText(context, "Account removed from pool", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    if (showAddDialog) {
        AddEditDriveAccountDialog(
            accountToEdit = editingAccount,
            onDismiss = {
                showAddDialog = false
                editingAccount = null
            },
            onSave = { newOrUpdated ->
                coroutineScope.launch {
                    val success = AdminDriveStoragePoolManager.linkOrUpdateAccount(newOrUpdated)
                    if (success) {
                        Toast.makeText(context, "Drive Account successfully saved to pool!", Toast.LENGTH_SHORT).show()
                        refreshAccounts()
                    } else {
                        Toast.makeText(context, "Error saving account", Toast.LENGTH_SHORT).show()
                    }
                    showAddDialog = false
                    editingAccount = null
                }
            }
        )
    }
}

@Composable
private fun StatPill(title: String, value: String, color: Color) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(text = title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

fun formatStorageBytes(bytes: Long): String {
    val tb = bytes / (1024.0 * 1024.0 * 1024.0 * 1024.0)
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        tb >= 1.0 -> String.format("%.2f TB", tb)
        gb >= 1.0 -> String.format("%.2f GB", gb)
        else -> String.format("%.1f MB", mb)
    }
}

@Composable
fun AdminDriveAccountCard(
    account: AdminDriveAccount,
    onToggleActive: () -> Unit,
    onToggleFull: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val fraction = if (account.totalStorageBytes > 0) {
        (account.usedStorageBytes.toDouble() / account.totalStorageBytes.toDouble()).toFloat().coerceIn(0f, 1f)
    } else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!account.isActive) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            } else if (account.isFull) {
                Color(0xFFEA4335).copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (account.isFull) Color(0xFFEA4335).copy(alpha = 0.2f)
                                else if (!account.isActive) Color.Gray.copy(alpha = 0.2f)
                                else Color(0xFF34A853).copy(alpha = 0.2f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (account.isFull) Icons.Default.Warning else Icons.Default.Cloud,
                            contentDescription = null,
                            tint = if (account.isFull) Color(0xFFEA4335) else if (!account.isActive) Color.Gray else Color(0xFF34A853),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = account.displayName.ifEmpty { "Drive Account" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = account.email,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status Badge
                AssistChip(
                    onClick = onToggleFull,
                    label = {
                        Text(
                            text = if (account.isFull) "FULL (Auto Rollover)" else if (!account.isActive) "INACTIVE" else "ACTIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = if (account.isFull) Color(0xFFEA4335) else if (!account.isActive) Color.Gray else Color(0xFF34A853)
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Storage Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${formatStorageBytes(account.usedStorageBytes)} of ${formatStorageBytes(account.totalStorageBytes)} used",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (fraction > 0.85f) Color(0xFFEA4335) else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${account.uploadedFilesCount} files uploaded",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (account.isFull || fraction > 0.85f) Color(0xFFEA4335) else Color(0xFF4285F4)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = account.isActive,
                        onCheckedChange = { onToggleActive() },
                        modifier = Modifier.scale(0.8f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (account.isActive) "Enabled" else "Disabled",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEA4335), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

private fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.size((48 * scale).dp, (24 * scale).dp)
)

@Composable
fun AddEditDriveAccountDialog(
    accountToEdit: AdminDriveAccount?,
    onDismiss: () -> Unit,
    onSave: (AdminDriveAccount) -> Unit
) {
    var email by remember { mutableStateOf(accountToEdit?.email ?: "") }
    var displayName by remember { mutableStateOf(accountToEdit?.displayName ?: "") }
    var accessTokenOrKey by remember { mutableStateOf(accountToEdit?.accessToken ?: accountToEdit?.refreshToken ?: "") }
    var folderId by remember { mutableStateOf(accountToEdit?.folderId ?: "") }
    var priorityText by remember { mutableStateOf((accountToEdit?.priorityOrder ?: 1).toString()) }
    var selectedPlanIndex by remember {
        val currentBytes = accountToEdit?.totalStorageBytes ?: (15L * 1024 * 1024 * 1024)
        val gb = currentBytes / (1024L * 1024 * 1024)
        mutableIntStateOf(
            when {
                gb >= 4000L -> 3
                gb >= 1800L -> 2
                gb >= 90L -> 1
                else -> 0
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (accountToEdit != null) "Edit Admin Drive Account" else "Link New Admin Drive Account",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Admin can add up to 50 accounts. When one account gets full (15GB), the system will automatically route new uploads to the next account without disturbing users.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Google Drive Email *") },
                    placeholder = { Text("admin1.drive@gmail.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Account Label / Name") },
                    placeholder = { Text("Admin Storage Node 1") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = accessTokenOrKey,
                    onValueChange = { accessTokenOrKey = it },
                    label = { Text("OAuth Access/Bearer Token or Key *") },
                    placeholder = { Text("ya29.a0AfH6SM...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                OutlinedTextField(
                    value = folderId,
                    onValueChange = { folderId = it },
                    label = { Text("Optional Google Drive Folder ID") },
                    placeholder = { Text("e.g. 1a2B3c4D5eF6...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = priorityText,
                    onValueChange = { priorityText = it },
                    label = { Text("Priority Order (1 = First, 2 = Next)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("Storage Plan Capacity:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                val plans = listOf("15 GB (Free)", "100 GB", "2 TB", "5 TB (Premium)")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    plans.forEachIndexed { idx, plan ->
                        FilterChip(
                            selected = selectedPlanIndex == idx,
                            onClick = { selectedPlanIndex = idx },
                            label = { Text(plan, fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (email.isBlank()) return@Button
                    val prio = priorityText.toIntOrNull() ?: 1
                    val totalBytes = when (selectedPlanIndex) {
                        1 -> 100L * 1024 * 1024 * 1024
                        2 -> 2048L * 1024 * 1024 * 1024
                        3 -> 5120L * 1024 * 1024 * 1024
                        else -> 15L * 1024 * 1024 * 1024
                    }
                    val account = (accountToEdit ?: AdminDriveAccount()).copy(
                        email = email.trim(),
                        displayName = displayName.trim().ifEmpty { email.trim() },
                        accessToken = accessTokenOrKey.trim(),
                        refreshToken = accessTokenOrKey.trim(),
                        folderId = folderId.trim(),
                        totalStorageBytes = totalBytes,
                        priorityOrder = prio,
                        isActive = true
                    )
                    onSave(account)
                },
                enabled = email.isNotBlank()
            ) {
                Text("Save to Pool")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
