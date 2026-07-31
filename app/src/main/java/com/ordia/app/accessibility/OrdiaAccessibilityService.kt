package com.ordia.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEngine
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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

    // Las llamadas al motor contextual son asíncronas; el análisis nunca
    // bloquea el hilo principal del sistema de accesibilidad.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            // Solo eventos de cambio de texto: es lo único que procesa el
            // handler. No registrar tipos que se ignoran ahorra batería y
            // alinea la configuración con ordia_accessibility_config.xml.
            eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 500
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        serviceInfo = info
        // Recuperar el estado guardado por el usuario; el servicio arranca
        // desactivado por defecto.
        isEnabled = prefs.getBoolean(KEY_ENABLED, false)
        Log.d(TAG, "AccessibilityService connected (enabled=$isEnabled)")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isEnabled) return

        // Solo procesar cambios de texto en aplicaciones autorizadas
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return
        val packageName = event.packageName?.toString() ?: return

        // Verificar consentimiento: aplicación autorizada
        if (!isPackageAuthorized(packageName)) return

        // Obtener el texto del evento (descarta campos password/masked)
        val sourceIsPassword = event.source?.isPassword == true
        val text = getEventText(event) ?: return

        // Política de privacidad: nunca procesar campos sensibles ni textos triviales
        if (!AccessibilityTextPolicy.shouldProcessText(text, sourceIsPassword)) return

        // Crear evento contextual y procesar (asíncrono, fuera del main)
        val contextEvent = ContextEvent(
            source = ContextCaptureSource.SCREEN_ADVANCED,
            rawText = text,
            timestampMs = System.currentTimeMillis(),
            sourcePackage = packageName
        )

        val engine = ContextEngine.getInstance(this)
        scope.launch {
            when (val result = engine.processEventAsync(contextEvent)) {
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
        // De los eventos de texto (con tope de longitud)
        val text = event.text?.joinToString(" ")?.trim()?.take(AccessibilityTextPolicy.MAX_TEXT_LENGTH)
        if (!text.isNullOrBlank()) return text

        // Del nodo raíz
        val source = event.source ?: return null
        val nodeText = extractTextFromNode(source, 0)
        source.recycle()
        return nodeText
    }

    /** Extrae texto de un nodo de accesibilidad, con profundidad acotada */
    private fun extractTextFromNode(node: AccessibilityNodeInfo, depth: Int): String? {
        // ORD-035: nunca extraer texto de campos password/masked
        if (AccessibilityTextPolicy.isSensitiveNode(node.isPassword)) return null
        // ORD-034: la recursión no puede bajar más de MAX_NODE_DEPTH niveles
        if (!AccessibilityTextPolicy.canDescend(depth)) return null

        if (node.text != null) return node.text.toString().take(AccessibilityTextPolicy.MAX_TEXT_LENGTH)
        // Intentar con contenido
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val text = extractTextFromNode(child, depth + 1)
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
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "OrdiaAccessibility"
        private const val PREFS_NAME = "ordia_accessibility"
        const val KEY_ENABLED = "capture_enabled"
        private const val KEY_AUTHORIZED_PACKAGES = "authorized_packages"

        const val ACTION_ENABLE = "com.ordia.app.action.ACCESSIBILITY_ENABLE"
        const val ACTION_DISABLE = "com.ordia.app.action.ACCESSIBILITY_DISABLE"
        const val ACTION_AUTHORIZE_PACKAGE = "com.ordia.app.action.AUTHORIZE_PACKAGE"
        const val ACTION_UNAUTHORIZE_PACKAGE = "com.ordia.app.action.UNAUTHORIZE_PACKAGE"
        const val EXTRA_PACKAGE_NAME = "package_name"

        /** Estado de captura guardado por el usuario (UI y servicio comparten prefs). */
        fun isCaptureEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

        /**
         * Activa/desactiva la captura. Persiste la preferencia y, si el
         * servicio está vivo, le entrega la orden directamente. Si no está
         * corriendo (no activado en ajustes del sistema), la preferencia se
         * aplicará en la próxima conexión del servicio.
         */
        fun setCaptureEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply()
            runCatching {
                context.startService(
                    Intent(context, OrdiaAccessibilityService::class.java)
                        .setAction(if (enabled) ACTION_ENABLE else ACTION_DISABLE)
                )
            }
        }

        /** ¿Está el servicio activado por el usuario en Ajustes → Accesibilidad? */
        fun isServiceEnabledInSystem(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return false
            return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { service ->
                    service.resolveInfo?.serviceInfo?.packageName == context.packageName &&
                        service.resolveInfo.serviceInfo.name == OrdiaAccessibilityService::class.java.name
                }
        }
    }
}
