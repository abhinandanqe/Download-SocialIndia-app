package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.model.GroupChat
import com.example.model.User
import com.example.utils.SoundManager

@Composable
fun GroupMemberPreviewDialog(
    user: User,
    group: GroupChat,
    currentUserId: String,
    onDismiss: () -> Unit,
    onStartDirectChat: (User) -> Unit,
    onViewProfile: (String) -> Unit,
    onPromoteToAdmin: (() -> Unit)? = null,
    onDemoteAdmin: (() -> Unit)? = null,
    onRemoveFromGroup: (() -> Unit)? = null
) {
    val isOwner = user.id == group.createdBy
    val isAdmin = group.admins.contains(user.id)
    val currentUserIsAdmin = group.admins.contains(currentUserId)
    val isSelf = user.id == currentUserId

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with close button
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = {
                            SoundManager.playClickSound()
                            onDismiss()
                        },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                // Avatar
                Box(contentAlignment = Alignment.BottomEnd) {
                    AsyncImage(
                        model = user.avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${user.id}" },
                        contentDescription = "User Avatar",
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .border(3.dp, if (isAdmin) Color(0xFFFF9800) else MaterialTheme.colorScheme.primary, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    if (isAdmin) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF9800)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = "Admin",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Name and Verified
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.username.ifEmpty { "User" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    if (user.isVerified || user.verificationBadgeUrl.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = Color(0xFF1DA1F2),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Role Chip
                AssistChip(
                    onClick = {},
                    leadingIcon = {
                        Icon(
                            if (isOwner) Icons.Default.Star else if (isAdmin) Icons.Default.Shield else Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = when {
                                isOwner -> "Group Creator"
                                isAdmin -> "Group Admin"
                                else -> "Member"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (isOwner) Color(0xFFFFD700).copy(alpha = 0.2f)
                                         else if (isAdmin) Color(0xFFFF9800).copy(alpha = 0.2f)
                                         else MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                if (user.bio.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = user.bio,
                        fontSize = 13.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions: Direct Message and View Profile
                if (!isSelf) {
                    Button(
                        onClick = {
                            SoundManager.playClickSound()
                            onDismiss()
                            onStartDirectChat(user)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Direct Message", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedButton(
                    onClick = {
                        SoundManager.playClickSound()
                        onDismiss()
                        onViewProfile(user.id)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Profile")
                }

                // Admin Controls (for managing this member)
                if (currentUserIsAdmin && !isSelf && !isOwner) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Admin Controls",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isAdmin) {
                            OutlinedButton(
                                onClick = {
                                    SoundManager.playClickSound()
                                    onDismiss()
                                    onDemoteAdmin?.invoke()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Dismiss Admin", fontSize = 12.sp)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    SoundManager.playClickSound()
                                    onDismiss()
                                    onPromoteToAdmin?.invoke()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Make Admin", fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = {
                                SoundManager.playClickSound()
                                onDismiss()
                                onRemoveFromGroup?.invoke()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Remove", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
