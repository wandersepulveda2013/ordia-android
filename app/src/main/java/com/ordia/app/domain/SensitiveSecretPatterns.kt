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
        // IBAN (ISO 13616): migrado en c.316 a `containsNumericSensitive` con validacion
        // mod-97 (checksum canonico). La regex estructural pelada producia falsos
        // positivos sobre codigos de producto/referencia largos (p.ej. "US99ABC..."),
        // bloqueando chats legitimos y perdiendo compromisos. El mod-97 elimina esos
        // falsos positivos sin perder IBANs reales (todo IBAN valido pasa mod-97).
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

    // ── Identificadores personales anclados por palabra-clave (c.313) ───────
    // INE/CURP/NSS/pasaporte/licencia/RFC son PII: su fuga en texto plano es tan
    // grave como una credencial. Pero NO son detectables por valor pelado: un
    // NSS son 11 dígitos (idéntico a un teléfono o referencia), una licencia es
    // alfanumérico corto (idéntico a un código de producto). Detectarlos por
    // valor solo generaría falsos positivos masivos y bloquearía chats legítimos
    // (pérdida de compromisos válidos).
    //
    // Solución (alineada con el principio de "palabra-clave acompañante" del
    // BACKLOG y simétrica a `otpCode`): exigir la palabra-clave canónica
    // (INE/CURP/NSS/seguro social/credencial de elector/pasaporte/licencia) en
    // una ventana corta (≤40 chars) antes del valor. "mi CURP es GOME850101..."
    // se bloquea, pero "referencia 1234567890123" no.
    //
    // CURP: 18 rígidos (4 letras + 6 dígitos + 6 alfanum + 2 dígitos).
    // NSS: 11 dígitos (mexicano). INE/credencial de elector: 12-18 alfanum.
    // Pasaporte/licencia: 6-12 alfanum (MX pasaporte: 1 letra + 8 dígitos).


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
     * Candidato IBAN (ISO 13616): codigo de pais (2 letras) + 2 digitos de control
     * + 11-30 alfanumericos (BBAN), con espacios opcionales entre grupos. La regex
     * es solo estructural; la confirmacion la hace `passesIbanMod97` (checksum
     * canonico ISO 13616), sin la cual cualquier secuencia larga "LLDD..." (codigos
     * de producto, referencias) generaria falsos positivos. Acepta mayusculas Y
     * minusculas (c.317): un IBAN en chat casual se escribe a menudo en minusculas
     * ("es66 2100...") y antes escapaba; como `passesIbanMod97` uppercasesa internamente,
     * el mod-97 filtra los falsos positivos igual que en mayusculas.
     */
    private val ibanCandidate = Regex("""\b[A-Z]{2}\s?\d{2}(?:\s?[A-Z0-9]){11,30}\b""", RegexOption.IGNORE_CASE)

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
     * Checksum canonico ISO 13616 de un IBAN: se mueven los 4 primeros caracteres
     * (pais + control) al final, las letras se convierten a su valor decimal
     * (A=10 ... Z=35) y el numero resultante mod 97 debe ser 1. `true` si [raw]
     * (con o sin espacios) es un IBAN valido. Todo IBAN real pasa este checksum,
     * asi que exigirlo no pierde cobertura y elimina los falsos positivos de
     * secuencias alfanumericas largas que solo cumplen la estructura (c.316).
     */
    private fun passesIbanMod97(raw: String): Boolean {
        val s = raw.filter { !it.isWhitespace() }.uppercase()
        if (s.length !in 15..34) return false
        if (!s.substring(0, 2).all { it.isLetter() } || !s.substring(2, 4).all { it.isDigit() }) return false
        if (!s.all { it.isLetterOrDigit() }) return false
        val rearranged = s.substring(4) + s.substring(0, 4)
        val converted = StringBuilder()
        for (ch in rearranged) {
            converted.append(if (ch.isDigit()) ch.toString() else (ch - 'A' + 10).toString())
        }
        val big = converted.toString().toBigIntegerOrNull() ?: return false
        return big % BIG_97 == BIG_ONE
    }

    private val BIG_97 = 97.toBigInteger()
    private val BIG_ONE = 1.toBigInteger()

    /**
     * `true` si [text] contiene un PAN (Luhn válido, 13-19 dígitos) o una CLABE
     * mexicana (checksum válido, 18 dígitos). Consumido por AMBOS gates de
     * privacidad para que la detección de tarjetas/cuentas sea estructuralmente
     * simétrica (c.303).
     */
    fun containsPersonalIdentifier(text: String): Boolean {
        for (kw in personalIdKeyword.findAll(text)) {
            val window = text.substring(kw.range.last + 1).take(40)
            if (nssValue.containsMatchIn(window)) return true
            if (curpValue.containsMatchIn(window)) return true
            if (kw.value.lowercase().let {
                    "ine" in it || "credencial" in it
                } && ineValue.containsMatchIn(window)) return true
            if (kw.value.lowercase().let {
                    "pasaporte" in it || "licencia" in it
                } && passportLicenceValue.containsMatchIn(window)) return true
            if (kw.value.lowercase().contains("rfc") && rfcValue.containsMatchIn(window)) return true
            // DNI/NIE espanol (c.315): 8 digitos + letra de control (modulo 23).
            // La letra se calcula y valida -> el falso positivo de un valor con
            // letra incorrecta se descarta (no es un DNI valido). Exige palabra-
            // clave "dni"/"nie"/"nif" para no bloquear secuencias casuales.
            if (kw.value.lowercase().let { "dni" in it || "nie" in it || "nif" in it } &&
                dniNieValue.containsMatchIn(window) &&
                matchesDniNieLetter(window)) return true
            // c.326: identificadores fiscales/personales de Brasil y Argentina
            // (CPF/CNPJ/CUIT/CUIL). Igual que NSS, son 11 (CUIT/CPF) o 14 (CNPJ)
            // digitos puros -> indistinguibles de una referencia o telefono por
            // valor pelado. Se exige palabra-clave canonica (cpf/cnpj/cuit/cuil)
            // + ventana corta, y el checksum modulo-11 (2 digitos verificadores)
            // es el desambiguador de precision: una secuencia con digitos de
            // control incorrectos no se bloquea. Misma forma que el DNI/NIE.
            if (kw.value.lowercase().let { "cpf" in it || "cuit" in it || "cuil" in it } &&
                cpfCuitValue.containsMatchIn(window) &&
                matchesCpfCuitChecksum(window)) return true
            if (kw.value.lowercase().contains("cnpj") &&
                cnpjValue.containsMatchIn(window) &&
                matchesCnpjChecksum(window)) return true
            // c.327: RUT (Rol Unico Tributario, Chile). 7-8 digitos + digito
            // verificador (0-9 o K) modulo-11 con la serie [2,3,4,5,6,7]. Es el
            // identificador nacional unico de Chile: aparece en toda factura,
            // contrato y chat chileno. Se exige palabra-clave "rut" + el
            // checksum es el desambiguador de precision (un valor con digito de
            // control incorrecto no se bloquea). Misma forma que DNI/CPF/CUIT.
            if (kw.value.lowercase().contains("rut") &&
                rutValue.containsMatchIn(window) &&
                matchesRutChecksum(window)) return true
        }
        return false
    }

    private val personalIdKeyword = Regex(
        """(?i)\b(?:curp|nss|ine|credencial\s+de\s+elector|n[uú]mero\s+de\s+seguro\s+social|seguro\s+social|pasaporte|licencia(?:\s+de\s+conducir)?|rfc|dni|nie|nif|cpf|cnpj|cuit|cuil|rut)\b"""
    )
    private val nssValue = Regex("""\b\d{11}\b""")
    private val curpValue = Regex("""\b[A-Z]{4}\d{6}[A-Z0-9]{6}\d{2}\b""")
    private val ineValue = Regex("""\b[A-Z0-9]{12,18}\b""")
    private val passportLicenceValue = Regex("""\b[A-Z0-9]{6,12}\b""")
    // RFC mexicano: persona moral = 3 letras + 6 dígitos + 3 homoclave (12);
    // persona física = 4 letras + 6 dígitos + 3 homoclave (13). El `\b` final
    // impide casar un substring dentro de un CURP (18 chars): tras 3 alfanum
    // el siguiente char de un CURP sigue siendo word-char → no hay boundary.
    private val rfcValue = Regex("""\b[A-Z&]{3,4}\d{6}[A-Z0-9]{3}\b""")

    // DNI/NIE espanol (c.315). DNI: 8 digitos + letra de control. NIE:
    // X/Y/Z + 7 digitos + letra (la X/Y/Z se reemplaza por 0/1/2 para el
    // calculo). La letra de control se obtiene de la tabla "TRWAGMYFPDXBNJZSQVHLCKE"
    // en la posicion (numero % 23). La validacion de la letra en
    // matchesDniNieLetter() es el desambiguador de precision: una secuencia
    // "12345678X" con letra incorrecta no es un DNI y no se bloquea. La regex
    // admite cualquier letra mayuscula y deja que el modulo-23 descarte los
    // invalidos (I, N-tilde, O, U no aparecen en la tabla).
    private val dniNieValue = Regex("""\b(?:[XYZ]\d{7}|\d{8})[A-Z]\b""")
    private val dniControlLetters = "TRWAGMYFPDXBNJZSQVHLCKE"

    /**
     * `true` si [window] contiene un DNI o NIE con la letra de control
     * correcta (modulo 23 sobre la tabla oficial espanola). La validacion de
     * la letra es el desambiguador de precision: una secuencia "12345678X"
     * con letra incorrecta no es un DNI y no se bloquea.
     */
    private fun matchesDniNieLetter(window: String): Boolean {
        return dniNieValue.findAll(window).any { match ->
            val raw = match.value
            val (digitsPart, letter) = if (raw[0].isDigit()) {
                raw.dropLast(1) to raw.last()
            } else {
                // NIE: reemplaza X->0, Y->1, Z->2 antes de calcular.
                val prefix = when (raw[0].uppercaseChar()) {
                    'X' -> '0'; 'Y' -> '1'; 'Z' -> '2'; else -> raw[0]
                }
                (prefix + raw.substring(1, raw.length - 1)) to raw.last()
            }
            val number = digitsPart.toLongOrNull() ?: return@any false
            dniControlLetters[(number % 23).toInt()] == letter.uppercaseChar()
        }
    }

    // ── CPF/CNPJ (Brasil) y CUIT/CUIL (Argentina) (c.326) ───────────────────
    // CPF: 11 digitos (9 base + 2 verificadores). CNPJ: 14 digitos (12 base +
    // 2 verificadores). CUIT/CUIL: 11 digitos (2 prefijo + 8 base + 1 verif).
    // Todos usan modulo-11 con ponderaciones descendentes. El 2do verificador
    // del CPF/CNPJ recalcula incluyendo el 1ro. El CUIT pondera con la tabla
    // fija [5,4,3,2,7,6,5,4,3,2] y su caso especial: residuo 1 -> digito 9.
    // La regex admite separadores `.` `-` `/` (formatos canonico de cada pais)
    // y deja que el checksum descarte los invalidos. Se ancla por palabra-clave.
    private val cpfCuitValue = Regex("""\b(?:\d[.\-]?){10}\d\b""")
    private val cnpjValue = Regex("""\b(?:\d[.\-/?]?){13}\d\b""")

    /**
     * `true` si [window] contiene un CPF (Brasil) o CUIT/CUIL (Argentina) de
     * 11 digitos con sus digitos verificadores modulo-11 correctos. La
     * validacion del checksum es el desambiguador de precision: una secuencia
     * de 11 digitos con verificadores incorrectos (p.ej. una referencia) no
     * se bloquea. CPF y CUIT comparten longitud (11) pero difieren en el
     * algoritmo: CPF tiene 2 verificadores consecutivos, CUIT tiene 1 (con su
     * caso especial residuo 1 -> 9). Se acepta si CUALQUIERA pasa.
     */
    private fun matchesCpfCuitChecksum(window: String): Boolean {
        return cpfCuitValue.findAll(window).any { match ->
            val digits = match.value.filter(Char::isDigit)
            digits.length == 11 && (passesCpfChecksum(digits) || passesCuitChecksum(digits))
        }
    }

    /**
     * `true` si [window] contiene un CNPJ (Brasil) de 14 digitos con sus 2
     * digitos verificadores modulo-11 correctos.
     */
    private fun matchesCnpjChecksum(window: String): Boolean {
        return cnpjValue.findAll(window).any { match ->
            val digits = match.value.filter(Char::isDigit)
            digits.length == 14 && passesCnpjChecksum(digits)
        }
    }

    /** CPF (Brasil): 2 digitos verificadores modulo-11 con ponderacion descendente. */
    private fun passesCpfChecksum(digits: String): Boolean {
        if (digits.all { it == digits[0] }) return false // CPFs all-iguales son invalidos por ley
        fun dv(slice: String): Int {
            var sum = 0
            for (i in slice.indices) sum += slice[i].digitToInt() * (slice.length + 1 - i)
            val r = sum % 11
            return if (r < 2) 0 else 11 - r
        }
        val d1 = dv(digits.substring(0, 9))
        val d2 = dv(digits.substring(0, 9) + d1.toString())
        return d1 == digits[9].digitToInt() && d2 == digits[10].digitToInt()
    }

    /** CUIT/CUIL (Argentina): 1 digito verificador, tabla fija [5,4,3,2,7,6,5,4,3,2], residuo 1 -> 9. */
    private fun passesCuitChecksum(digits: String): Boolean {
        val weights = intArrayOf(5, 4, 3, 2, 7, 6, 5, 4, 3, 2)
        var sum = 0
        for (i in 0 until 10) sum += digits[i].digitToInt() * weights[i]
        val dv = when (sum % 11) {
            0 -> 0
            1 -> 9
            else -> 11 - (sum % 11)
        }
        return dv == digits[10].digitToInt()
    }

    /** CNPJ (Brasil): 2 digitos verificadores modulo-11 con tablas [5..2] y [6..2]. */
    private fun passesCnpjChecksum(digits: String): Boolean {
        if (digits.all { it == digits[0] }) return false
        val w1 = intArrayOf(5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        val w2 = intArrayOf(6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        fun dv(slice: String, weights: IntArray): Int {
            var sum = 0
            for (i in slice.indices) sum += slice[i].digitToInt() * weights[i]
            val r = sum % 11
            return if (r < 2) 0 else 11 - r
        }
        val d1 = dv(digits.substring(0, 12), w1)
        val d2 = dv(digits.substring(0, 12) + d1.toString(), w2)
        return d1 == digits[12].digitToInt() && d2 == digits[13].digitToInt()
    }

    // â”€â”€ RUT (Rol Unico Tributario, Chile) (c.327) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // 7-8 digitos base + 1 digito verificador (0-9 o K), modulo-11 con la serie
    // [2,3,4,5,6,7] aplicada de derecha a izquierda. Formatos: "12.345.678-5",
    // "12345678-5", "123456785", "7.654.321-6", "76543216". La regex admite
    // puntos (miles) y guion opcional antes del verificador; deja que el checksum
    // descarte los invalidos. Se ancla por palabra-clave "rut".
    private val rutValue = Regex("""\b(?:\d{1,3}(?:\.\d{3}){1,2}|\d{7,8})[-]?[0-9Kk]\b""")

    /**
     * `true` si [window] contiene un RUT chileno de 7-8 digitos con su digito
     * verificador modulo-11 correcto. La validacion del checksum es el
     * desambiguador de precision: una secuencia con verificador incorrecto
     * (p.ej. una referencia) no se bloquea. A diferencia de CPF/CNPJ, un RUT
     * "11.111.111-1" (todos iguales) es valido -> no hay guarda anti-all-iguales.
     */
    private fun matchesRutChecksum(window: String): Boolean {
        return rutValue.findAll(window).any { match ->
            val raw = match.value
            val dvChar = raw.last().uppercaseChar()
            val base = raw.dropLast(1).filter(Char::isDigit)
            base.length in 7..8 && passesRutChecksum(base, dvChar)
        }
    }

    /** RUT (Chile): serie [2,3,4,5,6,7] derecha-a-izquierda; residuo 10 -> K, 11 -> 0. */
    private fun passesRutChecksum(base: String, dvChar: Char): Boolean {
        val series = intArrayOf(2, 3, 4, 5, 6, 7)
        var sum = 0
        for (i in base.indices) {
            sum += base[base.length - 1 - i].digitToInt() * series[i % 6]
        }
        val r = 11 - (sum % 11)
        val expected = when (r) {
            11 -> '0'
            10 -> 'K'
            else -> r.digitToChar()
        }
        return expected == dvChar
    }

    /**
     * `true` si [text] contiene un PAN (Luhn valido, 13-19 digitos), una CLABE
     * mexicana (checksum valido, 18 digitos) o un IBAN (ISO 13616, mod-97 valido).
     * Consumido por AMBOS gates de privacidad para que la deteccion de
     * tarjetas/cuentas sea estructuralmente simetrica (c.303; IBAN anadido en c.316).
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
        if (ibanCandidate.findAll(text).any { passesIbanMod97(it.value) }) return true
        return false
    }
}
