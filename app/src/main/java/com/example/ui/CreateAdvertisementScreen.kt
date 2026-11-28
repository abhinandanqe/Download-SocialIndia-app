package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.*
import com.example.repository.AdvertisingRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateAdvertisementScreen(
    onBack: () -> Unit,
    onAdCreated: (String) -> Unit,
    preselectedPostId: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var currentUser by remember { mutableStateOf<User?>(null) }
    var existingPosts by remember { mutableStateOf<List<FirebasePost>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }

    // Step navigation: 1. Format & Media, 2. Text & Destination, 3. Objective & Audience, 4. Budget & Placements, 5. Review & Submit
    var currentStep by remember { mutableIntStateOf(1) }

    // Ad Format: PHOTO, VIDEO, STORY, REEL, CAROUSEL
    var selectedFormat by remember { mutableStateOf(AdFormat.PHOTO) }

    // Media Source: NEW_UPLOAD or EXISTING_POST
    var mediaSource by remember { mutableStateOf("NEW_UPLOAD") }
    var selectedPost by remember { mutableStateOf<FirebasePost?>(null) }
    var uploadedMediaUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var isUploadingMedia by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableIntStateOf(0) }

    // Ad Creative Fields
    var headline by remember { mutableStateOf("") }
    var primaryText by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var selectedCTA by remember { mutableStateOf(CallToAction.LEARN_MORE) }
    var showCtaDropdown by remember { mutableStateOf(false) }
    var destinationUrl by remember { mutableStateOf("") }

    // Campaign Objective
    var selectedObjective by remember { mutableStateOf(CampaignObjective.WEBSITE_VISITS) }

    // Audience Targeting
    var targetCountry by remember { mutableStateOf("India") }
    var minAge by remember { mutableFloatStateOf(18f) }
    var maxAge by remember { mutableFloatStateOf(65f) }
    var selectedInterests by remember { mutableStateOf(listOf("Technology", "Shopping")) }
    val availableInterests = listOf("Technology", "Shopping", "Entertainment", "Fashion", "Food & Dining", "Travel", "Fitness", "Business", "Education", "Gaming")

    // Placements
    var selectedPlacements by remember { mutableStateOf(listOf(AdPlacement.HOME_FEED, AdPlacement.REELS, AdPlacement.STORIES)) }

    // Budget & Duration
    var budgetType by remember { mutableStateOf(BudgetType.DAILY) }
    var budgetAmountText by remember { mutableStateOf("500") }
    var durationDays by remember { mutableIntStateOf(7) }
    var pendingPaymentCampaignId by remember { mutableStateOf<String?>(null) }
    var pendingPaymentAdId by remember { mutableStateOf<String?>(null) }
    var pendingPaymentAmount by remember { mutableDoubleStateOf(500.0) }

    if (pendingPaymentCampaignId != null && pendingPaymentAdId != null) {
        com.example.ui.components.RealUpiPaymentBottomSheet(
            title = "Ad Boost: ${headline.take(22).ifBlank { "Campaign" }}",
            subtitle = "${durationDays}-Day Ad Promotion Budget",
            amountRupees = pendingPaymentAmount,
            productType = com.example.model.PaymentProductType.AD_BOOST_PROMOTION,
            productId = pendingPaymentAdId ?: "",
            contentId = pendingPaymentCampaignId ?: "",
            onDismiss = {
                pendingPaymentCampaignId = null
                pendingPaymentAdId = null
            },
            onPaymentVerified = { _ ->
                val adIdToReturn = pendingPaymentAdId ?: ""
                pendingPaymentCampaignId = null
                pendingPaymentAdId = null
                Toast.makeText(context, "UPI Payment Verified! Ad Campaign is now ACTIVE.", Toast.LENGTH_LONG).show()
                onAdCreated(adIdToReturn)
            },
            onUtrSubmitted = { _, utr ->
                val adIdToReturn = pendingPaymentAdId ?: ""
                pendingPaymentCampaignId = null
                pendingPaymentAdId = null
                Toast.makeText(context, "UTR $utr submitted! Ad Boost will go live on Admin approval.", Toast.LENGTH_LONG).show()
                onAdCreated(adIdToReturn)
            }
        )
    }

    // Media pickers
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isUploadingMedia = true
            scope.launch {
                try {
                    val url = RepositoryManager.mediaRepository.uploadMedia(it) { p ->
                        uploadProgress = p
                    }
                    if (url != null) {
                        uploadedMediaUrls = if (selectedFormat == AdFormat.CAROUSEL) {
                            uploadedMediaUrls + url
                        } else {
                            listOf(url)
                        }
                        Toast.makeText(context, "Media uploaded successfully!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isUploadingMedia = false
                }
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isUploadingMedia = true
            scope.launch {
                try {
                    val url = RepositoryManager.mediaRepository.uploadMedia(it) { p ->
                        uploadProgress = p
                    }
                    if (url != null) {
                        uploadedMediaUrls = listOf(url)
                        Toast.makeText(context, "Video uploaded successfully!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Video upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isUploadingMedia = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            val uid = RepositoryManager.getCurrentUserId()
            if (uid.isNotEmpty()) {
                val userDoc = RepositoryManager.firestore.collection("users").document(uid).get().await()
                currentUser = userDoc.toObject(User::class.java)?.copy(id = userDoc.id)

                val postsSnap = RepositoryManager.firestore.collection("posts")
                    .whereEqualTo("userId", uid)
                    .limit(20)
                    .get()
                    .await()
                existingPosts = postsSnap.documents.mapNotNull { it.toObject(FirebasePost::class.java)?.copy(id = it.id) }

                if (preselectedPostId != null) {
                    val found = existingPosts.firstOrNull { it.id == preselectedPostId }
                    if (found != null) {
                        selectedPost = found
                        mediaSource = "EXISTING_POST"
                        uploadedMediaUrls = if (found.postUrls.isNotEmpty()) found.postUrls else listOf(found.postUrl)
                        caption = found.caption
                        primaryText = found.caption
                        headline = "Special Promotion"
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Create Advertisement", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Step $currentStep of 5 • ${when (currentStep) {
                            1 -> "Format & Media"
                            2 -> "Creative & CTA"
                            3 -> "Objective & Audience"
                            4 -> "Budget & Placements"
                            else -> "Review & Publish"
                        }}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep > 1) currentStep-- else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep > 1) {
                        OutlinedButton(
                            onClick = { currentStep-- },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Previous")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (currentStep < 5) {
                        Button(
                            onClick = {
                                if (currentStep == 1 && uploadedMediaUrls.isEmpty()) {
                                    Toast.makeText(context, "Please upload or select media first", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (currentStep == 2 && headline.trim().isEmpty()) {
                                    Toast.makeText(context, "Please enter an ad headline", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                currentStep++
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("ad_wizard_next_button")
                        ) {
                            Text("Continue", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        // Submit & Pay via Real Indian UPI Gateway
                        Button(
                            onClick = {
                                val amount = budgetAmountText.toDoubleOrNull() ?: 500.0
                                if (amount < 100.0) {
                                    Toast.makeText(context, "Minimum daily budget is ₹100", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                val uid = RepositoryManager.getCurrentUserId()
                                if (uid.isEmpty()) {
                                    Toast.makeText(context, "Please log in first", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                val payableInr = if (budgetType == BudgetType.DAILY) amount * durationDays else amount
                                isSubmitting = true
                                scope.launch {
                                    val cId = UUID.randomUUID().toString()
                                    val sId = UUID.randomUUID().toString()
                                    val aId = UUID.randomUUID().toString()

                                    val campaign = Campaign(
                                        campaignId = cId,
                                        advertiserUid = uid,
                                        name = "${headline.take(20)} Campaign",
                                        objective = selectedObjective,
                                        status = AdStatus.UNDER_REVIEW,
                                        budgetType = budgetType,
                                        budgetAmount = amount,
                                        spentAmount = 0.0,
                                        currency = "INR",
                                        startAt = System.currentTimeMillis(),
                                        endAt = System.currentTimeMillis() + (durationDays.toLong() * 24 * 60 * 60 * 1000)
                                    )

                                    val adSet = AdSet(
                                        adSetId = sId,
                                        campaignId = cId,
                                        name = "Target Audience Set",
                                        audience = TargetingCriteria(
                                            countries = listOf(targetCountry),
                                            minAge = minAge.toInt(),
                                            maxAge = maxAge.toInt(),
                                            interests = selectedInterests
                                        ),
                                        placements = selectedPlacements,
                                        status = AdStatus.ACTIVE
                                    )

                                    val ad = Advertisement(
                                        adId = aId,
                                        campaignId = cId,
                                        adSetId = sId,
                                        advertiserUid = uid,
                                        advertiserName = currentUser?.businessName?.ifEmpty { currentUser?.displayName ?: "Business" } ?: "Business",
                                        advertiserUsername = currentUser?.username ?: "advertiser",
                                        advertiserAvatarUrl = currentUser?.avatarUrl ?: "",
                                        advertiserCategory = currentUser?.businessCategory ?: "Brand",
                                        format = selectedFormat,
                                        mediaUrls = uploadedMediaUrls,
                                        thumbnailUrl = uploadedMediaUrls.firstOrNull() ?: "",
                                        headline = headline.trim(),
                                        primaryText = primaryText.trim(),
                                        caption = caption.trim(),
                                        callToAction = selectedCTA,
                                        destinationUrl = destinationUrl.trim(),
                                        status = AdStatus.UNDER_REVIEW,
                                        sourcePostId = selectedPost?.id,
                                        placements = selectedPlacements
                                    )

                                    val res = AdvertisingRepository.createCampaignWithAd(campaign, adSet, ad)
                                    isSubmitting = false
                                    if (res.isSuccess) {
                                        pendingPaymentAmount = payableInr
                                        pendingPaymentCampaignId = cId
                                        pendingPaymentAdId = aId
                                    } else {
                                        Toast.makeText(context, "Failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !isSubmitting,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("submit_ad_button")
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pay via UPI & Launch Ad", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Progress Bar for Wizard
                LinearProgressIndicator(
                    progress = { currentStep / 5f },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                )

                when (currentStep) {
                    1 -> {
                        // ================= STEP 1: Format & Media =================
                        Text("Choose Ad Format", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        val formats = listOf(
                            Triple(AdFormat.PHOTO, Icons.Outlined.Photo, "Photo Ad"),
                            Triple(AdFormat.VIDEO, Icons.Outlined.Videocam, "Video Ad"),
                            Triple(AdFormat.STORY, Icons.Outlined.AutoStories, "Story Ad"),
                            Triple(AdFormat.REEL, Icons.Outlined.MovieCreation, "Reel Ad"),
                            Triple(AdFormat.CAROUSEL, Icons.Outlined.ViewCarousel, "Carousel Ad")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            formats.forEach { (fmt, icon, label) ->
                                val isSelected = selectedFormat == fmt
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedFormat = fmt
                                        if (fmt != AdFormat.CAROUSEL && uploadedMediaUrls.size > 1) {
                                            uploadedMediaUrls = uploadedMediaUrls.take(1)
                                        }
                                    },
                                    label = { Text(label, fontSize = 12.sp) },
                                    leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Media Source: Upload vs Existing Post
                        Text("Select Media Source", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedCard(
                                onClick = { mediaSource = "NEW_UPLOAD" },
                                modifier = Modifier.weight(1f),
                                border = if (mediaSource == "NEW_UPLOAD") BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else CardDefaults.outlinedCardBorder()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Upload Media", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                }
                            }
                            OutlinedCard(
                                onClick = { mediaSource = "EXISTING_POST" },
                                modifier = Modifier.weight(1f),
                                border = if (mediaSource == "EXISTING_POST") BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else CardDefaults.outlinedCardBorder()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Outlined.Collections, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Existing Post", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                }
                            }
                        }

                        if (mediaSource == "NEW_UPLOAD") {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth().height(160.dp).clickable {
                                    if (selectedFormat == AdFormat.VIDEO || selectedFormat == AdFormat.REEL) {
                                        videoPicker.launch("video/*")
                                    } else {
                                        photoPicker.launch("image/*")
                                    }
                                },
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    if (isUploadingMedia) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator()
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Uploading $uploadProgress%...", fontSize = 13.sp)
                                        }
                                    } else if (uploadedMediaUrls.isNotEmpty()) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(36.dp))
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text("${uploadedMediaUrls.size} media file(s) attached", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text("Tap to add / replace media", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text("Tap to upload ${selectedFormat.lowercase()} creative", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                            Text("Supports high-res JPG, PNG, MP4", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }

                            // Carousel media list preview
                            if (selectedFormat == AdFormat.CAROUSEL && uploadedMediaUrls.isNotEmpty()) {
                                Text("Carousel Cards (${uploadedMediaUrls.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(uploadedMediaUrls) { mUrl ->
                                        Box(modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp))) {
                                            AsyncImage(model = mUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        }
                                    }
                                }
                            }
                        } else {
                            // Existing posts selection
                            Text("Select an existing post to promote", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (existingPosts.isEmpty()) {
                                Text("No existing posts found on this account.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(existingPosts) { post ->
                                        val isSelected = selectedPost?.id == post.id
                                        Card(
                                            modifier = Modifier.size(100.dp).clickable {
                                                selectedPost = post
                                                uploadedMediaUrls = if (post.postUrls.isNotEmpty()) post.postUrls else listOf(post.postUrl)
                                                caption = post.caption
                                                primaryText = post.caption
                                                headline = "Featured Post"
                                            },
                                            border = if (isSelected) borderStroke(MaterialTheme.colorScheme.primary) else null
                                        ) {
                                            AsyncImage(
                                                model = post.postUrl,
                                                contentDescription = post.caption,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // ================= STEP 2: Creative & CTA =================
                        Text("Ad Copy & Destination", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                        OutlinedTextField(
                            value = headline,
                            onValueChange = { headline = it },
                            label = { Text("Headline *") },
                            placeholder = { Text("e.g. Exclusive 30% Off This Week Only!") },
                            leadingIcon = { Icon(Icons.Outlined.Title, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth().testTag("ad_headline_input"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = primaryText,
                            onValueChange = { primaryText = it },
                            label = { Text("Primary Text / Hook") },
                            placeholder = { Text("Describe your offer, value proposition, and why users should care...") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 5
                        )

                        OutlinedTextField(
                            value = caption,
                            onValueChange = { caption = it },
                            label = { Text("Secondary Caption (Optional)") },
                            placeholder = { Text("Tags, disclosures, or hashtags...") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Call to Action
                        ExposedDropdownMenuBox(
                            expanded = showCtaDropdown,
                            onExpandedChange = { showCtaDropdown = !showCtaDropdown }
                        ) {
                            OutlinedTextField(
                                value = selectedCTA,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Call To Action (CTA) Button *") },
                                leadingIcon = { Icon(Icons.Outlined.TouchApp, contentDescription = null) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCtaDropdown) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = showCtaDropdown,
                                onDismissRequest = { showCtaDropdown = false }
                            ) {
                                CallToAction.supportedCTAs.forEach { cta ->
                                    DropdownMenuItem(
                                        text = { Text(cta) },
                                        onClick = {
                                            selectedCTA = cta
                                            showCtaDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        // Destination URL
                        OutlinedTextField(
                            value = destinationUrl,
                            onValueChange = { destinationUrl = it },
                            label = { Text("Destination URL / Landing Page *") },
                            placeholder = { Text("https://www.yourbusiness.com/store") },
                            leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth().testTag("ad_destination_url_input"),
                            singleLine = true
                        )
                    }

                    3 -> {
                        // ================= STEP 3: Objective & Audience =================
                        Text("Campaign Objective", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        val objectives = listOf(
                            CampaignObjective.AWARENESS,
                            CampaignObjective.REACH,
                            CampaignObjective.ENGAGEMENT,
                            CampaignObjective.WEBSITE_VISITS,
                            CampaignObjective.PROFILE_VISITS,
                            CampaignObjective.MESSAGES,
                            CampaignObjective.APP_PROMOTION
                        )
                        objectives.forEach { obj ->
                            val isSelected = selectedObjective == obj
                            Card(
                                onClick = { selectedObjective = obj },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                border = if (isSelected) borderStroke(MaterialTheme.colorScheme.primary) else null
                            ) {
                                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = isSelected, onClick = { selectedObjective = obj })
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(CampaignObjective.getDisplayName(obj), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(CampaignObjective.getDescription(obj), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        Text("Privacy-Safe Audience Targeting", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        OutlinedTextField(
                            value = targetCountry,
                            onValueChange = { targetCountry = it },
                            label = { Text("Target Country") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Age Range: ${minAge.toInt()} - ${maxAge.toInt()} years", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        RangeSlider(
                            value = minAge..maxAge,
                            onValueChange = { range ->
                                minAge = range.start
                                maxAge = range.endInclusive
                            },
                            valueRange = 18f..65f,
                            steps = 46
                        )

                        Text("Target Interests", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            availableInterests.forEach { interest ->
                                val isSelected = selectedInterests.contains(interest)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedInterests = if (isSelected) {
                                            selectedInterests - interest
                                        } else {
                                            selectedInterests + interest
                                        }
                                    },
                                    label = { Text(interest, fontSize = 12.sp) }
                                )
                            }
                        }
                    }

                    4 -> {
                        // ================= STEP 4: Budget & Placements =================
                        Text("Ad Placements", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Where your advertisement will appear across Social India:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        AdPlacement.allPlacements.forEach { placement ->
                            val isChecked = selectedPlacements.contains(placement)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPlacements = if (isChecked) {
                                            if (selectedPlacements.size > 1) selectedPlacements - placement else selectedPlacements
                                        } else {
                                            selectedPlacements + placement
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        selectedPlacements = if (isChecked) {
                                            if (selectedPlacements.size > 1) selectedPlacements - placement else selectedPlacements
                                        } else {
                                            selectedPlacements + placement
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(AdPlacement.getDisplayName(placement), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(
                                        when (placement) {
                                            AdPlacement.HOME_FEED -> "Main feed cards among organic posts"
                                            AdPlacement.REELS -> "Full-screen vertical reels player"
                                            AdPlacement.STORIES -> "Between stories for high engagement"
                                            else -> "Explore discovery and search tabs"
                                        },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Text("Budget Configuration", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = budgetType == BudgetType.DAILY,
                                onClick = { budgetType = BudgetType.DAILY },
                                label = { Text("Daily Budget") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = budgetType == BudgetType.LIFETIME,
                                onClick = { budgetType = BudgetType.LIFETIME },
                                label = { Text("Lifetime Budget") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = budgetAmountText,
                            onValueChange = { budgetAmountText = it },
                            label = { Text("Budget Amount (INR ₹) *") },
                            placeholder = { Text("500") },
                            leadingIcon = { Text("₹", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("budget_amount_input"),
                            singleLine = true
                        )

                        Text("Campaign Duration: $durationDays days", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Slider(
                            value = durationDays.toFloat(),
                            onValueChange = { durationDays = it.toInt() },
                            valueRange = 1f..30f,
                            steps = 28
                        )
                    }

                    5 -> {
                        // ================= STEP 5: Review & Publish =================
                        Text("Live Advertisement Preview", fontWeight = FontWeight.Bold, fontSize = 18.sp)

                        AdPreviewCard(
                            format = selectedFormat,
                            headline = headline,
                            primaryText = primaryText,
                            callToAction = selectedCTA,
                            destinationUrl = destinationUrl,
                            businessName = currentUser?.businessName?.ifEmpty { currentUser?.displayName ?: "Business" } ?: "Your Business",
                            category = currentUser?.businessCategory ?: "Brand",
                            avatarUrl = currentUser?.avatarUrl ?: "",
                            mediaUrls = uploadedMediaUrls
                        )

                        Text("Review Summary", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Objective:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                    Text(CampaignObjective.getDisplayName(selectedObjective), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Budget:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                    Text("₹$budgetAmountText ($budgetType)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Duration:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                    Text("$durationDays Days", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Placements:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                    Text("${selectedPlacements.size} selected", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Target Age:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                    Text("${minAge.toInt()} - ${maxAge.toInt()}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }

                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Security, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Submitted ads undergo standard policy review (under review status). Once verified by an administrator, your ad will become active automatically.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

private fun borderStroke(color: Color) = androidx.compose.foundation.BorderStroke(2.dp, color)
