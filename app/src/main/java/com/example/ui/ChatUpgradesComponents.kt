package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class ChatThemeSpec(
    val id: String,
    val name: String,
    val emoji: String,
    val bgTop: Color,
    val bgBottom: Color,
    val myBubbleStart: Color,
    val myBubbleEnd: Color,
    val peerBubble: Color,
    val accent: Color
)

@Immutable
data class VoiceFilterSpec(
    val id: String,
    val name: String,
    val emoji: String,
    val pitch: Float,
    val speed: Float,
    val subtitle: String
)

object ChatUpgradeCatalog {
    val THEMES: List<ChatThemeSpec> = listOf(
        ChatThemeSpec(
            id = "default",
            name = "Classic Dark",
            emoji = "🌑",
            bgTop = Color(0xFF0E0E14),
            bgBottom = Color(0xFF0A0A10),
            myBubbleStart = Color(0xFF5B4BFF),
            myBubbleEnd = Color(0xFF7A4BFF),
            peerBubble = Color(0xFF1E1E2C),
            accent = Color(0xFF7A4BFF)
        ),
        ChatThemeSpec(
            id = "neon_cyber",
            name = "Cyber Neon",
            emoji = "⚡",
            bgTop = Color(0xFF091224),
            bgBottom = Color(0xFF150A2B),
            myBubbleStart = Color(0xFF00C6FF),
            myBubbleEnd = Color(0xFF0072FF),
            peerBubble = Color(0xFF1A1E3A),
            accent = Color(0xFF00F0FF)
        ),
        ChatThemeSpec(
            id = "sunset_romance",
            name = "Sunset Rose",
            emoji = "🌅",
            bgTop = Color(0xFF280D1B),
            bgBottom = Color(0xFF120916),
            myBubbleStart = Color(0xFFFF2A6D),
            myBubbleEnd = Color(0xFFFF7A45),
            peerBubble = Color(0xFF2F1A2B),
            accent = Color(0xFFFF4D8D)
        ),
        ChatThemeSpec(
            id = "midnight_ocean",
            name = "Deep Ocean",
            emoji = "🌊",
            bgTop = Color(0xFF071B26),
            bgBottom = Color(0xFF092A3A),
            myBubbleStart = Color(0xFF00B4DB),
            myBubbleEnd = Color(0xFF0083B0),
            peerBubble = Color(0xFF112D3C),
            accent = Color(0xFF38BDF8)
        ),
        ChatThemeSpec(
            id = "emerald_zen",
            name = "Emerald Zen",
            emoji = "🌿",
            bgTop = Color(0xFF0A1F18),
            bgBottom = Color(0xFF091310),
            myBubbleStart = Color(0xFF10B981),
            myBubbleEnd = Color(0xFF059669),
            peerBubble = Color(0xFF162E25),
            accent = Color(0xFF34D399)
        ),
        ChatThemeSpec(
            id = "royal_gold",
            name = "Royal Gold",
            emoji = "👑",
            bgTop = Color(0xFF221909),
            bgBottom = Color(0xFF0F0D09),
            myBubbleStart = Color(0xFFD97706),
            myBubbleEnd = Color(0xFFF59E0B),
            peerBubble = Color(0xFF2A2212),
            accent = Color(0xFFFFD700)
        )
    )

    val VOICE_FILTERS: List<VoiceFilterSpec> = listOf(
        VoiceFilterSpec("original", "Natural", "🎤", 1.0f, 1.0f, "Crystal clear real voice"),
        VoiceFilterSpec("chipmunk", "Chipmunk", "🐿️", 1.48f, 1.08f, "High-pitched fun vibe"),
        VoiceFilterSpec("deep_bass", "Deep Bass", "🎙️", 0.72f, 0.94f, "Radio DJ cinematic voice"),
        VoiceFilterSpec("robot", "Cyber Bot", "🤖", 0.88f, 1.02f, "Futuristic modulated tone"),
        VoiceFilterSpec("helium", "Helium Pop", "🎈", 1.65f, 1.12f, "Ultra playful squeak"),
        VoiceFilterSpec("studio", "Studio Pro", "🎧", 1.06f, 1.0f, "Crisp podcast warmth")
    )

    fun resolveTheme(themeId: String): ChatThemeSpec =
        THEMES.firstOrNull { it.id == themeId } ?: THEMES.first()

    fun resolveVoiceFilter(filterId: String): VoiceFilterSpec =
        VOICE_FILTERS.firstOrNull { it.id == filterId } ?: VOICE_FILTERS.first()

    fun parseVoiceFilterFromCaption(text: String): VoiceFilterSpec {
        val regex = Regex("\\[VOICE:([a-z_]+)]")
        val id = regex.find(text)?.groupValues?.getOrNull(1) ?: "original"
        return resolveVoiceFilter(id)
    }

    fun stripSystemTags(text: String): String {
        return text
            .replace("[VANISH]", "")
            .replace(Regex("\\[VOICE:[a-z_]+]"), "")
            .trim()
    }
}

@Composable
fun VanishModeStatusBanner(
    onDisable: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF1A0F2E),
        border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.55f)),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.VisibilityOff, contentDescription = "Vanish Mode", tint = Color(0xFFC084FC), modifier = Modifier.size(18.dp))
                Column {
                    Text("Vanish Mode Active", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    Text("Seen vanish messages auto-delete when you leave chat", color = Color(0xFFD8B4FE), fontSize = 10.sp)
                }
            }
            TextButton(onClick = onDisable) {
                Text("Turn Off", color = Color(0xFFFF4D8D), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatThemeAndVoiceUpgradesSheet(
    currentThemeId: String,
    vanishModeEnabled: Boolean,
    currentVoiceFilterId: String,
    onSelectTheme: (String) -> Unit,
    onToggleVanishMode: (Boolean) -> Unit,
    onSelectVoiceFilter: (String) -> Unit,
    onStartWatchParty: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF12121D),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Chat Studio & Upgrades ✨", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)

            // Quick Watch Party + Vanish Mode Row
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Surface(
                    onClick = {
                        onDismiss()
                        onStartWatchParty()
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1D1536),
                    border = BorderStroke(1.dp, Color(0xFF00F0FF).copy(alpha = 0.45f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("🎬 Watch Party", color = Color(0xFF00F0FF), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        Text("Watch synced Reels live together", color = Color(0xFFB4B4CC), fontSize = 10.sp)
                    }
                }
                Surface(
                    onClick = { onToggleVanishMode(!vanishModeEnabled) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (vanishModeEnabled) Color(0xFF2D1247) else Color(0xFF1A1A28),
                    border = BorderStroke(1.dp, if (vanishModeEnabled) Color(0xFFC084FC) else Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (vanishModeEnabled) "👻 Vanish: ON" else "👻 Vanish: OFF",
                            color = if (vanishModeEnabled) Color(0xFFC084FC) else Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                        Text("Disappearing messages", color = Color(0xFFB4B4CC), fontSize = 10.sp)
                    }
                }
            }

            // Chat Themes Grid
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                Text("Chat Wallpaper Theme", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(156.dp)
            ) {
                items(ChatUpgradeCatalog.THEMES, key = { it.id }) { theme ->
                    val selected = theme.id == currentThemeId
                    Box(
                        modifier = Modifier
                            .height(70.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.verticalGradient(listOf(theme.bgTop, theme.myBubbleStart.copy(alpha = 0.45f))))
                            .border(
                                if (selected) 2.dp else 1.dp,
                                if (selected) theme.accent else Color.White.copy(alpha = 0.12f),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { onSelectTheme(theme.id) }
                            .padding(8.dp)
                    ) {
                        Column(modifier = Modifier.align(Alignment.BottomStart)) {
                            Text(theme.emoji, fontSize = 14.sp)
                            Text(theme.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                        }
                        if (selected) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = theme.accent,
                                modifier = Modifier.size(16.dp).align(Alignment.TopEnd)
                            )
                        }
                    }
                }
            }

            // Voice Changer Filters
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color(0xFF00F0FF), modifier = Modifier.size(16.dp))
                Text("Voice Note Changer Filter", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(132.dp)
            ) {
                items(ChatUpgradeCatalog.VOICE_FILTERS, key = { it.id }) { filter ->
                    val selected = filter.id == currentVoiceFilterId
                    Surface(
                        onClick = { onSelectVoiceFilter(filter.id) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) Color(0xFF1A2D42) else Color(0xFF191928),
                        border = BorderStroke(
                            if (selected) 1.5.dp else 1.dp,
                            if (selected) Color(0xFF00F0FF) else Color.White.copy(alpha = 0.08f)
                        ),
                        modifier = Modifier.height(58.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("${filter.emoji} ${filter.name}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text("${filter.pitch}x pitch", color = Color(0xFF9A9AB2), fontSize = 9.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}
