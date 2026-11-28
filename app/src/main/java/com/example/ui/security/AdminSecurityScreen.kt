package com.example.ui.security

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.security.AccountSecurityViewModel
import com.example.security.AdminSecurityPolicy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSecurityScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val securityVm: AccountSecurityViewModel = viewModel()
    val policy by securityVm.adminPolicy.collectAsStateWithLifecycle()

    var require2FA by remember(policy) { mutableStateOf(policy.requireTwoFactorForNewDevices) }
    var maxAttempts by remember(policy) { mutableIntStateOf(policy.maxFailedLoginAttempts) }
    var sessionDays by remember(policy) { mutableIntStateOf(policy.sessionExpiryDays) }
    var riskThreshold by remember(policy) { mutableStateOf(policy.riskThreshold) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Security Policy Admin",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val updated = policy.copy(
                                requireTwoFactorForNewDevices = require2FA,
                                maxFailedLoginAttempts = maxAttempts,
                                sessionExpiryDays = sessionDays,
                                riskThreshold = riskThreshold
                            )
                            securityVm.updateAdminSecurityPolicy(updated) { success ->
                                if (success) {
                                    Toast.makeText(context, "Global security policy updated", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save", tint = Color(0xFFFE2C55))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF141417))
            )
        },
        containerColor = Color(0xFF0F0F12)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B20)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Identity Provider Configuration", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Provider: ${policy.identityProviderName}", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    Text(
                        text = if (policy.isProviderConfigured) "Status: Configured & Operational" else "Status: Not Configured (API Key Required)",
                        color = if (policy.isProviderConfigured) Color(0xFF4CAF50) else Color(0xFFFFB74D),
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Admins cannot view raw facial biometrics or private biometric templates. External identity engines verify liveness securely.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text("Global Rules & Risk Policy", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = Color(0xFF18181D),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enforce 2FA on New Devices", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Requires secondary code when logging in from unrecognized device", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                        }
                        Switch(
                            checked = require2FA,
                            onCheckedChange = { require2FA = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFE2C55), checkedTrackColor = Color(0xFFFE2C55).copy(alpha = 0.4f))
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.08f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Max Failed Login Attempts", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Triggers 15-minute security cooldown", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                        }
                        Text("$maxAttempts attempts", color = Color(0xFFFE2C55), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.08f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Session Expiry Duration", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Inactive device sessions are invalidated", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                        }
                        Text("$sessionDays days", color = Color(0xFFFE2C55), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val updated = policy.copy(
                        requireTwoFactorForNewDevices = require2FA,
                        maxFailedLoginAttempts = maxAttempts,
                        sessionExpiryDays = sessionDays,
                        riskThreshold = riskThreshold
                    )
                    securityVm.updateAdminSecurityPolicy(updated) { success ->
                        if (success) {
                            Toast.makeText(context, "Global security policy updated", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Security Configuration")
            }
        }
    }
}
