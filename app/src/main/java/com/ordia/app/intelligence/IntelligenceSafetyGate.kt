package com.ordia.app.intelligence

import android.util.Log
import com.ordia.app.context.ContextPrivacyFilter

/**
 * Puerta de seguridad que se ejecuta ANTES de cualquier proveedor de inteligencia.
 *
 * Responsabilidades:
 * 1. Filtro de privacidad (contraseñas, PIN, OTP, datos bancarios, salud, etc.)
 * 2. Contenido bloqueado (sexual, violencia, drogas, delitos)
 * 3. Sanitización básica del texto
 *
 * Si safetyGate.block() devuelve true, el proveedor de inteligencia NO debe ejecutarse.
 * El texto original se descarta inmediatamente.
 */
object IntelligenceSafetyGate {

    private const val TAG = "IntelligenceSafetyGate"

    /** Palabras y patrones de contenido bloqueado */
    private val BLOCKED_PATTERNS = listOf(
        // Contenido sexual explícito
        Regex("""\b(sexo|sexual|desnud|porno|xxx|eróti|culos|tetas|pene|vagina|orgasmo|masturb)""", RegexOption.IGNORE_CASE),
        // Violencia y amenazas
        Regex("""\b(matar|asesinar|violar|secuestr|bomba|amenaza|escopeta|pistola|cuchill)""", RegexOption.IGNORE_CASE),
        // Drogas ilegales
        Regex("""\b(droga|cocaína|heroína|marihuana|metanfetamina|narcotráfico)""", RegexOption.IGNORE_CASE),
        // Insultos graves
        Regex("""\b(pendejo|estúpido|imbécil|malparido|hijueputa)""", RegexOption.IGNORE_CASE),
        // Datos bancarios / financieros sensibles
        Regex("""\b(\d{13,19})\b""").also { /* tarjetas de crédito */ },
        // Números de seguridad social / identificación
        Regex("""\b(\d{3}[-\s]?\d{2}[-\s]?\d{4})\b""", RegexOption.IGNORE_CASE)
    )

    /**
     * Evalúa si el texto debe ser bloqueado por seguridad o privacidad.
     *
     * @param text Texto a evaluar
     * @return PrivacyResult.SAFE si es seguro, PrivacyResult.BLOCKED si debe bloquearse
     */
    fun evaluate(text: String): PrivacyResult {
        val lower = text.lowercase().trim()
        if (lower.isBlank()) return PrivacyResult.BLOCKED

        // 1. Verificar patrones bloqueados
        for (pattern in BLOCKED_PATTERNS) {
            if (pattern.containsMatchIn(text)) {
                Log.w(TAG, "Bloqueado por patrón: ${pattern.pattern().take(40)}")
                return PrivacyResult.BLOCKED
            }
        }

        // 2. Delegar al filtro de privacidad existente
        // ContextPrivacyFilter.shouldBlock requiere un ContextEvent,
        // aquí hacemos una verificación simplificada de patrones sensibles
        if (containsCredentials(text)) {
            Log.w(TAG, "Bloqueado por credenciales detectadas")
            return PrivacyResult.BLOCKED
        }

        return PrivacyResult.SAFE
    }

    /**
     * Sanitiza el texto eliminando información potencialmente sensible
     * sin bloquear completamente el análisis.
     * Útil para el BasicRuleProvider que no puede procesar texto bloqueado.
     */
    fun sanitize(text: String): String {
        var safe = text
        // Reemplazar números largos (tarjetas, teléfonos)
        safe = safe.replace(Regex("""\b\d{13,19}\b"""), "[TARJETA]")
        // Reemplazar emails
        safe = safe.replace(Regex("""\b[\w.+-]+@[\w-]+\.[\w.]+"""), "[EMAIL]")
        return safe.trim().take(500)
    }

    private fun containsCredentials(text: String): Boolean {
        val lower = text.lowercase()
        // Contraseñas
        if (lower.contains("contraseña") || lower.contains("password") ||
            lower.contains("mi contraseña") || lower.contains("clave") && lower.length < 30) {
            return true
        }
        // OTP / códigos de verificación
        if (lower.contains("código") && Regex("""\d{4,8}""").containsMatchIn(lower)) {
            return true
        }
        // PIN
        if (lower.contains("pin") && Regex("""\d{4,6}""").containsMatchIn(lower)) {
            return true
        }
        return false
    }
}
