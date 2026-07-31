package com.ordia.app.context

import android.content.Context
import android.util.Log
import com.ordia.app.intelligence.IntelligenceRequest
import com.ordia.app.intelligence.IntelligenceResponse
import com.ordia.app.intelligence.OrdiaIntelligenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Motor contextual central de Ordía 3.
 *
 * Orquesta el pipeline completo:
 * ContextEvent → SafetyGate → IntelligenceEngine → Deduplicator → ConfirmationCoordinator → AuditLog → Output
 *
 * El motor de inteligencia ([OrdiaIntelligenceEngine]) reemplaza al antiguo
 * [ContextIntentEngine] y puede usar un modelo de lenguaje local (Gemma 2B)
 * o el modo de reglas ([BasicRuleProvider]) como fallback.
 *
 * Uso desde cualquier fuente de captura:
 *   ContextEngine.getInstance(context).processEventAsync(event)  // en una corrutina
 *
 * PRECAUCIÓN DE CONCURRENCIA:
 * El análisis (incluida la inferencia del modelo local) puede tardar segundos.
 * Las fuentes de captura (accesibilidad, notificaciones, IME, UI) DEBEN usar
 * las variantes suspend (*Async) desde una corrutina. Las variantes síncronas
 * (processEvent/processText) bloquean el hilo llamante y solo deben usarse en
 * contexto no-UI o pruebas.
 */
class ContextEngine private constructor(appContext: Context) {

    private val intelligenceEngine = OrdiaIntelligenceEngine.getInstance(appContext)
    private val deduplicator = ContextDeduplicator()
    val confirmationCoordinator = ContextConfirmationCoordinator()
    private val auditLog = ContextAuditLog(appContext)

    /** Oyentes de eventos del pipeline */
    private val listeners = mutableListOf<ContextEngineListener>()

    /**
     * Procesa un evento contextual completo (variante síncrona).
     *
     * BLOQUEA el hilo llamante durante todo el pipeline (incluida la
     * inferencia del modelo local). Úsala solo desde contexto no-UI o pruebas;
     * las fuentes de captura deben usar [processEventAsync].
     */
    fun processEvent(event: ContextEvent): ContextResult = runBlocking {
        processEventAsync(event)
    }

    /**
     * Procesa un evento contextual completo (variante suspend).
     *
     * Ejecuta el pipeline en [Dispatchers.Default]; la inferencia del modelo
     * local nunca bloquea el hilo de la UI. Puede llamarse desde cualquier
     * corrutina (accesibilidad, notificaciones, IME, LaunchedEffect...).
     */
    suspend fun processEventAsync(event: ContextEvent): ContextResult = withContext(Dispatchers.Default) {
        Log.d(TAG, "Processing event from ${event.source}")

        // 0. Filtro de privacidad previo (ORD-005): paquetes bloqueados (banca,
        //    autenticadores, gestores de contraseñas, apps médicas), campos de
        //    entrada sensibles y contenido sensible se descartan ANTES de
        //    cualquier análisis, para TODAS las fuentes (accesibilidad,
        //    notificaciones, IME, UI). Evita además el coste de la inferencia
        //    local sobre datos que jamás deben procesarse.
        if (ContextPrivacyFilter.shouldBlock(event)) {
            Log.d(TAG, "Privacy filter blocked event from ${event.source}")
            return@withContext ContextResult.Discarded(
                reason = DiscardReason.PRIVACY_FILTER,
                source = event.source
            )
        }

        // 1. Analizar con el motor de inteligencia unificado
        val request = IntelligenceRequest(
            originalText = event.rawText,
            source = event.source,
            sourcePackage = event.sourcePackage,
            timestampMs = event.timestampMs
        )

        val intelligenceResponse = intelligenceEngine.analyze(request)

        val schema = intelligenceResponse.schema

        // 2. Verificar safety gate
        if (schema.privacyResult == com.ordia.app.intelligence.PrivacyResult.BLOCKED ||
            !intelligenceResponse.isActionable) {
            return@withContext ContextResult.Discarded(
                reason = DiscardReason.PRIVACY_FILTER,
                source = event.source
            )
        }

        // 3. Convertir IntelligenceResponse a ContextIntent para compatibilidad
        val intent = intelligenceResponseToIntent(event, intelligenceResponse)

        // 4. Verificar duplicados
        if (deduplicator.isDuplicate(intent)) {
            Log.d(TAG, "Duplicate intent: ${intent.kind} — ${intent.title.take(40)}")
            auditLog.logIntentDiscarded(intent, DiscardReason.DUPLICATE)
            notifyOnDiscarded(intent, DiscardReason.DUPLICATE)
            return@withContext ContextResult.Discarded(
                reason = DiscardReason.DUPLICATE,
                source = event.source,
                intent = intent
            )
        }

        // 5. Marcar como visto
        deduplicator.markAsSeen(intent)

        // 6. Verificar si necesita confirmación
        val needsConfirmation = confirmationCoordinator.needsConfirmation(intent) ||
            intelligenceResponse.needsFollowUp ||
            intelligenceResponse.schema.certainty == com.ordia.app.intelligence.Certainty.DUDOSO

        if (needsConfirmation) {
            val confirmationId = confirmationCoordinator.registerPending(intent)
            auditLog.logIntentDiscarded(intent, DiscardReason.LOW_CONFIDENCE)
            Log.d(TAG, "Pending confirmation $confirmationId for: ${intent.title.take(40)}")
            notifyOnPending(intent, confirmationId)
            ContextResult.PendingConfirmation(
                confirmationId = confirmationId,
                intent = intent
            )
        } else {
            // 7. Confirmación automática
            intelligenceEngine.confirmAction(intent.title, intelligenceResponse)
            auditLog.logIntentCreated(intent)
            notifyOnCreated(intent)
            Log.d(TAG, "Auto-confirmed: ${intent.kind} — ${intent.title.take(40)}")
            ContextResult.Created(intent)
        }
    }

    /**
     * Convierte una respuesta de inteligencia estructurada al formato ContextIntent
     * para compatibilidad con el pipeline existente (deduplicator, confirmation, audit).
     */
    private fun intelligenceResponseToIntent(
        event: ContextEvent,
        response: IntelligenceResponse
    ): ContextIntent {
        return ContextIntent(
            id = java.util.UUID.randomUUID().toString(),
            kind = mapSuggestedActionToKind(response.schema.actionSuggested),
            title = buildString {
                append(response.schema.actor.displayName)
                if (response.schema.polarity == com.ordia.app.intelligence.Polarity.NEGATIVO) append(" NO")
                append(": ")
                append(response.schema.actionSuggested.displayName)
            },
            dueAt = null, // Se puede extraer del texto en una fase posterior
            confidence = response.confidenceScore,
            source = event.source,
            sourcePackage = event.sourcePackage
        )
    }

    private fun mapSuggestedActionToKind(action: com.ordia.app.intelligence.ActionSuggested): ContextIntentKind {
        return when (action) {
            com.ordia.app.intelligence.ActionSuggested.TASK -> ContextIntentKind.TASK
            com.ordia.app.intelligence.ActionSuggested.SHOPPING -> ContextIntentKind.SHOPPING
            com.ordia.app.intelligence.ActionSuggested.APPOINTMENT -> ContextIntentKind.APPOINTMENT
            com.ordia.app.intelligence.ActionSuggested.MEETING -> ContextIntentKind.MEETING
            com.ordia.app.intelligence.ActionSuggested.REMINDER -> ContextIntentKind.REMINDER
            com.ordia.app.intelligence.ActionSuggested.CALL -> ContextIntentKind.CALL
            com.ordia.app.intelligence.ActionSuggested.PAYMENT -> ContextIntentKind.PAYMENT
            com.ordia.app.intelligence.ActionSuggested.STUDY -> ContextIntentKind.STUDY
            com.ordia.app.intelligence.ActionSuggested.EXERCISE -> ContextIntentKind.EXERCISE
            com.ordia.app.intelligence.ActionSuggested.DEADLINE -> ContextIntentKind.DEADLINE
            com.ordia.app.intelligence.ActionSuggested.HOUSEHOLD -> ContextIntentKind.HOUSEHOLD
            com.ordia.app.intelligence.ActionSuggested.NONE -> ContextIntentKind.UNKNOWN
        }
    }

    /**
     * Procesa texto plano desde cualquier fuente (variante síncrona).
     *
     * BLOQUEA el hilo llamante. Úsala solo desde contexto no-UI o pruebas;
     * las fuentes de captura deben usar [processTextAsync].
     */
    fun processText(text: String, source: ContextCaptureSource): ContextResult = runBlocking {
        processTextAsync(text, source)
    }

    /**
     * Procesa texto plano desde cualquier fuente (variante suspend).
     *
     * Crea un [ContextEvent] y lo procesa con [processEventAsync] en
     * [Dispatchers.Default].
     */
    suspend fun processTextAsync(text: String, source: ContextCaptureSource): ContextResult {
        val event = ContextEvent(
            source = source,
            rawText = text,
            timestampMs = System.currentTimeMillis()
        )
        return processEventAsync(event)
    }

    /**
     * Resuelve una confirmación pendiente.
     */
    fun resolveConfirmation(confirmationId: String, accepted: Boolean, modifiedIntent: ContextIntent? = null): Boolean {
        val resolved = confirmationCoordinator.resolve(confirmationId, accepted, modifiedIntent)
        if (resolved && accepted) {
            val pending = confirmationCoordinator.allPending()
            // El intent original ya no está disponible, pero el callback del coordinator lo maneja
        }
        return resolved
    }

    /**
     * Obtiene todas las confirmaciones pendientes.
     */
    fun getPendingConfirmations(): List<PendingConfirmation> {
        return confirmationCoordinator.allPending()
    }

    /**
     * Registra un oyente para eventos del pipeline.
     */
    fun addListener(listener: ContextEngineListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    /**
     * Elimina un oyente.
     */
    fun removeListener(listener: ContextEngineListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    /**
     * Exporta el registro de auditoría como JSON.
     */
    fun exportAuditLog(): String = auditLog.exportAsJson()

    /**
     * Obtiene estadísticas del registro de auditoría.
     */
    fun getAuditStats(): AuditStats = auditLog.getStats()

    /**
     * Limpia registros de auditoría antiguos.
     */
    fun cleanAuditLog(ageMs: Long = DEFAULT_AUDIT_RETENTION_MS): Int = auditLog.cleanOlderThan(ageMs)

    /**
     * Prepara el motor para ser destruido.
     */
    fun shutdown() {
        auditLog.close()
        synchronized(listeners) {
            listeners.clear()
        }
    }

    // Private: notificaciones a oyentes

    private fun notifyOnCreated(intent: ContextIntent) {
        synchronized(listeners) {
            listeners.forEach { it.onIntentCreated(intent) }
        }
    }

    private fun notifyOnDiscarded(intent: ContextIntent, reason: DiscardReason) {
        synchronized(listeners) {
            listeners.forEach { it.onIntentDiscarded(intent, reason) }
        }
    }

    private fun notifyOnPending(intent: ContextIntent, confirmationId: String) {
        synchronized(listeners) {
            listeners.forEach { it.onConfirmationPending(intent, confirmationId) }
        }
    }

    override fun toString(): String {
        val pending = confirmationCoordinator.pendingCount()
        val auditStats = try { auditLog.getStats().totalEntries } catch (e: Exception) { -1 }
        return "ContextEngine(pending=$pending, audit=$auditStats)"
    }

    companion object {
        private const val TAG = "ContextEngine"
        private const val DEFAULT_AUDIT_RETENTION_MS = 30L * 86_400_000L // 30 días

        @Volatile
        private var instance: ContextEngine? = null

        /**
         * Obtiene la instancia singleton del motor contextual.
         */
        @JvmStatic
        fun getInstance(context: Context): ContextEngine {
            return instance ?: synchronized(this) {
                instance ?: ContextEngine(context.applicationContext).also { instance = it }
            }
        }

        /**
         * Reinicia el singleton (solo para pruebas).
         */
        @JvmStatic
        fun resetInstance() {
            synchronized(this) {
                instance?.shutdown()
                instance = null
            }
        }
    }
}

/** Oyente de eventos del pipeline contextual */
interface ContextEngineListener {
    /** Se llamó cuando una intención se crea automáticamente */
    fun onIntentCreated(intent: ContextIntent) {}
    /** Se llamó cuando una intención se descarta */
    fun onIntentDiscarded(intent: ContextIntent, reason: DiscardReason) {}
    /** Se llamó cuando una intención queda pendiente de confirmación */
    fun onConfirmationPending(intent: ContextIntent, confirmationId: String) {}
}

/** Resultado del procesamiento de un evento contextual */
sealed class ContextResult {
    /** Intención creada automáticamente */
    data class Created(val intent: ContextIntent) : ContextResult()
    /** Pendiente de confirmación del usuario */
    data class PendingConfirmation(
        val confirmationId: String,
        val intent: ContextIntent
    ) : ContextResult()
    /** Descartado (filtro de privacidad, duplicado, baja confianza) */
    data class Discarded(
        val reason: DiscardReason,
        val source: ContextCaptureSource,
        val intent: ContextIntent? = null
    ) : ContextResult()
}
