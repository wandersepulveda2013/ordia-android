package com.ordia.app.intelligence

import android.content.Context
import android.util.Log

/**
 * Enrutador local de análisis estructurado.
 *
 * Esta versión utiliza exclusivamente [BasicRuleProvider]. No descarga ni
 * intenta cargar modelos generativos sin una ruta de inferencia funcional.
 */
class IntelligenceRouter(appContext: Context) {

    private val basicRuleProvider = BasicRuleProvider()
    val memory = IntelligenceMemory(appContext)

    val activeProviderName: String get() = basicRuleProvider.displayName

    suspend fun analyze(request: IntelligenceRequest): IntelligenceResponse {
        val privacyResult = IntelligenceSafetyGate.evaluate(request.originalText)
        if (privacyResult == PrivacyResult.BLOCKED) {
            Log.w(TAG, "Texto bloqueado por el filtro local")
            return IntelligenceResponse(
                schema = IntelligenceSchema(privacyResult = PrivacyResult.BLOCKED),
                confidenceScore = 0f,
                providerSource = ProviderSource.BASIC_RULE,
                processingTimeMs = 0L
            )
        }

        val enrichedRequest = request.copy(
            recentConfirmedHistory = memory.getRecentContextLabels()
        )
        return basicRuleProvider.analyze(enrichedRequest)
    }

    fun recordActionConfirmed(label: String, schema: IntelligenceSchema) {
        memory.recordConfirmedAction(label, schema)
    }

    companion object {
        private const val TAG = "IntelligenceRouter"
    }
}
