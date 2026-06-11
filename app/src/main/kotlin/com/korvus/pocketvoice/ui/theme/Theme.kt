package com.korvus.pocketvoice.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightScheme = lightColorScheme(
    primary = VioletPrimary,
    onPrimary = White,
    primaryContainer = VioletPale,
    onPrimaryContainer = VioletDeep,

    secondary = PeachText,
    onSecondary = White,
    secondaryContainer = PeachBg,
    onSecondaryContainer = Color(0xFF3A1F00),

    tertiary = PinkRoseText,
    onTertiary = White,
    tertiaryContainer = PinkRoseBg,
    onTertiaryContainer = Color(0xFF50111D),

    background = White,
    onBackground = Ink,

    surface = White,
    onSurface = Ink,
    surfaceVariant = Cloud,
    onSurfaceVariant = MutedText,

    outline = Hairline,
    outlineVariant = Color(0xFFF6F5FB),

    error = CrimsonError,
    onError = White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    scrim = Color(0xFF000000),
    inverseSurface = Ink,
    inverseOnSurface = White,
)

private val DarkScheme = darkColorScheme(
    primary = VioletPrimaryDark,
    onPrimary = Color(0xFF1A0F35),
    primaryContainer = Color(0xFF3F2A8A),
    onPrimaryContainer = Color(0xFFE4DAFF),

    secondary = Color(0xFFFFB078),
    onSecondary = Color(0xFF3A1F00),
    secondaryContainer = Color(0xFF5E3818),
    onSecondaryContainer = Color(0xFFFFE0C8),

    tertiary = Color(0xFFFFA8C0),
    onTertiary = Color(0xFF50111D),
    tertiaryContainer = Color(0xFF7A2238),
    onTertiaryContainer = Color(0xFFFFDCE5),

    background = DarkBg,
    onBackground = DarkOn,

    surface = DarkSurface,
    onSurface = DarkOn,
    surfaceVariant = DarkSurfaceVar,
    onSurfaceVariant = DarkMuted,

    outline = Color(0xFF3D3956),
    outlineVariant = Color(0xFF2A283E),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    scrim = Color.Black,
    inverseSurface = DarkOn,
    inverseOnSurface = DarkBg,
)

@Composable
fun PocketVoiceTheme(
    darkTheme: Boolean = false,   // принудительно light — фон всегда белый
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // hero сверху фиолетовый → statusbar тоже фиолетовый
            window.statusBarColor = VioletPrimary.toArgb()
            window.navigationBarColor = scheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
