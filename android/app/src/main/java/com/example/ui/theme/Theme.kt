package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val FocusTimerColorScheme = darkColorScheme(
    primary = GoldAccent,
    onPrimary = BgDark,
    primaryContainer = GoldDark,
    onPrimaryContainer = GoldBright,
    secondary = GoldLight,
    onSecondary = BgDark,
    background = BgDark,
    onBackground = TextPrimary,
    surface = PanelDark,
    onSurface = TextPrimary,
    surfaceVariant = PanelElevated,
    onSurfaceVariant = TextDim,
    outline = LineBorder,
    outlineVariant = LineBorder,
    error = DangerRed,
    onError = TextPrimary
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FocusTimerColorScheme,
        typography = Typography,
        content = content
    )
}
