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
        // Cadenas de conexion con credenciales embebidas (esquema://user:pass@host).
        Regex("""(?i)\b[a-z][a-z0-9+.-]*://[^\s:@/]+:[^\s@/]+@[^\s/]+""")
    )
}
