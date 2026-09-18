package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ============================================================
// SLEEK DARK BASE TOKENS (No muddy golden brown colors!)
// ============================================================
val BgDark = Color(0xFF040E08)
val PanelDark = Color(0xFF0A1F14)
val PanelElevated = Color(0xFF123020)
val LineBorder = Color(0x3834D399)
val LineBright = Color(0xFF34D399)

val TextPrimary = Color(0xFFF0FDF4)
val TextDim = Color(0xFFA7F3D0)

val DangerRed = Color(0xFFEF4444)
val SuccessGreen = Color(0xFF10B981)

// ============================================================
// 6 ATMOSPHERIC DARK THEMES
// ============================================================
enum class AppTheme(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val icon: String,
    val primary: Color,
    val primaryLight: Color,
    val primaryBright: Color,
    val primaryDark: Color,
    val bgBase: Color,
    val panelBg: Color,
    val panelElevated: Color,
    val border: Color,
    val textMain: Color,
    val textDim: Color,
    val gradientColors: List<Color>
) {
    EMERALD_FOREST(
        id = "forest",
        displayName = "Emerald Forest",
        subtitle = "Deep Nature Green",
        icon = "🌿",
        primary = Color(0xFF10B981),
        primaryLight = Color(0xFF34D399),
        primaryBright = Color(0xFF6EE7B7),
        primaryDark = Color(0xFF065F46),
        bgBase = Color(0xFF040E08),
        panelBg = Color(0xFF0A1F14),
        panelElevated = Color(0xFF123020),
        border = Color(0x3834D399),
        textMain = Color(0xFFF0FDF4),
        textDim = Color(0xFFA7F3D0),
        gradientColors = listOf(Color(0xFFFFFFFF), Color(0xFF6EE7B7), Color(0xFF10B981))
    ),
    CYBER_NEON(
        id = "cyber",
        displayName = "Cyber Neon",
        subtitle = "Tokyo Midnight Neon",
        icon = "🌌",
        primary = Color(0xFF8B5CF6),
        primaryLight = Color(0xFFC084FC),
        primaryBright = Color(0xFFE879F9),
        primaryDark = Color(0xFF581C87),
        bgBase = Color(0xFF060411),
        panelBg = Color(0xFF130E2B),
        panelElevated = Color(0xFF201646),
        border = Color(0x4DA855F7),
        textMain = Color(0xFFFAF5FF),
        textDim = Color(0xFFE9D5FF),
        gradientColors = listOf(Color(0xFFFFFFFF), Color(0xFFE879F9), Color(0xFF8B5CF6))
    ),
    SUNSET_EMBER(
        id = "sunset",
        displayName = "Sunset Ember",
        subtitle = "Golden Hour Ember",
        icon = "🌅",
        primary = Color(0xFFF97316),
        primaryLight = Color(0xFFFB923C),
        primaryBright = Color(0xFFFCD34D),
        primaryDark = Color(0xFF7C2D12),
        bgBase = Color(0xFF110703),
        panelBg = Color(0xFF23110A),
        panelElevated = Color(0xFF361A0F),
        border = Color(0x4DF97316),
        textMain = Color(0xFFFFF7ED),
        textDim = Color(0xFFFED7AA),
        gradientColors = listOf(Color(0xFFFFFFFF), Color(0xFFFCD34D), Color(0xFFF97316))
    ),
    OCEANIC_ABYSS(
        id = "ocean",
        displayName = "Oceanic Abyss",
        subtitle = "Deep Sea Marina",
        icon = "🌊",
        primary = Color(0xFF0284C7),
        primaryLight = Color(0xFF38BDF8),
        primaryBright = Color(0xFF7DD3FC),
        primaryDark = Color(0xFF075985),
        bgBase = Color(0xFF030B14),
        panelBg = Color(0xFF081A2E),
        panelElevated = Color(0xFF0E2A4A),
        border = Color(0x4D38BDF8),
        textMain = Color(0xFFF0F9FF),
        textDim = Color(0xFFBAE6FD),
        gradientColors = listOf(Color(0xFFFFFFFF), Color(0xFF7DD3FC), Color(0xFF0284C7))
    ),
    VINTAGE_ESPRESSO(
        id = "coffee",
        displayName = "Vintage Coffee",
        subtitle = "Dark Walnut & Coffee",
        icon = "☕",
        primary = Color(0xFFD97706),
        primaryLight = Color(0xFFF59E0B),
        primaryBright = Color(0xFFFDE68A),
        primaryDark = Color(0xFF78350F),
        bgBase = Color(0xFF100B07),
        panelBg = Color(0xFF20150E),
        panelElevated = Color(0xFF312015),
        border = Color(0x4DF59E0B),
        textMain = Color(0xFFFEFCE8),
        textDim = Color(0xFFFDE68A),
        gradientColors = listOf(Color(0xFFFFFFFF), Color(0xFFFDE68A), Color(0xFFD97706))
    ),
    ARCTIC_AURORA(
        id = "arctic",
        displayName = "Arctic Aurora",
        subtitle = "Frost & Northern Lights",
        icon = "❄️",
        primary = Color(0xFF2DD4BF),
        primaryLight = Color(0xFF5EEAD4),
        primaryBright = Color(0xFF99F6E4),
        primaryDark = Color(0xFF115E59),
        bgBase = Color(0xFF030813),
        panelBg = Color(0xFF0A182D),
        panelElevated = Color(0xFF12294B),
        border = Color(0x4D2DD4BF),
        textMain = Color(0xFFF0FDFA),
        textDim = Color(0xFFCCFBF1),
        gradientColors = listOf(Color(0xFFFFFFFF), Color(0xFF99F6E4), Color(0xFF2DD4BF))
    );

    companion object {
        fun fromId(id: String?): AppTheme {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: EMERALD_FOREST
        }
    }
}

// Active dynamic theme holder
var ActiveTheme = AppTheme.EMERALD_FOREST

// Semantic Active Theme Accessors
val ThemePrimary: Color get() = ActiveTheme.primary
val ThemeLight: Color get() = ActiveTheme.primaryLight
val ThemeBright: Color get() = ActiveTheme.primaryBright
val ThemeDark: Color get() = ActiveTheme.primaryDark

// Aliases completely removing gold hues and dynamically routing to active theme
val GoldAccent: Color get() = ThemePrimary
val GoldLight: Color get() = ThemeLight
val GoldBright: Color get() = ThemeBright
val GoldDark: Color get() = ThemeDark
val GoldFlame: Color get() = ThemeLight

val GoldGradientBrush: Brush
    get() = Brush.horizontalGradient(ActiveTheme.gradientColors)

val ThemeGradientBrush: Brush
    get() = Brush.horizontalGradient(ActiveTheme.gradientColors)

val DialRimBrush: Brush
    get() = Brush.sweepGradient(ActiveTheme.gradientColors)
