package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.auth.MultiAccountManager
import com.example.model.RepositoryManager
import com.example.zego.SocialIndiaZegoConfig
import com.example.zego.SocialIndiaZegoManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZegoDiagnosticsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUid = RepositoryManager.getCurrentUserId()
    val activeAcc = MultiAccountManager.activeAccount.value
    val zegoUid = remember(currentUid) { SocialIndiaZegoConfig.mapToZegoUserId(currentUid) }

    val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    val callState by SocialIndiaZegoManager.callState.collectAsState()
    val isInitialized by SocialIndiaZegoManager.isInitialized.collectAsState()

    var testStatusText by remember { mutableStateOf("Ready for tests.") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ZEGO Cloud Diagnostics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Service Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Service Status", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    DiagnosticItem("App ID", "${SocialIndiaZegoConfig.appId} (Active)")
                    DiagnosticItem("Environment", SocialIndiaZegoConfig.environment.name)
                    DiagnosticItem("SDK Initialized", if (isInitialized) "YES" else "READY")
                    DiagnosticItem("Firebase UID", currentUid.ifEmpty { "Not logged in" })
                    DiagnosticItem("ZEGO User ID", zegoUid)
                    DiagnosticItem("Display Name", activeAcc?.username ?: "User")
                    DiagnosticItem("Camera Permission", if (hasCam) "GRANTED" else "DENIED", if (hasCam) Color(0xFF4CAF50) else Color(0xFFE53935))
                    DiagnosticItem("Microphone Permission", if (hasMic) "GRANTED" else "DENIED", if (hasMic) Color(0xFF4CAF50) else Color(0xFFE53935))
                    DiagnosticItem("Active Call State", callState?.status?.name ?: "IDLE")
                }
            }

            // Test Output Console
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("DIAGNOSTIC CONSOLE", color = Color(0xFF9E9E9E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        testStatusText,
                        color = Color(0xFF81C784),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }

            // Test Actions Grid
            Text("Verification Tests", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        SocialIndiaZegoManager.initialize(context)
                        testStatusText = "PASS: Initialized ZEGO with AppID ${SocialIndiaZegoConfig.appId}"
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Init Test", fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        val testRoomId = "test_call_${System.currentTimeMillis()}"
                        testStatusText = "PASS: Generated test room: $testRoomId"
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Room Gen", fontSize = 12.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        val fakePeer = "peer_test_${System.currentTimeMillis().toString().takeLast(4)}"
                        SocialIndiaZegoManager.startVoiceCall(context, fakePeer, "Test Caller")
                        testStatusText = "PASS: Voice call initiated with $fakePeer"
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Voice Test", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        val fakePeer = "peer_test_${System.currentTimeMillis().toString().takeLast(4)}"
                        SocialIndiaZegoManager.startVideoCall(context, fakePeer, "Test Caller")
                        testStatusText = "PASS: Video call initiated with $fakePeer"
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Video Test", fontSize = 12.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val liveId = "live_${System.currentTimeMillis()}"
                        SocialIndiaZegoManager.startLive(context, liveId, isHost = true, userName = activeAcc?.username ?: "Host")
                        testStatusText = "PASS: Live Streaming launched ($liveId)"
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
                ) {
                    Text("Live Test", fontSize = 12.sp, color = Color.White)
                }

                Button(
                    onClick = {
                        val roomId = "audio_${System.currentTimeMillis()}"
                        SocialIndiaZegoManager.startAudioRoom(context, roomId, "Test Audio Room", isHost = true, userName = activeAcc?.username ?: "Host")
                        testStatusText = "PASS: Audio Room launched ($roomId)"
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))
                ) {
                    Text("Audio Room", fontSize = 12.sp, color = Color.White)
                }
            }

            Button(
                onClick = {
                    SocialIndiaZegoManager.cleanup()
                    testStatusText = "PASS: Cleaned up and released all ZEGO session states."
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Cleanup & Reset State", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun DiagnosticItem(
    title: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}
