package com.ordia.app.context

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager

/**
 * Registro de compatibilidad para el motor contextual.
 *
 * Verifica qué capacidades están disponibles en el dispositivo actual
 * y proporciona fallbacks para versiones anteriores de Android.
 *
 * Cada fuente de captura debe consultar este registro antes de activarse.
 */
class ContextCompatibilityRegistry(private val appContext: Context) {

    /** Versión de Android */
    val sdkInt: Int = Build.VERSION.SDK_INT

    /** ¿Soporta captura desde el portapapeles moderna (ClipboardManager) tal como está en API 33+? */
    val supportsClipboardCapture: Boolean
        get() = sdkInt >= Build.VERSION_CODES.TIRAMISU

    /** ¿Soporta NotificationListenerService? */
    val supportsNotificationCapture: Boolean
        get() {
            return if (sdkInt >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                val notificationListeners = Settings.Secure.getString(
                    appContext.contentResolver,
                    Settings.Secure.ENABLED_NOTIFICATION_LISTENERS
                )
                notificationListeners?.contains(appContext.packageName) == true
            } else false
        }

    /** ¿Soporta AccessibilityService avanzado? */
    val supportsAdvancedScreenCapture: Boolean
        get() = sdkInt >= Build.VERSION_CODES.JELLY_BEAN_MR2

    /** ¿Soporta entrada por voz (SpeechRecognizer)? */
    val supportsVoiceCapture: Boolean
        get() = sdkInt >= Build.VERSION_CODES.JELLY_BEAN

    /** ¿Es un dispositivo con Google Play Services (para voz)? */
    val hasGoogleVoiceSupport: Boolean
        get() {
            return try {
                val googleApi = Class.forName("com.google.android.gms.common.GoogleApiAvailability")
                googleApi.getMethod("isGooglePlayServicesAvailable", Context::class.java)
                    .invoke(null, appContext) as? Int == 0
            } catch (e: Exception) {
                false
            }
        }

    /** ¿Es un emulador? */
    val isEmulator: Boolean
        get() = Build.FINGERPRINT.contains("generic") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
            Build.PRODUCT.contains("sdk_google") ||
            Build.PRODUCT.contains("sdk") ||
            "google_sdk" == Build.PRODUCT

    /** ¿Soporta el teclado Ordía como método de entrada instalado? */
    val supportsOrdíaKeyboard: Boolean
        get() {
            return try {
                val imm = appContext.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                if (imm == null) return false
                val enabledMethods = imm.enabledInputMethodList
                enabledMethods.any { it.packageName == appContext.packageName }
            } catch (e: Exception) {
                false
            }
        }

    /** ¿Soporta almacenamiento local con cifrado? */
    val supportsEncryptedStorage: Boolean
        get() = sdkInt >= Build.VERSION_CODES.M

    /** ¿Soporta notificaciones con acción directa? */
    val supportsDirectReplyNotifications: Boolean
        get() = sdkInt >= Build.VERSION_CODES.N

    /** ¿Soporta BiometricPrompt? */
    val supportsBiometricAuth: Boolean
        get() = sdkInt >= Build.VERSION_CODES.P

    /** Lista de fuentes de captura activas según compatibilidad */
    fun activeCaptureSources(): List<ContextCaptureSource> {
        val sources = mutableListOf<ContextCaptureSource>()

        // Keyboard: soportado si el IME está instalado
        sources.add(ContextCaptureSource.KEYBOARD)

        // Texto seleccionado: soportado desde API 1 (FloatActionBar)
        sources.add(ContextCaptureSource.SELECTED_TEXT)

        // Texto compartido: soportado desde API 1 (Intent.ACTION_SEND)
        sources.add(ContextCaptureSource.SHARED_TEXT)

        // Notificaciones: requiere NotificationListenerService
        sources.add(ContextCaptureSource.NOTIFICATION)

        // Captura avanzada de pantalla: requiere AccessibilityService
        if (supportsAdvancedScreenCapture) {
            sources.add(ContextCaptureSource.SCREEN_ADVANCED)
        }

        // Voz: requiere Google Play Services
        if (supportsVoiceCapture && hasGoogleVoiceSupport) {
            sources.add(ContextCaptureSource.VOICE)
        }

        // Comandos del guardián: siempre activo
        sources.add(ContextCaptureSource.GUARDIAN_COMMAND)

        return sources
    }

    /** Etiqueta descriptiva del dispositivo para depuración */
    fun deviceLabel(): String {
        val parts = mutableListOf<String>()
        parts.add("API $sdkInt")
        parts.add(Build.MANUFACTURER)
        parts.add(Build.MODEL)
        if (isEmulator) parts.add("(emulator)")
        return parts.joinToString(" ")
    }

    /** Resumen de compatibilidad */
    fun summary(): String {
        return buildString {
            appendLine("=== Context Compatibility Registry ===")
            appendLine("Device: ${deviceLabel()}")
            appendLine("Active sources: ${activeCaptureSources().joinToString { it.name }}")
            appendLine("Clipboard capture: $supportsClipboardCapture")
            appendLine("Notification capture: $supportsNotificationCapture")
            appendLine("Screen capture (advanced): $supportsAdvancedScreenCapture")
            appendLine("Voice capture: $supportsVoiceCapture ($hasGoogleVoiceSupport)")
            appendLine("Ordía keyboard: $supportsOrdíaKeyboard")
            appendLine("Encrypted storage: $supportsEncryptedStorage")
            appendLine("Biometric auth: $supportsBiometricAuth")
        }
    }
}
