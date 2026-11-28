package com.example.security

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

enum class IdentityVerificationStatus {
    NOT_SET_UP,
    SETUP_REQUIRED,
    VERIFICATION_PENDING,
    VERIFIED,
    EXPIRED,
    NEEDS_RECHECK,
    FAILED,
    SUSPENDED
}

data class IdentityVerificationRecord(
    val verificationId: String = "",
    val uid: String = "",
    val provider: String = "SocialIndiaEnterpriseIdentity",
    val status: IdentityVerificationStatus = IdentityVerificationStatus.NOT_SET_UP,
    val securityLevel: String = "Tier 2 - Verified Biometric & ID",
    val createdAt: Long = System.currentTimeMillis(),
    val verifiedAt: Long? = null,
    val lastCheckedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val providerReference: String = "",
    val failureReason: String? = null
)

data class PasskeyRecord(
    val id: String = "",
    val uid: String = "",
    val name: String = "Android Device Passkey",
    val credentialId: String = "",
    val aaguid: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
    val deviceModel: String = android.os.Build.MODEL ?: "Android Device"
)

enum class TwoFactorMethod {
    AUTHENTICATOR_APP,
    EMAIL_OTP,
    SMS_OTP,
    BACKUP_CODES
}

data class TwoFactorConfig(
    val isEnabled: Boolean = false,
    val primaryMethod: TwoFactorMethod = TwoFactorMethod.AUTHENTICATOR_APP,
    val totpSecret: String = "",
    val backupCodesHashed: List<String> = emptyList(),
    val phoneVerified: Boolean = false,
    val recoveryPhone: String = "",
    val recoveryEmail: String = "",
    val lastUpdated: Long = System.currentTimeMillis()
)

data class DeviceSession(
    val sessionId: String = "",
    val uid: String = "",
    val deviceId: String = "",
    val deviceName: String = android.os.Build.MODEL ?: "Android Device",
    val deviceType: String = "Mobile",
    val osVersion: String = "Android ${android.os.Build.VERSION.RELEASE}",
    val ipAddress: String = "Current Connection",
    val locationCity: String = "India",
    val isCurrentDevice: Boolean = false,
    val isTrusted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis(),
    val riskScore: Int = 10
)

data class TrustedDevice(
    val deviceId: String = "",
    val uid: String = "",
    val deviceName: String = "",
    val deviceType: String = "Mobile",
    val trustedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis()
)

enum class LoginRiskLevel {
    LOW_RISK,
    MEDIUM_RISK,
    HIGH_RISK
}

data class RiskEvaluation(
    val riskLevel: LoginRiskLevel = LoginRiskLevel.LOW_RISK,
    val reasons: List<String> = emptyList(),
    val requiresChallenge: Boolean = false,
    val availableChallengeFactors: List<String> = emptyList()
)

data class SecurityAuditLog(
    val id: String = "",
    val uid: String = "",
    val eventType: String = "",
    val eventDescription: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val deviceId: String = "",
    val deviceModel: String = android.os.Build.MODEL ?: "Android Device",
    val status: String = "SUCCESS",
    val riskLevel: String = "LOW"
)

data class SecurityNotificationSettings(
    val loginAlertsEnabled: Boolean = true,
    val newDeviceAlertsEnabled: Boolean = true,
    val passwordChangeAlertsEnabled: Boolean = true,
    val twoFactorAlertsEnabled: Boolean = true,
    val identityAlertsEnabled: Boolean = true
)

data class SecurityCheckupResult(
    val passwordHealth: String = "Good",
    val twoFactorActive: Boolean = false,
    val passkeyActive: Boolean = false,
    val identityVerified: Boolean = false,
    val trustedDevicesCount: Int = 1,
    val activeSessionsCount: Int = 1,
    val overallScore: Int = 50,
    val recommendations: List<String> = emptyList()
)

data class AppLockConfig(
    val enabled: Boolean = false,
    val lockType: String = "BIOMETRIC", // "BIOMETRIC" or "DEVICE_CREDENTIAL"
    val timeoutSeconds: Int = 0,
    val protectSensitiveScreens: Boolean = true
)

data class AdminSecurityPolicy(
    val identityProviderName: String = "SocialIndia Enterprise ID Shield",
    val isProviderConfigured: Boolean = false,
    val requireTwoFactorForNewDevices: Boolean = true,
    val maxFailedLoginAttempts: Int = 5,
    val sessionExpiryDays: Int = 30,
    val cooldownMinutes: Int = 15,
    val riskThreshold: String = "MEDIUM"
)
