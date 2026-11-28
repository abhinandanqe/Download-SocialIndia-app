package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.CreatorAnalyticsSummary
import com.example.model.FirebasePost
import com.example.model.RepositoryManager
import com.example.model.User
import com.example.repository.MonetizationRepository
import com.example.utils.ExoPlayerUtils
import com.example.utils.MediaResolver
import com.example.utils.NumberFormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

private data class DailyAnalyticsBucket(
    val label: String,
    val views: Long,
    val likes: Long,
    val postCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalDashboardScreen(
    user: User,
    onBack: () -> Unit,
    onNavigateToMonetization: () -> Unit = {},
    onNavigateToWallet: () -> Unit = {},
    onNavigateToDrafts: () -> Unit = {},
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToReferrals: () -> Unit = {},
    onOpenPost: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var posts by remember { mutableStateOf<List<FirebasePost>>(emptyList()) }
    var analytics by remember { mutableStateOf(CreatorAnalyticsSummary()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFilter by remember { mutableIntStateOf(30) }
    val filters = listOf(7 to "7 Days", 28 to "28 Days", 30 to "30 Days", 90 to "90 Days")

    var selectedBucketIndex by remember { mutableIntStateOf(-1) }
    var showAiIdeaDialog by remember { mutableStateOf(false) }
    var aiGeneratedIdea by remember { mutableStateOf<com.example.ai.AICreatorIdea?>(null) }
    var isGeneratingIdea by remember { mutableStateOf(false) }

    LaunchedEffect(user.id, selectedFilter) {
        isLoading = true
        try {
            val (loadedAnalytics, loadedPosts) = withContext(Dispatchers.IO) {
                val a = MonetizationRepository.getCreatorAnalytics(user.id, selectedFilter)
                val snapshot = RepositoryManager.firestore.collection("posts")
                    .whereEqualTo("userId", user.id)
                    .get()
                    .await()
                val pList = snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(FirebasePost::class.java)?.copy(id = doc.id)
                    } catch (_: Exception) { null }
                }.filter { !it.isDeleted }
                a to pList
            }
            analytics = loadedAnalytics
            posts = loadedPosts
            selectedBucketIndex = -1
        } catch (e: Exception) {
            android.util.Log.w("CreatorDashboard", "Failed loading analytics: ${e.message}")
        } finally {
            isLoading = false
        }
    }

    // Build 7 real period buckets from actual Firestore posts
    val trendBuckets = remember(posts, selectedFilter) {
        val now = System.currentTimeMillis()
        val windowMs = selectedFilter.toLong() * 24L * 60L * 60L * 1000L
        val startMs = now - windowMs
        val bucketCount = 7
        val stepMs = (windowMs / bucketCount).coerceAtLeast(1L)
        val fmt = SimpleDateFormat(if (selectedFilter <= 7) "EEE" else "dd MMM", Locale.getDefault())

        (0 until bucketCount).map { idx ->
            val bStart = startMs + idx * stepMs
            val bEnd = if (idx == bucketCount - 1) now + 1000L else startMs + (idx + 1) * stepMs
            val inBucket = posts.filter { post ->
                val ts = post.timestamp?.time ?: 0L
                ts in bStart until bEnd
            }
            DailyAnalyticsBucket(
                label = fmt.format(Date(bEnd - stepMs / 2)),
                views = inBucket.sumOf { it.viewsCount.toLong() },
                likes = inBucket.sumOf { it.likesCount.toLong() },
                postCount = inBucket.size
            )
        }
    }

    val reelsCount = remember(posts) { posts.count { !MediaResolver.isImage(it.postUrl) } }
    val photosCount = remember(posts) { posts.count { MediaResolver.isImage(it.postUrl) } }
    val totalViewsComputed = remember(posts, analytics) {
        maxOf(analytics.totalViews, posts.sumOf { it.viewsCount.toLong() })
    }
    val totalLikesComputed = remember(posts, analytics) {
        maxOf(analytics.totalLikes, posts.sumOf { it.likesCount.toLong() })
    }
    val totalCommentsComputed = remember(posts, analytics) {
        maxOf(analytics.totalComments, posts.sumOf { it.commentsCount.toLong() })
    }
    val engagementRateComputed = remember(totalViewsComputed, totalLikesComputed, totalCommentsComputed, analytics) {
        if (totalViewsComputed > 0L) {
            ((totalLikesComputed + totalCommentsComputed).toDouble() / totalViewsComputed.toDouble() * 100.0).coerceAtMost(100.0)
        } else analytics.engagementRate
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Creator Analytics", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                        Text("@${user.username} • Studio Insights", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("analytics_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToMonetization) {
                        Icon(Icons.Default.MonetizationOn, contentDescription = "Monetization", tint = Color(0xFF00C853))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFFFF2A5F))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                // Time Range Filter Chips
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filters) { (days, label) ->
                        FilterChip(
                            selected = selectedFilter == days,
                            onClick = { selectedFilter = days },
                            label = { Text(label, fontWeight = if (selectedFilter == days) FontWeight.Bold else FontWeight.Medium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFF2A5F),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                // Studio Hero Card with Gradient Atmosphere
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF141828), Color(0xFF231838), Color(0xFF3E1330))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Total Accounts Reached", fontSize = 12.sp, color = Color.White.copy(alpha = 0.72f))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = NumberFormatUtils.formatCount(maxOf(analytics.reach, totalViewsComputed).toInt()),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF00E676).copy(alpha = 0.18f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color(0xFF69F0AE), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${"%.1f".format(engagementRateComputed)}% ER",
                                        color = Color(0xFF69F0AE),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.5.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            HeroMiniStat("Followers", NumberFormatUtils.formatCount(user.followerCount))
                            HeroMiniStat("Engagements", NumberFormatUtils.formatCount((totalLikesComputed + totalCommentsComputed).toInt()))
                            HeroMiniStat("Watch Time", "${(analytics.watchTimeSeconds / 60).coerceAtLeast(totalViewsComputed / 12)}m")
                            HeroMiniStat("Earnings", "₹${"%.0f".format(user.earnings)}")
                        }
                    }
                }

                // Real Data-Driven Interactive Performance Chart
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Views & Activity Trend", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                Text(
                                    text = if (selectedBucketIndex in trendBuckets.indices) {
                                        val b = trendBuckets[selectedBucketIndex]
                                        "${b.label}: ${NumberFormatUtils.formatCount(b.views.toInt())} views • ${b.likes} likes • ${b.postCount} post(s)"
                                    } else {
                                        "Tap any bar below to inspect period metrics"
                                    },
                                    fontSize = 11.5.sp,
                                    color = if (selectedBucketIndex >= 0) Color(0xFFFF2A5F) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFF2A5F).copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = NumberFormatUtils.formatCount(totalViewsComputed.toInt()),
                                    color = Color(0xFFFF2A5F),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val maxBucketViews = (trendBuckets.maxOfOrNull { it.views } ?: 1L).coerceAtLeast(1L)

                        // Interactive Real Bar + Smooth Area Trend Chart
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                        ) {
                            // Subtle curve line behind bars
                            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
                                val w = size.width
                                val h = size.height
                                val gridColor = Color.Gray.copy(alpha = 0.14f)
                                drawLine(gridColor, Offset(0f, h * 0.25f), Offset(w, h * 0.25f), 1f)
                                drawLine(gridColor, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), 1f)
                                drawLine(gridColor, Offset(0f, h * 0.75f), Offset(w, h * 0.75f), 1f)

                                if (trendBuckets.size > 1 && trendBuckets.any { it.views > 0L }) {
                                    val path = Path()
                                    val stepX = w / (trendBuckets.size - 1)
                                    trendBuckets.forEachIndexed { i, bucket ->
                                        val x = i * stepX
                                        val norm = (bucket.views.toFloat() / maxBucketViews.toFloat()).coerceIn(0.04f, 1f)
                                        val y = h * (1f - norm * 0.85f)
                                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                    }
                                    drawPath(
                                        path = path,
                                        brush = Brush.horizontalGradient(listOf(Color(0xFFFF2A5F), Color(0xFFFF8A00), Color(0xFF00E5FF))),
                                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                trendBuckets.forEachIndexed { idx, bucket ->
                                    val ratio = (bucket.views.toFloat() / maxBucketViews.toFloat()).coerceIn(0.08f, 1f)
                                    val isSelected = selectedBucketIndex == idx
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Bottom,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable { selectedBucketIndex = if (isSelected) -1 else idx }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth(),
                                            contentAlignment = Alignment.BottomCenter
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(if (isSelected) 22.dp else 16.dp)
                                                    .fillMaxHeight(if (bucket.views > 0L) ratio * 0.82f else 0.06f)
                                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                                    .background(
                                                        if (isSelected) {
                                                            Brush.verticalGradient(listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF)))
                                                        } else {
                                                            Brush.verticalGradient(listOf(Color(0xFFFF2A5F), Color(0xFFFF8A00).copy(alpha = 0.55f)))
                                                        }
                                                    )
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = bucket.label,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                            color = if (isSelected) Color(0xFFFF2A5F) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Content Mix Split (Reels vs Photos)
                val totalContentCount = (reelsCount + photosCount).coerceAtLeast(1)
                val reelShare = reelsCount.toFloat() / totalContentCount.toFloat()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Format Breakdown", fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                            Text("${posts.size} total posts", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (reelsCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(reelShare.coerceAtLeast(0.05f))
                                        .background(Color(0xFFFF2A5F))
                                )
                            }
                            if (photosCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight((1f - reelShare).coerceAtLeast(0.05f))
                                        .background(Color(0xFF00B0FF))
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("● Reels: $reelsCount (${NumberFormatUtils.formatCount(analytics.reelViews.toInt())} views)", fontSize = 12.sp, color = Color(0xFFFF2A5F), fontWeight = FontWeight.SemiBold)
                            Text("● Photos: $photosCount (${NumberFormatUtils.formatCount(analytics.postViews.toInt())} views)", fontSize = 12.sp, color = Color(0xFF00B0FF), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                SettingsGroupTitle("Performance Breakdown")

                // 2x2 Metrics Grid
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricCard(
                            title = "Total Likes",
                            value = NumberFormatUtils.formatCount(totalLikesComputed.toInt()),
                            icon = Icons.Default.Favorite,
                            color = Color(0xFFE91E63),
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Comments",
                            value = NumberFormatUtils.formatCount(totalCommentsComputed.toInt()),
                            icon = Icons.Default.ChatBubble,
                            color = Color(0xFF00B0FF),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricCard(
                            title = "Profile Visits",
                            value = NumberFormatUtils.formatCount(analytics.profileVisits.toInt()),
                            icon = Icons.Default.Visibility,
                            color = Color(0xFF7C4DFF),
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Avg Views/Post",
                            value = NumberFormatUtils.formatCount(
                                if (posts.isNotEmpty()) (totalViewsComputed / posts.size).toInt() else 0
                            ),
                            icon = Icons.Default.Speed,
                            color = Color(0xFFFF9800),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                SettingsGroupTitle("Top Performing Content")

                if (posts.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Outlined.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No posts published yet", fontWeight = FontWeight.Bold)
                            Text("Publish your first Reel or Photo to track live growth analytics.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val topPosts = posts.sortedByDescending { it.viewsCount + it.likesCount * 3 }.take(8)
                        items(topPosts, key = { it.id }) { post ->
                            TopContentCard(
                                post = post,
                                onClick = { onOpenPost(post.id) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                SettingsGroupTitle("Smart Creator Insights")

                val smartInsight = remember(posts, reelsCount, photosCount, engagementRateComputed) {
                    when {
                        posts.isEmpty() -> "Post your first Reel or Poll today to start building your audience analytics profile."
                        engagementRateComputed >= 8.0 -> "Strong engagement (${"%.1f".format(engagementRateComputed)}%)! Your audience interacts heavily — keep posting consistently."
                        reelsCount > photosCount -> "Reels drive most of your catalogue ($reelsCount reels vs $photosCount photos). Try adding interactive Feed Polls to boost repeat visits."
                        else -> "You have $photosCount photo posts. Mixing in 15–30s Reels and scheduled posts can significantly expand reach."
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Data-Driven Growth Tip", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = smartInsight,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ActionCard(
                    icon = Icons.Default.Lightbulb,
                    title = "Generate Reel Concept Ideas",
                    subtitle = "Get viral hook, concept structure & audio suggestions",
                    onClick = {
                        coroutineScope.launch {
                            isGeneratingIdea = true
                            showAiIdeaDialog = true
                            aiGeneratedIdea = com.example.ai.SocialIndiaAIManager.creatorService.generateContentIdeas("Trending Indian Creators & Lifestyle")
                            isGeneratingIdea = false
                        }
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))
                SettingsGroupTitle("Creator Studio Tools")

                ActionCard(
                    icon = Icons.Default.CardGiftcard,
                    title = "Refer & Earn Social Coins",
                    subtitle = "Invite friends with your code & unlock milestone coin bonuses",
                    onClick = onNavigateToReferrals
                )

                ActionCard(
                    icon = Icons.Default.Drafts,
                    title = "Drafts Studio",
                    subtitle = "Create, edit, publish or schedule your saved drafts",
                    onClick = onNavigateToDrafts
                )

                ActionCard(
                    icon = Icons.Default.ScheduleSend,
                    title = "Scheduled Content Queue",
                    subtitle = "Auto-publish posts & reels with live countdowns",
                    onClick = onNavigateToCalendar
                )

                ActionCard(
                    icon = Icons.Default.MonetizationOn,
                    title = "Monetization Hub",
                    subtitle = "Manage ad revenue, live gifts, and eligibility",
                    onClick = onNavigateToMonetization
                )

                ActionCard(
                    icon = Icons.Default.AccountBalanceWallet,
                    title = "Creator Wallet & Payouts",
                    subtitle = "Withdraw earnings to UPI/Bank and track transactions",
                    onClick = onNavigateToWallet
                )

                Spacer(modifier = Modifier.height(36.dp))
            }
        }
    }

    if (showAiIdeaDialog) {
        val idea = aiGeneratedIdea
        AlertDialog(
            onDismissRequest = { showAiIdeaDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reel Concept Idea", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                if (isGeneratingIdea) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (idea != null) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(idea.conceptTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Hook: \"${idea.hookHindi}\"", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Suggested Audio: ${idea.suggestedAudioVibe}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Structure Steps:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        idea.structureSteps.forEach { step ->
                            Text("• $step", fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAiIdeaDialog = false }) {
                    Text("Done", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun HeroMiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(color.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TopContentCard(
    post: FirebasePost,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .width(128.dp)
            .height(178.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val posterModel = remember(post.postUrl, post.thumbnailUrl) {
                MediaResolver.resolveVideoPosterUrl(post.postUrl, post.thumbnailUrl)
                    .ifBlank { post.postUrl }
            }
            AsyncImage(
                model = ExoPlayerUtils.buildFastPosterRequest(context, posterModel, 280, 380),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)))
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = NumberFormatUtils.formatCount(post.viewsCount),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFFF2A5F), modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = NumberFormatUtils.formatCount(post.likesCount),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
