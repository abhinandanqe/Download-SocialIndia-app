package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.UpdateInfo
import com.example.utils.AppUpdateManager
import kotlinx.coroutines.launch
import java.io.File

private enum class ApkPopupStage {
    IDLE,
    DOWNLOADING,
    READY_TO_INSTALL,
    FAILED,
    VERIFIED_INSTALLED
}

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismissRequest: () -> Unit,
    onUpdateVerifiedInstalled: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val installedVersionName = remember { AppUpdateManager.getInstalledVersionName(context) }
    val installedVersionCode = remember { AppUpdateManager.getInstalledVersionCode(context) }

    var stage by remember { mutableStateOf(ApkPopupStage.IDLE) }
    var progressPercent by remember { mutableIntStateOf(0) }
    var downloadedMb by remember { mutableDoubleStateOf(0.0) }
    var totalMb by remember { mutableDoubleStateOf(updateInfo.fileSizeMb) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Verify installed version whenever user returns to app (e.g. from Android Package Installer)
    DisposableEffect(lifecycleOwner, updateInfo.versionCode) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    val isNowUpdated = AppUpdateManager.syncUserUpdateStatusOnLaunch(context, updateInfo)
                    if (isNowUpdated) {
                        stage = ApkPopupStage.VERIFIED_INSTALLED
                        onUpdateVerifiedInstalled()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun startApkDownload() {
        stage = ApkPopupStage.DOWNLOADING
        errorMessage = null
        progressPercent = 0
        downloadedMb = 0.0
        scope.launch {
            val result = AppUpdateManager.downloadApkWithProgress(
                context = context,
                updateInfo = updateInfo,
                onProgress = { prog ->
                    progressPercent = prog.percent
                    downloadedMb = prog.downloadedMb
                    if (prog.totalMb > 0.0) {
                        totalMb = prog.totalMb
                    }
                }
            )
            result.fold(
                onSuccess = { apkFile ->
                    downloadedApkFile = apkFile
                    stage = ApkPopupStage.READY_TO_INSTALL
                    AppUpdateManager.updateUserDownloadStatus(context, updateInfo, "INSTALLING")
                    val launched = AppUpdateManager.launchOfficialPackageInstaller(context, apkFile)
                    if (!launched) {
                        // Fallback to opening direct download URL in system browser/installer if FileProvider intent fails
                        try {
                            val browserIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(AppUpdateManager.resolveApkDownloadUrl(updateInfo.url))
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(browserIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Unable to open installer: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onFailure = { err ->
                    errorMessage = err.message ?: "Download failed. Please check your connection and retry."
                    stage = ApkPopupStage.FAILED
                }
            )
        }
    }

    Dialog(
        onDismissRequest = {
            if (!updateInfo.mandatory && stage != ApkPopupStage.DOWNLOADING) {
                onDismissRequest()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !updateInfo.mandatory && stage != ApkPopupStage.DOWNLOADING,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            contentColor = Color(0xFF111111),
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("social_india_update_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Badge & Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = if (updateInfo.mandatory) Color(0xFFFF3366).copy(alpha = 0.12f) else Color(0xFF0095F6).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = when {
                                updateInfo.mandatory -> "IMPORTANT UPDATE"
                                updateInfo.source == "GITHUB_RELEASE" -> "GITHUB AUTO UPDATE"
                                else -> "OFFICIAL UPDATE"
                            },
                            color = if (updateInfo.mandatory) Color(0xFFFF3366) else Color(0xFF0095F6),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    if (!updateInfo.mandatory) {
                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("update_dialog_top_close")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF6B7280),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF3366).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = "Social India Update",
                        modifier = Modifier.size(34.dp),
                        tint = Color(0xFFFF3366)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Social India Update",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF111111),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = updateInfo.updateMessage.ifBlank {
                        "A new version of Social India is ready. Update now for the best experience."
                    },
                    fontSize = 13.5.sp,
                    color = Color(0xFF4B5563),
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Version & Release Date Info Card
                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("New Version:", fontSize = 12.5.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                            Text(
                                text = "v${updateInfo.versionName} (Build ${updateInfo.versionCode})",
                                fontSize = 12.5.sp,
                                color = Color(0xFF111111),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Installed Version:", fontSize = 12.5.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                            Text(
                                text = "v$installedVersionName (Build $installedVersionCode)",
                                fontSize = 12.5.sp,
                                color = Color(0xFF475569),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Update Date:", fontSize = 12.5.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                            Text(
                                text = updateInfo.releaseDate.ifBlank { AppUpdateManager.getCurrentDateString() },
                                fontSize = 12.5.sp,
                                color = Color(0xFF111111),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (totalMb > 0.0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Package Size:", fontSize = 12.5.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                                Text(
                                    text = "$totalMb MB",
                                    fontSize = 12.5.sp,
                                    color = Color(0xFF111111),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // What's New Section
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = Color(0xFFF9FAFB),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "What's New",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF111111)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = updateInfo.notes.ifBlank {
                                "• Faster video & Reels playback\n• Improved Google Drive media streaming\n• Bug fixes and stability improvements"
                            },
                            fontSize = 12.5.sp,
                            color = Color(0xFF374151),
                            lineHeight = 18.sp
                        )
                    }
                }

                // Data Safety Assurance
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFECFDF5))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = Color(0xFF059669),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Your account, chats, posts, photos & videos stay 100% safe.",
                        fontSize = 11.5.sp,
                        color = Color(0xFF065F46),
                        fontWeight = FontWeight.Medium
                    )
                }

                // Download Progress / Status Indicator
                if (stage == ApkPopupStage.DOWNLOADING) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = { (progressPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFFFF3366),
                            trackColor = Color(0xFFE5E7EB)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val sizeProgressText = if (totalMb > 0.0) {
                            "$downloadedMb MB / $totalMb MB ($progressPercent%)"
                        } else {
                            "$downloadedMb MB downloaded ($progressPercent%)"
                        }
                        Text(
                            text = "Downloading APK... $sizeProgressText",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF3366)
                        )
                    }
                } else if (stage == ApkPopupStage.READY_TO_INSTALL) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = Color(0xFFEFF6FF),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "APK downloaded! Complete installation in the Android installer to verify update.",
                                fontSize = 12.sp,
                                color = Color(0xFF1E40AF),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else if (stage == ApkPopupStage.FAILED) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage ?: "APK download failed. Tap Retry to try again.",
                                fontSize = 12.sp,
                                color = Color(0xFF991B1B),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons: DOWNLOAD UPDATE (or RETRY / INSTALL) and CLOSE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!updateInfo.mandatory) {
                        OutlinedButton(
                            onClick = onDismissRequest,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("update_dialog_close_button"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFD1D5DB)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF374151))
                        ) {
                            Text(
                                text = "CLOSE",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Button(
                        onClick = {
                            when (stage) {
                                ApkPopupStage.READY_TO_INSTALL -> {
                                    val file = downloadedApkFile
                                    if (file != null && file.exists()) {
                                        val ok = AppUpdateManager.launchOfficialPackageInstaller(context, file)
                                        if (!ok) {
                                            startApkDownload()
                                        }
                                    } else {
                                        startApkDownload()
                                    }
                                }
                                ApkPopupStage.DOWNLOADING -> {
                                    // Already downloading
                                }
                                else -> {
                                    startApkDownload()
                                }
                            }
                        },
                        enabled = stage != ApkPopupStage.DOWNLOADING,
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp)
                            .testTag("update_dialog_download_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF3366),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = when (stage) {
                                ApkPopupStage.FAILED -> Icons.Default.Refresh
                                ApkPopupStage.READY_TO_INSTALL -> Icons.Default.SystemUpdate
                                else -> Icons.Default.CloudDownload
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (stage) {
                                ApkPopupStage.DOWNLOADING -> "DOWNLOADING..."
                                ApkPopupStage.READY_TO_INSTALL -> "INSTALL UPDATE"
                                ApkPopupStage.FAILED -> "RETRY"
                                else -> "DOWNLOAD UPDATE"
                            },
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
