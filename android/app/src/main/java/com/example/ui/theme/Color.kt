package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val BgDark = Color(0xFF0C0A05)
val PanelDark = Color(0xFF14110A)
val PanelElevated = Color(0xFF1C1810)
val LineBorder = Color(0xFF3A3221)
val LineBright = Color(0xFFD4C4A1)

val TextPrimary = Color(0xFFFDFBF7)
val TextDim = Color(0xFFA89F8B)

val GoldAccent = Color(0xFFC9962F)
val GoldLight = Color(0xFFE6C875)
val GoldBright = Color(0xFFFCF6BA)
val GoldDark = Color(0xFF7A6229)
val GoldFlame = Color(0xFFE5A93C)

val DangerRed = Color(0xFFC95C47)
val SuccessGreen = Color(0xFF8BA888)

val GoldGradientBrush = Brush.horizontalGradient(
    listOf(
        Color(0xFFBF953F),
        Color(0xFFFCF6BA),
        Color(0xFFB38728),
        Color(0xFFFBF5B7),
        Color(0xFFAA771C)
    )
)

val DialRimBrush = Brush.sweepGradient(
    listOf(
        Color(0xFFFFF2B2),
        Color(0xFFD4AF37),
        Color(0xFF8A6327),
        Color(0xFFD4AF37),
        Color(0xFFFFF2B2)
    )
)
