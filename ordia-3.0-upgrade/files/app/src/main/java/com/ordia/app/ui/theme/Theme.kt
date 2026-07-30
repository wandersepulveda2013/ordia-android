package com.ordia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ordia.app.data.preferences.ThemeMode

val OrdiaInk = Color(0xFF202331)
val OrdiaCream = Color(0xFFF6F4EF)
val OrdiaPaper = Color(0xFFFFFEFB)
val OrdiaGold = Color(0xFF8B651D)
val OrdiaGoldSoft = Color(0xFFE4C77E)
val OrdiaSage = Color(0xFF4E7C6C)
val OrdiaRose = Color(0xFFB25F6B)
val OrdiaLavender = Color(0xFF7868D8)
val OrdiaIndigo = Color(0xFF5E58C7)
val OrdiaMint = Color(0xFF73B7A2)
val OrdiaSunset = Color(0xFFE5A05A)
val OrdiaNight = Color(0xFF12131A)

private val LightColors = lightColorScheme(
    primary = OrdiaIndigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E4FF),
    onPrimaryContainer = Color(0xFF211B63),
    secondary = OrdiaGold,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF8E7BE),
    onSecondaryContainer = Color(0xFF332400),
    tertiary = OrdiaSage,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD6F0E5),
    onTertiaryContainer = Color(0xFF0B3A2D),
    background = OrdiaCream,
    onBackground = OrdiaInk,
    surface = OrdiaPaper,
    onSurface = OrdiaInk,
    surfaceVariant = Color(0xFFEDEAF2),
    onSurfaceVariant = Color(0xFF5E5C68),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF8F4),
    surfaceContainer = Color(0xFFF2F0EC),
    surfaceContainerHigh = Color(0xFFECE9E5),
    surfaceContainerHighest = Color(0xFFE5E2DE),
    outline = Color(0xFF7A7884),
    outlineVariant = Color(0xFFD3D0DA),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Color(0xFF303038),
    inverseOnSurface = Color(0xFFF5F1F7),
    inversePrimary = Color(0xFFC8C2FF),
    scrim = Color.Black
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC8C2FF),
    onPrimary = Color(0xFF302A78),
    primaryContainer = Color(0xFF46408E),
    onPrimaryContainer = Color(0xFFE7E4FF),
    secondary = OrdiaGoldSoft,
    onSecondary = Color(0xFF3D2F00),
    secondaryContainer = Color(0xFF584500),
    onSecondaryContainer = Color(0xFFF8E7BE),
    tertiary = Color(0xFF9BD5C0),
    onTertiary = Color(0xFF00382B),
    tertiaryContainer = Color(0xFF245143),
    onTertiaryContainer = Color(0xFFD6F0E5),
    background = OrdiaNight,
    onBackground = Color(0xFFE7E1E8),
    surface = Color(0xFF191A22),
    onSurface = Color(0xFFE7E1E8),
    surfaceVariant = Color(0xFF47464F),
    onSurfaceVariant = Color(0xFFC9C5D0),
    surfaceContainerLowest = Color(0xFF0D0E14),
    surfaceContainerLow = Color(0xFF171820),
    surfaceContainer = Color(0xFF1D1E26),
    surfaceContainerHigh = Color(0xFF272831),
    surfaceContainerHighest = Color(0xFF32323B),
    outline = Color(0xFF938F9A),
    outlineVariant = Color(0xFF47464F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE7E1E8),
    inverseOnSurface = Color(0xFF303038),
    inversePrimary = OrdiaIndigo,
    scrim = Color.Black
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
