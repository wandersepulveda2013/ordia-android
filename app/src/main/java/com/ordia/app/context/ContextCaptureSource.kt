package com.ordia.app.context

/**
 * Origen de un evento contextual.
 * Cada fuente tiene su propio mecanismo de captura y permisos asociados.
 */
enum class ContextCaptureSource(
    val displayName: String,
    val requiresPermission: Boolean,
    val isAdvanced: Boolean = false
) {
    /** Teclado opcional de Ordía (InputMethodService) */
    KEYBOARD("Teclado de Ordía", requiresPermission = true, isAdvanced = false),

    /** Texto seleccionado manualmente por el usuario (ACTION_PROCESS_TEXT) */
    SELECTED_TEXT("Texto seleccionado", requiresPermission = false),

    /** Texto compartido desde otra aplicación (ACTION_SEND) */
    SHARED_TEXT("Texto compartido", requiresPermission = false),

    /** Notificaciones autorizadas vía NotificationListenerService */
    NOTIFICATION("Notificaciones", requiresPermission = true),

    /** Captura de pantalla avanzada vía AccessibilityService (solo previewAdvanced) */
    SCREEN_ADVANCED("Pantalla avanzada", requiresPermission = true, isAdvanced = true),

    /** Entrada de voz */
    VOICE("Voz", requiresPermission = true),

    /** Comandos escritos o hablados desde el guardián flotante */
    GUARDIAN_COMMAND("Guardián", requiresPermission = false);
}
