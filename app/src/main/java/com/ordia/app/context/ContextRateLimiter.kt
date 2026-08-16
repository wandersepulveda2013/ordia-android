package com.ordia.app.context

/**
 * Rate-limiter del pipeline contextual (ORD-024).
 *
 * Protege CPU y batería frente a ráfagas de eventos de las fuentes de
 * captura (notificaciones, accesibilidad, IME, UI):
 * - Mínimo [MIN_INTERVAL_MS] entre eventos de la MISMA fuente (~2 s).
 * - Tope global de [DAILY_LIMIT] eventos procesados por día.
 *
 * Objeto JVM sin dependencias de Android para poder testear en unit tests.
 * El estado es en memoria (no persiste entre reinicios del proceso).
 */
object ContextRateLimiter {

    /** Mínimo intervalo entre eventos de la misma fuente. */
    const val MIN_INTERVAL_MS = 2_000L

    /** Tope global de eventos procesados por día. */
    const val DAILY_LIMIT = 500

    /** Última marca de tiempo procesada por fuente. */
    private val lastProcessedBySource = HashMap<ContextCaptureSource, Long>()

    /** Día (epoch day) del contador diario actual. */
    private var dayBucket: Long = Long.MIN_VALUE
    private var dailyCount = 0

    /**
     * Decide si un evento de [source] debe procesarse ahora.
     * Devuelve false (descartar silenciosamente) si:
     * - La misma fuente emitió un evento hace menos de [MIN_INTERVAL_MS], o
     * - Ya se alcanzó el tope diario global.
     * Si devuelve true, registra el evento (actualiza la marca y el contador).
     */
    @Synchronized
    fun shouldProcess(source: ContextCaptureSource, nowMs: Long = System.currentTimeMillis()): Boolean {
        val currentDay = nowMs / 86_400_000L
        if (currentDay != dayBucket) {
            dayBucket = currentDay
            dailyCount = 0
        }
        if (dailyCount >= DAILY_LIMIT) return false

        val last = lastProcessedBySource[source]
        if (last != null && nowMs - last < MIN_INTERVAL_MS) return false

        lastProcessedBySource[source] = nowMs
        dailyCount++
        return true
    }

    /** Limpia el estado (para pruebas). */
    @Synchronized
    fun reset() {
        lastProcessedBySource.clear()
        dayBucket = Long.MIN_VALUE
        dailyCount = 0
    }
}
