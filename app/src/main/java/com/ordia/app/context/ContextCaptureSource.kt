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

    /** Valor heredado para compatibilidad de datos y pruebas; nunca se activa en la aplicación. */
    @Deprecated("Ordía no usa AccessibilityService para leer interfaces")
    SCREEN_ADVANCED("Pantalla avanzada", requiresPermission = true, isAdvanced = true),

    /** Entrada de voz */
    VOICE("Voz", requiresPermission = true),

    /** Comandos escritos o hablados desde el guardián flotante */
    GUARDIAN_COMMAND("Guardián", requiresPermission = false),
    /** Superposición flotante (asistente en guardian) */
    OVERLAY("Superposición", requiresPermission = true, isAdvanced = true),
    /** Pruebas de diagnóstico interno */
    DIAGNOSTICS("Diagnóstico", requiresPermission = false);
}
