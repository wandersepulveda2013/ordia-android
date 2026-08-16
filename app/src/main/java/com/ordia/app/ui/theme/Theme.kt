package com.ordia.app.ui.theme

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
val OrdiaInk = Color(0xFF1A1B22)
val OrdiaInkSoft = Color(0xFF5B5D6B)
val OrdiaCanvas = Color(0xFFF6F4EF)
val OrdiaPaper = Color(0xFFFCFBF8)
val OrdiaAccent = Color(0xFF4B4FCF)
val OrdiaAccentStrong = Color(0xFF3237B0)
val OrdiaAccentSoft = Color(0xFFE5E6FB)
val OrdiaCyan = Color(0xFF1AA6B8)
val OrdiaCyanSoft = Color(0xFFD6F2F5)
val OrdiaCoral = Color(0xFFE06A3F)
val OrdiaCoralSoft = Color(0xFFFBE3D8)
val OrdiaSuccess = Color(0xFF1E8A56)
val OrdiaWarning = Color(0xFF9A6700)
val OrdiaDanger = Color(0xFFB42318)
val OrdiaNight = Color(0xFF0C0D12)
val OrdiaFocus = Color(0xFF3237B0)

private val LightColors = lightColorScheme(
    primary = OrdiaAccent,
    onPrimary = Color.White,
    primaryContainer = OrdiaAccentSoft,
    onPrimaryContainer = OrdiaAccentStrong,
    secondary = OrdiaCyan,
    onSecondary = Color(0xFF062B2E),
    secondaryContainer = OrdiaCyanSoft,
    onSecondaryContainer = Color(0xFF06343A),
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
    inversePrimary = OrdiaAccent,
    scrim = Color.Black
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9BCFF),
    onPrimary = Color(0xFF1A1C4A),
    primaryContainer = Color(0xFF3237B0),
    onPrimaryContainer = Color(0xFFE5E6FB),
    secondary = Color(0xFF6FD3E0),
    onSecondary = Color(0xFF04303A),
    secondaryContainer = Color(0xFF0F4C56),
    onSecondaryContainer = Color(0xFFD6F2F5),
    tertiary = Color(0xFF72C79D),
    onTertiary = Color(0xFF083523),
    tertiaryContainer = Color(0xFF174B35),
    onTertiaryContainer = Color(0xFFDDF5E9),
    background = OrdiaNight,
    onBackground = Color(0xFFEDEBF1),
    surface = Color(0xFF13141A),
    onSurface = Color(0xFFEDEBF1),
    surfaceVariant = Color(0xFF2B2C36),
    onSurfaceVariant = Color(0xFFB6B5C3),
    surfaceContainerLowest = Color(0xFF070809),
    surfaceContainerLow = Color(0xFF171820),
    surfaceContainer = Color(0xFF1E1F28),
    surfaceContainerHigh = Color(0xFF272832),
    surfaceContainerHighest = Color(0xFF33343F),
    outline = Color(0xFF8A8A99),
    outlineVariant = Color(0xFF3E3F4A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE7E6EE),
    inverseOnSurface = Color(0xFF202028),
    inversePrimary = OrdiaAccent,
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
