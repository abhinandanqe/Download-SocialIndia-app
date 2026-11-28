package com.example.utils

import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * UpiPaymentManager:
 * Dynamically fetches and caches the Admin's Primary and Secondary UPI IDs, Payee Name,
 * QR Code URL, and Gateway Keys from Firestore (`admin_settings/payment_settings`) so the
 * Admin can change receiving bank accounts and gateway credentials anytime from the Admin Panel.
 *
 * Also builds strictly formatted NPCI-compliant UPI URIs (`upi://pay`) for GPay, PhonePe,
 * Paytm, BHIM, and universal UPI apps.
 */
object UpiPaymentManager {

    private const val TAG = "UpiPaymentManager"

    // Default Fallback UPI IDs (Indian VPA handles)
    private const val DEFAULT_PRIMARY_UPI_ID = "7279072295@navi"
    private const val DEFAULT_SECONDARY_UPI_ID = "8603733382@upi"
    private const val DEFAULT_PAYEE_NAME = "Social India"

    // Known Indian UPI App Packages for 1-tap direct launch
    const val PKG_GPAY = "com.google.android.apps.nbu.paisa.user"
    const val PKG_PHONEPE = "com.phonepe.app"
    const val PKG_PAYTM = "net.one97.paytm"
    const val PKG_BHIM = "in.org.npci.upiapp"

    @Volatile
    private var primaryUpiId: String = DEFAULT_PRIMARY_UPI_ID

    @Volatile
    private var secondaryUpiId: String = DEFAULT_SECONDARY_UPI_ID

    @Volatile
    private var cachedPayeeName: String = DEFAULT_PAYEE_NAME

    @Volatile
    private var cachedQrUrl: String = ""

    @Volatile
    private var cachedGatewayProvider: String = "NPCI_DIRECT_UPI"

    @Volatile
    private var cachedMerchantKeyId: String = ""

    @Volatile
    private var cachedMerchantKeySecret: String = ""

    // 0 = Primary Account, 1 = Secondary Account
    @Volatile
    private var selectedAccountIndex: Int = 0

    @Volatile
    private var isListening: Boolean = false

    private var settingsListener: com.google.firebase.firestore.ListenerRegistration? = null

    private val _primaryUpiIdFlow = MutableStateFlow(DEFAULT_PRIMARY_UPI_ID)
    val primaryUpiIdFlow: StateFlow<String> = _primaryUpiIdFlow.asStateFlow()

    private val _secondaryUpiIdFlow = MutableStateFlow(DEFAULT_SECONDARY_UPI_ID)
    val secondaryUpiIdFlow: StateFlow<String> = _secondaryUpiIdFlow.asStateFlow()

    private val _payeeNameFlow = MutableStateFlow(DEFAULT_PAYEE_NAME)
    val payeeNameFlow: StateFlow<String> = _payeeNameFlow.asStateFlow()

    private val _qrUrlFlow = MutableStateFlow("")
    val qrUrlFlow: StateFlow<String> = _qrUrlFlow.asStateFlow()

    private val _gatewayProviderFlow = MutableStateFlow("NPCI_DIRECT_UPI")
    val gatewayProviderFlow: StateFlow<String> = _gatewayProviderFlow.asStateFlow()

    private val _merchantKeyIdFlow = MutableStateFlow("")
    val merchantKeyIdFlow: StateFlow<String> = _merchantKeyIdFlow.asStateFlow()

    private val _merchantKeySecretFlow = MutableStateFlow("")
    val merchantKeySecretFlow: StateFlow<String> = _merchantKeySecretFlow.asStateFlow()

    init {
        startListeningForAdminUpiConfig()
    }

    /**
     * Real-time listener on `admin_settings/payment_settings` so any change made by Admin
     * in the Admin Panel updates all users' apps immediately without app restart.
     * Note: merchant_key_secret is stored exclusively in admin-only `admin_secrets/payment_gateway`.
     */
    fun startListeningForAdminUpiConfig() {
        if (isListening && settingsListener != null) return
        try {
            isListening = true
            settingsListener?.remove()
            settingsListener = FirebaseFirestore.getInstance()
                .collection("admin_settings")
                .document("payment_settings")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Error listening to payment_settings: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val upi1 = (snapshot.get("primary_upi_id") as? String)?.trim()
                            ?: (snapshot.get("admin_upi_id") as? String)?.trim()
                        val upi2 = (snapshot.get("secondary_upi_id") as? String)?.trim()
                        val name = (snapshot.get("payee_name") as? String)?.trim()
                        val qrUrl = (snapshot.get("upi_qr_url") as? String)?.trim()
                        val provider = (snapshot.get("gateway_provider") as? String)?.trim()
                        val keyId = (snapshot.get("merchant_key_id") as? String)?.trim()

                        if (!upi1.isNullOrBlank() && upi1.contains("@")) {
                            primaryUpiId = upi1
                            _primaryUpiIdFlow.value = upi1
                        }
                        if (!upi2.isNullOrBlank() && upi2.contains("@")) {
                            secondaryUpiId = upi2
                            _secondaryUpiIdFlow.value = upi2
                        }
                        if (!name.isNullOrBlank()) {
                            cachedPayeeName = name
                            _payeeNameFlow.value = name
                        }
                        if (qrUrl != null) {
                            cachedQrUrl = qrUrl
                            _qrUrlFlow.value = qrUrl
                        }
                        if (!provider.isNullOrBlank()) {
                            cachedGatewayProvider = provider
                            _gatewayProviderFlow.value = provider
                        }
                        if (keyId != null) {
                            cachedMerchantKeyId = keyId
                            _merchantKeyIdFlow.value = keyId
                        }
                    }
                }

            // Load admin-only merchant secret only if current user has admin permission (ignored silently for regular users)
            FirebaseFirestore.getInstance()
                .collection("admin_secrets")
                .document("payment_gateway")
                .get()
                .addOnSuccessListener { secretDoc ->
                    val secret = (secretDoc.get("merchant_key_secret") as? String)?.trim()
                    if (secret != null) {
                        cachedMerchantKeySecret = secret
                        _merchantKeySecretFlow.value = secret
                    }
                }
        } catch (e: Exception) {
            isListening = false
            Log.e(TAG, "Failed to attach payment_settings listener", e)
        }
    }

    fun stopListening() {
        settingsListener?.remove()
        settingsListener = null
        isListening = false
    }

    /**
     * Switch active receiving account (0 = Primary, 1 = Secondary)
     */
    fun switchAccount(index: Int) {
        selectedAccountIndex = if (index == 1) 1 else 0
    }

    fun getSelectedAccountIndex(): Int = selectedAccountIndex

    /**
     * Returns the currently selected Admin UPI ID
     */
    fun getAdminUpiId(): String {
        startListeningForAdminUpiConfig()
        return if (selectedAccountIndex == 1 && secondaryUpiId.isNotBlank()) {
            secondaryUpiId
        } else {
            primaryUpiId
        }
    }

    fun getPrimaryUpiId(): String {
        startListeningForAdminUpiConfig()
        return primaryUpiId
    }

    fun getSecondaryUpiId(): String {
        startListeningForAdminUpiConfig()
        return secondaryUpiId
    }

    fun getPayeeName(): String {
        startListeningForAdminUpiConfig()
        return cachedPayeeName
    }

    fun getCustomQrUrl(): String {
        startListeningForAdminUpiConfig()
        return cachedQrUrl
    }

    fun getGatewayProvider(): String {
        startListeningForAdminUpiConfig()
        return cachedGatewayProvider
    }

    fun getMerchantKeyId(): String {
        startListeningForAdminUpiConfig()
        return cachedMerchantKeyId
    }

    fun getMerchantKeySecret(): String {
        startListeningForAdminUpiConfig()
        return cachedMerchantKeySecret
    }

    /**
     * Admin Panel helper to update Primary & Secondary UPI IDs, Payee Name, QR URL, and Gateway Keys in Firestore.
     * Public payment metadata goes to `admin_settings/payment_settings`; private merchant secret goes strictly to `admin_secrets/payment_gateway`.
     */
    fun updateAdminUpiConfig(
        primaryUpiId: String,
        secondaryUpiId: String = this.secondaryUpiId,
        payeeName: String = DEFAULT_PAYEE_NAME,
        qrUrl: String = this.cachedQrUrl,
        gatewayProvider: String = this.cachedGatewayProvider,
        merchantKeyId: String = this.cachedMerchantKeyId,
        merchantKeySecret: String = this.cachedMerchantKeySecret,
        onResult: (Boolean) -> Unit = {}
    ) {
        val cleanPrimary = primaryUpiId.trim()
        val cleanSecondary = secondaryUpiId.trim().ifEmpty { DEFAULT_SECONDARY_UPI_ID }
        val cleanName = payeeName.trim().ifEmpty { DEFAULT_PAYEE_NAME }
        if (cleanPrimary.isEmpty() || !cleanPrimary.contains("@")) {
            onResult(false)
            return
        }
        this.primaryUpiId = cleanPrimary
        this.secondaryUpiId = cleanSecondary
        this.cachedPayeeName = cleanName
        this.cachedQrUrl = qrUrl.trim()
        this.cachedGatewayProvider = gatewayProvider.trim().ifEmpty { "NPCI_DIRECT_UPI" }
        this.cachedMerchantKeyId = merchantKeyId.trim()
        this.cachedMerchantKeySecret = merchantKeySecret.trim()

        _primaryUpiIdFlow.value = cleanPrimary
        _secondaryUpiIdFlow.value = cleanSecondary
        _payeeNameFlow.value = cleanName
        _qrUrlFlow.value = this.cachedQrUrl
        _gatewayProviderFlow.value = this.cachedGatewayProvider
        _merchantKeyIdFlow.value = this.cachedMerchantKeyId
        _merchantKeySecretFlow.value = this.cachedMerchantKeySecret

        val publicData = hashMapOf<String, Any>(
            "admin_upi_id" to cleanPrimary,
            "primary_upi_id" to cleanPrimary,
            "secondary_upi_id" to cleanSecondary,
            "payee_name" to cleanName,
            "upi_qr_url" to this.cachedQrUrl,
            "gateway_provider" to this.cachedGatewayProvider,
            "merchant_key_id" to this.cachedMerchantKeyId,
            "merchant_key_secret" to com.google.firebase.firestore.FieldValue.delete(),
            "updated_at" to System.currentTimeMillis()
        )

        val db = FirebaseFirestore.getInstance()
        db.collection("admin_settings")
            .document("payment_settings")
            .set(publicData, SetOptions.merge())
            .addOnSuccessListener {
                if (this.cachedMerchantKeySecret.isNotEmpty()) {
                    db.collection("admin_secrets")
                        .document("payment_gateway")
                        .set(
                            mapOf(
                                "merchant_key_secret" to this.cachedMerchantKeySecret,
                                "updated_at" to System.currentTimeMillis()
                            ),
                            SetOptions.merge()
                        )
                        .addOnSuccessListener { onResult(true) }
                        .addOnFailureListener { onResult(false) }
                } else {
                    onResult(true)
                }
            }
            .addOnFailureListener { onResult(false) }
    }

    /**
     * Builds a strictly formatted, NPCI-compliant UPI Deep-Link URI (`upi://pay`).
     * Uses proper decimal formatting (e.g., "99.00") and alphanumeric transaction IDs.
     */
    fun buildUpiUri(
        upiId: String = getAdminUpiId(),
        payeeName: String = getPayeeName(),
        amount: String,
        note: String,
        txnRefId: String = "SI${System.currentTimeMillis()}"
    ): Uri {
        val numericAmount = amount.toDoubleOrNull() ?: 99.0
        val formattedAmount = String.format(Locale.US, "%.2f", numericAmount)
        val cleanTr = txnRefId.replace(Regex("[^A-Za-z0-9]"), "").take(32).ifEmpty {
            "SI${System.currentTimeMillis()}"
        }

        return Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", upiId.trim())
            .appendQueryParameter("pn", payeeName.trim())
            .appendQueryParameter("tn", note.trim().take(48))
            .appendQueryParameter("am", formattedAmount)
            .appendQueryParameter("cu", "INR")
            .appendQueryParameter("tr", cleanTr)
            .build()
    }

    /**
     * Builds an Intent targeting a specific UPI app (GPay, PhonePe, Paytm, BHIM) or general UPI chooser.
     */
    fun buildUpiIntent(upiUri: Uri, targetPackage: String? = null): Intent {
        return Intent(Intent.ACTION_VIEW, upiUri).apply {
            if (!targetPackage.isNullOrBlank()) {
                setPackage(targetPackage)
            }
        }
    }

    /**
     * Parses the raw `response` string returned by GPay / PhonePe / Paytm / BHIM.
     * Example response: "txnId=12345&responseCode=00&Status=SUCCESS&txnRef=SI12345"
     */
    fun isPaymentSuccessful(response: String?): Boolean {
        if (response.isNullOrBlank()) return false

        val params = response.split("&").associate { pair ->
            val parts = pair.split("=")
            if (parts.size >= 2) {
                parts[0].trim().lowercase(Locale.ROOT) to parts[1].trim().lowercase(Locale.ROOT)
            } else {
                pair.trim().lowercase(Locale.ROOT) to ""
            }
        }

        val status = params["status"] ?: ""
        val responseCode = params["responsecode"] ?: ""

        return status == "success" || status == "completed" || responseCode == "00" || responseCode == "0"
    }

    /**
     * Parses the raw UPI response string into a structured status + txnId + utr/approvalRefNo.
     */
    fun parseUpiResponse(response: String?): ParsedUpiResult {
        if (response.isNullOrBlank()) {
            return ParsedUpiResult(status = "CANCELLED", txnId = "", approvalRefNo = "", responseCode = "")
        }
        val params = response.split("&").associate { pair ->
            val parts = pair.split("=")
            if (parts.size >= 2) {
                parts[0].trim().lowercase(Locale.ROOT) to parts[1].trim()
            } else {
                pair.trim().lowercase(Locale.ROOT) to ""
            }
        }
        val rawStatus = params["status"] ?: ""
        val responseCode = params["responsecode"] ?: ""
        val txnId = params["txnid"] ?: params["txnref"] ?: ""
        val approvalRef = params["approvalrefno"] ?: params["utr"] ?: txnId

        val normalizedStatus = when {
            rawStatus.equals("SUCCESS", ignoreCase = true) ||
                rawStatus.equals("COMPLETED", ignoreCase = true) ||
                responseCode == "00" || responseCode == "0" -> "SUCCESS"
            rawStatus.equals("SUBMITTED", ignoreCase = true) ||
                rawStatus.equals("PENDING", ignoreCase = true) -> "SUBMITTED"
            else -> "FAILURE"
        }

        return ParsedUpiResult(
            status = normalizedStatus,
            txnId = txnId.ifEmpty { "TXN_${System.currentTimeMillis()}" },
            approvalRefNo = approvalRef,
            responseCode = responseCode
        )
    }

    data class ParsedUpiResult(
        val status: String, // "SUCCESS", "SUBMITTED", "FAILURE", "CANCELLED"
        val txnId: String,
        val approvalRefNo: String,
        val responseCode: String
    )
}
