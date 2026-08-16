package com.ordia.app.intelligence

import android.content.Context
import android.util.Log

/**
 * API pública del sistema de inteligencia de Ordía 3.
 *
 * Punto de entrada único para todos los módulos que necesitan
 * analizar texto (ContextEngine, IME, Guardian).
 *
 * COMPORTAMIENTO:
 * - Delega en IntelligenceRouter y su motor de reglas deterministas local.
 * - Siempre ejecuta SafetyGate ANTES de cualquier proveedor.
 * - El texto original se descarta después del análisis.
 * - Memoria solo almacena acciones confirmadas (nunca texto).
 *
 * USO:
 *   OrdiaIntelligenceEngine.getInstance(context).analyze(request)
 *
 * @see IntelligenceRouter Enrutador interno
 * @see IntelligenceSafetyGate Filtro de seguridad
 * @see BasicRuleProvider Modo reglas (fallback)
 */
class OrdiaIntelligenceEngine private constructor(appContext: Context) {

    private val router = IntelligenceRouter(appContext)

    /** Referencia al enrutador para configuración avanzada */
    val intelligenceRouter: IntelligenceRouter get() = router

    /** Nombre del proveedor activo para UI */
    val activeProviderDisplayName: String
        get() = router.activeProviderName

    /**
     * Analiza un texto y produce una respuesta estructurada.
     *
     * @param request Solicitud con texto y metadatos
     * @return IntelligenceResponse con schema validado
     *
     * El texto original se descarta inmediatamente después del análisis.
     * Si el safety gate bloquea el contenido, actionSuggested será NONE.
     */
    suspend fun analyze(request: IntelligenceRequest): IntelligenceResponse {
        Log.d(TAG, "Analizando desde ${request.source}")
        return router.analyze(request)
    }

    /**
     * Versión simplificada para casos donde solo se necesita texto.
     */
    suspend fun analyzeText(
        text: String,
        source: com.ordia.app.context.ContextCaptureSource
    ): IntelligenceResponse {
        return analyze(
            IntelligenceRequest(
                originalText = text,
                source = source
            )
        )
    }

    /**
     * Confirma una acción sugerida por el motor, almacenándola en memoria.
     */
    fun confirmAction(label: String, response: IntelligenceResponse) {
        router.recordActionConfirmed(label, response.schema)
        Log.d(TAG, "Acción confirmada localmente")
    }

    /**
     * Prepara el motor para ser destruido.
     */
    fun shutdown() {
        Log.d(TAG, "OrdiaIntelligenceEngine shutdown")
    }

    companion object {
        private const val TAG = "OrdiaIntelligenceEngine"

        @Volatile
        private var instance: OrdiaIntelligenceEngine? = null

        @JvmStatic
        fun getInstance(context: Context): OrdiaIntelligenceEngine {
            return instance ?: synchronized(this) {
                instance ?: OrdiaIntelligenceEngine(context.applicationContext).also { instance = it }
            }
        }

        @JvmStatic
        fun resetInstance() {
            synchronized(this) {
                instance?.shutdown()
                instance = null
            }
        }
    }
}
