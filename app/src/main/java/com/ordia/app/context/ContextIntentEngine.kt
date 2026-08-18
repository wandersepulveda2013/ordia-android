package com.ordia.app.context

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.UUID

/**
 * Motor de clasificación de intenciones organizativas basado en reglas/regex.
 *
 * DEPRECATED: Usar [com.ordia.app.intelligence.OrdiaIntelligenceEngine] en su lugar.
 * Este motor se mantiene como backend del [com.ordia.app.intelligence.BasicRuleProvider]
 * para el modo de reglas (fallback cuando el modelo local no está disponible).
 *
 * Flujo original:
 * 1. Filtro de privacidad (descarta contenido sensible)
 * 2. Detección de contenido bloqueado (sexual, violencia, etc.)
 * 3. Clasificación por intención permitida (allow-list)
 * 4. Extracción de fecha/hora
 * 5. Cálculo de confianza
 * 6. Generación de ContextIntent
 *
 * Nunca almacena el texto original después del análisis.
 */
@Deprecated("Usar OrdiaIntelligenceEngine via BasicRuleProvider")
object ContextIntentEngine {

    /** Confianza mínima para considerar una intención como válida */
    private const val MINIMUM_CONFIDENCE = 0.45f

    /** Palabras sin intención organizativa */
    private val CHAT_WORDS = setOf(
        "hola", "buenos días", "buenas tardes", "buenas noches", "cómo estás",
        "bien", "gracias", "ok", "okey", "vale", "sí", "no", "jeje", "jaja",
        "lol", "q tal", "qué tal", "bienvenido", "adiós", "bye", "chao",
        "nos vemos", "luego", "después", "hablamos", "x", "ok", "okis"
    )

    /** Palabras de baja confianza que indican conversación casual */
    private val LOW_CONFIDENCE_WORDS = setOf(
        "amor", "cariño", "corazón", "bebé", "hermoso", "lindo",
        "bonito", "te quiero", "te amo", "te extraño", "te adoro"
    )

    /**
     * Analiza un evento contextual y retorna una intención clasificada,
     * o null si el contenido debe descartarse.
     *
     * El texto original se descarta inmediatamente después del análisis.
     */
    fun analyze(event: ContextEvent): ContextIntent? {
        // 1. Filtro de privacidad
        if (ContextPrivacyFilter.shouldBlock(event)) return null

        val text = event.safeText.trim()
        if (text.length < MIN_TEXT_LENGTH) return null

        val lower = text.lowercase(Locale.ROOT)

        // 2. Verificar que no sea solo conversación casual
        if (isCasualChat(lower)) return null

        // 3. Verificar contenido bloqueado
        if (containsBlockedContent(lower)) return null

        // 4. Clasificar intención
        val (kind, confidence, extractedTitle) = classify(lower, text) ?: return null
        if (confidence < MINIMUM_CONFIDENCE) return null

        // 5. Extraer fecha/hora
        val dueAt = extractDateTime(lower)

        // 6. Generar título descriptivo
        val title = extractedTitle ?: generateTitle(text, kind)

        return ContextIntent(
            id = UUID.randomUUID().toString(),
            kind = kind,
            title = title,
            dueAt = dueAt,
            confidence = confidence,
            source = event.source,
            sourcePackage = event.sourcePackage
        )
    }

    /**
     * Clasifica el texto en una intención organizativa.
     * Retorna el tipo, confianza y título extraído, o null si no hay coincidencia.
     */
    private fun classify(lower: String, original: String): Triple<ContextIntentKind, Float, String?>? {
        val matches = mutableListOf<Pair<ContextIntentKind, Float>>()

        for (kind in ContextIntentKind.entries) {
            val score = scoreKind(lower, kind)
            if (score > 0f) {
                matches.add(kind to score)
            }
        }

        if (matches.isEmpty()) return null

        // Tomar la mejor coincidencia
        val best = matches.maxByOrNull { it.second } ?: return null

        // Extraer título
        val title = extractTitle(original, best.first)

        return Triple(best.first, best.second, title)
    }

    /**
     * Puntúa qué tan bien coincide el texto con una categoría de intención.
     */
    private fun scoreKind(lower: String, kind: ContextIntentKind): Float {
        var score = 0f
        val words = lower.split(Regex("\\s+"))

        // Palabras clave directas
        for (keyword in kind.keywords) {
            if (lower.contains(keyword, ignoreCase = true)) {
                score += KEYWORD_WEIGHT
            }
        }

        // Patrones específicos por tipo
        score += scoreSpecificPatterns(lower, kind)

        // Bonos contextuales
        score += scoreContextualBonus(lower, kind)

        // Penalización por ambigüedad
        score -= scoreAmbiguityPenalty(lower, kind)

        return score.coerceIn(0f, 1f)
    }

    /**
     * Patrones específicos por tipo de intención.
     */
    private fun scoreSpecificPatterns(lower: String, kind: ContextIntentKind): Float {
        return when (kind) {
            ContextIntentKind.TASK -> {
                var s = 0f
                // "tengo que + verbo"
                if (Regex("""tengo (que|q) \w+""").containsMatchIn(lower)) s += 0.15f
                // "hay que + verbo"
                if (Regex("""hay que \w+""").containsMatchIn(lower)) s += 0.15f
                // "recuérdame + verbo"
                if (Regex("""recuérdame \w+""").containsMatchIn(lower)) s += 0.2f
                // "no olvides + verbo"
                if (Regex("""no olvides \w+""").containsMatchIn(lower)) s += 0.2f
                s
            }
            ContextIntentKind.SHOPPING -> {
                var s = 0f
                if (Regex("""(ir|voy|iremos|vamos|iré) (a |al |a la |a los )?(super|mercad|tiend|farm)""").containsMatchIn(lower)) s += 0.25f
                if (Regex("""comprar \w+""").containsMatchIn(lower)) s += 0.15f
                if (lower.contains("leche") || lower.contains("pan") || lower.contains("huevo")
                    || lower.contains("detergente") || lower.contains("verduras")
                    || lower.contains("fruta") || lower.contains("carne")) s += 0.1f
                s
            }
            ContextIntentKind.APPOINTMENT -> {
                var s = 0f
                if (Regex("""cita (con|médica|del|con el|con la)""").containsMatchIn(lower)) s += 0.25f
                if (Regex("""(dentista|doctor|médico|especialista|consulta|revisión|chequeo|terapia)""").containsMatchIn(lower)) s += 0.2f
                s
            }
            ContextIntentKind.MEETING -> {
                var s = 0f
                if (Regex("""(quedar|vernos|quedamos|encuentro) (con|en|a las)""").containsMatchIn(lower)) s += 0.2f
                if (Regex("""reunión (con|de|del)""").containsMatchIn(lower)) s += 0.2f
                if (lower.contains("nos vemos")) s += 0.1f
                s
            }
            ContextIntentKind.STUDY -> {
                var s = 0f
                if (Regex("""(estudiar|estudio|examen|repasar|preparar)""").containsMatchIn(lower)) s += 0.15f
                if (lower.contains("auditoría") || lower.contains("examen") || lower.contains("clase")) s += 0.1f
                s
            }
            ContextIntentKind.DEADLINE -> {
                var s = 0f
                if (Regex("""(entregar|debo entregar|tengo que entregar)""").containsMatchIn(lower)) s += 0.2f
                if (Regex("""(el|lunes|martes|miércoles|jueves|viernes) (entrego|entrega|entregan)""").containsMatchIn(lower)) s += 0.15f
                s
            }
            ContextIntentKind.CALL -> {
                var s = 0f
                if (Regex("""llamar (a|por teléfono)""").containsMatchIn(lower)) s += 0.2f
                if (Regex("""hablar (con|por teléfono)""").containsMatchIn(lower)) s += 0.15f
                s
            }
            ContextIntentKind.EXERCISE -> {
                var s = 0f
                if (Regex("""(gimnasio|entrenar|yoga|correr|natación)""").containsMatchIn(lower)) s += 0.2f
                if (Regex("""ir al gimnasio""").containsMatchIn(lower)) s += 0.15f
                s
            }
            ContextIntentKind.REMINDER -> {
                var s = 0f
                if (Regex("""(recuérdame|avísame|notifícame|acordarme)""").containsMatchIn(lower)) s += 0.25f
                s
            }
            ContextIntentKind.PAYMENT -> {
                var s = 0f
                if (Regex("""pagar (el|la|los|las)""").containsMatchIn(lower)) s += 0.2f
                if (lower.contains("factura") || lower.contains("recibo") || lower.contains("internet")
                    || lower.contains("luz") || lower.contains("agua") || lower.contains("teléfono")) s += 0.1f
                s
            }
            ContextIntentKind.COMMITMENT_PERSONAL -> {
                var s = 0f
                if (Regex("""(pienso|planeo|quiero|voy a|intentaré)""").containsMatchIn(lower)) s += 0.1f
                s
            }
            ContextIntentKind.HOUSEHOLD -> {
                var s = 0f
                if (Regex("""(limpiar|ordenar|cocinar|lavar|planchar|arreglar|reparar|jardín)""").containsMatchIn(lower)) s += 0.15f
                s
            }
            else -> 0f
        }
    }

    /**
     * Bonos contextuales que aumentan la confianza.
     */
    private fun scoreContextualBonus(lower: String, kind: ContextIntentKind): Float {
        var bonus = 0f

        // Presencia de fecha aumenta confianza en cualquier intención
        if (hasDateReference(lower)) bonus += 0.1f

        // Presencia de hora
        if (hasTimeReference(lower)) bonus += 0.08f

        // Verbos en futuro
        if (Regex("""(iré|voy|vamos|iremos|haré|compraré|llamaré|estudiaré|entregaré)""").containsMatchIn(lower)) {
            bonus += 0.08f
        }

        return bonus
    }

    /**
     * Penalización por ambigüedad.
     */
    private fun scoreAmbiguityPenalty(lower: String, kind: ContextIntentKind): Float {
        var penalty = 0f

        // Muchas palabras de conversación casual
        val chatCount = CHAT_WORDS.count { lower.contains(it) }
        if (chatCount > 2) penalty += 0.15f
        if (chatCount > 4) penalty += 0.2f

        // Palabras románticas/íntimas sin intención organizativa
        val lowCount = LOW_CONFIDENCE_WORDS.count { lower.contains(it) }
        if (lowCount > 0) penalty += 0.2f * lowCount

        // Ambigüedad: "voy a" puede ser "voy a hacer" (tarea) o "voy a tu casa" (casual)
        if (kind == ContextIntentKind.COMMITMENT_PERSONAL && lower.length < 20) penalty += 0.15f

        return penalty
    }

    /**
     * Verifica si el texto es solo conversación casual.
     */
    private fun isCasualChat(lower: String): Boolean {
        val words = lower.split(Regex("\\s+")).filter { it.length > 2 }
        if (words.isEmpty()) return true

        val chatRatio = words.count { it in CHAT_WORDS }.toFloat() / words.size
        return chatRatio > 0.6f
    }

    /**
     * Verifica contenido explícitamente bloqueado.
     */
    private fun containsBlockedContent(lower: String): Boolean {
        // Contenido sexual
        if (Regex("""\b(sexo|sexual|desnud|porno|xxx|eróti|intimidad|culos|tetas|pene|vagina|orgasmo|masturb)""").containsMatchIn(lower)) {
            return true
        }
        // Violencia y amenazas
        if (Regex("""\b(matar|asesinar|violar|secuestr|bomba|amenaza|escopeta|pistola|cuchill)""").containsMatchIn(lower)) {
            return true
        }
        // Drogas
        if (Regex("""\b(droga|cocaína|marihuana|heroína|metanfetamina|narcotráfico|porro|weed)""").containsMatchIn(lower)) {
            return true
        }
        // Insultos graves
        if (Regex("""\b(pendejo|estúpido|imbécil|idiota|malparido|hijueputa|culero)""").containsMatchIn(lower)) {
            return true
        }
        return false
    }

    /**
     * Extrae el título del texto original.
     */
    private fun extractTitle(original: String, kind: ContextIntentKind): String? {
        val lower = original.lowercase(Locale.ROOT)

        return when (kind) {
            ContextIntentKind.TASK -> {
                // "tengo que X" → "X"
                val match = Regex("""tengo (que|q) (.+)""", RegexOption.IGNORE_CASE).find(original)
                if (match != null) return capitalizeFirst(match.groupValues[2])

                // "recuérdame X" → "X"
                val match2 = Regex("""recuérdame (.+)""", RegexOption.IGNORE_CASE).find(original)
                if (match2 != null) return capitalizeFirst(match2.groupValues[1])

                // "no olvides X" → "X"
                val match3 = Regex("""no olvides (.+)""", RegexOption.IGNORE_CASE).find(original)
                if (match3 != null) return capitalizeFirst(match3.groupValues[1])

                // "hay que X" → "X"
                val match4 = Regex("""hay que (.+)""", RegexOption.IGNORE_CASE).find(original)
                if (match4 != null) return capitalizeFirst(match4.groupValues[1])

                null
            }
            ContextIntentKind.SHOPPING -> {
                // "comprar X" → "Comprar X"
                val match = Regex("""comprar (.+)""", RegexOption.IGNORE_CASE).find(original)
                if (match != null) return "Comprar ${capitalizeFirst(match.groupValues[1])}"

                // "ir al supermercado" → "Ir al supermercado"
                val match2 = Regex("""(ir|vamos|voy|iremos) (.+)""", RegexOption.IGNORE_CASE).find(original)
                if (match2 != null) return "Ir ${capitalizeFirst(match2.groupValues[2])}"

                null
            }
            ContextIntentKind.APPOINTMENT -> {
                val match = Regex("""(tengo|cita|voy a|debo ir a) (.+)""", RegexOption.IGNORE_CASE).find(original)
                if (match != null) return "Cita: ${capitalizeFirst(match.groupValues[2])}"
                null
            }
            ContextIntentKind.MEETING -> {
                val match = Regex("""(reunión|quedar|vernos|quedamos) (.+)""", RegexOption.IGNORE_CASE).find(original)
                if (match != null) return "Reunión: ${capitalizeFirst(match.groupValues[2])}"
                null
            }
            ContextIntentKind.STUDY -> {
                val match = Regex("""(estudiar|estudio|examen|repasar) (.+)""", RegexOption.IGNORE_CASE).find(original)
                if (match != null) return "Estudio: ${capitalizeFirst(match.groupValues[2])}"
                null
            }
            ContextIntentKind.CALL -> {
                val match = Regex("""(llamar|hablar con) (.+)""", RegexOption.IGNORE_CASE).find(original)
                if (match != null) return "Llamar a ${capitalizeFirst(match.groupValues[2])}"
                null
            }
            ContextIntentKind.PAYMENT -> {
                val match = Regex("""pagar (.+)""", RegexOption.IGNORE_CASE).find(original)
                if (match != null) return "Pagar ${capitalizeFirst(match.groupValues[1])}"
                null
            }
            ContextIntentKind.REMINDER -> {
                val match = Regex("""(recuérdame|avísame|notifícame) (.+)""", RegexOption.IGNORE_CASE).find(original)
                if (match != null) return capitalizeFirst(match.groupValues[2])
                null
            }
            ContextIntentKind.EXERCISE -> {
                val match = Regex("""(ir al gimnasio|entrenar|hacer|yoga|correr)""", RegexOption.IGNORE_CASE).find(original)
                if (match != null) return capitalizeFirst(original)
                null
            }
            ContextIntentKind.HOUSEHOLD -> {
                val match = Regex("""(limpiar|ordenar|cocinar|lavar|arreglar|reparar) (.+)""", RegexOption.IGNORE_CASE).find(original)
                if (match != null) return "${capitalizeFirst(match.groupValues[1])} ${match.groupValues[2]}"
                null
            }
            else -> null
        }
    }

    /**
     * Genera un título descriptivo cuando no se puede extraer uno específico.
     */
    private fun generateTitle(text: String, kind: ContextIntentKind): String {
        val cleaned = text
            .replace(Regex("""\b(muy|más|tan|hay que|tengo que|voy a|debo|para)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        val maxLen = 80
        val title = capitalizeFirst(cleaned.take(maxLen))
        return if (title.length <= 3) kind.displayName else title
    }

    /**
     * Extrae fecha/hora de un texto en español.
     * Retorna timestamp en milisegundos o null.
     */
    internal fun extractDateTime(lower: String): Long? {
        val today = LocalDate.now()
        var targetDate: LocalDate? = null
        var targetTime: LocalTime? = null
        // Punto medio canonico inequivoco (medianoche 00:00 / mediodia 12:00)
        // proveniente de meridiem explicito o palabra canonica. Permite aplicar
        // past-safe (ruedo +1 dia si ya paso hoy) solo a midpoints seguros, no a
        // horas numericas arbitrarias donde +1 es ambiguo. Paridad con el
        // past-safe de medianoche/mediodia del NaturalTaskParser (l.4706-4711).
        var canonicalMidpoint: LocalTime? = null

        // Días de la semana
        val dayMap = mapOf(
            "lunes" to DayOfWeek.MONDAY, "martes" to DayOfWeek.TUESDAY,
            "miércoles" to DayOfWeek.WEDNESDAY, "miercoles" to DayOfWeek.WEDNESDAY,
            "jueves" to DayOfWeek.THURSDAY, "viernes" to DayOfWeek.FRIDAY,
            "sábado" to DayOfWeek.SATURDAY, "sabado" to DayOfWeek.SATURDAY,
            "domingo" to DayOfWeek.SUNDAY
        )

        // "esta <parte del día>": paridad con NaturalTaskParser.partOfDayTimes (c.592).
        // Parser: mañana→09:00, tarde→15:00, noche→21:00, madrugada→04:00, fecha=hoy.
        // Antes SÓLO "esta noche"/"esta tarde" fijaban targetDate=today (sin hora → caía
        // al default 12:00 = mediodía, p.ej. "esta noche" = 12:00, 9h de error) y
        // "esta mañana"/"esta madrugada" no se reconocían: "esta mañana" contiene "mañana"
        // y colisionaba con la regla "mañana"=día siguiente (sin guard null) → se fechaba
        // para MAÑANA a mediodía (día Y hora erróneos). Aquí se fijan fecha=hoy Y hora
        // canónica, ANTES de la regla "mañana"=día siguiente, para que esta última no la
        // pise; su guard `targetDate == null` la deja sin efecto cuando ya hay parte del
        // día. La hora canónica se pisa luego si hay hora numérica explícita
        // ("esta noche a las 10"), más específica (simétrico al orden de patrones del
        // parser donde "a las N" se prueba antes que las canónicas).
        val estaPartOfDay = Regex("""\besta\s+(mañana|manana|tarde|noche|madrugada)\b""", RegexOption.IGNORE_CASE)
            .find(lower)
        if (estaPartOfDay != null && targetDate == null) {
            targetDate = today
            targetTime = when (estaPartOfDay.groupValues[1].lowercase()) {
                "mañana", "manana" -> LocalTime.of(9, 0)
                "tarde" -> LocalTime.of(15, 0)
                "noche" -> LocalTime.of(21, 0)
                "madrugada" -> LocalTime.of(4, 0)
                else -> null
            }
        }

        // "mañana" como día siguiente. Solo si NO forma parte de una hora del día
        // ("de la mañana", "por la mañana", "en la mañana", "de/por/en mañana"):
        // antes, "reunión a las 9 de la mañana" caía aquí y se fechaba para MAÑANA.
        // Pero la frase "mañana por la mañana" / "mañana a las 9 de la mañana" tiene
        // DOS "mañana": el primer token es el adverbio de día siguiente y el segundo
        // el sufijo de meridiano. La exclusión anti-colisión solo debe suprimir el
        // adverbio cuando TODOS los "mañana" del texto forman parte de un sufijo de
        // hora. Si hay más tokens "mañana" que sufijos de meridiano, sobra al menos
        // un "mañana" que sí significa día siguiente.
        val mananaSuffix = Regex("""\b(de la|por la|en la|de|por|en)\s+mañana\b""", RegexOption.IGNORE_CASE)
        val mananaTokens = Regex("""\bmañana\b""", RegexOption.IGNORE_CASE).findAll(lower).count()
        val mananaSuffixMatches = mananaSuffix.findAll(lower).count()
        val manaanaComoDiaSiguiente = targetDate == null && lower.contains("mañana") &&
            !lower.contains("pasado mañana") &&
            mananaTokens > mananaSuffixMatches
        if (manaanaComoDiaSiguiente) {
            targetDate = today.plusDays(1)
        }
        // "anteayer"/"antier" = hace 2 días. Paridad con NaturalTaskParser (l.4164):
        // fechas PASADAS explícitas — el usuario reconoce que la cita está vencida y
        // debe aparecer en What Now como tal (no perderse ni caer a hoy). Antes
        // extractDateTime NO las reconocía → la captura de contexto de una
        // notificación con "ayer"/"anteayer" nacía SIN dueAt (tarea vencida
        // olvidada, P1). "antier" = variante coloquial hispanoamericana de
        // "anteayer". Va ANTES que "ayer" ("anteayer" contiene "ayer") y con guard
        // null para no pisar una fecha ya resuelta (p.ej. "mañana" explícito).
        if (targetDate == null &&
            (lower.contains("anteayer") || lower.contains("antier"))) {
            targetDate = today.minusDays(2)
        }
        // "ayer" = hace 1 día. Word-boundary evita casar dentro de "anteayer"
        // (aunque el guard de orden ya lo cubre) y futuras palabras con sufijo.
        if (targetDate == null &&
            Regex("""\bayer\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
            targetDate = today.minusDays(1)
        }
        // "pasado mañana"
        if (lower.contains("pasado mañana") || lower.contains("pasado manana")) {
            targetDate = today.plusDays(2)
        }
        // "hoy"
        if (lower.contains("hoy") && !lower.contains("a hoy")) {
            targetDate = today
        }
        // Períodos relativos (paridad con NaturalTaskParser nextPeriodPattern /
        // lastPeriodPattern, l.3806-3844 y l.3288-3300). "la semana/el mes/el año
        // que viene"/"próxima"/"entrante"/"en una semana" → +7/+30/+365 días;
        // "la semana/el mes/el año pasado"/"anterior" → −7/−30/−365 días. Misma
        // día-aritmética que el parser (now ± N·86400000 → aquí today ± N días).
        // Antes extractDateTime NO reconocía períodos → un ContextEvent de
        // notificación ("reunión la semana que viene") nacía SIN dueAt → la cita
        // futura no generaba recordatorio ni aparecía en el planificador; una cita
        // vencida ("la semana pasada") no se hacía visible en What Now (P1 evitar
        // olvidos). Se exige un calificador explícito (que viene/próxima/pasada/
        // anterior/en una…) para no inventar fechas a partir de "semana"/"mes"/
        // "año" sueltos ("esta semana", "cada semana", "fin de semana"). Va
        // DESPUÉS de hoy/mañana/pasado mañana para que esas frases más específicas
        // se resuelvan primero (sin necesidad de exclusiones). Orden del `when`
        // espeja el parser: trimestre/bimestre/semestre ANTES de "mes" porque
        // "trimes**tre**"/"bi**mes**tre"/"se**mes**tre" contienen la subcadena "mes".
        if (targetDate == null) {
            // Períodos relativos multi-unidad (paridad con NaturalTaskParser.relativePattern,
            // l.334): "en 2 semanas"/"dentro de 3 meses"/"de aquí a 5 días"/"en un par de
            // semanas" → cantidad × unitDays. Antes extractDateTime sólo reconocía el
            // singular escrito "en una semana" (bloque siguiente) → estas formas con
            // cantidad numérica devolvían null → un ContextEvent de notificación futuro
            // nacía SIN dueAt → sin recordatorio ni planificador (P1 evitar olvidos).
            // Misma aritmética y ORDEN de unidades que el parser (trimestre/bimestre/
            // semestre antes de "mes" porque contienen la subcadena "mes"). Va antes del
            // bloque singular ("en una semana") para que la forma más específica gane.
            val multiUnitMatch = Regex(
                """(?i)\b(?:en|dentro\s+de|de\s+aqu[íi]\s+a|de\s+ac[aá]\s+a)\s+(un\s+par\s+de|unos|unas|\d{1,3})\s*(d[ií]as?|semanas?|quincenas?|bimestres?|trimestres?|semestres?|mes(?:es)?|a[nñ]os?)\b"""
            ).find(lower)
            if (multiUnitMatch != null) {
                val rawAmount = multiUnitMatch.groupValues[1]
                val amount: Long = if (rawAmount.startsWith("un par") || rawAmount in setOf("unos", "unas")) {
                    2L
                } else {
                    rawAmount.toLongOrNull() ?: 1L
                }
                val unit = multiUnitMatch.groupValues[2].lowercase()
                val unitDays = when {
                    unit.startsWith("día") || unit.startsWith("dia") -> 1L
                    unit.startsWith("quincena") -> 15L
                    unit.startsWith("semana") -> 7L
                    unit.startsWith("bimestre") -> 60L
                    unit.startsWith("trimestre") -> 90L
                    unit.startsWith("semestre") -> 180L
                    unit.startsWith("mes") -> 30L
                    unit.startsWith("año") || unit.startsWith("ano") -> 365L
                    else -> null
                }
                if (unitDays != null) {
                    targetDate = today.plusDays(amount * unitDays)
                }
            }
        }
        if (targetDate == null) {
            val periodFuture = Regex(
                """que\s+viene|que\s+entra|entrante|pr[oó]xim|siguiente""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(lower) || Regex(
                """\ben\s+(?:un|una|unos|unas)\s+(?:semanas?|mes(?:es)?|a[nñ]os?)\b""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(lower)
            val periodPast = Regex(
                """pasad[oa]s?|anteriore?s?""", RegexOption.IGNORE_CASE
            ).containsMatchIn(lower)
            if (periodFuture || periodPast) {
                val days = when {
                    "trimestre" in lower -> 90L
                    "bimestre" in lower -> 60L
                    "semestre" in lower -> 180L
                    "quincena" in lower -> 15L
                    "semana" in lower -> 7L
                    "mes" in lower -> 30L
                    "año" in lower || "ano" in lower -> 365L
                    else -> null
                }
                if (days != null) {
                    targetDate = if (periodFuture) today.plusDays(days) else today.minusDays(days)
                }
            }
        }
        // "esta noche"/"esta tarde" ya se resuelven antes (bloque `estaPartOfDay`,
        // l.429): fijan targetDate=today + hora canónica. El guard `targetDate ==
        // null` de aquí era siempre falso para esas frases → bloque muerto (c.594).

        // Día de la semana
        if (targetDate == null) {
            for ((name, day) in dayMap) {
                if (lower.contains(name)) {
                    val daysUntil = (day.value - today.dayOfWeek.value + 7) % 7
                    targetDate = if (daysUntil == 0) today.plusDays(7) else today.plusDays(daysUntil.toLong())
                    break
                }
            }
        }

        // "el [día]" → ejemplo: "el 25", "el 25 de mayo"
        // El prefijo "el" o un mes explícito es OBLIGATORIO para evitar tratar como
        // fecha cualquier número suelto del texto (p.ej. "comprar 2 kilos de arroz").
        // Igual que con la hora, el primer `.find()` puede casar un número suelto
        // anterior ("comprar 2 kilos el 25") y descartarlo por guard sin examinar
        // la fecha válida posterior: se itera hasta el primer match con "el" o mes.
        val dayPattern = Regex("""(el\s+)?(\d{1,2})(?:\s+de\s+(\w+))?""")
        val dayMatch = dayPattern.findAll(lower).firstOrNull { m ->
            val hasEl = m.groupValues[1].isNotBlank()
            val dayNum = m.groupValues[2].toIntOrNull() ?: return@firstOrNull false
            val month = monthName(m.groupValues[3])
            dayNum in 1..31 && (hasEl || month != null)
        }
        if (targetDate == null && dayMatch != null) {
            val dayNum = dayMatch.groupValues[2].toIntOrNull()
            val monthName = dayMatch.groupValues[3]
            val month = monthName(monthName)
            if (dayNum != null && dayNum in 1..31) {
                targetDate = if (month != null) {
                    LocalDate.of(today.year, month, dayNum.coerceIn(1, 28))
                } else {
                    if (dayNum > today.dayOfMonth) today.withDayOfMonth(dayNum)
                    else today.plusMonths(1).withDayOfMonth(dayNum.coerceAtMost(28))
                }
            }
        }

        // Extraer hora. Se exige una pista temporal explícita (prefijo "a las",
        // formato "HH:MM" con dos puntos, o sufijo am/pm/tarde/noche/mañana/madrugada) para
        // evitar inventar una hora a partir de un número suelto ("comprar 2 kilos").
        // El primer `.find()` puede casar un número suelto anterior ("comprar 2 kilos
        // ... a las 9 de la mañana"): ese match tiene hasTimeCue=false y antes se
        // descartaba en silencio, dejando SIN hora una cita que sí la mencionaba más
        // adelante. Se itera hasta el primer match con pista horaria válida.
        val timePattern = Regex(
            """(a\s+las?|a\s+la|para\s+las?|para\s+la)?\s*(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?|am|pm|de la mañana|de la tarde|de la noche|de la madrugada|del día)?\s*(y\s+(?:media|treinta|cuarto|tres cuartos|cuarenta y cinco|veinticinco|veinte|diez|cinco|\d{1,2})|menos\s+(?:cuarto|quince|cinco|diez|veinte|veinticinco|\d{1,2}))?(?:\s*(a\.?m\.?|p\.?m\.?|am|pm|de la mañana|de la tarde|de la noche|de la madrugada|del día))?""",
            RegexOption.IGNORE_CASE
        )
        val timeMatch = timePattern.findAll(lower).firstOrNull { m ->
            // La fracción (grupo 5) por sí sola NO es pista horaria: "comprar 2 y
            // media kilos" casaría como 2:30. Se exige el prefijo "a las", el `:MM`,
            // un meridiano antes (grupo 4) o después (grupo 6) de la fracción.
            val hasTimeCue = m.groupValues[1].isNotBlank() ||
                m.groupValues[3].isNotBlank() ||
                m.groupValues[4].isNotBlank() ||
                m.groupValues[6].isNotBlank()
            if (!hasTimeCue) return@firstOrNull false
            val hour = m.groupValues[2].toIntOrNull() ?: return@firstOrNull false
            val minute = m.groupValues[3].toIntOrNull() ?: 0
            hour in 0..23 && minute in 0..59
        }
        if (timeMatch != null) {
            val hour = timeMatch.groupValues[2].toIntOrNull()
            val minute = timeMatch.groupValues[3].toIntOrNull() ?: 0
            val suffix = (timeMatch.groupValues[4].ifBlank { timeMatch.groupValues[6] }).lowercase()
            if (hour != null && hour in 0..23 && minute in 0..59) {
                var adjustedHour = hour
                if (suffix.contains("pm") || suffix.contains("tarde") || suffix.contains("noche")) {
                    // "12 de la noche" = medianoche (00:00), NO mediodía: antes la rama
                    // PM sólo sumaba 12 si hour<12, así hour=12 quedaba en 12:00 (mediodía).
                    // Paridad con NaturalTaskParser (`part == "noche" && h == 12 -> 0`).
                    // "12 de la tarde"/"12 pm" siguen siendo mediodía (12:00).
                    if (hour == 12 && suffix.contains("noche")) adjustedHour = 0
                    else if (hour < 12) adjustedHour = hour + 12
                } else if (suffix.contains("am") || suffix.contains("mañana") || suffix.contains("madrugada")) {
                    if (hour == 12) adjustedHour = 0
                }
                // Fracción sub-hora "y media"/"y cuarto"/"menos cuarto"/... (c.594,
                // paridad con NaturalTaskParser CLOCK_FRACTION_MAP). Sólo si NO hubo
                // `:MM` explícito: una hora con dos puntos (15:30) ya fijó sus minutos
                // y no admite fracción hablada adicional. El wrap de 24 h de la rama
                // "menos" es simétrico al del parser.
                val fraction = resolveClockFraction(timeMatch.groupValues[5])
                val effectiveHour: Int
                val effectiveMinute: Int
                if (fraction != null && timeMatch.groupValues[3].isBlank()) {
                    val totalMin = adjustedHour * 60 + minute + fraction
                    val wrapped = ((totalMin % 1440) + 1440) % 1440
                    effectiveHour = wrapped / 60
                    effectiveMinute = wrapped % 60
                } else {
                    effectiveHour = adjustedHour
                    effectiveMinute = minute
                }
                targetTime = LocalTime.of(effectiveHour, effectiveMinute)
                // Marca el midpoint canonico cuando un meridiem explicito resuelve
                // a medianoche (00:00) o mediodia (12:00): solo esos dos son
                // inequivocos y admiten past-safe (a una hora numerica como "a las
                // 9" el ruido de +1 es ambiguo: podria ser hoy si no ha llegado).
                // Paridad con el guard del parser (isInequivocalMidpoint, l.4706).
                // Una fraccion (c.594) vuelve el punto no-inequivoco: NO se marca.
                if (fraction == null && effectiveMinute == 0 && effectiveHour == 0) {
                    canonicalMidpoint = LocalTime.MIDNIGHT
                } else if (fraction == null && effectiveMinute == 0 && effectiveHour == 12 &&
                    (suffix.contains("pm") || suffix.contains("tarde"))) {
                    canonicalMidpoint = LocalTime.NOON
                }
            }
        }

        // Horas canónicas "al mediodía"/"a medianoche"/"a la medianoche" (c.587).
        // Paridad con NaturalTaskParser: esas palabras sueltas resuelven 12:00/00:00
        // (parser l.1532-1554 → explicitTimeData l.4395-4406). El [timePattern]
        // numérico NO las casa (no hay dígito), así targetTime quedaba null y:
        //  - "reunión al mediodía" (sin día) → null → ContextEvent sin vencimiento,
        //    invisible en What Now y sin recordatorio (evitar olvidos, P1).
        //  - "entrega a medianoche mañana" → targetDate=mañana, targetTime=null → caía
        //    al default `LocalTime.of(12,0)` (l.529) = mediodía, ¡12h de error!
        // Sólo se aplica si NO hubo hora numérica: una hora explícita ("a las 3")
        // siempre tiene prioridad (más específica, simétrico al orden de patrones
        // del parser donde "a las N" se prueba antes que las canónicas). Las
        // variantes con modificador ("pasada la medianoche"/"pasado el mediodía"/
        // "después del mediodía") también contienen la palabra canónica, así se
        // cubren sin lógica extra: al motor de contexto le basta la hora neta.
        if (targetTime == null) {
            val hasMedianoche = lower.contains("medianoche")
            val hasMediodia = lower.contains("mediodía") || lower.contains("mediodia")
            // Fracción sub-hora sobre las canónicas (c.591, paridad con el
            // grupo 1 de los patrones mediodía/medianoche del parser): "al mediodía
            // y media" → 12:30, "a medianoche y cuarto" → 00:15. Se reutiliza el
            // mismo resolver de la rama numérica; aquí la fracción siempre es
            // positiva (no se dice "medianoche menos cuarto").
            when {
                hasMedianoche -> {
                    // Fracción sub-hora sobre la canónica (c.594, paridad con el
                    // grupo 1 de los patrones mediodía/medianoche del parser): "a
                    // medianoche y cuarto" → 00:15. Aquí la fracción siempre es
                    // positiva (no se dice "medianoche menos cuarto"). Una fracción
                    // vuelve el punto no-inequivoco: NO se marca canonicalMidpoint
                    // (sin past-safe) — paridad con isInequivocalMidpoint (sólo 00:00/12:00).
                    val f = resolveClockFraction(lower)
                    if (f != null && f >= 0) {
                        targetTime = LocalTime.of(0, 0).plusMinutes(f.toLong())
                    } else {
                        targetTime = LocalTime.of(0, 0)
                        canonicalMidpoint = LocalTime.MIDNIGHT
                    }
                }
                hasMediodia -> {
                    val f = resolveClockFraction(lower)
                    if (f != null && f >= 0) {
                        targetTime = LocalTime.of(12, 0).plusMinutes(f.toLong())
                    } else {
                        targetTime = LocalTime.of(12, 0)
                        canonicalMidpoint = LocalTime.NOON
                    }
                }
            }
        }

        if (targetDate == null && targetTime == null) return null

        // Past-safe (c.590): un punto medio canonico inequivoco (medianoche/
        // mediodia) sin fecha explicita que ya paso hoy se rueda a +1 dia. Sin
        // esto, una captura contextual "reunion a las 12 de la noche" tomada por
        // la manana caia en hoy 00:00 (pasado) -> reminder <= now -> descartado
        // por ReminderSync -> la cita nacia olvidada (P1: evitar olvidos). Paridad
        // con el past-safe de midpoints del NaturalTaskParser (l.4706-4711, que
        // usa el mismo guard: date==null + parsedTime==MIDNIGHT/NOON + < now).
        // Solo se aplica a midpoints inequivocos: una hora numerica ("a las 9")
        // NO se rueda porque +1 es ambiguo (podria ser hoy si aun no llego) y el
        // parser tampoco lo hace salvo para esos midpoints.
        val baseDate = targetDate ?: today
        val time = targetTime ?: LocalTime.of(12, 0)
        val zone = java.time.ZoneId.systemDefault()
        val instant = baseDate.atTime(time).atZone(zone).toInstant()
        val date = if (targetDate == null && canonicalMidpoint != null &&
            instant.toEpochMilli() <= System.currentTimeMillis()) baseDate.plusDays(1) else baseDate

        return date.atTime(time)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    /**
     * Resuelve la fracción sub-hora cotidiana del español en minutos con signo
     * (c.591, paridad con NaturalTaskParser.resolveClockFraction / CLOCK_FRACTION_MAP).
     *
     * Acepta la frase completa tal cual la captura el grupo 5 del [timePattern]
     * ("y media", "menos cuarto", "y tres cuartos", "y 20") o, en el caso de las
     * horas canónicas mediodía/medianoche, el texto [lower] entero (busca la
     * primera aparición de "y media"/"y cuarto"/...). Rama positiva suma minutos
     * ("y media" → +30); rama negativa resta ("menos cuarto" → −15). Devuelve
     * `null` si [raw] no contiene ninguna fracción reconocida, para que el
     * llamador deje la hora en punto.
     *
     * No usa azar ni heurísticas opacas: es un mapa fijo de palabras→minutos,
     * idéntico al del parser para que una cita capturada por el motor de contexto
     * y una creada a mano en el parser resuelvan el mismo vencimiento.
     */
    private val CLOCK_FRACTION_MAP = listOf(
        "tres cuartos" to 45, "cuarenta y cinco" to 45, "cincuenta y cinco" to 55,
        "treinta y cinco" to 35, "veinticinco" to 25, "media" to 30, "treinta" to 30,
        "cuarto" to 15, "quince" to 15, "cuarenta" to 40, "cincuenta" to 50,
        "veinte" to 20, "diez" to 10, "cinco" to 5
    )
    private val CLOCK_FRACTION_PHRASE = Regex(
        """y\s+(?:tres cuartos|cuarenta y cinco|cincuenta y cinco|treinta y cinco|veinticinco|media|treinta|cuarto|quince|cuarenta|cincuenta|veinte|diez|cinco|\d{1,2})|menos\s+(?:cuarto|quince|cinco|diez|veinte|veinticinco|\d{1,2})""",
        RegexOption.IGNORE_CASE
    )
    private fun resolveClockFraction(raw: String): Int? {
        val s = raw.trim().lowercase().replace("ñ", "n").replace("í", "i")
        // Si [raw] es la frase exacta (grupo 5 del [timePattern]): "y media", "menos cuarto".
        if (s.startsWith("y ") || s.startsWith("menos ")) {
            val positive = s.startsWith("y ")
            val body = s.removePrefix("y ").removePrefix("menos ")
            val m = CLOCK_FRACTION_MAP.firstOrNull { body == it.first }?.second
                ?: body.toIntOrNull()?.takeIf { it in 0..59 }
            return m?.let { if (positive) it else -it }
        }
        // Si [raw] es texto completo (caso mediodí a/medianoche: se pasa [lower]):
        // primera aparición de "y media"/"menos cuarto"/... en el texto.
        val m = CLOCK_FRACTION_PHRASE.find(s) ?: return null
        val gs = m.value.trim().lowercase()
        return resolveClockFraction(gs)
    }

    private fun hasDateReference(lower: String): Boolean {
        // Paridad con extractDateTime (c.600): reconocer los anclajes de fecha que
        // el parser resuelve pero este detector omitía. Sin ellos, una frase como
        // "pagar la factura ayer" no recibía el bono de fecha (+0.1) y caía por
        // debajo de MINIMUM_CONFIDENCE, descartando un compromiso (olvido, P1),
        // mientras que "pagar la factura mañana" sí pasaba. Mismo verbo, mismo
        // objeto: la única diferencia era el ancla temporal reconocida.
        return lower.contains("mañana") || lower.contains("hoy") ||
            lower.contains("ayer") || lower.contains("antier") ||
            lower.contains("lunes") || lower.contains("martes") ||
            lower.contains("miércoles") || lower.contains("miercoles") ||
            lower.contains("jueves") || lower.contains("viernes") ||
            lower.contains("sábado") || lower.contains("sabado") ||
            lower.contains("domingo") ||
            Regex("""(pasado mañana|pasado manana|esta noche|esta tarde|esta madrugada|el \d+)""").containsMatchIn(lower) ||
            // "1 de enero", "15 de marzo": extractDateTime lo resuelve vía
            // dayPattern + monthName; el detector anterior solo miraba "el \d+".
            Regex("""\d{1,2}\s+de\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)""").containsMatchIn(lower) ||
            Regex("""\d{1,2}:\d{2}""").containsMatchIn(lower) ||
            Regex("""a las \d+""").containsMatchIn(lower)
    }

    private fun hasTimeReference(lower: String): Boolean {
        // Paridad con extractDateTime (c.600): "a medianoche"/"al mediodía" son
        // horas canónicas (00:00/12:00) que el parser resuelve; el detector las
        // omitía, así una entrega "a medianoche" no recibía el bono de hora (+0.08).
        return Regex("""\d{1,2}:\d{2}""").containsMatchIn(lower) ||
            Regex("""a (las|la) \d{1,2}""").containsMatchIn(lower) ||
            lower.contains("de la mañana") || lower.contains("de la tarde") ||
            lower.contains("de la noche") || lower.contains("del día") ||
            lower.contains("medianoche") ||
            lower.contains("mediodía") || lower.contains("mediodia")
    }

    private fun monthName(name: String): Int? {
        val months = mapOf(
            "enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4,
            "mayo" to 5, "junio" to 6, "julio" to 7, "agosto" to 8,
            "septiembre" to 9, "octubre" to 10, "noviembre" to 11, "diciembre" to 12
        )
        return months[name.lowercase()]
    }

    private fun capitalizeFirst(text: String): String {
        if (text.isBlank()) return text
        return text.first().uppercase() + text.substring(1)
    }

    /** Peso de cada palabra clave en la puntuación */
    private const val KEYWORD_WEIGHT = 0.12f
    private const val MIN_TEXT_LENGTH = 4
}
