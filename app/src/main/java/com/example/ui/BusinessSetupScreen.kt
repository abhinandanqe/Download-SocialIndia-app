package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.AccountType
import com.example.model.CallToAction
import com.example.model.RepositoryManager
import com.example.model.User
import com.example.repository.AdvertisingRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessSetupScreen(
    onBack: () -> Unit,
    onSetupComplete: () -> Unit,
    onNavigateToVerification: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var currentUser by remember { mutableStateOf<User?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    var businessName by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("E-Commerce & Retail") }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("India") }
    var hours by remember { mutableStateOf("Mon - Sat: 9:00 AM - 7:00 PM") }
    var actionButton by remember { mutableStateOf(CallToAction.VISIT_WEBSITE) }
    var actionUrl by remember { mutableStateOf("") }
    var showActionDropdown by remember { mutableStateOf(false) }
    var logoUrl by remember { mutableStateOf("") }

    val categories = listOf(
        "E-Commerce & Retail",
        "Restaurant & Food",
        "Local Business & Store",
        "Tech & Software",
        "Digital Creator & Agency",
        "Health & Wellness",
        "Education & Coaching",
        "Entertainment & Media",
        "Fashion & Apparel",
        "Professional Services",
        "Travel & Hospitality",
        "Automotive",
        "Other"
    )

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    Toast.makeText(context, "Uploading logo...", Toast.LENGTH_SHORT).show()
                    val uploaded = RepositoryManager.mediaRepository.uploadMedia(it)
                    if (uploaded != null) {
                        logoUrl = uploaded
                        val uid = RepositoryManager.getCurrentUserId()
                        if (uid.isNotEmpty()) {
                            RepositoryManager.firestore.collection("users").document(uid).update(
                                mapOf("avatar_url" to uploaded, "profilePicUrl" to uploaded)
                            ).await()
                        }
                        Toast.makeText(context, "Logo updated!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            val uid = RepositoryManager.getCurrentUserId()
            if (uid.isNotEmpty()) {
                val doc = RepositoryManager.firestore.collection("users").document(uid).get().await()
                currentUser = doc.toObject(User::class.java)?.copy(id = doc.id)
                currentUser?.let { user ->
                    businessName = if (user.businessName.isNotEmpty()) user.businessName else user.displayName.ifEmpty { user.username }
                    if (user.businessCategory.isNotEmpty()) selectedCategory = user.businessCategory
                    description = if (user.businessDescription.isNotEmpty()) user.businessDescription else user.bio
                    website = if (user.businessWebsite.isNotEmpty()) user.businessWebsite else user.websiteUrl
                    email = if (user.businessEmail.isNotEmpty()) user.businessEmail else user.email
                    phone = user.businessPhone
                    address = user.businessAddress
                    city = user.businessCity
                    state = user.businessState
                    if (user.businessCountry.isNotEmpty()) country = user.businessCountry
                    if (user.businessHours.isNotEmpty()) hours = user.businessHours
                    if (user.businessActionButton.isNotEmpty()) actionButton = user.businessActionButton
                    actionUrl = if (user.businessActionUrl.isNotEmpty()) user.businessActionUrl else user.websiteUrl
                    logoUrl = user.avatarUrl
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
                        Text(
                            text = if (currentUser?.accountType == AccountType.BUSINESS) "Business Profile Settings" else "Switch to Business Account",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Professional suite for brands and enterprises",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (currentUser?.isVerified != true && currentUser?.verificationStatus != "approved") {
            // Hard verification requirement block
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF1DA1F2).copy(alpha = 0.15f),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = Color(0xFF1DA1F2),
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }

                        Text(
                            text = "Social India Verification Required",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Text(
                            text = "Business Account requires an active Social India Verification.\n\nTo ensure an authentic, safe, and trustworthy experience for customers across India, business accounts and storefront features are exclusively reserved for verified accounts.",
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = onNavigateToVerification,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Verified, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Get Verified", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Go Back")
                        }
                    }
                }
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
                // Info banner
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Storefront,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Social India Business",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 15.sp
                            )
                            Text(
                                "Connect with your audience, launch real ad campaigns, and showcase your products & contact information.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                // Business Logo / Avatar
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .clickable { imagePicker.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (logoUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = logoUrl,
                                    contentDescription = "Business Logo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Outlined.AddBusiness,
                                    contentDescription = "Upload Logo",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap to upload Business Logo / Photo",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Business Name & Category
                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    label = { Text("Business Name *") },
                    placeholder = { Text("e.g. Acme Tech Solutions") },
                    leadingIcon = { Icon(Icons.Outlined.Business, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().testTag("business_name_input"),
                    singleLine = true
                )

                // Category selector
                ExposedDropdownMenuBox(
                    expanded = showCategoryDropdown,
                    onExpandedChange = { showCategoryDropdown = !showCategoryDropdown }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Business Category *") },
                        leadingIcon = { Icon(Icons.Outlined.Category, contentDescription = null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryDropdown) },
                        modifier = Modifier.menuAnchor().fillMaxWidth().testTag("category_dropdown")
                    )
                    ExposedDropdownMenu(
                        expanded = showCategoryDropdown,
                        onDismissRequest = { showCategoryDropdown = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    selectedCategory = cat
                                    showCategoryDropdown = false
                                }
                            )
                        }
                    }
                }

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Business Description / About *") },
                    placeholder = { Text("Describe products, services, and vision...") },
                    leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().testTag("business_desc_input"),
                    minLines = 3,
                    maxLines = 5
                )

                Text(
                    text = "Contact & Action Controls",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Website
                OutlinedTextField(
                    value = website,
                    onValueChange = { website = it },
                    label = { Text("Website") },
                    placeholder = { Text("https://www.yourbusiness.com") },
                    leadingIcon = { Icon(Icons.Outlined.Language, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().testTag("business_website_input"),
                    singleLine = true
                )

                // Email
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Business Email") },
                    placeholder = { Text("contact@yourbusiness.com") },
                    leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().testTag("business_email_input"),
                    singleLine = true
                )

                // Phone
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Business Phone / WhatsApp") },
                    placeholder = { Text("+91 98765 43210") },
                    leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().testTag("business_phone_input"),
                    singleLine = true
                )

                // Action button type
                ExposedDropdownMenuBox(
                    expanded = showActionDropdown,
                    onExpandedChange = { showActionDropdown = !showActionDropdown }
                ) {
                    OutlinedTextField(
                        value = actionButton,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Profile Action Button") },
                        leadingIcon = { Icon(Icons.Outlined.TouchApp, contentDescription = null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showActionDropdown) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = showActionDropdown,
                        onDismissRequest = { showActionDropdown = false }
                    ) {
                        CallToAction.supportedCTAs.forEach { cta ->
                            DropdownMenuItem(
                                text = { Text(cta) },
                                onClick = {
                                    actionButton = cta
                                    showActionDropdown = false
                                }
                            )
                        }
                    }
                }

                // Action URL / destination
                OutlinedTextField(
                    value = actionUrl,
                    onValueChange = { actionUrl = it },
                    label = { Text("Action Button Destination (URL or Phone)") },
                    placeholder = { Text("https://yourstore.com/shop or tel:+91...") },
                    leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    text = "Location & Operating Hours (Optional)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Address
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Street Address") },
                    placeholder = { Text("e.g. 102 MG Road") },
                    leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("City") },
                        placeholder = { Text("Bengaluru") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = state,
                        onValueChange = { state = it },
                        label = { Text("State") },
                        placeholder = { Text("Karnataka") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = hours,
                    onValueChange = { hours = it },
                    label = { Text("Business Operating Hours") },
                    placeholder = { Text("e.g. Mon-Sat: 9:00 AM - 7:00 PM") },
                    leadingIcon = { Icon(Icons.Outlined.AccessTime, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Submit button
                Button(
                    onClick = {
                        if (businessName.trim().isEmpty()) {
                            Toast.makeText(context, "Please enter your business name", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val uid = RepositoryManager.getCurrentUserId()
                        if (uid.isEmpty()) {
                            Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSaving = true
                        scope.launch {
                            val res = AdvertisingRepository.updateBusinessProfile(
                                userId = uid,
                                businessName = businessName.trim(),
                                category = selectedCategory,
                                description = description.trim(),
                                website = website.trim(),
                                email = email.trim(),
                                phone = phone.trim(),
                                address = address.trim(),
                                city = city.trim(),
                                state = state.trim(),
                                country = country.trim(),
                                hours = hours.trim(),
                                actionButton = actionButton,
                                actionUrl = actionUrl.trim()
                            )
                            isSaving = false
                            if (res.isSuccess) {
                                Toast.makeText(context, "Business Account activated successfully!", Toast.LENGTH_SHORT).show()
                                onSetupComplete()
                            } else {
                                Toast.makeText(context, "Failed to save: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_business_profile_button"),
                    enabled = !isSaving,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (currentUser?.accountType == AccountType.BUSINESS) "Save Business Profile" else "Activate Business Account",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
