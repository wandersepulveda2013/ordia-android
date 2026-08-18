package com.ordia.app.intelligence

import android.util.Log
import com.ordia.app.domain.ContentModeration
import com.ordia.app.domain.ContentModeration.ModerationRule
import com.ordia.app.domain.SensitiveSecretPatterns
import java.text.Normalizer

/**
 * Puerta de seguridad que se ejecuta ANTES de cualquier proveedor de inteligencia.
 *
 * Responsabilidades:
 * 1. Filtro de privacidad (contraseñas, PIN, OTP, datos bancarios, salud, etc.)
 * 2. Contenido bloqueado (sexual, violencia, drogas, delitos) — por raíz con
 *    exenciones de contexto legítimo (c.582, ver [BLOCKED_CONTENT_RULES]).
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
     *  "código QR de la factura 2024001", "código de área 555", etc. (c.510).
     *  Se evalúa sobre texto normalizado sin tildes (ver [unaccent]) para que
     *  las formas sin acento ("codigo postal 12345") sigan excluyéndose (c.516). */
    private val CODIGO_NO_SECRETO = Regex(
        """codigo\s+(postal|de\s+barras|qr|de\s+area|de\s+fuente|de\s+producto|de\s+cliente|de\s+articulo)""",
        RegexOption.IGNORE_CASE
    )

    /** Usos de "clave" que NO son credenciales: metafóricos ("la clave del éxito"),
     *  de juego/acertijo ("la clave del juego"), o musicales ("clave musical",
     *  "clave de sol/fa/do"). Evita bloquear "la clave del éxito es practicar 100
     *  veces" o "recordar la clave de sol del acertijo 123" (c.512). Se evalúa
     *  sobre texto normalizado sin tildes (ver [unaccent]) (c.516). */
    private val CLAVE_NO_CREDENCIAL = Regex(
        """clave\s+(del?\s+exito|del?\s+juego|musical|de\s+(?:sol|fa|do|re|mi|la|si))""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Patrones de contenido bloqueado: modera el tema del que la inteligencia
     * puede ocuparse. Son legítimamente específicos de esta puerta (no forman
     * parte de [SensitiveSecretPatterns], que solo detecta secretos).
     *
     * Detección por RAÍZ con EXENCIONES de contexto legítimo (c.582). Antes se
     * casaba la raíz cruda (`matar`, `bomba`, `violar`, `droga`, `pene`...)
     * sin distinguir el sentido, lo que bloqueaba 16/17 tareas cotidianas
     * ("matar el proceso del servidor", "comprar bomba de agua", "violar la
     * política", "comprar la droga en la farmacia", "modelo de amenaza",
     * "pistola de agua") y se descartaban SILENCIOSAMENTE. Ahora [ContentModeration.isHarmful]
     * solo bloquea las OCURRENCIAS de la raíz no cubiertas por una forma legítima
     * (colocación: la coincidencia legítima envuelve a la raíz; o proximidad:
     * una señal que legitima toda la mención). El algoritmo está centralizado en
     * [ContentModeration] (paridad con [ContextPrivacyFilter], mismo principio
     * que c.299 para secretos: no pueden desincronizarse).
     *
     * Las raíces se evalúan sobre texto normalizado sin tildes (ver
     * [ContentModeration]), por lo que no hace falta duplicar formas con/sin
     * acento. `\b` evita que "mata" case dentro de "matar" — las raíces se
     * buscan como prefijo de palabra.
     */
    private val BLOCKED_CONTENT_RULES = listOf(
        // Contenido sexual explícito. "sexo"/"sexual"/"porno"/"xxx"/"desnud"
        // rara vez aparecen en tareas legítimas y son palcabras completas, así
        // que se casan sin exención. Las raíces anatómicas (pene/vagina) SÍ se
        // eximen en contexto médico (cita con el urólogo/ginecólogo por...).
        ModerationRule(
            stem = Regex("""\b(sexo|sexual|desnud|porno|xxx|eroti|culos|tetas|pene|vagina|orgasmo|masturb)\b"""),
            contain = listOf(
                Regex("""\b(cita con el ur[oó]logo|cita con la ginec[oó]loga?)\b[^.]*\bpene\b"""),
                Regex("""\b(revisi[oó]n de|revisar la|revisar el|examen de la|examen del)[^.]*\b(pene|vagina)\b""")
            ),
            proximity = Regex("""\b(ur[oó]logo|ginec[oó]log[oa]|sex[oó]logo|prostate|m[ée]dico|cl[íi]nica|farmac[ée]utic[oa])\b""")
        ),
        // Violencia y amenazas. Las raíces tienen sentidos legítimos muy
        // frecuentes en tareas técnicas ("matar el proceso", "violar la
        // política", "modelo de amenaza", "pistola/bomba de agua", "revisar el
        // secuestro de DNS") que se eximen por colocación.
        ModerationRule(
            stem = Regex("""\b(matar|asesinar|violar|secuestr|bomba|amenaza|escopeta|pistola|cuchill)\b"""),
            contain = listOf(
                Regex("""\bmatar\b\s+(el|la|los|las|un|una)?\s*(proceso|hilo|servicio|servidor|demonio|sesi[oó]n|tarea|job|zombie)\b"""),
                Regex("""\bviolar\b\s+(la|el|una|un|las|los)?\s*(pol[ií]tica|contrato|licencia|restricci[oó]n|norma|ley|clausula|cl[áa]usula|t[ée]rminos?)\b"""),
                Regex("""\b(modelo|m[oó]delo)\s+de\s+amenaza\b"""),
                Regex("""\bamenaza\b\s+(de)?\s*(de\s+integridad|de\s+seguridad|de\s+modelo)\b"""),
                Regex("""\b(revisi[oó]n|revisar|diagn[oó]stico|diag|audit|auditor[íi]a)\s+(de[l]?)\s*secuestro\b"""),
                Regex("""\bsecuestro\s+de\s+(dns|sesi[oó]n|cookie|token|sesiones?)\b"""),
                Regex("""\b(bomba|pistola|escopeta)\s+de\s+agua\b"""),
                Regex("""\b(matar|asesinar)\s+(un|el)\s+proceso\b""")
            )
        ),
        // Drogas ilegales. "droga" se exonera por proximidad médica/farmacéutica
        // (la mención entera es legítima en "comprar la droga en la farmacia",
        // "ir a buscar la droga recetada"). Las drogas específicas (cocaína,
        // marihuana...) no se eximen: su mención aislada es señal fuerte.
        ModerationRule(
            stem = Regex("""\b(droga|cocaina|heroina|marihuana|metanfetamina|narcotrafico)\b"""),
            proximity = Regex("""\b(farmac[ée]utic[oa]|farmacia|recetad[oa]|m[ée]dic[oa]|medicament[oa]|ur[oó]log[oa]|receta|tratamiento|recetar)\b""")
        ),
        // Insultos graves: palabras completas, sin exención (su mención aislada
        // es señal fuerte y rara vez legitiman una tarea).
        ModerationRule(stem = Regex("""\b(pendejo|estupido|imbecil|malparido|hijueputa)\b"""))
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

        // 1. Contenido bloqueado (moderación temática por raíz con exenciones
        //    de contexto legítimo, c.582). Solo bloquea ocurrencias de la raíz
        //    no cubiertas por una forma legítima (colocación/proximidad).
        for (rule in BLOCKED_CONTENT_RULES) {
            if (ContentModeration.isHarmful(text, rule)) {
                Log.w(TAG, "Bloqueado por contenido: ${rule.stem.pattern.take(40)}")
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
        // Normaliza tildes: en español escrito de forma casual (móvil, sin
        // autocorrector de acentos) "codigo"/"contrasena" sin tilde son formas
        // extremadamente comunes. Sin esta normalización, un OTP como "mi codigo
        // de verificacion es 1234" o "la contrasena es secreta123" NO se bloqueaba
        // y el secreto se persistía/procesaba en texto plano (c.516).
        val lower = unaccent(text.lowercase())
        // Contraseñas: palabra clave + valor adyacente (evita bloquear
        // "recuérdame cambiar mi contraseña" sin valor real, que el gate
        // canónico también deja pasar). Formas sin tilde para casar el texto
        // normalizado.
        if (credentialKeywordWithValue(lower, listOf("contrasena", "password", "pwd", "clave"))) {
            return true
        }
        // OTP / códigos de verificación. Se exige "código" + un valor numérico
        // corto (4-8 dígitos), pero se excluyen los tipos de "código" que NO son
        // secretos (postal, de barras, QR, de área, de fuente, de producto, de factura):
        // su número es un identificador público, no un OTP. Sin esta exclusión,
        // tareas como "envía el paquete al código postal 12345" o "imprime el código
        // QR de la factura 2024001" se bloqueaban injustamente (c.510).
        if (lower.contains("codigo") &&
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

    /** Quita marcas diacríticas (tildes/diacríticos) para comparación tolerante
     *  a acentos en la detección de credenciales (c.516). */
    private fun unaccent(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

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
