package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.MonetizationRequest
import com.example.model.User
import com.example.repository.MonetizationRepository
import com.example.utils.NumberFormatUtils
import kotlinx.coroutines.launch

@Composable
fun MonetizationRequestDialog(
    user: User,
    totalViews: Int,
    onDismiss: () -> Unit,
    onSubmitted: (String) -> Unit,
    minFollowersRequired: Int = 100,
    minViewsRequired: Int = 1000
) {
    val scope = rememberCoroutineScope()
    val totalSteps = 4
    var currentStep by remember { mutableIntStateOf(1) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val requiredFollowers = minFollowersRequired.coerceAtLeast(1)
    val requiredViews = minViewsRequired.coerceAtLeast(0)
    val meetsFollowers = user.followerCount >= requiredFollowers
    val meetsViews = totalViews >= requiredViews
    val remainingFollowers = (requiredFollowers - user.followerCount).coerceAtLeast(0)

    // Form states
    var fullName by remember { mutableStateOf(user.username) }
    var email by remember { mutableStateOf(user.email) }
    var phone by remember { mutableStateOf("") }
    var panOrTaxId by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(if (user.category.isNotEmpty()) user.category else "Entertainment") }
    val selectedMethods = remember { mutableStateListOf("video_ads", "reels_bonus", "live_gifts", "creator_subs") }
    var acceptedTerms by remember { mutableStateOf(false) }

    val categories = listOf(
        "Entertainment",
        "Comedy & Sketches",
        "Music & Dance",
        "Lifestyle & Vlogs",
        "Education & Tech",
        "Gaming"
    )

    val stepTitle = when (currentStep) {
        1 -> "Eligibility Check"
        2 -> "Creator Profile"
        3 -> "Earning Programs"
        else -> "Review & Submit"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.86f)
                .clip(RoundedCornerShape(26.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Gradient Top Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(listOf(Color(0xFF1E3A8A), Color(0xFF0F766E)))
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.MonetizationOn,
                                        contentDescription = null,
                                        tint = Color(0xFF4ADE80),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Apply for Monetization",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Step $currentStep of $totalSteps • $stepTitle",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 4-Segment Step Indicator
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (step in 1..totalSteps) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(5.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                step < currentStep -> Color(0xFF4ADE80)
                                                step == currentStep -> Color.White
                                                else -> Color.White.copy(alpha = 0.22f)
                                            }
                                        )
                                )
                            }
                        }
                    }
                }

                // Body Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (currentStep) {
                        1 -> {
                            Text(
                                text = "Creator Eligibility Verification",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Your account is checked live against the creator threshold set by admin.",
                                fontSize = 12.5.sp,
                                color = Color(0xFF94A3B8)
                            )

                            EligibilityProgressCard(
                                title = "Minimum ${NumberFormatUtils.formatCount(requiredFollowers)} Followers",
                                current = user.followerCount,
                                required = requiredFollowers,
                                unit = "followers",
                                passed = meetsFollowers
                            )

                            if (requiredViews > 0) {
                                EligibilityProgressCard(
                                    title = "Public Video Views",
                                    current = totalViews,
                                    required = requiredViews,
                                    unit = "views",
                                    passed = meetsViews
                                )
                            }

                            EligibilityCheckItem(
                                title = "Policy & Community Standing",
                                passed = true,
                                subtitle = "No active strikes • Original creator profile"
                            )

                            if (!meetsFollowers) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF7F1D1D).copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.6f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "You need ${NumberFormatUtils.formatCount(remainingFollowers)} more followers to unlock monetization apply.",
                                            color = Color(0xFFFECACA),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF14532D).copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, Color(0xFF4ADE80).copy(alpha = 0.55f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4ADE80), modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Follower criteria met! Proceed to complete your creator application.",
                                            color = Color(0xFFBBF7D0),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        2 -> {
                            Text(
                                text = "Creator Identity & Category",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            val fieldColors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedLabelColor = Color(0xFF38BDF8),
                                unfocusedLabelColor = Color(0xFF94A3B8),
                                cursorColor = Color(0xFF38BDF8)
                            )
                            OutlinedTextField(
                                value = fullName,
                                onValueChange = { fullName = it },
                                label = { Text("Full Legal Name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = fieldColors
                            )
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Contact Email") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = fieldColors
                            )
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it.filter { ch -> ch.isDigit() || ch == '+' }.take(14) },
                                label = { Text("Phone Number") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = fieldColors
                            )
                            OutlinedTextField(
                                value = panOrTaxId,
                                onValueChange = { panOrTaxId = it.uppercase().take(12) },
                                label = { Text("PAN / Tax ID (Optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = fieldColors
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Primary Content Category",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFCBD5E1)
                            )
                            categories.chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    pair.forEach { cat ->
                                        val isSelected = selectedCategory == cat
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { selectedCategory = cat },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) Color(0xFF1D4ED8).copy(alpha = 0.35f) else Color(0xFF1E293B),
                                            border = BorderStroke(
                                                1.2.dp,
                                                if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155)
                                            )
                                        ) {
                                            Text(
                                                text = cat,
                                                color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 11.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        3 -> {
                            Text(
                                text = "Choose Monetization Programs",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Enable revenue streams to activate once your account is approved:",
                                fontSize = 12.5.sp,
                                color = Color(0xFF94A3B8)
                            )
                            val methods = listOf(
                                Triple("video_ads", Icons.Default.PlayCircle, "In-Stream Video Ads" to "Earn ₹ CPM payouts from original videos"),
                                Triple("reels_bonus", Icons.Default.AutoAwesome, "Reels Performance Bonus" to "Monthly payouts for high-reach short reels"),
                                Triple("live_gifts", Icons.Default.CardGiftcard, "Live & Super Gifts" to "Receive coins & real cash tips from fans"),
                                Triple("creator_subs", Icons.Default.Loyalty, "Creator Subscriptions" to "Monthly supporter badges & exclusive content")
                            )
                            methods.forEach { (key, icon, info) ->
                                val (title, desc) = info
                                val checked = selectedMethods.contains(key)
                                ProgramToggleCard(
                                    icon = icon,
                                    title = title,
                                    subtitle = desc,
                                    checked = checked,
                                    onToggle = {
                                        if (checked) {
                                            if (selectedMethods.size > 1) selectedMethods.remove(key)
                                        } else {
                                            selectedMethods.add(key)
                                        }
                                    }
                                )
                            }
                        }

                        else -> {
                            Text(
                                text = "Review & Confirm Application",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SummaryRow("Creator", fullName.ifBlank { user.username })
                                    SummaryRow("Username", "@${user.username}")
                                    SummaryRow("Followers", NumberFormatUtils.formatCount(user.followerCount))
                                    SummaryRow("Category", selectedCategory)
                                    SummaryRow("Programs", "${selectedMethods.size} enabled")
                                    if (email.isNotBlank()) SummaryRow("Email", email)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Creator Partner Agreement: Only approved monetized creators earn video payouts & creator missions. Artificial views, bots, or reused copyright content lead to monetization revocation.",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { acceptedTerms = !acceptedTerms }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = acceptedTerms,
                                    onCheckedChange = { acceptedTerms = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF22C55E),
                                        uncheckedColor = Color(0xFF94A3B8)
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "I accept the Social India Creator Monetization Terms & Policies",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFFF87171),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Footer Buttons
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep > 1) {
                        OutlinedButton(
                            onClick = {
                                errorMessage = null
                                currentStep--
                            },
                            border = BorderStroke(1.dp, Color(0xFF475569)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Back", color = Color.White)
                        }
                    } else {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = Color(0xFF94A3B8))
                        }
                    }

                    if (currentStep < totalSteps) {
                        Button(
                            onClick = {
                                when {
                                    currentStep == 1 && !meetsFollowers -> {
                                        errorMessage = "Reach at least $requiredFollowers followers to apply."
                                    }
                                    currentStep == 2 && fullName.isBlank() -> {
                                        errorMessage = "Please enter your full legal name."
                                    }
                                    else -> {
                                        errorMessage = null
                                        currentStep++
                                    }
                                }
                            },
                            enabled = !(currentStep == 1 && !meetsFollowers),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2563EB),
                                disabledContainerColor = Color(0xFF334155),
                                disabledContentColor = Color(0xFF94A3B8)
                            )
                        ) {
                            Text("Continue →", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = {
                                if (!acceptedTerms) {
                                    errorMessage = "Please accept the Creator Monetization Terms first."
                                    return@Button
                                }
                                errorMessage = null
                                isSubmitting = true
                                scope.launch {
                                    val req = MonetizationRequest(
                                        userId = user.id,
                                        username = user.username,
                                        userAvatar = user.avatarUrl,
                                        fullName = fullName.trim(),
                                        email = email.trim(),
                                        phone = phone.trim(),
                                        followerCount = user.followerCount,
                                        totalViews = totalViews,
                                        contentCategory = selectedCategory,
                                        audienceAgeGroup = "18-34",
                                        primaryRegion = "India",
                                        selectedMethods = selectedMethods.toList(),
                                        panOrTaxId = panOrTaxId.trim(),
                                        acceptedTerms = acceptedTerms
                                    )
                                    val result = MonetizationRepository.submitMonetizationRequest(req)
                                    isSubmitting = false
                                    if (result.isSuccess) {
                                        onSubmitted(result.getOrDefault(req.id))
                                    } else {
                                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to submit application"
                                    }
                                }
                            },
                            enabled = !isSubmitting,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Submit Application", fontWeight = FontWeight.ExtraBold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EligibilityProgressCard(
    title: String,
    current: Int,
    required: Int,
    unit: String,
    passed: Boolean
) {
    val progress = if (required <= 0) 1f else (current.toFloat() / required.toFloat()).coerceIn(0f, 1f)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1E293B),
        border = BorderStroke(
            1.dp,
            if (passed) Color(0xFF4ADE80).copy(alpha = 0.5f) else Color(0xFF334155)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (passed) Color(0xFF4ADE80) else Color(0xFF38BDF8),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${NumberFormatUtils.formatCount(current)} / ${NumberFormatUtils.formatCount(required)} $unit",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (passed) Color(0xFF4ADE80) else Color(0xFF94A3B8)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (passed) Color(0xFF4ADE80) else Color(0xFF38BDF8),
                trackColor = Color(0xFF0F172A)
            )
        }
    }
}

@Composable
private fun ProgramToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (checked) Color(0xFF1E3A8A).copy(alpha = 0.32f) else Color(0xFF1E293B),
        border = BorderStroke(1.dp, if (checked) Color(0xFF38BDF8) else Color(0xFF334155)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (checked) Color(0xFF38BDF8).copy(alpha = 0.2f) else Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = if (checked) Color(0xFF38BDF8) else Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Color.White)
                Text(subtitle, fontSize = 11.5.sp, color = Color(0xFF94A3B8))
            }
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF38BDF8),
                    uncheckedColor = Color(0xFF64748B)
                )
            )
        }
    }
}

@Composable
private fun EligibilityCheckItem(title: String, passed: Boolean, subtitle: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1E293B),
        border = BorderStroke(1.dp, if (passed) Color(0xFF4ADE80).copy(alpha = 0.4f) else Color(0xFF334155)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (passed) Color(0xFF4ADE80) else Color(0xFFF87171),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Color.White)
                Text(subtitle, fontSize = 11.5.sp, color = Color(0xFF94A3B8))
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF94A3B8), fontSize = 12.5.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Color.White)
    }
}
