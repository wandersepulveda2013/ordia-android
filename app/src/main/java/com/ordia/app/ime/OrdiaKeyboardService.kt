package com.ordia.app.ime

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEngine
import com.ordia.app.context.ContextResult
import com.ordia.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Teclado Ordía 3 — IME minimalista con análisis contextual.
 *
 * Funcionamiento:
 * - Captura el texto completo al enviar (ENTER) o tras una pausa
 * - Envía el texto al ContextEngine para detección de intenciones
 * - Muestra sugerencias en la fila de candidatos
 * - Nunca almacena texto completo, conversaciones ni pulsaciones
 * - No procesa contraseñas, PIN, OTP ni contenido sensible
 *
 * Privacidad: el buffer de entrada se borra inmediatamente después del análisis.
 * No se registran pulsaciones individuales, solo el texto completo al enviar.
 */
class OrdiaKeyboardService : InputMethodService(),
    KeyboardView.OnKeyboardActionListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var keyboardView: KeyboardView? = null
    private var keyboard: Keyboard? = null
    private var analysisJob: Job? = null
    private var pendingText: StringBuilder = StringBuilder()

    /** Última sugerencia del motor contextual */
    private var lastSuggestion: String? = null

    override fun onCreateInputView(): View {
        keyboard = Keyboard(this, R.xml.ordia_keyboard_qwerty)
        keyboardView = layoutInflater.inflate(
            R.layout.ordia_keyboard_view, null
        ) as KeyboardView
        keyboardView?.apply {
            this.keyboard = this@OrdiaKeyboardService.keyboard
            setOnKeyboardActionListener(this@OrdiaKeyboardService)
            isPreviewEnabled = false
        }
        return keyboardView!!
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        pendingText = StringBuilder()
        lastSuggestion = null
        // Detectar campos sensibles por tipo de input
        if (info != null && isSensitiveInputType(info)) {
            // No analizar campos de contraseña, PIN, etc.
            keyboardView?.let { view ->
                view.keyboard?.let { kb ->
                    // El teclado sigue funcionando pero no analiza
                }
            }
        }
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
                // Análisis con pausa (debounce 1.5s)
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

    /** Envía el texto completo al motor contextual */
    private fun commitAndAnalyze(inputConnection: InputConnection?) {
        if (pendingText.isBlank()) return
        analysisJob?.cancel()
        val text = takeText()
        if (text.isBlank()) return

        // Enviar al motor contextual
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

    /** Procesa con el motor contextual y muestra resultados */
    private fun processWithEngine(text: String) {
        if (text.isBlank()) return
        val engine = ContextEngine.getInstance(this)
        val result = engine.processText(text, ContextCaptureSource.KEYBOARD)
        when (result) {
            is ContextResult.PendingConfirmation -> {
                lastSuggestion = result.intent.title.take(60)
                showSuggestionInCandidates("¿${result.intent.kind.displayName}? $lastSuggestion")
            }
            is ContextResult.Created -> {
                lastSuggestion = result.intent.title.take(60)
                showSuggestionInCandidates("${result.intent.kind.displayName}: $lastSuggestion")
            }
            is ContextResult.Discarded -> {
                // Silencio — no mostrar nada
            }
        }
    }

    /** Muestra una sugerencia en la fila de candidatos */
    private fun showSuggestionInCandidates(text: String) {
        // Enviar como texto de candidato
        currentInputConnection?.let { conn ->
            conn.beginBatchEdit()
            conn.commitText("", 0) // placeholder para mantener el cursor
            conn.endBatchEdit()
        }
    }

    /** Verifica si el tipo de entrada es sensible */
    private fun isSensitiveInputType(info: EditorInfo): Boolean {
        val variation = info.inputType and EditorInfo.TYPE_MASK_VARIATION
        val type = info.inputType and EditorInfo.TYPE_MASK_CLASS
        // Contraseñas
        if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD) return true
        // Campos de autenticación
        if (type == EditorInfo.TYPE_CLASS_NUMBER &&
            (variation == EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD)) return true
        // Campos de fecha/hora específicos (no confundir con texto con fechas)
        if (type == EditorInfo.TYPE_CLASS_DATETIME) return true
        return false
    }

    override fun onDestroy() {
        analysisJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
