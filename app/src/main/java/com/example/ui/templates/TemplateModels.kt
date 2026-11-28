package com.example.ui.templates

import android.net.Uri
import com.example.ui.editor.AspectRatioPreset
import com.example.ui.editor.ColorAdjustments
import com.example.ui.editor.EditorClip
import com.example.ui.editor.EditorProjectSnapshot
import com.example.ui.editor.EditorStickerItem
import com.example.ui.editor.EditorTextItem
import com.example.ui.editor.MusicTrimConfig
import com.example.ui.editor.StickerKind
import com.example.ui.editor.TextAlignMode
import com.example.ui.editor.TextAnimStyle
import com.example.ui.editor.TextBgStyle
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Exact JSON-serializable Template Data Model matching:
 * { id, type: reel|story|quick_story|post|carousel, title, category, aspectRatio,
 *   duration, slots:[{id, mediaType, start, end, transition, defaultFilter}],
 *   textLayers:[], stickers:[], audio:{url, beatMarkers},
 *   creatorId, usageCount, isPremium, price }
 */

@Serializable
data class TemplateSlot(
    val id: String = UUID.randomUUID().toString(),
    val mediaType: String = "any", // "video" | "photo" | "any"
    val start: Long = 0L,          // ms
    val end: Long = 3000L,         // ms
    val transition: String = "cut", // "cut" | "fade" | "zoom" | "slide" | "beat_flash"
    val defaultFilter: String = "normal"
) {
    val slotDurationMs: Long get() = (end - start).coerceAtLeast(600L)
}

@Serializable
data class TemplateAudio(
    val url: String = "",
    val beatMarkers: List<Long> = emptyList(),
    val name: String = "Original Audio",
    val artist: String = "Social India",
    val artworkUrl: String = "",
    val bpm: Int = 120
)

@Serializable
data class ContentTemplate(
    val id: String = UUID.randomUUID().toString(),
    val type: String = "reel", // "reel" | "story" | "quick_story" | "post" | "carousel"
    val title: String = "",
    val category: String = "Recommended", // Recommended, Trending, Festival, Birthday, Wedding, Business
    val aspectRatio: String = "9:16", // "9:16" | "4:5" | "1:1"
    val duration: Long = 10_000L,
    val slots: List<TemplateSlot> = emptyList(),
    val textLayers: List<EditorTextItem> = emptyList(),
    val stickers: List<EditorStickerItem> = emptyList(),
    val audio: TemplateAudio = TemplateAudio(),
    val creatorId: String = "social_india_official",
    val usageCount: Long = 0L,
    val isPremium: Boolean = false,
    val price: Double = 0.0,
    // Visual & Quick Story metadata
    val creatorName: String = "Social India Studio",
    val layoutStyle: String = "SINGLE", // SINGLE, STORY_FRAME, ADD_YOURS_CHAIN, COLLAGE_3, GRID_2X2, CAROUSEL_MULTI, TEXT_POST
    val promptText: String = "",
    val originTemplateId: String = "",
    val previewGradientStart: Long = 0xFF16162A,
    val previewGradientEnd: Long = 0xFFEE1D52,
    val previewThumbUrl: String = "",
    val tags: List<String> = emptyList(),
    val createdAtMs: Long = System.currentTimeMillis()
) {
    fun toAspectRatioPreset(): AspectRatioPreset = when (aspectRatio) {
        "4:5" -> AspectRatioPreset.RATIO_4_5
        "1:1" -> AspectRatioPreset.RATIO_1_1
        else -> AspectRatioPreset.RATIO_9_16
    }

    val typeLabel: String
        get() = when (type.lowercase()) {
            "reel" -> "Reel"
            "story" -> "Story"
            "quick_story" -> "Add Yours"
            "post" -> "Post"
            "carousel" -> "Carousel"
            else -> "Template"
        }
}

/**
 * Reusable Creator Brand Kit: saved colors, logo, handle & font applied in one tap.
 */
@Serializable
data class BrandKit(
    val brandName: String = "My Brand",
    val handle: String = "@socialindia",
    val primaryColorArgb: Long = 0xFFEE1D52,
    val secondaryColorArgb: Long = 0xFF141422,
    val accentColorArgb: Long = 0xFFFFD54F,
    val fontKey: String = "montserrat",
    val logoUriString: String = ""
)

val TEMPLATE_BROWSER_TABS = listOf(
    "Recommended",
    "Trending",
    "Saved",
    "Festival",
    "Birthday",
    "Wedding",
    "Business"
)

/**
 * 10 Pre-Built India-Focused Sample Templates (3 Reel, 3 Story, 2 Quick Story, 2 Post/Carousel)
 * ready for immediate testing both online and offline.
 */
val SAMPLE_SOCIAL_INDIA_TEMPLATES: List<ContentTemplate> = listOf(
    // ================= 3 REEL TEMPLATES =================
    ContentTemplate(
        id = "tpl_reel_diwali_beats",
        type = "reel",
        title = "Diwali Sparkle Beat-Cut",
        category = "Festival",
        aspectRatio = "9:16",
        duration = 12_000L,
        slots = listOf(
            TemplateSlot("s1", "any", 0L, 2800L, "beat_flash", "delhi_gold"),
            TemplateSlot("s2", "any", 2800L, 5600L, "zoom", "delhi_gold"),
            TemplateSlot("s3", "any", 5600L, 8800L, "slide", "juno"),
            TemplateSlot("s4", "any", 8800L, 12000L, "fade", "delhi_gold")
        ),
        textLayers = listOf(
            EditorTextItem(
                id = "txt_diwali_1",
                text = "शुभ दीपावली ✨",
                fontKey = "rozha_one",
                colorArgb = 0xFFFFD54F,
                bgColorArgb = 0x99260E00,
                bgStyle = TextBgStyle.FROSTED,
                alignment = TextAlignMode.CENTER,
                hasOutline = true,
                animStyle = TextAnimStyle.GLOW_PULSE,
                xFraction = 0.5f,
                yFraction = 0.22f,
                fontSizeSp = 32f
            )
        ),
        stickers = listOf(
            EditorStickerItem(id = "st_diwali_1", kind = StickerKind.EMOJI, primaryText = "🪔", xFraction = 0.82f, yFraction = 0.20f),
            EditorStickerItem(id = "st_diwali_2", kind = StickerKind.HASHTAG, primaryText = "HappyDiwali", xFraction = 0.5f, yFraction = 0.78f)
        ),
        audio = TemplateAudio(
            url = "",
            beatMarkers = listOf(0L, 2800L, 5600L, 8800L, 12000L),
            name = "Festive Dhol & Sitar Beat",
            artist = "Social India Beats",
            bpm = 126
        ),
        creatorId = "social_india_official",
        creatorName = "Social India Studio",
        usageCount = 14820L,
        isPremium = false,
        price = 0.0,
        layoutStyle = "SINGLE",
        previewGradientStart = 0xFF4A1500,
        previewGradientEnd = 0xFFFF8F00,
        tags = listOf("diwali", "festival", "hindi", "status", "beat-sync", "india")
    ),
    ContentTemplate(
        id = "tpl_reel_shaadi_royal",
        type = "reel",
        title = "Royal Shaadi Cinematic",
        category = "Wedding",
        aspectRatio = "9:16",
        duration = 15_000L,
        slots = listOf(
            TemplateSlot("w1", "video", 0L, 3500L, "fade", "juno"),
            TemplateSlot("w2", "any", 3500L, 6500L, "zoom", "reyes"),
            TemplateSlot("w3", "any", 6500L, 10000L, "fade", "delhi_gold"),
            TemplateSlot("w4", "any", 10000L, 15000L, "fade", "juno")
        ),
        textLayers = listOf(
            EditorTextItem(
                id = "txt_shaadi_1",
                text = "शादी मुबारक",
                fontKey = "yatra_one",
                colorArgb = 0xFFFFF8E1,
                bgColorArgb = 0xAA6A041D,
                bgStyle = TextBgStyle.GRADIENT_PILL,
                alignment = TextAlignMode.CENTER,
                animStyle = TextAnimStyle.FADE_POP,
                xFraction = 0.5f,
                yFraction = 0.19f,
                fontSizeSp = 30f
            ),
            EditorTextItem(
                id = "txt_shaadi_2",
                text = "FOREVER BEGINS TODAY",
                fontKey = "cinzel",
                colorArgb = 0xFFFFD54F,
                bgStyle = TextBgStyle.NONE,
                alignment = TextAlignMode.CENTER,
                hasOutline = true,
                animStyle = TextAnimStyle.SLIDE_UP,
                xFraction = 0.5f,
                yFraction = 0.78f,
                fontSizeSp = 18f
            )
        ),
        stickers = listOf(
            EditorStickerItem(id = "st_shaadi_1", kind = StickerKind.EMOJI, primaryText = "💍", xFraction = 0.5f, yFraction = 0.70f)
        ),
        audio = TemplateAudio(
            url = "",
            beatMarkers = listOf(0L, 3500L, 6500L, 10000L, 15000L),
            name = "Shehnai Lo-Fi Royal",
            artist = "Desi Wedding Orchestra",
            bpm = 104
        ),
        creatorId = "creator_royal_films",
        creatorName = "Royal Shaadi Films",
        usageCount = 9340L,
        isPremium = true,
        price = 49.0,
        layoutStyle = "SINGLE",
        previewGradientStart = 0xFF5A0A22,
        previewGradientEnd = 0xFFD97706,
        tags = listOf("wedding", "shaadi", "cinematic", "premium", "hindi", "couple")
    ),
    ContentTemplate(
        id = "tpl_reel_holi_splash",
        type = "reel",
        title = "Holi Color Splash Beat",
        category = "Festival",
        aspectRatio = "9:16",
        duration = 10_000L,
        slots = listOf(
            TemplateSlot("h1", "any", 0L, 3200L, "beat_flash", "clarendon"),
            TemplateSlot("h2", "any", 3200L, 6400L, "zoom", "clarendon"),
            TemplateSlot("h3", "any", 6400L, 10000L, "slide", "juno")
        ),
        textLayers = listOf(
            EditorTextItem(
                id = "txt_holi_1",
                text = "रंग बरसे 🌈",
                fontKey = "kalam",
                colorArgb = 0xFFFFFFFF,
                bgColorArgb = 0xCCEC4899,
                bgStyle = TextBgStyle.GRADIENT_PILL,
                animStyle = TextAnimStyle.BOUNCE,
                xFraction = 0.5f,
                yFraction = 0.20f,
                fontSizeSp = 30f
            )
        ),
        stickers = listOf(
            EditorStickerItem(id = "st_holi_1", kind = StickerKind.HASHTAG, primaryText = "HoliHai", xFraction = 0.5f, yFraction = 0.80f)
        ),
        audio = TemplateAudio(
            url = "",
            beatMarkers = listOf(0L, 3200L, 6400L, 10000L),
            name = "Rangoli EDM Drop",
            artist = "DJ Bharat",
            bpm = 132
        ),
        creatorId = "social_india_official",
        creatorName = "Social India Studio",
        usageCount = 11250L,
        isPremium = false,
        price = 0.0,
        layoutStyle = "SINGLE",
        previewGradientStart = 0xFFDB2777,
        previewGradientEnd = 0xFF06B6D4,
        tags = listOf("holi", "festival", "colors", "hindi", "trending", "reel")
    ),

    // ================= 3 STORY TEMPLATES =================
    ContentTemplate(
        id = "tpl_story_eid_mubarak",
        type = "story",
        title = "Eid Mubarak Moon Frame",
        category = "Festival",
        aspectRatio = "9:16",
        duration = 7_000L,
        slots = listOf(
            TemplateSlot("e1", "photo", 0L, 7000L, "fade", "perpetua")
        ),
        textLayers = listOf(
            EditorTextItem(
                id = "txt_eid_1",
                text = "ईद मुबारक 🌙",
                fontKey = "yatra_one",
                colorArgb = 0xFFFFE082,
                bgColorArgb = 0xAA064E3B,
                bgStyle = TextBgStyle.FROSTED,
                animStyle = TextAnimStyle.GLOW_PULSE,
                xFraction = 0.5f,
                yFraction = 0.18f,
                fontSizeSp = 32f
            ),
            EditorTextItem(
                id = "txt_eid_2",
                text = "Wishing peace, joy & blessings",
                fontKey = "playfair",
                colorArgb = 0xFFFFFFFF,
                bgStyle = TextBgStyle.NONE,
                xFraction = 0.5f,
                yFraction = 0.82f,
                fontSizeSp = 18f
            )
        ),
        stickers = listOf(
            EditorStickerItem(id = "st_eid_1", kind = StickerKind.EMOJI, primaryText = "✨", xFraction = 0.18f, yFraction = 0.22f),
            EditorStickerItem(id = "st_eid_2", kind = StickerKind.LOCATION, primaryText = "INDIA", xFraction = 0.5f, yFraction = 0.74f)
        ),
        audio = TemplateAudio(name = "Sufi Acoustic Breeze", artist = "Moonlight Ensemble", bpm = 92),
        creatorId = "social_india_official",
        creatorName = "Social India Studio",
        usageCount = 8640L,
        isPremium = false,
        price = 0.0,
        layoutStyle = "STORY_FRAME",
        previewGradientStart = 0xFF064E3B,
        previewGradientEnd = 0xFF0F766E,
        tags = listOf("eid", "festival", "story", "status", "hindi")
    ),
    ContentTemplate(
        id = "tpl_story_birthday_bash",
        type = "story",
        title = "Birthday Glow Countdown",
        category = "Birthday",
        aspectRatio = "9:16",
        duration = 8_000L,
        slots = listOf(
            TemplateSlot("b1", "photo", 0L, 4000L, "zoom", "lark"),
            TemplateSlot("b2", "photo", 4000L, 8000L, "fade", "juno")
        ),
        textLayers = listOf(
            EditorTextItem(
                id = "txt_bday_1",
                text = "Happy Birthday! 🎂",
                fontKey = "pacifico",
                colorArgb = 0xFFFFFFFF,
                bgColorArgb = 0xCCEC4899,
                bgStyle = TextBgStyle.GRADIENT_PILL,
                animStyle = TextAnimStyle.BOUNCE,
                xFraction = 0.5f,
                yFraction = 0.18f,
                fontSizeSp = 30f
            )
        ),
        stickers = listOf(
            EditorStickerItem(id = "st_bday_1", kind = StickerKind.COUNTDOWN, primaryText = "PARTY TIME", secondaryText = "Tonight 9 PM", xFraction = 0.5f, yFraction = 0.76f),
            EditorStickerItem(id = "st_bday_2", kind = StickerKind.EMOJI, primaryText = "🎉", xFraction = 0.82f, yFraction = 0.25f)
        ),
        audio = TemplateAudio(name = "Celebration Pop", artist = "Party Beats", bpm = 122),
        creatorId = "social_india_official",
        creatorName = "Social India Studio",
        usageCount = 12490L,
        isPremium = false,
        price = 0.0,
        layoutStyle = "STORY_FRAME",
        previewGradientStart = 0xFF7C3AED,
        previewGradientEnd = 0xFFEC4899,
        tags = listOf("birthday", "wish", "story", "countdown", "party")
    ),
    ContentTemplate(
        id = "tpl_story_independence_tiranga",
        type = "story",
        title = "Independence Day Tiranga",
        category = "Festival",
        aspectRatio = "9:16",
        duration = 7_000L,
        slots = listOf(
            TemplateSlot("i1", "any", 0L, 7000L, "fade", "clarendon")
        ),
        textLayers = listOf(
            EditorTextItem(
                id = "txt_ind_1",
                text = "जय हिन्द 🇮🇳",
                fontKey = "rozha_one",
                colorArgb = 0xFFFFFFFF,
                bgColorArgb = 0xCCFF6F00,
                bgStyle = TextBgStyle.SOLID_CHIP,
                animStyle = TextAnimStyle.FADE_POP,
                xFraction = 0.5f,
                yFraction = 0.18f,
                fontSizeSp = 34f
            ),
            EditorTextItem(
                id = "txt_ind_2",
                text = "PROUD TO BE INDIAN",
                fontKey = "bebas_neue",
                colorArgb = 0xFF34D399,
                bgColorArgb = 0xAA000000,
                bgStyle = TextBgStyle.SOLID_CHIP,
                xFraction = 0.5f,
                yFraction = 0.80f,
                fontSizeSp = 22f
            )
        ),
        stickers = listOf(
            EditorStickerItem(id = "st_ind_1", kind = StickerKind.HASHTAG, primaryText = "IndependenceDay", xFraction = 0.5f, yFraction = 0.70f)
        ),
        audio = TemplateAudio(name = "Vande Desh Anthem", artist = "Bharat Strings", bpm = 98),
        creatorId = "social_india_official",
        creatorName = "Social India Studio",
        usageCount = 16900L,
        isPremium = false,
        price = 0.0,
        layoutStyle = "STORY_FRAME",
        previewGradientStart = 0xFFFF6F00,
        previewGradientEnd = 0xFF15803D,
        tags = listOf("independence", "15 august", "tiranga", "india", "hindi", "status")
    ),

    // ================= 2 QUICK STORY ("ADD YOURS" CHAIN) TEMPLATES =================
    ContentTemplate(
        id = "tpl_quick_chai_chain",
        type = "quick_story",
        title = "Add Yours: Aaj Ki Chai ☕",
        category = "Trending",
        aspectRatio = "9:16",
        duration = 6_000L,
        slots = listOf(
            TemplateSlot("q1", "any", 0L, 6000L, "cut", "crema")
        ),
        textLayers = listOf(
            EditorTextItem(
                id = "txt_chai_1",
                text = "चाय और सुकून ☕",
                fontKey = "kalam",
                colorArgb = 0xFFFFF3E0,
                bgColorArgb = 0x993E2723,
                bgStyle = TextBgStyle.FROSTED,
                animStyle = TextAnimStyle.TYPEWRITER,
                xFraction = 0.5f,
                yFraction = 0.22f,
                fontSizeSp = 28f
            )
        ),
        stickers = listOf(
            EditorStickerItem(
                id = "st_chai_prompt",
                kind = StickerKind.QUESTION,
                primaryText = "Add Yours: Show your chai / coffee right now ☕",
                secondaryText = "Tap to join chain",
                xFraction = 0.5f,
                yFraction = 0.72f
            )
        ),
        audio = TemplateAudio(name = "Monsoon Chai Lo-Fi", artist = "Desi Chill", bpm = 86),
        creatorId = "social_india_official",
        creatorName = "Social India Trends",
        usageCount = 24310L,
        isPremium = false,
        price = 0.0,
        layoutStyle = "ADD_YOURS_CHAIN",
        promptText = "Show your chai / coffee right now ☕",
        originTemplateId = "tpl_quick_chai_chain",
        previewGradientStart = 0xFF4E342E,
        previewGradientEnd = 0xFFD97706,
        tags = listOf("add yours", "chai", "quick story", "trending", "chain", "hindi")
    ),
    ContentTemplate(
        id = "tpl_quick_desi_fit_chain",
        type = "quick_story",
        title = "Add Yours: Desi Fit Check ✨",
        category = "Wedding",
        aspectRatio = "9:16",
        duration = 6_000L,
        slots = listOf(
            TemplateSlot("q2", "any", 0L, 6000L, "zoom", "juno")
        ),
        textLayers = listOf(
            EditorTextItem(
                id = "txt_fit_1",
                text = "DESI FIT CHECK 🇮🇳",
                fontKey = "oswald",
                colorArgb = 0xFFFFD54F,
                bgColorArgb = 0xB3000000,
                bgStyle = TextBgStyle.SOLID_CHIP,
                animStyle = TextAnimStyle.SLIDE_UP,
                xFraction = 0.5f,
                yFraction = 0.20f,
                fontSizeSp = 28f
            )
        ),
        stickers = listOf(
            EditorStickerItem(
                id = "st_fit_prompt",
                kind = StickerKind.POLL,
                primaryText = "Add Yours: Rate this festive outfit ✨",
                extraOptions = listOf("10/10 Fire 🔥", "Absolute Royalty 👑"),
                xFraction = 0.5f,
                yFraction = 0.75f
            )
        ),
        audio = TemplateAudio(name = "Sangeet Runway", artist = "Desi Glam", bpm = 118),
        creatorId = "social_india_official",
        creatorName = "Social India Trends",
        usageCount = 19450L,
        isPremium = false,
        price = 0.0,
        layoutStyle = "ADD_YOURS_CHAIN",
        promptText = "Drop your best desi / festive outfit ✨",
        originTemplateId = "tpl_quick_desi_fit_chain",
        previewGradientStart = 0xFF831843,
        previewGradientEnd = 0xFF6D28D9,
        tags = listOf("add yours", "fashion", "desi", "wedding", "quick story", "chain")
    ),

    // ================= 2 POST / CAROUSEL TEMPLATES =================
    ContentTemplate(
        id = "tpl_post_business_promo",
        type = "post",
        title = "Business Mega Sale Grid",
        category = "Business",
        aspectRatio = "4:5",
        duration = 5_000L,
        slots = listOf(
            TemplateSlot("p1", "photo", 0L, 5000L, "cut", "ludwig"),
            TemplateSlot("p2", "photo", 0L, 5000L, "cut", "ludwig"),
            TemplateSlot("p3", "photo", 0L, 5000L, "cut", "ludwig")
        ),
        textLayers = listOf(
            EditorTextItem(
                id = "txt_biz_1",
                text = "FESTIVE MEGA SALE • UP TO 50% OFF",
                fontKey = "montserrat",
                colorArgb = 0xFFFFFFFF,
                bgColorArgb = 0xFFEE1D52,
                bgStyle = TextBgStyle.SOLID_CHIP,
                alignment = TextAlignMode.CENTER,
                xFraction = 0.5f,
                yFraction = 0.16f,
                fontSizeSp = 20f
            ),
            EditorTextItem(
                id = "txt_biz_2",
                text = "सीमित समय ऑफर • आज ही ऑर्डर करें",
                fontKey = "noto_devanagari",
                colorArgb = 0xFFFFD54F,
                bgColorArgb = 0xCC0F172A,
                bgStyle = TextBgStyle.SOLID_CHIP,
                xFraction = 0.5f,
                yFraction = 0.84f,
                fontSizeSp = 18f
            )
        ),
        stickers = listOf(
            EditorStickerItem(id = "st_biz_1", kind = StickerKind.LINK, primaryText = "Shop Now", xFraction = 0.5f, yFraction = 0.72f)
        ),
        audio = TemplateAudio(name = "Clean Promo Groove", artist = "Brand Studio", bpm = 112),
        creatorId = "creator_brand_lab",
        creatorName = "BrandLab India",
        usageCount = 6780L,
        isPremium = true,
        price = 29.0,
        layoutStyle = "COLLAGE_3",
        previewGradientStart = 0xFF0F172A,
        previewGradientEnd = 0xFF2563EB,
        tags = listOf("business", "sale", "collage", "post", "brand kit", "premium", "hindi")
    ),
    ContentTemplate(
        id = "tpl_carousel_india_diary",
        type = "carousel",
        title = "Travel India 4-Slide Carousel",
        category = "Recommended",
        aspectRatio = "1:1",
        duration = 12_000L,
        slots = listOf(
            TemplateSlot("c1", "photo", 0L, 3000L, "slide", "lark"),
            TemplateSlot("c2", "photo", 3000L, 6000L, "slide", "reyes"),
            TemplateSlot("c3", "photo", 6000L, 9000L, "slide", "juno"),
            TemplateSlot("c4", "photo", 9000L, 12000L, "slide", "delhi_gold")
        ),
        textLayers = listOf(
            EditorTextItem(
                id = "txt_car_1",
                text = "SWIPE FOR THE MEMORIES →",
                fontKey = "space_grotesk",
                colorArgb = 0xFFFFFFFF,
                bgColorArgb = 0xBB111827,
                bgStyle = TextBgStyle.FROSTED,
                xFraction = 0.5f,
                yFraction = 0.82f,
                fontSizeSp = 18f
            )
        ),
        stickers = listOf(
            EditorStickerItem(id = "st_car_1", kind = StickerKind.LOCATION, primaryText = "JAIPUR, INDIA", xFraction = 0.5f, yFraction = 0.18f)
        ),
        audio = TemplateAudio(name = "Wanderlust Guitar", artist = "Indie India", bpm = 100),
        creatorId = "social_india_official",
        creatorName = "Social India Studio",
        usageCount = 10420L,
        isPremium = false,
        price = 0.0,
        layoutStyle = "CAROUSEL_MULTI",
        previewGradientStart = 0xFF0E7490,
        previewGradientEnd = 0xFFF59E0B,
        tags = listOf("carousel", "post", "travel", "india", "collage", "recommended")
    )
)

/**
 * Converts a ContentTemplate + user-selected slot URIs + optional BrandKit
 * directly into an EditorProjectSnapshot that opens inside InstagramProEditorScreen.
 */
fun buildEditorSnapshotFromTemplate(
    template: ContentTemplate,
    slotUris: List<Uri>,
    isVideoFlags: List<Boolean>,
    brandKit: BrandKit? = null
): EditorProjectSnapshot {
    val safeSlots = template.slots.ifEmpty {
        listOf(TemplateSlot(id = "default_1", start = 0L, end = template.duration.coerceAtLeast(5000L)))
    }

    val clips = safeSlots.mapIndexedNotNull { idx, slot ->
        val uri = slotUris.getOrNull(idx) ?: slotUris.firstOrNull() ?: return@mapIndexedNotNull null
        val isVid = isVideoFlags.getOrNull(idx) ?: isVideoFlags.firstOrNull() ?: (slot.mediaType == "video")
        EditorClip(
            id = "tpl_clip_${slot.id}_$idx",
            uriString = uri.toString(),
            isVideo = isVid,
            durationMs = slot.slotDurationMs,
            trimStartFraction = 0f,
            trimEndFraction = 1f,
            speed = 1f
        )
    }

    val primaryFilter = safeSlots.firstOrNull()?.defaultFilter?.ifBlank { "normal" } ?: "normal"

    // Apply Brand Kit colors, font & watermark handle in 1 tap if requested
    val styledTexts = template.textLayers.mapIndexed { idx, txt ->
        if (brandKit != null) {
            txt.copy(
                id = UUID.randomUUID().toString(),
                fontKey = brandKit.fontKey,
                colorArgb = if (idx == 0) brandKit.accentColorArgb else 0xFFFFFFFF,
                bgColorArgb = if (idx == 0) brandKit.primaryColorArgb else brandKit.secondaryColorArgb
            )
        } else {
            txt.copy(id = UUID.randomUUID().toString())
        }
    }.toMutableList()

    if (brandKit != null && brandKit.handle.isNotBlank()) {
        styledTexts.add(
            EditorTextItem(
                id = UUID.randomUUID().toString(),
                text = brandKit.handle,
                fontKey = brandKit.fontKey,
                colorArgb = brandKit.accentColorArgb,
                bgColorArgb = brandKit.secondaryColorArgb,
                bgStyle = TextBgStyle.FROSTED,
                xFraction = 0.5f,
                yFraction = 0.90f,
                fontSizeSp = 15f
            )
        )
    }

    val styledStickers = template.stickers.map { st ->
        if (brandKit != null) {
            st.copy(id = UUID.randomUUID().toString(), accentColorArgb = brandKit.primaryColorArgb)
        } else {
            st.copy(id = UUID.randomUUID().toString())
        }
    }.toMutableList()

    if (brandKit != null && brandKit.logoUriString.isNotBlank()) {
        styledStickers.add(
            EditorStickerItem(
                id = UUID.randomUUID().toString(),
                kind = StickerKind.GIF,
                primaryText = brandKit.brandName,
                mediaUrl = brandKit.logoUriString,
                xFraction = 0.84f,
                yFraction = 0.14f,
                scale = 0.65f
            )
        )
    }

    val musicCfg = if (template.audio.name.isNotBlank() || template.audio.url.isNotBlank()) {
        MusicTrimConfig(
            trackId = template.id.hashCode().toLong(),
            name = template.audio.name,
            artist = template.audio.artist,
            artworkUrl = template.audio.artworkUrl,
            previewUrl = template.audio.url,
            startSec = 0f,
            windowDurationSec = (template.duration / 1000f).coerceIn(15f, 30f),
            volume = 0.85f,
            beatSyncAutoCut = template.audio.beatMarkers.isNotEmpty(),
            bpm = template.audio.bpm
        )
    } else null

    return EditorProjectSnapshot(
        draftId = "tpl_${template.id}_${System.currentTimeMillis()}",
        aspectRatio = template.toAspectRatioPreset(),
        clips = clips,
        selectedClipIndex = 0,
        filterId = primaryFilter,
        adjustments = ColorAdjustments(),
        textItems = styledTexts,
        stickerItems = styledStickers,
        musicConfig = musicCfg
    )
}
