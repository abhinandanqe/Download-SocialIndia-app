package com.example.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.example.BuildConfig
import com.example.model.ApkPackageMetadata
import com.example.model.RepositoryManager
import com.example.model.UpdateHistoryRecord
import com.example.model.UpdateInfo
import com.example.model.UserUpdateStatus
import com.example.repository.MediaRepository
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * AppUpdateManager
 *
 * End-to-end manager for the Admin-Controlled Automatic APK Update System:
 * 1. Admin APK file inspection & direct Google Drive upload with auto-public permissions.
 * 2. Real-time Firebase `app_config/update_info` and `app_update_history` management.
 * 3. Per-authenticated-user update status tracking (`user_update_status/{uid}` & `users/{uid}`).
 * 4. Real byte-by-byte APK downloader with Google Drive virus-scan confirmation bypass.
 * 5. Official Android Package Installer launch & post-install version verification.
 */
object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    const val COLLECTION_APP_CONFIG = "app_config"
    const val DOC_UPDATE_INFO = "update_info"
    const val COLLECTION_UPDATE_HISTORY = "app_update_history"
    const val COLLECTION_USER_UPDATE_STATUS = "user_update_status"

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun getInstalledVersionCode(context: Context): Int {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            PackageInfoCompat.getLongVersionCode(pInfo).toInt()
        } catch (e: Exception) {
            BuildConfig.VERSION_CODE
        }
    }

    fun getInstalledVersionName(context: Context): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: BuildConfig.VERSION_NAME
        } catch (e: Exception) {
            BuildConfig.VERSION_NAME
        }
    }

    fun getCurrentDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    fun getCurrentTimeString(): String {
        return SimpleDateFormat("hh:mm:ss a", Locale.US).format(Date())
    }

    /**
     * Inspects a local .apk file or URI to extract its real packageName, versionName, and versionCode.
     */
    suspend fun inspectApkUri(context: Context, uri: Uri): ApkPackageMetadata? = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            tempFile = File(updatesDir, "inspect_${System.currentTimeMillis()}.apk")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (!tempFile.exists() || tempFile.length() <= 0L) return@withContext null
            inspectApkFile(context, tempFile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inspect APK URI", e)
            null
        } finally {
            try { tempFile?.delete() } catch (_: Exception) {}
        }
    }

    fun inspectApkFile(context: Context, apkFile: File): ApkPackageMetadata? {
        return try {
            if (!apkFile.exists() || apkFile.length() < 100L) return null
            val pm = context.packageManager
            val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            } ?: return null

            val pkgName = archiveInfo.packageName ?: ""
            val vName = archiveInfo.versionName ?: "1.0"
            val vCode = PackageInfoCompat.getLongVersionCode(archiveInfo).toInt()
            val sizeMb = (apkFile.length().toDouble() / (1024.0 * 1024.0))
            val roundedMb = Math.round(sizeMb * 100.0) / 100.0

            ApkPackageMetadata(
                packageName = pkgName,
                versionName = vName,
                versionCode = vCode,
                fileSizeMb = roundedMb,
                isCompatiblePackage = pkgName.isEmpty() || pkgName == context.packageName
            )
        } catch (e: Exception) {
            Log.e(TAG, "inspectApkFile error", e)
            null
        }
    }

    /**
     * Parses a Firestore DocumentSnapshot safely into UpdateInfo.
     */
    fun parseUpdateInfo(doc: com.google.firebase.firestore.DocumentSnapshot?): UpdateInfo? {
        if (doc == null || !doc.exists()) return null
        return try {
            val vCode = (doc.getLong("versionCode") ?: doc.getDouble("versionCode")?.toLong() ?: 1L).toInt()
            val vName = doc.getString("versionName") ?: "1.0"
            val url = doc.getString("url") ?: ""
            val notes = doc.getString("notes") ?: ""
            val message = doc.getString("updateMessage")
                ?: "A new version of Social India is available!"
            val releaseDate = doc.getString("releaseDate") ?: ""
            val mandatory = doc.getBoolean("mandatory") ?: false
            val popupEnabled = doc.getBoolean("popupEnabled") ?: true
            val driveFileId = doc.getString("driveFileId") ?: ""
            val fileSizeMb = doc.getDouble("fileSizeMb") ?: (doc.getLong("fileSizeMb")?.toDouble() ?: 0.0)
            val releasedAt = doc.getLong("releasedAtMillis") ?: 0L
            val releasedBy = doc.getString("releasedByEmail") ?: ""
            val githubRepo = normalizeGitHubRepo(doc.getString("githubRepo") ?: "").ifBlank {
                getDefaultGitHubRepo()
            }
            val githubAutoSyncEnabled = doc.getBoolean("githubAutoSyncEnabled") ?: true
            val source = doc.getString("source") ?: "FIREBASE"
            val githubReleaseTag = doc.getString("githubReleaseTag") ?: ""
            val githubHtmlUrl = doc.getString("githubHtmlUrl") ?: ""

            UpdateInfo(
                versionCode = vCode,
                versionName = vName,
                url = url,
                notes = notes,
                updateMessage = message,
                releaseDate = releaseDate,
                mandatory = mandatory,
                popupEnabled = popupEnabled,
                driveFileId = driveFileId,
                fileSizeMb = fileSizeMb,
                releasedAtMillis = releasedAt,
                releasedByEmail = releasedBy,
                githubRepo = githubRepo,
                githubAutoSyncEnabled = githubAutoSyncEnabled,
                source = source,
                githubReleaseTag = githubReleaseTag,
                githubHtmlUrl = githubHtmlUrl
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing UpdateInfo", e)
            null
        }
    }

    fun getDefaultGitHubRepo(): String {
        return try {
            val raw = BuildConfig.GITHUB_UPDATE_REPO.trim()
            if (raw.isBlank() || raw.startsWith("YOUR_", ignoreCase = true)) "" else normalizeGitHubRepo(raw)
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * Normalizes inputs like "owner/repo", "https://github.com/owner/repo", or
     * "https://github.com/owner/repo/releases" into canonical "owner/repo".
     */
    fun normalizeGitHubRepo(rawInput: String): String {
        val trimmed = rawInput.trim().removeSuffix("/").removeSuffix(".git")
        if (trimmed.isBlank()) return ""
        val withoutScheme = trimmed
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removePrefix("https://api.github.com/repos/")
            .removePrefix("/")
        val parts = withoutScheme.split("/").filter { it.isNotBlank() }
        if (parts.size < 2) return ""
        val owner = parts[0].trim()
        val repo = parts[1].trim()
        val validRegex = Regex("^[A-Za-z0-9_.-]+$")
        return if (validRegex.matches(owner) && validRegex.matches(repo)) "$owner/$repo" else ""
    }

    /**
     * Parses a GitHub `/releases/latest` JSON payload (and optional `update-manifest.json`)
     * into an [UpdateInfo] ready for auto-popup & APK download.
     */
    fun parseGitHubReleaseJson(
        releaseJson: String,
        repoSlug: String,
        manifestJson: String? = null,
        baseConfig: UpdateInfo? = null
    ): UpdateInfo? {
        if (releaseJson.isBlank()) return null
        return try {
            val root = Json.parseToJsonElement(releaseJson).jsonObject
            if (root["draft"]?.jsonPrimitive?.booleanOrNull == true) return null

            val tagName = root["tag_name"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
            val releaseTitle = root["name"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
            val releaseBody = root["body"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
            val htmlUrl = root["html_url"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
            val publishedAt = root["published_at"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""

            val assets = (root["assets"] as? JsonArray) ?: JsonArray(emptyList())
            var apkUrl = ""
            var apkAssetName = ""
            var apkSizeBytes = 0L

            for (element in assets) {
                val asset = element as? JsonObject ?: continue
                val name = asset["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val dlUrl = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: ""
                if (name.endsWith(".apk", ignoreCase = true) && dlUrl.isNotBlank()) {
                    apkUrl = dlUrl
                    apkAssetName = name
                    apkSizeBytes = asset["size"]?.jsonPrimitive?.longOrNull ?: 0L
                    break
                }
            }
            if (apkUrl.isBlank()) return null

            val manifestObj = if (!manifestJson.isNullOrBlank()) {
                try { Json.parseToJsonElement(manifestJson) as? JsonObject } catch (_: Exception) { null }
            } else null

            // Extract versionCode from manifest, tag (`-c101` / `-code101`), APK filename (`-code101.apk`), or body
            val tagCodeRegex = Regex("(?:-c|-code|code|build-)(\\d+)", RegexOption.IGNORE_CASE)
            val tagVersionRegex = Regex("v?(\\d+(?:\\.\\d+)+)", RegexOption.IGNORE_CASE)

            val parsedCodeFromTag = tagCodeRegex.find(tagName)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: tagCodeRegex.find(apkAssetName)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: tagCodeRegex.find(releaseTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: Regex("versionCode\\s*[:=]\\s*(\\d+)", RegexOption.IGNORE_CASE)
                    .find(releaseBody)?.groupValues?.getOrNull(1)?.toIntOrNull()

            val versionCode = (manifestObj?.get("versionCode")?.jsonPrimitive?.intOrNull ?: 0)
                .takeIf { it > 0 }
                ?: parsedCodeFromTag
                ?: 1

            val versionName = manifestObj?.get("versionName")?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }
                ?: tagVersionRegex.find(tagName)?.groupValues?.getOrNull(1)
                ?: tagVersionRegex.find(apkAssetName)?.groupValues?.getOrNull(1)
                ?: tagName.removePrefix("v").ifBlank { "1.0" }

            val sizeMb = manifestObj?.get("fileSizeMb")?.jsonPrimitive?.doubleOrNull?.takeIf { it > 0.0 }
                ?: if (apkSizeBytes > 0L) {
                    Math.round((apkSizeBytes.toDouble() / (1024.0 * 1024.0)) * 100.0) / 100.0
                } else 0.0

            val releaseDate = manifestObj?.get("releaseDate")?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }
                ?: publishedAt.take(10).ifBlank { getCurrentDateString() }

            val notes = manifestObj?.get("notes")?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }
                ?: releaseBody.ifBlank { "• Official Social India v$versionName update from GitHub Releases" }

            val message = manifestObj?.get("updateMessage")?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }
                ?: "Social India v$versionName is available! Tap Download Update to install automatically."

            val mandatory = manifestObj?.get("mandatory")?.jsonPrimitive?.booleanOrNull
                ?: (releaseBody.contains("[FORCE_UPDATE]", ignoreCase = true) ||
                    (baseConfig?.mandatory == true && baseConfig.versionCode == versionCode))

            val popupEnabled = manifestObj?.get("popupEnabled")?.jsonPrimitive?.booleanOrNull ?: true

            UpdateInfo(
                versionCode = versionCode,
                versionName = versionName,
                url = apkUrl,
                notes = notes,
                updateMessage = message,
                releaseDate = releaseDate,
                mandatory = mandatory,
                popupEnabled = popupEnabled,
                driveFileId = "",
                fileSizeMb = sizeMb,
                releasedAtMillis = System.currentTimeMillis(),
                releasedByEmail = baseConfig?.releasedByEmail ?: "",
                githubRepo = repoSlug,
                githubAutoSyncEnabled = baseConfig?.githubAutoSyncEnabled ?: true,
                source = "GITHUB_RELEASE",
                githubReleaseTag = tagName,
                githubHtmlUrl = htmlUrl
            )
        } catch (e: Exception) {
            try { Log.e(TAG, "parseGitHubReleaseJson error", e) } catch (_: Throwable) {}
            null
        }
    }

    /**
     * Fetches the latest public GitHub Release for `owner/repo` and reads optional `update-manifest.json`.
     */
    suspend fun fetchLatestGitHubRelease(
        repoInput: String,
        baseConfig: UpdateInfo? = null
    ): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        val repoSlug = normalizeGitHubRepo(repoInput)
        if (repoSlug.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter a valid GitHub repo like owner/repo"))
        }
        try {
            val apiReq = Request.Builder()
                .url("https://api.github.com/repos/$repoSlug/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "SocialIndiaUpdater/1.0")
                .get()
                .build()
            val apiResp = httpClient.newCall(apiReq).execute()
            val apiBody = apiResp.body?.string().orEmpty()
            val statusCode = apiResp.code
            apiResp.close()

            if (statusCode !in 200..299 || apiBody.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("GitHub API returned HTTP $statusCode for $repoSlug")
                )
            }

            // Look for update-manifest.json asset to fetch exact versionCode / notes / mandatory flag
            var manifestBody: String? = null
            try {
                val root = JSONObject(apiBody)
                val assets = root.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.optJSONObject(i) ?: continue
                        if (a.optString("name").equals("update-manifest.json", ignoreCase = true)) {
                            val mUrl = a.optString("browser_download_url", "")
                            if (mUrl.isNotBlank()) {
                                val mReq = Request.Builder()
                                    .url(mUrl)
                                    .header("User-Agent", "SocialIndiaUpdater/1.0")
                                    .get()
                                    .build()
                                httpClient.newCall(mReq).execute().use { mResp ->
                                    if (mResp.isSuccessful) {
                                        manifestBody = mResp.body?.string()
                                    }
                                }
                            }
                            break
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Non-fatal: fallback to release tag/asset metadata
            }

            val parsed = parseGitHubReleaseJson(
                releaseJson = apiBody,
                repoSlug = repoSlug,
                manifestJson = manifestBody,
                baseConfig = baseConfig
            ) ?: return@withContext Result.failure(
                IllegalStateException("Latest GitHub Release in $repoSlug has no .apk asset attached yet.")
            )
            Result.success(parsed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "fetchLatestGitHubRelease failed for $repoSlug: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Resolves the active update shown to users by combining Firebase `app_config/update_info`
     * with the latest public GitHub Release when `githubAutoSyncEnabled` is ON.
     */
    fun resolveEffectiveUpdate(
        firebaseConfig: UpdateInfo?,
        githubConfig: UpdateInfo?,
        installedVersionCode: Int
    ): UpdateInfo? {
        val autoSync = firebaseConfig?.githubAutoSyncEnabled ?: true
        if (githubConfig != null && githubConfig.url.isNotBlank() && autoSync) {
            val fbCode = firebaseConfig?.versionCode ?: 0
            if (githubConfig.versionCode > fbCode) {
                return githubConfig
            }
            if (githubConfig.versionCode == fbCode && firebaseConfig != null) {
                return if (firebaseConfig.url.isNotBlank()) firebaseConfig else githubConfig.copy(
                    popupEnabled = firebaseConfig.popupEnabled,
                    mandatory = firebaseConfig.mandatory
                )
            }
        }
        return firebaseConfig
    }

    /**
     * Saves the GitHub Releases repository (`owner/repo`) and auto-sync toggle into
     * `app_config/update_info` so all public devices check it automatically on launch.
     */
    suspend fun saveGitHubAutoSyncConfig(
        repoInput: String,
        enabled: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val normalized = normalizeGitHubRepo(repoInput)
        try {
            RepositoryManager.firestore.collection(COLLECTION_APP_CONFIG)
                .document(DOC_UPDATE_INFO)
                .set(
                    mapOf(
                        "githubRepo" to normalized,
                        "githubAutoSyncEnabled" to enabled
                    ),
                    SetOptions.merge()
                )
                .await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save GitHub auto-sync config", e)
            false
        }
    }

    /**
     * Uploads an APK file selected by the Admin directly to Google Drive,
     * automatically sets public 'anyone' reader permission, and returns the direct download URL.
     */
    suspend fun uploadApkToGoogleDrive(
        context: Context,
        apkUri: Uri,
        versionName: String,
        onProgress: (Int) -> Unit = {}
    ): Pair<String, Double>? = withContext(Dispatchers.IO) {
        onProgress(5)
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val cleanVersion = versionName.trim().ifEmpty { "latest" }.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val tempApkFile = File(updatesDir, "SocialIndia_v${cleanVersion}_${System.currentTimeMillis()}.apk")

        try {
            context.contentResolver.openInputStream(apkUri)?.use { input ->
                FileOutputStream(tempApkFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (!tempApkFile.exists() || tempApkFile.length() <= 0L) {
                return@withContext null
            }
            val fileSizeBytes = tempApkFile.length()
            val fileSizeMb = Math.round((fileSizeBytes.toDouble() / (1024.0 * 1024.0)) * 100.0) / 100.0
            val apkMimeType = "application/vnd.android.package-archive"

            onProgress(15)

            // 1. Try Admin Drive Storage Pool accounts first
            try {
                val accounts = AdminDriveStoragePoolManager.getAllAccounts().filter { it.isActive && !it.isFull }
                for (account in accounts) {
                    val token = account.accessToken.ifEmpty { account.refreshToken }
                    if (token.isNotBlank() && !token.startsWith("pool_linked_")) {
                        val uploadedUrl = uploadApkFileWithDriveToken(
                            apkFile = tempApkFile,
                            token = token,
                            folderId = account.folderId,
                            versionName = cleanVersion,
                            onProgress = onProgress
                        )
                        if (!uploadedUrl.isNullOrEmpty()) {
                            AdminDriveStoragePoolManager.recordMediaUploadInPool(fileSizeBytes)
                            onProgress(100)
                            return@withContext Pair(uploadedUrl, fileSizeMb)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Admin Drive Pool APK upload attempt skipped: ${e.message}")
            }

            // 2. Try signed-in Admin Google Account Drive API
            try {
                val driveScope = Scope("https://www.googleapis.com/auth/drive.file")
                val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
                if (googleAccount != null && GoogleSignIn.hasPermissions(googleAccount, driveScope)) {
                    val osAccount = googleAccount.account
                    if (osAccount != null) {
                        val token = GoogleAuthUtil.getToken(
                            context,
                            osAccount,
                            "oauth2:https://www.googleapis.com/auth/drive.file"
                        )
                        if (!token.isNullOrBlank()) {
                            val uploadedUrl = uploadApkFileWithDriveToken(
                                apkFile = tempApkFile,
                                token = token,
                                folderId = "",
                                versionName = cleanVersion,
                                onProgress = onProgress
                            )
                            if (!uploadedUrl.isNullOrEmpty()) {
                                onProgress(100)
                                return@withContext Pair(uploadedUrl, fileSizeMb)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Direct GoogleSignIn Drive APK upload skipped: ${e.message}")
            }

            // 3. Fallback to multi-tier MediaRepository cloud storage so Admin upload never fails
            onProgress(35)
            val fallbackUrl = MediaRepository(context).uploadMedia(apkUri) { p ->
                onProgress((35 + (p * 0.65).toInt()).coerceIn(35, 100))
            }
            if (!fallbackUrl.isNullOrBlank()) {
                onProgress(100)
                return@withContext Pair(fallbackUrl, fileSizeMb)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "uploadApkToGoogleDrive failed", e)
        } finally {
            try { tempApkFile.delete() } catch (_: Exception) {}
        }
        null
    }

    private suspend fun uploadApkFileWithDriveToken(
        apkFile: File,
        token: String,
        folderId: String,
        versionName: String,
        onProgress: (Int) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        try {
            onProgress(30)
            val metadata = JSONObject().apply {
                put("name", "SocialIndia_v${versionName}.apk")
                put("description", "Social India Official Android APK Update v$versionName")
                if (folderId.isNotBlank()) {
                    val parents = JSONArray()
                    parents.put(folderId)
                    put("parents", parents)
                }
            }

            val apkMime = "application/vnd.android.package-archive"
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "metadata",
                    null,
                    metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull())
                )
                .addFormDataPart(
                    "file",
                    apkFile.name,
                    apkFile.asRequestBody(apkMime.toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,webViewLink")
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            onProgress(65)
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
            if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
                val json = JSONObject(responseBody)
                val fileId = json.getString("id")
                onProgress(85)

                // Ensure public 'anyone' reader permission on the uploaded APK file
                val confirmed = GoogleDrivePermissionRepairManager.ensurePublicPermission(fileId, token)
                if (!confirmed) {
                    GoogleDrivePermissionRepairManager.repairPermissionAsync(fileId)
                }
                return@withContext "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"
            } else {
                Log.e(TAG, "Drive APK upload HTTP ${response.code}: $responseBody")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "uploadApkFileWithDriveToken error", e)
        }
        null
    }

    /**
     * Normalizes any Google Drive or direct URL into a binary APK download URL.
     */
    fun resolveApkDownloadUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""
        val driveFileId = MediaResolver.extractGoogleDriveFileId(trimmed)
        if (!driveFileId.isNullOrEmpty()) {
            return "https://drive.usercontent.google.com/download?id=$driveFileId&export=download&confirm=t"
        }
        return trimmed
    }

    /**
     * Publishes a new APK update config to `app_config/update_info` and logs it to `app_update_history`.
     */
    suspend fun publishAppUpdate(updateInfo: UpdateInfo): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = RepositoryManager.firestore
            val now = System.currentTimeMillis()
            val releaseDate = updateInfo.releaseDate.ifBlank { getCurrentDateString() }
            val adminEmail = FirebaseAuth.getInstance().currentUser?.email ?: "admin@socialindia.app"
            val normalizedUrl = resolveApkDownloadUrl(updateInfo.url)
            val extractedDriveId = MediaResolver.extractGoogleDriveFileId(normalizedUrl) ?: updateInfo.driveFileId

            val finalInfo = updateInfo.copy(
                url = normalizedUrl,
                releaseDate = releaseDate,
                driveFileId = extractedDriveId,
                releasedAtMillis = now,
                releasedByEmail = adminEmail
            )

            // 1. Save active update config in app_config/update_info
            val normalizedGitHubRepo = normalizeGitHubRepo(finalInfo.githubRepo).ifBlank { getDefaultGitHubRepo() }
            val updateMap = mutableMapOf<String, Any>(
                "versionCode" to finalInfo.versionCode,
                "versionName" to finalInfo.versionName,
                "url" to finalInfo.url,
                "notes" to finalInfo.notes,
                "updateMessage" to finalInfo.updateMessage,
                "releaseDate" to finalInfo.releaseDate,
                "mandatory" to finalInfo.mandatory,
                "popupEnabled" to finalInfo.popupEnabled,
                "driveFileId" to finalInfo.driveFileId,
                "fileSizeMb" to finalInfo.fileSizeMb,
                "releasedAtMillis" to finalInfo.releasedAtMillis,
                "releasedByEmail" to finalInfo.releasedByEmail,
                "githubAutoSyncEnabled" to finalInfo.githubAutoSyncEnabled,
                "source" to finalInfo.source.ifBlank { "FIREBASE" },
                "githubReleaseTag" to finalInfo.githubReleaseTag,
                "githubHtmlUrl" to finalInfo.githubHtmlUrl
            )
            if (normalizedGitHubRepo.isNotBlank()) {
                updateMap["githubRepo"] = normalizedGitHubRepo
            }
            db.collection(COLLECTION_APP_CONFIG)
                .document(DOC_UPDATE_INFO)
                .set(updateMap, SetOptions.merge())
                .await()

            // 2. Append to app_update_history
            val historyId = "v${finalInfo.versionCode}_${now}"
            val historyRecord = UpdateHistoryRecord(
                id = historyId,
                versionCode = finalInfo.versionCode,
                versionName = finalInfo.versionName,
                url = finalInfo.url,
                releaseDate = finalInfo.releaseDate,
                updateMessage = finalInfo.updateMessage,
                notes = finalInfo.notes,
                mandatory = finalInfo.mandatory,
                popupEnabled = finalInfo.popupEnabled,
                fileSizeMb = finalInfo.fileSizeMb,
                releasedAtMillis = now,
                releasedBy = adminEmail
            )
            db.collection(COLLECTION_UPDATE_HISTORY)
                .document(historyId)
                .set(historyRecord)
                .await()

            // 3. Update existing user_update_status documents so latestVersion is synced across all users
            try {
                val statusSnap = db.collection(COLLECTION_USER_UPDATE_STATUS).get().await()
                for (doc in statusSnap.documents) {
                    val userInstalledCode = (doc.getLong("installedVersionCode") ?: doc.getLong("currentVersionCode") ?: 1L).toInt()
                    val isAlreadyOnNewVersion = userInstalledCode >= finalInfo.versionCode
                    val newStatus = if (isAlreadyOnNewVersion) "INSTALLED" else "PENDING"
                    doc.reference.set(
                        mapOf(
                            "latestVersion" to finalInfo.versionName,
                            "latestVersionCode" to finalInfo.versionCode,
                            "downloadStatus" to newStatus
                        ),
                        SetOptions.merge()
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Non-fatal error syncing user_update_status batch", e)
            }

            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish app update", e)
            false
        }
    }

    /**
     * Toggles the Update Popup ON/OFF in Firebase `app_config/update_info`.
     */
    suspend fun setPopupEnabled(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            RepositoryManager.firestore.collection(COLLECTION_APP_CONFIG)
                .document(DOC_UPDATE_INFO)
                .set(mapOf("popupEnabled" to enabled), SetOptions.merge())
                .await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle popupEnabled", e)
            false
        }
    }

    /**
     * Toggles Optional Update (false) vs Force Update (true) in Firebase `app_config/update_info`.
     */
    suspend fun setMandatoryUpdate(mandatory: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            RepositoryManager.firestore.collection(COLLECTION_APP_CONFIG)
                .document(DOC_UPDATE_INFO)
                .set(mapOf("mandatory" to mandatory), SetOptions.merge())
                .await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle mandatory flag", e)
            false
        }
    }

    /**
     * Synchronizes the authenticated user's current installed version & update completion status in Firebase.
     * Returns true if the user's installed version is already >= updateInfo.versionCode (no update needed).
     */
    suspend fun syncUserUpdateStatusOnLaunch(
        context: Context,
        updateInfo: UpdateInfo?
    ): Boolean = withContext(Dispatchers.IO) {
        val installedCode = getInstalledVersionCode(context)
        val installedName = getInstalledVersionName(context)
        val uid = RepositoryManager.getCurrentUserId()
        if (uid.isEmpty()) {
            return@withContext updateInfo == null || installedCode >= updateInfo.versionCode
        }

        val latestCode = updateInfo?.versionCode ?: installedCode
        val latestName = updateInfo?.versionName?.ifBlank { installedName } ?: installedName
        val isUpToDate = installedCode >= latestCode

        try {
            val db = RepositoryManager.firestore
            val statusRef = db.collection(COLLECTION_USER_UPDATE_STATUS).document(uid)
            val existingSnap = statusRef.get().await()

            val userDoc = try {
                db.collection("users").document(uid).get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            val username = userDoc?.getString("username")
                ?: userDoc?.getString("displayName")
                ?: FirebaseAuth.getInstance().currentUser?.displayName
                ?: "User"
            val avatarUrl = userDoc?.getString("profilePicUrl")
                ?: userDoc?.getString("avatarUrl")
                ?: ""

            val prevInstalledCode = (existingSnap.getLong("installedVersionCode") ?: 0L).toInt()
            val prevStatus = existingSnap.getString("downloadStatus") ?: "PENDING"
            val prevDate = existingSnap.getString("updateCompletedDate") ?: ""
            val prevTime = existingSnap.getString("updateCompletedTime") ?: ""

            val nowDate = getCurrentDateString()
            val nowTime = getCurrentTimeString()

            val resolvedStatus: String
            val completedDate: String
            val completedTime: String

            if (isUpToDate) {
                resolvedStatus = "INSTALLED"
                if (prevInstalledCode < installedCode || prevStatus != "INSTALLED" || prevDate.isEmpty()) {
                    completedDate = nowDate
                    completedTime = nowTime
                } else {
                    completedDate = prevDate
                    completedTime = prevTime
                }
            } else {
                resolvedStatus = if (prevStatus == "DOWNLOADING" || prevStatus == "DOWNLOADED" || prevStatus == "FAILED") {
                    prevStatus
                } else {
                    "PENDING"
                }
                completedDate = prevDate
                completedTime = prevTime
            }

            val statusPayload = mapOf(
                "uid" to uid,
                "username" to username,
                "avatarUrl" to avatarUrl,
                "currentVersion" to "$installedName ($installedCode)",
                "currentVersionCode" to installedCode,
                "latestVersion" to "$latestName ($latestCode)",
                "latestVersionCode" to latestCode,
                "downloadStatus" to resolvedStatus,
                "installedVersion" to "$installedName ($installedCode)",
                "installedVersionCode" to installedCode,
                "updateCompletedDate" to completedDate,
                "updateCompletedTime" to completedTime,
                "lastUpdatedMillis" to System.currentTimeMillis()
            )

            statusRef.set(statusPayload, SetOptions.merge()).await()
            db.collection("users").document(uid).set(
                mapOf(
                    "appVersionName" to installedName,
                    "appVersionCode" to installedCode,
                    "updateDownloadStatus" to resolvedStatus,
                    "updateCompletedDate" to completedDate,
                    "updateCompletedTime" to completedTime
                ),
                SetOptions.merge()
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing user update status in Firebase", e)
        }

        isUpToDate
    }

    /**
     * Updates the current user's downloadStatus in Firebase (`DOWNLOADING`, `DOWNLOADED`, `INSTALLING`, `FAILED`).
     */
    suspend fun updateUserDownloadStatus(
        context: Context,
        updateInfo: UpdateInfo,
        status: String
    ) = withContext(Dispatchers.IO) {
        val uid = RepositoryManager.getCurrentUserId()
        if (uid.isEmpty()) return@withContext
        val installedCode = getInstalledVersionCode(context)
        val installedName = getInstalledVersionName(context)

        try {
            val db = RepositoryManager.firestore
            val payload = mutableMapOf<String, Any>(
                "uid" to uid,
                "currentVersion" to "$installedName ($installedCode)",
                "currentVersionCode" to installedCode,
                "latestVersion" to "${updateInfo.versionName} (${updateInfo.versionCode})",
                "latestVersionCode" to updateInfo.versionCode,
                "downloadStatus" to status,
                "lastUpdatedMillis" to System.currentTimeMillis()
            )
            db.collection(COLLECTION_USER_UPDATE_STATUS)
                .document(uid)
                .set(payload, SetOptions.merge())
                .await()
            db.collection("users")
                .document(uid)
                .set(mapOf("updateDownloadStatus" to status), SetOptions.merge())
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update download status to $status", e)
        }
    }

    data class DownloadProgress(
        val percent: Int,
        val downloadedMb: Double,
        val totalMb: Double
    )

    /**
     * Downloads the APK from Google Drive / direct URL with real byte-by-byte progress,
     * bypassing Google Drive virus-scan HTML warning pages for large APKs,
     * and verifies the downloaded file is a valid Android APK package before returning.
     */
    suspend fun downloadApkWithProgress(
        context: Context,
        updateInfo: UpdateInfo,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val rawUrl = updateInfo.url.trim()
            if (rawUrl.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("APK Download URL is empty."))
            }

            updateUserDownloadStatus(context, updateInfo, "DOWNLOADING")
            onProgress(DownloadProgress(0, 0.0, updateInfo.fileSizeMb))

            val directUrl = resolveApkDownloadUrl(rawUrl)
            val isGitHubAsset = directUrl.contains("github.com", ignoreCase = true) ||
                directUrl.contains("githubusercontent.com", ignoreCase = true)
            val driveFileId = if (isGitHubAsset) null else MediaResolver.extractGoogleDriveFileId(directUrl)

            var requestBuilder = Request.Builder()
                .url(directUrl)
                .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile) SocialIndiaUpdater/1.0")
                .apply {
                    if (isGitHubAsset) {
                        header("Accept", "application/octet-stream")
                    }
                }
                .get()

            // Attach pooled OAuth token if available for Google Drive URLs
            if (!driveFileId.isNullOrEmpty()) {
                val token = GoogleDrivePermissionRepairManager.getValidBearerToken()
                if (!token.isNullOrBlank()) {
                    requestBuilder = requestBuilder.header("Authorization", "Bearer $token")
                }
            }

            var response = httpClient.newCall(requestBuilder.build()).execute()

            // If 403/401 on Drive, try without Authorization header (public link)
            if (!response.isSuccessful && !driveFileId.isNullOrEmpty()) {
                response.close()
                val publicUrl = "https://drive.usercontent.google.com/download?id=$driveFileId&export=download&confirm=t"
                response = httpClient.newCall(
                    Request.Builder()
                        .url(publicUrl)
                        .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile)")
                        .get()
                        .build()
                ).execute()
            }

            // Handle Google Drive large-file HTML virus-scan warning page
            val contentType = response.header("Content-Type")?.lowercase() ?: ""
            if (response.isSuccessful && contentType.contains("text/html") && !driveFileId.isNullOrEmpty()) {
                val htmlPreview = response.body?.string() ?: ""
                response.close()
                val uuidRegex = Regex("name=\"uuid\"\\s+value=\"([^\"]+)\"")
                val uuidMatch = uuidRegex.find(htmlPreview)?.groupValues?.getOrNull(1)
                val confirmedUrl = if (!uuidMatch.isNullOrEmpty()) {
                    "https://drive.usercontent.google.com/download?id=$driveFileId&export=download&confirm=t&uuid=$uuidMatch"
                } else {
                    "https://drive.google.com/uc?export=download&confirm=t&id=$driveFileId"
                }
                response = httpClient.newCall(
                    Request.Builder()
                        .url(confirmedUrl)
                        .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile)")
                        .get()
                        .build()
                ).execute()
            }

            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                updateUserDownloadStatus(context, updateInfo, "FAILED")
                return@withContext Result.failure(Exception("Server returned HTTP $code while downloading APK."))
            }

            val body = response.body ?: run {
                response.close()
                updateUserDownloadStatus(context, updateInfo, "FAILED")
                return@withContext Result.failure(Exception("Empty response body from download server."))
            }

            val contentLength = body.contentLength()
            val expectedTotalBytes = if (contentLength > 0L) {
                contentLength
            } else if (updateInfo.fileSizeMb > 0.0) {
                (updateInfo.fileSizeMb * 1024 * 1024).toLong()
            } else {
                -1L
            }

            val baseDir = context.getExternalFilesDir("updates") ?: File(context.cacheDir, "updates")
            if (!baseDir.exists()) baseDir.mkdirs()
            val targetApkFile = File(baseDir, "SocialIndia_update_v${updateInfo.versionCode}.apk")
            if (targetApkFile.exists()) targetApkFile.delete()

            body.byteStream().use { input ->
                FileOutputStream(targetApkFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L
                    var lastReportedPercent = -1

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        val dlMb = Math.round((totalRead.toDouble() / (1024.0 * 1024.0)) * 100.0) / 100.0
                        val totMb = if (expectedTotalBytes > 0L) {
                            Math.round((expectedTotalBytes.toDouble() / (1024.0 * 1024.0)) * 100.0) / 100.0
                        } else {
                            updateInfo.fileSizeMb
                        }
                        val pct = if (expectedTotalBytes > 0L) {
                            ((totalRead * 100L) / expectedTotalBytes).toInt().coerceIn(1, 99)
                        } else {
                            50
                        }
                        if (pct != lastReportedPercent || totalRead % (256 * 1024) == 0L) {
                            lastReportedPercent = pct
                            onProgress(DownloadProgress(pct, dlMb, totMb))
                        }
                    }
                    output.flush()
                }
            }
            response.close()

            if (!targetApkFile.exists() || targetApkFile.length() < 1024L) {
                targetApkFile.delete()
                updateUserDownloadStatus(context, updateInfo, "FAILED")
                return@withContext Result.failure(Exception("Downloaded file is incomplete or corrupted."))
            }

            // Verify APK ZIP magic header ('P', 'K')
            val headerBytes = ByteArray(2)
            targetApkFile.inputStream().use { it.read(headerBytes) }
            val isZipHeader = headerBytes[0] == 'P'.code.toByte() && headerBytes[1] == 'K'.code.toByte()
            if (!isZipHeader) {
                targetApkFile.delete()
                updateUserDownloadStatus(context, updateInfo, "FAILED")
                return@withContext Result.failure(Exception("Invalid APK file received (make sure the Google Drive file is a valid .apk with public access)."))
            }

            // Verify package compatibility if archive metadata is readable
            val metadata = inspectApkFile(context, targetApkFile)
            if (metadata != null && !metadata.isCompatiblePackage) {
                targetApkFile.delete()
                updateUserDownloadStatus(context, updateInfo, "FAILED")
                return@withContext Result.failure(
                    Exception("Package mismatch: APK package (${metadata.packageName}) does not match ${context.packageName}.")
                )
            }

            val finalMb = Math.round((targetApkFile.length().toDouble() / (1024.0 * 1024.0)) * 100.0) / 100.0
            onProgress(DownloadProgress(100, finalMb, finalMb))
            updateUserDownloadStatus(context, updateInfo, "DOWNLOADED")
            Result.success(targetApkFile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "APK download failed", e)
            updateUserDownloadStatus(context, updateInfo, "FAILED")
            Result.failure(e)
        }
    }

    /**
     * Launches the official Android system Package Installer for the downloaded APK file.
     * Preserves all user data, accounts, chats, posts, photos, and videos by performing a standard upgrade install.
     */
    fun launchOfficialPackageInstaller(context: Context, apkFile: File): Boolean {
        return try {
            val authority = "${context.packageName}.fileprovider"
            val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch PackageInstaller via ACTION_VIEW", e)
            false
        }
    }
}
