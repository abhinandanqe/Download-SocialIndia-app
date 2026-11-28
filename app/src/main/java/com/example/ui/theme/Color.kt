package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Social India Signature Brand Colors
val BrandPrimary = Color(0xFFFF3366)       // Vibrant Indian sunset crimson-pink
val BrandPrimaryDark = Color(0xFFE01E5A)   // Deeper press state
val BrandSecondary = Color(0xFFFF8C42)     // Warm saffron-orange accent
val BrandTertiary = Color(0xFF7928CA)      // Deep electric violet

val BrandGradient = Brush.horizontalGradient(
    listOf(BrandPrimary, BrandSecondary)
)

val BrandStoryGradient = Brush.linearGradient(
    listOf(
        Color(0xFFFF8C42), // Saffron
        Color(0xFFFF3366), // Crimson
        Color(0xFF9933FF)  // Violet
    )
)

// Semantic & Status Colors
val SuccessGreen = Color(0xFF00C853)
val WarningAmber = Color(0xFFFFAB00)
val ErrorRed = Color(0xFFFF3D71)
val VerifiedBlue = Color(0xFF1DA1F2)
val GoldBadge = Color(0xFFFFD700)

// Dark Theme Surfaces (Modern OLED/Slate Black)
val DarkBackground = Color(0xFF0D0D11)
val DarkSurface = Color(0xFF15151A)
val DarkSurfaceVariant = Color(0xFF202028)
val DarkSurfaceBorder = Color(0xFF2E2E38)
val DarkOnBackground = Color(0xFFF7F7F9)
val DarkOnSurface = Color(0xFFEDEDF2)
val DarkOnSurfaceVariant = Color(0xFFA1A1AA)

// Light Theme Surfaces (Clean, pure white, high-contrast)
val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF2F2F2)
val LightSurfaceBorder = Color(0xFFE5E5E5)
val LightOnBackground = Color(0xFF111111)
val LightOnSurface = Color(0xFF111111)
val LightOnSurfaceVariant = Color(0xFF737373)

// Social India Follow & Action Blues
val SocialIndiaBlue = Color(0xFF0095F6)
val SocialIndiaBlueDark = Color(0xFF0077CC)
val SocialIndiaBlueLight = Color(0xFFE8F3FF)

// Backward compatibility tokens
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

