package com.ordia.app.ui.theme
import com.ordia.app.ui.components.*

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ordia.app.data.preferences.ThemeMode

/**
 * Identidad visual oficial de Ordía.
 *
 * La interfaz es deliberadamente neutra: blanco, negro y grises. El azul se
 * reserva como un único acento de orientación y nunca domina superficies
 * completas. Verde, ámbar y rojo se usan solo para estados semánticos.
 */
val OrdiaInk = Color(0xFF111111)
val OrdiaInkSoft = Color(0xFF5F5F5F)
val OrdiaCanvas = Color(0xFFFAFAFA)
val OrdiaPaper = Color(0xFFFFFFFF)
val OrdiaAccent = Color(0xFF315EF5)
val OrdiaSuccess = Color(0xFF247A52)
val OrdiaWarning = Color(0xFF9A6700)
val OrdiaDanger = Color(0xFFB42318)
val OrdiaNight = Color(0xFF0B0B0B)

private val LightColors = lightColorScheme(
    primary = OrdiaInk,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E9E9),
    onPrimaryContainer = OrdiaInk,
    secondary = Color(0xFF3D3D3D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0F0F0),
    onSecondaryContainer = OrdiaInk,
    tertiary = OrdiaSuccess,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE2F3EA),
    onTertiaryContainer = Color(0xFF123D2C),
    background = OrdiaCanvas,
    onBackground = OrdiaInk,
    surface = OrdiaPaper,
    onSurface = OrdiaInk,
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = OrdiaInkSoft,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7F7),
    surfaceContainer = Color(0xFFF1F1F1),
    surfaceContainerHigh = Color(0xFFEAEAEA),
    surfaceContainerHighest = Color(0xFFE2E2E2),
    outline = Color(0xFF747474),
    outlineVariant = Color(0xFFDADADA),
    error = OrdiaDanger,
    onError = Color.White,
    errorContainer = Color(0xFFFDE7E5),
    onErrorContainer = Color(0xFF5F1410),
    inverseSurface = Color(0xFF202020),
    inverseOnSurface = Color(0xFFF7F7F7),
    inversePrimary = Color.White,
    scrim = Color.Black
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF4F4F4),
    onPrimary = Color(0xFF111111),
    primaryContainer = Color(0xFF303030),
    onPrimaryContainer = Color(0xFFF5F5F5),
    secondary = Color(0xFFD0D0D0),
    onSecondary = Color(0xFF171717),
    secondaryContainer = Color(0xFF272727),
    onSecondaryContainer = Color(0xFFECECEC),
    tertiary = Color(0xFF72C79D),
    onTertiary = Color(0xFF083523),
    tertiaryContainer = Color(0xFF174B35),
    onTertiaryContainer = Color(0xFFDDF5E9),
    background = OrdiaNight,
    onBackground = Color(0xFFF0F0F0),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFF0F0F0),
    surfaceVariant = Color(0xFF2B2B2B),
    onSurfaceVariant = Color(0xFFBEBEBE),
    surfaceContainerLowest = Color(0xFF070707),
    surfaceContainerLow = Color(0xFF151515),
    surfaceContainer = Color(0xFF1B1B1B),
    surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF303030),
    outline = Color(0xFF8A8A8A),
    outlineVariant = Color(0xFF3E3E3E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE7E6EE),
    inverseOnSurface = Color(0xFF202020),
    inversePrimary = OrdiaInk,
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
