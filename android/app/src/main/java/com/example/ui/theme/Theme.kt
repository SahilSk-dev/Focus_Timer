package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalAppTheme = staticCompositionLocalOf { AppTheme.EMERALD_FOREST }

@Composable
fun MyApplicationTheme(
    appTheme: AppTheme = AppTheme.EMERALD_FOREST,
    content: @Composable () -> Unit
) {
    ActiveTheme = appTheme

    val colorScheme = darkColorScheme(
        primary = appTheme.primary,
        onPrimary = appTheme.bgBase,
        primaryContainer = appTheme.primaryDark,
        onPrimaryContainer = appTheme.primaryBright,
        secondary = appTheme.primaryLight,
        onSecondary = appTheme.bgBase,
        background = appTheme.bgBase,
        onBackground = appTheme.textMain,
        surface = appTheme.panelBg,
        onSurface = appTheme.textMain,
        surfaceVariant = appTheme.panelElevated,
        onSurfaceVariant = appTheme.textDim,
        outline = appTheme.border,
        outlineVariant = appTheme.border,
        error = DangerRed,
        onError = TextPrimary
    )

    CompositionLocalProvider(LocalAppTheme provides appTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
