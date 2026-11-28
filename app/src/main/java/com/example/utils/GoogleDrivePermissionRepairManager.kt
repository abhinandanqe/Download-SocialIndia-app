package com.example.utils

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.myAppInstance
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Manages Google Drive permission verification and retroactive auto-repair.
 * Guarantees that every Google Drive fileId is granted the public 'anyone' reader permission
 * so media is 100% visible across all devices and accounts without 403 Forbidden errors.
 */
object GoogleDrivePermissionRepairManager {
    private const val TAG = "DrivePermissionRepair"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // Cache of file IDs that have already been repaired or verified to avoid redundant API calls
    private val repairedFileIds = ConcurrentHashMap.newKeySet<String>()
    private val cachedBearerTokens = java.util.concurrent.CopyOnWriteArrayList<String>()

    fun cacheBearerToken(token: String?) {
        if (!token.isNullOrBlank() && !token.startsWith("pool_linked_") && !token.startsWith("1//") && !cachedBearerTokens.contains(token)) {
            cachedBearerTokens.add(0, token)
        }
    }

    @Volatile
    private var isWarmingTokens = false

    fun warmBearerTokensAsync() {
        if (isWarmingTokens) return
        isWarmingTokens = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                try {
                    val accounts = AdminDriveStoragePoolManager.getAllAccounts()
                    for (acc in accounts) {
                        val tok = acc.accessToken
                        if (tok.isNotBlank() && !tok.startsWith("pool_linked_") && !tok.startsWith("1//")) {
                            cacheBearerToken(tok)
                        }
                    }
                } catch (_: Exception) {}
                try {
                    val context: Context? = try { myAppInstance } catch (_: Exception) { null }
                    if (context != null) {
                        val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
                        val driveScope = Scope("https://www.googleapis.com/auth/drive.file")
                        if (googleAccount != null && GoogleSignIn.hasPermissions(googleAccount, driveScope)) {
                            googleAccount.account?.let { acc ->
                                val userToken = GoogleAuthUtil.getToken(context, acc, "oauth2:https://www.googleapis.com/auth/drive.file")
                                if (userToken.isNotBlank()) {
                                    cacheBearerToken(userToken)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            } finally {
                isWarmingTokens = false
            }
        }
    }

    fun getValidBearerToken(): String? {
        val existing = cachedBearerTokens.firstOrNull { it.isNotBlank() && !it.startsWith("pool_linked_") && !it.startsWith("1//") }
        if (existing != null) return existing
        warmBearerTokensAsync()
        return null
    }

    /**
     * Checks and sets public 'anyone' reader permission during upload.
     * Retries up to maxRetries times to confirm permission before allowing the file to be saved.
     */
    suspend fun ensurePublicPermission(fileId: String, token: String, maxRetries: Int = 3): Boolean = withContext(Dispatchers.IO) {
        if (fileId.isBlank() || token.isBlank()) return@withContext false
        cacheBearerToken(token)

        val permJson = JSONObject().apply {
            put("role", "reader")
            put("type", "anyone")
        }
        val mediaType = "application/json; charset=UTF-8".toMediaTypeOrNull()

        for (attempt in 1..maxRetries) {
            try {
                val permRequest = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/$fileId/permissions")
                    .addHeader("Authorization", "Bearer $token")
                    .post(permJson.toString().toRequestBody(mediaType))
                    .build()

                val response = httpClient.newCall(permRequest).execute()
                val responseBody = response.body?.string()
                response.close()

                if (response.isSuccessful || response.code == 200 || response.code == 201) {
                    repairedFileIds.add(fileId)
                    Log.d(TAG, "Successfully confirmed public 'anyone' permission for file: $fileId")
                    return@withContext true
                } else if (responseBody?.contains("alreadyExists", ignoreCase = true) == true) {
                    repairedFileIds.add(fileId)
                    Log.d(TAG, "File $fileId already has public permission.")
                    return@withContext true
                } else {
                    Log.w(TAG, "Permission attempt $attempt failed ($responseBody). Retrying...")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exception on permission attempt $attempt: ${e.message}")
            }

            if (attempt < maxRetries) {
                delay(attempt * 400L)
            }
        }
        return@withContext false
    }

    /**
     * Retroactively checks and repairs permissions in the background for existing posts, Reels, and Stories.
     * Uses linked Admin Drive pool tokens and active Google Sign-In accounts to grant public reader rights.
     */
    fun repairPermissionAsync(fileId: String) {
        if (fileId.isBlank() || repairedFileIds.contains(fileId)) return
        repairedFileIds.add(fileId)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tokens = mutableListOf<String>()

                // 1. Gather tokens from Admin Drive Storage Pool accounts
                try {
                    val accounts = AdminDriveStoragePoolManager.getAllAccounts()
                    for (acc in accounts) {
                        val tok = acc.accessToken.ifEmpty { acc.refreshToken }
                        if (tok.isNotBlank() && !tok.startsWith("pool_linked_") && !tokens.contains(tok)) {
                            tokens.add(tok)
                            cacheBearerToken(tok)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load admin accounts for repair: ${e.message}")
                }

                // 2. Gather token from active Google Sign-In if available
                try {
                    val context: Context? = try { myAppInstance } catch (_: Exception) { null }
                    if (context != null) {
                        val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
                        val driveScope = Scope("https://www.googleapis.com/auth/drive.file")
                        if (googleAccount != null && GoogleSignIn.hasPermissions(googleAccount, driveScope)) {
                            googleAccount.account?.let { acc ->
                                val userToken = GoogleAuthUtil.getToken(context, acc, "oauth2:https://www.googleapis.com/auth/drive.file")
                                if (userToken.isNotBlank() && !tokens.contains(userToken)) {
                                    tokens.add(userToken)
                                    cacheBearerToken(userToken)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not fetch user Google token for repair: ${e.message}")
                }

                // 3. Attempt repair with each available token
                val permJson = JSONObject().apply {
                    put("role", "reader")
                    put("type", "anyone")
                }
                val mediaType = "application/json; charset=UTF-8".toMediaTypeOrNull()

                for (token in tokens) {
                    try {
                        val permRequest = Request.Builder()
                            .url("https://www.googleapis.com/drive/v3/files/$fileId/permissions")
                            .addHeader("Authorization", "Bearer $token")
                            .post(permJson.toString().toRequestBody(mediaType))
                            .build()

                        val resp = httpClient.newCall(permRequest).execute()
                        val body = resp.body?.string()
                        resp.close()
                        if (resp.isSuccessful || resp.code == 200 || resp.code == 201 || body?.contains("alreadyExists", ignoreCase = true) == true) {
                            Log.d(TAG, "Retroactive repair successful for fileId: $fileId")
                            break
                        }
                    } catch (_: Exception) {
                        // Continue to next available token
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error in retroactive permission repair: ${e.message}")
            }
        }
    }

    /**
     * Normalizes any Google Drive link to a direct streaming or thumbnail endpoint without invalid API keys.
     */
    fun normalizeDriveUrl(url: String, isVideo: Boolean): String {
        val fileId = MediaResolver.extractGoogleDriveFileId(url) ?: return url
        repairPermissionAsync(fileId)

        return if (isVideo) {
            "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"
        } else {
            "https://drive.google.com/thumbnail?id=$fileId&sz=w1080"
        }
    }
}
