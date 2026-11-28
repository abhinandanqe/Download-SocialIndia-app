package com.example.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainActivity
import com.example.R

class CrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val crashInfo = CrashHandler.CrashInfo(
            exceptionName = intent.getStringExtra(CrashHandler.EXTRA_EXCEPTION_NAME)
                ?.takeIf { it.isNotBlank() } ?: "Unknown Exception",
            message = intent.getStringExtra(CrashHandler.EXTRA_MESSAGE)
                ?.takeIf { it.isNotBlank() } ?: "(No error message)",
            fileName = intent.getStringExtra(CrashHandler.EXTRA_FILE_NAME)
                ?.takeIf { it.isNotBlank() } ?: "Unknown file",
            lineNumber = intent.getIntExtra(CrashHandler.EXTRA_LINE_NUMBER, -1),
            methodLocation = intent.getStringExtra(CrashHandler.EXTRA_METHOD_LOCATION).orEmpty(),
            threadName = intent.getStringExtra(CrashHandler.EXTRA_THREAD_NAME)
                ?.takeIf { it.isNotBlank() } ?: "unknown",
            stackTrace = intent.getStringExtra(CrashHandler.EXTRA_STACK_TRACE)
                ?.takeIf { it.isNotBlank() } ?: "No stack trace available."
        )

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFEF5350),
                    onPrimary = Color.White,
                    background = Color(0xFF101218),
                    surface = Color(0xFF181B24),
                    surfaceVariant = Color(0xFF222634),
                    onBackground = Color(0xFFF3F4F6),
                    onSurface = Color(0xFFF3F4F6),
                    error = Color(0xFFFF6B6B)
                )
            ) {
                CrashScreen(
                    crashInfo = crashInfo,
                    onCopy = {
                        copyCrashToClipboard(crashInfo.toCopyableText())
                    },
                    onRestart = {
                        val restartIntent = Intent(this, MainActivity::class.java).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                            )
                        }
                        startActivity(restartIntent)
                        finish()
                    }
                )
            }
        }
    }

    private fun copyCrashToClipboard(text: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Crash Report", text))
            Toast.makeText(this, getString(R.string.crash_copied_toast), Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            Toast.makeText(this, getString(R.string.crash_copy_failed_toast), Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrashScreen(
    crashInfo: CrashHandler.CrashInfo,
    onCopy: () -> Unit,
    onRestart: () -> Unit
) {
    var copied by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = stringResource(R.string.crash_title),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.crash_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        onCopy()
                        copied = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("copy_crash_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.crash_copy_button),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (copied) {
                            stringResource(R.string.crash_copied_button)
                        } else {
                            stringResource(R.string.crash_copy_button)
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = onRestart,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("restart_app_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.crash_restart_button),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.crash_restart_button))
                }
            }

            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CrashField(
                                label = stringResource(R.string.crash_label_exception),
                                value = crashInfo.exceptionName,
                                valueColor = MaterialTheme.colorScheme.error,
                                bold = true
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                            CrashField(
                                label = stringResource(R.string.crash_label_message),
                                value = crashInfo.message
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                            CrashField(
                                label = stringResource(R.string.crash_label_location),
                                value = crashInfo.fileAndLine(),
                                valueColor = Color(0xFFFFD54F),
                                bold = true
                            )
                            if (crashInfo.methodLocation.isNotBlank()) {
                                CrashField(
                                    label = stringResource(R.string.crash_label_method),
                                    value = crashInfo.methodLocation
                                )
                            }
                            CrashField(
                                label = stringResource(R.string.crash_label_thread),
                                value = crashInfo.threadName
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.crash_label_stacktrace),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFF9CA3AF),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = crashInfo.stackTrace,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                color = Color(0xFFE5E7EB),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun CrashField(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    bold: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF9CA3AF)
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = valueColor
        )
    }
}
