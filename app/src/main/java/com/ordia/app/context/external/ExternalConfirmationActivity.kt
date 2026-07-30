package com.ordia.app.context.external

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.ordia.app.R

/**
 * Actividad compacta y transparente para editar una sugerencia externa.
 *
 * Se abre solo cuando la edición en la tarjeta no es suficiente.
 * Es temática (no abre toda Ordía) y se cierra al confirmar o cancelar.
 *
 * NO almacena texto original.
 * NO muestra contenido sensible.
 */
class ExternalConfirmationActivity : Activity() {

    companion object {
        private const val TAG = "ExtConfirmActivity"

        const val RESULT_EDITED = Activity.RESULT_FIRST_USER + 1
    }

    // ========================================================================
    // Ciclo de vida
    // ========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configurar ventana translúcida
        window.setFlags(
            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND
        )
        window.setDimAmount(0.4f)
        setFinishOnTouchOutside(true)

        setContentView(R.layout.ordia_external_confirmation)

        val suggestionId = intent?.getStringExtra(ExternalConfirmationController.EXTRA_SUGGESTION_ID)
        val kindName = intent?.getStringExtra(ExternalConfirmationController.EXTRA_KIND)
        val title = intent?.getStringExtra(ExternalConfirmationController.EXTRA_TITLE)
        val dueAt = intent?.getLongExtra(ExternalConfirmationController.EXTRA_DUE_AT, -1L) ?: -1L

        if (suggestionId == null) {
            finish()
            return
        }

        // Mostrar datos estructurados (sin texto original)
        findViewById<TextView>(R.id.confirmation_title)?.text = title ?: "Sin título"
        findViewById<TextView>(R.id.confirmation_kind)?.text = kindName ?: "Desconocido"
        if (dueAt > 0) {
            val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(dueAt))
            findViewById<TextView>(R.id.confirmation_date)?.text = dateStr
        }

        // Campo de edición (solo título, no texto original)
        val titleInput = findViewById<EditText>(R.id.confirmation_input_title)
        titleInput?.setText(title)

        // Configurar acciones
        findViewById<Button>(R.id.confirmation_btn_save)?.setOnClickListener {
            val newTitle = titleInput?.text?.toString()?.take(120) ?: title
            val controller = ExternalConfirmationController.getInstance(this)
            val current = controller.queue.getCurrent()
            if (current != null && current.id == suggestionId) {
                controller.editSuggestion(
                    current,
                    ExternalSuggestionAction.Edit(newTitle = newTitle)
                )
            }
            setResult(RESULT_EDITED)
            finish()
        }

        findViewById<Button>(R.id.confirmation_btn_cancel)?.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // No guardar el texto original
        super.onSaveInstanceState(outState)
        outState.clear()
    }
}
