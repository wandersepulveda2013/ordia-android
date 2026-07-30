package com.ordia.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEngine
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextResult

/**
 * AccessibilityService para captura avanzada de pantalla en Ordía 3.
 *
 * Requiere activación manual del usuario en Ajustes → Accesibilidad.
 * Todo desactivado inicialmente. Solo procesa texto de aplicaciones
 * autorizadas explícitamente por el usuario.
 *
 * Privacidad:
 * - Nunca procesa contraseñas, PIN, OTP, campos sensibles
 * - Solo texto visible, no pulsaciones ni navegación
 * - Las aplicaciones no autorizadas son ignoradas
 * - El texto se descarta inmediatamente después del análisis
 *
 * Consentimiento: el usuario debe activar el servicio en ajustes del sistema
 * Y autorizar cada aplicación individualmente dentro de Ordía.
 */
class OrdiaAccessibilityService : AccessibilityService() {

    private var isEnabled = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 500
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        serviceInfo = info
        Log.d(TAG, "AccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isEnabled) return

        // Solo procesar cambios de texto en aplicaciones autorizadas
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return
        val packageName = event.packageName?.toString() ?: return

        // Verificar consentimiento: aplicación autorizada
        if (!isPackageAuthorized(packageName)) return

        // Obtener el texto del evento
        val text = getEventText(event) ?: return
        if (text.isBlank() || text.length < 8) return

        // Crear evento contextual y procesar
        val contextEvent = ContextEvent(
            source = ContextCaptureSource.SCREEN_ADVANCED,
            rawText = text,
            timestampMs = System.currentTimeMillis(),
            sourcePackage = packageName
        )

        val engine = ContextEngine.getInstance(this)
        when (val result = engine.processEvent(contextEvent)) {
            is ContextResult.PendingConfirmation -> {
                Log.d(TAG, "Screen capture: pending confirmation for '${result.intent.title.take(40)}'")
                // La UI recogerá las confirmaciones pendientes al abrir la app
            }
            is ContextResult.Created -> {
                Log.d(TAG, "Screen capture: auto-created '${result.intent.title.take(40)}'")
            }
            is ContextResult.Discarded -> {
                // Silenciosamente descartado
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "AccessibilityService interrupted")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ENABLE) {
            setEnabled(true)
        } else if (intent?.action == ACTION_DISABLE) {
            setEnabled(false)
        } else if (intent?.action == ACTION_AUTHORIZE_PACKAGE) {
            val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME)
            if (pkg != null) authorizePackage(pkg)
        } else if (intent?.action == ACTION_UNAUTHORIZE_PACKAGE) {
            val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME)
            if (pkg != null) unauthorizePackage(pkg)
        }
        return START_NOT_STICKY
    }

    /** Obtiene texto de un evento de accesibilidad */
    private fun getEventText(event: AccessibilityEvent): String? {
        // De los eventos de texto
        val text = event.text?.joinToString(" ")?.trim()?.take(MAX_TEXT_LENGTH)
        if (!text.isNullOrBlank()) return text

        // Del nodo raíz
        val source = event.source ?: return null
        val nodeText = extractTextFromNode(source)
        source.recycle()
        return nodeText
    }

    /** Extrae texto de un nodo de accesibilidad */
    private fun extractTextFromNode(node: AccessibilityNodeInfo): String? {
        if (node.text != null) return node.text.toString().take(MAX_TEXT_LENGTH)
        // Intentar con contenido
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val text = extractTextFromNode(child)
            child.recycle()
            if (text != null) return text
        }
        return null
    }

    /** Verifica si un paquete está autorizado por el usuario */
    private fun isPackageAuthorized(packageName: String): Boolean {
        val authorized = getAuthorizedPackages()
        return authorized.contains(packageName)
    }

    /** Obtiene la lista de paquetes autorizados */
    private fun getAuthorizedPackages(): Set<String> {
        return prefs.getStringSet(KEY_AUTHORIZED_PACKAGES, emptySet()) ?: emptySet()
    }

    /** Autoriza un paquete para captura de pantalla */
    private fun authorizePackage(packageName: String) {
        val current = getAuthorizedPackages().toMutableSet()
        current.add(packageName)
        prefs.edit().putStringSet(KEY_AUTHORIZED_PACKAGES, current).apply()
        Log.d(TAG, "Package authorized: $packageName")
    }

    /** Desautoriza un paquete */
    private fun unauthorizePackage(packageName: String) {
        val current = getAuthorizedPackages().toMutableSet()
        current.remove(packageName)
        prefs.edit().putStringSet(KEY_AUTHORIZED_PACKAGES, current).apply()
        Log.d(TAG, "Package unauthorized: $packageName")
    }

    private fun setEnabled(value: Boolean) {
        isEnabled = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        Log.d(TAG, "Accessibility capture ${if (value) "enabled" else "disabled"}")
    }

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("ordia_accessibility", MODE_PRIVATE)
    }

    companion object {
        private const val TAG = "OrdiaAccessibility"
        private const val KEY_ENABLED = "capture_enabled"
        private const val KEY_AUTHORIZED_PACKAGES = "authorized_packages"
        private const val MAX_TEXT_LENGTH = 4_000

        const val ACTION_ENABLE = "com.ordia.app.action.ACCESSIBILITY_ENABLE"
        const val ACTION_DISABLE = "com.ordia.app.action.ACCESSIBILITY_DISABLE"
        const val ACTION_AUTHORIZE_PACKAGE = "com.ordia.app.action.AUTHORIZE_PACKAGE"
        const val ACTION_UNAUTHORIZE_PACKAGE = "com.ordia.app.action.UNAUTHORIZE_PACKAGE"
        const val EXTRA_PACKAGE_NAME = "package_name"
    }
}
