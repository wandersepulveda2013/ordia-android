package com.ordia.app.intelligence

import com.ordia.app.context.ContextCaptureSource

/**
 * Solicitud de análisis de inteligencia.
 * El texto completo se pasa al proveedor y se descarta inmediatamente después del análisis.
 *
 * @property originalText Texto completo capturado. Se borra post-análisis.
 * @property source Origen de la captura (teclado, notificación, overlay, etc.)
 * @property sourcePackage Paquete de la aplicación de origen (nullable)
 * @property timestampMs Momento de la captura en milisegundos UTC
 * @property recentConfirmedHistory Últimas 5 acciones confirmadas por el usuario
 *   (solo títulos, sin texto original). Útil para contexto entre turnos.
 */
data class IntelligenceRequest(
    val originalText: String,
    val source: ContextCaptureSource,
    val sourcePackage: String? = null,
    val timestampMs: Long = System.currentTimeMillis(),
    val recentConfirmedHistory: List<String> = emptyList()
) {
    /** Texto sanitizado sin información sensible (se aplica SafetyGate antes de llegar aquí) */
    val safeText: String
        get() = originalText.trim().take(MAX_TEXT_LENGTH)

    companion object {
        private const val MAX_TEXT_LENGTH = 500
    }
}
