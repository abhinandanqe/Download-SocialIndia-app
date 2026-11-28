package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
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
import java.util.concurrent.TimeUnit

class DriveAuthException(val intent: Intent?) : Exception("Needs Drive Auth") { 
// init removed
    init {

    }
}

object GoogleDriveManager {
    private const val TAG = "GoogleDriveManager"
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun uploadUri(context: Context, uri: Uri, mimeType: String): String? = withContext(Dispatchers.IO) {
        val driveScope = Scope("https://www.googleapis.com/auth/drive.file")
        
        try {
            // 1. Get GoogleSignInAccount if available
            var googleAccount = GoogleSignIn.getLastSignedInAccount(context)
            
            // 2. If no account or missing scope, we MUST prompt the user with GoogleSignIn
            if (googleAccount == null || !GoogleSignIn.hasPermissions(googleAccount, driveScope)) {
                Log.e(TAG, "No GoogleSignInAccount or missing Drive scope. Requesting intent.")
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestScopes(driveScope)
                    .build()
                val signInClient = GoogleSignIn.getClient(context, gso)
                throw DriveAuthException(signInClient.signInIntent)
            }
            
            // 3. Extract the OS-level Account object
            val account = googleAccount.account
            if (account == null) {
                Log.e(TAG, "Account is null inside GoogleSignInAccount.")
                return@withContext null
            }

            // 4. Now that we have a valid, system-registered Account, get the token
            val token = try {
                GoogleAuthUtil.getToken(context, account, "oauth2:https://www.googleapis.com/auth/drive.file")
            } catch (e: UserRecoverableAuthException) {
                Log.e(TAG, "Needs user permission for Drive (UserRecoverable)")
                throw DriveAuthException(e.intent)
            } catch (e: Exception) {
                Log.e(TAG, "Auth failed: ${e.message}", e)
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestScopes(driveScope)
                    .build()
                val signInClient = GoogleSignIn.getClient(context, gso)
                throw DriveAuthException(signInClient.signInIntent)
            }

            val localPath = uri.path
            val inputStream = if (uri.scheme == "file" && localPath != null) {
                java.io.FileInputStream(File(localPath))
            } else {
                context.contentResolver.openInputStream(uri)
            } ?: return@withContext null
            val tempFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}")
            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val metadata = JSONObject()
            val uriLower = uri.toString().lowercase()
            val isImg = mimeType.startsWith("image/") || uriLower.endsWith(".jpg") || uriLower.endsWith(".jpeg") || uriLower.endsWith(".png") || uriLower.endsWith(".webp") || uriLower.endsWith(".gif")
            val isVid = !isImg && (mimeType.startsWith("video/") || mimeType.contains("mp4") || uriLower.contains("video") || uriLower.endsWith(".mp4") || uriLower.endsWith(".mov") || uriLower.endsWith(".mkv"))
            val ext = if (isVid) ".mp4" else if (mimeType.startsWith("audio/")) ".mp3" else if (mimeType.contains("png") || uriLower.endsWith(".png")) ".png" else if (mimeType.contains("webp") || uriLower.endsWith(".webp")) ".webp" else if (mimeType.contains("gif") || uriLower.endsWith(".gif")) ".gif" else ".jpg"
            metadata.put("name", "SocialIndia_Upload_${System.currentTimeMillis()}$ext")
            
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

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            try { tempFile.delete() } catch (_: Exception) {}
            
            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val fileId = json.getString("id")
                
                // Confirm public 'anyone' reader permission before returning
                val permissionConfirmed = GoogleDrivePermissionRepairManager.ensurePublicPermission(fileId, token)
                if (!permissionConfirmed) {
                    Log.w(TAG, "Public permission confirmation timed out for $fileId. Triggering async repair.")
                    GoogleDrivePermissionRepairManager.repairPermissionAsync(fileId)
                }
                
                val isImage = !isVid && (ext == ".jpg" || ext == ".png" || ext == ".webp" || ext == ".gif" || actualMime.startsWith("image/"))
                if (isImage) {
                    return@withContext "https://drive.google.com/thumbnail?id=$fileId&sz=w1080"
                }
                return@withContext "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t&ext=$ext"
            } else {
                Log.e(TAG, "Upload failed: $responseBody")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "GMS Security Exception (Emulator limitation)", e)
            throw Exception("Google Play Services error. Please test on a real Android device.")
        } catch (e: DriveAuthException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Upload Exception", e)
        }
        return@withContext null
    }

    data class DriveMediaResult(
        val fileId: String,
        val mediaUrl: String,
        val mimeType: String,
        val fileName: String
    )

    /**
     * Uploads sticker media (PNG, WEBP, GIF, animated) directly to Google Drive.
     * Sets proper permissions and returns direct accessible Drive CDN URL.
     */
    suspend fun uploadStickerMedia(
        context: Context,
        uri: Uri,
        packId: String,
        suggestedName: String,
        mimeType: String
    ): DriveMediaResult? = withContext(Dispatchers.IO) {
        val driveScope = Scope("https://www.googleapis.com/auth/drive.file")
        try {
            val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
            if (googleAccount == null || !GoogleSignIn.hasPermissions(googleAccount, driveScope)) {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestScopes(driveScope)
                    .build()
                val signInClient = GoogleSignIn.getClient(context, gso)
                throw DriveAuthException(signInClient.signInIntent)
            }

            val account = googleAccount.account ?: return@withContext null
            val token = GoogleAuthUtil.getToken(context, account, "oauth2:https://www.googleapis.com/auth/drive.file")

            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val tempFile = File(context.cacheDir, "sticker_${System.currentTimeMillis()}")
            val outputStream = FileOutputStream(tempFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()

            val isGif = mimeType.contains("gif", ignoreCase = true) || uri.toString().contains(".gif", ignoreCase = true)
            val isWebp = mimeType.contains("webp", ignoreCase = true) || uri.toString().contains(".webp", ignoreCase = true)
            val ext = if (isGif) ".gif" else if (isWebp) ".webp" else ".png"
            val actualMime = if (isGif) "image/gif" else if (isWebp) "image/webp" else "image/png"
            val sanitizedName = if (suggestedName.endsWith(ext)) suggestedName else "$suggestedName$ext"

            val metadata = JSONObject().apply {
                put("name", "Sticker_Pack_${packId}_$sanitizedName")
                put("description", "Social India Sticker Media")
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

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val fileId = json.getString("id")

                // Grant public read permission to this specific sticker file
                GoogleDrivePermissionRepairManager.ensurePublicPermission(fileId, token)

                val directMediaUrl = "https://drive.google.com/thumbnail?id=$fileId&sz=w1080"
                return@withContext DriveMediaResult(
                    fileId = fileId,
                    mediaUrl = directMediaUrl,
                    mimeType = actualMime,
                    fileName = sanitizedName
                )
            } else {
                Log.e(TAG, "Sticker upload failed: $responseBody")
            }
        } catch (e: DriveAuthException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Sticker upload error", e)
        }
        return@withContext null
    }
}
