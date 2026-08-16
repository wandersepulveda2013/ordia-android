package com.ordia.app.context.external

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ordia.app.R
import com.ordia.app.context.*
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Controlador central de confirmaciones contextuales externas.
 *
 * Responsabilidades:
 * - Recibir resultados del ContextEngine via ContextEngineListener
 * - Gestionar la cola FIFO con prioridades (una sugerencia visible)
 * - Verificar permisos de superposición y seguridad
 * - Coordinar con el guardián flotante
 * - Integrar con el IME (deduplicación)
 * - Persistir la cola para recuperación ante muerte de proceso
 * - No almacenar nunca texto original del usuario
 * - No procesar contenido sensible
 *
 * SINGLETON. Obtener vía [getInstance].
 */
class ExternalConfirmationController private constructor(
    private val app: Context
) : ContextEngineListener {

    companion object {
        private const val TAG = "ExtConfirmCtrl"
        private const val NOTIFICATION_CHANNEL_ID = "ordia_external_suggestion"
        private const val NOTIFICATION_ID = 2001
        private const val QUEUE_EXPIRY_CHECK_MS = 60_000L

        // El singleton guarda SIEMPRE applicationContext (ver getInstance), por lo
        // que no retiene Activities ni vistas: vive lo que el proceso (ORD-036).
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: ExternalConfirmationController? = null

        // Extras para Intents
        const val EXTRA_SUGGESTION_ID = "ordia_suggestion_id"
        const val EXTRA_KIND = "ordia_kind"
        const val EXTRA_TITLE = "ordia_title"
        const val EXTRA_DUE_AT = "ordia_due_at"
        const val EXTRA_CONFIRMATION_ID = "ordia_confirmation_id"
        const val EXTRA_ACTION = "ordia_action"

        // Acciones
        const val ACTION_ADD = "com.ordia.app.action.ADD_SUGGESTION"
        const val ACTION_EDIT = "com.ordia.app.action.EDIT_SUGGESTION"
        const val ACTION_IGNORE = "com.ordia.app.action.IGNORE_SUGGESTION"
        const val ACTION_POSTPONE = "com.ordia.app.action.POSTPONE_SUGGESTION"
        const val ACTION_OPEN = "com.ordia.app.action.OPEN_SUGGESTION"

        // Paquetes sensibles donde nunca mostrar tarjeta externa.
        // Fuente única de verdad: ExternalSecureContext (compatible con el
        // ContextPrivacyFilter del pipeline contextual). Los nombres reales
        // incluyen sufijos (com.bbva.mx), por eso se compara por prefijo.
        val SECURE_PACKAGES: Set<String> = ExternalSecureContext.SECURE_PACKAGES

        @JvmStatic
        fun getInstance(context: Context): ExternalConfirmationController {
            return instance ?: synchronized(this) {
                instance ?: ExternalConfirmationController(context.applicationContext).also {
                    instance = it
                }
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

    // ========================================================================
    // Dependencias
    // ========================================================================

    val queue: ExternalSuggestionQueue = ExternalSuggestionQueue()
    val repository: ExternalConfirmationRepository by lazy {
        ExternalConfirmationRepository(app)
    }

    // ========================================================================
    // Callbacks del listener de estado del guardián
    // ========================================================================

    /**
     * Listener opcional para que el GuardianOverlayService reaccione
     * a cambios en la sugerencia externa sin acoplar el controlador al overlay.
     */
    @Volatile
    var guardianSuggestionListener: GuardSuggestionListener? = null

    fun interface GuardSuggestionListener {
        fun onSuggestionChanged(
            suggestion: ExternalSuggestion?,
            event: GuardSuggestionEvent
        )
    }

    enum class GuardSuggestionEvent {
        SUGGESTION_DETECTED,
        APPROACHING_CARD,
        POINTING,
        WAITING_FOR_DECISION,
        CONFIRMING,
        DISMISSED,
        RETURNING_TO_IDLE
    }

    // ========================================================================
    // Estado
    // ========================================================================

    @Volatile
    private var isEnabled = false

    @Volatile
    private var engine: ContextEngine? = null

    @Volatile
    private var confirmationUseCase: ConfirmExternalSuggestionUseCase? = null

    @Volatile
    private var initialized = false

    private val confirmationMutex = Mutex()

    // ========================================================================
    // Inicialización y ciclo de vida
    // ========================================================================

    /**
     * Inicializa dependencias, pero nunca concede consentimiento ni activa la
     * observacion por si sola. Solo restaura la cola si ambos opt-ins ya fueron
     * persistidos por una accion explicita del usuario.
     */
    @Synchronized
    fun initialize(engine: ContextEngine, confirmationUseCase: ConfirmExternalSuggestionUseCase) {
        this.confirmationUseCase = confirmationUseCase
        if (initialized) return
        this.engine = engine
        engine.addListener(this)
        initialized = true
        isEnabled = repository.externalConfirmationEnabled && repository.consentGiven
        createNotificationChannel()

        if (isEnabled) {
            val saved = repository.loadQueue()
            if (saved.isNotEmpty()) {
                queue.restore(saved)
                Log.d(TAG, "Cola restaurada: ${saved.size} sugerencias")
            }
        } else {
            queue.clear()
            repository.clearQueue()
        }

        Log.d(TAG, "Controlador de confirmaciones externas inicializado; activo=$isEnabled")
    }

    /** Desactiva el controlador. */
    fun shutdown() {
        isEnabled = false
        engine?.removeListener(this)
        engine = null
        confirmationUseCase = null
        initialized = false
        queue.clear()
        repository.clearQueue()
        Log.d(TAG, "Controlador detenido")
    }

    /** Activa confirmaciones externas solo si el consentimiento ya existe. */
    fun setEnabled(enabled: Boolean): Boolean {
        val effective = enabled && repository.consentGiven
        isEnabled = effective
        repository.externalConfirmationEnabled = effective
        if (!effective) {
            queue.clear()
            repository.clearQueue()
            hideNotification()
            notifyGuardian(null, GuardSuggestionEvent.DISMISSED)
        }
        return effective == enabled
    }

    /** Registra o revoca el opt-in; concederlo no activa la funcion automaticamente. */
    fun setConsentGiven(given: Boolean) {
        repository.consentGiven = given
        if (!given) setEnabled(false)
    }

    fun isEnabled(): Boolean = isEnabled

    // ========================================================================
    // ContextEngineListener
    // ========================================================================

    override fun onConfirmationPending(intent: ContextIntent, confirmationId: String) {
        if (!isEnabled) return
        if (!canShowExternal()) return

        // Seguridad: verificar app de origen usando el paquete real del evento
        // (el antiguo getForegroundPackage siempre devolvía null; ORD-018).
        if (isSecureContext(intent.sourcePackage)) {
            Log.d(TAG, "Contexto seguro, ignorando sugerencia")
            return
        }

        // Seguridad: verificar contenido sensible
        if (isSensitiveContent(intent)) {
            Log.d(TAG, "Contenido sensible potencial, ignorando")
            return
        }

        // Verificar si es un patrón ignorado
        val tokensHash = ExternalConfirmationRepository.normalizeTokensHash(intent.title)
        if (repository.isPatternIgnored(intent.kind, tokensHash, intent.sourcePackage ?: "")) {
            Log.d(TAG, "Patrón ignorado, descartando")
            return
        }

        // Calcular prioridad
        val priority = ExternalSuggestion.calculatePriority(intent.kind, intent.dueAt)

        val suggestion = ExternalSuggestion(
            id = intent.id,
            confirmationId = confirmationId,
            kind = intent.kind,
            title = intent.title,
            dueAt = intent.dueAt,
            source = intent.source,
            priority = priority,
            confidence = intent.confidence,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 300_000L // 5 min
        )

        enqueueAndShow(suggestion)
    }

    override fun onIntentCreated(intent: ContextIntent) {
        // El engine ya confirmó automáticamente — no mostrar como sugerencia externa
        Log.d(TAG, "Intento creado automáticamente, omitiendo confirmación externa: ${intent.id}")
    }

    override fun onIntentDiscarded(intent: ContextIntent, reason: DiscardReason) {
        // Silencio
    }

    // ========================================================================
    // Recepción desde IME (teclado)
    // ========================================================================

    /**
     * Recibe una sugerencia desde el IME para integrar con la cola externa.
     * El IME muestra primero en su barra de candidatos.
     * Si el teclado se cierra sin resolver, se traslada aquí.
     *
     * La ruta IME también aplica las verificaciones de seguridad (paquete de
     * origen y contenido sensible) que se aplicaban solo en la ruta del
     * ContextEngine; sin ellas, una sugerencia de una app sensible podía
     * colarse al cerrar el teclado (ORD-018).
     */
    fun receiveFromIME(suggestion: ExternalSuggestion) {
        if (!canShowExternal()) return
        if (isSecureContext(suggestion.sourcePackage)) {
            Log.d(TAG, "Contexto seguro desde IME, ignorando sugerencia")
            return
        }
        if (ExternalSecureContext.isSensitiveTitle(suggestion.title)) {
            Log.d(TAG, "Contenido sensible potencial desde IME, ignorando")
            return
        }
        enqueueAndShow(suggestion)
    }

    /** Verifica si un ID ya está siendo procesado (dedup IME/overlay). */
    fun isProcessing(id: String): Boolean {
        return queue.contains(id)
    }

    // ========================================================================
    // Acciones del usuario
    // ========================================================================

    /**
     * Persiste la entidad y su recordatorio antes de resolver el motor o
     * retirar la sugerencia. Ante cualquier fallo la cola queda intacta.
     */
    suspend fun addSuggestion(suggestion: ExternalSuggestion): ContextActionConfirmationResult =
        confirmationMutex.withLock {
            val useCase = confirmationUseCase
                ?: return@withLock ContextActionConfirmationResult.Failure(
                    ContextActionFailureStage.NOT_INITIALIZED
                )
            Log.d(TAG, "Confirmando sugerencia de tipo ${suggestion.kind}")
            val result = withContext(Dispatchers.IO) { useCase(suggestion) }

            withContext(Dispatchers.Main.immediate) {
                if (result is ContextActionConfirmationResult.Success) {
                    if (suggestion.confirmationId.isNotEmpty()) {
                        engine?.resolveConfirmation(suggestion.confirmationId, true)
                    }
                    queue.updateState(suggestion.id, ExternalSuggestionState.RESOLVED)
                    queue.remove(suggestion.id)
                    queue.advanceToNext()
                    persistQueue()
                    notifyGuardian(queue.getCurrent(), GuardSuggestionEvent.CONFIRMING)
                    scheduleGuardianReturnToIdle()
                } else {
                    notifyGuardian(suggestion, GuardSuggestionEvent.WAITING_FOR_DECISION)
                }
            }
            result
        }

    /** Abre editor compacto para editar sugerencia. */
    fun editSuggestion(suggestion: ExternalSuggestion, action: ExternalSuggestionAction.Edit) {
        queue.updateState(suggestion.id, ExternalSuggestionState.EDITING)

        val modified = suggestion.copy(
            title = action.newTitle ?: suggestion.title,
            dueAt = action.newDueAt ?: suggestion.dueAt,
            priority = action.newPriority ?: suggestion.priority,
            kind = action.newKind ?: suggestion.kind,
            state = ExternalSuggestionState.PENDING
        )

        queue.remove(suggestion.id)
        queue.enqueue(modified)
        queue.advanceToNext()
        persistQueue()

        notifyGuardian(queue.getCurrent(), GuardSuggestionEvent.WAITING_FOR_DECISION)
    }

    /** Posponer sugerencia. */
    fun postponeSuggestion(suggestion: ExternalSuggestion, duration: PostponeDuration) {
        if (duration == PostponeDuration.CUSTOM) {
            // Necesitamos abrir el selector de fecha/hora (ExternalConfirmationActivity)
            queue.updateState(suggestion.id, ExternalSuggestionState.EDITING)
            notifyGuardian(suggestion, GuardSuggestionEvent.APPROACHING_CARD)
            return
        }

        val newExpiry = duration.resolveMillis()
        val postponed = suggestion.copy(
            expiresAt = newExpiry,
            state = ExternalSuggestionState.POSTPONED
        )

        queue.remove(suggestion.id)
        queue.enqueue(postponed)
        queue.advanceToNext()
        persistQueue()

        notifyGuardian(queue.getCurrent(), GuardSuggestionEvent.RETURNING_TO_IDLE)
    }

    /** Ignorar sugerencia. */
    fun ignoreSuggestion(suggestion: ExternalSuggestion) {
        queue.updateState(suggestion.id, ExternalSuggestionState.IGNORED)
        queue.remove(suggestion.id)
        queue.advanceToNext()
        persistQueue()

        notifyGuardian(queue.getCurrent(), GuardSuggestionEvent.DISMISSED)
        scheduleGuardianReturnToIdle()
    }

    /** No detectar frases parecidas. */
    fun dontDetectSimilar(suggestion: ExternalSuggestion) {
        val tokensHash = ExternalConfirmationRepository.normalizeTokensHash(suggestion.title)
        repository.addIgnoredPattern(
            ExternalConfirmationRepository.IgnoredPattern(
                kind = suggestion.kind,
                tokensHash = tokensHash,
                sourceApp = suggestion.source.name
            )
        )
        ignoreSuggestion(suggestion)
    }

    /** Cambiar solo la fecha. */
    fun changeDate(suggestion: ExternalSuggestion, newDate: Long) {
        editSuggestion(suggestion, ExternalSuggestionAction.Edit(newDueAt = newDate))
    }

    /** Cambiar solo la hora. */
    fun changeTime(suggestion: ExternalSuggestion, newTime: Long) {
        val currentDue = suggestion.dueAt ?: System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = currentDue
        val newDue = calendar.also {
            it.set(java.util.Calendar.HOUR_OF_DAY, java.util.Calendar.getInstance().apply { timeInMillis = newTime }.get(java.util.Calendar.HOUR_OF_DAY))
            it.set(java.util.Calendar.MINUTE, java.util.Calendar.getInstance().apply { timeInMillis = newTime }.get(java.util.Calendar.MINUTE))
        }.timeInMillis
        editSuggestion(suggestion, ExternalSuggestionAction.Edit(newDueAt = newDue))
    }

    /** Convertir tipo de intención. */
    fun convertType(suggestion: ExternalSuggestion, newKind: ContextIntentKind) {
        editSuggestion(suggestion, ExternalSuggestionAction.Edit(newKind = newKind))
    }

    // ========================================================================
    // Lógica interna
    // ========================================================================

    private fun enqueueAndShow(suggestion: ExternalSuggestion) {
        // Dedup: verificar duplicados
        if (queue.contains(suggestion.id)) return

        // Verificar horas silenciosas
        if (isQuietHours()) {
            Log.d(TAG, "Horas silenciosas, encolando sin mostrar")
            queue.enqueue(suggestion)
            persistQueue()
            return
        }

        queue.enqueue(suggestion)

        // Si no hay sugerencia actual visible, mostrar la siguiente
        if (queue.getCurrent() == null) {
            val next = queue.advanceToNext()
            if (next != null) {
                showSuggestion(next)
            }
        }

        persistQueue()
    }

    private fun showSuggestion(suggestion: ExternalSuggestion) {
        // Verificar permiso de superposición
        if (hasOverlayPermission()) {
            // Mostrar vía guardián flotante
            notifyGuardian(suggestion, GuardSuggestionEvent.SUGGESTION_DETECTED)
            notifyGuardian(suggestion, GuardSuggestionEvent.APPROACHING_CARD)
            notifyGuardian(suggestion, GuardSuggestionEvent.POINTING)
            notifyGuardian(suggestion, GuardSuggestionEvent.WAITING_FOR_DECISION)
        } else {
            // Sin permiso: notificación privada
            showPrivateNotification()
        }
    }

    private fun showPrivateNotification() {
        if (!hasOverlayPermission()) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = android.net.Uri.parse("package:${app.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pendingIntent = PendingIntent.getActivity(
                app, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(app, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(app.getString(R.string.app_short_name))
                .setContentText(app.getString(R.string.external_suggestion_no_permission))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun hideNotification() {
        NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID)
    }

    private fun notifyGuardian(suggestion: ExternalSuggestion?, event: GuardSuggestionEvent) {
        guardianSuggestionListener?.onSuggestionChanged(suggestion, event)
    }

    private fun scheduleGuardianReturnToIdle() {
        // El overlay implementará la transición después de CONFIRMING/DISMISSED
        android.os.Handler(app.mainLooper).postDelayed({
            notifyGuardian(queue.getCurrent(), GuardSuggestionEvent.RETURNING_TO_IDLE)
        }, 1600L)
    }

    private fun persistQueue() {
        repository.saveQueue(queue.toList())
    }

    // ========================================================================
    // Verificaciones de seguridad
    // ========================================================================

    /** ¿Podemos mostrar sugerencias externas ahora? */
    private fun canShowExternal(): Boolean {
        if (!isEnabled) return false
        if (!repository.consentGiven) return false
        return true
    }

    /**
     * Verifica si el paquete de origen está en la lista de exclusión o es
     * sensible (banca, autenticadores, gestores de contraseñas, apps
     * médicas). Usa el `sourcePackage` del evento/IME, que sí está
     * disponible, en lugar del antiguo `getForegroundPackage()` que siempre
     * devolvía null (ORD-018). `null` (desconocido) no bloquea: el pipeline
     * contextual ya filtró el contenido antes de producir la sugerencia.
     */
    private fun isSecureContext(packageName: String?): Boolean =
        ExternalSecureContext.isSecurePackage(packageName, SECURE_PACKAGES, repository.excludedApps)

    /** Verifica si el intent contiene contenido sensible. */
    private fun isSensitiveContent(intent: ContextIntent): Boolean =
        ExternalSecureContext.isSensitiveTitle(intent.title)

    /** ¿Tenemos permiso de superposición? (minSdk 26 ⇒ M siempre disponible) */
    private fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(app)

    /** ¿Estamos en horas silenciosas? */
    private fun isQuietHours(): Boolean {
        val now = java.util.Calendar.getInstance()
        val currentMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        val start = repository.quietHoursStart
        val end = repository.quietHoursEnd
        return if (start <= end) {
            currentMinutes in start until end
        } else {
            currentMinutes >= start || currentMinutes < end
        }
    }

    // ========================================================================
    // Notificaciones
    // ========================================================================

    // minSdk 26 ⇒ los canales de notificación existen siempre (O+).
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            app.getString(R.string.external_suggestion_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = app.getString(R.string.external_suggestion_channel_description)
            setShowBadge(false)
        }
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    /** Configura un Intent para abrir la edición de una sugerencia. */
    fun createEditIntent(suggestion: ExternalSuggestion): Intent {
        return Intent(app, com.ordia.app.context.external.ExternalConfirmationActivity::class.java).apply {
            putExtra(EXTRA_SUGGESTION_ID, suggestion.id)
            putExtra(EXTRA_KIND, suggestion.kind.name)
            putExtra(EXTRA_TITLE, suggestion.title)
            putExtra(EXTRA_DUE_AT, suggestion.dueAt ?: -1L)
            putExtra(EXTRA_CONFIRMATION_ID, suggestion.confirmationId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Crea una PendingIntent para las acciones de la notificación. */
    fun createActionPendingIntent(action: String, suggestionId: String): PendingIntent {
        val intent = Intent(app, com.ordia.app.context.external.ExternalConfirmationReceiver::class.java).apply {
            putExtra(EXTRA_ACTION, action)
            putExtra(EXTRA_SUGGESTION_ID, suggestionId)
        }
        return PendingIntent.getBroadcast(
            app, suggestionId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Obtiene el MessageDigest para hash */
    private fun getDigest(): MessageDigest = MessageDigest.getInstance("SHA-256")

    fun processPendingQueue() {
        queue.removeExpired()
        if (queue.getCurrent() == null) {
            val next = queue.advanceToNext()
            if (next != null) {
                showSuggestion(next)
            }
        }
        persistQueue()
    }

}
