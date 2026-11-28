package com.example.security

import android.content.Context
import android.os.Build
import com.example.model.RepositoryManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.util.UUID

object AccountSecurityRepository {

    private val firestore: FirebaseFirestore
        get() = RepositoryManager.firestore

    // ==========================================
    // 1. AUDIT LOGGING
    // ==========================================

    suspend fun recordAuditEvent(
        context: Context,
        userId: String,
        eventType: String,
        description: String,
        status: String = "SUCCESS",
        riskLevel: String = "LOW"
    ) = withContext(Dispatchers.IO) {
        if (userId.isEmpty()) return@withContext
        try {
            val deviceId = LoginRiskEngine.getDeviceId(context)
            val log = SecurityAuditLog(
                id = UUID.randomUUID().toString(),
                uid = userId,
                eventType = eventType,
                eventDescription = description,
                timestamp = System.currentTimeMillis(),
                deviceId = deviceId,
                deviceModel = Build.MODEL ?: "Android Device",
                status = status,
                riskLevel = riskLevel
            )
            firestore.collection("users")
                .document(userId)
                .collection("security_audit_logs")
                .document(log.id)
                .set(log)
                .await()
        } catch (e: Exception) {
            android.util.Log.e("AccountSecurityRepo", "Failed to record audit event", e)
        }
    }

    fun listenAuditLogs(userId: String): Flow<List<SecurityAuditLog>> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("users")
            .document(userId)
            .collection("security_audit_logs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val logs = snapshot.documents.mapNotNull {
                        try { it.toObject(SecurityAuditLog::class.java) } catch (_: Exception) { null }
                    }
                    trySend(logs)
                }
            }
        awaitClose { listener.remove() }
    }

    // ==========================================
    // 2. ACTIVE SESSIONS & TRUSTED DEVICES
    // ==========================================

    fun listenSessions(context: Context, userId: String): Flow<List<DeviceSession>> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val currentDeviceId = LoginRiskEngine.getDeviceId(context)
        val listener = firestore.collection("users")
            .document(userId)
            .collection("security_sessions")
            .orderBy("lastActiveAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val sessions = snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(DeviceSession::class.java)?.copy(
                                isCurrentDevice = (doc.getString("deviceId") == currentDeviceId)
                            )
                        } catch (_: Exception) { null }
                    }
                    trySend(sessions)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun registerCurrentSession(context: Context, userId: String, isTrusted: Boolean = false) = withContext(Dispatchers.IO) {
        if (userId.isEmpty()) return@withContext
        try {
            val deviceId = LoginRiskEngine.getDeviceId(context)
            val session = DeviceSession(
                sessionId = "sess_${deviceId.take(8)}",
                uid = userId,
                deviceId = deviceId,
                deviceName = Build.MODEL ?: "Android Device",
                deviceType = "Mobile (Android ${Build.VERSION.RELEASE})",
                osVersion = "Android ${Build.VERSION.SDK_INT}",
                ipAddress = "Current Network",
                locationCity = "India",
                isCurrentDevice = true,
                isTrusted = isTrusted,
                createdAt = System.currentTimeMillis(),
                lastActiveAt = System.currentTimeMillis()
            )
            firestore.collection("users")
                .document(userId)
                .collection("security_sessions")
                .document(session.sessionId)
                .set(session)
                .await()

            if (isTrusted) {
                trustDevice(context, userId, Build.MODEL ?: "Android Device")
            }
        } catch (e: Exception) {
            android.util.Log.e("AccountSecurityRepo", "Failed to register current session", e)
        }
    }

    suspend fun revokeSession(userId: String, sessionId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("users")
                .document(userId)
                .collection("security_sessions")
                .document(sessionId)
                .delete()
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun revokeAllOtherSessions(context: Context, userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentDeviceId = LoginRiskEngine.getDeviceId(context)
            val snap = firestore.collection("users")
                .document(userId)
                .collection("security_sessions")
                .get()
                .await()

            for (doc in snap.documents) {
                if (doc.getString("deviceId") != currentDeviceId) {
                    doc.reference.delete()
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun listenTrustedDevices(userId: String): Flow<List<TrustedDevice>> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("users")
            .document(userId)
            .collection("trusted_devices")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val devices = snapshot.documents.mapNotNull {
                        try { it.toObject(TrustedDevice::class.java) } catch (_: Exception) { null }
                    }
                    trySend(devices)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun trustDevice(context: Context, userId: String, deviceName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val deviceId = LoginRiskEngine.getDeviceId(context)
            val trusted = TrustedDevice(
                deviceId = deviceId,
                uid = userId,
                deviceName = deviceName,
                deviceType = "Mobile",
                trustedAt = System.currentTimeMillis(),
                lastUsedAt = System.currentTimeMillis()
            )
            firestore.collection("users")
                .document(userId)
                .collection("trusted_devices")
                .document(deviceId)
                .set(trusted)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun removeTrustedDevice(userId: String, deviceId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("users")
                .document(userId)
                .collection("trusted_devices")
                .document(deviceId)
                .delete()
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==========================================
    // 3. TWO-FACTOR AUTHENTICATION (2FA)
    // ==========================================

    fun listenTwoFactorConfig(userId: String): Flow<TwoFactorConfig> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(TwoFactorConfig())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("users")
            .document(userId)
            .collection("security")
            .document("two_factor")
            .addSnapshotListener { snapshot, _ ->
                val config = try { snapshot?.toObject(TwoFactorConfig::class.java) } catch (_: Exception) { null } ?: TwoFactorConfig()
                trySend(config)
            }
        awaitClose { listener.remove() }
    }

    fun generateTotpSecret(): String {
        val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val random = SecureRandom()
        return (1..16).map { allowedChars[random.nextInt(allowedChars.length)] }.joinToString("")
    }

    fun generateBackupCodes(): List<String> {
        val random = SecureRandom()
        return (1..8).map {
            val part1 = random.nextInt(9000) + 1000
            val part2 = random.nextInt(9000) + 1000
            "$part1-$part2"
        }
    }

    suspend fun enableTwoFactor(
        userId: String,
        secret: String,
        backupCodes: List<String>,
        method: TwoFactorMethod = TwoFactorMethod.AUTHENTICATOR_APP
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val config = TwoFactorConfig(
                isEnabled = true,
                primaryMethod = method,
                totpSecret = secret,
                backupCodesHashed = backupCodes,
                lastUpdated = System.currentTimeMillis()
            )
            firestore.collection("users")
                .document(userId)
                .collection("security")
                .document("two_factor")
                .set(config)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun disableTwoFactor(userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("users")
                .document(userId)
                .collection("security")
                .document("two_factor")
                .update("isEnabled", false)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==========================================
    // 4. PASSKEYS
    // ==========================================

    fun listenPasskeys(userId: String): Flow<List<PasskeyRecord>> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("users")
            .document(userId)
            .collection("passkeys")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val passkeys = snapshot.documents.mapNotNull {
                        try { it.toObject(PasskeyRecord::class.java) } catch (_: Exception) { null }
                    }
                    trySend(passkeys)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun addPasskey(userId: String, passkey: PasskeyRecord): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("users")
                .document(userId)
                .collection("passkeys")
                .document(passkey.id)
                .set(passkey)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun renamePasskey(userId: String, passkeyId: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("users")
                .document(userId)
                .collection("passkeys")
                .document(passkeyId)
                .update("name", newName)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun removePasskey(userId: String, passkeyId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("users")
                .document(userId)
                .collection("passkeys")
                .document(passkeyId)
                .delete()
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==========================================
    // 5. IDENTITY VERIFICATION METADATA
    // ==========================================

    fun listenIdentityVerification(userId: String): Flow<IdentityVerificationRecord> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(IdentityVerificationRecord())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("users")
            .document(userId)
            .collection("security_identity")
            .document("record")
            .addSnapshotListener { snapshot, _ ->
                val record = try { snapshot?.toObject(IdentityVerificationRecord::class.java) } catch (_: Exception) { null } ?: IdentityVerificationRecord(uid = userId)
                trySend(record)
            }
        awaitClose { listener.remove() }
    }

    suspend fun updateIdentityVerificationRecord(userId: String, record: IdentityVerificationRecord): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("users")
                .document(userId)
                .collection("security_identity")
                .document("record")
                .set(record)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun resetIdentityVerification(userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("users")
                .document(userId)
                .collection("security_identity")
                .document("record")
                .delete()
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==========================================
    // 6. APP LOCK & NOTIFICATION SETTINGS
    // ==========================================

    fun listenAppLockConfig(userId: String): Flow<AppLockConfig> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(AppLockConfig())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("users")
            .document(userId)
            .collection("security")
            .document("app_lock")
            .addSnapshotListener { snapshot, _ ->
                val config = try { snapshot?.toObject(AppLockConfig::class.java) } catch (_: Exception) { null } ?: AppLockConfig()
                trySend(config)
            }
        awaitClose { listener.remove() }
    }

    suspend fun saveAppLockConfig(userId: String, config: AppLockConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("users")
                .document(userId)
                .collection("security")
                .document("app_lock")
                .set(config)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun listenNotificationSettings(userId: String): Flow<SecurityNotificationSettings> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(SecurityNotificationSettings())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("users")
            .document(userId)
            .collection("security")
            .document("notifications")
            .addSnapshotListener { snapshot, _ ->
                val settings = try { snapshot?.toObject(SecurityNotificationSettings::class.java) } catch (_: Exception) { null } ?: SecurityNotificationSettings()
                trySend(settings)
            }
        awaitClose { listener.remove() }
    }

    suspend fun saveNotificationSettings(userId: String, settings: SecurityNotificationSettings): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("users")
                .document(userId)
                .collection("security")
                .document("notifications")
                .set(settings)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==========================================
    // 7. ADMIN SECURITY POLICY
    // ==========================================

    fun listenAdminSecurityPolicy(): Flow<AdminSecurityPolicy> = callbackFlow {
        val listener = firestore.collection("security_policies")
            .document("global")
            .addSnapshotListener { snapshot, _ ->
                val policy = try { snapshot?.toObject(AdminSecurityPolicy::class.java) } catch (_: Exception) { null } ?: AdminSecurityPolicy()
                trySend(policy)
            }
        awaitClose { listener.remove() }
    }

    suspend fun updateAdminSecurityPolicy(policy: AdminSecurityPolicy): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("security_policies")
                .document("global")
                .set(policy)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==========================================
    // 8. SECURITY CHECKUP ENGINE
    // ==========================================

    suspend fun performSecurityCheckup(
        context: Context,
        userId: String
    ): SecurityCheckupResult = withContext(Dispatchers.IO) {
        var score = 40 // Base score for having an active password account
        val recommendations = mutableListOf<String>()

        try {
            // Check 2FA
            val twoFactorDoc = firestore.collection("users").document(userId).collection("security").document("two_factor").get().await()
            val is2FA = twoFactorDoc.getBoolean("isEnabled") ?: false
            if (is2FA) {
                score += 25
            } else {
                recommendations.add("Enable Two-Factor Authentication (2FA) for heightened account defense.")
            }

            // Check Passkeys
            val passkeysSnap = firestore.collection("users").document(userId).collection("passkeys").get().await()
            val hasPasskey = !passkeysSnap.isEmpty
            if (hasPasskey) {
                score += 15
            } else {
                recommendations.add("Create an Android Passkey for seamless, phishing-resistant device login.")
            }

            // Check Identity Verification
            val idDoc = firestore.collection("users").document(userId).collection("security_identity").document("record").get().await()
            val isIdVerified = idDoc.getString("status") == IdentityVerificationStatus.VERIFIED.name
            if (isIdVerified) {
                score += 15
            } else {
                recommendations.add("Verify your Identity to protect against unauthorized account takeovers.")
            }

            // Check Active Sessions
            val sessionsSnap = firestore.collection("users").document(userId).collection("security_sessions").get().await()
            val sessionCount = sessionsSnap.size().coerceAtLeast(1)

            // Check Trusted Devices
            val trustedSnap = firestore.collection("users").document(userId).collection("trusted_devices").get().await()
            val trustedCount = trustedSnap.size()

            val finalScore = score.coerceIn(20, 95) // Never claim fake 100%

            return@withContext SecurityCheckupResult(
                passwordHealth = "Encrypted Firebase Token",
                twoFactorActive = is2FA,
                passkeyActive = hasPasskey,
                identityVerified = isIdVerified,
                trustedDevicesCount = trustedCount,
                activeSessionsCount = sessionCount,
                overallScore = finalScore,
                recommendations = recommendations
            )
        } catch (e: Exception) {
            return@withContext SecurityCheckupResult(
                passwordHealth = "Encrypted Token",
                twoFactorActive = false,
                passkeyActive = false,
                identityVerified = false,
                trustedDevicesCount = 1,
                activeSessionsCount = 1,
                overallScore = 45,
                recommendations = listOf("Review your security settings to enhance account protection.")
            )
        }
    }
}
