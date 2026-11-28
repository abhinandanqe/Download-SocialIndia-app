package com.example.security

import android.content.Context
import android.provider.Settings
import com.example.model.RepositoryManager
import kotlinx.coroutines.tasks.await

object LoginRiskEngine {

    fun getDeviceId(context: Context): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "device_generic_${android.os.Build.MODEL.hashCode()}"
        } catch (e: Exception) {
            "device_unknown_${System.currentTimeMillis()}"
        }
    }

    suspend fun evaluateLoginRisk(
        context: Context,
        userId: String,
        isPasswordCorrect: Boolean
    ): RiskEvaluation {
        val deviceId = getDeviceId(context)
        val reasons = mutableListOf<String>()
        val challengeFactors = mutableListOf<String>()

        try {
            val firestore = RepositoryManager.firestore
            // Check trusted devices
            val trustedDoc = firestore.collection("users")
                .document(userId)
                .collection("trusted_devices")
                .document(deviceId)
                .get()
                .await()

            val isDeviceTrusted = trustedDoc.exists()

            // Check 2FA config
            val twoFactorDoc = firestore.collection("users")
                .document(userId)
                .collection("security")
                .document("two_factor")
                .get()
                .await()

            val isTwoFactorEnabled = twoFactorDoc.getBoolean("isEnabled") ?: false

            // Check Identity Verification
            val identityDoc = firestore.collection("users")
                .document(userId)
                .collection("security_identity")
                .document("record")
                .get()
                .await()

            val isIdentityVerified = identityDoc.getString("status") == IdentityVerificationStatus.VERIFIED.name

            // Check recent failed attempts in last 15 minutes
            val fifteenMinutesAgo = System.currentTimeMillis() - (15 * 60 * 1000)
            val failedAttemptsQuery = firestore.collection("users")
                .document(userId)
                .collection("security_audit_logs")
                .whereEqualTo("status", "FAILED")
                .whereGreaterThan("timestamp", fifteenMinutesAgo)
                .get()
                .await()

            val recentFailuresCount = failedAttemptsQuery.size()

            if (recentFailuresCount >= 3) {
                reasons.add("Multiple recent failed login attempts detected ($recentFailuresCount)")
            }

            if (!isDeviceTrusted) {
                reasons.add("New or unrecognized device (${android.os.Build.MODEL})")
            }

            // Build available challenge factors
            if (isTwoFactorEnabled) {
                challengeFactors.add("TWO_FACTOR_AUTHENTICATOR")
                challengeFactors.add("BACKUP_CODE")
            }

            // Passkey support
            val passkeysSnap = firestore.collection("users")
                .document(userId)
                .collection("passkeys")
                .limit(1)
                .get()
                .await()

            if (!passkeysSnap.isEmpty) {
                challengeFactors.add("PASSKEY")
            }

            if (isIdentityVerified) {
                challengeFactors.add("IDENTITY_VERIFICATION")
            }

            // Fallback recovery option
            challengeFactors.add("ACCOUNT_RECOVERY")

            val riskLevel = when {
                recentFailuresCount >= 5 -> LoginRiskLevel.HIGH_RISK
                !isDeviceTrusted && (isTwoFactorEnabled || isIdentityVerified) -> LoginRiskLevel.HIGH_RISK
                !isDeviceTrusted -> LoginRiskLevel.MEDIUM_RISK
                recentFailuresCount in 1..4 -> LoginRiskLevel.MEDIUM_RISK
                else -> LoginRiskLevel.LOW_RISK
            }

            val requiresChallenge = (riskLevel == LoginRiskLevel.HIGH_RISK || (riskLevel == LoginRiskLevel.MEDIUM_RISK && isTwoFactorEnabled))

            return RiskEvaluation(
                riskLevel = riskLevel,
                reasons = reasons,
                requiresChallenge = requiresChallenge,
                availableChallengeFactors = challengeFactors
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("LoginRiskEngine", "Risk evaluation error", e)
            return RiskEvaluation(
                riskLevel = LoginRiskLevel.LOW_RISK,
                reasons = emptyList(),
                requiresChallenge = false,
                availableChallengeFactors = emptyList()
            )
        }
    }
}
