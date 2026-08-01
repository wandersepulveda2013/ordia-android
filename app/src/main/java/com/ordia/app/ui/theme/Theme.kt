package com.ordia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ordia.app.data.preferences.ThemeMode

/**
 * Identidad visual Ordia (rediseño limpio, no saturado).
 * - Fondo blanco cálido, superficies blancas, bordes suaves y sombras discretas.
 * - Primario: violeta profundo. Secundario: lavanda suave. Acento: dorado cálido.
 * - Texto: azul marino muy oscuro / gris azulado. Positivo: verde suave. Error: rojo moderado.
 */
val OrdiaInk = Color(0xFF1C2340)        // Azul marino muy oscuro (texto principal)
val OrdiaInkSoft = Color(0xFF5B6173)    // Gris azulado (texto secundario)
val OrdiaCream = Color(0xFFF8F7F4)      // Blanco cálido (fondo)
val OrdiaPaper = Color(0xFFFFFFFF)      // Superficies
val OrdiaGold = Color(0xFF8B651D)       // Acento dorado cálido
val OrdiaGoldSoft = Color(0xFFE4C77E)   // Acento dorado suave
val OrdiaSage = Color(0xFF3E8F6D)       // Verde suave (estados positivos)
val OrdiaRose = Color(0xFFB25F6B)
val OrdiaLavender = Color(0xFF7A6FD0)   // Lavanda (secundario)
val OrdiaViolet = Color(0xFF4E43B8)     // Violeta profundo (primario)
val OrdiaMint = Color(0xFF73B7A2)
val OrdiaSunset = Color(0xFFE5A05A)
val OrdiaNight = Color(0xFF13141B)      // Navy profundo (fondo oscuro)

private val LightColors = lightColorScheme(
    primary = OrdiaViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E6FF),
    onPrimaryContainer = Color(0xFF241C66),
    secondary = OrdiaLavender,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFEDFB),
    onSecondaryContainer = Color(0xFF2B2460),
    tertiary = OrdiaSage,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDF2E7),
    onTertiaryContainer = Color(0xFF0B3A2D),
    background = OrdiaCream,
    onBackground = OrdiaInk,
    surface = OrdiaPaper,
    onSurface = OrdiaInk,
    surfaceVariant = Color(0xFFF0EFF6),
    onSurfaceVariant = OrdiaInkSoft,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F4F1),
    surfaceContainer = Color(0xFFF1F0ED),
    surfaceContainerHigh = Color(0xFFECEAF0),
    surfaceContainerHighest = Color(0xFFE5E3EA),
    outline = Color(0xFF7A7E8E),
    outlineVariant = Color(0xFFD9D7E0),
    error = Color(0xFFC04848),
    onError = Color.White,
    errorContainer = Color(0xFFF9E3E3),
    onErrorContainer = Color(0xFF5C1A1A),
    inverseSurface = Color(0xFF2A2F45),
    inverseOnSurface = Color(0xFFF4F4F8),
    inversePrimary = Color(0xFFC9C3FF),
    scrim = Color.Black
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC9C3FF),
    onPrimary = Color(0xFF2F2770),
    primaryContainer = Color(0xFF4A41A8),
    onPrimaryContainer = Color(0xFFE9E6FF),
    secondary = Color(0xFFB7B0E8),
    onSecondary = Color(0xFF332C60),
    secondaryContainer = Color(0xFF524B8C),
    onSecondaryContainer = Color(0xFFEDEBFF),
    tertiary = Color(0xFF8FCFAE),
    onTertiary = Color(0xFF0A3B2A),
    tertiaryContainer = Color(0xFF25604A),
    onTertiaryContainer = Color(0xFFDDF2E7),
    background = OrdiaNight,
    onBackground = Color(0xFFE7E6EE),
    surface = Color(0xFF1A1B24),
    onSurface = Color(0xFFE7E6EE),
    surfaceVariant = Color(0xFF454556),
    onSurfaceVariant = Color(0xFFC6C6D3),
    surfaceContainerLowest = Color(0xFF0D0E14),
    surfaceContainerLow = Color(0xFF181922),
    surfaceContainer = Color(0xFF1E1F29),
    surfaceContainerHigh = Color(0xFF282935),
    surfaceContainerHighest = Color(0xFF33343F),
    outline = Color(0xFF8E8E9C),
    outlineVariant = Color(0xFF454556),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE7E6EE),
    inverseOnSurface = Color(0xFF2A2F45),
    inversePrimary = OrdiaViolet,
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
