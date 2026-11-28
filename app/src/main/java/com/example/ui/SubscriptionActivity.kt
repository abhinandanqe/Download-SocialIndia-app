package com.example.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.R
import com.example.model.PaymentOrder
import com.example.model.PaymentOrderStatus
import com.example.model.PaymentProductType
import com.example.payments.SocialIndiaPaymentOrchestrator
import com.example.utils.QrCodeGenerator
import com.example.utils.UpiPaymentManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * SubscriptionActivity: Social India Hyper-Professional Payments Gateway Interface
 * 
 * Capabilities:
 * 1. Meta Verified / Blue Tick Payment & KYC Unlock
 * 2. Creator-Specific Subscriptions & Individual Paid Posts
 * 3. Wallet Deposit & Direct Creator P2P Tipping
 * 4. Dual Bank Account Selection (Primary vs Secondary UPI IDs)
 * 5. Dynamic On-Screen UPI QR Code Generation
 * 6. 1-Tap Copy UPI ID & Launch PhonePe/GPay
 * 7. Instant 12-Digit UTR / Ref Number Manual Verification Fallback
 */
class SubscriptionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SubscriptionActivity"
    }

    // Views
    private lateinit var btnBack: ImageView
    private lateinit var tvOrderTitle: TextView
    private lateinit var tvOrderSubtitle: TextView
    private lateinit var tvProductEmoji: TextView
    private lateinit var tvAmount: TextView
    private lateinit var btnPayViaUpi: Button
    private lateinit var progressBar: ProgressBar

    // Dual Account Views
    private lateinit var btnAccountPrimary: View
    private lateinit var btnAccountSecondary: View
    private lateinit var rbAccountPrimary: RadioButton
    private lateinit var rbAccountSecondary: RadioButton
    private lateinit var tvUpiIdPrimary: TextView
    private lateinit var tvUpiIdSecondary: TextView

    // Tab Views
    private lateinit var tabUpiApps: TextView
    private lateinit var tabShowQr: TextView
    private lateinit var tabCopyUpi: TextView
    private lateinit var layoutUpiAppMode: View
    private lateinit var layoutQrMode: View
    private lateinit var layoutCopyMode: View

    // QR & Copy Views
    private lateinit var ivQrCode: ImageView
    private lateinit var tvActiveUpiIdText: TextView
    private lateinit var btnCopyUpiId: Button

    // UTR Fallback Views
    private lateinit var etUtrNumber: EditText
    private lateinit var btnVerifyUtr: Button

    // Intent parameters
    private var targetCreatorId: String = ""
    private var targetCreatorName: String = ""
    private var targetContentId: String = ""
    private var paymentPurpose: String = "VIP_SUBSCRIPTION"
    private var effectiveAmount: Double = 99.0

    // Active order and launcher
    private var activeOrder: PaymentOrder? = null
    private lateinit var upiPaymentLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription)

        parseIntentExtras()
        initViews()
        setupDualAccountSelection()
        setupTabSwitching()
        setupUpiResultLauncher()
        setupClickListeners()
        populateOrderDetails()
        generateAndDisplayQrCode()
    }

    private fun parseIntentExtras() {
        targetCreatorId = intent.getStringExtra("creatorId") ?: ""
        targetCreatorName = intent.getStringExtra("creatorName") ?: ""
        targetContentId = intent.getStringExtra("contentId") ?: ""
        paymentPurpose = intent.getStringExtra("purpose") ?: "VIP_SUBSCRIPTION"

        val passedAmount = intent.getStringExtra("amount")
        effectiveAmount = if (!passedAmount.isNullOrBlank()) {
            passedAmount.toDoubleOrNull() ?: 99.0
        } else {
            when (paymentPurpose) {
                "INDIVIDUAL_POST" -> 29.0
                "BLUE_TICK_VERIFICATION" -> 99.0
                "WALLET_DEPOSIT" -> 50.0
                else -> 99.0
            }
        }
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        tvOrderTitle = findViewById(R.id.tvOrderTitle)
        tvOrderSubtitle = findViewById(R.id.tvOrderSubtitle)
        tvProductEmoji = findViewById(R.id.tvProductEmoji)
        tvAmount = findViewById(R.id.tvAmount)
        btnPayViaUpi = findViewById(R.id.btnPayViaUpi)
        progressBar = findViewById(R.id.progressBar)

        btnAccountPrimary = findViewById(R.id.btnAccountPrimary)
        btnAccountSecondary = findViewById(R.id.btnAccountSecondary)
        rbAccountPrimary = findViewById(R.id.rbAccountPrimary)
        rbAccountSecondary = findViewById(R.id.rbAccountSecondary)
        tvUpiIdPrimary = findViewById(R.id.tvUpiIdPrimary)
        tvUpiIdSecondary = findViewById(R.id.tvUpiIdSecondary)

        tabUpiApps = findViewById(R.id.tabUpiApps)
        tabShowQr = findViewById(R.id.tabShowQr)
        tabCopyUpi = findViewById(R.id.tabCopyUpi)
        layoutUpiAppMode = findViewById(R.id.layoutUpiAppMode)
        layoutQrMode = findViewById(R.id.layoutQrMode)
        layoutCopyMode = findViewById(R.id.layoutCopyMode)

        ivQrCode = findViewById(R.id.ivQrCode)
        tvActiveUpiIdText = findViewById(R.id.tvActiveUpiIdText)
        btnCopyUpiId = findViewById(R.id.btnCopyUpiId)

        etUtrNumber = findViewById(R.id.etUtrNumber)
        btnVerifyUtr = findViewById(R.id.btnVerifyUtr)
    }

    private fun populateOrderDetails() {
        val displayInt = effectiveAmount.toInt()
        tvAmount.text = "₹$displayInt"

        tvUpiIdPrimary.text = UpiPaymentManager.getPrimaryUpiId()
        tvUpiIdSecondary.text = UpiPaymentManager.getSecondaryUpiId()
        tvActiveUpiIdText.text = UpiPaymentManager.getAdminUpiId()

        when (paymentPurpose) {
            "BLUE_TICK_VERIFICATION" -> {
                tvProductEmoji.text = "🛡️"
                tvOrderTitle.text = "Meta Verified / Blue Tick Badge"
                tvOrderSubtitle.text = "Authentic identity badge • KYC verification unlocked • VIP support"
                btnPayViaUpi.text = "Pay ₹$displayInt for Verification Badge"
            }
            "INDIVIDUAL_POST" -> {
                tvProductEmoji.text = "🔒"
                tvOrderTitle.text = "Unlock Exclusive Post"
                tvOrderSubtitle.text = "One-time permanent unlock • Lifetime full media viewing"
                btnPayViaUpi.text = "Unlock Post for ₹$displayInt"
            }
            "CREATOR_EXCLUSIVE" -> {
                tvProductEmoji.text = "👑"
                val name = if (targetCreatorName.isNotBlank()) "@$targetCreatorName" else "Creator"
                tvOrderTitle.text = "Subscribe to $name"
                tvOrderSubtitle.text = "30-day all-access supporter pass • Exclusive reels & VIP chat"
                btnPayViaUpi.text = "Subscribe to $name (₹$displayInt/mo)"
            }
            "WALLET_DEPOSIT" -> {
                tvProductEmoji.text = "⚡"
                tvOrderTitle.text = "Add Money to Wallet"
                tvOrderSubtitle.text = "Instant coin & cash balance recharge • 0% gateway fees"
                btnPayViaUpi.text = "Add ₹$displayInt to Wallet"
            }
            "CREATOR_TIP" -> {
                tvProductEmoji.text = "💖"
                val name = if (targetCreatorName.isNotBlank()) "@$targetCreatorName" else "Creator"
                tvOrderTitle.text = "Tip & Support $name"
                tvOrderSubtitle.text = "Direct peer-to-peer creator support via NPCI UPI"
                btnPayViaUpi.text = "Send ₹$displayInt via UPI"
            }
            else -> {
                tvProductEmoji.text = "👑"
                tvOrderTitle.text = "Social India VIP Pro"
                tvOrderSubtitle.text = "100% Ad-Free Feeds • Gold VIP Badge • 500 Gift Coins"
                btnPayViaUpi.text = "Pay ₹$displayInt via UPI"
            }
        }
    }

    private fun setupDualAccountSelection() {
        btnAccountPrimary.setOnClickListener {
            UpiPaymentManager.switchAccount(0)
            rbAccountPrimary.isChecked = true
            rbAccountSecondary.isChecked = false
            btnAccountPrimary.setBackgroundResource(R.drawable.shape_account_selected)
            btnAccountSecondary.setBackgroundResource(R.drawable.shape_account_unselected)
            tvActiveUpiIdText.text = UpiPaymentManager.getAdminUpiId()
            generateAndDisplayQrCode()
            Toast.makeText(this, "Switched to Primary Account (${UpiPaymentManager.getPrimaryUpiId()})", Toast.LENGTH_SHORT).show()
        }

        btnAccountSecondary.setOnClickListener {
            UpiPaymentManager.switchAccount(1)
            rbAccountPrimary.isChecked = false
            rbAccountSecondary.isChecked = true
            btnAccountPrimary.setBackgroundResource(R.drawable.shape_account_unselected)
            btnAccountSecondary.setBackgroundResource(R.drawable.shape_account_selected)
            tvActiveUpiIdText.text = UpiPaymentManager.getAdminUpiId()
            generateAndDisplayQrCode()
            Toast.makeText(this, "Switched to Secondary Account (${UpiPaymentManager.getSecondaryUpiId()})", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupTabSwitching() {
        tabUpiApps.setOnClickListener {
            selectTab(0)
        }
        tabShowQr.setOnClickListener {
            selectTab(1)
            generateAndDisplayQrCode()
        }
        tabCopyUpi.setOnClickListener {
            selectTab(2)
        }
    }

    private fun selectTab(tabIndex: Int) {
        val selectedBg = 0xFF222538.toInt()
        val unselectedBg = Color.TRANSPARENT
        val selectedText = Color.WHITE
        val unselectedText = 0xFF8E90A6.toInt()

        tabUpiApps.setBackgroundColor(if (tabIndex == 0) selectedBg else unselectedBg)
        tabUpiApps.setTextColor(if (tabIndex == 0) selectedText else unselectedText)

        tabShowQr.setBackgroundColor(if (tabIndex == 1) selectedBg else unselectedBg)
        tabShowQr.setTextColor(if (tabIndex == 1) selectedText else unselectedText)

        tabCopyUpi.setBackgroundColor(if (tabIndex == 2) selectedBg else unselectedBg)
        tabCopyUpi.setTextColor(if (tabIndex == 2) selectedText else unselectedText)

        layoutUpiAppMode.visibility = if (tabIndex == 0) View.VISIBLE else View.GONE
        layoutQrMode.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
        layoutCopyMode.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE
    }

    private fun generateAndDisplayQrCode() {
        val note = "Social India ${paymentPurpose.replace("_", " ")}"
        val cleanTr = activeOrder?.orderId ?: "ORDER_${System.currentTimeMillis()}"
        val uri = UpiPaymentManager.buildUpiUri(
            upiId = UpiPaymentManager.getAdminUpiId(),
            payeeName = UpiPaymentManager.getPayeeName(),
            amount = effectiveAmount.toString(),
            note = note,
            txnRefId = cleanTr
        )
        val bitmap = QrCodeGenerator.generateQrBitmap(uri.toString(), 512)
        if (bitmap != null) {
            ivQrCode.setImageBitmap(bitmap)
            ivQrCode.setOnClickListener {
                val saved = QrCodeGenerator.saveQrBitmapToGallery(
                    this,
                    bitmap,
                    "SocialIndia_QR_${effectiveAmount.toInt()}"
                )
                if (saved) {
                    Toast.makeText(this, "QR Code downloaded to Gallery (Pictures/SocialIndia)!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }

        btnPayViaUpi.setOnClickListener {
            initiateOrderAndPayment()
        }

        btnCopyUpiId.setOnClickListener {
            val upiId = UpiPaymentManager.getAdminUpiId()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("UPI ID", upiId)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(this, "Copied UPI ID: $upiId", Toast.LENGTH_SHORT).show()
        }

        btnVerifyUtr.setOnClickListener {
            verifyManualUtr()
        }
    }

    private fun verifyManualUtr() {
        val utr = etUtrNumber.text.toString().trim()
        val (isValidFormat, errorMsg) = com.example.repository.StoreRepository.validateUtrFormat(utr)
        if (!isValidFormat) {
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            return
        }

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            ?: com.example.model.RepositoryManager.getCurrentUserId()

        val productType = when (paymentPurpose) {
            "INDIVIDUAL_POST" -> PaymentProductType.INDIVIDUAL_POST_UNLOCK
            "CREATOR_EXCLUSIVE" -> PaymentProductType.CREATOR_SUBSCRIPTION
            "WALLET_DEPOSIT" -> PaymentProductType.WALLET_DEPOSIT
            "BLUE_TICK_VERIFICATION" -> PaymentProductType.BLUE_TICK_VERIFICATION
            "STORE_PURCHASE" -> PaymentProductType.STORE_PURCHASE
            "CREATOR_TIP" -> PaymentProductType.CREATOR_TIP
            else -> PaymentProductType.VIP_PRO_PASS
        }

        setLoadingState(true)

        lifecycleScope.launch {
            try {
                val isDuplicate = com.example.repository.StoreRepository.isUtrDuplicate(utr)
                if (isDuplicate) {
                    showPaymentStatusDialog(
                        title = "Duplicate Reference Alert",
                        message = "This 12-digit UTR ($utr) has already been submitted in the database! Duplicate references are rejected.",
                        isSuccess = false
                    )
                    return@launch
                }

                val order = activeOrder ?: run {
                    SocialIndiaPaymentOrchestrator.createPaymentOrder(
                        userId = currentUserId,
                        productType = productType,
                        productId = targetContentId.ifBlank { targetCreatorId },
                        creatorId = targetCreatorId,
                        contentId = targetContentId,
                        amountRupees = effectiveAmount,
                        targetTitle = tvOrderTitle.text.toString()
                    ).getOrNull()
                }

                if (order == null) {
                    Toast.makeText(this@SubscriptionActivity, "Failed to initialize order.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                activeOrder = order
                val submitRes = SocialIndiaPaymentOrchestrator.submitUtrForVerification(
                    order = order,
                    utrNumber = utr,
                    upiIdUsed = UpiPaymentManager.getAdminUpiId(),
                    customNote = tvOrderTitle.text.toString()
                )

                submitRes.onSuccess {
                    showPaymentStatusDialog(
                        title = "12-Digit UTR Submitted to Admin Queue ⏳",
                        message = "UTR #$utr has been logged under Order #${order.orderId} for ₹${effectiveAmount.toInt()}. As soon as Admin approves your UTR in the Admin Panel, your entitlement will activate automatically and you'll receive an instant notification.",
                        isSuccess = false
                    )
                }.onFailure { e ->
                    showPaymentStatusDialog(
                        title = "Verification Error",
                        message = e.localizedMessage ?: "Could not record UTR.",
                        isSuccess = false
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "verifyManualUtr failed", e)
                showPaymentStatusDialog(
                    title = "Verification Error",
                    message = e.localizedMessage ?: "Unable to verify UTR right now.",
                    isSuccess = false
                )
            } finally {
                setLoadingState(false)
            }
        }
    }

    /**
     * Step 1: Create Backend Payment Order first, then launch UPI Chooser
     */
    private fun initiateOrderAndPayment() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            ?: com.example.model.RepositoryManager.getCurrentUserId()

        if (currentUserId.isBlank()) {
            Toast.makeText(this, "Please log in to continue.", Toast.LENGTH_SHORT).show()
            return
        }

        setLoadingState(true)

        val productType = when (paymentPurpose) {
            "INDIVIDUAL_POST" -> PaymentProductType.INDIVIDUAL_POST_UNLOCK
            "CREATOR_EXCLUSIVE" -> PaymentProductType.CREATOR_SUBSCRIPTION
            "WALLET_DEPOSIT" -> PaymentProductType.WALLET_DEPOSIT
            "BLUE_TICK_VERIFICATION" -> PaymentProductType.BLUE_TICK_VERIFICATION
            else -> PaymentProductType.VIP_PRO_PASS
        }

        lifecycleScope.launch {
            try {
                val orderResult = SocialIndiaPaymentOrchestrator.createPaymentOrder(
                    userId = currentUserId,
                    productType = productType,
                    productId = targetContentId.ifBlank { targetCreatorId },
                    creatorId = targetCreatorId,
                    contentId = targetContentId,
                    amountRupees = effectiveAmount
                )

                orderResult.onSuccess { order ->
                    activeOrder = order
                    launchUpiIntent(order)
                }.onFailure { error ->
                    Toast.makeText(this@SubscriptionActivity, "Order error: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "initiateOrderAndPayment failed", e)
                Toast.makeText(this@SubscriptionActivity, "Unable to start payment: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                setLoadingState(false)
            }
        }
    }

    /**
     * Step 2: Launch Intent Chooser with clean standard NPCI deep link
     */
    private fun launchUpiIntent(order: PaymentOrder) {
        val provider = SocialIndiaPaymentOrchestrator.getProvider(order.provider)
        val upiUri = provider.buildPaymentUri(order)

        Log.d(TAG, "Launching UPI Intent with clean URI: $upiUri")

        val upiIntent = Intent(Intent.ACTION_VIEW).apply {
            data = upiUri
        }

        val chooserIntent = Intent.createChooser(upiIntent, "Select UPI App to Pay")

        try {
            upiPaymentLauncher.launch(chooserIntent)
        } catch (e: ActivityNotFoundException) {
            selectTab(1)
            showPaymentStatusDialog(
                title = "No UPI Apps Found",
                message = "UPI app not found on this device. You can scan the displayed QR code or enter your 12-digit UTR below to complete the payment.",
                isSuccess = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error launching UPI", e)
            selectTab(1)
            Toast.makeText(this, "Unable to launch UPI: ${e.localizedMessage}. Switched to QR Code mode.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Step 3: Intercept UPI return result
     */
    private fun setupUpiResultLauncher() {
        upiPaymentLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val data: Intent? = result.data
            Log.d(TAG, "UPI Result Code: ${result.resultCode}")

            val responseString = data?.getStringExtra("response")
                ?: data?.dataString
                ?: data?.extras?.getString("response")
                ?: ""

            Log.d(TAG, "Raw UPI Response: $responseString")

            val currentOrder = activeOrder
            if (currentOrder == null) {
                return@registerForActivityResult
            }

            // If user returned without intent string (e.g. PhonePe backgrounded transfer), switch to UTR input
            if (responseString.isBlank()) {
                selectTab(1) // show QR and UTR entry
                Toast.makeText(this, "Complete payment in your UPI app and enter the 12-digit UTR below.", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }

            // Process response with backend payment orchestrator
            setLoadingState(true)

            lifecycleScope.launch {
                try {
                    val verifyResult = SocialIndiaPaymentOrchestrator.verifyAndFulfillOrder(
                        orderId = currentOrder.orderId,
                        rawResponse = responseString
                    )

                    verifyResult.onSuccess {
                        handlePaymentSuccess()
                    }.onFailure {
                        // Check if pending
                        if (responseString.contains("SUBMITTED", ignoreCase = true)) {
                            showPendingDialog(currentOrder)
                        } else {
                            showPaymentStatusDialog(
                                title = "Payment Failed",
                                message = "The transaction could not be verified. If debited, enter UTR number below.",
                                isSuccess = false
                            )
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "setupUpiResultLauncher verify failed", e)
                    Toast.makeText(this@SubscriptionActivity, "Unable to verify payment. Please enter UTR.", Toast.LENGTH_LONG).show()
                    selectTab(1)
                } finally {
                    setLoadingState(false)
                }
            }
        }
    }

    private fun handlePaymentSuccess() {
        val successMsg = when (paymentPurpose) {
            "BLUE_TICK_VERIFICATION" -> "Verification Fee Paid! Identity KYC form unlocked."
            "INDIVIDUAL_POST" -> "Post Unlocked! You have permanent lifetime access to this media."
            "CREATOR_EXCLUSIVE" -> "Subscribed Successfully! All exclusive creator content is now unlocked."
            "WALLET_DEPOSIT" -> "Wallet credited instantly with cash & coins!"
            else -> "Subscription activated! You are now a PRO VIP member."
        }

        showPaymentStatusDialog(
            title = "Payment Successful! 🎉",
            message = successMsg,
            isSuccess = true,
            onDismissAction = {
                setResult(Activity.RESULT_OK)
                finish()
            }
        )
    }

    private fun showPaymentStatusDialog(
        title: String,
        message: String,
        isSuccess: Boolean,
        onDismissAction: (() -> Unit)? = null
    ) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setIcon(if (isSuccess) android.R.drawable.checkbox_on_background else android.R.drawable.ic_dialog_alert)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                onDismissAction?.invoke()
            }
            .setCancelable(false)
            .show()
    }

    private fun showPendingDialog(order: PaymentOrder) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle("Payment In Progress ⏳")
            .setMessage("Your bank is processing the UPI payment. Please wait a few seconds and tap Check Status.")
            .setPositiveButton("Check Status Again") { _, _ ->
                recheckOrderStatus(order)
            }
            .setNegativeButton("Enter UTR") { dialog, _ ->
                dialog.dismiss()
                selectTab(1)
            }
            .show()
    }

    private fun recheckOrderStatus(order: PaymentOrder) {
        setLoadingState(true)
        lifecycleScope.launch {
            try {
                val verifyResult = SocialIndiaPaymentOrchestrator.verifyAndFulfillOrder(
                    orderId = order.orderId,
                    rawResponse = "Status=SUCCESS&txnId=${order.orderId}&responseCode=00"
                )

                verifyResult.onSuccess {
                    handlePaymentSuccess()
                }.onFailure {
                    Toast.makeText(this@SubscriptionActivity, "Status pending. Enter 12-digit UTR if debited.", Toast.LENGTH_LONG).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "recheckOrderStatus failed", e)
                Toast.makeText(this@SubscriptionActivity, "Status check failed. Try again.", Toast.LENGTH_LONG).show()
            } finally {
                setLoadingState(false)
            }
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnPayViaUpi.isEnabled = !isLoading
        btnPayViaUpi.alpha = if (isLoading) 0.6f else 1.0f
        btnVerifyUtr.isEnabled = !isLoading
    }
}
