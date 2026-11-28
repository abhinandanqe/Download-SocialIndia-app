package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.model.ApkPackageMetadata
import com.example.model.RepositoryManager
import com.example.model.UpdateHistoryRecord
import com.example.model.UpdateInfo
import com.example.model.User
import com.example.model.UserUpdateStatus
import com.example.utils.AppUpdateManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AdminAppUpdateTab(
    users: List<User>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = RepositoryManager.firestore

    // Live Firebase state for active UpdateInfo, Update History, and Per-User Update Status
    var activeConfig by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateHistory by remember { mutableStateOf<List<UpdateHistoryRecord>>(emptyList()) }
    var userStatusMap by remember { mutableStateOf<Map<String, UserUpdateStatus>>(emptyMap()) }

    // Form state for releasing / editing an update
    var versionCodeInput by remember { mutableStateOf((BuildConfig.VERSION_CODE + 1).toString()) }
    var versionNameInput by remember { mutableStateOf("2.0") }
    var apkUrlInput by remember { mutableStateOf("") }
    var releaseDateInput by remember { mutableStateOf(AppUpdateManager.getCurrentDateString()) }
    var updateMessageInput by remember {
        mutableStateOf("A new version of Social India is available with faster video playback and new features!")
    }
    var updateNotesInput by remember {
        mutableStateOf("• Faster Reels & Home Feed video streaming\n• Improved Google Drive media stability\n• Performance & security upgrades")
    }
    var popupEnabledInput by remember { mutableStateOf(true) }
    var mandatoryInput by remember { mutableStateOf(false) } // false = Optional, true = Force
    var fileSizeMbInput by remember { mutableDoubleStateOf(0.0) }
    var inspectedApkMeta by remember { mutableStateOf<ApkPackageMetadata?>(null) }

    var githubRepoInput by remember { mutableStateOf(AppUpdateManager.getDefaultGitHubRepo()) }
    var githubAutoSyncInput by remember { mutableStateOf(true) }
    var isFetchingGitHub by remember { mutableStateOf(false) }
    var isSavingGitHubConfig by remember { mutableStateOf(false) }
    var fetchedGitHubRelease by remember { mutableStateOf<UpdateInfo?>(null) }
    var githubStatusMsg by remember { mutableStateOf<String?>(null) }

    var isUploadingApk by remember { mutableStateOf(false) }
    var apkUploadProgress by remember { mutableIntStateOf(0) }
    var isPublishing by remember { mutableStateOf(false) }
    var hasInitializedFormFromRemote by remember { mutableStateOf(false) }

    var selectedUserFilter by remember { mutableStateOf("ALL") } // ALL, UPDATED, PENDING, FAILED

    // Real-time listeners for app_config/update_info, app_update_history, and user_update_status
    DisposableEffect(Unit) {
        val configReg = db.collection(AppUpdateManager.COLLECTION_APP_CONFIG)
            .document(AppUpdateManager.DOC_UPDATE_INFO)
            .addSnapshotListener { snap, err ->
                if (err == null && snap != null && snap.exists()) {
                    val parsed = AppUpdateManager.parseUpdateInfo(snap)
                    activeConfig = parsed
                    if (parsed != null && !hasInitializedFormFromRemote) {
                        hasInitializedFormFromRemote = true
                        versionCodeInput = parsed.versionCode.toString()
                        versionNameInput = parsed.versionName
                        apkUrlInput = parsed.url
                        releaseDateInput = parsed.releaseDate.ifBlank { AppUpdateManager.getCurrentDateString() }
                        updateMessageInput = parsed.updateMessage
                        updateNotesInput = parsed.notes
                        popupEnabledInput = parsed.popupEnabled
                        mandatoryInput = parsed.mandatory
                        fileSizeMbInput = parsed.fileSizeMb
                        if (parsed.githubRepo.isNotBlank()) {
                            githubRepoInput = parsed.githubRepo
                        }
                        githubAutoSyncInput = parsed.githubAutoSyncEnabled
                    }
                }
            }

        val historyReg = db.collection(AppUpdateManager.COLLECTION_UPDATE_HISTORY)
            .limit(30)
            .addSnapshotListener { snap, err ->
                if (err == null && snap != null) {
                    val list = snap.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(UpdateHistoryRecord::class.java)?.copy(id = doc.id)
                        } catch (_: Exception) {
                            null
                        }
                    }.sortedByDescending { it.releasedAtMillis }
                    updateHistory = list
                }
            }

        val statusReg = db.collection(AppUpdateManager.COLLECTION_USER_UPDATE_STATUS)
            .limit(300)
            .addSnapshotListener { snap, err ->
                if (err == null && snap != null) {
                    val map = mutableMapOf<String, UserUpdateStatus>()
                    for (doc in snap.documents) {
                        try {
                            val item = doc.toObject(UserUpdateStatus::class.java)?.copy(
                                uid = doc.getString("uid")?.ifBlank { doc.id } ?: doc.id
                            )
                            if (item != null) {
                                map[item.uid] = item
                            }
                        } catch (_: Exception) {}
                    }
                    userStatusMap = map
                }
            }

        onDispose {
            configReg.remove()
            historyReg.remove()
            statusReg.remove()
        }
    }

    // APK File Picker & Direct Google Drive Uploader
    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingApk = true
            apkUploadProgress = 5
            scope.launch {
                try {
                    val meta = AppUpdateManager.inspectApkUri(context, uri)
                    inspectedApkMeta = meta
                    if (meta != null) {
                        versionNameInput = meta.versionName
                        versionCodeInput = meta.versionCode.toString()
                        fileSizeMbInput = meta.fileSizeMb
                        if (!meta.isCompatiblePackage) {
                            Toast.makeText(
                                context,
                                "Warning: APK package (${meta.packageName}) differs from ${context.packageName}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    val targetVName = meta?.versionName ?: versionNameInput.ifBlank { "latest" }
                    val uploadedPair = AppUpdateManager.uploadApkToGoogleDrive(
                        context = context,
                        apkUri = uri,
                        versionName = targetVName,
                        onProgress = { p -> apkUploadProgress = p }
                    )

                    if (uploadedPair != null) {
                        apkUrlInput = uploadedPair.first
                        if (uploadedPair.second > 0.0) {
                            fileSizeMbInput = uploadedPair.second
                        }
                        if (releaseDateInput.isBlank()) {
                            releaseDateInput = AppUpdateManager.getCurrentDateString()
                        }
                        Toast.makeText(
                            context,
                            "APK uploaded to Google Drive! Ready to publish.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "Failed to upload APK. You can also paste a Google Drive link directly.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "APK upload error: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isUploadingApk = false
                }
            }
        }
    }

    // Combine registered users + user_update_status records for complete dashboard visibility
    val latestReleasedVersionCode = activeConfig?.versionCode ?: BuildConfig.VERSION_CODE
    val latestReleasedVersionName = activeConfig?.versionName ?: BuildConfig.VERSION_NAME
    val latestReleaseDate = activeConfig?.releaseDate?.ifBlank { "Not released yet" } ?: "Not released yet"

    val mergedUserStatuses = remember(users, userStatusMap, latestReleasedVersionCode, latestReleasedVersionName) {
        val allUids = (users.map { it.id } + userStatusMap.keys).filter { it.isNotBlank() }.distinct()
        allUids.map { uid ->
            val userObj = users.find { it.id == uid }
            val statusObj = userStatusMap[uid]
            val curCode = statusObj?.currentVersionCode ?: 1
            val instCode = statusObj?.installedVersionCode ?: curCode
            val isUpdated = instCode >= latestReleasedVersionCode && (activeConfig != null)
            val resolvedDownloadStatus = when {
                statusObj != null && statusObj.downloadStatus.isNotBlank() -> {
                    if (isUpdated) "INSTALLED" else statusObj.downloadStatus
                }
                isUpdated -> "INSTALLED"
                else -> "PENDING"
            }

            UserUpdateStatus(
                uid = uid,
                username = statusObj?.username?.ifBlank { userObj?.username ?: "User" } ?: (userObj?.username ?: "User"),
                avatarUrl = statusObj?.avatarUrl?.ifBlank { userObj?.avatarUrl ?: "" } ?: (userObj?.avatarUrl ?: ""),
                currentVersion = statusObj?.currentVersion?.ifBlank { "1.0 (1)" } ?: "1.0 (1)",
                currentVersionCode = curCode,
                latestVersion = "$latestReleasedVersionName ($latestReleasedVersionCode)",
                latestVersionCode = latestReleasedVersionCode,
                downloadStatus = resolvedDownloadStatus,
                installedVersion = statusObj?.installedVersion?.ifBlank { statusObj.currentVersion } ?: "1.0 (1)",
                installedVersionCode = instCode,
                updateCompletedDate = statusObj?.updateCompletedDate ?: "",
                updateCompletedTime = statusObj?.updateCompletedTime ?: "",
                lastUpdatedMillis = statusObj?.lastUpdatedMillis ?: 0L
            )
        }.sortedByDescending { it.lastUpdatedMillis }
    }

    val totalUsersCount = mergedUserStatuses.size
    val updatedUsersCount = mergedUserStatuses.count {
        it.installedVersionCode >= latestReleasedVersionCode && activeConfig != null
    }
    val pendingUsersCount = (totalUsersCount - updatedUsersCount).coerceAtLeast(0)

    val filteredUserStatuses = remember(mergedUserStatuses, selectedUserFilter, latestReleasedVersionCode, activeConfig) {
        when (selectedUserFilter) {
            "UPDATED" -> mergedUserStatuses.filter { it.installedVersionCode >= latestReleasedVersionCode && activeConfig != null }
            "PENDING" -> mergedUserStatuses.filter { it.installedVersionCode < latestReleasedVersionCode || activeConfig == null }
            "FAILED" -> mergedUserStatuses.filter { it.downloadStatus.equals("FAILED", ignoreCase = true) || it.downloadStatus.equals("DOWNLOADING", ignoreCase = true) }
            else -> mergedUserStatuses
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("admin_app_update_tab"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. ADMIN UPDATE DASHBOARD SUMMARY CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181824)),
                border = BorderStroke(1.dp, Color(0xFFFF3366).copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = Color(0xFFFF3366),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Automatic APK Update Dashboard",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Google Drive APK Hosting & Real-Time Firebase Control",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.5.sp
                                )
                            }
                        }

                        Surface(
                            color = if (activeConfig?.popupEnabled == true) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = if (activeConfig?.popupEnabled == true) "POPUP ON" else "POPUP OFF",
                                color = if (activeConfig?.popupEnabled == true) Color(0xFF10B981) else Color(0xFFEF4444),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Metrics Grid (Total Users, Updated Users, Pending Update Users)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DashboardStatTile(
                            label = "Total Users",
                            value = totalUsersCount.toString(),
                            accentColor = Color(0xFF38BDF8),
                            modifier = Modifier.weight(1f)
                        )
                        DashboardStatTile(
                            label = "Updated Users",
                            value = updatedUsersCount.toString(),
                            accentColor = Color(0xFF10B981),
                            modifier = Modifier.weight(1f)
                        )
                        DashboardStatTile(
                            label = "Pending Update",
                            value = pendingUsersCount.toString(),
                            accentColor = Color(0xFFF59E0B),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Latest Released Version & Release Date Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0F172A))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Latest Released Version", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            Text(
                                text = if (activeConfig != null) "v${activeConfig!!.versionName} (Code ${activeConfig!!.versionCode})" else "None published",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Release Date", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            Text(
                                text = latestReleaseDate,
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            }
        }

        // 2. LIVE POPUP ON/OFF & OPTIONAL/FORCE CONTROLS
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Live Update Popup Controls",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    // Popup ON / OFF Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Update Popup for Old Versions", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                            Text(
                                text = "When ON, users with versionCode < latest will see the update popup on every app launch until installed.",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.5.sp
                            )
                        }
                        Switch(
                            checked = popupEnabledInput,
                            onCheckedChange = { enabled ->
                                popupEnabledInput = enabled
                                scope.launch {
                                    val ok = AppUpdateManager.setPopupEnabled(enabled)
                                    if (ok) {
                                        Toast.makeText(
                                            context,
                                            if (enabled) "Update Popup turned ON" else "Update Popup turned OFF",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF10B981))
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    // Optional Update vs Force Update Selector
                    Text("Update Mode", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val isOptional = !mandatoryInput
                        Surface(
                            onClick = {
                                mandatoryInput = false
                                scope.launch { AppUpdateManager.setMandatoryUpdate(false) }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isOptional) Color(0xFF0095F6) else Color(0xFF0F172A),
                            border = BorderStroke(1.dp, if (isOptional) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text("Optional Update", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            }
                        }

                        Surface(
                            onClick = {
                                mandatoryInput = true
                                scope.launch { AppUpdateManager.setMandatoryUpdate(true) }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (mandatoryInput) Color(0xFFFF3366) else Color(0xFF0F172A),
                            border = BorderStroke(1.dp, if (mandatoryInput) Color(0xFFFF8C42) else Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text("Force Update", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            }
                        }
                    }
                }
            }
        }

        // 2B. GITHUB RELEASES AUTO-UPDATE & PUBLIC SYNC CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2D)),
                border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = Color(0xFFA78BFA),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "GitHub Releases Auto-Update",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Pushes to GitHub build APK via Actions & auto-popup on all users' devices",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = githubRepoInput,
                        onValueChange = { githubRepoInput = it },
                        label = { Text("GitHub Repository (owner/repo or URL)") },
                        placeholder = { Text("e.g. abhinandan/social-india") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_github_repo_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFA78BFA),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Detect New GitHub Releases",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Every device checks GitHub Releases on launch and pops up update automatically",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = githubAutoSyncInput,
                            onCheckedChange = { enabled ->
                                githubAutoSyncInput = enabled
                                scope.launch {
                                    AppUpdateManager.saveGitHubAutoSyncConfig(githubRepoInput, enabled)
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF8B5CF6))
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val norm = AppUpdateManager.normalizeGitHubRepo(githubRepoInput)
                                if (norm.isBlank()) {
                                    Toast.makeText(context, "Enter repo like owner/repo", Toast.LENGTH_SHORT).show()
                                    return@OutlinedButton
                                }
                                githubRepoInput = norm
                                isSavingGitHubConfig = true
                                scope.launch {
                                    val ok = AppUpdateManager.saveGitHubAutoSyncConfig(norm, githubAutoSyncInput)
                                    isSavingGitHubConfig = false
                                    githubStatusMsg = if (ok) {
                                        "Saved $norm! All users will auto-check this repo on app launch."
                                    } else {
                                        "Failed to save GitHub repo config."
                                    }
                                }
                            },
                            enabled = !isSavingGitHubConfig && !isFetchingGitHub,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFA78BFA).copy(alpha = 0.65f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("admin_save_github_repo_button")
                        ) {
                            Text(
                                text = if (isSavingGitHubConfig) "Saving..." else "Save Repo",
                                color = Color(0xFFDDD6FE),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                        }

                        Button(
                            onClick = {
                                val norm = AppUpdateManager.normalizeGitHubRepo(githubRepoInput)
                                if (norm.isBlank()) {
                                    Toast.makeText(context, "Enter repo like owner/repo", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                githubRepoInput = norm
                                isFetchingGitHub = true
                                githubStatusMsg = null
                                scope.launch {
                                    val res = AppUpdateManager.fetchLatestGitHubRelease(norm, activeConfig)
                                    isFetchingGitHub = false
                                    res.fold(
                                        onSuccess = { rel ->
                                            fetchedGitHubRelease = rel
                                            versionNameInput = rel.versionName
                                            versionCodeInput = rel.versionCode.toString()
                                            apkUrlInput = rel.url
                                            releaseDateInput = rel.releaseDate
                                            updateMessageInput = rel.updateMessage
                                            updateNotesInput = rel.notes
                                            fileSizeMbInput = rel.fileSizeMb
                                            mandatoryInput = rel.mandatory
                                            popupEnabledInput = true
                                            githubStatusMsg = "Found GitHub Release ${rel.githubReleaseTag.ifBlank { "v${rel.versionName}" }} (Code ${rel.versionCode}, ${rel.fileSizeMb} MB)"
                                        },
                                        onFailure = { err ->
                                            fetchedGitHubRelease = null
                                            githubStatusMsg = err.message ?: "Unable to fetch GitHub Release."
                                        }
                                    )
                                }
                            },
                            enabled = !isFetchingGitHub && !isSavingGitHubConfig,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(48.dp)
                                .testTag("admin_fetch_github_release_button")
                        ) {
                            if (isFetchingGitHub) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.DownloadForOffline, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Fetch Latest APK", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            }
                        }
                    }

                    githubStatusMsg?.let { msg ->
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                color = Color(0xFFE2E8F0),
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    fetchedGitHubRelease?.let { rel ->
                        Button(
                            onClick = {
                                isPublishing = true
                                scope.launch {
                                    val toPublish = rel.copy(
                                        githubRepo = AppUpdateManager.normalizeGitHubRepo(githubRepoInput),
                                        githubAutoSyncEnabled = githubAutoSyncInput,
                                        popupEnabled = true,
                                        mandatory = mandatoryInput
                                    )
                                    val ok = AppUpdateManager.publishAppUpdate(toPublish)
                                    isPublishing = false
                                    Toast.makeText(
                                        context,
                                        if (ok) "GitHub Release v${rel.versionName} published publicly to all users!" else "Publish failed",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            enabled = !isPublishing,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("admin_publish_github_release_button")
                        ) {
                            Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Publish GitHub v${rel.versionName} (Code ${rel.versionCode}) to All Users",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // 3. UPLOAD NEW APK TO GOOGLE DRIVE & PUBLISH FORM
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181824)),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Upload New APK & Release Update",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.5.sp
                    )
                    Text(
                        text = "Upload a signed .apk file directly to Google Drive (auto-sets public access & extracts version info) or paste a Google Drive APK link.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )

                    // Google Drive APK Upload Button
                    Button(
                        onClick = { apkPickerLauncher.launch("*/*") },
                        enabled = !isUploadingApk,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("admin_upload_apk_drive_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0095F6))
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isUploadingApk) "Uploading APK to Google Drive ($apkUploadProgress%)..." else "Upload New APK to Google Drive",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }

                    if (isUploadingApk) {
                        LinearProgressIndicator(
                            progress = { (apkUploadProgress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF38BDF8),
                            trackColor = Color(0xFF334155)
                        )
                    }

                    inspectedApkMeta?.let { meta ->
                        Surface(
                            color = if (meta.isCompatiblePackage) Color(0xFF064E3B) else Color(0xFF7F1D1D),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (meta.isCompatiblePackage) {
                                    "Verified APK: ${meta.packageName} • v${meta.versionName} (Code ${meta.versionCode}) • ${meta.fileSizeMb} MB"
                                } else {
                                    "Warning: APK package (${meta.packageName}) does not match ${context.packageName}!"
                                },
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = versionNameInput,
                            onValueChange = { versionNameInput = it },
                            label = { Text("Version Name (e.g. 2.0)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                            )
                        )

                        OutlinedTextField(
                            value = versionCodeInput,
                            onValueChange = { versionCodeInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Version Code (Int)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                            )
                        )
                    }

                    OutlinedTextField(
                        value = releaseDateInput,
                        onValueChange = { releaseDateInput = it },
                        label = { Text("Release Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                        )
                    )

                    OutlinedTextField(
                        value = apkUrlInput,
                        onValueChange = { apkUrlInput = it },
                        label = { Text("Google Drive / Direct APK Download URL") },
                        placeholder = { Text("https://drive.google.com/... or direct .apk URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                        )
                    )

                    OutlinedTextField(
                        value = updateMessageInput,
                        onValueChange = { updateMessageInput = it },
                        label = { Text("Update Popup Message") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                        )
                    )

                    OutlinedTextField(
                        value = updateNotesInput,
                        onValueChange = { updateNotesInput = it },
                        label = { Text("Update Notes / What's New") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                        )
                    )

                    Button(
                        onClick = {
                            val parsedCode = versionCodeInput.trim().toIntOrNull()
                            if (parsedCode == null || parsedCode < 1) {
                                Toast.makeText(context, "Enter a valid Version Code (e.g. 2)", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (versionNameInput.isBlank()) {
                                Toast.makeText(context, "Enter a Version Name (e.g. 2.0)", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (apkUrlInput.isBlank()) {
                                Toast.makeText(context, "Please upload an APK or enter the APK Download URL", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            isPublishing = true
                            scope.launch {
                                val normalizedRepo = AppUpdateManager.normalizeGitHubRepo(githubRepoInput)
                                val infoToPublish = UpdateInfo(
                                    versionCode = parsedCode,
                                    versionName = versionNameInput.trim(),
                                    url = apkUrlInput.trim(),
                                    notes = updateNotesInput.trim(),
                                    updateMessage = updateMessageInput.trim(),
                                    releaseDate = releaseDateInput.trim().ifBlank { AppUpdateManager.getCurrentDateString() },
                                    mandatory = mandatoryInput,
                                    popupEnabled = popupEnabledInput,
                                    fileSizeMb = fileSizeMbInput,
                                    githubRepo = normalizedRepo,
                                    githubAutoSyncEnabled = githubAutoSyncInput,
                                    source = if (apkUrlInput.contains("github.com", ignoreCase = true) ||
                                        apkUrlInput.contains("githubusercontent.com", ignoreCase = true)
                                    ) "GITHUB_RELEASE" else "FIREBASE",
                                    githubReleaseTag = fetchedGitHubRelease?.githubReleaseTag ?: "",
                                    githubHtmlUrl = fetchedGitHubRelease?.githubHtmlUrl ?: ""
                                )
                                val ok = AppUpdateManager.publishAppUpdate(infoToPublish)
                                isPublishing = false
                                if (ok) {
                                    Toast.makeText(
                                        context,
                                        "Social India v${infoToPublish.versionName} (Code ${infoToPublish.versionCode}) published!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(context, "Failed to publish update to Firebase", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !isPublishing && !isUploadingApk,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("admin_publish_apk_update_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        if (isPublishing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Publish, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Release & Save APK Update in Firebase",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // 4. UPDATE HISTORY SECTION
        item {
            Text(
                text = "Update Release History (${updateHistory.size})",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )
        }

        if (updateHistory.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E293B)
                ) {
                    Text(
                        text = "No update history records yet. Published APK releases will appear here.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(updateHistory, key = { "hist_${it.id}" }) { record ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "v${record.versionName} (Code ${record.versionCode})",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Surface(
                                color = if (record.mandatory) Color(0xFFFF3366).copy(alpha = 0.2f) else Color(0xFF38BDF8).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (record.mandatory) "FORCE UPDATE" else "OPTIONAL",
                                    color = if (record.mandatory) Color(0xFFFF3366) else Color(0xFF38BDF8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Text(
                            text = "Release Date: ${record.releaseDate} • Size: ${if (record.fileSizeMb > 0) "${record.fileSizeMb} MB" else "Standard"}",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp
                        )
                        if (record.notes.isNotBlank()) {
                            Text(
                                text = record.notes,
                                color = Color(0xFFE2E8F0),
                                fontSize = 12.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    versionCodeInput = record.versionCode.toString()
                                    versionNameInput = record.versionName
                                    apkUrlInput = record.url
                                    releaseDateInput = record.releaseDate
                                    updateMessageInput = record.updateMessage
                                    updateNotesInput = record.notes
                                    mandatoryInput = record.mandatory
                                    fileSizeMbInput = record.fileSizeMb
                                    Toast.makeText(context, "Loaded v${record.versionName} into form", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Load in Editor", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            db.collection(AppUpdateManager.COLLECTION_UPDATE_HISTORY).document(record.id).delete().await()
                                        } catch (_: Exception) {}
                                    }
                                }
                            ) {
                                Text("Delete", color = Color(0xFFEF4444), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // 5. PER-USER UPDATE STATUS TRACKING TABLE
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "User Update Status (${filteredUserStatuses.size})",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "ALL" to "All ($totalUsersCount)",
                    "UPDATED" to "Updated ($updatedUsersCount)",
                    "PENDING" to "Pending ($pendingUsersCount)",
                    "FAILED" to "Downloading / Retry"
                ).forEach { (key, label) ->
                    item {
                        val selected = selectedUserFilter == key
                        Surface(
                            onClick = { selectedUserFilter = key },
                            shape = RoundedCornerShape(20.dp),
                            color = if (selected) Color(0xFFFF3366) else Color(0xFF1E293B),
                            border = BorderStroke(1.dp, if (selected) Color(0xFFFF3366) else Color.White.copy(alpha = 0.12f))
                        ) {
                            Text(
                                text = label,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        }

        items(filteredUserStatuses, key = { "u_status_${it.uid}" }) { uStatus ->
            val statusColor = when (uStatus.downloadStatus.uppercase()) {
                "INSTALLED" -> Color(0xFF10B981)
                "DOWNLOADING", "DOWNLOADED", "INSTALLING" -> Color(0xFF38BDF8)
                "FAILED" -> Color(0xFFEF4444)
                else -> Color(0xFFF59E0B)
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181824))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = uStatus.avatarUrl.ifBlank { "https://api.dicebear.com/7.x/avataaars/png?seed=${uStatus.uid}" },
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.DarkGray),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "@${uStatus.username}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                                Text(
                                    text = "UID: ${uStatus.uid}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            color = statusColor.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = uStatus.downloadStatus.uppercase(),
                                color = statusColor,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Current Version: ${uStatus.currentVersion}", color = Color(0xFFCBD5E1), fontSize = 11.5.sp)
                        Text("Latest Version: ${uStatus.latestVersion}", color = Color(0xFF38BDF8), fontSize = 11.5.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Installed Version: ${uStatus.installedVersion}", color = Color(0xFFCBD5E1), fontSize = 11.5.sp)
                        val completedText = if (uStatus.updateCompletedDate.isNotBlank()) {
                            "${uStatus.updateCompletedDate} ${uStatus.updateCompletedTime}"
                        } else {
                            "Pending Install"
                        }
                        Text("Completed: $completedText", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardStatTile(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0F172A),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                color = accentColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = Color(0xFF94A3B8),
                fontSize = 10.5.sp,
                maxLines = 1
            )
        }
    }
}
