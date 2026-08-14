package com.ordia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ordia.app.data.preferences.AccentPalette
import com.ordia.app.data.preferences.ThemeMode

val OrdiaInk = Color(0xFF0F0F0F) // Very dark gray, almost black
val OrdiaPaper = Color(0xFFFFFFFF) // Pure white
val OrdiaCream = Color(0xFFF9F9F9) // Slightly off-white background
val OrdiaGray200 = Color(0xFFE5E5E5) // Borders, outlines
val OrdiaGray500 = Color(0xFF737373) // Muted text
val OrdiaGray800 = Color(0xFF262626) // Secondary text, icons

// Colors for semantic purposes
val OrdiaPriorityUrgent = Color(0xFFE04F4F)
val OrdiaPriorityHigh = Color(0xFFD68A36)
val OrdiaSuccess = Color(0xFF488F60)

// Original accent colors
val OrdiaGold = Color(0xFF8A682D)
val OrdiaGoldSoft = Color(0xFFD9BC7A)
val OrdiaSage = Color(0xFF76845F)
val OrdiaRose = Color(0xFFA87373)
val OrdiaLavender = Color(0xFF88759C)

/** Palette of accent colors chosen by the user. Each entry holds (secondary, secondaryContainer, onSecondaryContainer) for light and dark. */
data class AccentSwatch(
    val lightSecondary: Color,
    val lightSecondaryContainer: Color,
    val lightOnSecondaryContainer: Color,
    val darkSecondary: Color,
    val darkSecondaryContainer: Color,
    val darkOnSecondaryContainer: Color
)

val accentSwatches: Map<AccentPalette, AccentSwatch> = mapOf(
    AccentPalette.GOLD to AccentSwatch(
        OrdiaGold, Color(0xFFF2E5C9), Color(0xFF34270F),
        OrdiaGoldSoft, Color(0xFF4C3C1C), Color(0xFFFFE8AF)
    ),
    AccentPalette.SAGE to AccentSwatch(
        OrdiaSage, Color(0xFFE0E8D6), Color(0xFF1F2A14),
        Color(0xFFB9C99F), Color(0xFF37431F), Color(0xFFDDEBC8)
    ),
    AccentPalette.ROSE to AccentSwatch(
        OrdiaRose, Color(0xFFF2DADA), Color(0xFF3A1717),
        Color(0xFFD9A3A3), Color(0xFF4A1F1F), Color(0xFFFFDADA)
    ),
    AccentPalette.LAVENDER to AccentSwatch(
        OrdiaLavender, Color(0xFFE4DCEE), Color(0xFF241B33),
        Color(0xFFB5A5CC), Color(0xFF332745), Color(0xFFE6DCF6)
    ),
    AccentPalette.OCEAN to AccentSwatch(
        Color(0xFF3E6680), Color(0xFFD2E0EA), Color(0xFF0F2533),
        Color(0xFF8FB4CC), Color(0xFF1F3A4C), Color(0xFFCFE2F0)
    ),
    AccentPalette.TERRACOTTA to AccentSwatch(
        Color(0xFFB5603E), Color(0xFFF2DDD0), Color(0xFF33180C),
        Color(0xFFD89A78), Color(0xFF4A2417), Color(0xFFF6D9C8)
    ),
    AccentPalette.SYSTEM to AccentSwatch(
        OrdiaGold, Color(0xFFF2E5C9), Color(0xFF34270F),
        OrdiaGoldSoft, Color(0xFF4C3C1C), Color(0xFFFFE8AF)
    )
)

private val LightColors = lightColorScheme(
    primary = OrdiaInk,
    onPrimary = OrdiaPaper,
    primaryContainer = OrdiaGray200,
    onPrimaryContainer = OrdiaInk,
    secondary = OrdiaInk,
    onSecondary = OrdiaPaper,
    secondaryContainer = OrdiaGray200,
    onSecondaryContainer = OrdiaInk,
    tertiary = OrdiaGray800,
    onTertiary = OrdiaPaper,
    background = OrdiaPaper,
    onBackground = OrdiaInk,
    surface = OrdiaPaper,
    onSurface = OrdiaInk,
    surfaceVariant = OrdiaCream,
    onSurfaceVariant = OrdiaGray500,
    outline = OrdiaGray500,
    outlineVariant = OrdiaGray200,
    error = OrdiaPriorityUrgent,
    onError = OrdiaPaper
)

private val DarkColors = darkColorScheme(
    primary = OrdiaPaper,
    onPrimary = OrdiaInk,
    primaryContainer = OrdiaGray800,
    onPrimaryContainer = OrdiaPaper,
    secondary = OrdiaPaper,
    onSecondary = OrdiaInk,
    secondaryContainer = OrdiaGray800,
    onSecondaryContainer = OrdiaPaper,
    tertiary = OrdiaGray200,
    onTertiary = OrdiaInk,
    background = OrdiaInk,
    onBackground = OrdiaPaper,
    surface = OrdiaInk,
    onSurface = OrdiaPaper,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = OrdiaGray500,
    outline = OrdiaGray500,
    outlineVariant = OrdiaGray800,
    error = Color(0xFFF27E7E),
    onError = OrdiaInk
)

@Composable
fun OrdiaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentPalette: AccentPalette = AccentPalette.GOLD,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (accentPalette == AccentPalette.SYSTEM && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        val context = androidx.compose.ui.platform.LocalContext.current
        if (dark) androidx.compose.material3.dynamicDarkColorScheme(context)
        else androidx.compose.material3.dynamicLightColorScheme(context)
    } else {
        val swatch = accentSwatches[accentPalette] ?: accentSwatches.getValue(AccentPalette.GOLD)
        val base = if (dark) DarkColors else LightColors
        if (dark) {
            base.copy(
                secondary = swatch.darkSecondary,
                onSecondary = Color(0xFF1A1916),
                secondaryContainer = swatch.darkSecondaryContainer,
                onSecondaryContainer = swatch.darkOnSecondaryContainer
            )
        } else {
            base.copy(
                secondary = swatch.lightSecondary,
                onSecondary = Color.White,
                secondaryContainer = swatch.lightSecondaryContainer,
                onSecondaryContainer = swatch.lightOnSecondaryContainer
            )
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = OrdiaTypography,
        shapes = OrdiaShapes,
        content = content
    )
}
