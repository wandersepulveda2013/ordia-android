package com.ordia.app.context.external

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver para acciones rápidas desde notificaciones de sugerencias externas.
 *
 * Recibe acciones del usuario sin necesidad de abrir Ordía.
 * Procesa: Agregar, Ignorar, Posponer desde la notificación.
 *
 * No expone texto original en el Intent ni en logs.
 */
class ExternalConfirmationReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ExtConfirmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(ExternalConfirmationController.EXTRA_ACTION) ?: return
        val suggestionId = intent.getStringExtra(ExternalConfirmationController.EXTRA_SUGGESTION_ID) ?: return

        val controller = ExternalConfirmationController.getInstance(context)
        val current = controller.queue.getCurrent()

        // Verificar que la sugerencia coincide
        if (current?.id != suggestionId) {
            Log.w(TAG, "Sugerencia no coincide con la actual, ignorando acción")
            return
        }

        Log.d(TAG, "Acción recibida: $action")

        when (action) {
            ExternalConfirmationController.ACTION_ADD -> {
                controller.addSuggestion(current)
            }
            ExternalConfirmationController.ACTION_IGNORE -> {
                controller.ignoreSuggestion(current)
            }
            ExternalConfirmationController.ACTION_POSTPONE -> {
                controller.postponeSuggestion(current, PostponeDuration.ONE_HOUR)
            }
            ExternalConfirmationController.ACTION_OPEN -> {
                // Abrir actividad compacta de edición
                val editIntent = controller.createEditIntent(current)
                editIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(editIntent)
            }
        }
    }
}
