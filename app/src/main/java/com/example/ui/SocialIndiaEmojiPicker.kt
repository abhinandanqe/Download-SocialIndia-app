package com.example.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import com.example.model.RepositoryManager

/**
 * Lightweight, zero-asset, system-rendered Unicode Emoji Picker.
 * Consumes 0 MB of APK storage by relying purely on Android system fonts and Unicode.
 * Automatically persists user's recently used emojis per-account.
 */
@Composable
fun SocialIndiaEmojiPicker(
    onEmojiSelected: (String) -> Unit,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    val recentEmojis = remember {
        mutableStateListOf<String>().apply {
            addAll(loadRecentEmojis(context))
        }
    }

    val categories = listOf(
        "🕒 Recent" to recentEmojis.toList(),
        "😀 Smileys" to EMOJI_SMILEYS,
        "👋 People" to EMOJI_PEOPLE,
        "🐶 Animals" to EMOJI_ANIMALS,
        "🍔 Food" to EMOJI_FOOD,
        "⚽ Activities" to EMOJI_ACTIVITIES,
        "🚗 Travel" to EMOJI_TRAVEL,
        "💡 Objects" to EMOJI_OBJECTS,
        "❤️ Symbols" to EMOJI_SYMBOLS,
        "🚩 Flags" to EMOJI_FLAGS
    )

    // Current displayed emoji list
    val currentEmojis = remember(selectedCategoryIndex, searchQuery, recentEmojis.size) {
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            ALL_SEARCHABLE_EMOJIS.filter { it.contains(q) || matchesEmojiKeyword(it, q) }
        } else {
            categories[selectedCategoryIndex].second
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: Category Tabs & Search
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search emoji...", fontSize = 12.sp, color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                )

                if (onClose != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }
            }

            // Category Icons Row
            if (searchQuery.isBlank()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(categories) { index, (label, _) ->
                        val isSelected = selectedCategoryIndex == index
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.clickable { selectedCategoryIndex = index }
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

            // Emojis LazyVerticalGrid
            if (currentEmojis.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No emojis found" else "No recent emojis yet",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 44.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    items(currentEmojis, key = { it }) { emoji ->
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable {
                                    saveRecentEmoji(context, emoji)
                                    if (!recentEmojis.contains(emoji)) {
                                        recentEmojis.add(0, emoji)
                                    }
                                    onEmojiSelected(emoji)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = emoji,
                                fontSize = 24.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val PREFS_RECENT_EMOJIS = "si_recent_emojis"

private fun loadRecentEmojis(context: Context): List<String> {
    val uid = RepositoryManager.getCurrentUserId().ifEmpty { "global" }
    val prefs = context.getSharedPreferences("${PREFS_RECENT_EMOJIS}_$uid", Context.MODE_PRIVATE)
    val raw = prefs.getString("recent_emojis", "") ?: ""
    if (raw.isBlank()) {
        return listOf("❤️", "🔥", "😂", "👍", "👏", "🥰", "😍", "🎉", "✨", "💯")
    }
    return raw.split(",").filter { it.isNotBlank() }.take(35)
}

private fun saveRecentEmoji(context: Context, emoji: String) {
    val uid = RepositoryManager.getCurrentUserId().ifEmpty { "global" }
    val prefs = context.getSharedPreferences("${PREFS_RECENT_EMOJIS}_$uid", Context.MODE_PRIVATE)
    val current = loadRecentEmojis(context).toMutableList()
    current.remove(emoji)
    current.add(0, emoji)
    val saved = current.take(35).joinToString(",")
    prefs.edit().putString("recent_emojis", saved).apply()
}

private fun matchesEmojiKeyword(emoji: String, query: String): Boolean {
    val q = query.lowercase()
    return when (emoji) {
        "❤️", "💖", "💕", "💘" -> q.contains("love") || q.contains("heart") || q.contains("pyaar")
        "🔥" -> q.contains("fire") || q.contains("hot") || q.contains("aag")
        "😂", "🤣", "😆" -> q.contains("laugh") || q.contains("lol") || q.contains("funny") || q.contains("hasi")
        "👍", "👏", "🙌" -> q.contains("like") || q.contains("clap") || q.contains("good") || q.contains("badiya")
        "🇮🇳" -> q.contains("india") || q.contains("flag") || q.contains("desh")
        else -> false
    }
}

val EMOJI_SMILEYS = listOf(
    "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🥹", "😊",
    "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙",
    "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎",
    "🥸", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁",
    "☹️", "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😮‍💨", "😤",
    "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰",
    "😥", "😓", "🤗", "🤔", "🫣", "🫢", "🫡", "🤫", "🫠", "🤥"
)

val EMOJI_PEOPLE = listOf(
    "👋", "🤚", "🖐️", "✋", "🖖", "🫱", "🫲", "🫳", "🫴", "👌",
    "🤌", "🤏", "✌️", "🤞", "🫰", "🤟", "🤘", "🤙", "👈", "👉",
    "👆", "🖕", "👇", "☝️", "🫵", "👍", "👎", "✊", "👊", "🤛",
    "🤜", "👏", "🙌", "🫶", "👐", "🤲", "🤝", "🙏", "✍️", "💅",
    "🤳", "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻", "👃", "🫀",
    "🫁", "🧠", "👀", "👁️", "👅", "👄", "🫦", "👶", "🧒", "👦",
    "👧", "🧑", "👨", "👩", "👱", "👴", "👵", "🧓", "👲", "🧕"
)

val EMOJI_ANIMALS = listOf(
    "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐻‍❄️", "🐨",
    "🐯", "🦁", "🐮", "🐷", "🐽", "🐸", "🐵", "🙈", "🙉", "🙊",
    "🐒", "🐔", "🐧", "🐦", "🐤", "🐣", "🐥", "🦆", "🦅", "🦉",
    "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🪱", "🐛", "🦋", "🐌",
    "🐞", "🐜", "🪰", "🪲", "🪳", "🦟", "🦗", "🕷️", "🦂", "🐢",
    "🐍", "🦎", "🦖", "🦕", "🐙", "🦑", "🦐", "🦞", "🦀", "🐡",
    "🐠", "🐟", "🐬", "🐳", "🐋", "🦈", "🦭", "🐊", "🐅", "🐆"
)

val EMOJI_FOOD = listOf(
    "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐",
    "🍈", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍅", "🍆", "🥑",
    "🥦", "🥬", "🥒", "🌶️", "🫑", "🌽", "🥕", "🫒", "🧄", "🧅",
    "🥔", "🍠", "🥐", "🥯", "🍞", "🥖", "🥨", "🧀", "🥚", "🍳",
    "🧈", "🥞", "🧇", "🥓", "🥩", "🍗", "🍖", "🌭", "🍔", "🍟",
    "🍕", "🫓", "🥪", "🥙", "🧆", "🌮", "🌯", "🫔", "🥗", "🥘",
    "🍝", "🍜", "🍲", "🍛", "🍣", "🍱", "🥟", "🦪", "🍤", "🍦",
    "🍧", "🍨", "🍩", "🍪", "🎂", "🍰", "🧁", "🥧", "🍫", "🍬",
    "🍭", "🍮", "🍯", "🍼", "🥛", "☕", "🫖", "🍵", "🧃", "🥤"
)

val EMOJI_ACTIVITIES = listOf(
    "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱",
    "🪀", "🏓", "🏸", "🏒", "🏑", "🥍", "🏏", "🪃", "🥅", "⛳",
    "🪁", "🏹", "🎣", "🤿", "🥊", "🥋", "🎽", "🛹", "🛼", "🛷",
    "⛸️", "🥌", "🎿", "⛷️", "🏂", "🪂", "🏋️", "🤼", "🤸", "⛹️",
    "🤺", "🤾", "🏌️", "🏇", "🧘", "🏄", "🏊", "🤽", "🚣", "🧗",
    "🏆", "🥇", "🥈", "🥉", "🏅", "🎖️", "🏵️", "🎗️", "🎫", "🎟️",
    "🎪", "🤹", "🎭", "🩰", "🎨", "🎬", "🎤", "🎧", "🎼", "🎹",
    "🥁", "🪘", "🎷", "🎺", "🪗", "🎸", "🪕", "🎻", "🎲", "🎯"
)

val EMOJI_TRAVEL = listOf(
    "🚗", "🚙", "🛻", "🚐", "🚚", "🚛", "🚜", "🏎️", "🏍️", "🛵",
    "🚲", "🛴", "🚨", "🚔", "🚍", "🚘", "🚖", "🚡", "🚠", "🚟",
    "🚋", "🚞", "🚅", "🚄", "🚆", "🚇", "🚊", "🚉", "✈️", "🛫",
    "🛬", "💺", "🛰️", "🚀", "🛸", "🚁", "🛶", "⛵", "🚤", "🛳️",
    "🚢", "⚓", "⛽", "🛑", "🚥", "🚦", "🧭", "🏔️", "⛰️", "🌋",
    "🗻", "🏕️", "🏖️", "🏜️", "🏝️", "🏞️", "🏟️", "🏛️", "🏠", "🏡",
    "🏢", "🏣", "🏤", "🏥", "🏦", "🏨", "🏩", "🏪", "🏫", "🏬"
)

val EMOJI_OBJECTS = listOf(
    "⌚", "📱", "📲", "💻", "⌨️", "🖥️", "🖨️", "🖱️", "📷", "📸",
    "📹", "🎥", "📽️", "🎞️", "📞", "☎️", "📟", "📠", "📺", "📻",
    "🎙️", "⏱️", "⏲️", "⏰", "🕰️", "⌛", "⏳", "📡", "🔋", "🔌",
    "💡", "🔦", "🕯️", "🧯", "💸", "💵", "💴", "💶", "💷", "🪙",
    "💰", "💳", "💎", "⚖️", "🪜", "🧰", "🔧", "🔨", "⚒️", "🛠️",
    "⛏️", "🔩", "⚙️", "⛓️", "🧲", "🔫", "💣", "🧨", "🔪", "🗡️",
    "🛡️", "🔮", "📿", "🧿", "💈", "🔭", "🔬", "💊", "💉", "🩸"
)

val EMOJI_SYMBOLS = listOf(
    "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
    "❤️‍🔥", "❤️‍🩹", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝",
    "💟", "☮️", "✝️", "☪️", "🕉️", "☸️", "✡️", "🔯", "🕎", "☯️",
    "☦️", "🛐", "⛎", "♈", "♉", "♊", "♋", "♌", "♍", "♎",
    "♏", "♐", "♑", "♒", "♓", "🆔", "☣️", "📴", "📳", "🈶",
    "🈚", "🈸", "🈺", "🈷️", "✴️", "🆚", "💮", "🉐", "㊙️", "㊗️",
    "🈴", "🈵", "🈹", "🈲", "🅰️", "🅱️", "🆎", "🆑", "🅾️", "🆘",
    "❌", "⭕", "🛑", "⛔", "🚫", "💯", "💢", "♨️", "❗", "❕",
    "❓", "❔", "‼️", "⁉️", "⚠️", "🔱", "⚜️", "🔰", "♻️", "✅",
    "❇️", "✳️", "❎", "🌐", "💠", "Ⓜ️", "🌀", "💤", "🏧", "♿"
)

val EMOJI_FLAGS = listOf(
    "🇮🇳", "🇺🇸", "🇬🇧", "🇨🇦", "🇦🇺", "🇩🇪", "🇫🇷", "🇯🇵", "🇰🇷", "🇧🇷",
    "🇮🇹", "🇪🇸", "🇷🇺", "🇨🇳", "🇿🇦", "🇦🇪", "🇸🇦", "🇲🇽", "🇮🇩", "🇹🇷",
    "🇳🇱", "🇨🇭", "🇸🇪", "🇳🇴", "🇳🇿", "🇸🇬", "🇮🇪", "🇵🇰", "🇧🇩", "🇳🇵",
    "🇱🇰", "🇲🇾", "🇵🇭", "🇹🇭", "🇻🇳", "🇪🇬", "🇳🇬", "🇰🇪", "🇦🇷", "🇨🇱",
    "🏁", "🚩", "🎌", "🏴", "🏳️", "🏳️‍🌈", "🏳️‍⚧️", "🏴‍☠️"
)

private val ALL_SEARCHABLE_EMOJIS: List<String> by lazy {
    (EMOJI_SMILEYS + EMOJI_PEOPLE + EMOJI_ANIMALS + EMOJI_FOOD +
     EMOJI_ACTIVITIES + EMOJI_TRAVEL + EMOJI_OBJECTS + EMOJI_SYMBOLS + EMOJI_FLAGS).distinct()
}
