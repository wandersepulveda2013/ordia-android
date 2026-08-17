package com.ordia.app.intelligence

import android.util.Log
import com.ordia.app.domain.SensitiveSecretPatterns

/**
 * Puerta de seguridad que se ejecuta ANTES de cualquier proveedor de inteligencia.
 *
 * Responsabilidades:
 * 1. Filtro de privacidad (contraseñas, PIN, OTP, datos bancarios, salud, etc.)
 * 2. Contenido bloqueado (sexual, violencia, drogas, delitos)
 * 3. Sanitización básica del texto
 *
 * La detección de secretos estructurados (PAN/CLABE/IBAN por Luhn/checksum,
 * claves PEM, tokens, CURP/RFC/DNI/INE, etc.) se delega a
 * [SensitiveSecretPatterns], única fuente de verdad (c.299), para que la
 * puerta de inteligencia, la de persistencia y la de lectura no se
 * desincronicen. Antes de c.361 este gate usaba patrones propios
 * (`\b\d{13,19}\b` sin Luhn y un SSN `\d{3}-\d{2}-\d{4}`) que producían
 * falsos positivos sobre referencias/facturas/IMEI largos (pérdida de
 * captura de tareas legítimas) y falsos negativos sobre claves PEM y
 * tokens que el gate canónico sí detectaba.
 *
 * Si evaluate() devuelve [PrivacyResult.BLOCKED], el proveedor de
 * inteligencia NO debe ejecutarse. El texto original se descarta.
 */
object IntelligenceSafetyGate {

    private const val TAG = "IntelligenceSafetyGate"

    /** "pin"/"nip" como palabra aislada (limite \b), no como subcadena de
     *  "pintar"/"pintura"/"pines"/"snippet" (c.509). `lower` ya esta en
     *  minusculas al evaluarse. */
    private val PIN_NIP_WORD = Regex("""\b(pin|nip)\b""")

    /** Tipos de "código" cuyo número es un identificador público, no un OTP/secret.
     *  Evita bloquear "código postal 12345", "código de barras 1234567",
     *  "código QR de la factura 2024001", "código de área 555", etc. (c.510). */
    private val CODIGO_NO_SECRETO = Regex(
        """código\s+(postal|de\s+barras|qr|de\s+área|de\s+fuente|de\s+producto|de\s+cliente|de\s+artículo)""",
        RegexOption.IGNORE_CASE
    )

    /** Usos de "clave" que NO son credenciales: metafóricos ("la clave del éxito"),
     *  de juego/acertijo ("la clave del juego"), o musicales ("clave musical",
     *  "clave de sol/fa/do"). Evita bloquear "la clave del éxito es practicar 100
     *  veces" o "recordar la clave de sol del acertijo 123" (c.512). */
    private val CLAVE_NO_CREDENCIAL = Regex(
        """clave\s+(del?\s+(?:é|e)xito|del?\s+juego|musical|de\s+(?:sol|fa|do|re|mi|la|si))""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Patrones de contenido bloqueado: modera el tema del que la inteligencia
     * puede ocuparse. Son legítimamente específicos de esta puerta (no forman
     * parte de [SensitiveSecretPatterns], que solo detecta secretos).
     */
    private val BLOCKED_CONTENT_PATTERNS = listOf(
        // Contenido sexual explícito
        Regex("""\b(sexo|sexual|desnud|porno|xxx|eróti|culos|tetas|pene|vagina|orgasmo|masturb)""", RegexOption.IGNORE_CASE),
        // Violencia y amenazas
        Regex("""\b(matar|asesinar|violar|secuestr|bomba|amenaza|escopeta|pistola|cuchill)""", RegexOption.IGNORE_CASE),
        // Drogas ilegales
        Regex("""\b(droga|cocaína|heroína|marihuana|metanfetamina|narcotráfico)""", RegexOption.IGNORE_CASE),
        // Insultos graves
        Regex("""\b(pendejo|estúpido|imbécil|malparido|hijueputa)""", RegexOption.IGNORE_CASE)
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

        // 1. Contenido bloqueado (moderación temática).
        for (blockedPattern in BLOCKED_CONTENT_PATTERNS) {
            if (blockedPattern.containsMatchIn(text)) {
                Log.w(TAG, "Bloqueado por contenido: ${blockedPattern.pattern.take(40)}")
                return PrivacyResult.BLOCKED
            }
        }

        // 2. Secretos estructurados: fuente única de verdad (c.299/c.303).
        //    PAN/CLABE/IBAN validados por Luhn/checksum, claves criptográficas
        //    (PEM), tokens de servicio e identificadores personales (CURP/RFC/
        //    DNI/INE/pasaporte). Evita falsos positivos sobre números largos
        //    que no son tarjetas válidas.
        if (SensitiveSecretPatterns.containsNumericSensitive(text) ||
            SensitiveSecretPatterns.containsPersonalIdentifier(text) ||
            SensitiveSecretPatterns.patterns.any { it.containsMatchIn(text) }
        ) {
            Log.w(TAG, "Bloqueado por secreto detectado")
            return PrivacyResult.BLOCKED
        }

        // 3. Credenciales conversacionales (contraseña/PIN/OTP con valor).
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
        // Reemplazar secretos estructurados detectados por la fuente canónica
        // (no cualquier número largo, para no ocultar referencias/facturas
        // legítimas — c.303).
        SensitiveSecretPatterns.patterns.forEach { p ->
            safe = safe.replace(p, "[REDACTED]")
        }
        // Reemplazar emails
        safe = safe.replace(Regex("""\b[\w.+-]+@[\w-]+\.[\w.]+"""), "[EMAIL]")
        return safe.trim().take(500)
    }

    private fun containsCredentials(text: String): Boolean {
        val lower = text.lowercase()
        // Contraseñas: palabra clave + valor adyacente (evita bloquear
        // "recuérdame cambiar mi contraseña" sin valor real, que el gate
        // canónico también deja pasar).
        if (credentialKeywordWithValue(lower, listOf("contraseña", "password", "pwd", "clave"))) {
            return true
        }
        // OTP / códigos de verificación. Se exige "código" + un valor numérico
        // corto (4-8 dígitos), pero se excluyen los tipos de "código" que NO son
        // secretos (postal, de barras, QR, de área, de fuente, de producto, de factura):
        // su número es un identificador público, no un OTP. Sin esta exclusión,
        // tareas como "envía el paquete al código postal 12345" o "imprime el código
        // QR de la factura 2024001" se bloqueaban injustamente (c.510).
        if (lower.contains("código") &&
            !CODIGO_NO_SECRETO.containsMatchIn(lower) &&
            Regex("""\d{4,8}""").containsMatchIn(lower)
        ) {
            return true
        }
        // PIN / NIP: limite de palabra \b(pin|nip)\b para no casar dentro de
        // "pintar"/"pintura"/"pines"/"snippet" (falso positivo que bloqueaba
        // tareas de pintura legitimas cuando aparecia un numero de 4-6 digitos,
        // c.509). El valor sigue siendo cualquier \d{4,6} en el texto, como la
        // rama de "codigo" OTP.
        if (PIN_NIP_WORD.containsMatchIn(lower) && Regex("""\d{4,6}""").containsMatchIn(lower)) {
            return true
        }
        return false
    }

    /** Palabra clave de credencial seguida (en una ventana corta) de un valor: separador
     *  explícito (`:`/`=`) o un token numérico (\d{3,}). Evita bloquear frases sin valor
     *  real como "recuérdame cambiar mi contraseña esta semana" (sin dígitos). */
    private fun credentialKeywordWithValue(lower: String, keywords: List<String>): Boolean {
        val valueAfter = Regex("""[=:]\s*\S+""")
        val tokenAfter = Regex("""\d{3,}""")
        for (kw in keywords) {
            val idx = lower.indexOf(kw)
            if (idx < 0) continue
            // "clave" tiene usos no-credenciales ("la clave del éxito", "clave
            // musical", "clave de sol"). Si el contexto inmediato es uno de esos,
            // no es una credencial aunque haya un número cerca (c.512).
            if (kw == "clave" && CLAVE_NO_CREDENCIAL.containsMatchIn(lower)) continue
            val window = lower.substring(idx + kw.length).take(40)
            if (valueAfter.containsMatchIn(window) || tokenAfter.containsMatchIn(window)) return true
        }
        return false
    }
}
