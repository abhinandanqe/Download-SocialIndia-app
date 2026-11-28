package com.example.auth

import android.content.Context
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

object AccountSecurityManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "SocialIndiaAuthCredentialMasterKey"
    private const val AES_GCM_NOPADDING = "AES/GCM/NoPadding"

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGenerator.init(keyGenParameterSpec)
            return keyGenerator.generateKey()
        }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        return entry?.secretKey ?: throw IllegalStateException("Cannot retrieve master key from AndroidKeyStore")
    }

    /**
     * Encrypts sensitive credentials so they are NEVER saved in plain text in local storage.
     * Uses AndroidKeyStore AES/GCM/NoPadding. If AndroidKeyStore is unavailable on a legacy emulator,
     * falls back to device-bound AES key.
     */
    fun encrypt(context: Context, plainText: String?): String {
        if (plainText.isNullOrEmpty()) return ""
        try {
            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val ivStr = Base64.encodeToString(iv, Base64.NO_WRAP)
            val cipherStr = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
            return "KS:$ivStr:$cipherStr"
        } catch (e: Exception) {
            android.util.Log.w("AccountSecurity", "AndroidKeyStore encrypt failed, using fallback device AES cipher", e)
            return encryptWithDeviceFallback(context, plainText)
        }
    }

    /**
     * Decrypts encrypted credentials in-memory only right before authenticating.
     */
    fun decrypt(context: Context, encryptedString: String?): String {
        if (encryptedString.isNullOrEmpty()) return ""
        try {
            if (encryptedString.startsWith("KS:")) {
                val parts = encryptedString.split(":")
                if (parts.size == 3) {
                    val iv = Base64.decode(parts[1], Base64.NO_WRAP)
                    val cipherBytes = Base64.decode(parts[2], Base64.NO_WRAP)
                    val secretKey = getOrCreateKey()
                    val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
                    val spec = GCMParameterSpec(128, iv)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                    val plainBytes = cipher.doFinal(cipherBytes)
                    return String(plainBytes, Charsets.UTF_8)
                }
            } else if (encryptedString.startsWith("FB:")) {
                return decryptWithDeviceFallback(context, encryptedString)
            }
        } catch (e: Exception) {
            android.util.Log.e("AccountSecurity", "Failed to decrypt credential", e)
        }
        return ""
    }

    private fun getDeviceFallbackKey(context: Context): SecretKey {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "social_india_sec_seed"
        val sha256 = MessageDigest.getInstance("SHA-256")
        val keyBytes = sha256.digest((context.packageName + ":" + androidId + ":SocialIndiaDeviceSalt").toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun encryptWithDeviceFallback(context: Context, plainText: String): String {
        val secretKey = getDeviceFallbackKey(context)
        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val ivStr = Base64.encodeToString(iv, Base64.NO_WRAP)
        val cipherStr = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        return "FB:$ivStr:$cipherStr"
    }

    private fun decryptWithDeviceFallback(context: Context, encryptedString: String): String {
        val parts = encryptedString.split(":")
        if (parts.size != 3) return ""
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipherBytes = Base64.decode(parts[2], Base64.NO_WRAP)
        val secretKey = getDeviceFallbackKey(context)
        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, Charsets.UTF_8)
    }
}
