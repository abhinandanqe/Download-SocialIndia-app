package com.example.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.model.AdminDriveAccount
import com.example.model.RepositoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

object AdminDriveStoragePoolManager {
    private const val TAG = "AdminDrivePool"
    private const val COLLECTION_ACCOUNTS = "admin_drive_accounts"
    private const val MAX_AUTO_STORAGE_BYTES = 14L * 1024 * 1024 * 1024 // 14 GB threshold (safe before 15GB hard limit)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    /**
     * Get all linked admin drive accounts from Firestore ordered by priority.
     * Uses defensive field parsing so malformed Firestore documents never crash or disappear.
     */
    suspend fun getAllAccounts(): List<AdminDriveAccount> = withContext(Dispatchers.IO) {
        try {
            val snapshot = RepositoryManager.firestore.collection(COLLECTION_ACCOUNTS).get().await()
            snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(AdminDriveAccount::class.java)?.copy(
                        id = doc.id.ifBlank { doc.getString("id") ?: UUID.randomUUID().toString() }
                    )
                } catch (_: Exception) {
                    try {
                        val data = doc.data ?: return@mapNotNull null
                        AdminDriveAccount(
                            id = (data["id"] as? String)?.ifBlank { doc.id } ?: doc.id,
                            email = (data["email"] as? String) ?: "",
                            displayName = (data["displayName"] as? String) ?: "",
                            accessToken = (data["accessToken"] as? String) ?: "",
                            refreshToken = (data["refreshToken"] as? String) ?: "",
                            folderId = (data["folderId"] as? String) ?: "",
                            totalStorageBytes = (data["totalStorageBytes"] as? Number)?.toLong() ?: (15L * 1024 * 1024 * 1024),
                            usedStorageBytes = (data["usedStorageBytes"] as? Number)?.toLong() ?: 0L,
                            priorityOrder = (data["priorityOrder"] as? Number)?.toInt() ?: 1,
                            isActive = (data["isActive"] as? Boolean) ?: true,
                            isFull = (data["isFull"] as? Boolean) ?: false,
                            uploadedFilesCount = (data["uploadedFilesCount"] as? Number)?.toInt() ?: 0,
                            lastUsedTimestamp = (data["lastUsedTimestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
            }
                .filter { it.email.isNotBlank() }
                .distinctBy { it.email.trim().lowercase() }
                .sortedWith(compareBy({ !it.isActive }, { it.isFull }, { it.priorityOrder }))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load admin drive accounts", e)
            emptyList()
        }
    }

    /**
     * Link or update an Admin Drive Account in the pool (Supports up to 50+ accounts).
     * Deduplicates by email so re-linking an existing email updates it in place.
     */
    suspend fun linkOrUpdateAccount(account: AdminDriveAccount): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanEmail = account.email.trim()
            val existingAccounts = getAllAccounts()
            val matchingExisting = existingAccounts.firstOrNull {
                it.email.trim().equals(cleanEmail, ignoreCase = true)
            }
            val docId = when {
                matchingExisting != null && matchingExisting.id.isNotBlank() -> matchingExisting.id
                account.id.isNotBlank() -> account.id
                cleanEmail.isNotBlank() -> "drive_" + cleanEmail.lowercase().replace(Regex("[^a-z0-9]"), "_")
                else -> UUID.randomUUID().toString()
            }
            val finalAccount = account.copy(
                id = docId,
                email = cleanEmail,
                usedStorageBytes = if (account.usedStorageBytes > 0L) account.usedStorageBytes else (matchingExisting?.usedStorageBytes ?: 0L),
                uploadedFilesCount = if (account.uploadedFilesCount > 0) account.uploadedFilesCount else (matchingExisting?.uploadedFilesCount ?: 0)
            )
            RepositoryManager.firestore.collection(COLLECTION_ACCOUNTS)
                .document(docId)
                .set(finalAccount)
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save admin drive account", e)
            false
        }
    }

    /**
     * Synchronizes existing uploaded media count with the active Drive Pool account if pool stats are still zero.
     */
    suspend fun syncPoolStatsWithExistingMedia(totalMediaCount: Int) = withContext(Dispatchers.IO) {
        if (totalMediaCount <= 0) return@withContext
        try {
            val accounts = getAllAccounts()
            if (accounts.isEmpty()) return@withContext
            val totalPoolFiles = accounts.sumOf { it.uploadedFilesCount }
            val totalPoolBytes = accounts.sumOf { it.usedStorageBytes }
            if (totalPoolFiles == 0 && totalPoolBytes == 0L) {
                val primary = accounts.firstOrNull { it.isActive && !it.isFull } ?: accounts.first()
                // Average ~2.2 MB per social media photo/reel upload
                val estimatedBytes = totalMediaCount.toLong() * 2_306_867L
                RepositoryManager.firestore.collection(COLLECTION_ACCOUNTS)
                    .document(primary.id)
                    .update(
                        mapOf(
                            "uploadedFilesCount" to totalMediaCount,
                            "usedStorageBytes" to estimatedBytes,
                            "lastUsedTimestamp" to System.currentTimeMillis()
                        )
                    )
                    .await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not sync initial pool media stats", e)
        }
    }

    /**
     * Records a media upload's size and file count into the highest-priority active Admin Drive Pool account,
     * automatically rolling over to the next linked account if the current one reaches capacity.
     */
    suspend fun recordMediaUploadInPool(fileSizeBytes: Long) = withContext(Dispatchers.IO) {
        if (fileSizeBytes <= 0L) return@withContext
        try {
            val target = getNextAvailableAccount() ?: return@withContext
            updateAccountStats(target.id, fileSizeBytes)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to record media upload in pool", e)
        }
    }

    /**
     * Delete an account from pool
     */
    suspend fun removeAccount(accountId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            RepositoryManager.firestore.collection(COLLECTION_ACCOUNTS)
                .document(accountId)
                .delete()
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove account", e)
            false
        }
    }

    /**
     * Fetch real storage quota directly from Google Drive API.
     * Returns Pair(totalLimitBytes, usedBytes).
     * Automatically handles free (15GB), Google One (100GB, 2TB), and Workspace/Enterprise (5TB+) accounts!
     */
    suspend fun fetchAccountStorageQuota(token: String): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        try {
            if (token.isBlank() || token.startsWith("pool_linked_")) return@withContext null
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/about?fields=storageQuota")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()
            if (response.isSuccessful && body != null) {
                val json = JSONObject(body)
                val quota = json.optJSONObject("storageQuota")
                if (quota != null) {
                    val limitStr = quota.optString("limit", "")
                    val usageStr = quota.optString("usage", "")
                    val limitBytes = limitStr.toLongOrNull() ?: (15L * 1024 * 1024 * 1024)
                    val usedBytes = usageStr.toLongOrNull() ?: 0L
                    Log.d(TAG, "Fetched quota: Limit=${limitBytes / (1024 * 1024 * 1024)}GB, Used=${usedBytes / (1024 * 1024 * 1024)}GB")
                    return@withContext Pair(limitBytes, usedBytes)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching storage quota from Google Drive", e)
        }
        null
    }

    /**
     * Finds the next available non-full, active admin drive account.
     * Respects each account's real limit (15 GB, 100 GB, 2 TB, 5 TB, etc.).
     */
    suspend fun getNextAvailableAccount(): AdminDriveAccount? = withContext(Dispatchers.IO) {
        try {
            val accounts = getAllAccounts()
            accounts.firstOrNull { account ->
                val safeLimit = if (account.totalStorageBytes > 0) {
                    account.totalStorageBytes - (100L * 1024 * 1024)
                } else {
                    14L * 1024 * 1024 * 1024
                }
                account.isActive && !account.isFull && (account.usedStorageBytes < safeLimit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding next available account", e)
            null
        }
    }

    /**
     * Upload user media directly using the Admin Drive Storage Pool.
     * ZERO Google Sign-in needed for the normal user!
     * If current account fills up, it automatically marks it full and tries the next available account.
     */
    suspend fun uploadToAdminDrivePool(
        context: Context,
        fileUri: Uri,
        mimeType: String,
        onProgress: (Int) -> Unit = {}
    ): String? = withContext(Dispatchers.IO) {
        try {
            val accounts = getAllAccounts().filter { it.isActive && !it.isFull }
            if (accounts.isEmpty()) {
                Log.d(TAG, "No active admin drive accounts configured.")
                return@withContext null
            }

            val localPath = fileUri.path
            val inputStream = if (fileUri.scheme == "file" && localPath != null) {
                java.io.FileInputStream(File(localPath))
            } else {
                context.contentResolver.openInputStream(fileUri)
            } ?: return@withContext null
            val uriLower = fileUri.toString().lowercase()
            val isImg = mimeType.startsWith("image/") || uriLower.endsWith(".jpg") || uriLower.endsWith(".jpeg") || uriLower.endsWith(".png") || uriLower.endsWith(".webp") || uriLower.endsWith(".gif")
            val isVid = !isImg && (mimeType.startsWith("video/") || mimeType.contains("mp4") || uriLower.contains("video") || uriLower.endsWith(".mp4") || uriLower.endsWith(".mov") || uriLower.endsWith(".mkv"))
            val ext = if (isVid) ".mp4" else if (mimeType.startsWith("audio/")) ".mp3" else if (mimeType.contains("png") || uriLower.endsWith(".png")) ".png" else if (mimeType.contains("webp") || uriLower.endsWith(".webp")) ".webp" else if (mimeType.contains("gif") || uriLower.endsWith(".gif")) ".gif" else ".jpg"
            val tempFile = File(context.cacheDir, "admin_pool_${System.currentTimeMillis()}$ext")
            inputStream.use { input ->
                FileOutputStream(tempFile).use { out ->
                    input.copyTo(out)
                }
            }
            val fileSize = tempFile.length()

            try {
                // Try accounts in priority order until one succeeds
                for (targetAccount in accounts) {
                    val safeLimit = if (targetAccount.totalStorageBytes > 0) {
                        targetAccount.totalStorageBytes - (100L * 1024 * 1024)
                    } else {
                        14L * 1024 * 1024 * 1024
                    }
                    // If this account will exceed safe threshold, mark as full and cascade to next
                    if (targetAccount.usedStorageBytes + fileSize >= safeLimit) {
                        markAccountFull(targetAccount.id, true)
                        continue
                    }

                    val uploadedUrl = uploadFileToSpecificAccount(tempFile, targetAccount, mimeType, isVid, ext)
                    if (uploadedUrl != null) {
                        // Update stats
                        updateAccountStats(targetAccount.id, fileSize)
                        onProgress(100)
                        return@withContext uploadedUrl
                    }
                }
            } finally {
                try { tempFile.delete() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in uploadToAdminDrivePool", e)
        }
        return@withContext null
    }

    private suspend fun uploadFileToSpecificAccount(
        tempFile: File,
        account: AdminDriveAccount,
        mimeType: String,
        isVid: Boolean,
        ext: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val token = account.accessToken.ifEmpty { account.refreshToken }
            if (token.isBlank()) return@withContext null

            val metadata = JSONObject().apply {
                put("name", "SocialIndia_${System.currentTimeMillis()}$ext")
                if (account.folderId.isNotBlank()) {
                    val parents = org.json.JSONArray()
                    parents.put(account.folderId)
                    put("parents", parents)
                }
            }

            val actualMime = when {
                isVid && (mimeType == "application/octet-stream" || mimeType.isEmpty()) -> "video/mp4"
                !isVid && (mimeType == "application/octet-stream" || mimeType.isEmpty()) -> "image/jpeg"
                else -> mimeType
            }
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("metadata", null, metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull()))
                .addFormDataPart("file", tempFile.name, tempFile.asRequestBody(actualMime.toMediaTypeOrNull()))
                .build()

            val request = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,webViewLink")
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val fileId = json.getString("id")

                // Make file public read and verify confirmation
                val confirmed = GoogleDrivePermissionRepairManager.ensurePublicPermission(fileId, token)
                if (!confirmed) {
                    Log.w(TAG, "Pool permission confirmation timed out for $fileId. Triggering async repair.")
                    GoogleDrivePermissionRepairManager.repairPermissionAsync(fileId)
                }

                val isImage = !isVid && (ext == ".jpg" || ext == ".png" || ext == ".webp" || ext == ".gif" || actualMime.startsWith("image/"))
                if (isImage) {
                    return@withContext "https://drive.google.com/thumbnail?id=$fileId&sz=w1080"
                }
                return@withContext "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t&ext=$ext"
            } else {
                Log.e(TAG, "Drive Pool file upload HTTP fail: ${response.code} - $responseBody")
                // Check if quota exceeded error from Google Drive
                if (responseBody?.contains("quota", ignoreCase = true) == true ||
                    responseBody?.contains("storage", ignoreCase = true) == true) {
                    markAccountFull(account.id, true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception uploading to account ${account.email}", e)
        }
        return@withContext null
    }

    private suspend fun markAccountFull(accountId: String, isFull: Boolean) {
        try {
            RepositoryManager.firestore.collection(COLLECTION_ACCOUNTS)
                .document(accountId)
                .update(mapOf("isFull" to isFull))
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update full status", e)
        }
    }

    private suspend fun updateAccountStats(accountId: String, additionalBytes: Long) {
        try {
            val docRef = RepositoryManager.firestore.collection(COLLECTION_ACCOUNTS).document(accountId)
            RepositoryManager.firestore.runTransaction { tx ->
                val snapshot = tx.get(docRef)
                val currentBytes = snapshot.getLong("usedStorageBytes") ?: 0L
                val currentCount = snapshot.getLong("uploadedFilesCount")?.toInt() ?: 0
                val totalCapacity = snapshot.getLong("totalStorageBytes") ?: (15L * 1024 * 1024 * 1024)
                val safeLimit = if (totalCapacity > 0) totalCapacity - (100L * 1024 * 1024) else (14L * 1024 * 1024 * 1024)
                val newBytes = currentBytes + additionalBytes
                val isNowFull = newBytes >= safeLimit

                tx.update(
                    docRef,
                    mapOf(
                        "usedStorageBytes" to newBytes,
                        "uploadedFilesCount" to (currentCount + 1),
                        "lastUsedTimestamp" to System.currentTimeMillis(),
                        "isFull" to isNowFull
                    )
                )
            }.await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update stats", e)
        }
    }
}
