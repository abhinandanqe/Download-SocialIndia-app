package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.model.FirebasePost
import com.example.model.RepositoryManager
import com.example.model.StoreOrder
import com.example.model.StoreProduct
import com.example.repository.StoreRepository
import com.example.ui.components.PaymentSuccessAnimationDialog
import com.example.utils.QrCodeGenerator
import com.example.utils.SoundManager
import com.example.utils.UpiPaymentManager
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCheckout: (StoreProduct) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserId = remember { RepositoryManager.getCurrentUserId() }

    val products by StoreRepository.getStoreProductsFlow()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Trending 🔥", "Merch 👑", "Apparel 👕", "Digital 🎨", "Gadgets ⚡")

    var showAddProductDialog by remember { mutableStateOf(false) }
    var selectedProductForDetail by remember { mutableStateOf<StoreProduct?>(null) }
    var selectedProductForCheckout by remember { mutableStateOf<StoreProduct?>(null) }
    var completedOrderForSuccessAnimation by remember { mutableStateOf<StoreOrder?>(null) }

    // Authoritative Admin verification: Only verified Admin can list/delete products!
    var isCurrentUserAdmin by remember { mutableStateOf(false) }

    LaunchedEffect(currentUserId) {
        isCurrentUserAdmin = StoreRepository.checkIsAdmin(currentUserId)
    }

    val filteredProducts = remember(products, searchQuery, selectedCategory) {
        products.filter { product ->
            val matchesQuery = searchQuery.isBlank() || 
                product.title.contains(searchQuery, ignoreCase = true) ||
                product.description.contains(searchQuery, ignoreCase = true)
            val matchesCat = selectedCategory == "All" || product.category.contains(selectedCategory.take(5), ignoreCase = true)
            matchesQuery && matchesCat
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Social India Store",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified Official Store",
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Admin Add Product Button - ONLY VISIBLE TO VERIFIED ADMINS
                    if (isCurrentUserAdmin) {
                        FilledTonalButton(
                            onClick = {
                                SoundManager.playClickSound()
                                showAddProductDialog = true
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFFFF3366).copy(alpha = 0.15f),
                                contentColor = Color(0xFFFF3366)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Add Product",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { 
                        Text("Search products, merch, reels items...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) 
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        focusedBorderColor = Color(0xFFFF3366),
                        unfocusedBorderColor = Color.Transparent
                    ),
                    singleLine = true
                )

                // Category Chips
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { cat ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFFFF3366) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clickable {
                                    SoundManager.playClickSound()
                                    selectedCategory = cat
                                }
                        ) {
                            Text(
                                text = cat,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (isCurrentUserAdmin) {
                FloatingActionButton(
                    onClick = {
                        SoundManager.playClickSound()
                        showAddProductDialog = true
                    },
                    containerColor = Color(0xFFFF3366),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.navigationBarsPadding().padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Product")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("List Product (Admin)", fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (filteredProducts.isEmpty()) {
                // Empty state with invitation to add products
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF3366).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            tint = Color(0xFFFF3366),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Store is Getting Ready!",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Admin can list products with photos, videos, and prices directly from Social India videos or gallery.",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.SansSerif,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    if (isCurrentUserAdmin) {
                        Button(
                            onClick = { showAddProductDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3366)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("List First Product (Admin)", fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
                        StoreProductCard(
                            product = product,
                            isAdmin = isCurrentUserAdmin,
                            onClick = { selectedProductForDetail = product },
                            onDelete = {
                                scope.launch {
                                    StoreRepository.deleteProduct(product.id)
                                    Toast.makeText(context, "Product removed from store", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onBuyNow = {
                                selectedProductForCheckout = product
                            }
                        )
                    }
                }
            }
        }
    }

    // Dialog for Admin to Add New Product (Double-check Admin authority)
    if (showAddProductDialog && isCurrentUserAdmin) {
        AdminAddProductDialog(
            onDismiss = { showAddProductDialog = false },
            onProductAdded = {
                showAddProductDialog = false
                Toast.makeText(context, "Product listed in Social India Store! 🎉", Toast.LENGTH_LONG).show()
            }
        )
    }

    // Product Detail Sheet
    selectedProductForDetail?.let { product ->
        ProductDetailBottomSheet(
            product = product,
            onDismiss = { selectedProductForDetail = null },
            onBuyViaUpi = {
                selectedProductForDetail = null
                selectedProductForCheckout = product
            }
        )
    }

    // Real Authoritative Secure Checkout Sheet
    selectedProductForCheckout?.let { product ->
        StoreCheckoutBottomSheet(
            product = product,
            onDismiss = { selectedProductForCheckout = null },
            onPaymentSuccess = { order ->
                selectedProductForCheckout = null
                completedOrderForSuccessAnimation = order
            }
        )
    }

    // High-End Payment Success Animation Dialog
    completedOrderForSuccessAnimation?.let { order ->
        PaymentSuccessAnimationDialog(
            orderId = order.orderId,
            amount = order.productPrice,
            productTitle = order.productTitle,
            txnIdOrUtr = order.txnId.ifBlank { order.utrNumber },
            onDismiss = { completedOrderForSuccessAnimation = null }
        )
    }
}

@Composable
fun StoreProductCard(
    product: StoreProduct,
    isAdmin: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onBuyNow: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .graphicsLayer {
                // Subtle 3D elevation
                shadowElevation = 4f
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Color(0xFF1E1E24))
            ) {
                AsyncImage(
                    model = if (product.thumbnailUrl.isNotBlank()) product.thumbnailUrl else product.mediaUrl,
                    contentDescription = product.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Video Badge
                if (product.isVideo) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Video", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                        }
                    }
                }

                // Delete Button for Admin
                if (isAdmin) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(28.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                    }
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = product.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FontFamily.SansSerif,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "₹${product.price.toInt()}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.SansSerif,
                        color = Color(0xFF00E676)
                    )
                    if (product.originalPrice > product.price) {
                        Text(
                            text = "₹${product.originalPrice.toInt()}",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onBuyNow,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3366)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(34.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Buy Now", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                }
            }
        }
    }
}

/**
 * Admin Dialog to Add Products:
 * - Enter Amount
 * - Enter Name & Description
 * - Pick Media from phone gallery OR directly select from Social India existing videos/posts!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAddProductDialog(
    onDismiss: () -> Unit,
    onProductAdded: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var originalPriceText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Trending") }
    
    var selectedMediaUrl by remember { mutableStateOf("") }
    var isVideoMedia by remember { mutableStateOf(false) }
    var mediaSourceTab by remember { mutableIntStateOf(0) } // 0: Social India Videos, 1: Gallery
    var isSubmitting by remember { mutableStateOf(false) }

    // Real Social India Videos loaded from Firestore
    var platformPosts by remember { mutableStateOf<List<FirebasePost>>(emptyList()) }
    var isLoadingPlatformPosts by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoadingPlatformPosts = true
        platformPosts = StoreRepository.getSocialIndiaPosts()
        isLoadingPlatformPosts = false
    }

    // Media Picker for device gallery
    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedMediaUrl = uri.toString()
            isVideoMedia = uri.toString().contains("video", ignoreCase = true) || uri.toString().contains(".mp4", ignoreCase = true)
            Toast.makeText(context, "Media selected from gallery", Toast.LENGTH_SHORT).show()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Add Product to Store (Admin)",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                fontFamily = FontFamily.SansSerif,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Title Input
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Product Title", fontFamily = FontFamily.SansSerif) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Price & Original Price (₹ Amount)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Price (₹ Amount)*", fontFamily = FontFamily.SansSerif) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = originalPriceText,
                    onValueChange = { originalPriceText = it },
                    label = { Text("Original Price (₹)", fontFamily = FontFamily.SansSerif) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description & Highlights", fontFamily = FontFamily.SansSerif) },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Media Selection Method Tabs
            Text(
                text = "Product Photo / Video Source:",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                fontFamily = FontFamily.SansSerif,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = mediaSourceTab == 0,
                    onClick = { mediaSourceTab = 0 },
                    label = { Text("Social India Videos 📱", fontFamily = FontFamily.SansSerif) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = mediaSourceTab == 1,
                    onClick = { mediaSourceTab = 1 },
                    label = { Text("Device Gallery 📁", fontFamily = FontFamily.SansSerif) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (mediaSourceTab == 0) {
                // Pick from Social India Posts/Videos
                if (isLoadingPlatformPosts) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFFFF3366))
                    }
                } else if (platformPosts.isEmpty()) {
                    Text("No platform posts available yet.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Tap any Social India video/photo to attach:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(platformPosts) { post ->
                            val pUrl = post.postUrl.ifEmpty { post.thumbnailUrl ?: "" }
                            val isSelected = selectedMediaUrl == pUrl
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) Color(0xFFFF3366) else Color.Gray.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        selectedMediaUrl = pUrl
                                        isVideoMedia = post.mediaType == "video" || post.postUrl.contains(".mp4")
                                    }
                            ) {
                                AsyncImage(
                                    model = post.thumbnailUrl ?: pUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(Color(0xFFFF3366).copy(alpha = 0.4f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Pick from gallery
                Button(
                    onClick = { galleryPicker.launch("*/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (selectedMediaUrl.isNotBlank()) "Change Media ✅" else "Choose Photo / Video from Gallery",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // Preview of selected media
            if (selectedMediaUrl.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.DarkGray)
                    ) {
                        AsyncImage(
                            model = selectedMediaUrl,
                            contentDescription = "Selected media",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isVideoMedia) "Video attached ✅" else "Photo attached ✅",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676),
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Submit Button
            Button(
                onClick = {
                    val price = priceText.toDoubleOrNull()
                    if (title.isBlank() || price == null || price <= 0) {
                        Toast.makeText(context, "Please enter product title and valid price amount", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val currentUid = RepositoryManager.getCurrentUserId()
                    isSubmitting = true
                    scope.launch {
                        if (!StoreRepository.checkIsAdmin(currentUid)) {
                            isSubmitting = false
                            Toast.makeText(context, "Unauthorized: Only official admins can publish products to the Store", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        val product = StoreProduct(
                            title = title.trim(),
                            description = description.trim(),
                            price = price,
                            originalPrice = originalPriceText.toDoubleOrNull() ?: (price * 1.3),
                            mediaUrl = selectedMediaUrl,
                            isVideo = isVideoMedia,
                            thumbnailUrl = selectedMediaUrl,
                            category = category,
                            creatorId = RepositoryManager.getCurrentUserId(),
                            creatorName = "Social India Official Store"
                        )
                        val res = StoreRepository.addProduct(product)
                        isSubmitting = false
                        if (res.isSuccess) {
                            onProductAdded()
                        } else {
                            Toast.makeText(context, "Error listing product: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3366)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Publish to Store", fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailBottomSheet(
    product: StoreProduct,
    onDismiss: () -> Unit,
    onBuyViaUpi: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // Media Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = product.mediaUrl.ifEmpty { product.thumbnailUrl },
                    contentDescription = product.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = product.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "₹${product.price.toInt()}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif,
                    color = Color(0xFF00E676)
                )
                if (product.originalPrice > product.price) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "₹${product.originalPrice.toInt()}",
                        fontSize = 15.sp,
                        fontFamily = FontFamily.SansSerif,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = TextDecoration.LineThrough
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFD700).copy(alpha = 0.2f),
                    modifier = Modifier.padding(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("${product.rating} (${product.reviewsCount})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = product.description.ifEmpty { "High quality official product backed by Social India Authentic Guarantee." },
                fontSize = 14.sp,
                fontFamily = FontFamily.SansSerif,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onBuyViaUpi,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3366)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Instant Buy via UPI (₹${product.price.toInt()})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
    }
}

/**
 * Real Authoritative UPI Payment Gateway & Anti-Fraud Tracking BottomSheet
 * Enforces:
 * - Real NPCI intent launcher with response parsing
 * - Dual account selection (Primary / Secondary Admin UPI ID)
 * - Real QR Code generator
 * - Strict 12-digit UTR format validation
 * - Anti-fraud duplicate detection in Firestore
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreCheckoutBottomSheet(
    product: StoreProduct,
    onDismiss: () -> Unit,
    onPaymentSuccess: (StoreOrder) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserId = remember { RepositoryManager.getCurrentUserId() }

    var selectedTab by remember { mutableStateOf(0) } // 0 = UPI App, 1 = QR & UTR
    var selectedAccountIndex by remember { mutableStateOf(0) } // 0 = Primary, 1 = Secondary
    var utrInput by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val activeUpiId = remember(selectedAccountIndex) {
        if (selectedAccountIndex == 0) UpiPaymentManager.getPrimaryUpiId() else UpiPaymentManager.getSecondaryUpiId()
    }

    val orderId = remember(product.id) {
        "ORD_STORE_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6).uppercase()}"
    }

    // Real UPI Intent Launcher
    val upiLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val response = data?.getStringExtra("response")
            ?: data?.dataString
            ?: data?.extras?.getString("response")
            ?: ""

        if (response.isBlank()) {
            errorMessage = "Payment was cancelled or returned empty from your UPI app. Entitlement NOT granted."
            return@rememberLauncherForActivityResult
        }

        // Parse standard UPI key-value response
        val responseMap = response.split("&").associate {
            val parts = it.split("=")
            if (parts.size >= 2) parts[0].lowercase(Locale.ROOT) to parts[1] else "" to ""
        }

        val status = responseMap["status"] ?: ""
        val responseCode = responseMap["responsecode"] ?: ""
        val isRealSuccess = (status.equals("SUCCESS", ignoreCase = true) || status.equals("COMPLETED", ignoreCase = true)) &&
                (responseCode == "00" || responseCode == "0" || responseCode.isEmpty())

        if (isRealSuccess) {
            val txnId = responseMap["txnid"] ?: responseMap["approvalrefno"] ?: "TXN_${System.currentTimeMillis()}"
            isVerifying = true
            scope.launch {
                val order = StoreOrder(
                    orderId = orderId,
                    productId = product.id,
                    productTitle = product.title,
                    productPrice = product.price,
                    buyerId = currentUserId,
                    txnId = txnId,
                    paymentMode = "DIRECT_UPI_APP",
                    status = "VERIFIED_PAID",
                    createdAt = System.currentTimeMillis()
                )
                StoreRepository.recordStoreOrder(order)
                isVerifying = false
                onPaymentSuccess(order)
            }
        } else {
            errorMessage = "Payment verification failed. UPI app status: ${if (status.isNotBlank()) status else "FAILED"}. If money was debited, enter UTR below."
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Social India Authoritative Payment",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "Real NPCI 100% Secure Anti-Fraud Gateway",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Product Summary Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = product.mediaUrl.ifEmpty { product.thumbnailUrl },
                        contentDescription = null,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = product.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Order: #$orderId",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "₹${product.price.toInt()}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF00E676)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dual Account Switcher (Primary / Secondary)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedAccountIndex == 0) Color(0xFF00E676).copy(alpha = 0.2f) else Color.Transparent,
                        border = if (selectedAccountIndex == 0) BorderStroke(1.dp, Color(0xFF00E676)) else null,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                SoundManager.playClickSound()
                                selectedAccountIndex = 0
                                UpiPaymentManager.switchAccount(0)
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Account 1 (Primary)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selectedAccountIndex == 0) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurface)
                            Text(UpiPaymentManager.getPrimaryUpiId(), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedAccountIndex == 1) Color(0xFF00E676).copy(alpha = 0.2f) else Color.Transparent,
                        border = if (selectedAccountIndex == 1) BorderStroke(1.dp, Color(0xFF00E676)) else null,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                SoundManager.playClickSound()
                                selectedAccountIndex = 1
                                UpiPaymentManager.switchAccount(1)
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Account 2 (Secondary)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selectedAccountIndex == 1) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurface)
                            Text(UpiPaymentManager.getSecondaryUpiId(), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Payment Mode Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        SoundManager.playClickSound()
                        selectedTab = 0
                        errorMessage = null
                    },
                    text = { Text("📱 UPI Apps (GPay/PhonePe)", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        SoundManager.playClickSound()
                        selectedTab = 1
                        errorMessage = null
                    },
                    text = { Text("📷 QR Code & UTR", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Error Banner if payment failed or was rejected
            errorMessage?.let { err ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (selectedTab == 0) {
                // Tab 0: Direct UPI Intent
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Pay instantly using your installed UPI apps with zero manual entry.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            errorMessage = null
                            val note = "Store Order $orderId"
                            val cleanUpiUri = UpiPaymentManager.buildUpiUri(
                                upiId = activeUpiId,
                                payeeName = "Social India Store",
                                amount = String.format(Locale.US, "%.2f", product.price),
                                note = note,
                                txnRefId = orderId
                            )
                            val upiIntent = Intent(Intent.ACTION_VIEW).apply {
                                data = cleanUpiUri
                            }
                            try {
                                val chooser = Intent.createChooser(upiIntent, "Select UPI App to Pay ₹${product.price.toInt()}")
                                upiLauncher.launch(chooser)
                            } catch (e: Exception) {
                                errorMessage = "No UPI app found on this device. Please switch to the QR Code & UTR tab."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black)
                        } else {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pay ₹${product.price.toInt()} with UPI App",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }
            } else {
                // Tab 1: QR Code & 12-Digit Banking UTR
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val cleanUpiUri = remember(activeUpiId, product.price, orderId) {
                        UpiPaymentManager.buildUpiUri(
                            upiId = activeUpiId,
                            payeeName = "Social India Store",
                            amount = String.format(Locale.US, "%.2f", product.price),
                            note = "Store Order $orderId",
                            txnRefId = orderId
                        ).toString()
                    }

                    val qrBitmap = remember(cleanUpiUri) {
                        QrCodeGenerator.generateQrBitmap(cleanUpiUri, 512)
                    }

                    if (qrBitmap != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = BorderStroke(2.dp, Color(0xFF00E676)),
                            modifier = Modifier.size(190.dp)
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Scan UPI QR Code",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Download QR & Copy UPI ID buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (qrBitmap != null) {
                                    val saved = QrCodeGenerator.saveQrBitmapToGallery(
                                        context,
                                        qrBitmap,
                                        "SocialIndia_Store_${product.price.toInt()}"
                                    )
                                    if (saved) {
                                        Toast.makeText(context, "QR Code saved to Gallery (Pictures/SocialIndia)!", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Could not save QR to gallery", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Download QR", modifier = Modifier.size(16.dp), tint = Color(0xFF00E676))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download QR", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                        }

                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("UPI ID", activeUpiId))
                                Toast.makeText(context, "UPI ID copied: $activeUpiId", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy UPI ID", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy UPI ID", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = utrInput,
                        onValueChange = { input ->
                            if (input.length <= 12 && input.all { it.isDigit() }) {
                                utrInput = input
                                errorMessage = null
                            }
                        },
                        label = { Text("Enter 12-Digit Banking UTR Number", fontSize = 12.sp) },
                        placeholder = { Text("e.g. 423589123456", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = {
                            Text("${utrInput.length}/12 Digits", fontSize = 11.sp, color = if (utrInput.length == 12) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingIcon = {
                            if (utrInput.length == 12) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00E676))
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            errorMessage = null
                            val (isValid, msg) = StoreRepository.validateUtrFormat(utrInput)
                            if (!isValid) {
                                errorMessage = msg
                                return@Button
                            }

                            isVerifying = true
                            scope.launch {
                                val isDup = StoreRepository.isUtrDuplicate(utrInput)
                                if (isDup) {
                                    isVerifying = false
                                    errorMessage = "🚨 Duplicate Alert: This 12-digit UTR ($utrInput) has already been submitted! Duplicate submissions are rejected."
                                    return@launch
                                }

                                // Register order & UTR in centralized Admin Verification Queue
                                val paymentOrderRes = com.example.payments.SocialIndiaPaymentOrchestrator.createPaymentOrder(
                                    userId = currentUserId,
                                    productType = com.example.model.PaymentProductType.STORE_PURCHASE,
                                    productId = product.id,
                                    contentId = orderId,
                                    amountRupees = product.price,
                                    targetTitle = "Store: ${product.title}"
                                )
                                paymentOrderRes.getOrNull()?.let { payOrd ->
                                    com.example.payments.SocialIndiaPaymentOrchestrator.submitUtrForVerification(
                                        order = payOrd,
                                        utrNumber = utrInput,
                                        upiIdUsed = activeUpiId,
                                        customNote = "Store Order: ${product.title}"
                                    )
                                }

                                val order = StoreOrder(
                                    orderId = orderId,
                                    productId = product.id,
                                    productTitle = product.title,
                                    productPrice = product.price,
                                    buyerId = currentUserId,
                                    txnId = utrInput,
                                    utrNumber = utrInput,
                                    paymentMode = "UPI_QR_UTR",
                                    status = "PENDING_VERIFICATION",
                                    createdAt = System.currentTimeMillis()
                                )
                                val saveResult = StoreRepository.recordStoreOrder(order)
                                isVerifying = false

                                if (saveResult.isSuccess) {
                                    Toast.makeText(context, "12-Digit UTR submitted to Admin Queue for verification!", Toast.LENGTH_LONG).show()
                                    onPaymentSuccess(order)
                                } else {
                                    errorMessage = "Failed to record order: ${saveResult.exceptionOrNull()?.message}"
                                }
                            }
                        },
                        enabled = utrInput.length == 12 && !isVerifying,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black)
                        } else {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Submit 12-Digit UTR & Place Order (₹${product.price.toInt()})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }
            }
        }
    }
}

