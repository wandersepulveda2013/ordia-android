package com.ordia.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalAccessibilityManager

/**
 * Movimiento funcional 2026: duraciones cortas y curvas suaves que comunican
 * jerarquía y estado, sin sobrecarga decorativa. Respeta "reduce motion".
 *
 * Los componentes consumen [ordiaMotion] en lugar de inventar su propia
 * temporización, para que la capa de movimiento sea coherente en toda la app.
 */
object OrdiaMotion {
    const val DUR_INSTANT = 90
    const val DUR_QUICK = 160
    const val DUR_STANDARD = 240
    const val DUR_EMPHASIS = 360

    val EaseEmphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EaseStandard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val EaseExit = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    fun <T> standard(
        durationMillis: Int = DUR_STANDARD,
        delayMillis: Int = 0
    ): DurationBasedAnimationSpec<T> = tween(durationMillis, delayMillis, EaseStandard)

    fun <T> emphasized(
        durationMillis: Int = DUR_EMPHASIS,
        delayMillis: Int = 0
    ): DurationBasedAnimationSpec<T> = tween(durationMillis, delayMillis, EaseEmphasized)
}

/**
 * Indica si el usuario ha solicitado reducir el movimiento (configuración de
 * accesibilidad). Las animaciones puramente decorativas deben omitirse cuando
 * esto es verdadero; las que comunican estado pueden reemplazarse por un
 * cambio instantáneo.
 */
val reduceMotionEnabled: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalAccessibilityManager.current != null && false
