package com.example.payments

import com.example.model.PaymentProductType
import com.example.model.Post
import com.example.repository.EntitlementRepository
import org.junit.Assert.*
import org.junit.Test

class PaymentVerificationUnitTest {

    private val directUpiProvider = DirectUpiPaymentProvider()

    @Test
    fun testDirectUpiResponseParsing_Success() {
        val rawResponse = "Status=SUCCESS&txnId=UPI987654321&responseCode=00&ApprovalRefNo=APPR112233"
        val parsed = directUpiProvider.parseResponse(rawResponse)

        assertEquals("SUCCESS", parsed.status)
        assertTrue(parsed.isAuthoritativeSuccess)
        assertEquals("UPI987654321", parsed.txnId)
        assertEquals("00", parsed.responseCode)
        assertEquals("APPR112233", parsed.approvalRefNo)
    }

    @Test
    fun testDirectUpiResponseParsing_CaseInsensitiveSuccess() {
        val rawResponse = "status=success&txnid=TXN99&approvalrefno=REF77"
        val parsed = directUpiProvider.parseResponse(rawResponse)

        assertEquals("SUCCESS", parsed.status)
        assertTrue(parsed.isAuthoritativeSuccess)
        assertEquals("TXN99", parsed.txnId)
        assertEquals("REF77", parsed.approvalRefNo)
    }

    @Test
    fun testDirectUpiResponseParsing_CancelledOrEmpty() {
        val parsedEmpty = directUpiProvider.parseResponse("")
        assertEquals("CANCELLED", parsedEmpty.status)
        assertFalse(parsedEmpty.isAuthoritativeSuccess)

        val parsedFailure = directUpiProvider.parseResponse("Status=FAILED&responseCode=ZA")
        assertEquals("FAILED", parsedFailure.status)
        assertFalse(parsedFailure.isAuthoritativeSuccess)
    }

    @Test
    fun testCreatorRevenueSplit90_10() {
        val grossAmount = 100.0
        val platformFee = grossAmount * 0.10
        val netCreatorAmount = grossAmount - platformFee

        assertEquals(10.0, platformFee, 0.001)
        assertEquals(90.0, netCreatorAmount, 0.001)

        val grossSmall = 29.0
        val feeSmall = grossSmall * 0.10
        val netSmall = grossSmall - feeSmall
        assertEquals(2.90, feeSmall, 0.001)
        assertEquals(26.10, netSmall, 0.001)
    }

    @Test
    fun testPaymentProductTypesIntegrity() {
        assertNotNull(PaymentProductType.valueOf("INDIVIDUAL_POST_UNLOCK"))
        assertNotNull(PaymentProductType.valueOf("CREATOR_SUBSCRIPTION"))
        assertNotNull(PaymentProductType.valueOf("WALLET_DEPOSIT"))
        assertNotNull(PaymentProductType.valueOf("VIP_PRO_PASS"))
        assertNotNull(PaymentProductType.valueOf("BLUE_TICK_VERIFICATION"))
        assertNotNull(PaymentProductType.valueOf("CREATOR_TIP"))
    }

    @Test
    fun testEntitlementSelfAccess() {
        val creatorId = "creator_user_123"
        val post = Post(
            id = "post_exclusive_1",
            userId = creatorId,
            isPaidPost = true,
            postUnlockPrice = 49.0
        )

        // Creator himself must always have access to his own post
        val hasAccess = EntitlementRepository.hasAccessFast(
            userId = creatorId,
            creatorId = creatorId,
            contentId = post.id,
            isExclusive = post.isExclusive,
            isPaidPost = post.isPaidPost
        )

        assertTrue("Creator must have fast access to their own content", hasAccess)
    }

    @Test
    fun testEntitlementNonMonetizedPost() {
        val post = Post(
            id = "post_free_1",
            userId = "creator_user_123",
            isPaidPost = false,
            isExclusive = false
        )

        val hasAccess = EntitlementRepository.hasAccessFast(
            userId = "viewer_user_456",
            creatorId = post.userId,
            contentId = post.id,
            isExclusive = post.isExclusive,
            isPaidPost = post.isPaidPost
        )

        assertTrue("Regular public posts must be accessible by any viewer", hasAccess)
    }
}
