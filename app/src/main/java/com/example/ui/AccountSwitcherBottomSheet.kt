package com.example.ui

import android.widget.Toast
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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.auth.MultiAccountManager
import com.example.auth.SavedAccount
import com.example.model.RepositoryManager
import com.example.utils.bounceClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSwitcherBottomSheet(
    onDismissRequest: () -> Unit,
    onAddAccount: () -> Unit,
    onAccountSwitched: (SavedAccount) -> Unit = {}
) {
    val context = LocalContext.current
    val accounts by MultiAccountManager.accountsList.collectAsStateWithLifecycle()
    val activeAccount by MultiAccountManager.activeAccount.collectAsStateWithLifecycle()
    val isSwitching by MultiAccountManager.isSwitching.collectAsStateWithLifecycle()

    val currentUid = RepositoryManager.getCurrentUserId()
    var accountToRemove by remember { mutableStateOf<SavedAccount?>(null) }
    var accountToLogout by remember { mutableStateOf<SavedAccount?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color(0xFF181818),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Switch Accounts",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isSwitching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF3897F0)
                    )
                } else {
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (accounts.isEmpty()) {
                // No accounts recorded yet
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No saved accounts on this device",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(accounts, key = { it.uid }) { account ->
                        val isActive = account.uid == currentUid || account.uid == activeAccount?.uid
                        var showMenu by remember { mutableStateOf(false) }

                        AccountItemRow(
                            account = account,
                            isActive = isActive,
                            onSelect = {
                                if (!isActive && !isSwitching) {
                                    MultiAccountManager.switchAccount(context, account.uid) { success, errMsg ->
                                        if (success) {
                                            Toast.makeText(context, "Switched to @${account.username}", Toast.LENGTH_SHORT).show()
                                            onAccountSwitched(account)
                                            onDismissRequest()
                                        } else {
                                            Toast.makeText(context, errMsg ?: "Failed to switch", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            onMoreOptions = { showMenu = true }
                        )

                        // Dropdown menu for account actions
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(Color(0xFF262626))
                            ) {
                                if (isActive) {
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Outlined.Logout, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text("Log out of @${account.username}", color = Color.White)
                                            }
                                        },
                                        onClick = {
                                            showMenu = false
                                            accountToLogout = account
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("Remove from device", color = Color(0xFFFF5252))
                                        }
                                    },
                                    onClick = {
                                        showMenu = false
                                        accountToRemove = account
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Add Account Option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .bounceClick()
                    .clickable {
                        onDismissRequest()
                        onAddAccount()
                    }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                        .background(Color.White.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Account",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Add Account",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Log into an existing account or create a new one",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Confirmation Dialog for "Remove Account From Device"
    val targetToRemove = accountToRemove
    if (targetToRemove != null) {
        val target = targetToRemove
        AlertDialog(
            onDismissRequest = { accountToRemove = null },
            containerColor = Color(0xFF242424),
            title = {
                Text(
                    text = "Remove @${target.username} from device?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "This will only remove @${target.username} from this device's account switcher.\n\nYour Social India account, posts, messages, and profile will NOT be deleted from Firebase.",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        MultiAccountManager.removeAccountFromDevice(context, target.uid)
                        Toast.makeText(context, "Account removed from device", Toast.LENGTH_SHORT).show()
                        accountToRemove = null
                        if (accounts.size <= 1) {
                            onDismissRequest()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("Remove", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToRemove = null }) {
                    Text("Cancel", color = Color.LightGray)
                }
            }
        )
    }

    // Confirmation Dialog for "Log Out"
    val targetToLogout = accountToLogout
    if (targetToLogout != null) {
        val target = targetToLogout
        AlertDialog(
            onDismissRequest = { accountToLogout = null },
            containerColor = Color(0xFF242424),
            title = {
                Text(
                    text = "Log out of @${target.username}?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "You can log back in at any time from this account switcher or by entering your password.",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        MultiAccountManager.logoutCurrentAccount(context)
                        Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
                        accountToLogout = null
                        onDismissRequest()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("Log Out", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToLogout = null }) {
                    Text("Cancel", color = Color.LightGray)
                }
            }
        )
    }
}

@Composable
private fun AccountItemRow(
    account: SavedAccount,
    isActive: Boolean,
    onSelect: () -> Unit,
    onMoreOptions: () -> Unit
) {
    val bgModifier = if (isActive) {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color(0xFF3897F0).copy(alpha = 0.6f), RoundedCornerShape(14.dp))
    } else {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.03f))
    }

    Row(
        modifier = bgModifier
            .bounceClick()
            .clickable(onClick = onSelect)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            if (account.avatarUrl.isNotEmpty()) {
                AsyncImage(
                    model = account.avatarUrl,
                    contentDescription = "Avatar",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Username & Display Name
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = account.username.ifEmpty { "User" },
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (account.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "Verified",
                        tint = Color(0xFF1DA1F2),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            if (account.displayName.isNotEmpty() && account.displayName != account.username) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = account.displayName,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Active Checkmark or Radio indicator
        if (isActive) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF3897F0).copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Active",
                        color = Color(0xFF3897F0),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Active Account",
                    tint = Color(0xFF3897F0),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        IconButton(onClick = onMoreOptions, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Account options",
                tint = Color.LightGray.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
