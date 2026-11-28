package com.example.ui.templates

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.example.model.RepositoryManager
import com.example.ui.editor.EditorDeviceProfile
import com.example.ui.editor.EditorProjectSnapshot
import com.example.ui.editor.findEditorFont
import com.example.utils.ExoPlayerUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Full Instagram-Style Template Browser Screen:
 * - Tabs: Recommended, Trending, Saved, Festival, Birthday, Wedding, Business
 * - Live search across Hindi & English titles, tags, prompts
 * - Recently Used strip + Offline Saved Templates support
 * - Shimmer skeleton loaders, Error state with Retry, and Empty states
 * - Autoplay preview modal on tap with live beat-cut animation, text layers & audio
 * - Brand Kit 1-tap studio + Creator Publish Template button
 */
@Composable
fun TemplateBrowserScreen(
    initialTemplateId: String? = null,
    onClose: () -> Unit,
    onTemplateReadyForEditor: (snapshot: EditorProjectSnapshot, uris: List<Uri>, template: ContentTemplate) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentUserId = RepositoryManager.getCurrentUserId()
    val lowEndMode = remember { EditorDeviceProfile.isLowEndDevice(context) }

    var allTemplates by remember { mutableStateOf<List<ContentTemplate>>(emptyList()) }
    var savedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var unlockedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var recentIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isOffline by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var selectedTab by remember { mutableStateOf("Recommended") }
    var searchQuery by remember { mutableStateOf("") }

    var previewTemplate by remember { mutableStateOf<ContentTemplate?>(null) }
    var slotFillerTemplate by remember { mutableStateOf<ContentTemplate?>(null) }
    var lockedTemplateToUnlock by remember { mutableStateOf<ContentTemplate?>(null) }
    var showBrandKitSheet by remember { mutableStateOf(false) }
    var showPublishSheet by remember { mutableStateOf(false) }
    var brandKit by remember { mutableStateOf(TemplateRepository.loadBrandKitLocally(context)) }

    fun loadData() {
        scope.launch {
            isLoading = true
            loadError = null
            try {
                val (list, offlineFlag) = TemplateRepository.fetchAllTemplates(context)
                allTemplates = list
                isOffline = offlineFlag
                savedIds = TemplateRepository.getSavedTemplateIds(context)
                unlockedIds = TemplateRepository.getUnlockedTemplateIds(context)
                recentIds = TemplateRepository.getRecentlyUsedIds(context)
            } catch (e: Exception) {
                loadError = e.message ?: "Unable to load templates."
            } finally {
                isLoading = false
            }
        }
    }

    var handledInitialId by remember(initialTemplateId) { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loadData()
    }

    LaunchedEffect(initialTemplateId, allTemplates) {
        if (!handledInitialId && !initialTemplateId.isNullOrBlank() && allTemplates.isNotEmpty()) {
            val match = allTemplates.firstOrNull { it.id == initialTemplateId }
            if (match != null) {
                previewTemplate = match
                handledInitialId = true
            }
        }
    }

    fun isTemplateLocked(tpl: ContentTemplate): Boolean {
        if (!tpl.isPremium || tpl.price <= 0.0) return false
        if (tpl.creatorId == currentUserId && currentUserId.isNotBlank()) return false
        return tpl.id !in unlockedIds
    }

    val recentlyUsedTemplates = remember(allTemplates, recentIds) {
        val byId = allTemplates.associateBy { it.id }
        recentIds.mapNotNull { byId[it] }
    }

    val filteredTemplates = remember(allTemplates, selectedTab, searchQuery, savedIds) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        val tabList = when (selectedTab) {
            "Recommended" -> allTemplates
            "Trending" -> allTemplates.sortedByDescending { it.usageCount }
            "Saved" -> allTemplates.filter { it.id in savedIds }
            else -> allTemplates.filter {
                it.category.equals(selectedTab, ignoreCase = true) ||
                    it.tags.any { tag -> tag.equals(selectedTab, ignoreCase = true) }
            }
        }
        if (q.isBlank()) tabList
        else tabList.filter { tpl ->
            tpl.title.lowercase(Locale.ROOT).contains(q) ||
                tpl.category.lowercase(Locale.ROOT).contains(q) ||
                tpl.typeLabel.lowercase(Locale.ROOT).contains(q) ||
                tpl.promptText.lowercase(Locale.ROOT).contains(q) ||
                tpl.tags.any { it.lowercase(Locale.ROOT).contains(q) } ||
                tpl.textLayers.any { it.text.lowercase(Locale.ROOT).contains(q) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090910))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("templates_close_btn")
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back to Camera", tint = Color.White)
                }
                Column {
                    Text("Templates", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text("India Packs • Reels, Stories & Carousels", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(brandKit.primaryColorArgb).copy(alpha = 0.22f),
                    border = BorderStroke(1.dp, Color(brandKit.primaryColorArgb)),
                    modifier = Modifier
                        .clickable { showBrandKitSheet = true }
                        .testTag("templates_brand_kit_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = "Brand Kit", tint = Color.White, modifier = Modifier.size(14.dp))
                        Text("Brand", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFEE1D52),
                    modifier = Modifier
                        .clickable { showPublishSheet = true }
                        .testTag("templates_publish_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Publish Template", tint = Color.White, modifier = Modifier.size(15.dp))
                        Text("Create", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search Diwali, Shaadi, Holi, Eid, Add Yours...", color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.65f)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear Search", tint = Color.White.copy(alpha = 0.7f))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF151522),
                unfocusedContainerColor = Color(0xFF151522),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFFEE1D52),
                unfocusedBorderColor = Color.White.copy(alpha = 0.12f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .testTag("templates_search_input")
        )

        Spacer(Modifier.height(10.dp))

        // 3. Category Tabs (Recommended, Trending, Saved, Festival, Birthday, Wedding, Business)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TEMPLATE_BROWSER_TABS.forEach { tab ->
                val active = tab == selectedTab
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (active) Color(0xFFEE1D52) else Color(0xFF181826),
                    border = BorderStroke(1.dp, if (active) Color(0xFFEE1D52) else Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedTab = tab
                        }
                        .testTag("templates_tab_${tab.lowercase(Locale.ROOT)}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (tab == "Saved") {
                            Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                        Text(
                            text = if (tab == "Saved" && savedIds.isNotEmpty()) "$tab (${savedIds.size})" else tab,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Offline Mode Indicator
        if (isOffline) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF2D2212),
                border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(16.dp))
                    Text(
                        text = "Offline mode • Saved templates & India packs work offline",
                        color = Color(0xFFFFE082),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }

        // 4. Main Content Area (Skeleton / Error / Empty / Grid)
        when {
            isLoading -> {
                TemplateBrowserSkeletonGrid(modifier = Modifier.weight(1f))
            }
            loadError != null -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(44.dp))
                        Text("Could not load templates", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(loadError ?: "Unknown error", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, textAlign = TextAlign.Center)
                        Button(
                            onClick = { loadData() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Retry", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            filteredTemplates.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            imageVector = if (selectedTab == "Saved") Icons.Default.BookmarkBorder else Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (selectedTab == "Saved") "No saved templates yet" else "No matching templates found",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (selectedTab == "Saved") "Tap the bookmark icon on any template to access it offline anytime."
                            else "Try clearing your search or switching to Recommended.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        OutlinedButton(
                            onClick = {
                                searchQuery = ""
                                selectedTab = "Recommended"
                            },
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                        ) {
                            Text("Browse Recommended", color = Color.White)
                        }
                    }
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Recently Used Horizontal Strip
                    if (recentlyUsedTemplates.isNotEmpty() && selectedTab == "Recommended" && searchQuery.isBlank()) {
                        item(span = { GridItemSpan(2) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(16.dp))
                                    Text("Recently Used", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(recentlyUsedTemplates, key = { "recent_${it.id}" }) { tpl ->
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = Color(0xFF191928),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                                            modifier = Modifier
                                                .width(172.dp)
                                                .clickable { previewTemplate = tpl }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(
                                                            Brush.linearGradient(
                                                                listOf(Color(tpl.previewGradientStart), Color(tpl.previewGradientEnd))
                                                            )
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(tpl.typeLabel.take(1), color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(tpl.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text("${tpl.slots.size} clips • ${tpl.typeLabel}", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }

                    // Template Cards Grid
                    items(filteredTemplates, key = { it.id }) { tpl ->
                        val isSaved = tpl.id in savedIds
                        val isLocked = isTemplateLocked(tpl)
                        TemplateCardItem(
                            template = tpl,
                            isSaved = isSaved,
                            isLocked = isLocked,
                            onTapPreview = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                previewTemplate = tpl
                            },
                            onTapUse = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isLocked) lockedTemplateToUnlock = tpl else slotFillerTemplate = tpl
                            },
                            onToggleSave = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch {
                                    val nowSaved = TemplateRepository.toggleSaveTemplate(context, tpl, isSaved)
                                    savedIds = if (nowSaved) savedIds + tpl.id else savedIds - tpl.id
                                    Toast.makeText(
                                        context,
                                        if (nowSaved) "Saved for offline use 📌" else "Removed from Saved",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Autoplay Preview Modal on Tap
    previewTemplate?.let { tpl ->
        val isSaved = tpl.id in savedIds
        val isLocked = isTemplateLocked(tpl)
        TemplateAutoplayPreviewDialog(
            template = tpl,
            isSaved = isSaved,
            isLocked = isLocked,
            lowEndMode = lowEndMode,
            onDismiss = { previewTemplate = null },
            onToggleSave = {
                scope.launch {
                    val nowSaved = TemplateRepository.toggleSaveTemplate(context, tpl, isSaved)
                    savedIds = if (nowSaved) savedIds + tpl.id else savedIds - tpl.id
                }
            },
            onUseTemplate = {
                previewTemplate = null
                if (isLocked) lockedTemplateToUnlock = tpl else slotFillerTemplate = tpl
            }
        )
    }

    // Slot Filler -> Launches into InstagramProEditorScreen
    slotFillerTemplate?.let { tpl ->
        TemplateSlotFillerDialog(
            template = tpl,
            onDismiss = { slotFillerTemplate = null },
            onOpenInEditor = { snapshot, uris, usedTemplate ->
                slotFillerTemplate = null
                recentIds = TemplateRepository.getRecentlyUsedIds(context)
                onTemplateReadyForEditor(snapshot, uris, usedTemplate)
            }
        )
    }

    // Premium Unlock Modal
    lockedTemplateToUnlock?.let { tpl ->
        TemplatePremiumUnlockSheet(
            template = tpl,
            onDismiss = { lockedTemplateToUnlock = null },
            onUnlocked = {
                unlockedIds = unlockedIds + tpl.id
                val unlockedTpl = tpl
                lockedTemplateToUnlock = null
                slotFillerTemplate = unlockedTpl
            }
        )
    }

    // Brand Kit Modal
    if (showBrandKitSheet) {
        BrandKitBottomSheet(
            initialKit = brandKit,
            onDismiss = { showBrandKitSheet = false },
            onSaveAndApply = { saved ->
                brandKit = saved
                showBrandKitSheet = false
            }
        )
    }

    // Publish Creator Template Modal
    if (showPublishSheet) {
        PublishTemplateBottomSheet(
            onDismiss = { showPublishSheet = false },
            onPublished = { published ->
                showPublishSheet = false
                allTemplates = listOf(published) + allTemplates.filterNot { it.id == published.id }
            }
        )
    }
}

@Composable
private fun TemplateCardItem(
    template: ContentTemplate,
    isSaved: Boolean,
    isLocked: Boolean,
    onTapPreview: () -> Unit,
    onTapUse: () -> Unit,
    onToggleSave: () -> Unit
) {
    val heroText = template.textLayers.firstOrNull()
    val heroFont = remember(heroText?.fontKey) { findEditorFont(heroText?.fontKey ?: "montserrat").fontFamily }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF151522),
        border = BorderStroke(
            width = if (template.isPremium) 1.2.dp else 1.dp,
            color = if (template.isPremium) Color(0xFFFFD54F).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.1f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTapPreview)
            .testTag("template_card_${template.id}")
    ) {
        Column {
            // Visual Preview Poster (9:13 ratio for compact 2-col scroll)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(216.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(template.previewGradientStart), Color(template.previewGradientEnd))
                        )
                    )
            ) {
                if (template.previewThumbUrl.isNotBlank()) {
                    AsyncImage(
                        model = template.previewThumbUrl,
                        contentDescription = template.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().alpha(if (isLocked) 0.45f else 0.85f)
                    )
                }

                // Layout-specific decorative wireframe
                TemplateLayoutDecoration(layoutStyle = template.layoutStyle)

                // Live Hindi / English Hero Typography Preview
                if (heroText != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(heroText.bgColorArgb).copy(alpha = if (heroText.bgColorArgb == 0L) 0.35f else 0.78f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 10.dp)
                    ) {
                        Text(
                            text = heroText.text,
                            color = Color(heroText.colorArgb),
                            fontFamily = heroFont,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Top Row: Type Badge + Save Heart/Bookmark
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.58f)
                    ) {
                        Text(
                            text = template.typeLabel,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.52f))
                            .clickable(onClick = onToggleSave)
                            .testTag("template_save_btn_${template.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isSaved) "Saved" else "Save template",
                            tint = if (isSaved) Color(0xFFFFD54F) else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Premium Lock / Unlocked Badge
                if (template.isPremium) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isLocked) Color(0xFF1A1200).copy(alpha = 0.88f) else Color(0xFF064E3B).copy(alpha = 0.9f),
                        border = BorderStroke(1.dp, if (isLocked) Color(0xFFFFD54F) else Color(0xFF34D399)),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (isLocked) Color(0xFFFFD54F) else Color(0xFF34D399),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (isLocked) "₹${template.price.toInt()}" else "Unlocked",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                // Bottom-Right Slot & Duration Pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.62f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "${template.slots.size} clips • ${template.duration / 1000}s",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            // Card Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = template.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${formatUsageCount(template.usageCount)} uses",
                        color = Color.White.copy(alpha = 0.58f),
                        fontSize = 11.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isLocked) Color(0xFFFFB300) else Color(0xFFEE1D52),
                        modifier = Modifier
                            .clickable(onClick = onTapUse)
                            .testTag("template_use_btn_${template.id}")
                    ) {
                        Text(
                            text = if (isLocked) "Unlock" else "Use",
                            color = if (isLocked) Color.Black else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.TemplateLayoutDecoration(layoutStyle: String) {
    when (layoutStyle) {
        "COLLAGE_3", "GRID_2X2" -> {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                )
                Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp)))
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp)))
                }
            }
        }
        "CAROUSEL_MULTI" -> {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 34.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                repeat(4) { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == 0) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(if (i == 0) Color.White else Color.White.copy(alpha = 0.4f))
                    )
                }
            }
        }
        "STORY_FRAME" -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .border(1.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            )
        }
        "ADD_YOURS_CHAIN" -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp)
            ) {
                Text(
                    text = "🔗 Add Yours Chain",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/**
 * Autoplay Live Preview Modal on Tap:
 * Animates through template slots synced to slot timings & beat markers, plays preview audio if available,
 * and launches directly into Slot Filler (or Premium Unlock Sheet if locked).
 */
@Composable
private fun TemplateAutoplayPreviewDialog(
    template: ContentTemplate,
    isSaved: Boolean,
    isLocked: Boolean,
    lowEndMode: Boolean,
    onDismiss: () -> Unit,
    onToggleSave: () -> Unit,
    onUseTemplate: () -> Unit
) {
    val context = LocalContext.current
    val safeSlots = remember(template.id) {
        template.slots.ifEmpty { listOf(TemplateSlot(id = "s0", start = 0L, end = 4000L)) }
    }
    var activeSlotIndex by remember(template.id) { mutableIntStateOf(0) }
    var beatPulse by remember { mutableStateOf(false) }

    // Autoplay slot cycling synced to slot duration
    LaunchedEffect(template.id) {
        while (true) {
            val slot = safeSlots[activeSlotIndex % safeSlots.size]
            beatPulse = true
            delay(180)
            beatPulse = false
            delay((slot.slotDurationMs.coerceIn(1000L, 4500L) - 180L).coerceAtLeast(500L))
            activeSlotIndex = (activeSlotIndex + 1) % safeSlots.size
        }
    }

    // Play audio preview URL if present
    val audioPlayer = remember(template.audio.url) {
        if (template.audio.url.isNotBlank()) {
            try {
                ExoPlayerUtils.createExoPlayer(context, isMusic = true).apply {
                    setMediaItem(MediaItem.fromUri(Uri.parse(template.audio.url)))
                    repeatMode = Player.REPEAT_MODE_ALL
                    prepare()
                    play()
                }
            } catch (_: Exception) {
                null
            }
        } else null
    }
    DisposableEffect(audioPlayer) {
        onDispose { ExoPlayerUtils.safeRelease(audioPlayer) }
    }

    val pulseScale by animateFloatAsState(
        targetValue = if (beatPulse && !lowEndMode) 1.04f else 1f,
        animationSpec = tween(180),
        label = "beat_pulse"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.84f),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF10101A),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Live Autoplay Stage
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(template.previewGradientStart),
                                    Color(template.previewGradientEnd)
                                )
                            )
                        )
                        .scale(pulseScale)
                ) {
                    // Slot progress segments at top (Instagram Story/Reel style)
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        safeSlots.forEachIndexed { i, _ ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.dp)
                                    .clip(CircleShape)
                                    .background(if (i <= activeSlotIndex) Color.White else Color.White.copy(alpha = 0.3f))
                            )
                        }
                    }

                    // Close & Bookmark Buttons
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 24.dp, end = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onToggleSave,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Save Template",
                                tint = if (isSaved) Color(0xFFFFD54F) else Color.White
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close Preview", tint = Color.White)
                        }
                    }

                    TemplateLayoutDecoration(layoutStyle = template.layoutStyle)

                    // Active Slot Beat Indicator
                    val currentSlot = safeSlots[activeSlotIndex % safeSlots.size]
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Black.copy(alpha = 0.45f)
                        ) {
                            Text(
                                text = "Slot ${activeSlotIndex + 1}/${safeSlots.size} • ${currentSlot.transition.uppercase()} • ${currentSlot.defaultFilter}",
                                color = Color(0xFFFFD54F),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        template.textLayers.forEach { txt ->
                            val ff = findEditorFont(txt.fontKey).fontFamily
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(txt.bgColorArgb).copy(alpha = if (txt.bgColorArgb == 0L) 0.25f else 0.85f)
                            ) {
                                Text(
                                    text = txt.text,
                                    color = Color(txt.colorArgb),
                                    fontFamily = ff,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }

                        if (template.stickers.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                template.stickers.take(3).forEach { st ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.White.copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            text = st.primaryText,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (isLocked) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.Black.copy(alpha = 0.72f),
                                border = BorderStroke(1.dp, Color(0xFFFFD54F))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(16.dp))
                                    Text("Premium Preview • Unlock for ₹${template.price.toInt()}", color = Color(0xFFFFD54F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Bottom Metadata Overlay
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(template.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            text = "by ${template.creatorName} • ${formatUsageCount(template.usageCount)} uses • ${template.audio.name}",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Bottom Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onUseTemplate,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLocked) Color(0xFFFFB300) else Color(0xFFEE1D52)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("template_preview_use_cta")
                    ) {
                        Icon(
                            imageVector = if (isLocked) Icons.Default.LockOpen else Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = if (isLocked) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isLocked) "Unlock Premium • ₹${template.price.toInt()}" else "Use Template",
                            color = if (isLocked) Color.Black else Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shimmer Skeleton Loader Grid while templates fetch from Firestore / local cache.
 */
@Composable
private fun TemplateBrowserSkeletonGrid(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "template_skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "skeleton_alpha"
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        items(6) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = alpha * 0.22f))
            )
        }
    }
}
