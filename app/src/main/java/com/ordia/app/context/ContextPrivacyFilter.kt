package com.ordia.app.context

/**
 * Filtro de privacidad para eventos contextuales.
 * Bloquea contenido sensible antes de cualquier análisis.
 *
 * Reglas:
 * - No procesar campos de contraseña, PIN, OTP, CVV, tarjetas, números bancarios.
 * - No procesar aplicaciones bancarias, autenticadores, médicas, de contraseñas.
 * - No procesar contenido sexual, violencia, drogas, delitos, acoso, autolesiones.
 * - No procesar navegación privada ni campos marcados como sensibles.
 * - El filtro es determinista y no requiere red.
 */
object ContextPrivacyFilter {

    /** Paquetes de aplicaciones bloqueadas completamente */
    private val BLOCKED_PACKAGES = setOf(
        "com.android.chrome",           // Chrome (navegación privada manejada aparte)
        // Aplicaciones bancarias conocidas
        "com.bancomer", "com.banamex", "com.santander", "com.bbva",
        "com.hsbc", "com.scotiabank", "com.banregio", "com.azteca",
        "com.banorte", "com.inbursa", "com.afirme", "com.interacciones",
        // Autenticadores
        "com.google.android.apps.authenticator2", "com.authy",
        "com.microsoft.authenticator", "com.lastpass.authenticator",
        "com.duosecurity", "com.okta.android.auth",
        // Gestores de contraseñas
        "com.lastpass", "com.1password", "com.dashlane",
        "com.bitwarden", "com.keepass.android", "com.enpass",
        // Aplicaciones médicas
        "com.health", "com.medical", "com.clinic", "com.hospital"
    )

    /** Patrones de contenido a bloquear */
    private val BLOCKED_CONTENT_PATTERNS = listOf(
        // Credenciales y datos sensibles
        Regex("""\b(contraseña|password|passwd|pwd|clave|pin)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(otp|2fa|two.?factor|verificación)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(cvv|código de seguridad|número de tarjeta|card number)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(número de cuenta|account number|clabe|iban|swift)\b""", RegexOption.IGNORE_CASE),
        // Contenido sexual explícito
        Regex("""\b(sexo|sexual|desnud|porno|xxx|eróti|intimidad)\b""", RegexOption.IGNORE_CASE),
        // Violencia y delitos
        Regex("""\b(matar|asesinar|violar|robar|secuestr|bomba|arma|amenaza)\b""", RegexOption.IGNORE_CASE),
        // Drogas
        Regex("""\b(droga|cocaína|marihuana|heroína|metanfetamina|narcotráfico)\b""", RegexOption.IGNORE_CASE),
        // Acoso
        Regex("""\b(acoso|hostigamiento|extorsión|chantaje)\b""", RegexOption.IGNORE_CASE),
        // Autolesiones
        Regex("""\b(suicidi|autolesión|hacerme daño|quitarme la vida)\b""", RegexOption.IGNORE_CASE),
        // Información bancaria
        Regex("""\b(transferencia|depósito|retiro|saldo|estado de cuenta)\b""", RegexOption.IGNORE_CASE),
        // Política y religión (contenido polarizante no organizativo)
        Regex("""\b(partido político|elección|campaña política|votar por)\b""", RegexOption.IGNORE_CASE)
    )

    /** Campos de entrada que deben ser ignorados */
    private val SENSITIVE_INPUT_TYPES = setOf(
        "password", "textPassword", "textVisiblePassword", "textWebEditTextPassword",
        "numberPassword", "date", "time" // fecha/hora en campos específicos
    )

    /**
     * Verifica si un evento debe ser bloqueado por razones de privacidad.
     * Retorna true si el evento debe ser descartado silenciosamente.
     */
    fun shouldBlock(event: ContextEvent): Boolean {
        // Bloquear por paquete
        event.sourcePackage?.let { pkg ->
            if (BLOCKED_PACKAGES.any { pkg.startsWith(it, ignoreCase = true) }) return true
        }

        // Bloquear por tipo de entrada sensible
        event.metadata["inputType"]?.let { type ->
            if (SENSITIVE_INPUT_TYPES.contains(type)) return true
        }

        // Bloquear por contenido
        val text = event.safeText
        return BLOCKED_CONTENT_PATTERNS.any { it.containsMatchIn(text) }
    }

    /**
     * Determina si un paquete debe ser bloqueado.
     */
    fun isPackageBlocked(packageName: String): Boolean {
        return BLOCKED_PACKAGES.any { packageName.startsWith(it, ignoreCase = true) }
    }

    /**
     * Verifica si un tipo de entrada es sensible y debe ignorarse.
     */
    fun isSensitiveInputType(inputType: String): Boolean {
        return SENSITIVE_INPUT_TYPES.contains(inputType)
    }
}
