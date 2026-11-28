package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.LivePresenceManager
import com.example.model.StoryUiModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.example.utils.ExoPlayerUtils
import com.example.utils.bounceClick

private val StoryInstagramGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFFF09433),
        Color(0xFFE6683C),
        Color(0xFFDC2743),
        Color(0xFFCC2366),
        Color(0xFFBC1888)
    )
)
private val StoryCloseFriendsGradient = Brush.linearGradient(
    listOf(Color(0xFF10B981), Color(0xFF059669), Color(0xFF34D399))
)
private val StoryLiveGradient = Brush.linearGradient(
    listOf(Color(0xFFE91E63), Color(0xFFFF5252), Color(0xFFFF1744))
)
private val StorySeenMutedBrush = Brush.linearGradient(
    listOf(Color.Gray.copy(alpha = 0.45f), Color.Gray.copy(alpha = 0.35f))
)
private val StoryEmptyRingBrush = Brush.linearGradient(
    listOf(Color.LightGray.copy(alpha = 0.35f), Color.LightGray.copy(alpha = 0.35f))
)
private val StoryUploadSweepBrush = Brush.sweepGradient(
    listOf(
        Color(0xFFF09433),
        Color(0xFFE6683C),
        Color(0xFFDC2743),
        Color(0xFFCC2366),
        Color(0xFFBC1888),
        Color(0xFFF09433)
    )
)

@Composable
fun OriginalStoryTray(
    stories: List<StoryUiModel>,
    currentUserId: String,
    userAvatar: String,
    onStoryClick: (List<StoryUiModel>, Int) -> Unit,
    onLiveClick: (StoryUiModel) -> Unit = {},
    onCreateStoryClick: () -> Unit
) {
    val context = LocalContext.current
    val activeLives by LivePresenceManager.activeLives.collectAsStateWithLifecycle()
    val isPostingStory by com.example.ui.GlobalState.isPostingStory.collectAsStateWithLifecycle()
    val currentUserRealtime by com.example.model.RepositoryManager.currentUserState.collectAsStateWithLifecycle()
    val isMyAccountVerified = remember(currentUserRealtime, stories) {
        val u = currentUserRealtime
        val fromUser = u != null && !u.verificationSuspended && (
            u.isVerified ||
            u.verificationStatus == "approved" ||
            u.verificationStatus == com.example.model.VerificationStatus.VERIFIED_ACTIVE
        )
        fromUser || (stories.firstOrNull { it.userId == currentUserId }?.isVerified == true)
    }

    val storiesByUser = remember(stories) { stories.filter { !it.isLive }.groupBy { it.userId } }
    val myStories = remember(storiesByUser, currentUserId) { storiesByUser[currentUserId].orEmpty() }
    val myLatestStory = myStories.firstOrNull()
    val hasStory = myStories.isNotEmpty()
    val myIsLive = currentUserId.isNotEmpty() && activeLives.containsKey(currentUserId)

    val seenStoryUserIds = remember { androidx.compose.runtime.mutableStateListOf<String>() }

    // Bounce-scale animation when story upload completes (Posting... -> Active Ring)
    val myAvatarScale = remember { androidx.compose.animation.core.Animatable(1f) }
    var wasPostingStory by remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(isPostingStory) {
        if (isPostingStory) {
            wasPostingStory = true
        } else if (wasPostingStory) {
            wasPostingStory = false
            myAvatarScale.snapTo(0.85f)
            myAvatarScale.animateTo(
                targetValue = 1.18f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                )
            )
            myAvatarScale.animateTo(
                targetValue = 1.0f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                )
            )
        }
    }

    // Group & dynamically sort other user stories in O(N log N) without nested filters:
    // 0 = Live, 1 = Unseen Close Friends, 2 = Unseen Regular, 3 = Seen (pushed to end)
    val displayOtherStories = remember(storiesByUser, activeLives, seenStoryUserIds.size, currentUserId) {
        val groupedOther = storiesByUser.entries
            .asSequence()
            .filter { it.key != currentUserId && it.value.isNotEmpty() }
            .map { (userId, userStories) ->
                val firstStory = userStories.first()
                val liveInfo = activeLives[userId]
                if (liveInfo != null) {
                    firstStory.copy(isLive = true, id = liveInfo.storyId)
                } else {
                    firstStory
                }
            }
            .toList()

        val groupedIds = groupedOther.mapTo(HashSet()) { it.userId }
        val liveStories = activeLives.entries.mapNotNull { (uid, liveInfo) ->
            if (uid == currentUserId || uid in groupedIds) return@mapNotNull null
            StoryUiModel(
                id = liveInfo.storyId,
                userId = uid,
                username = liveInfo.username.ifBlank { "Live User" },
                avatarUrl = liveInfo.avatarUrl,
                imageUrl = "",
                textOverlay = "",
                timeAgo = "Live",
                isLive = true
            )
        }

        (liveStories + groupedOther).sortedWith(
            compareBy<StoryUiModel> { item ->
                val itemStories = storiesByUser[item.userId].orEmpty()
                val isSeen = (currentUserId.isNotEmpty() && itemStories.isNotEmpty() && itemStories.all { it.viewers.contains(currentUserId) }) ||
                    seenStoryUserIds.contains(item.userId)
                when {
                    item.isLive -> 0
                    !isSeen && item.isCloseFriendsOnly -> 1
                    !isSeen -> 2
                    else -> 3
                }
            }.thenByDescending { it.timestamp }
        )
    }

    // Full ordered list of non-live stories across all friends for 3D Cube Pager navigation
    val allOrderedFriendStories = remember(displayOtherStories, storiesByUser) {
        displayOtherStories.filter { !it.isLive }.flatMap { rep ->
            storiesByUser[rep.userId].orEmpty()
        }
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // My Story Item (Fixed/First with clean blue '+' badge & live upload ring)
        item(key = "my_story_bubble", contentType = "my_story") {
            val isMyStoriesAllSeen = hasStory && myStories.all { it.viewers.contains(currentUserId) || seenStoryUserIds.contains(currentUserId) }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(76.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer {
                            scaleX = myAvatarScale.value
                            scaleY = myAvatarScale.value
                        }
                        .bounceClick()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .clickable {
                                if (isPostingStory) {
                                    // Background upload in progress
                                } else if (myIsLive) {
                                    myLatestStory?.let { onLiveClick(it) } ?: onLiveClick(StoryUiModel(id="live_$currentUserId", userId=currentUserId, username="Me", avatarUrl=userAvatar, imageUrl="", textOverlay="", timeAgo="Live", isLive=true))
                                } else if (hasStory) {
                                    if (!seenStoryUserIds.contains(currentUserId)) seenStoryUserIds.add(currentUserId)
                                    onStoryClick(myStories + allOrderedFriendStories, 0)
                                } else {
                                    onCreateStoryClick()
                                }
                            }
                            .background(MaterialTheme.colorScheme.background, CircleShape)
                            .border(
                                width = if (isPostingStory) 2.8.dp else 2.2.dp,
                                brush = when {
                                    isPostingStory -> StoryUploadSweepBrush
                                    myIsLive -> StoryLiveGradient
                                    hasStory && myLatestStory?.isCloseFriendsOnly == true -> StoryCloseFriendsGradient
                                    isMyStoriesAllSeen -> StorySeenMutedBrush
                                    myLatestStory?.isExclusiveContent == true -> ExclusiveGoldBrush
                                    hasStory -> StoryInstagramGradient
                                    else -> StoryEmptyRingBrush
                                },
                                shape = CircleShape
                            )
                            .padding(4.2.dp)
                    ) {
                        val currentUserRealtime by com.example.model.RepositoryManager.currentUserState.collectAsStateWithLifecycle()
                        val effectiveAvatar = if (currentUserRealtime?.avatarUrl?.isNotEmpty() == true) currentUserRealtime?.avatarUrl ?: userAvatar else userAvatar
                        val previewUrl = if (hasStory && myLatestStory?.imageUrl?.isNotEmpty() == true) myLatestStory.imageUrl else effectiveAvatar
                        if (previewUrl.isNotBlank()) {
                            val myStoryReq = remember(previewUrl) {
                                ExoPlayerUtils.buildFastPosterRequest(context, previewUrl, 200, 200)
                            }
                            AsyncImage(
                                model = myStoryReq,
                                contentDescription = "My Story",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Color(0xFF232634)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "My Story",
                                    tint = Color(0xFFB0B6C8),
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }

                    if (isPostingStory) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0095F6))
                                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        }
                    } else if (!myIsLive) {
                        // Clean blue '+' badge always accessible on Your Story
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(23.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0095F6))
                                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                                .clickable { onCreateStoryClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Add Story",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = 8.dp)
                                .background(Color(0xFFE91E63), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "LIVE",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isPostingStory) "Posting..." else "Your story",
                        fontSize = 11.5.sp,
                        fontWeight = if (isPostingStory) FontWeight.Bold else FontWeight.Normal,
                        color = if (isPostingStory) Color(0xFF0095F6) else MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isMyAccountVerified && !isPostingStory) {
                        Spacer(modifier = Modifier.width(2.dp))
                        VerificationBadge(
                            isVerified = true,
                            badgeUrl = currentUserRealtime?.verificationBadgeUrl,
                            modifier = Modifier.size(12.dp),
                            showDialogOnClick = false
                        )
                    }
                }
            }
        }

        // Other Friends' Stories (Unseen Close Friends -> Unseen -> Seen pushed to end)
        items(
            items = displayOtherStories,
            key = { it.id + "_" + it.userId },
            contentType = { "friend_story" }
        ) { story ->
            val userStoriesForItem = storiesByUser[story.userId].orEmpty()
            val isSeenByCurrentUser = (currentUserId.isNotEmpty() && userStoriesForItem.isNotEmpty() && userStoriesForItem.all { it.viewers.contains(currentUserId) }) || seenStoryUserIds.contains(story.userId)
            val hasExclusiveStory = userStoriesForItem.any { it.isExclusiveContent } || story.isExclusiveContent
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(76.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .bounceClick()
                        .clip(CircleShape)
                        .clickable {
                            if (story.isLive) {
                                onLiveClick(story)
                            } else {
                                val uid = story.userId ?: ""
                                if (uid.isNotEmpty() && !seenStoryUserIds.contains(uid)) seenStoryUserIds.add(uid)
                                val combinedList = allOrderedFriendStories.ifEmpty { userStoriesForItem }
                                val startIdx = combinedList.indexOfFirst { it.userId == story.userId }.coerceAtLeast(0)
                                onStoryClick(combinedList, startIdx)
                            }
                        }
                        .background(MaterialTheme.colorScheme.background, CircleShape)
                        .border(
                            width = if (isSeenByCurrentUser && !story.isLive && !hasExclusiveStory) 1.6.dp else 2.6.dp,
                            brush = when {
                                story.isLive -> StoryLiveGradient
                                hasExclusiveStory -> ExclusiveGoldBrush
                                isSeenByCurrentUser -> StorySeenMutedBrush
                                story.isCloseFriendsOnly -> StoryCloseFriendsGradient
                                else -> StoryInstagramGradient
                            },
                            shape = CircleShape
                        )
                        .padding(4.2.dp)
                ) {
                    val displayPreviewUrl = if (hasExclusiveStory) story.avatarUrl else if (story.imageUrl.isNotBlank()) story.imageUrl else story.avatarUrl
                    if (displayPreviewUrl.isNotBlank()) {
                        val storyImgReq = remember(displayPreviewUrl) {
                            ExoPlayerUtils.buildFastPosterRequest(context, displayPreviewUrl, 200, 200)
                        }
                        AsyncImage(
                            model = storyImgReq,
                            contentDescription = "User Story",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color(0xFF232634)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = story.username.take(1).uppercase().ifEmpty { "U" },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                    }

                    if (hasExclusiveStory && !story.isLive) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFB300))
                                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Exclusive Story",
                                tint = Color(0xFF1A1200),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    } else if (story.imageUrl.isNotBlank() && story.avatarUrl.isNotBlank()) {
                        val badgeReq = remember(story.avatarUrl) {
                            ExoPlayerUtils.buildFastPosterRequest(context, story.avatarUrl, 72, 72)
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(22.dp)
                                .clip(CircleShape)
                                .border(2.dp, Color.White, CircleShape)
                        ) {
                            AsyncImage(
                                model = badgeReq,
                                contentDescription = "Avatar Badge",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    if (story.isLive) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = 8.dp)
                                .background(Color(0xFFE91E63), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "LIVE",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = story.username,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (story.isVerified) {
                        Spacer(modifier = Modifier.width(2.dp))
                        VerificationBadge(
                            isVerified = true,
                            badgeUrl = story.verificationBadgeUrl.ifBlank { null },
                            modifier = Modifier.size(12.dp),
                            showDialogOnClick = false
                        )
                    }
                }
            }
        }
    }
}
