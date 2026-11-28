package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Dialog to wrap a text message in an Instagram-style interactive Gift Box.
 */
@Composable
fun GiftBoxWrapDialog(
    initialText: String,
    currentUserId: String,
    onDismiss: () -> Unit,
    onSendGiftBox: (message: String, styleKey: String, coins: Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var messageText by remember { mutableStateOf(initialText) }
    var selectedStyleKey by remember { mutableStateOf("classic_red") }
    var selectedCoins by remember { mutableStateOf(0) }
    var userCoinBalance by remember { mutableStateOf(0L) }
    var isLoadingBalance by remember { mutableStateOf(true) }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            try {
                val doc = FirebaseFirestore.getInstance().collection("users").document(currentUserId).get().await()
                userCoinBalance = doc.getLong("coins") ?: 0L
            } catch (e: Exception) {}
            isLoadingBalance = false
        }
    }

    val styles = listOf(
        Triple("classic_red", "Classic Red 🎀", listOf(Color(0xFFC62828), Color(0xFFE53935))),
        Triple("diamond_blue", "Diamond Blue 💎", listOf(Color(0xFF0072FF), Color(0xFF00C6FF))),
        Triple("romantic_pink", "Pink Romance 💖", listOf(Color(0xFFE91E63), Color(0xFFFF4081))),
        Triple("royal_gold", "Royal Gold 👑", listOf(Color(0xFFB8860B), Color(0xFFFFD700)))
    )

    val coinOptions = listOf(0, 5, 10, 25, 50)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Wrap Message in Gift Box 🎁", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Recipient will see a wrapped 3D gift box with ribbon. Tapping it plays an unboxing animation to reveal your message!",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )

                // Message Text Field
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    label = { Text("Secret Message to Wrap") },
                    placeholder = { Text("Write your sweet message or surprise here...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )

                // Box Theme Selection
                Text("Select Gift Box Theme:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    styles.forEach { (key, label, gradient) ->
                        val isSelected = selectedStyleKey == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.horizontalGradient(gradient))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedStyleKey = key },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label.split(" ").last(),
                                fontSize = 20.sp
                            )
                        }
                    }
                }

                // Attach Coins (Optional)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Attach Bonus Coins (Optional):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CoinIcon(size = 12.dp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Balance: $userCoinBalance",
                            fontSize = 11.sp,
                            color = Color(0xFFFFD700)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    coinOptions.forEach { coins ->
                        val isSelected = selectedCoins == coins
                        Surface(
                            onClick = { selectedCoins = coins },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) Color(0xFFFFD700) else Color(0xFF262626),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFFFFD700) else Color(0xFF383838)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (coins > 0) {
                                    CoinIcon(size = 11.dp)
                                    Spacer(modifier = Modifier.width(3.dp))
                                }
                                Text(
                                    text = if (coins == 0) "None" else "+$coins",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else Color.White,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanText = messageText.trim()
                    if (cleanText.isBlank()) {
                        Toast.makeText(context, "Please write a message to wrap", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (selectedCoins > 0 && selectedCoins > userCoinBalance) {
                        Toast.makeText(context, "Insufficient coin balance (Have: $userCoinBalance)", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    scope.launch {
                        // Deduct coins if attached
                        if (selectedCoins > 0 && currentUserId.isNotEmpty()) {
                            try {
                                FirebaseFirestore.getInstance().collection("users").document(currentUserId)
                                    .update("coins", FieldValue.increment(-selectedCoins.toLong()))
                                    .await()
                            } catch (e: Exception) {}
                        }
                        onSendGiftBox(cleanText, selectedStyleKey, selectedCoins)
                        Toast.makeText(context, "Gift Box wrapped & sent! 🎁", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
            ) {
                Text("Wrap & Send 🎁", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
