package com.ordia.app.ime

import android.view.inputmethod.EditorInfo
import com.ordia.app.context.ContextPrivacyFilter

/**
 * Guardián de privacidad del IME.
 *
 * Lógica pura y testeable que decide si un campo de entrada debe ignorarse
 * por completo (contraseñas, PIN, OTP, fechas y aplicaciones bloqueadas) y
 * que normaliza/hashea los patrones "No detectar" para no persistir nunca
 * texto en claro.
 */
object KeyboardPrivacyGuard {

    /** Tope de caracteres del buffer de captura del teclado. */
    const val MAX_BUFFER_CHARS = 1000

    /**
     * Determina si un tipo de entrada es sensible.
     *
     * @param inputType combinación de [EditorInfo.inputType]
     * @return true si el campo es contraseña/PIN/OTP o una fecha/hora
     */
    fun isSensitiveInputType(inputType: Int): Boolean {
        val variation = inputType and EditorInfo.TYPE_MASK_VARIATION
        val type = inputType and EditorInfo.TYPE_MASK_CLASS
        if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD) return true
        if (type == EditorInfo.TYPE_CLASS_NUMBER &&
            variation == EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD) return true
        if (type == EditorInfo.TYPE_CLASS_DATETIME) return true
        return false
    }

    /**
     * Determina si un campo debe ignorarse por completo.
     *
     * @param inputType combinación de [EditorInfo.inputType]
     * @param packageName paquete de la aplicación anfitriona ([EditorInfo.packageName])
     * @return true si el campo es sensible o la app está bloqueada
     */
    fun shouldIgnore(inputType: Int, packageName: String?): Boolean {
        if (isSensitiveInputType(inputType)) return true
        if (!packageName.isNullOrEmpty() && ContextPrivacyFilter.isPackageBlocked(packageName)) return true
        return false
    }

    /** Normaliza un texto a tokens en minúsculas para comparar frases similares */
    fun normalizeTokens(text: String): String =
        text.lowercase()
            .split(Regex("\\W+"))
            .filter { it.isNotBlank() }
            .joinToString(" ")

    /** SHA-256 en hexadecimal (patrones "No detectar", nunca texto en claro) */
    fun sha256Hex(value: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
