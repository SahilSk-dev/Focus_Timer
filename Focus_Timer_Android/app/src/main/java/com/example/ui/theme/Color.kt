package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ============================================================
// DYNAMIC BASE TOKENS (Tracks ActiveTheme directly for ultra-clean Dark / White support)
// ============================================================
val BgDark: Color get() = ActiveTheme.bgBase
val PanelDark: Color get() = ActiveTheme.panelBg
val PanelElevated: Color get() = ActiveTheme.panelElevated
val LineBorder: Color get() = ActiveTheme.border
val LineBright: Color get() = ActiveTheme.primaryLight

val TextPrimary: Color get() = ActiveTheme.textMain
val TextDim: Color get() = ActiveTheme.textDim

val DangerRed = Color(0xFFEF4444)
val SuccessGreen = Color(0xFF10B981)

// ============================================================
// DUAL THEMES: OLED DARK & PURE WHITE
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
    val gradientColors: List<Color>,
    val isLight: Boolean = false
) {
    OLED_DARK(
        id = "dark",
        displayName = "Dark Mode",
        subtitle = "Zero Lag Pitch Black",
        icon = "🌙",
        primary = Color(0xFFFFFFFF),
        primaryLight = Color(0xFFE2E8F0),
        primaryBright = Color(0xFFFFFFFF),
        primaryDark = Color(0xFF64748B),
        bgBase = Color(0xFF000000),
        panelBg = Color(0xFF0F0F12),
        panelElevated = Color(0xFF1C1C22),
        border = Color(0xFF2E2E38),
        textMain = Color(0xFFFFFFFF),
        textDim = Color(0xFFA1A1AA),
        gradientColors = listOf(Color(0xFFFFFFFF), Color(0xFFE2E8F0), Color(0xFFCBD5E1)),
        isLight = false
    ),
    PURE_WHITE(
        id = "white",
        displayName = "White Mode",
        subtitle = "Clean Minimalist Light",
        icon = "☀️",
        primary = Color(0xFF0F172A),
        primaryLight = Color(0xFF334155),
        primaryBright = Color(0xFF000000),
        primaryDark = Color(0xFF64748B),
        bgBase = Color(0xFFF8FAFC),
        panelBg = Color(0xFFFFFFFF),
        panelElevated = Color(0xFFF1F5F9),
        border = Color(0xFFE2E8F0),
        textMain = Color(0xFF0F172A),
        textDim = Color(0xFF64748B),
        gradientColors = listOf(Color(0xFF0F172A), Color(0xFF334155), Color(0xFF1E293B)),
        isLight = true
    );

    companion object {
        fun fromId(id: String?): AppTheme {
            if (id.equals("white", ignoreCase = true) || id.equals("light", ignoreCase = true)) {
                return PURE_WHITE
            }
            return OLED_DARK
        }
    }
}

// Active dynamic theme holder
var ActiveTheme = AppTheme.OLED_DARK

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
