package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.model.*
import com.example.repository.AdvertisingRepository
import kotlinx.coroutines.launch

@Composable
fun SponsoredPostCard(
    ad: Advertisement,
    currentUserId: String = "",
    onNavigateToProfile: (String) -> Unit = {},
    onAdHidden: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showOptionsDialog by remember { mutableStateOf(false) }
    var showWhyDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("Inappropriate Content") }

    // Track real impression when card enters view
    LaunchedEffect(ad.adId) {
        AdvertisingRepository.recordImpression(
            adId = ad.adId,
            campaignId = ad.campaignId,
            advertiserUid = ad.advertiserUid,
            placement = AdPlacement.HOME_FEED,
            viewerUid = currentUserId
        )
    }

    val openDestination: (String) -> Unit = { clickType ->
        scope.launch {
            AdvertisingRepository.recordClick(
                adId = ad.adId,
                campaignId = ad.campaignId,
                advertiserUid = ad.advertiserUid,
                clickType = clickType,
                destinationUrl = ad.destinationUrl
            )
        }
        if (ad.destinationUrl.isNotEmpty()) {
            try {
                val url = if (!ad.destinationUrl.startsWith("http://") && !ad.destinationUrl.startsWith("https://")) {
                    "https://${ad.destinationUrl}"
                } else ad.destinationUrl
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open link: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            onNavigateToProfile(ad.advertiserUid)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("sponsored_post_${ad.adId}"),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header: Advertiser Avatar, Name, Sponsored Badge, and Menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onNavigateToProfile(ad.advertiserUid) }
                ) {
                    if (ad.advertiserAvatarUrl.isNotEmpty()) {
                        AsyncImage(
                            model = ad.advertiserAvatarUrl,
                            contentDescription = ad.advertiserName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.Business,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.Center)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = ad.advertiserName.ifEmpty { ad.advertiserUsername.ifEmpty { "Sponsored Brand" } },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = "Verified Business",
                            tint = Color(0xFF1E88E5),
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Sponsored",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (ad.advertiserCategory.isNotEmpty()) {
                            Text(
                                text = " • ${ad.advertiserCategory}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                IconButton(onClick = { showOptionsDialog = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Ad Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Primary Text / Caption if present
            if (ad.primaryText.isNotEmpty()) {
                Text(
                    text = ad.primaryText,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            // Media Rendering (Photo, Video, or Carousel)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                when (ad.format) {
                    AdFormat.VIDEO -> {
                        AdVideoPlayer(videoUrl = ad.mediaUrls.firstOrNull() ?: "")
                    }
                    AdFormat.CAROUSEL -> {
                        val mediaList = if (ad.mediaUrls.isNotEmpty()) ad.mediaUrls else listOf("")
                        val pagerState = rememberPagerState(pageCount = { mediaList.size })
                        Box(modifier = Modifier.fillMaxSize()) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                AsyncImage(
                                    model = mediaList[page],
                                    contentDescription = "Carousel item ${page + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            // Dots indicator
                            if (mediaList.size > 1) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    mediaList.indices.forEach { idx ->
                                        Box(
                                            modifier = Modifier
                                                .size(if (pagerState.currentPage == idx) 8.dp else 6.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (pagerState.currentPage == idx) Color.White else Color.White.copy(alpha = 0.5f)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        // PHOTO / STORY / REEL format photo
                        val mediaUrl = ad.mediaUrls.firstOrNull() ?: ad.thumbnailUrl
                        if (mediaUrl.isNotEmpty()) {
                            AsyncImage(
                                model = mediaUrl,
                                contentDescription = ad.headline,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = ad.headline.ifEmpty { "Sponsored Announcement" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // Call to Action (CTA) Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable { openDestination("CTA_CLICK") }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (ad.headline.isNotEmpty()) {
                        Text(
                            text = ad.headline,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (ad.destinationUrl.isNotEmpty()) {
                        Text(
                            text = ad.destinationUrl.replace("https://", "").replace("http://", ""),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Button(
                    onClick = { openDestination("CTA_CLICK") },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = ad.callToAction.ifEmpty { "Learn More" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            if (ad.caption.isNotEmpty()) {
                Text(
                    text = ad.caption,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }

    // Ad Options Dialog (Hide, Report, Why seeing this)
    if (showOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showOptionsDialog = false },
            title = { Text("About this Advertisement", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ListItem(
                        headlineContent = { Text("Why am I seeing this ad?") },
                        leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                        modifier = Modifier.clickable {
                            showOptionsDialog = false
                            showWhyDialog = true
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Hide ad") },
                        supportingContent = { Text("Never see this advertisement again") },
                        leadingContent = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null) },
                        modifier = Modifier.clickable {
                            showOptionsDialog = false
                            AdvertisingRepository.hideAdForSession(ad.adId)
                            Toast.makeText(context, "Ad hidden", Toast.LENGTH_SHORT).show()
                            onAdHidden()
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Report ad") },
                        supportingContent = { Text("Tell us if you find this misleading or harmful") },
                        leadingContent = { Icon(Icons.Outlined.Report, contentDescription = null, tint = Color.Red) },
                        modifier = Modifier.clickable {
                            showOptionsDialog = false
                            showReportDialog = true
                        }
                    )
                    ListItem(
                        headlineContent = { Text("About advertiser") },
                        leadingContent = { Icon(Icons.Outlined.Business, contentDescription = null) },
                        modifier = Modifier.clickable {
                            showOptionsDialog = false
                            onNavigateToProfile(ad.advertiserUid)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showOptionsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // "Why am I seeing this" Dialog
    if (showWhyDialog) {
        AlertDialog(
            onDismissRequest = { showWhyDialog = false },
            title = { Text("Why You're Seeing This Ad", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "You were matched with this advertisement based on the advertiser's campaign targeting parameters:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("• Target Country: India", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("• Target Age Range: 18 - 65", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    if (ad.advertiserCategory.isNotEmpty()) {
                        Text("• Relevance: Interest in ${ad.advertiserCategory}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    Text("• Placement: Home Feed placement option", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        "Social India does not sell your private personal data. Advertisers only select general privacy-safe criteria.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showWhyDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }

    // Report Dialog
    if (showReportDialog) {
        val reasons = listOf(
            "Inappropriate or offensive content",
            "Misleading or scam",
            "Intellectual property violation",
            "Prohibited goods or services",
            "Spam or repetitive"
        )
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report Advertisement", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Select a reason for reporting:", fontSize = 13.sp)
                    reasons.forEach { r ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { reportReason = r }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = reportReason == r,
                                onClick = { reportReason = r }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(r, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            AdvertisingRepository.reportAd(
                                AdReport(
                                    adId = ad.adId,
                                    reporterUid = currentUserId,
                                    reason = reportReason
                                )
                            )
                            AdvertisingRepository.hideAdForSession(ad.adId)
                            showReportDialog = false
                            Toast.makeText(context, "Report submitted. Thank you for keeping Social India safe.", Toast.LENGTH_LONG).show()
                            onAdHidden()
                        }
                    }
                ) {
                    Text("Submit Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun AdVideoPlayer(videoUrl: String) {
    val context = LocalContext.current
    val exoPlayer = remember {
        com.example.utils.ExoPlayerUtils.createExoPlayer(context).apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }

    LaunchedEffect(videoUrl) {
        if (videoUrl.isNotEmpty()) {
            val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun AdPreviewCard(
    format: String,
    headline: String,
    primaryText: String,
    callToAction: String,
    destinationUrl: String,
    businessName: String,
    category: String,
    avatarUrl: String,
    mediaUrls: List<String>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Preview Banner Label
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ADVERTISEMENT PREVIEW • ${format.uppercase()}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUrl.isNotEmpty()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.Business, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = businessName.ifEmpty { "Your Business" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = null,
                            tint = Color(0xFF1E88E5),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = "Sponsored • ${category.ifEmpty { "Brand" }}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (primaryText.isNotEmpty()) {
                Text(
                    text = primaryText,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            // Media
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                val media = mediaUrls.firstOrNull() ?: ""
                if (media.isNotEmpty()) {
                    AsyncImage(
                        model = media,
                        contentDescription = "Ad Preview Media",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Image, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Media will be displayed here", color = Color.LightGray, fontSize = 12.sp)
                    }
                }
            }

            // CTA Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = headline.ifEmpty { "Headline here" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = destinationUrl.ifEmpty { "www.yourwebsite.com" },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = {},
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(callToAction.ifEmpty { "Learn More" }, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
