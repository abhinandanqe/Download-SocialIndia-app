package com.example.model

object VerificationStatus {
    const val NOT_VERIFIED = "NOT_VERIFIED"
    const val APPLICATION_SUBMITTED = "APPLICATION_SUBMITTED"
    const val UNDER_REVIEW = "UNDER_REVIEW"
    const val PAYMENT_PENDING = "PAYMENT_PENDING"
    const val PAYMENT_VERIFICATION = "PAYMENT_VERIFICATION" // User submitted payment, pending admin review
    const val VERIFIED_ACTIVE = "VERIFIED_ACTIVE"
    const val EXPIRING_SOON = "EXPIRING_SOON"
    const val EXPIRED = "EXPIRED"
    const val SUSPENDED = "SUSPENDED"
    const val REJECTED = "REJECTED"

    // Backward-compat aliases
    const val PENDING = "pending"
    const val APPROVED = "approved"
}

object VerificationType {
    const val PERSONAL = "Personal"
    const val CREATOR = "Creator"
    const val BUSINESS = "Business"
}

data class VerificationRequest(
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val userAvatarUrl: String = "",
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val country: String = "India",
    val category: String = "Creator / Influencer",
    val verificationType: String = VerificationType.PERSONAL,
    
    // Business specific
    val businessName: String = "",
    val businessCategory: String = "",
    val businessContact: String = "",
    val businessEmail: String = "",
    val businessPhone: String = "",
    val businessWebsite: String = "",
    val businessAddress: String = "",
    val businessRegistrationNumber: String = "",
    
    val reason: String = "",
    val documentUrl: String = "",
    val selfieUrl: String = "",
    val businessDocUrl: String = "",
    
    // Status
    val status: String = VerificationStatus.APPLICATION_SUBMITTED,
    val paymentStatus: String = "UNPAID", // UNPAID, PAYMENT_PENDING_ADMIN_REVIEW, APPROVED, REJECTED
    
    // Plan & Duration
    val planId: String = "plan_12m",
    val planName: String = "Annual Verified",
    val planDurationMonths: Int = 12,
    val planPrice: Double = 4499.0,
    val planCurrency: String = "INR",
    
    // Manual Admin Payment Approval
    val paymentReference: String = "",
    val paymentProofUrl: String = "",
    val paymentSubmittedAt: Long = 0,
    val paymentApprovedAt: Long = 0,
    
    // Dates & Timestamps
    val timestamp: Long = System.currentTimeMillis(),
    val submittedAt: Long = System.currentTimeMillis(),
    val reviewedAt: Long = 0,
    val verifiedAt: Long = 0,
    val startTimestamp: Long = 0,
    val expiryTimestamp: Long = 0,
    
    // Admin management
    val adminId: String = "",
    val adminNotes: String = "",
    val rejectionReason: String = "",
    val suspensionReason: String = "",
    val isRenewal: Boolean = false,
    val previousVerificationId: String = ""
)

data class VerificationPlan(
    val id: String = "",
    val name: String = "",
    val durationMonths: Int = 1,
    val price: Double = 0.0,
    val currency: String = "INR",
    val benefits: List<String> = emptyList(),
    val isActive: Boolean = true,
    val allowRenewal: Boolean = true,
    val popularBadge: String = ""
)

data class VerificationSettings(
    val id: String = "default",
    val paymentInstructions: String = "Pay directly using instant automated UPI (Google Pay, PhonePe, Paytm). Zero-fee direct auto-approval without screenshot upload.",
    val upiId: String = "abhinandankumar7867@okaxis",
    val qrCodeUrl: String = "",
    val bankAccountDetails: String = "Bank: HDFC Bank\nA/C No: 50200088991122\nIFSC: HDFC0000123\nBeneficiary: Social India Network",
    val requireDocumentForPersonal: Boolean = true,
    val requireSelfie: Boolean = true,
    val requireBusinessRegistration: Boolean = true,
    val businessRequiresActiveVerification: Boolean = true,
    val autoExpireCheck: Boolean = true
)

data class VerificationAuditLog(
    val id: String = "",
    val verificationId: String = "",
    val userId: String = "",
    val adminId: String = "",
    val action: String = "",
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class VerificationSupportTicket(
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val category: String = "Verification Support",
    val subject: String = "",
    val message: String = "",
    val status: String = "OPEN", // OPEN, IN_PROGRESS, RESOLVED, CLOSED
    val isPriority: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val adminReply: String = ""
)

data class BusinessProfile(
    val userId: String = "",
    val businessName: String = "",
    val category: String = "",
    val description: String = "",
    val website: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val country: String = "India",
    val hours: String = "",
    val actionButton: String = "Contact Us",
    val actionUrl: String = "",
    val verifiedAccountUid: String = "",
    val verificationId: String = "",
    val isVerified: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
