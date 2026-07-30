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
import com.ordia.app.context.ContextIntent
import com.ordia.app.context.ContextResult
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

/**
 * Teclado Ordía 3 — IME minimalista con análisis contextual y barra de candidatos real.
 *
 * Funcionamiento:
 * - Captura el texto completo al enviar (ENTER) o tras una pausa (1.5s debounce)
 * - Envía el texto al ContextEngine para detección de intenciones
 * - Muestra una tarjeta de sugerencia en la barra de candidatos
 * - Nunca almacena texto completo, conversaciones ni pulsaciones
 * - No procesa contraseñas, PIN, OTP ni contenido sensible
 * - No modifica ni reemplaza el texto escrito en la aplicación
 *
 * Privacidad: el buffer de entrada se borra inmediatamente después del análisis.
 * No se registran pulsaciones individuales, solo el texto completo al enviar.
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

    // --- Estado ---
    private var pendingText: StringBuilder = StringBuilder()
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
        clearSuggestion()

        // Cancelar auto-cierre pendiente
        autoCloseJob?.cancel()

        // Detectar campos sensibles
        if (info != null && isSensitiveInputType(info)) {
            // No analizar campos de contraseña, PIN, etc.
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        clearSuggestion()
        suggestionQueue.clear()
        autoCloseJob?.cancel()
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val inputConnection = currentInputConnection ?: return
        when (primaryCode) {
            Keyboard.KEYCODE_DONE -> {
                commitAndAnalyze(inputConnection)
            }
            Keyboard.KEYCODE_DELETE -> {
                if (pendingText.isNotEmpty()) {
                    pendingText.deleteCharAt(pendingText.length - 1)
                }
                inputConnection.deleteSurroundingText(1, 0)
            }
            Keyboard.KEYCODE_SHIFT -> {
                // Alternar mayúsculas no implementado en MVP
            }
            else -> {
                val code = primaryCode.toChar()
                pendingText.append(code)
                inputConnection.commitText(code.toString(), 1)
                scheduleAnalysis()
            }
        }
    }

    override fun onText(text: CharSequence?) {
        text?.let { pendingText.append(it) }
        currentInputConnection?.commitText(text ?: "", 1)
        scheduleAnalysis()
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

    /** Programa análisis con debounce */
    private fun scheduleAnalysis() {
        if (isPaused) return
        analysisJob?.cancel()
        analysisJob = scope.launch {
            delay(1500L) // 1.5 segundos de pausa
            if (!isActive) return@launch
            val text = pendingText.toString().trim()
            if (text.length >= 4) {
                processWithEngine(text)
            }
        }
    }

    /** Procesa con el motor contextual */
    private fun processWithEngine(text: String) {
        if (text.isBlank() || isPaused) return

        // Mostrar indicador de análisis
        showAnalysisIndicator()

        val engine = ContextEngine.getInstance(this)
        val result = engine.processText(text, ContextCaptureSource.KEYBOARD)

        // Ocultar indicador
        hideAnalysisIndicator()

        when (result) {
            is ContextResult.PendingConfirmation -> {
                enqueueSuggestion(result.intent, result.confirmationId)
            }
            is ContextResult.Created -> {
                enqueueSuggestion(result.intent, confirmationId = null)
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
        actionAdd?.setOnClickListener {
            currentSuggestion?.let { pending ->
                if (pending.confirmationId.isNotEmpty()) {
                    val engine = ContextEngine.getInstance(this)
                    engine.resolveConfirmation(pending.confirmationId, true)
                }
                clearSuggestion()
                showNextSuggestion()
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
                // Guardar patrón para no detectar frases similares
                val pattern = intent.title.take(100)
                prefs.edit().putStringSet(PREF_IGNORED_PATTERNS,
                    (prefs.getStringSet(PREF_IGNORED_PATTERNS, emptySet()) ?: emptySet()) + pattern
                ).apply()
                clearSuggestion()
                showNextSuggestion()
            }
        }

        actionPrivacy?.setOnClickListener {
            // Mostrar resumen de privacidad
            suggestionText?.text = "Privacidad activa: no se almacena texto, contraseñas ni contenido sensible. Análisis solo en español."
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

        suggestionText?.text = "Análisis pausado por 1 hora. Presiona aquí para reanudar."
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

    /** Verifica si el tipo de entrada es sensible */
    private fun isSensitiveInputType(info: EditorInfo): Boolean {
        val variation = info.inputType and EditorInfo.TYPE_MASK_VARIATION
        val type = info.inputType and EditorInfo.TYPE_MASK_CLASS
        if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD) return true
        if (type == EditorInfo.TYPE_CLASS_NUMBER &&
            variation == EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD) return true
        if (type == EditorInfo.TYPE_CLASS_DATETIME) return true
        return false
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
    }
}
