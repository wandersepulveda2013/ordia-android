package com.ordia.app.context.external

/**
 * Lógica pura de seguridad del contexto externo (ORD-018).
 *
 * Objeto JVM sin dependencias de Android para poder testear en unit tests:
 * - [isSecurePackage]: decide si un paquete de origen debe impedir mostrar
 *   una sugerencia externa (lista fija de paquetes sensibles + exclusión
 *   configurada por el usuario). El paquete se compara por prefijo porque
 *   los nombres reales incluyen sufijos de país/edición (com.bbva.mx, etc.).
 * - [isSensitiveTitle]: detecta contenido sensible en el título interpretado
 *   (nunca texto original del usuario).
 */
object ExternalSecureContext {

    /** Paquetes sensibles donde nunca mostrar la tarjeta externa. */
    internal val SECURE_PACKAGES = setOf(
        // Sistema
        "com.android.contacts",
        "com.android.settings",
        "com.google.android.apps.photos",
        "com.android.vending",
        // Aplicaciones bancarias (México)
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

    /** Palabras de contenido sensible en títulos interpretados. */
    private val SENSITIVE_WORDS = listOf(
        "contraseña", "password", "pin", "otp", "código de verificación",
        "token", "clave", "credencial", "numero de tarjeta",
        "iban", "cvv", "cvc", "documento de identidad", "rut", "dni",
        "seguro social", "historial clínico", "diagnóstico"
    )

    /**
     * ¿El paquete de origen impide mostrar la sugerencia externa?
     * `null` (desconocido) no bloquea: el pipeline contextual ya filtró el
     * contenido con [com.ordia.app.context.ContextPrivacyFilter] antes de
     * producir la sugerencia.
     */
    fun isSecurePackage(
        packageName: String?,
        securePackages: Set<String> = SECURE_PACKAGES,
        excludedApps: Set<String> = emptySet()
    ): Boolean {
        val pkg = packageName ?: return false
        return (securePackages + excludedApps)
            .any { pkg.startsWith(it, ignoreCase = true) }
    }

    /** ¿El título interpretado parece contenido sensible? */
    fun isSensitiveTitle(title: String): Boolean {
        val t = title.lowercase()
        return SENSITIVE_WORDS.any { t.contains(it) }
    }
}
