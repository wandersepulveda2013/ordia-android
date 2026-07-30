package com.ordia.app.intelligence

import android.content.Context
import android.util.Log

/**
 * Enrutador de inteligencia que selecciona el proveedor adecuado
 * según disponibilidad y configuración.
 *
 * FLUJO:
 * 1. IntelligenceSafetyGate.evaluate() — bloquea contenido sensible
 * 2. Si el modelo local está disponible y el usuario lo activó:
 *    → LocalModelProvider
 * 3. Si no hay modelo local:
 *    → BasicRuleProvider (modo reglas, NUNCA llamarlo inteligencia)
 *
 * El router nunca expone al usuario el nombre "Inteligencia" cuando
 * solo BasicRuleProvider está activo. En la UI se muestra como
 * "Modo básico" o "Inteligencia local (modelo)".
 *
 * @property appContext Contexto de aplicación
 * @property localModelProvider Proveedor local (puede no estar cargado)
 * @property basicRuleProvider Proveedor de reglas (siempre disponible)
 * @property memory Sistema de memoria basado en acciones confirmadas
 */
class IntelligenceRouter(private val appContext: Context) {

    private val localModelProvider = LocalModelProvider(appContext)
    private val basicRuleProvider = BasicRuleProvider()
    val memory = IntelligenceMemory(appContext)

    /** ¿El usuario ha activado el modo de inteligencia local? */
    var isLocalModelEnabled: Boolean = false
        private set

    /** Indica si la inteligencia local REAL está disponible (modelo cargado) */
    val isLocalModelAvailable: Boolean
        get() = localModelProvider.isAvailable && isLocalModelEnabled

    /** Nombre del proveedor actual para mostrar en UI */
    val activeProviderName: String
        get() = if (isLocalModelAvailable) localModelProvider.displayName
        else basicRuleProvider.displayName

    /**
     * Activa o desactiva el modelo local.
     * Si se activa sin que el modelo esté listo, el router usará BasicRuleProvider
     * como fallback pero mostrará que el modo local está pendiente de descarga.
     */
    fun setLocalModelEnabled(enabled: Boolean) {
        isLocalModelEnabled = enabled
        Log.d(TAG, "Modelo local ${if (enabled) "activado" else "desactivado"}")

        if (enabled && !localModelProvider.isAvailable) {
            Log.w(TAG, "Modo local activado pero modelo no cargado. Usando BasicRuleProvider como fallback.")
        }
    }

    /**
     * Intenta cargar el modelo local en segundo plano.
     * Se llama desde la UI de configuración o desde el diálogo de descarga.
     */
    suspend fun loadLocalModel(): Boolean {
        return localModelProvider.loadModel()
    }

    /**
     * Analiza una solicitud de inteligencia y devuelve una respuesta estructurada.
     *
     * @param request Solicitud con texto y metadatos
     * @return Respuesta estructurada con el esquema IntelligenceSchema
     */
    suspend fun analyze(request: IntelligenceRequest): IntelligenceResponse {
        // 1. Safety Gate — siempre primero
        val privacyResult = IntelligenceSafetyGate.evaluate(request.originalText)
        if (privacyResult == PrivacyResult.BLOCKED) {
            Log.w(TAG, "Texto bloqueado por safety gate")
            return IntelligenceResponse(
                schema = IntelligenceSchema(privacyResult = PrivacyResult.BLOCKED),
                confidenceScore = 0f,
                providerSource = if (isLocalModelAvailable) ProviderSource.LOCAL_MODEL else ProviderSource.BASIC_RULE,
                processingTimeMs = 0L
            )
        }

        // 2. Enriquecer request con contexto de memoria
        val enrichedRequest = request.copy(
            recentConfirmedHistory = memory.getRecentContextLabels()
        )

        // 3. Elegir proveedor
        return if (isLocalModelAvailable) {
            Log.d(TAG, "Usando LocalModelProvider")
            localModelProvider.analyze(enrichedRequest)
        } else {
            Log.d(TAG, "Usando BasicRuleProvider (modelo local no disponible)")
            if (isLocalModelEnabled) {
                Log.d(TAG, "NOTA: Modo local activado pero modelo no cargado. " +
                        "El usuario debe descargar el modelo en Configuración > Inteligencia local.")
            }
            basicRuleProvider.analyze(enrichedRequest)
        }
    }

    /**
     * Registra una acción confirmada por el usuario en la memoria.
     */
    fun recordActionConfirmed(label: String, schema: IntelligenceSchema) {
        memory.recordConfirmedAction(label, schema)
    }

    companion object {
        private const val TAG = "IntelligenceRouter"
    }
}
