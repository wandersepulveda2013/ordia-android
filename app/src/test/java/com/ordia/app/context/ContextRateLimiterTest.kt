package com.ordia.app.context

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del rate-limiter del pipeline contextual (ORD-024):
 * - Primer evento de una fuente siempre pasa.
 * - Eventos seguidos de la misma fuente se descartan dentro del intervalo.
 * - Otras fuentes no se ven afectadas por el cooldown de una fuente.
 * - Pasado el intervalo, la fuente vuelve a procesar.
 * - Tope diario global descarta el exceso.
 */
class ContextRateLimiterTest {

    private val sourceA = ContextCaptureSource.NOTIFICATION
    private val sourceB = ContextCaptureSource.SCREEN_ADVANCED
    private val sourceC = ContextCaptureSource.KEYBOARD

    @Test
    fun firstEvent_passes() {
        ContextRateLimiter.reset()
        assertTrue(ContextRateLimiter.shouldProcess(sourceA, nowMs = 1_000L))
    }

    @Test
    fun immediateSecondEvent_sameSource_isRateLimited() {
        ContextRateLimiter.reset()
        assertTrue(ContextRateLimiter.shouldProcess(sourceA, nowMs = 1_000L))
        assertFalse(ContextRateLimiter.shouldProcess(sourceA, nowMs = 2_000L))
    }

    @Test
    fun eventAfterInterval_sameSource_passes() {
        ContextRateLimiter.reset()
        assertTrue(ContextRateLimiter.shouldProcess(sourceA, nowMs = 1_000L))
        assertTrue(ContextRateLimiter.shouldProcess(
            sourceA,
            nowMs = 1_000L + ContextRateLimiter.MIN_INTERVAL_MS + 1
        ))
    }

    @Test
    fun otherSource_passesDuringCooldown() {
        ContextRateLimiter.reset()
        assertTrue(ContextRateLimiter.shouldProcess(sourceA, nowMs = 1_000L))
        assertTrue(ContextRateLimiter.shouldProcess(sourceB, nowMs = 1_500L))
    }

    @Test
    fun sourcesAreTrackedIndependently() {
        ContextRateLimiter.reset()
        assertTrue(ContextRateLimiter.shouldProcess(sourceA, nowMs = 1_000L))
        assertTrue(ContextRateLimiter.shouldProcess(sourceB, nowMs = 1_000L))
        assertFalse(ContextRateLimiter.shouldProcess(sourceA, nowMs = 1_500L))
        assertTrue(ContextRateLimiter.shouldProcess(sourceC, nowMs = 1_500L))
    }

    @Test
    fun dailyLimit_blocksExcess() {
        ContextRateLimiter.reset()
        var t = 1_000L
        repeat(ContextRateLimiter.DAILY_LIMIT) { i ->
            assertTrue(
                "Evento ${i + 1} debía pasar",
                ContextRateLimiter.shouldProcess(sourceA, nowMs = t)
            )
            t += ContextRateLimiter.MIN_INTERVAL_MS
        }
        // El siguiente evento del día ya está dentro del tope diario.
        assertFalse(ContextRateLimiter.shouldProcess(sourceA, nowMs = t))
        assertFalse(ContextRateLimiter.shouldProcess(sourceB, nowMs = t))
    }

    @Test
    fun dailyLimit_rollsOverNextDay() {
        ContextRateLimiter.reset()
        var t = 1_000L
        repeat(ContextRateLimiter.DAILY_LIMIT) {
            ContextRateLimiter.shouldProcess(sourceA, nowMs = t)
            t += ContextRateLimiter.MIN_INTERVAL_MS
        }
        // Al día siguiente el tope se reinicia.
        val nextDay = 86_400_000L + 1_000L
        assertTrue(ContextRateLimiter.shouldProcess(sourceA, nowMs = nextDay))
    }

    @Test
    fun reset_clearsAllState() {
        ContextRateLimiter.reset()
        ContextRateLimiter.shouldProcess(sourceA, nowMs = 1_000L)
        ContextRateLimiter.reset()
        assertTrue(ContextRateLimiter.shouldProcess(sourceA, nowMs = 1_000L))
    }

    @Test
    fun limitsAreDocumentedConstants() {
        assertTrue(ContextRateLimiter.MIN_INTERVAL_MS == 2_000L)
        assertTrue(ContextRateLimiter.DAILY_LIMIT > 0)
    }
}
