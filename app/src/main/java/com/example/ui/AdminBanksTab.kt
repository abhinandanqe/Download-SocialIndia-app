package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.model.IndianBank
import com.example.model.IndianBanksData
import com.example.model.RepositoryManager
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminBanksTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var banks by remember { mutableStateOf<List<IndianBank>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddBankDialog by remember { mutableStateOf(false) }
    var editingBank by remember { mutableStateOf<IndianBank?>(null) }

    val loadBanks = {
        scope.launch {
            isLoading = true
            banks = IndianBanksData.fetchBanks()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadBanks()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Indian Banks Management", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                Text("Add, edit, or upload bank logos for user payouts", color = Color(0xFF94A3B8), fontSize = 12.sp)
            }
            Button(
                onClick = {
                    editingBank = null
                    showAddBankDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Bank", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFEE1D52))
            }
        } else if (banks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No banks added yet", color = Color(0xFF94A3B8))
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = banks,
                    key = { index, bank -> if (bank.id.isNotEmpty()) bank.id else "bank_$index" }
                ) { _, bank ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SubcomposeAsyncImage(
                                model = bank.logoUrl,
                                contentDescription = bank.name,
                                loading = {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(Color(0xFF334155)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(bank.code.take(2), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                },
                                error = {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(Color(0xFF334155)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(bank.code.take(3), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                },
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .padding(2.dp),
                                contentScale = ContentScale.Fit
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(bank.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Code: ${bank.code}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = {
                                    editingBank = bank
                                    showAddBankDialog = true
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        try {
                                            FirebaseFirestore.getInstance().collection("admin_banks").document(bank.id).delete().await()
                                            Toast.makeText(context, "${bank.name} deleted", Toast.LENGTH_SHORT).show()
                                            loadBanks()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Failed to delete", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddBankDialog) {
        var name by remember { mutableStateOf(editingBank?.name ?: "") }
        var code by remember { mutableStateOf(editingBank?.code ?: "") }
        var logoUrl by remember { mutableStateOf(editingBank?.logoUrl ?: "") }
        var isUploadingLogo by remember { mutableStateOf(false) }

        val logoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                isUploadingLogo = true
                scope.launch {
                    try {
                        val uploadedUrl = withContext(Dispatchers.IO) {
                            RepositoryManager.mediaRepository.uploadMedia(uri)
                        }
                        if (!uploadedUrl.isNullOrEmpty()) {
                            logoUrl = uploadedUrl
                            Toast.makeText(context, "Logo uploaded successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to upload logo image", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    isUploadingLogo = false
                }
            }
        }

        Dialog(onDismissRequest = { if (!isUploadingLogo) showAddBankDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(18.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        if (editingBank != null) "Edit Bank" else "Add New Bank",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Bank Name (e.g. State Bank of India)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.uppercase() },
                        label = { Text("Bank Code (e.g. SBI, HDFC, ICICI)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Logo Upload & Preview Section
                    Text("Bank Logo Image", color = Color(0xFFCBD5E1), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(1.dp, Color(0xFF00E5FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (logoUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = logoUrl,
                                    contentDescription = "Bank Logo",
                                    modifier = Modifier.size(48.dp).clip(CircleShape),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(28.dp))
                            }
                        }

                        Button(
                            onClick = { logoPickerLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isUploadingLogo) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Uploading...", fontSize = 11.sp, color = Color.White)
                            } else {
                                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (logoUrl.isNotEmpty()) "Change Image" else "Upload Logo Image", fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = logoUrl,
                        onValueChange = { logoUrl = it },
                        label = { Text("Or Paste Logo Image URL") },
                        placeholder = { Text("https://example.com/logo.png") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddBankDialog = false }) {
                            Text("Cancel", color = Color(0xFF94A3B8))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (name.isNotBlank() && code.isNotBlank()) {
                                    scope.launch {
                                        try {
                                            val db = FirebaseFirestore.getInstance()
                                            val docRef = if (editingBank != null && editingBank!!.id.isNotEmpty()) {
                                                db.collection("admin_banks").document(editingBank!!.id)
                                            } else {
                                                db.collection("admin_banks").document()
                                            }
                                            val finalLogo = logoUrl.ifEmpty {
                                                "https://images.seeklogo.com/logo-png/33/1/state-bank-of-india-sbi-logo-png_seeklogo-330691.png"
                                            }
                                            docRef.set(
                                                mapOf(
                                                    "id" to docRef.id,
                                                    "name" to name.trim(),
                                                    "code" to code.trim(),
                                                    "logoUrl" to finalLogo
                                                )
                                            ).await()
                                            Toast.makeText(context, "Bank saved successfully!", Toast.LENGTH_SHORT).show()
                                            showAddBankDialog = false
                                            loadBanks()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Failed to save bank", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "Please enter bank name and code", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                        ) {
                            Text("Save Bank", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
