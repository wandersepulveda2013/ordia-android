package com.ordia.app.context

/**
 * Evento contextual capturado desde cualquier fuente.
 * Se procesa una sola vez y se descarta inmediatamente después del análisis.
 * Nunca se almacena el texto original completo de forma persistente.
 */
data class ContextEvent(
    /** Origen de la captura */
    val source: ContextCaptureSource,
    /** Texto plano capturado (se descarta tras el análisis) */
    val rawText: String,
    /** Marca de tiempo en milisegundos */
    val timestampMs: Long,
    /** Paquete de la aplicación en primer plano (si está disponible) */
    val sourcePackage: String? = null,
    /** Nombre visible de la aplicación (si está disponible) */
    val sourceLabel: String? = null,
    /** Metadatos adicionales específicos de la fuente */
    val metadata: Map<String, String> = emptyMap()
) {
    /** Longitud segura del texto para análisis */
    val safeText: String get() = rawText.take(MAX_ANALYZED_CHARS)

    companion object {
        /** Máximo de caracteres a analizar por evento */
        const val MAX_ANALYZED_CHARS = 5_000
    }
}
