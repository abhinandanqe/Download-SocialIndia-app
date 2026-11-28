package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.RepositoryManager
import com.example.model.SuggestedUser
import com.example.model.User
import com.example.ui.theme.SocialIndiaBlue
import com.example.utils.SoundManager
import com.example.utils.bounceClick
import kotlinx.coroutines.launch

/**
 * Universal, responsive and consistent Social India Follow Button.
 * Synchronizes with Room DB and Firestore in real-time, with zero lag optimistic updates.
 */
@Composable
fun SocialFollowButton(
    targetUser: User,
    currentUserId: String = RepositoryManager.getCurrentUserId(),
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    onStateChanged: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isFollowing by remember(targetUser.id) {
        mutableStateOf(RepositoryManager.isFollowingCached(targetUser.id))
    }
    var isRequested by remember(targetUser.id) {
        mutableStateOf(RepositoryManager.isFollowRequestedCached(targetUser.id))
    }
    var isLoading by remember { mutableStateOf(false) }

    // Real-time synchronization flow (debounced so fast feed scrolling skips transient cards)
    LaunchedEffect(targetUser.id, currentUserId) {
        if (currentUserId.isNotEmpty() && targetUser.id.isNotEmpty() && currentUserId != targetUser.id) {
            kotlinx.coroutines.delay(450)
            RepositoryManager.socialRepository.isFollowing(currentUserId, targetUser.id).collect { followingState ->
                isFollowing = followingState
                if (followingState) isRequested = false
            }
        }
    }

    if (currentUserId.isNotEmpty() && currentUserId == targetUser.id) {
        // Self: do not display follow button
        return
    }

    val reducedMotion = com.example.ui.motion.rememberReducedMotionEnabled()

    val buttonScale by animateFloatAsState(
        targetValue = if (isLoading) com.example.ui.motion.SocialMotion.PressScale else 1f,
        animationSpec = com.example.ui.motion.SocialMotion.springSnappy(reducedMotion),
        label = "follow_scale"
    )

    val containerColor by animateColorAsState(
        targetValue = when {
            isFollowing -> MaterialTheme.colorScheme.surfaceVariant
            isRequested -> MaterialTheme.colorScheme.surfaceVariant
            else -> SocialIndiaBlue
        },
        animationSpec = com.example.ui.motion.SocialMotion.colorSpec(
            durationMillis = com.example.ui.motion.SocialMotion.DurationFast,
            reducedMotion = reducedMotion
        ),
        label = "follow_bg"
    )

    val textColor by animateColorAsState(
        targetValue = when {
            isFollowing -> MaterialTheme.colorScheme.onSurfaceVariant
            isRequested -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> Color.White
        },
        animationSpec = com.example.ui.motion.SocialMotion.colorSpec(
            durationMillis = com.example.ui.motion.SocialMotion.DurationFast,
            reducedMotion = reducedMotion
        ),
        label = "follow_fg"
    )

    val buttonText = when {
        isFollowing -> "Following"
        isRequested -> "Requested"
        else -> "Follow"
    }

    Button(
        onClick = {
            if (isLoading) return@Button
            SoundManager.playClickSound()

            if (currentUserId.isEmpty()) {
                Toast.makeText(context, "Please login first to follow creators!", Toast.LENGTH_SHORT).show()
                return@Button
            }

            scope.launch {
                isLoading = true
                try {
                    if (isFollowing) {
                        // Unfollow
                        isFollowing = false
                        RepositoryManager.setFollowingCached(targetUser.id, false)
                        RepositoryManager.socialRepository.unfollowUser(currentUserId, targetUser.id)
                        onStateChanged?.invoke(false)
                    } else if (isRequested) {
                        // Cancel Request
                        isRequested = false
                        RepositoryManager.setFollowRequestedCached(targetUser.id, false)
                        RepositoryManager.socialRepository.cancelFollowRequest(currentUserId, targetUser.id)
                        onStateChanged?.invoke(false)
                    } else {
                        // Check if target account is private
                        if (targetUser.isPrivate) {
                            isRequested = true
                            RepositoryManager.setFollowRequestedCached(targetUser.id, true)
                            RepositoryManager.socialRepository.sendFollowRequest(currentUserId, targetUser.id)
                            Toast.makeText(context, "Follow request sent to @${targetUser.username}", Toast.LENGTH_SHORT).show()
                        } else {
                            isFollowing = true
                            RepositoryManager.setFollowingCached(targetUser.id, true)
                            RepositoryManager.socialRepository.followUser(currentUserId, targetUser.id)
                            onStateChanged?.invoke(true)
                        }
                    }
                } catch (e: Exception) {
                    // Revert in case of unexpected failure
                    isFollowing = RepositoryManager.isFollowingCached(targetUser.id)
                    isRequested = RepositoryManager.isFollowRequestedCached(targetUser.id)
                    Toast.makeText(context, "Network issue, please try again", Toast.LENGTH_SHORT).show()
                } finally {
                    isLoading = false
                }
            }
        },
        modifier = modifier
            .graphicsLayer {
                scaleX = buttonScale
                scaleY = buttonScale
            }
            .bounceClick(enableHaptics = false)
            .height(if (isCompact) 32.dp else 36.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = textColor,
            disabledContainerColor = containerColor.copy(alpha = 0.6f),
            disabledContentColor = textColor.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = if (isCompact) 12.dp else 16.dp, vertical = 0.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = textColor,
                strokeWidth = 2.dp
            )
        } else {
            AnimatedContent(
                targetState = buttonText,
                transitionSpec = {
                    fadeIn(
                        com.example.ui.motion.SocialMotion.floatSpec(
                            com.example.ui.motion.SocialMotion.DurationQuick,
                            reducedMotion
                        )
                    ).togetherWith(
                        fadeOut(
                            com.example.ui.motion.SocialMotion.floatSpec(
                                com.example.ui.motion.SocialMotion.DurationQuick,
                                reducedMotion
                            )
                        )
                    )
                },
                label = "follow_label_morph"
            ) { label ->
                Text(
                    text = label,
                    fontSize = if (isCompact) 12.sp else 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Compact horizontal Quick Follow card for Home Feed and Profile.
 */
@Composable
fun QuickFollowCard(
    suggestedUser: SuggestedUser,
    onNavigateToProfile: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val user = suggestedUser.user
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .width(155.dp)
            .wrapContentHeight()
            .border(
                width = 0.8.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Dismiss button (Top right)
            IconButton(
                onClick = {
                    SoundManager.playClickSound()
                    onDismiss()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(15.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToProfile(user.id) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(6.dp))

                // Avatar
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = user.avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${user.id}" },
                        contentDescription = user.username,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Username & Verification
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = user.username,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (user.isVerified) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = Color(0xFF1DA1F2),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                // Display name / Reason
                Text(
                    text = if (user.displayName.isNotEmpty() && user.displayName != user.username) user.displayName else suggestedUser.reason,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Follow CTA Button
                SocialFollowButton(
                    targetUser = user,
                    isCompact = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Horizontally scrolling Suggested People row for Home Feed and Profile empty states.
 */
@Composable
fun QuickFollowHorizontalSection(
    suggestedUsers: List<SuggestedUser>,
    onNavigateToProfile: (String) -> Unit,
    onSeeAllClick: () -> Unit,
    onDismissUser: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Suggested for you"
) {
    if (suggestedUsers.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 12.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "See all",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = SocialIndiaBlue,
                modifier = Modifier
                    .bounceClick()
                    .clickable {
                        SoundManager.playClickSound()
                        onSeeAllClick()
                    }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Horizontal Carousel
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(suggestedUsers, key = { it.user.id }) { item ->
                QuickFollowCard(
                    suggestedUser = item,
                    onNavigateToProfile = onNavigateToProfile,
                    onDismiss = { onDismissUser(item.user.id) }
                )
            }
        }
    }
}

/**
 * Vertical user row for the dedicated Discover People list and search pages.
 */
@Composable
fun DiscoverPeopleUserRow(
    suggestedUser: SuggestedUser,
    onNavigateToProfile: (String) -> Unit,
    onDismissUser: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val user = suggestedUser.user
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onNavigateToProfile(user.id) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Circular profile photo
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = user.avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${user.id}" },
                contentDescription = user.username,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Name, username and suggestion label
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.username,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (user.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = "Verified",
                        tint = Color(0xFF1DA1F2),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            val subtitle = if (user.displayName.isNotEmpty() && user.displayName != user.username) {
                user.displayName
            } else {
                suggestedUser.reason
            }

            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (user.displayName.isNotEmpty() && user.displayName != user.username) {
                Text(
                    text = suggestedUser.reason,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Follow Button
        SocialFollowButton(
            targetUser = user,
            isCompact = true
        )

        // Three-dot menu
        Box {
            IconButton(
                onClick = {
                    SoundManager.playClickSound()
                    showMenu = true
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                DropdownMenuItem(
                    text = { Text("Not interested", color = MaterialTheme.colorScheme.onSurface) },
                    leadingIcon = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        showMenu = false
                        onDismissUser(user.id)
                        Toast.makeText(context, "Suggestion hidden", Toast.LENGTH_SHORT).show()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Block @${user.username}", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Outlined.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        val currentUserId = RepositoryManager.getCurrentUserId()
                        scope.launch {
                            RepositoryManager.socialRepository.blockUser(currentUserId, user.id)
                            onDismissUser(user.id)
                            Toast.makeText(context, "@${user.username} blocked", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                DropdownMenuItem(
                    text = { Text("Report profile", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Outlined.Report, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        val currentUserId = RepositoryManager.getCurrentUserId()
                        scope.launch {
                            RepositoryManager.socialRepository.reportUser(currentUserId, user.id, "Inappropriate profile")
                            onDismissUser(user.id)
                            Toast.makeText(context, "Report submitted. Thank you.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}
