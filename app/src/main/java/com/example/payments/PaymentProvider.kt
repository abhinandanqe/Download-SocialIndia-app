package com.example.payments

import android.net.Uri
import com.example.model.PaymentOrder
import com.example.utils.UpiPaymentManager
import java.util.UUID

/**
 * Parsed Payment Response data from Payment Provider
 */
data class PaymentResponseData(
    val status: String, // SUCCESS, FAILURE, SUBMITTED, CANCELLED
    val responseCode: String,
    val txnId: String,
    val approvalRefNo: String,
    val txnRef: String,
    val rawString: String
) {
    val isAuthoritativeSuccess: Boolean
        get() = status.equals("SUCCESS", ignoreCase = true) || responseCode == "00"
}

/**
 * Payment Provider Abstraction
 */
interface PaymentProvider {
    val providerId: String

    fun buildPaymentUri(order: PaymentOrder): Uri
    fun parseResponse(rawResponse: String): PaymentResponseData
}

/**
 * Direct NPCI UPI Payment Provider (Google Pay, PhonePe, Paytm, BHIM)
 */
class DirectUpiPaymentProvider : PaymentProvider {
    override val providerId: String = "DIRECT_UPI"

    override fun buildPaymentUri(order: PaymentOrder): Uri {
        val upiId = UpiPaymentManager.getAdminUpiId()
        val payeeName = UpiPaymentManager.getPayeeName()
        val formattedAmount = String.format(java.util.Locale.US, "%.2f", order.amountRupees)
        val note = "Social India - ${order.productType.replace("_", " ")}"
        val cleanTr = order.orderId.replace("[^a-zA-Z0-9]".toRegex(), "")

        val builder = Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", upiId)
            .appendQueryParameter("pn", payeeName)
            .appendQueryParameter("am", formattedAmount)
            .appendQueryParameter("cu", order.currency)
            .appendQueryParameter("tn", note)

        if (cleanTr.isNotBlank()) {
            builder.appendQueryParameter("tr", cleanTr)
        }

        return builder.build()
    }

    override fun parseResponse(rawResponse: String): PaymentResponseData {
        if (rawResponse.isBlank()) {
            return PaymentResponseData(
                status = "CANCELLED",
                responseCode = "USER_CANCELLED",
                txnId = "",
                approvalRefNo = "",
                txnRef = "",
                rawString = ""
            )
        }

        val map = mutableMapOf<String, String>()
        val pairs = rawResponse.split("&")
        for (pair in pairs) {
            val parts = pair.split("=")
            if (parts.size >= 2) {
                map[parts[0].trim().lowercase()] = parts[1].trim()
            }
        }

        val status = map["status"]?.uppercase() ?: "UNKNOWN"
        val responseCode = map["responsecode"] ?: ""
        val txnId = map["txnid"] ?: map["approvalrefno"] ?: "UPI_${System.currentTimeMillis()}"
        val approvalRefNo = map["approvalrefno"] ?: ""
        val txnRef = map["txnref"] ?: ""

        return PaymentResponseData(
            status = status,
            responseCode = responseCode,
            txnId = txnId,
            approvalRefNo = approvalRefNo,
            txnRef = txnRef,
            rawString = rawResponse
        )
    }
}

/**
 * Sandbox Payment Provider for Automated Testing & Verification
 */
class SandboxPaymentProvider : PaymentProvider {
    override val providerId: String = "SANDBOX"

    override fun buildPaymentUri(order: PaymentOrder): Uri {
        return Uri.parse("sandbox://pay?orderId=${order.orderId}&amount=${order.amountRupees}")
    }

    override fun parseResponse(rawResponse: String): PaymentResponseData {
        return PaymentResponseData(
            status = "SUCCESS",
            responseCode = "00",
            txnId = "SANDBOX_TXN_${UUID.randomUUID().toString().take(8)}",
            approvalRefNo = "REF_SANDBOX_001",
            txnRef = "SANDBOX_REF",
            rawString = rawResponse
        )
    }
}
