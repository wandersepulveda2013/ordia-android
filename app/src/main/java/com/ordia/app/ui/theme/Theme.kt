package com.ordia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ordia.app.data.preferences.ThemeMode

val OrdiaInk = Color(0xFF1D1B17)
val OrdiaCream = Color(0xFFF7F3EB)
val OrdiaPaper = Color(0xFFFFFCF7)
val OrdiaGold = Color(0xFF8A682D)
val OrdiaGoldSoft = Color(0xFFD9BC7A)
val OrdiaSage = Color(0xFF76845F)
val OrdiaRose = Color(0xFFA87373)
val OrdiaLavender = Color(0xFF88759C)

private val LightColors = lightColorScheme(
    primary = OrdiaInk,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAE2D4),
    onPrimaryContainer = OrdiaInk,
    secondary = OrdiaGold,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2E5C9),
    onSecondaryContainer = Color(0xFF34270F),
    tertiary = OrdiaSage,
    onTertiary = Color.White,
    background = OrdiaCream,
    onBackground = OrdiaInk,
    surface = OrdiaPaper,
    onSurface = OrdiaInk,
    surfaceVariant = Color(0xFFEDE7DC),
    onSurfaceVariant = Color(0xFF625D55),
    outline = Color(0xFF8C8579),
    outlineVariant = Color(0xFFDAD1C2),
    error = Color(0xFF9A3E3E),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF3EDE2),
    onPrimary = Color(0xFF1A1916),
    primaryContainer = Color(0xFF34312B),
    onPrimaryContainer = Color(0xFFF5EFE4),
    secondary = OrdiaGoldSoft,
    onSecondary = Color(0xFF33270E),
    secondaryContainer = Color(0xFF4C3C1C),
    onSecondaryContainer = Color(0xFFFFE8AF),
    tertiary = Color(0xFFB9C99F),
    onTertiary = Color(0xFF263017),
    background = Color(0xFF141310),
    onBackground = Color(0xFFEDE8DF),
    surface = Color(0xFF1C1B18),
    onSurface = Color(0xFFEDE8DF),
    surfaceVariant = Color(0xFF2B2924),
    onSurfaceVariant = Color(0xFFC9C2B8),
    outline = Color(0xFF938C81),
    outlineVariant = Color(0xFF47433D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun OrdiaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = OrdiaTypography,
        shapes = OrdiaShapes,
        content = content
    )
}
