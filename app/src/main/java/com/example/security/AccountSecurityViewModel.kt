package com.example.security

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.RepositoryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class AccountSecurityViewModel : ViewModel() {

    private val repository = AccountSecurityRepository
    private val currentUserId: String
        get() = RepositoryManager.getCurrentUserId()

    private val _sessions = MutableStateFlow<List<DeviceSession>>(emptyList())
    val sessions: StateFlow<List<DeviceSession>> = _sessions.asStateFlow()

    private val _trustedDevices = MutableStateFlow<List<TrustedDevice>>(emptyList())
    val trustedDevices: StateFlow<List<TrustedDevice>> = _trustedDevices.asStateFlow()

    private val _passkeys = MutableStateFlow<List<PasskeyRecord>>(emptyList())
    val passkeys: StateFlow<List<PasskeyRecord>> = _passkeys.asStateFlow()

    private val _twoFactorConfig = MutableStateFlow(TwoFactorConfig())
    val twoFactorConfig: StateFlow<TwoFactorConfig> = _twoFactorConfig.asStateFlow()

    private val _identityRecord = MutableStateFlow(IdentityVerificationRecord())
    val identityRecord: StateFlow<IdentityVerificationRecord> = _identityRecord.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<SecurityAuditLog>>(emptyList())
    val auditLogs: StateFlow<List<SecurityAuditLog>> = _auditLogs.asStateFlow()

    private val _appLockConfig = MutableStateFlow(AppLockConfig())
    val appLockConfig: StateFlow<AppLockConfig> = _appLockConfig.asStateFlow()

    private val _notificationSettings = MutableStateFlow(SecurityNotificationSettings())
    val notificationSettings: StateFlow<SecurityNotificationSettings> = _notificationSettings.asStateFlow()

    private val _checkupResult = MutableStateFlow<SecurityCheckupResult?>(null)
    val checkupResult: StateFlow<SecurityCheckupResult?> = _checkupResult.asStateFlow()

    private val _adminPolicy = MutableStateFlow(AdminSecurityPolicy())
    val adminPolicy: StateFlow<AdminSecurityPolicy> = _adminPolicy.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun initForUser(context: Context) {
        val uid = currentUserId
        if (uid.isEmpty()) return

        viewModelScope.launch {
            repository.registerCurrentSession(context, uid)
            repository.recordAuditEvent(
                context = context,
                userId = uid,
                eventType = "SESSION_ACTIVE",
                description = "Account security session initialized"
            )
        }

        viewModelScope.launch {
            repository.listenSessions(context, uid).collect { _sessions.value = it }
        }
        viewModelScope.launch {
            repository.listenTrustedDevices(uid).collect { _trustedDevices.value = it }
        }
        viewModelScope.launch {
            repository.listenPasskeys(uid).collect { _passkeys.value = it }
        }
        viewModelScope.launch {
            repository.listenTwoFactorConfig(uid).collect { _twoFactorConfig.value = it }
        }
        viewModelScope.launch {
            repository.listenIdentityVerification(uid).collect { _identityRecord.value = it }
        }
        viewModelScope.launch {
            repository.listenAuditLogs(uid).collect { _auditLogs.value = it }
        }
        viewModelScope.launch {
            repository.listenAppLockConfig(uid).collect { _appLockConfig.value = it }
        }
        viewModelScope.launch {
            repository.listenNotificationSettings(uid).collect { _notificationSettings.value = it }
        }
        viewModelScope.launch {
            repository.listenAdminSecurityPolicy().collect { _adminPolicy.value = it }
        }
    }

    fun runSecurityCheckup(context: Context) {
        val uid = currentUserId
        if (uid.isEmpty()) return
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.performSecurityCheckup(context, uid)
            _checkupResult.value = result
            _isLoading.value = false
            repository.recordAuditEvent(
                context = context,
                userId = uid,
                eventType = "SECURITY_CHECKUP",
                description = "Security checkup completed with score ${result.overallScore}/100"
            )
        }
    }

    fun revokeSession(context: Context, sessionId: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.revokeSession(currentUserId, sessionId)
            if (success) {
                repository.recordAuditEvent(
                    context = context,
                    userId = currentUserId,
                    eventType = "SESSION_REVOKED",
                    description = "Session $sessionId was revoked"
                )
            }
            onComplete(success)
        }
    }

    fun revokeAllOtherSessions(context: Context, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.revokeAllOtherSessions(context, currentUserId)
            if (success) {
                repository.recordAuditEvent(
                    context = context,
                    userId = currentUserId,
                    eventType = "ALL_OTHER_SESSIONS_REVOKED",
                    description = "Logged out from all other active devices"
                )
            }
            onComplete(success)
        }
    }

    fun removeTrustedDevice(context: Context, deviceId: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.removeTrustedDevice(currentUserId, deviceId)
            if (success) {
                repository.recordAuditEvent(
                    context = context,
                    userId = currentUserId,
                    eventType = "DEVICE_UNTRUSTED",
                    description = "Device $deviceId removed from trusted list"
                )
            }
            onComplete(success)
        }
    }

    fun trustCurrentDevice(context: Context, deviceName: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.trustDevice(context, currentUserId, deviceName)
            if (success) {
                repository.recordAuditEvent(
                    context = context,
                    userId = currentUserId,
                    eventType = "DEVICE_TRUSTED",
                    description = "Device $deviceName marked as trusted"
                )
            }
            onComplete(success)
        }
    }

    fun enableTwoFactor(
        context: Context,
        secret: String,
        backupCodes: List<String>,
        method: TwoFactorMethod,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val success = repository.enableTwoFactor(currentUserId, secret, backupCodes, method)
            if (success) {
                repository.recordAuditEvent(
                    context = context,
                    userId = currentUserId,
                    eventType = "2FA_ENABLED",
                    description = "Two-Factor Authentication enabled via ${method.name}"
                )
            }
            onComplete(success)
        }
    }

    fun disableTwoFactor(context: Context, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.disableTwoFactor(currentUserId)
            if (success) {
                repository.recordAuditEvent(
                    context = context,
                    userId = currentUserId,
                    eventType = "2FA_DISABLED",
                    description = "Two-Factor Authentication was disabled",
                    riskLevel = "MEDIUM"
                )
            }
            onComplete(success)
        }
    }

    fun addPasskey(context: Context, name: String, credentialId: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val passkey = PasskeyRecord(
                id = UUID.randomUUID().toString(),
                uid = currentUserId,
                name = name,
                credentialId = credentialId,
                createdAt = System.currentTimeMillis()
            )
            val success = repository.addPasskey(currentUserId, passkey)
            if (success) {
                repository.recordAuditEvent(
                    context = context,
                    userId = currentUserId,
                    eventType = "PASSKEY_ADDED",
                    description = "Passkey '$name' registered on device"
                )
            }
            onComplete(success)
        }
    }

    fun renamePasskey(context: Context, passkeyId: String, newName: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.renamePasskey(currentUserId, passkeyId, newName)
            onComplete(success)
        }
    }

    fun removePasskey(context: Context, passkeyId: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.removePasskey(currentUserId, passkeyId)
            if (success) {
                repository.recordAuditEvent(
                    context = context,
                    userId = currentUserId,
                    eventType = "PASSKEY_REMOVED",
                    description = "Passkey $passkeyId removed from account"
                )
            }
            onComplete(success)
        }
    }

    fun resetIdentityVerification(context: Context, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.resetIdentityVerification(currentUserId)
            if (success) {
                repository.recordAuditEvent(
                    context = context,
                    userId = currentUserId,
                    eventType = "IDENTITY_VERIFICATION_RESET",
                    description = "Identity verification status reset by user",
                    riskLevel = "MEDIUM"
                )
            }
            onComplete(success)
        }
    }

    fun saveAppLockConfig(context: Context, config: AppLockConfig, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.saveAppLockConfig(currentUserId, config)
            if (success) {
                repository.recordAuditEvent(
                    context = context,
                    userId = currentUserId,
                    eventType = if (config.enabled) "APP_LOCK_ENABLED" else "APP_LOCK_DISABLED",
                    description = "App Lock state changed to ${config.enabled}"
                )
            }
            onComplete(success)
        }
    }

    fun saveNotificationSettings(settings: SecurityNotificationSettings, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.saveNotificationSettings(currentUserId, settings)
            onComplete(success)
        }
    }

    fun updateAdminSecurityPolicy(policy: AdminSecurityPolicy, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.updateAdminSecurityPolicy(policy)
            onComplete(success)
        }
    }
}
