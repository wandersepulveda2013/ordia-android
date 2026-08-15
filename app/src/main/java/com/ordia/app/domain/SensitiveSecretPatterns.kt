package com.ordia.app.domain

/**
 * Fuente unica y compartida de los patrones regex de credenciales/secretos de
 * infraestructura y nube que AMBOS gates de privacidad
 * ([`ConversationPrivacyPolicy`][com.ordia.app.conversations.CommitmentEngine.ConversationPrivacyPolicy]
 * para persistencia y [`ContextPrivacyFilter`][com.ordia.app.context.ContextPrivacyFilter]
 * para lectura de contexto) deben bloquear.
 *
 * Causa raiz permanente cerrada (c.299): durante c.287-c.298 dos listas de patrones
 * mantenidas a mano en gates distintos se desincronizaron 7 veces y dejaron escapar
 * secretos de produccion (claves privadas, SSH, API keys, AWS, JWT, Google, Slack,
 * GitHub/GitLab PATs, cadenas de conexion) que se persistian en texto plano. Al
 * centralizarlos aqui, anadir un nuevo tipo de credencial a un solo sitio actualiza
 * AMBOS gates a la vez: la paridad es estructural, no manual.
 *
 * Solo alberga patrones de CREDENCIAL/SECRETO cuyo valor es exactamente el mismo
 * en ambos gates (byte-a-byte). Las listas de PALABRAS sensibles (contrasena, 2fa,
 * cuenta, saldo, etc.) se mantienen por gate porque difieren a proposito: el gate
 * de persistencia es mas preciso (evita bloquear chats legitimos con "clave del
 * exito") y el de lectura anade categorias de contenido (adultos, violencia,
 * politica) que no deben frenar la persistencia de un compromiso.
 */
object SensitiveSecretPatterns {

    /**
     * Patrones canonicos de credenciales/secretos compartidos por ambos gates.
     * Orden irrelevante para la deteccion (cualquier coincidencia bloquea).
     */
    val patterns: List<Regex> = listOf(
        // Clave privada en bloque PEM (OpenSSL/SSH): `-----BEGIN ... PRIVATE KEY-----`.
        Regex("""-----BEGIN [A-Z ]*PRIVATE KEY-----""", RegexOption.IGNORE_CASE),
        // Clave cripto/secreto de 64 hex (con o sin prefijo 0x): wallet, API secret, hash.
        Regex("""\b(?:0x)?[0-9a-f]{64}\b""", RegexOption.IGNORE_CASE),
        // IBAN estructural (ISO 13616): 2 letras + 2 digitos + 11-30 alfanumericos.
        Regex("""\b[A-Z]{2}\s?\d{2}(?:\s?[A-Z0-9]){11,30}\b"""),
        // Clave SSH publica (rsa|dsa|ecdsa|ed25519) + blob base64.
        Regex("""\bssh-(?:rsa|dsa|ecdsa|ed25519)\s+[A-Za-z0-9+/]{20,}={0,2}"""),
        // API keys tipo Stripe/OpenAI (sk-, sk_live_, sk_test_ + 20+ alfanum).
        Regex("""(?i)\bsk[-_](?:live[-_]|test[-_])?[A-Za-z0-9]{20,}"""),
        // AWS access key IDs (prefijo canonico + 16 base32).
        Regex("""\b(?:AKIA|ASIA|AGPA|AIDA|AROA|AIPA|ANPA|ANVA|ABIA|ACCA)[0-9A-Z]{16}\b"""),
        // JWT: 3 segmentos base64url separados por punto (eyJ = {" decodificado).
        Regex("""\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}"""),
        // Google API key (AIza + 35 base64url).
        Regex("""\bAIza[0-9A-Za-z_-]{35}\b"""),
        // Slack tokens (xoxa- app / xoxb- bot / xoxp- user + 20+).
        Regex("""\bxox[abp]-[0-9A-Za-z-]{20,}\b"""),
        // GitHub PATs (ghp/gho/ghu/ghs/ghr/github_pat + 20+).
        Regex("""\b(?:ghp|gho|ghu|ghs|ghr|github_pat)_[A-Za-z0-9_]{20,}\b"""),
        // GitLab PATs (glpat- + 20+).
        Regex("""\bglpat-[A-Za-z0-9_-]{20,}\b"""),
        // c.300: Stripe restricted keys (rk_live_/rk_test_ + 20+). Distintos de las
        // sk- (c.295): las "restricted" empiezan por rk_ y tienen permisos acotados
        // pero siguen siendo credenciales de cobro. Prefijo canonico, bajo falso positivo.
        Regex("""\brk_(?:live|test)_[A-Za-z0-9]{20,}"""),
        // c.300: Azure storage account keys embebidos en connection strings
        // (`AccountKey=` + base64 largo 50+). Los comparten equipos devops por
        // mensajeria; la clave `AccountKey=` es la sennal de credencial de storage
        // (blobs/tables/queues de produccion). 50+ evita falsos positivos cortos.
        Regex("""\bAccountKey=[A-Za-z0-9+/=]{50,}"""),
        // c.300: Mailgun API keys (`key-` + 32 hex). Prefijo canonico `key-`
        // + cuerpo hex de 32 (formato historico de Mailgun). Bajo falso positivo:
        // `key-` seguido de 32 hex no ocurre en texto conversacional normal.
        Regex("""\bkey-[a-f0-9]{32}\b"""),
        // c.302: SendGrid API keys (`SG.` + 16+ base64url + `.` + 16+ base64url).
        // Prefijo canonico `SG.` distintivo de SendGrid (envio de email
        // transaccional/marketing). El patron de 2 segmentos separados por punto,
        // ambos de 16+ alfanum, no ocurre en texto conversacional normal -> bajo
        // falso positivo. Las keys reales son `SG.` + 22 + `.` + 43; 16+ admite
        // variantes antiguas sin perder distintividad.
        Regex("""\bSG\.[A-Za-z0-9_-]{16,}\.[A-Za-z0-9_-]{16,}"""),
        // c.302: Square access tokens (`sq0atp-` produccion / `sq0csp-` sandbox
        // + 40+ alfanum). Prefijo canonico de Square (procesamiento de pagos).
        // `sq0` + `atp`/`csp` + `-` + cuerpo largo no ocurre en texto normal.
        Regex("""\bsq0(?:atp|csp)-[A-Za-z0-9_]{40,}"""),
        // c.302: Twilio API Key SID (`SK` + 32 hex) y Account SID (`AC` + 32 hex).
        // Prefijo de 2 letras (`SK`/`AC`) + 32 hex consecutivos. La longitud
        // exacta (34 total) y el alfabeto hex puro evitan falsos positivos: 32
        // hex seguidos no aparecen en palabras espanolas tras `SK`/`AC`. El
        // grupo `[SA][CK]` cubre ambos prefijos. Comprobado en probe JVM.
        Regex("""\b[SA][CK][0-9a-f]{32}\b""", RegexOption.IGNORE_CASE),
        // c.302: PubNub subscribe/publish keys (`sub-c-`/`pub-c-` + UUID canonico).
        // Prefijo `sub`/`pub` + `-c-` + UUID (8-4-4-4-12 hex). Distintivo de PubNub
        // (mensajeria realtime). No ocurre en texto conversacional normal.
        Regex("""\b(?:sub|pub)-c-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b"""),
        // Cadenas de conexion con credenciales embebidas (esquema://user:pass@host).
        Regex("""(?i)\b[a-z][a-z0-9+.-]*://[^\s:@/]+:[^\s@/]+@[^\s/]+""")
    )

    // ── Numéricos sensibles validados por checksum (c.303) ──────────────────
    // PAN y CLABE no son detectables por un Regex plano: su naturaleza depende
    // de un dígito verificador (Luhn para tarjetas, ponderación 3-7-1 para la
    // CLABE mexicana). Antes, el gate de persistencia usaba un patrón crudo
    // `\b(?:\d[ -]?){13,19}\b` que bloqueaba CUALQUIER secuencia larga de
    // dígitos (IMEI, número de factura, referencia de 19 dígitos, teléfono con
    // prefijo internacional) mientras el gate de lectura exigía Luhn. Esa
    // asimetría causaba (a) falsos positivos en persistencia → pérdida de
    // compromisos legítimos y (b) divergencia entre gates. Al centralizar la
    // detección numérica validada aquí, ambos gates bloquean exactamente lo
    // mismo: un PAN real (Luhn) o una CLABE real (checksum propio), y dejan
    // pasar secuencias largas que no son ninguna de las dos.

    /** Candidato PAN: 13-19 dígitos con posibles separadores ` ` o `-`. */
    private val panCandidate = Regex("""(?<!\d)(?:\d[ -]?){12,18}\d(?!\d)""")

    /** Candidato CLABE: 18 dígitos con posibles separadores. */
    private val clabeCandidate = Regex("""(?<!\d)(?:\d[ -]?){17}\d(?!\d)""")

    /**
     * Dígito verificador Luhn (ISO/IEC 7812). `true` si [digits] (sólo dígitos)
     * forma un número de tarjeta válido. Un PAN real siempre pasa Luhn: es el
     * checksum que lo define, así que exigirlo no pierde cobertura de tarjetas
     * reales y sí elimina los falsos positivos de secuencias largas arbitrarias.
     */
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
     * Dígito verificador de la CLABE interbancaria mexicana (18 dígitos): los
     * primeros 17 se ponderan cíclicamente con (3, 7, 1), se suman mod 10 y el
     * dígito de control es (10 - (suma mod 10)) mod 10. Debe coincidir con el
     * dígito 18. No es Luhn, por lo que requiere validación propia.
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

    /**
     * `true` si [text] contiene un PAN (Luhn válido, 13-19 dígitos) o una CLABE
     * mexicana (checksum válido, 18 dígitos). Consumido por AMBOS gates de
     * privacidad para que la detección de tarjetas/cuentas sea estructuralmente
     * simétrica (c.303).
     */
    fun containsNumericSensitive(text: String): Boolean {
        if (panCandidate.findAll(text).any { c ->
                val d = c.value.filter(Char::isDigit)
                d.length in 13..19 && passesLuhn(d)
            }) return true
        if (clabeCandidate.findAll(text).any { c ->
                val d = c.value.filter(Char::isDigit)
                d.length == 18 && passesClabeChecksum(d)
            }) return true
        return false
    }
}
