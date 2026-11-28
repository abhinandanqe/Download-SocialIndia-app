package com.example.ui.components

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PaymentOrder
import com.example.model.PaymentProductType
import com.example.model.RepositoryManager
import com.example.payments.SocialIndiaPaymentOrchestrator
import com.example.utils.QrCodeGenerator
import com.example.utils.UpiPaymentManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealUpiPaymentBottomSheet(
    title: String,
    subtitle: String,
    amountRupees: Double,
    productType: PaymentProductType,
    productId: String = "",
    creatorId: String = "",
    contentId: String = "",
    onDismiss: () -> Unit,
    onPaymentVerified: (PaymentOrder) -> Unit,
    onUtrSubmitted: (PaymentOrder, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser = FirebaseAuth.getInstance().currentUser
    val userId = currentUser?.uid ?: ""
    val userName = currentUser?.displayName ?: ""

    val primaryUpi by UpiPaymentManager.primaryUpiIdFlow.collectAsState()
    val secondaryUpi by UpiPaymentManager.secondaryUpiIdFlow.collectAsState()
    val payeeName by UpiPaymentManager.payeeNameFlow.collectAsState()

    var useSecondaryUpi by remember { mutableStateOf(false) }
    val activeUpiId = if (useSecondaryUpi && secondaryUpi.isNotBlank()) secondaryUpi else primaryUpi

    var activeOrder by remember { mutableStateOf<PaymentOrder?>(null) }
    var isCreatingOrder by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = UPI App Intent, 1 = Dynamic QR + 12-Digit UTR
    var utrInput by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isErrorStatus by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var screenshotUri by remember { mutableStateOf<Uri?>(null) }
    var isUploadingScreenshot by remember { mutableStateOf(false) }

    // Create backend payment order on open
    LaunchedEffect(userId, amountRupees, productType, productId, creatorId, contentId) {
        if (userId.isBlank()) {
            isCreatingOrder = false
            isErrorStatus = true
            statusMessage = "Please sign in to initiate a UPI payment."
            return@LaunchedEffect
        }
        isCreatingOrder = true
        val res = SocialIndiaPaymentOrchestrator.createPaymentOrder(
            userId = userId,
            productType = productType,
            productId = productId,
            creatorId = creatorId,
            contentId = contentId,
            amountRupees = amountRupees,
            userName = userName,
            targetTitle = title
        )
        isCreatingOrder = false
        res.onSuccess { order ->
            activeOrder = order
        }.onFailure { err ->
            isErrorStatus = true
            statusMessage = err.localizedMessage ?: "Could not create payment order."
        }
    }

    // Generate Dynamic ZXing QR Code whenever activeOrder or activeUpiId changes
    val upiDeepLinkUri = remember(activeOrder, activeUpiId, payeeName, amountRupees, title) {
        val ordId = activeOrder?.orderId ?: "ORD_${System.currentTimeMillis()}"
        UpiPaymentManager.buildUpiUri(
            upiId = activeUpiId,
            payeeName = payeeName,
            amount = String.format("%.2f", amountRupees),
            note = "$title ($ordId)",
            txnRefId = ordId
        )
    }

    LaunchedEffect(upiDeepLinkUri) {
        withContext(Dispatchers.Default) {
            val bmp = QrCodeGenerator.generateQrBitmap(upiDeepLinkUri.toString(), 640)
            withContext(Dispatchers.Main) {
                qrBitmap = bmp
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            screenshotUri = uri
        }
    }

    val upiIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val order = activeOrder ?: return@rememberLauncherForActivityResult
        val data = result.data
        val rawResp = data?.getStringExtra("response")
            ?: data?.dataString
            ?: if (result.resultCode == Activity.RESULT_OK) "Status=SUBMITTED" else "Status=CANCELLED"

        val parsed = UpiPaymentManager.parseUpiResponse(rawResp)
        when (parsed.status) {
            "SUCCESS" -> {
                scope.launch {
                    isProcessing = true
                    statusMessage = "Verifying UPI payment with bank..."
                    isErrorStatus = false
                    val verifyRes = SocialIndiaPaymentOrchestrator.verifyAndFulfillOrder(order.orderId, rawResp)
                    isProcessing = false
                    verifyRes.onSuccess { verifiedOrder ->
                        Toast.makeText(context, "Payment Verified! ₹${amountRupees.toInt()} fulfilled.", Toast.LENGTH_LONG).show()
                        onPaymentVerified(verifiedOrder)
                    }.onFailure { err ->
                        isErrorStatus = true
                        statusMessage = err.localizedMessage ?: "Verification failed. Please submit 12-digit UTR below."
                        selectedTab = 1
                    }
                }
            }
            "SUBMITTED" -> {
                isErrorStatus = false
                statusMessage = "Bank returned SUBMITTED status. Please enter your 12-digit UPI UTR / Ref No. below for Admin verification."
                selectedTab = 1
            }
            else -> {
                isErrorStatus = true
                statusMessage = "UPI payment was cancelled or failed in the app. You can retry or scan the QR & enter 12-digit UTR."
            }
        }
    }

    fun launchUpiApp(targetPkg: String? = null, appLabel: String = "UPI App") {
        val order = activeOrder
        if (order == null) {
            Toast.makeText(context, "Initializing order, please wait...", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = UpiPaymentManager.buildUpiIntent(upiDeepLinkUri, targetPkg)
            if (intent.resolveActivity(context.packageManager) != null) {
                statusMessage = null
                upiIntentLauncher.launch(intent)
            } else if (targetPkg != null) {
                // Fallback to generic chooser if specific app isn't installed
                val genericIntent = UpiPaymentManager.buildUpiIntent(upiDeepLinkUri, null)
                if (genericIntent.resolveActivity(context.packageManager) != null) {
                    Toast.makeText(context, "$appLabel not found, opening installed UPI apps...", Toast.LENGTH_SHORT).show()
                    upiIntentLauncher.launch(IntentChooserHelper.create(genericIntent, "Pay ₹${amountRupees.toInt()} via UPI"))
                } else {
                    selectedTab = 1
                    isErrorStatus = false
                    statusMessage = "No UPI app installed on this device. Scan the Dynamic QR Code from any phone and enter the 12-digit UTR."
                }
            } else {
                selectedTab = 1
                isErrorStatus = false
                statusMessage = "No UPI app detected on this device. Scan the Dynamic QR Code below and enter your 12-digit UTR."
            }
        } catch (e: Exception) {
            selectedTab = 1
            isErrorStatus = false
            statusMessage = "Opening QR + 12-Digit UTR mode (${e.localizedMessage ?: "UPI intent unavailable"})."
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        containerColor = Color(0xFF10131C),
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.35f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Badge
            Surface(
                color = Color(0xFF00E676).copy(alpha = 0.14f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = "NPCI UPI Verified",
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "INDIAN UPI GATEWAY • NPCI 2.0",
                        color = Color(0xFF00E676),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(14.dp))

            // Amount Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181D2B)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Payable Amount (INR)",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "₹${String.format("%.2f", amountRupees)}",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF00E676)
                        )
                        activeOrder?.let { ord ->
                            Text(
                                text = "Order ID: ${ord.orderId}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White.copy(alpha = 0.45f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Merchant UPI",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                        Text(
                            text = activeUpiId,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF64B5F6)
                        )
                        if (secondaryUpi.isNotBlank() && secondaryUpi != primaryUpi) {
                            TextButton(
                                onClick = { useSecondaryUpi = !useSecondaryUpi },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text(
                                    text = if (useSecondaryUpi) "Use Primary UPI" else "Switch Backup UPI",
                                    fontSize = 10.sp,
                                    color = Color(0xFFFFD54F)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Mode Selector Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF181D2B))
                    .padding(4.dp)
            ) {
                TabPillButton(
                    text = "1-Tap UPI Apps",
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.weight(1f)
                )
                TabPillButton(
                    text = "Scan QR / 12-Digit UTR",
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(14.dp))

            // Status Banner if present
            AnimatedVisibility(visible = statusMessage != null) {
                statusMessage?.let { msg ->
                    Surface(
                        color = if (isErrorStatus) Color(0xFFFF5252).copy(alpha = 0.14f) else Color(0xFF29B6F6).copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isErrorStatus) Color(0xFFFF5252).copy(alpha = 0.4f) else Color(0xFF29B6F6).copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isErrorStatus) Icons.Default.ErrorOutline else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (isErrorStatus) Color(0xFFFF5252) else Color(0xFF29B6F6),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = msg,
                                fontSize = 12.sp,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (isCreatingOrder) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF00E676))
                        Spacer(Modifier.height(10.dp))
                        Text("Generating secure NPCI UPI order...", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    }
                }
            } else if (selectedTab == 0) {
                // TAB 0: NATIVE UPI DEEP-LINK APPS (GPay, PhonePe, Paytm, BHIM)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Select your installed UPI app for instant verification:",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        UpiAppLaunchCard(
                            appName = "Google Pay",
                            accentColor = Color(0xFF4285F4),
                            badgeText = "GPay",
                            modifier = Modifier.weight(1f),
                            onClick = { launchUpiApp(UpiPaymentManager.PKG_GPAY, "Google Pay") }
                        )
                        UpiAppLaunchCard(
                            appName = "PhonePe",
                            accentColor = Color(0xFF6739B7),
                            badgeText = "Pe",
                            modifier = Modifier.weight(1f),
                            onClick = { launchUpiApp(UpiPaymentManager.PKG_PHONEPE, "PhonePe") }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        UpiAppLaunchCard(
                            appName = "Paytm UPI",
                            accentColor = Color(0xFF00BAF2),
                            badgeText = "Paytm",
                            modifier = Modifier.weight(1f),
                            onClick = { launchUpiApp(UpiPaymentManager.PKG_PAYTM, "Paytm") }
                        )
                        UpiAppLaunchCard(
                            appName = "BHIM UPI",
                            accentColor = Color(0xFFFF6D00),
                            badgeText = "BHIM",
                            modifier = Modifier.weight(1f),
                            onClick = { launchUpiApp(UpiPaymentManager.PKG_BHIM, "BHIM") }
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    Button(
                        onClick = { launchUpiApp(null, "All UPI Apps") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color.Black)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Pay ₹${String.format("%.2f", amountRupees)} via Any UPI App",
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }

                    OutlinedButton(
                        onClick = { selectedTab = 1 },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Pay from Another Phone (QR + 12-Digit UTR)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                // TAB 1: DYNAMIC QR CODE + 12-DIGIT UTR VERIFICATION
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Scannable QR Container
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = payeeName.uppercase(),
                                color = Color(0xFF10131C),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = activeUpiId,
                                color = Color(0xFF455A64),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(Modifier.height(8.dp))

                            val activeQrBitmap = qrBitmap
                            if (activeQrBitmap != null) {
                                Image(
                                    bitmap = activeQrBitmap.asImageBitmap(),
                                    contentDescription = "Dynamic UPI QR Code for ₹$amountRupees",
                                    modifier = Modifier
                                        .size(200.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier.size(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Color(0xFF10131C))
                                }
                            }

                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Scan with GPay • PhonePe • Paytm • BHIM",
                                color = Color(0xFF263238),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Download QR & Copy UPI ID Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val bmp = qrBitmap
                                if (bmp != null) {
                                    val saved = QrCodeGenerator.saveQrBitmapToGallery(
                                        context,
                                        bmp,
                                        "SocialIndia_UPI_${amountRupees.toInt()}"
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
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Download QR", tint = Color(0xFF00E676), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Download QR", color = Color(0xFF00E676), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Merchant UPI ID", activeUpiId))
                                Toast.makeText(context, "Copied UPI ID: $activeUpiId", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF64B5F6).copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy UPI ID", tint = Color(0xFF64B5F6), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy UPI ID", color = Color(0xFF64B5F6), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // 12-Digit UTR Input Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF181D2B))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Enter 12-Digit UPI UTR / Ref No.",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Text(
                                text = "${utrInput.length}/12 digits",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (utrInput.length == 12) Color(0xFF00E676) else Color(0xFFFFD54F)
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = utrInput,
                            onValueChange = { input ->
                                val digitsOnly = input.filter { it.isDigit() }.take(12)
                                utrInput = digitsOnly
                            },
                            placeholder = { Text("e.g. 412389012345 (12 digits)", color = Color.White.copy(alpha = 0.35f)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            leadingIcon = {
                                Icon(
                                    Icons.Default.ReceiptLong,
                                    contentDescription = null,
                                    tint = if (utrInput.length == 12) Color(0xFF00E676) else Color.White.copy(alpha = 0.6f)
                                )
                            },
                            trailingIcon = {
                                if (utrInput.length == 12) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Valid 12-digit UTR", tint = Color(0xFF00E676))
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00E676),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(10.dp))

                        // Optional payment screenshot attachment
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (screenshotUri != null) "Screenshot Attached ✓" else "Optional: Attach Payment Screenshot",
                                fontSize = 12.sp,
                                color = if (screenshotUri != null) Color(0xFF00E676) else Color.White.copy(alpha = 0.65f)
                            )
                            TextButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = if (screenshotUri != null) Icons.Default.Check else Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = Color(0xFF64B5F6),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (screenshotUri != null) "Change" else "Attach",
                                    color = Color(0xFF64B5F6),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Button(
                            onClick = {
                                val ord = activeOrder
                                if (ord == null) {
                                    Toast.makeText(context, "Order not ready yet", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (!SocialIndiaPaymentOrchestrator.isValidUtr(utrInput)) {
                                    isErrorStatus = true
                                    statusMessage = "Please enter an exact 12-digit numeric UPI UTR / Reference number."
                                    return@Button
                                }
                                scope.launch {
                                    isProcessing = true
                                    isErrorStatus = false
                                    statusMessage = "Submitting 12-digit UTR to Admin Verification Queue..."

                                    var uploadedScreenshotUrl = ""
                                    val pickedUri = screenshotUri
                                    if (pickedUri != null) {
                                        isUploadingScreenshot = true
                                        uploadedScreenshotUrl = RepositoryManager.mediaRepository.uploadMedia(pickedUri) ?: ""
                                        isUploadingScreenshot = false
                                    }

                                    val submitRes = SocialIndiaPaymentOrchestrator.submitUtrForVerification(
                                        order = ord,
                                        utrNumber = utrInput,
                                        upiIdUsed = activeUpiId,
                                        screenshotUrl = uploadedScreenshotUrl,
                                        customNote = title
                                    )
                                    isProcessing = false
                                    submitRes.onSuccess { updatedOrd ->
                                        Toast.makeText(
                                            context,
                                            "UTR $utrInput submitted! Admin Panel notified for instant approval.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        onUtrSubmitted(updatedOrd, utrInput)
                                    }.onFailure { err ->
                                        isErrorStatus = true
                                        statusMessage = err.localizedMessage ?: "Failed to submit UTR."
                                    }
                                }
                            },
                            enabled = !isProcessing && !isUploadingScreenshot && utrInput.length == 12,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E676),
                                disabledContainerColor = Color(0xFF00E676).copy(alpha = 0.3f)
                            )
                        ) {
                            if (isProcessing || isUploadingScreenshot) {
                                CircularProgressIndicator(
                                    color = Color.Black,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Submitting UTR...", color = Color.Black, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = Color.Black)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Submit 12-Digit UTR for Verification",
                                    color = Color.Black,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private object IntentChooserHelper {
    fun create(target: android.content.Intent, title: String): android.content.Intent {
        return android.content.Intent.createChooser(target, title)
    }
}

@Composable
private fun TabPillButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) Brush.horizontalGradient(listOf(Color(0xFF00E676), Color(0xFF00B0FF)))
                else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (selected) Color.Black else Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun UpiAppLaunchCard(
    appName: String,
    accentColor: Color,
    badgeText: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = Color(0xFF181D2B),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.2f))
                    .border(1.dp, accentColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeText.take(2).uppercase(),
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = appName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Instant UPI",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp
                )
            }
        }
    }
}
