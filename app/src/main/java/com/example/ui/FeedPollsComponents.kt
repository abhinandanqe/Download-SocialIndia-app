package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.repository.FeedPollPost
import com.example.repository.FeedPollRepository
import com.example.utils.ExoPlayerUtils
import com.example.utils.SoundManager
import com.example.utils.bounceClick
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun HomeFeedPollsStrip(
    currentUserId: String,
    currentUsername: String,
    currentUserAvatar: String,
    onNavigateToProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val pollsFlow = remember { FeedPollRepository.listenActivePolls(12L) }
    val polls by pollsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var showCreatorSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFFF2A5F), Color(0xFFFF8A00))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Poll,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Live Polls & Quizzes",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Vote & test your knowledge with India",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FilledTonalButton(
                onClick = {
                    SoundManager.playClickSound()
                    showCreatorSheet = true
                },
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFFFF2A5F).copy(alpha = 0.14f),
                    contentColor = Color(0xFFFF2A5F)
                ),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("create_poll_quiz_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Poll / Quiz", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        if (polls.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable { showCreatorSheet = true },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Quiz,
                        contentDescription = null,
                        tint = Color(0xFFFF2A5F),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Start the first Community Poll or Quiz",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Ask followers a question with live percentage bars or trivia reveal.",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(polls, key = { it.id }) { poll ->
                    InteractivePollQuizCard(
                        poll = poll,
                        currentUserId = currentUserId,
                        onCreatorClick = {
                            if (poll.creatorId.isNotBlank()) onNavigateToProfile(poll.creatorId)
                        }
                    )
                }
            }
        }
    }

    if (showCreatorSheet) {
        CreateFeedPollQuizSheet(
            creatorId = currentUserId,
            creatorUsername = currentUsername,
            creatorAvatarUrl = currentUserAvatar,
            onDismiss = { showCreatorSheet = false }
        )
    }
}

@Composable
fun InteractivePollQuizCard(
    poll: FeedPollPost,
    currentUserId: String,
    onCreatorClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var optimisticVote by remember(poll.id) { mutableStateOf<Int?>(null) }
    var isVoting by remember(poll.id) { mutableStateOf(false) }

    val serverVote = poll.voters[currentUserId]
    val selectedOptionId = optimisticVote ?: serverVote
    val hasVoted = selectedOptionId != null
    val showResults = hasVoted || poll.isExpired

    val effectiveTotalVotes = remember(poll, optimisticVote, serverVote) {
        when {
            optimisticVote != null && serverVote == null -> poll.totalVotes + 1
            else -> poll.totalVotes
        }
    }

    val accentBrush = if (poll.isQuiz) {
        Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFF00E5FF)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFFF2A5F), Color(0xFFFF8A00)))
    }

    Card(
        modifier = Modifier
            .width(305.dp)
            .testTag("feed_poll_card_${poll.id}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (poll.isQuiz) Color(0xFF7C4DFF).copy(alpha = 0.35f) else Color(0xFFFF2A5F).copy(alpha = 0.28f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            // Creator Row + Poll/Quiz Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onCreatorClick() }
                ) {
                    val avatarReq = remember(poll.creatorAvatarUrl) {
                        ExoPlayerUtils.buildFastPosterRequest(context, poll.creatorAvatarUrl, 96, 96)
                    }
                    if (poll.creatorAvatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = avatarReq,
                            contentDescription = poll.creatorUsername,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(accentBrush),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = poll.creatorUsername.take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "@${poll.creatorUsername}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = formatRemainingTime(poll.expiresAt),
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (poll.isQuiz) Color(0xFF7C4DFF).copy(alpha = 0.16f) else Color(0xFFFF2A5F).copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = if (poll.isQuiz) "QUIZ" else "POLL",
                            color = if (poll.isQuiz) Color(0xFF9C7CFF) else Color(0xFFFF2A5F),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (poll.creatorId == currentUserId && currentUserId.isNotBlank()) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    FeedPollRepository.deletePoll(poll.id)
                                    Toast.makeText(context, "Poll deleted", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete poll",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = poll.question,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Options with Animated Percentage Progress Bars
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                poll.options.forEach { option ->
                    val optionVotes = when {
                        optimisticVote != null && serverVote == null && option.id == optimisticVote -> option.votesCount + 1
                        optimisticVote != null && serverVote != null && serverVote != optimisticVote -> {
                            when (option.id) {
                                optimisticVote -> option.votesCount + 1
                                serverVote -> (option.votesCount - 1).coerceAtLeast(0)
                                else -> option.votesCount
                            }
                        }
                        else -> option.votesCount
                    }
                    val fraction = if (effectiveTotalVotes > 0) {
                        (optionVotes.toFloat() / effectiveTotalVotes.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    val reducedMotion = com.example.ui.motion.rememberReducedMotionEnabled()
                    val animatedFraction by animateFloatAsState(
                        targetValue = if (showResults) fraction else 0f,
                        animationSpec = com.example.ui.motion.SocialMotion.floatSpec(
                            durationMillis = com.example.ui.motion.SocialMotion.DurationMedium,
                            reducedMotion = reducedMotion
                        ),
                        label = "pollBar_${poll.id}_${option.id}"
                    )

                    val isUserChoice = selectedOptionId == option.id
                    val isCorrectOption = poll.isQuiz && option.id == poll.correctOptionId
                    val isWrongChoice = poll.isQuiz && isUserChoice && !isCorrectOption

                    val barColor = when {
                        !showResults -> Color.Transparent
                        isCorrectOption -> Color(0xFF00C853).copy(alpha = 0.25f)
                        isWrongChoice -> Color(0xFFFF1744).copy(alpha = 0.24f)
                        isUserChoice -> Color(0xFFFF2A5F).copy(alpha = 0.24f)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    }

                    val borderColor = when {
                        showResults && isCorrectOption -> Color(0xFF00C853)
                        showResults && isWrongChoice -> Color(0xFFFF1744)
                        isUserChoice -> Color(0xFFFF2A5F)
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 42.dp)
                            .bounceClick(enableHaptics = false)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .clickable(enabled = !poll.isExpired && !(poll.isQuiz && hasVoted) && !isVoting) {
                                if (currentUserId.isBlank()) {
                                    Toast.makeText(context, "Sign in to vote", Toast.LENGTH_SHORT).show()
                                    return@clickable
                                }
                                SoundManager.playClickSound()
                                optimisticVote = option.id
                                isVoting = true
                                scope.launch {
                                    val res = FeedPollRepository.voteOnPoll(poll.id, currentUserId, option.id)
                                    if (res.isFailure) {
                                        optimisticVote = null
                                        Toast.makeText(context, res.exceptionOrNull()?.message ?: "Vote failed", Toast.LENGTH_SHORT).show()
                                    }
                                    isVoting = false
                                }
                            }
                    ) {
                        if (showResults && animatedFraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .graphicsLayer {
                                        scaleX = animatedFraction.coerceIn(0f, 1f)
                                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                                    }
                                    .background(barColor)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (showResults && isCorrectOption) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Correct",
                                        tint = Color(0xFF00C853),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                } else if (showResults && isWrongChoice) {
                                    Icon(
                                        Icons.Default.Cancel,
                                        contentDescription = "Wrong",
                                        tint = Color(0xFFFF1744),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                } else if (isUserChoice) {
                                    Icon(
                                        Icons.Default.RadioButtonChecked,
                                        contentDescription = "Selected",
                                        tint = Color(0xFFFF2A5F),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = option.text,
                                    fontSize = 13.sp,
                                    fontWeight = if (isUserChoice || (showResults && isCorrectOption)) FontWeight.Bold else FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (showResults) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${(fraction * 100f).roundToInt()}%",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = when {
                                        isCorrectOption -> Color(0xFF00C853)
                                        isWrongChoice -> Color(0xFFFF1744)
                                        isUserChoice -> Color(0xFFFF2A5F)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Quiz Explanation Banner after answering
            AnimatedVisibility(visible = poll.isQuiz && showResults && poll.quizExplanation.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF7C4DFF).copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = poll.quizExplanation,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$effectiveTotalVotes ${if (effectiveTotalVotes == 1) "vote" else "votes"}",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = when {
                        poll.isExpired -> "Final results"
                        poll.isQuiz && hasVoted -> if (selectedOptionId == poll.correctOptionId) "Correct!" else "Better luck next time"
                        hasVoted -> "Tap another option to change"
                        poll.isQuiz -> "Tap to guess answer"
                        else -> "Tap to vote"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (poll.isQuiz && hasVoted && selectedOptionId == poll.correctOptionId) Color(0xFF00C853) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFeedPollQuizSheet(
    creatorId: String,
    creatorUsername: String,
    creatorAvatarUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isQuiz by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    var correctOptionIndex by remember { mutableIntStateOf(0) }
    var explanation by remember { mutableStateOf("") }
    var durationHours by remember { mutableIntStateOf(24) }
    var isSubmitting by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isQuiz) "Create Interactive Quiz" else "Create Community Poll",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Mode Switcher: Poll vs Quiz
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = !isQuiz,
                    onClick = { isQuiz = false },
                    label = { Text("Voting Poll", fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.Poll, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFF2A5F),
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White
                    )
                )
                FilterChip(
                    selected = isQuiz,
                    onClick = { isQuiz = true },
                    label = { Text("Trivia Quiz", fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.Quiz, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF7C4DFF),
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White
                    )
                )
            }

            OutlinedTextField(
                value = question,
                onValueChange = { if (it.length <= 160) question = it },
                label = { Text(if (isQuiz) "Quiz Question" else "Ask your followers...") },
                placeholder = { Text(if (isQuiz) "e.g. Which city is known as Pink City?" else "e.g. Which Reel topic should I post next?") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                maxLines = 3
            )

            Text(
                text = if (isQuiz) "Options (Tap circle to mark the correct answer)" else "Poll Options (${options.size}/4)",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            options.forEachIndexed { idx, optText ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isQuiz) {
                        RadioButton(
                            selected = correctOptionIndex == idx,
                            onClick = { correctOptionIndex = idx },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00C853))
                        )
                    }
                    OutlinedTextField(
                        value = optText,
                        onValueChange = { if (it.length <= 60) options[idx] = it },
                        placeholder = { Text("Option ${idx + 1}") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (options.size > 2) {
                        IconButton(onClick = {
                            options.removeAt(idx)
                            if (correctOptionIndex >= options.size) correctOptionIndex = 0
                        }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            if (options.size < 4) {
                TextButton(
                    onClick = { options.add("") },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Option", fontWeight = FontWeight.Bold)
                }
            }

            if (isQuiz) {
                OutlinedTextField(
                    value = explanation,
                    onValueChange = { if (it.length <= 160) explanation = it },
                    label = { Text("Fun Fact / Explanation (Optional)") },
                    placeholder = { Text("Shown right after users submit their guess") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    maxLines = 2
                )
            }

            // Duration selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Duration:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                listOf(24 to "24h", 72 to "3 Days", 168 to "7 Days").forEach { (hrs, label) ->
                    FilterChip(
                        selected = durationHours == hrs,
                        onClick = { durationHours = hrs },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            Button(
                onClick = {
                    val cleanOpts = options.map { it.trim() }.filter { it.isNotEmpty() }
                    if (question.isBlank() || cleanOpts.size < 2) {
                        Toast.makeText(context, "Add a question and at least 2 options", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isSubmitting = true
                    scope.launch {
                        val res = FeedPollRepository.createFeedPoll(
                            creatorId = creatorId,
                            creatorUsername = creatorUsername,
                            creatorAvatarUrl = creatorAvatarUrl,
                            question = question,
                            optionTexts = cleanOpts,
                            isQuiz = isQuiz,
                            correctOptionId = correctOptionIndex.coerceIn(0, cleanOpts.lastIndex),
                            quizExplanation = explanation,
                            durationHours = durationHours
                        )
                        isSubmitting = false
                        if (res.isSuccess) {
                            Toast.makeText(context, if (isQuiz) "Quiz posted live!" else "Poll posted live!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } else {
                            Toast.makeText(context, res.exceptionOrNull()?.message ?: "Failed to publish", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("publish_poll_quiz_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isQuiz) Color(0xFF7C4DFF) else Color(0xFFFF2A5F)
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(
                        text = if (isQuiz) "Publish Quiz to Feed" else "Publish Poll to Feed",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

private fun formatRemainingTime(expiresAt: Long): String {
    if (expiresAt <= 0L) return "Live"
    val diff = expiresAt - System.currentTimeMillis()
    if (diff <= 0L) return "Ended"
    val hours = diff / (1000L * 60L * 60L)
    val days = hours / 24L
    return when {
        days >= 1L -> "${days}d left"
        hours >= 1L -> "${hours}h left"
        else -> "${((diff / (1000L * 60L)).coerceAtLeast(1L))}m left"
    }
}
