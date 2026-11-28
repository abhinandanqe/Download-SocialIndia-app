package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.GroupPoll
import com.example.model.GroupPollOption
import com.example.utils.SoundManager
import java.util.UUID

@Composable
fun PollCreatorDialog(
    creatorId: String,
    onDismiss: () -> Unit,
    onPollCreated: (GroupPoll) -> Unit
) {
    val context = LocalContext.current
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("Yes", "No") }
    var multipleChoice by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Poll,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Create a Poll",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    IconButton(onClick = {
                        SoundManager.playClickSound()
                        onDismiss()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Question Input
                OutlinedTextField(
                    value = question,
                    onValueChange = { if (it.length <= 120) question = it },
                    label = { Text("Ask a question...") },
                    placeholder = { Text("e.g. When should we have our next meetup?") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Options (${options.size}/6)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Options list
                LazyColumn(
                    modifier = Modifier.heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(options) { index, optionText ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = optionText,
                                onValueChange = { newText ->
                                    if (newText.length <= 50) {
                                        options[index] = newText
                                    }
                                },
                                placeholder = { Text("Option ${index + 1}") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                            if (options.size > 2) {
                                IconButton(onClick = {
                                    SoundManager.playClickSound()
                                    options.removeAt(index)
                                }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove option",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (options.size < 6) {
                    TextButton(
                        onClick = {
                            SoundManager.playClickSound()
                            options.add("")
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add another option")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Multiple choice switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Allow multiple answers", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("Members can select more than one option", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = multipleChoice,
                        onCheckedChange = { multipleChoice = it }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        SoundManager.playClickSound()
                        val validOptions = options.map { it.trim() }.filter { it.isNotEmpty() }
                        if (question.trim().isEmpty()) {
                            Toast.makeText(context, "Please ask a question", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (validOptions.size < 2) {
                            Toast.makeText(context, "Please enter at least 2 non-empty options", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val poll = GroupPoll(
                            id = "poll_${UUID.randomUUID().toString().take(8)}",
                            question = question.trim(),
                            options = validOptions.mapIndexed { idx, opt ->
                                GroupPollOption(id = idx, text = opt, votes = emptyList())
                            },
                            multipleChoice = multipleChoice,
                            createdBy = creatorId
                        )
                        onPollCreated(poll)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Share Poll to Group", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
