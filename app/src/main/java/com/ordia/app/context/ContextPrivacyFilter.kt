package com.ordia.app.context

import com.ordia.app.domain.ContentModeration
import com.ordia.app.domain.ContentModeration.ModerationRule
import com.ordia.app.domain.SensitiveSecretPatterns

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
        // c.299: credenciales/secretos de infraestructura y nube (claves PEM,
        // hex 64, IBAN estructural, SSH, API keys, AWS, JWT, Google, Slack,
        // GitHub/GitLab PATs, cadenas de conexion) movidos a la fuente unica
        // `domain.SensitiveSecretPatterns` para que persistencia y lectura no
        // puedan desincronizarse (causa raiz de las 7 fugas c.287-c.298). Se
        // consumen en `containsSensitiveContent` mas abajo. Las categorias de
        // contenido (adultos, violencia, politica) siguen aqui, propias del gate
        // de lectura: no aplican a la persistencia de un compromiso.
        Regex("""\b(acoso|hostigamiento|extorsión|extorsion|chantaje)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(suicidi|autolesión|autolesion|hacerme daño|hacerme dano|quitarme la vida)\b""", RegexOption.IGNORE_CASE),
        // c.1198: la fuga plural era una rendija del ancla `\b` (la coda 's'
        // rompe la frontera) — «transferencias» entraba aunque el singular se
        // bloqueaba. Anti-overreach c.1029: preferir NO capturar a persistir
        // contenido financiero sensible.
        Regex("""\b(transferencias?|depósitos?|depositos?|retiros?|saldos?|estados? de cuenta)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(partido político|partido politico|elección|eleccion|campaña política|campana politica|votar por)\b""", RegexOption.IGNORE_CASE)
    )

    /** Reglas de moderación temática (sexual/violencia/drogas) con exenciones
     *  de contexto legítimo (c.582). Algoritmo centralizado en [ContentModeration],
     *  paridad con [IntelligenceSafetyGate]: añadir una exención o sentido legítimo
     *  en un solo sitio corrige ambos gates a la vez (mismo principio que c.299).
     *  Las raíces se evalúan sobre texto normalizado sin tildes (no hace falta
     *  duplicar formas con/sin acento). */
    private val moderationRules = listOf(
        ModerationRule(
            stem = Regex("""\b(sexo|sexual|desnud|porno|xxx|eroti|intimidad)\b"""),
            contain = listOf(
                Regex("""\b(revisi[oó]n de|revisar la|revisar el|examen de la|examen del)[^.]*\b(pene|vagina)\b""")
            ),
            proximity = Regex("""\b(ur[oó]logo|ginec[oó]log[oa]|sex[oó]logo|m[ée]dico|cl[íi]nica|farmac[ée]utic[oa])\b""")
        ),
        ModerationRule(
            stem = Regex("""\b(matar|asesinar|violar|robar|secuestr|bomba|arma|amenaza)\b"""),
            contain = listOf(
                Regex("""\bmatar\b\s+(el|la|los|las|un|una)?\s*(proceso|hilo|servicio|servidor|demonio|sesi[oó]n|tarea|job|zombie)\b"""),
                Regex("""\bviolar\b\s+(la|el|una|un|las|los)?\s*(pol[ií]tica|contrato|licencia|restricci[oó]n|norma|ley|cl[áa]usula|t[ée]rminos?)\b"""),
                Regex("""\b(modelo|m[oó]delo)\s+de\s+amenaza\b"""),
                Regex("""\bamenaza\b\s+(de)?\s*(de\s+integridad|de\s+seguridad|de\s+modelo)\b"""),
                Regex("""\b(revisi[oó]n|revisar|diagn[oó]stico|diag|audit|auditor[íi]a)\s+(de[l]?)\s*secuestro\b"""),
                Regex("""\bsecuestro\s+de\s+(dns|sesi[oó]n|cookie|token|sesiones?)\b"""),
                Regex("""\b(bomba|pistola|escopeta)\s+de\s+agua\b""")
            )
        ),
        ModerationRule(
            stem = Regex("""\b(droga|cocaina|heroina|marihuana|metanfetamina|narcotrafico)\b"""),
            proximity = Regex("""\b(farmac[ée]utic[oa]|farmacia|recetad[oa]|m[ée]dic[oa]|medicament[oa]|ur[oó]log[oa]|receta|tratamiento|recetar)\b""")
        )
    )

    private val sensitiveInputTypes = setOf(
        "password", "textPassword", "textVisiblePassword", "textWebEditTextPassword",
        "numberPassword", "date", "time"
    )

    private val sensitiveMetadataCue = Regex(
        """\b(password|passwd|pin|otp|2fa|cvv|cvc|card.?number|credit.?card|private.?key|seed.?phrase|recovery.?phrase|contraseña|contrasena|clave|tarjeta)\b""",
        RegexOption.IGNORE_CASE
    )

    private val numericSecret = Regex("""^\s*\d{4,8}\s*$""")
    private val shortNumericSecret = Regex("""^\s*\d{3,4}\s*$""")

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
        if (moderationRules.any { ContentModeration.isHarmful(text, it) }) return true
        if (SensitiveSecretPatterns.patterns.any { it.containsMatchIn(text) }) return true
        if (SensitiveSecretPatterns.containsNumericSensitive(text)) return true
        if (SensitiveSecretPatterns.containsPersonalIdentifier(text)) return true
        if (numericSecret.matches(text)) return true
        if (metadata["inputClass"].equals("number", ignoreCase = true) && shortNumericSecret.matches(text)) return true
        return false
    }

    fun isPackageBlocked(packageName: String): Boolean {
        val normalized = packageName.lowercase()
        return blockedPackagePrefixes.any { normalized.startsWith(it.lowercase()) } ||
            blockedPackageFragments.any { it in normalized }
    }

    fun isSensitiveInputType(inputType: String): Boolean =
        sensitiveInputTypes.any { it.equals(inputType, ignoreCase = true) }

}
