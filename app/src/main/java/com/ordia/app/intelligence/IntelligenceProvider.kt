package com.ordia.app.intelligence

/**
 * Interfaz común para todos los proveedores de análisis de inteligencia.
 *
 * Cada proveedor debe implementar el análisis de texto y devolver
 * una respuesta estructurada siguiendo IntelligenceSchema.
 *
 * El texto original NUNCA debe persistirse después del análisis.
 */
interface IntelligenceProvider {

    /** Nombre legible del proveedor para UI y diagnóstico */
    val displayName: String

    /** Identificador único del proveedor */
    val providerId: ProviderSource

    /**
     * Analiza una solicitud de inteligencia y produce una respuesta estructurada.
     *
     * @param request Solicitud con el texto a analizar
     * @return Respuesta estructurada. Nunca null: si no hay intención clara,
     *         devolver IntelligenceSchema con actionSuggested = NONE
     */
    suspend fun analyze(request: IntelligenceRequest): IntelligenceResponse

    /**
     * Indica si el proveedor está disponible actualmente.
     * BasicRuleProvider siempre devuelve true.
     * LocalModelProvider devuelve true solo si el modelo está cargado en memoria.
     */
    val isAvailable: Boolean
        get() = true
}
