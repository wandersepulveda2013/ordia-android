package com.ordia.app.context

/**
 * Filtro local que descarta datos sensibles antes de cualquier análisis.
 * Las reglas son deterministas, no usan red y se aplican a todas las fuentes.
 */
object ContextPrivacyFilter {

    private val blockedPackagePrefixes = setOf(
        "com.android.chrome",
        "com.bancomer", "com.banamex", "com.santander", "com.bbva",
        "com.hsbc", "com.scotiabank", "com.banregio", "com.azteca",
        "com.banorte", "com.inbursa", "com.afirme", "com.interacciones",
        "com.google.android.apps.authenticator2", "com.authy",
        "com.microsoft.authenticator", "com.lastpass.authenticator",
        "com.duosecurity", "com.okta.android.auth",
        "com.lastpass", "com.1password", "com.dashlane",
        "com.bitwarden", "com.keepass.android", "com.enpass",
        "com.health", "com.medical", "com.clinic", "com.hospital"
    )

    private val blockedPackageFragments = setOf(
        "bank", "banco", "banking", "wallet", "authenticator",
        "password", "keepass", "medical", "healthcare"
    )

    private val blockedContentPatterns = listOf(
        Regex("""\b(contraseña|contrasena|password|passwd|pwd|clave|pin|nip)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(otp|2fa|two.?factor|verificación|verificacion|código de acceso|codigo de acceso)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(cvv|cvc|código de seguridad|codigo de seguridad|número de tarjeta|numero de tarjeta|card number)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(número de cuenta|numero de cuenta|account number|clabe|iban|swift|cédula|cedula)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(seed phrase|recovery phrase|frase semilla|frase de recuperación|frase de recuperacion|palabras de recuperación|palabras de recuperacion|mnemonic)\b""", RegexOption.IGNORE_CASE),
        Regex("""-----BEGIN [A-Z ]*PRIVATE KEY-----""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:0x)?[0-9a-f]{64}\b""", RegexOption.IGNORE_CASE),
        Regex("""\b[A-Z]{2}\s?\d{2}(?:\s?[A-Z0-9]){11,30}\b"""),
        // c.294: claves SSH publicas (ssh-(rsa|dsa|ecdsa|ed25519) + blob base64 20+).
        Regex("""\bssh-(?:rsa|dsa|ecdsa|ed25519)\s+[A-Za-z0-9+/]{20,}={0,2}"""),
        // c.294: API keys tipo Stripe/OpenAI (sk-, sk_live_, sk_test_ + 20+ alfanum).
        Regex("""(?i)\bsk[-_](?:live[-_]|test[-_])?[A-Za-z0-9]{20,}"""),
        // c.294: AWS access key IDs (prefijo canonico de 4 mayusculas + 16 base32).
        Regex("""\b(?:AKIA|ASIA|AGPA|AIDA|AROA|AIPA|ANPA|ANVA|ABIA|ACCA)[0-9A-Z]{16}\b"""),
        // c.294: JWT (eyJ.\.eyJ\....): 3 segmentos base64url separados por punto.
        Regex("""\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}"""),
        // c.296: Google API key, Slack tokens, GitHub/GitLab PATs (paridad con
        // ConversationPrivacyPolicy; ver alli).
        Regex("""\bAIza[0-9A-Za-z_-]{35}\b"""),
        Regex("""\bxox[abp]-[0-9A-Za-z-]{20,}\b"""),
        Regex("""\b(?:ghp|gho|ghu|ghs|ghr|github_pat)_[A-Za-z0-9_]{20,}\b"""),
        Regex("""\bglpat-[A-Za-z0-9_-]{20,}\b"""),
        Regex("""\b(sexo|sexual|desnud|porno|xxx|eróti|intimidad)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(matar|asesinar|violar|robar|secuestr|bomba|arma|amenaza)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(droga|cocaína|cocaina|marihuana|heroína|heroina|metanfetamina|narcotráfico|narcotrafico)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(acoso|hostigamiento|extorsión|extorsion|chantaje)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(suicidi|autolesión|autolesion|hacerme daño|hacerme dano|quitarme la vida)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(transferencia|depósito|deposito|retiro|saldo|estado de cuenta)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(partido político|partido politico|elección|eleccion|campaña política|campana politica|votar por)\b""", RegexOption.IGNORE_CASE)
    )

    private val sensitiveInputTypes = setOf(
        "password", "textPassword", "textVisiblePassword", "textWebEditTextPassword",
        "numberPassword", "date", "time"
    )

    private val sensitiveMetadataCue = Regex(
        """\b(password|passwd|pin|otp|2fa|cvv|cvc|card.?number|credit.?card|private.?key|seed.?phrase|recovery.?phrase|contraseña|contrasena|clave|tarjeta)\b""",
        RegexOption.IGNORE_CASE
    )

    private val cardCandidate = Regex("""(?<!\d)(?:\d[ -]?){12,18}\d(?!\d)""")
    private val numericSecret = Regex("""^\s*\d{4,8}\s*$""")
    private val shortNumericSecret = Regex("""^\s*\d{3,4}\s*$""")
    // CLABE interbancaria mexicana: 18 d�gitos (con posibles separadores). Se valida
    // aparte porque su d�gito verificador NO es Luhn, por lo que cardCandidate+Luhn la
    // deja escapar cuando aparece sin la palabra "clabe"/"cuenta" (fuga de cuenta bancaria).
    private val clabeCandidate = Regex("""(?<!\d)(?:\d[ -]?){17}\d(?!\d)""")

    fun shouldBlock(event: ContextEvent): Boolean {
        event.sourcePackage?.let { if (isPackageBlocked(it)) return true }
        if (event.metadata.any { (key, value) ->
                key.contains("input", ignoreCase = true) &&
                    (isSensitiveInputType(value) || sensitiveMetadataCue.containsMatchIn(value))
            }) return true

        return containsSensitiveContent(event.safeText, event.metadata)
    }

    fun containsSensitiveContent(text: String, metadata: Map<String, String> = emptyMap()): Boolean {
        if (text.isBlank()) return false
        if (blockedContentPatterns.any { it.containsMatchIn(text) }) return true
        if (numericSecret.matches(text)) return true
        if (metadata["inputClass"].equals("number", ignoreCase = true) && shortNumericSecret.matches(text)) return true
        if (cardCandidate.findAll(text).any { candidate ->
                val digits = candidate.value.filter(Char::isDigit)
                digits.length in 13..19 && passesLuhn(digits)
            }) return true
        if (clabeCandidate.findAll(text).any { candidate ->
                val digits = candidate.value.filter(Char::isDigit)
                digits.length == 18 && passesClabeChecksum(digits)
            }) return true
        return false
    }

    fun isPackageBlocked(packageName: String): Boolean {
        val normalized = packageName.lowercase()
        return blockedPackagePrefixes.any { normalized.startsWith(it.lowercase()) } ||
            blockedPackageFragments.any { it in normalized }
    }

    fun isSensitiveInputType(inputType: String): Boolean =
        sensitiveInputTypes.any { it.equals(inputType, ignoreCase = true) }

    private fun passesLuhn(digits: String): Boolean {
        var sum = 0
        var doubleDigit = false
        for (index in digits.indices.reversed()) {
            var value = digits[index].digitToInt()
            if (doubleDigit) {
                value *= 2
                if (value > 9) value -= 9
            }
            sum += value
            doubleDigit = !doubleDigit
        }
        return sum > 0 && sum % 10 == 0
    }

    /**
     * D�gito verificador de la CLABE interbancaria mexicana (18 d�gitos):
     * los primeros 17 se ponderan c�clicamente con (3, 7, 1), se suman mod 10 y
     * el d�gito de control es (10 - (suma mod 10)) mod 10. Debe coincidir con el
     * d�gito 18. No es Luhn, por lo que requiere validaci�n propia.
     */
    private fun passesClabeChecksum(digits: String): Boolean {
        if (digits.length != 18) return false
        val weights = intArrayOf(3, 7, 1)
        var sum = 0
        for (i in 0 until 17) {
            sum += (digits[i].digitToInt() * weights[i % 3]) % 10
        }
        val control = (10 - sum % 10) % 10
        return control == digits[17].digitToInt()
    }
}
