package com.example.ui

import android.widget.Toast
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.GroupChat
import com.example.model.User
import com.example.repository.GroupChatRepository
import com.example.utils.SoundManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoBottomSheet(
    group: GroupChat,
    currentUserId: String,
    currentUserName: String,
    allUsers: List<User>,
    onDismiss: () -> Unit,
    onGroupLeftOrDeleted: () -> Unit,
    onMemberClick: (User) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isOwner = group.createdBy == currentUserId
    val isAdmin = group.admins.contains(currentUserId)

    var showEditInfoDialog by remember { mutableStateOf(false) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showLeaveConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Real-time permissions state
    var onlyAdminsCanPost by remember(group.onlyAdminsCanPost) { mutableStateOf(group.onlyAdminsCanPost) }
    var onlyAdminsCanEdit by remember(group.onlyAdminsCanEditGroup) { mutableStateOf(group.onlyAdminsCanEditGroup) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Group Header Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = group.avatarUrl,
                    contentDescription = "Group Icon",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = group.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    )
                    if (isAdmin || !group.onlyAdminsCanEditGroup) {
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                SoundManager.playClickSound()
                                showEditInfoDialog = true
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Group Info", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                if (group.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = group.description,
                        fontSize = 13.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${group.members.size} members • ${group.admins.size} admins",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Action Buttons: Add Member, Media, Search
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (isAdmin || !group.onlyAdminsCanEditGroup) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                SoundManager.playClickSound()
                                showAddMemberDialog = true
                            }
                            .padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Add Member", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            SoundManager.playClickSound()
                            Toast.makeText(context, "Announcements posted: ${group.announcementCount}", Toast.LENGTH_SHORT).show()
                        }
                        .padding(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Announcements", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Granular Admin Permissions (Admin only)
            if (isAdmin) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = "Admin Permissions",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Only Admins Can Post", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Announcement-only mode", fontSize = 12.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = onlyAdminsCanPost,
                            onCheckedChange = { newVal ->
                                SoundManager.playClickSound()
                                onlyAdminsCanPost = newVal
                                scope.launch {
                                    GroupChatRepository.updateGroupPermissions(
                                        groupId = group.id,
                                        onlyAdminsCanPost = newVal,
                                        onlyAdminsCanEditGroup = onlyAdminsCanEdit
                                    )
                                    Toast.makeText(context, "Permissions updated", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Only Admins Can Edit Info", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Name, photo, and settings", fontSize = 12.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = onlyAdminsCanEdit,
                            onCheckedChange = { newVal ->
                                SoundManager.playClickSound()
                                onlyAdminsCanEdit = newVal
                                scope.launch {
                                    GroupChatRepository.updateGroupPermissions(
                                        groupId = group.id,
                                        onlyAdminsCanPost = onlyAdminsCanPost,
                                        onlyAdminsCanEditGroup = newVal
                                    )
                                    Toast.makeText(context, "Permissions updated", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            }

            // Members List Section
            Text(
                text = "Members (${group.members.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(group.members) { memberId ->
                    val memberUser = allUsers.firstOrNull { it.id == memberId } ?: User(
                        id = memberId,
                        username = if (memberId == currentUserId) "$currentUserName (You)" else "User_${memberId.take(5)}",
                        avatarUrl = "https://api.dicebear.com/7.x/avataaars/png?seed=$memberId"
                    )

                    val isMemberAdmin = group.admins.contains(memberId)
                    val isMemberOwner = group.createdBy == memberId

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                SoundManager.playClickSound()
                                onMemberClick(memberUser)
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = memberUser.avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=$memberId" },
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (memberId == currentUserId) "${memberUser.username} (You)" else memberUser.username,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            if (memberUser.bio.isNotEmpty()) {
                                Text(
                                    text = memberUser.bio,
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (isMemberOwner) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Owner", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFFFD700).copy(alpha = 0.2f))
                            )
                        } else if (isMemberAdmin) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Admin", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFFF9800).copy(alpha = 0.2f))
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Leave or Delete Group
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        SoundManager.playClickSound()
                        showLeaveConfirmDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Leave Group")
                }

                if (isOwner) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            SoundManager.playClickSound()
                            showDeleteConfirmDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete Group for Everyone")
                    }
                }
            }
        }
    }

    // Edit Group Info Dialog
    if (showEditInfoDialog) {
        var newName by remember { mutableStateOf(group.name) }
        var newDesc by remember { mutableStateOf(group.description) }

        AlertDialog(
            onDismissRequest = { showEditInfoDialog = false },
            title = { Text("Edit Group Info") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { if (it.length <= 40) newName = it },
                        label = { Text("Group Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newDesc,
                        onValueChange = { if (it.length <= 150) newDesc = it },
                        label = { Text("Description") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        SoundManager.playClickSound()
                        if (newName.trim().isNotEmpty()) {
                            scope.launch {
                                GroupChatRepository.updateGroupInfo(
                                    groupId = group.id,
                                    name = newName.trim(),
                                    description = newDesc.trim(),
                                    avatarUrl = group.avatarUrl
                                )
                                Toast.makeText(context, "Group info updated!", Toast.LENGTH_SHORT).show()
                                showEditInfoDialog = false
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditInfoDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Member Dialog
    if (showAddMemberDialog) {
        val nonMembers = allUsers.filter { !group.members.contains(it.id) }
        val toAddIds = remember { mutableStateListOf<String>() }

        AlertDialog(
            onDismissRequest = { showAddMemberDialog = false },
            title = { Text("Add Members") },
            text = {
                if (nonMembers.isEmpty()) {
                    Text("All your contacts are already members of this group!")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        items(nonMembers) { u ->
                            val checked = toAddIds.contains(u.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (checked) toAddIds.remove(u.id) else toAddIds.add(u.id)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = u.avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${u.id}" },
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp).clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(u.username, modifier = Modifier.weight(1f))
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        if (checked) toAddIds.remove(u.id) else toAddIds.add(u.id)
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (nonMembers.isNotEmpty()) {
                    Button(
                        onClick = {
                            SoundManager.playClickSound()
                            if (toAddIds.isNotEmpty()) {
                                scope.launch {
                                    GroupChatRepository.addMembers(
                                        groupId = group.id,
                                        newMemberIds = toAddIds.toList(),
                                        actorName = currentUserName
                                    )
                                    Toast.makeText(context, "Added ${toAddIds.size} member(s)", Toast.LENGTH_SHORT).show()
                                    showAddMemberDialog = false
                                }
                            }
                        },
                        enabled = toAddIds.isNotEmpty()
                    ) {
                        Text("Add (${toAddIds.size})")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMemberDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Leave Confirmation Dialog
    if (showLeaveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirmDialog = false },
            title = { Text("Leave \"${group.name}\"?") },
            text = { Text("You won't receive any more messages from this group.") },
            confirmButton = {
                Button(
                    onClick = {
                        SoundManager.playClickSound()
                        scope.launch {
                            GroupChatRepository.leaveGroup(
                                groupId = group.id,
                                userId = currentUserId,
                                userName = currentUserName
                            )
                            Toast.makeText(context, "You left the group", Toast.LENGTH_SHORT).show()
                            showLeaveConfirmDialog = false
                            onDismiss()
                            onGroupLeftOrDeleted()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Group?") },
            text = { Text("This will permanently delete the group and all its message history for all members. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        SoundManager.playClickSound()
                        scope.launch {
                            GroupChatRepository.deleteGroup(group.id)
                            Toast.makeText(context, "Group deleted", Toast.LENGTH_SHORT).show()
                            showDeleteConfirmDialog = false
                            onDismiss()
                            onGroupLeftOrDeleted()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
