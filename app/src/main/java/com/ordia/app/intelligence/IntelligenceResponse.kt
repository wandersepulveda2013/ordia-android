package com.ordia.app.intelligence

/**
 * Respuesta estructurada del motor de inteligencia.
 *
 * @property schema Esquema estructurado parseado de la salida del proveedor
 * @property rawModelOutput Texto crudo de salida del modelo (solo para diagnóstico/auditoría)
 * @property confidenceScore Puntaje de confianza (0.0 - 1.0)
 * @property providerSource Indicador de qué proveedor generó la respuesta
 * @property processingTimeMs Tiempo de procesamiento en milisegundos
 */
data class IntelligenceResponse(
    val schema: IntelligenceSchema,
    val rawModelOutput: String? = null,
    val confidenceScore: Float = 0.0f,
    val providerSource: ProviderSource = ProviderSource.BASIC_RULE,
    val processingTimeMs: Long = 0L
) {
    /** ¿La respuesta es accionable? */
    val isActionable: Boolean
        get() = schema.privacyResult == PrivacyResult.SAFE &&
            schema.actionSuggested != ActionSuggested.NONE &&
            confidenceScore >= MIN_CONFIDENCE_FOR_ACTION

    /** ¿Requiere seguimiento por ambigüedad? */
    val needsFollowUp: Boolean
        get() = schema.followUpQuestion != null &&
            schema.certainty in setOf(Certainty.DUDOSO, Certainty.CONDICIONAL)

    companion object {
        /** Confianza mínima para sugerir una acción automática */
        const val MIN_CONFIDENCE_FOR_ACTION = 0.45f
    }
}

enum class ProviderSource(val value: String, val displayName: String) {
    BASIC_RULE("basicRule", "Modo básico (reglas)"),
    LOCAL_MODEL("localModel", "Inteligencia local (modelo)");

    companion object {
        fun fromValue(v: String): ProviderSource = entries.firstOrNull { it.value == v } ?: BASIC_RULE
    }
}
