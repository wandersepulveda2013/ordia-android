package com.ordia.app.domain

import kotlin.math.ceil

/** Cálculo por reloj absoluto; no depende de que cada delay dure exactamente un segundo. */
object FocusTimerRules {
    fun remainingSeconds(deadlineAt: Long, now: Long): Int =
        ceil((deadlineAt - now).coerceAtLeast(0L) / 1_000.0).toInt()
}
