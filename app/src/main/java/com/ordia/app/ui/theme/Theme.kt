package com.ordia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ordia.app.data.preferences.AccentPalette
import com.ordia.app.data.preferences.ThemeMode

// Base Palette (Clean B&W foundation)
val OrdiaWhite = Color(0xFFFFFFFF)
val OrdiaBlack = Color(0xFF000000)

val OrdiaGray50 = Color(0xFFF9FAFB)
val OrdiaGray100 = Color(0xFFF3F4F6)
val OrdiaGray200 = Color(0xFFE5E7EB)
val OrdiaGray300 = Color(0xFFD1D5DB)
val OrdiaGray400 = Color(0xFF9CA3AF)
val OrdiaGray500 = Color(0xFF6B7280)
val OrdiaGray600 = Color(0xFF4B5563)
val OrdiaGray700 = Color(0xFF374151)
val OrdiaGray800 = Color(0xFF1F2937)
val OrdiaGray900 = Color(0xFF111827)
val OrdiaGray950 = Color(0xFF030712)

// Semantic Accents
val OrdiaPriority = Color(0xFFE53935)
val OrdiaPrioritySoft = Color(0xFFFFCDD2)
val OrdiaPriorityDark = Color(0xFFB71C1C)

val OrdiaAlert = Color(0xFFFFB300)
val OrdiaAlertSoft = Color(0xFFFFECB3)
val OrdiaAlertDark = Color(0xFFFF8F00)

val OrdiaSuccess = Color(0xFF43A047)
val OrdiaSuccessSoft = Color(0xFFC8E6C9)
val OrdiaSuccessDark = Color(0xFF1B5E20)

val OrdiaFocus = Color(0xFF1E88E5)
val OrdiaFocusSoft = Color(0xFFBBDEFB)
val OrdiaFocusDark = Color(0xFF0D47A1)

val OrdiaCalendar = Color(0xFF8E24AA)
val OrdiaCalendarSoft = Color(0xFFE1BEE7)
val OrdiaCalendarDark = Color(0xFF4A148C)

val OrdiaAutomations = Color(0xFF00ACC1)
val OrdiaAutomationsSoft = Color(0xFFB2EBF2)
val OrdiaAutomationsDark = Color(0xFF006064)

val OrdiaGuardians = Color(0xFF3949AB)
val OrdiaGuardiansSoft = Color(0xFFC5CAE9)
val OrdiaGuardiansDark = Color(0xFF1A237E)

/** Palette of accent colors chosen by the user. Each entry holds (secondary, secondaryContainer, onSecondaryContainer) for light and dark. */
data class AccentSwatch(
    val lightSecondary: Color,
    val lightSecondaryContainer: Color,
    val lightOnSecondaryContainer: Color,
    val darkSecondary: Color,
    val darkSecondaryContainer: Color,
    val darkOnSecondaryContainer: Color
)

// Maintained for backward compatibility but remapped to semantic colors or updated tones
val accentSwatches: Map<AccentPalette, AccentSwatch> = mapOf(
    AccentPalette.GOLD to AccentSwatch(
        OrdiaAlert, OrdiaAlertSoft, OrdiaAlertDark,
        OrdiaAlertSoft, OrdiaAlertDark, OrdiaAlertSoft
    ),
    AccentPalette.SAGE to AccentSwatch(
        OrdiaSuccess, OrdiaSuccessSoft, OrdiaSuccessDark,
        OrdiaSuccessSoft, OrdiaSuccessDark, OrdiaSuccessSoft
    ),
    AccentPalette.ROSE to AccentSwatch(
        OrdiaPriority, OrdiaPrioritySoft, OrdiaPriorityDark,
        OrdiaPrioritySoft, OrdiaPriorityDark, OrdiaPrioritySoft
    ),
    AccentPalette.LAVENDER to AccentSwatch(
        OrdiaCalendar, OrdiaCalendarSoft, OrdiaCalendarDark,
        OrdiaCalendarSoft, OrdiaCalendarDark, OrdiaCalendarSoft
    ),
    AccentPalette.OCEAN to AccentSwatch(
        OrdiaFocus, OrdiaFocusSoft, OrdiaFocusDark,
        OrdiaFocusSoft, OrdiaFocusDark, OrdiaFocusSoft
    ),
    AccentPalette.TERRACOTTA to AccentSwatch(
        Color(0xFFE64A19), Color(0xFFFFCCBC), Color(0xFFBF360C),
        Color(0xFFFFAB91), Color(0xFFBF360C), Color(0xFFFFCCBC)
    ),
    AccentPalette.SYSTEM to AccentSwatch(
        OrdiaFocus, OrdiaFocusSoft, OrdiaFocusDark,
        OrdiaFocusSoft, OrdiaFocusDark, OrdiaFocusSoft
    )
)

private val LightColors = lightColorScheme(
    primary = OrdiaGray900,
    onPrimary = OrdiaWhite,
    primaryContainer = OrdiaGray200,
    onPrimaryContainer = OrdiaGray900,
    secondary = OrdiaFocus,
    onSecondary = OrdiaWhite,
    secondaryContainer = OrdiaFocusSoft,
    onSecondaryContainer = OrdiaFocusDark,
    tertiary = OrdiaSuccess,
    onTertiary = OrdiaWhite,
    background = OrdiaWhite,
    onBackground = OrdiaGray900,
    surface = OrdiaGray50,
    onSurface = OrdiaGray900,
    surfaceVariant = OrdiaGray100,
    onSurfaceVariant = OrdiaGray600,
    outline = OrdiaGray300,
    outlineVariant = OrdiaGray200,
    error = OrdiaPriority,
    onError = OrdiaWhite
)

private val DarkColors = darkColorScheme(
    primary = OrdiaGray50,
    onPrimary = OrdiaGray900,
    primaryContainer = OrdiaGray800,
    onPrimaryContainer = OrdiaGray50,
    secondary = OrdiaFocusSoft,
    onSecondary = OrdiaGray900,
    secondaryContainer = OrdiaFocusDark,
    onSecondaryContainer = OrdiaFocusSoft,
    tertiary = OrdiaSuccessSoft,
    onTertiary = OrdiaGray900,
    background = OrdiaBlack,
    onBackground = OrdiaGray50,
    surface = OrdiaGray950,
    onSurface = OrdiaGray50,
    surfaceVariant = OrdiaGray900,
    onSurfaceVariant = OrdiaGray400,
    outline = OrdiaGray700,
    outlineVariant = OrdiaGray800,
    error = OrdiaPrioritySoft,
    onError = OrdiaPriorityDark
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
                onSecondary = OrdiaGray900,
                secondaryContainer = swatch.darkSecondaryContainer,
                onSecondaryContainer = swatch.darkOnSecondaryContainer
            )
        } else {
            base.copy(
                secondary = swatch.lightSecondary,
                onSecondary = OrdiaWhite,
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
