package com.ordia.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tokens semánticos 2026 de Ordía.
 *
 * Un único punto de verdad para color y elevación con significado, accesible
 * desde cualquier composable vía [ordiaTokens]. Esto permite que las pantallas
 * y componentes se refieran a conceptos (acento, advertencia, superficie
 * elevada) en lugar de a roles crudos de Material.
 */
data class OrdiaColorTokens(
    val background: Color,
    val surface: Color,
    val surfaceContainer: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentStrong: Color,
    val accentSoft: Color,
    val activity: Color,
    val activitySoft: Color,
    val human: Color,
    val humanSoft: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val divider: Color,
    val focus: Color
)

data class OrdiaElevationTokens(
    val flat: Dp = 0.dp,
    val raised: Dp = 1.dp,
    val card: Dp = 2.dp,
    val overlay: Dp = 6.dp
)

val ordiaTokens: OrdiaColorTokens
    @Composable
    @ReadOnlyComposable
    get() = with(MaterialTheme.colorScheme) {
        OrdiaColorTokens(
            background = background,
            surface = surface,
            surfaceContainer = surfaceContainer,
            surfaceElevated = surfaceContainerHighest,
            textPrimary = onSurface,
            textSecondary = onSurfaceVariant,
            textTertiary = onSurfaceVariant.copy(alpha = 0.6f),
            accent = primary,
            accentStrong = primary,
            accentSoft = primaryContainer,
            activity = secondary,
            activitySoft = secondaryContainer,
            human = OrdiaCoral,
            humanSoft = OrdiaCoralSoft,
            success = OrdiaSuccess,
            warning = OrdiaWarning,
            danger = error,
            divider = outlineVariant,
            focus = OrdiaFocus
        )
    }

val ordiaElevation = OrdiaElevationTokens()
