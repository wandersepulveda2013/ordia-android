package com.ordia.app.ime

import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.KeyEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ordia.app.R
import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEngine
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntent
import com.ordia.app.context.ContextPrivacyFilter
import com.ordia.app.context.ContextResult
import com.ordia.app.context.external.ContextActionConfirmationResult
import com.ordia.app.context.external.ExternalConfirmationController
import com.ordia.app.context.external.ExternalSuggestion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Contenedor de una sugerencia pendiente con su ID de confirmación.
 * Se usa para almacenar tanto el intent como el confirmationId
 * requerido por [ContextEngine.resolveConfirmation].
 */
private class PendingSuggestion(
    val intent: ContextIntent,
    val confirmationId: String
)

private fun PendingSuggestion.toExternalSuggestion(): ExternalSuggestion = ExternalSuggestion(
    id = intent.id,
    confirmationId = confirmationId,
    kind = intent.kind,
    title = intent.title,
    dueAt = intent.dueAt,
    source = intent.source,
    sourcePackage = intent.sourcePackage,
    priority = ExternalSuggestion.calculatePriority(intent.kind, intent.dueAt),
    confidence = intent.confidence
)

/**
 * Teclado Ordía 3 — IME minimalista con análisis contextual y barra de candidatos real.
 *
 * Funcionamiento:
 * - Captura el texto completo al enviar (ENTER) o tras una pausa (1.5s debounce)
 * - Envía el texto al ContextEngine para detección de intenciones
 * - Muestra una tarjeta de sugerencia en la barra de candidatos
 * - Nunca almacena texto completo, conversaciones ni pulsaciones
 * - No procesa contraseñas, PIN, OTP ni contenido sensible: los campos con
 *   variación password/webPassword/numberPassword y las apps bloqueadas por
 *   [com.ordia.app.context.ContextPrivacyFilter] se ignoran por completo
 *   (el texto se escribe en la app, pero jamás se captura ni se analiza)
 * - La tecla "ABC" cambia al siguiente IME del sistema; "↵" envía la acción
 *   del editor (Done/Send/Search/Next...) o salto de línea en multilínea
 * - No modifica ni reemplaza el texto escrito en la aplicación
 *
 * Privacidad: el buffer de entrada se borra inmediatamente después del análisis.
 * No se registran pulsaciones individuales, solo el texto completo al enviar.
 * Los patrones "No detectar" se persisten únicamente como hash SHA-256.
 */
class OrdiaKeyboardService : InputMethodService(),
    KeyboardView.OnKeyboardActionListener {

    // --- Corrutinas ---
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var analysisJob: Job? = null
    private var autoCloseJob: Job? = null

    // --- Vistas ---
    private var keyboardView: KeyboardView? = null
    private var keyboard: Keyboard? = null
    private var candidateContainer: FrameLayout? = null
    private var suggestionCard: LinearLayout? = null
    private var suggestionText: TextView? = null
    private var analysisIndicator: LinearLayout? = null
    private var suggestionActions: LinearLayout? = null
    private var actionAdd: Button? = null
    private var actionEdit: Button? = null
    private var actionRemindLater: Button? = null
    private var actionIgnore: ImageButton? = null
    private var dontDetectText: TextView? = null
    private var actionPrivacy: ImageButton? = null
    private var actionGuardian: ImageButton? = null
    private var actionPause: ImageButton? = null
    private var analysisPermissionText: TextView? = null
    private var analysisPermissionButton: Button? = null

    // --- Estado ---
    private var pendingText: StringBuilder = StringBuilder()
    private var sensitiveField = false
    private var hardBlockedField = false
    private var currentSuggestion: PendingSuggestion? = null
    private val suggestionQueue = ArrayDeque<PendingSuggestion>(3)
    private var isPaused = false
    private var pauseUntilMs = 0L
    private var currentEditorInfo: EditorInfo? = null
    private var autoCloseDelayMs = 15_000L

    // --- Preferencias ---
    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("ordia_keyboard", android.content.Context.MODE_PRIVATE)
    }

    // ========================================================================
    // Ciclo de vida del IME
    // ========================================================================

    override fun onCreateInputView(): View {
        keyboard = Keyboard(this, R.xml.ordia_keyboard_qwerty)
        val root = layoutInflater.inflate(R.layout.ordia_keyboard_view, null) as LinearLayout

        keyboardView = root.findViewById(R.id.keyboard)
        keyboardView?.apply {
            this.keyboard = this@OrdiaKeyboardService.keyboard
            setOnKeyboardActionListener(this@OrdiaKeyboardService)
            isPreviewEnabled = false
        }

        // Inicializar vistas de la barra de candidatos
        candidateContainer = root.findViewById(R.id.candidate_bar_container)
        suggestionCard = root.findViewById(R.id.suggestion_card)
        suggestionText = root.findViewById(R.id.suggestion_text)
        analysisIndicator = root.findViewById(R.id.analysis_indicator)
        suggestionActions = root.findViewById(R.id.suggestion_actions)
        actionAdd = root.findViewById(R.id.action_add)
        actionEdit = root.findViewById(R.id.action_edit)
        actionRemindLater = root.findViewById(R.id.action_remind_later)
        actionIgnore = root.findViewById(R.id.action_ignore)
        dontDetectText = root.findViewById(R.id.dont_detect_text)
        actionPrivacy = root.findViewById(R.id.action_privacy)
        actionGuardian = root.findViewById(R.id.action_guardian)
        actionPause = root.findViewById(R.id.action_pause)
        analysisPermissionText = root.findViewById(R.id.analysis_permission_text)
        analysisPermissionButton = root.findViewById(R.id.analysis_permission_button)

        // Configurar acciones
        setupActions()

        // Cargar preferencias
        isPaused = prefs.getBoolean(PREF_PAUSED, false)
        pauseUntilMs = prefs.getLong(PREF_PAUSE_UNTIL, 0L)
        if (isPaused && System.currentTimeMillis() > pauseUntilMs) {
            isPaused = false
            prefs.edit().putBoolean(PREF_PAUSED, false).apply()
        }

        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentEditorInfo = info
        pendingText = StringBuilder()
        analysisJob?.cancel()
        clearSuggestion()

        // Cancelar auto-cierre pendiente
        autoCloseJob?.cancel()

        // Detectar campos sensibles: contraseñas/PIN/OTP (variación password,
        // numberPassword, date/time) o apps bloqueadas por privacidad.
        val inputType = info?.inputType ?: 0
        val packageName = info?.packageName
        hardBlockedField = KeyboardPrivacyGuard.shouldIgnore(
            inputType = inputType,
            packageName = packageName,
            fieldHint = info?.hintText?.toString(),
            privateImeOptions = info?.privateImeOptions
        )
        sensitiveField = !KeyboardPrivacyGuard.isAnalysisAllowed(
            inputType = inputType,
            packageName = packageName,
            allowedPackages = allowedPackages(),
            fieldHint = info?.hintText?.toString(),
            privateImeOptions = info?.privateImeOptions
        )
        updateAnalysisPermissionUi()

        if (sensitiveField) {
            // No capturar ni analizar nada mientras el campo sea sensible.
            // Las sugerencias previas no deben mostrarse sobre una contraseña.
            suggestionQueue.clear()
            currentSuggestion = null
            candidateContainer?.visibility = View.GONE
            suggestionActions?.visibility = View.GONE
            suggestionText?.text = ""
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        pendingText = StringBuilder()
        analysisJob?.cancel()
        // Transferir sugerencias no resueltas al controlador externo
        val controller = ExternalConfirmationController.getInstance(this)
        val unresolved = suggestionQueue.toList()
        suggestionQueue.clear()

        if (currentSuggestion != null) {
            val pending = currentSuggestion!!
            controller.receiveFromIME(pending.toExternalSuggestion())
        }

        unresolved.forEach { pending ->
            controller.receiveFromIME(pending.toExternalSuggestion())
        }

        clearSuggestion()
        autoCloseJob?.cancel()
        super.onFinishInputView(finishingInput)
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val inputConnection = currentInputConnection ?: return
        when (primaryCode) {
            Keyboard.KEYCODE_DONE -> {
                // Tecla "↵": analizar el texto capturado y enviar la acción del editor.
                commitAndAnalyze(inputConnection)
                sendEditorAction(inputConnection)
            }
            Keyboard.KEYCODE_DELETE -> {
                if (!sensitiveField && pendingText.isNotEmpty()) {
                    pendingText.deleteCharAt(pendingText.length - 1)
                }
                inputConnection.deleteSurroundingText(1, 0)
            }
            Keyboard.KEYCODE_SHIFT -> {
                // Alternar mayúsculas no implementado en MVP
            }
            Keyboard.KEYCODE_MODE_CHANGE -> {
                // Tecla "ABC": cambiar al siguiente IME del sistema.
                // switchToNextInputMethod requiere API 28; en 26-27 se usa el
                // selector clásico de IME.
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    switchToNextInputMethod(false)
                } else {
                    switchInputMethod("")
                }
            }
            else -> {
                if (primaryCode < 0) return // Códigos de control no manejados
                val code = primaryCode.toChar()
                // El texto siempre se escribe en la app; en campos sensibles
                // jamás se captura para análisis.
                inputConnection.commitText(code.toString(), 1)
                if (!sensitiveField) {
                    appendPendingText(code)
                    scheduleAnalysis()
                }
            }
        }
    }

    override fun onText(text: CharSequence?) {
        currentInputConnection?.commitText(text ?: "", 1)
        if (!sensitiveField) {
            text?.let { pendingText.append(it) }
            if (pendingText.length > KeyboardPrivacyGuard.MAX_BUFFER_CHARS) {
                pendingText.delete(0, pendingText.length - KeyboardPrivacyGuard.MAX_BUFFER_CHARS)
            }
            scheduleAnalysis()
        }
    }

    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun swipeRight() { commitAndAnalyze(currentInputConnection) }
    override fun swipeLeft() {}
    override fun swipeDown() { requestHideSelf(0) }
    override fun swipeUp() {}

    /** Espacio como pulsación normal */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            onKey(' '.code, null)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ========================================================================
    // Análisis contextual
    // ========================================================================

    /** Envía el texto completo al motor contextual */
    private fun commitAndAnalyze(inputConnection: InputConnection?) {
        if (sensitiveField) return
        if (pendingText.isBlank()) return
        if (isPaused) return
        analysisJob?.cancel()
        val text = takeText()
        if (text.isBlank()) return

        scope.launch {
            processWithEngine(text)
        }
    }

    /** Toma el texto y limpia el buffer inmediatamente */
    private fun takeText(): String {
        val text = pendingText.toString().trim()
        pendingText = StringBuilder()
        return text
    }

    /** Añade un carácter al buffer con tope de tamaño */
    private fun appendPendingText(char: Char) {
        pendingText.append(char)
        if (pendingText.length > KeyboardPrivacyGuard.MAX_BUFFER_CHARS) {
            pendingText.delete(0, pendingText.length - KeyboardPrivacyGuard.MAX_BUFFER_CHARS)
        }
    }

    /** Programa análisis con debounce */
    private fun scheduleAnalysis() {
        if (isPaused || sensitiveField) return
        analysisJob?.cancel()
        analysisJob = scope.launch {
            delay(1500L) // 1.5 segundos de pausa
            if (!isActive) return@launch
            val text = takeText()
            if (text.length >= 4) {
                processWithEngine(text)
            }
        }
    }

    /** Procesa con el motor contextual (suspend: análisis fuera del hilo main) */
    private suspend fun processWithEngine(text: String) {
        if (text.isBlank() || isPaused || sensitiveField) return
        // "No detectar": descartar frases que el usuario ha marcado previamente.
        if (isIgnoredText(text)) return

        val editor = currentEditorInfo ?: return
        val event = ContextEvent(
            source = ContextCaptureSource.KEYBOARD,
            rawText = text,
            timestampMs = System.currentTimeMillis(),
            sourcePackage = editor.packageName,
            sourceLabel = applicationLabel(editor.packageName),
            metadata = mapOf(
                "inputClass" to KeyboardPrivacyGuard.inputClassName(editor.inputType),
                "inputType" to editor.inputType.toString(),
                "fieldHint" to editor.hintText?.toString().orEmpty()
            )
        )
        if (ContextPrivacyFilter.shouldBlock(event)) return

        // Mostrar indicador de análisis
        showAnalysisIndicator()

        val engine = ContextEngine.getInstance(this)
        val result = engine.processEventAsync(event)

        // Ocultar indicador (se reanuda en el hilo main)
        hideAnalysisIndicator()

        when (result) {
            is ContextResult.PendingConfirmation -> {
                if (!isIgnoredText(result.intent.title)) {
                    enqueueSuggestion(result.intent, result.confirmationId)
                }
            }
            is ContextResult.Created -> {
                if (!isIgnoredText(result.intent.title)) {
                    enqueueSuggestion(result.intent, confirmationId = null)
                }
            }
            is ContextResult.Discarded -> {
                // Silencio
            }
        }
    }

    // ========================================================================
    // Gestión de sugerencias
    // ========================================================================

    /** Añade una sugerencia a la cola y muestra si no hay ninguna activa */
    private fun enqueueSuggestion(intent: ContextIntent, confirmationId: String? = null) {
        // Dedup con el controlador externo
        val controller = ExternalConfirmationController.getInstance(this)
        if (controller.isProcessing(intent.id)) {
            // El controlador externo ya tiene esta sugerencia
            return
        }

        if (suggestionQueue.size >= 3) suggestionQueue.removeFirst()
        suggestionQueue.addLast(PendingSuggestion(intent, confirmationId ?: ""))

        if (currentSuggestion == null) {
            showNextSuggestion()
        }
    }

    /** Muestra la siguiente sugerencia de la cola */
    private fun showNextSuggestion() {
        if (suggestionQueue.isEmpty()) {
            clearSuggestion()
            return
        }
        val pending = suggestionQueue.removeFirst()
        currentSuggestion = pending

        val kindName = pending.intent.kind.displayName
        val title = pending.intent.title.take(80)
        suggestionText?.text = getString(R.string.keyboard_suggestion_pattern_add, "$kindName: $title")

        candidateContainer?.visibility = View.VISIBLE
        suggestionActions?.visibility = View.VISIBLE
        animateCardIn()

        // Auto-cierre
        startAutoCloseTimer()
    }

    /** Limpia la sugerencia actual */
    private fun clearSuggestion() {
        currentSuggestion = null
        if (candidateContainer?.visibility == View.VISIBLE) {
            animateCardOut {
                candidateContainer?.visibility = View.GONE
                suggestionActions?.visibility = View.GONE
                suggestionText?.text = ""
            }
        }
        autoCloseJob?.cancel()
    }

    /** Muestra el indicador de análisis */
    private fun showAnalysisIndicator() {
        analysisIndicator?.visibility = View.VISIBLE
    }

    /** Oculta el indicador de análisis */
    private fun hideAnalysisIndicator() {
        analysisIndicator?.visibility = View.GONE
    }

    // ========================================================================
    // Configuración de acciones
    // ========================================================================

    private fun setupActions() {
        analysisPermissionButton?.setOnClickListener { toggleCurrentPackagePermission() }

        actionAdd?.setOnClickListener {
            currentSuggestion?.let { pending ->
                actionAdd?.isEnabled = false
                suggestionText?.setText(R.string.context_action_saving)
                scope.launch {
                    val result = ExternalConfirmationController.getInstance(this@OrdiaKeyboardService)
                        .addSuggestion(pending.toExternalSuggestion())
                    actionAdd?.isEnabled = true
                    if (result is ContextActionConfirmationResult.Success) {
                        if (currentSuggestion?.intent?.id == pending.intent.id) {
                            clearSuggestion()
                            showNextSuggestion()
                        }
                    } else if (currentSuggestion?.intent?.id == pending.intent.id) {
                        suggestionText?.setText(R.string.context_action_save_failed)
                    }
                }
            }
        }

        actionEdit?.setOnClickListener {
            currentSuggestion?.let { pending ->
                // Abrir editor externo (no abre Ordía completa)
                showExternalEditor(pending.intent)
            }
        }

        actionRemindLater?.setOnClickListener {
            // Re-encolar para recordar después
            currentSuggestion?.let { pending ->
                suggestionQueue.addLast(pending)
            }
            clearSuggestion()
            showNextSuggestion()
        }

        actionIgnore?.setOnClickListener {
            clearSuggestion()
            showNextSuggestion()
        }

        dontDetectText?.setOnClickListener {
            currentSuggestion?.let { pending ->
                val intent = pending.intent
                // Guardar patrón para no detectar frases similares.
                // Solo se persiste el hash SHA-256 normalizado, nunca el texto en claro.
                val patternHash = KeyboardPrivacyGuard.sha256Hex(
                    KeyboardPrivacyGuard.normalizeTokens(intent.title.take(100))
                )
                prefs.edit().putStringSet(PREF_IGNORED_PATTERNS,
                    (prefs.getStringSet(PREF_IGNORED_PATTERNS, emptySet()) ?: emptySet()) + patternHash
                ).apply()
                clearSuggestion()
                showNextSuggestion()
            }
        }

        actionPrivacy?.setOnClickListener {
            // Mostrar resumen de privacidad
            suggestionText?.text = getString(R.string.keyboard_privacy_active)
            suggestionActions?.visibility = View.GONE
            scope.launch {
                delay(3000L)
                if (currentSuggestion != null) {
                    suggestionActions?.visibility = View.VISIBLE
                    updateSuggestionText()
                }
            }
        }

        actionGuardian?.setOnClickListener {
            // Abrir guardián flotante
            val intent = android.content.Intent(this, com.ordia.app.overlay.GuardianOverlayService::class.java)
            intent.action = "com.ordia.app.action.SHOW_GUARDIAN"
            ContextCompat.startForegroundService(this, intent)
        }

        actionPause?.setOnClickListener {
            pauseForOneHour()
        }
    }

    /** Pausa el análisis por una hora */
    private fun pauseForOneHour() {
        isPaused = true
        pauseUntilMs = System.currentTimeMillis() + 3600_000L
        prefs.edit()
            .putBoolean(PREF_PAUSED, true)
            .putLong(PREF_PAUSE_UNTIL, pauseUntilMs)
            .apply()

        suggestionText?.text = getString(R.string.keyboard_paused)
        suggestionActions?.visibility = View.GONE

        scope.launch {
            delay(3600_000L)
            isPaused = false
            prefs.edit().putBoolean(PREF_PAUSED, false).apply()
            clearSuggestion()
        }
    }

    /** Actualiza el texto de la sugerencia actual */
    private fun updateSuggestionText() {
        currentSuggestion?.let { pending ->
            val kindName = pending.intent.kind.displayName
            val title = pending.intent.title.take(80)
            suggestionText?.text = getString(R.string.keyboard_suggestion_pattern_add, "$kindName: $title")
        }
    }

    /** Muestra el editor externo (fuera de Ordía) */
    private fun showExternalEditor(intent: ContextIntent) {
        // Enviar a través del ExternalConfirmationController
        val externalIntent = android.content.Intent()
        externalIntent.action = "com.ordia.app.action.EDIT_SUGGESTION"
        externalIntent.putExtra("intent_id", intent.id)
        externalIntent.putExtra("kind", intent.kind.name)
        externalIntent.putExtra("title", intent.title)
        externalIntent.putExtra("due_at", intent.dueAt ?: 0L)
        externalIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(externalIntent)
    }

    // ========================================================================
    // Animaciones
    // ========================================================================

    private var cardAnimating = false

    private fun animateCardIn() {
        if (cardAnimating) return
        cardAnimating = true
        candidateContainer?.let { container ->
            val slideIn = AnimationUtils.loadAnimation(this, R.anim.slide_in_top)
            slideIn.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}
                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    cardAnimating = false
                }
            })
            container.startAnimation(slideIn)
        }
    }

    private fun animateCardOut(onFinished: () -> Unit) {
        if (cardAnimating) return
        cardAnimating = true
        candidateContainer?.let { container ->
            val slideOut = AnimationUtils.loadAnimation(this, R.anim.slide_out_top)
            slideOut.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}
                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    cardAnimating = false
                    onFinished()
                }
            })
            container.startAnimation(slideOut)
        }
    }

    /** Inicia temporizador de auto-cierre */
    private fun startAutoCloseTimer() {
        autoCloseJob?.cancel()
        autoCloseJob = scope.launch {
            delay(autoCloseDelayMs)
            if (!isActive) return@launch
            clearSuggestion()
            showNextSuggestion()
        }
    }

    // ========================================================================
    // Utilidades
    // ========================================================================

    /** Envía la acción del editor o un salto de línea en campos multilínea */
    private fun sendEditorAction(inputConnection: InputConnection?) {
        // sendDefaultEditorAction(true) ejecuta la acción asociada a "Enter"
        // (Done/Next/Send/Search/Go) si existe y no está desactivada por el editor.
        if (!sendDefaultEditorAction(true)) {
            // Sin acción definida: campo multilínea o libre → salto de línea.
            inputConnection?.commitText("\n", 1)
        }
    }

    /** Verifica si un texto coincide con un patrón "No detectar" (por hash) */
    private fun isIgnoredText(text: String): Boolean {
        val ignored = prefs.getStringSet(PREF_IGNORED_PATTERNS, emptySet()) ?: emptySet()
        if (ignored.isEmpty()) return false
        return KeyboardPrivacyGuard.sha256Hex(KeyboardPrivacyGuard.normalizeTokens(text)) in ignored
    }

    private fun allowedPackages(): Set<String> =
        prefs.getStringSet(PREF_ALLOWED_PACKAGES, emptySet()).orEmpty().toSet()

    private fun toggleCurrentPackagePermission() {
        val editor = currentEditorInfo ?: return
        val packageName = editor.packageName ?: return
        if (hardBlockedField) return
        val next = allowedPackages().toMutableSet()
        if (packageName in next) next.remove(packageName) else next.add(packageName)
        prefs.edit().putStringSet(PREF_ALLOWED_PACKAGES, next).apply()
        sensitiveField = !KeyboardPrivacyGuard.isAnalysisAllowed(
            inputType = editor.inputType,
            packageName = packageName,
            allowedPackages = next,
            fieldHint = editor.hintText?.toString(),
            privateImeOptions = editor.privateImeOptions
        )
        if (sensitiveField) {
            pendingText = StringBuilder()
            analysisJob?.cancel()
            suggestionQueue.clear()
            clearSuggestion()
        }
        updateAnalysisPermissionUi()
    }

    private fun updateAnalysisPermissionUi() {
        val editor = currentEditorInfo
        val packageName = editor?.packageName
        val label = applicationLabel(packageName)
        when {
            hardBlockedField -> {
                analysisPermissionText?.setText(R.string.keyboard_analysis_blocked_field)
                analysisPermissionButton?.visibility = View.GONE
            }
            packageName.isNullOrBlank() -> {
                analysisPermissionText?.setText(R.string.keyboard_analysis_missing_app)
                analysisPermissionButton?.visibility = View.GONE
            }
            packageName in allowedPackages() -> {
                analysisPermissionText?.text = getString(R.string.keyboard_analysis_on_app, label)
                analysisPermissionButton?.setText(R.string.keyboard_analysis_revoke)
                analysisPermissionButton?.visibility = View.VISIBLE
            }
            else -> {
                analysisPermissionText?.text = getString(R.string.keyboard_analysis_off_app, label)
                analysisPermissionButton?.setText(R.string.keyboard_analysis_allow)
                analysisPermissionButton?.visibility = View.VISIBLE
            }
        }
    }

    private fun applicationLabel(packageName: String?): String {
        if (packageName.isNullOrBlank()) return getString(R.string.app_name)
        return runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
    }

    override fun onDestroy() {
        analysisJob?.cancel()
        autoCloseJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val PREF_PAUSED = "keyboard_paused"
        private const val PREF_PAUSE_UNTIL = "keyboard_pause_until"
        private const val PREF_IGNORED_PATTERNS = "keyboard_ignored_patterns"
        private const val PREF_ALLOWED_PACKAGES = "keyboard_allowed_packages"
    }
}
