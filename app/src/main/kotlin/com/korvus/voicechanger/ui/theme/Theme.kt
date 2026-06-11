package com.korvus.voicechanger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0E0E10)
val BgElev = Color(0xFF18181B)
val BgCard = Color(0xFF1F1F24)
val Line = Color(0x14FFFFFF)
val Ink = Color(0xFFFAFAFA)
val InkSoft = Color(0xFFA1A1AA)
val Accent = Color(0xFFE3FF5A)
val AccentDim = Color(0xFFB6CC3A)
val Warn = Color(0xFFF87171)

private val Scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Bg,
    secondary = AccentDim,
    background = Bg,
    onBackground = Ink,
    surface = BgElev,
    onSurface = Ink,
    surfaceVariant = BgCard,
    onSurfaceVariant = InkSoft,
    outline = Line,
    error = Warn,
)

@Composable
fun VoiceChangerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
