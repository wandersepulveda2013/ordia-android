package com.ordia.app.domain

import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.TaskPriority
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Resultado del analizador de lenguaje natural en español.
 *
 * El analizador es 100 % local y determinista (sin red ni IA remota).
 * Los campos nuevos usan valores por defecto para no romper llamadas existentes.
 */
data class ParsedTaskInput(
    val title: String,
    val dueAt: Long?,
    val priority: TaskPriority,
    /** Duración estimada en minutos, p. ej. "durante 45 minutos" o "una hora y media". */
    val durationMinutes: Int? = null,
    /** Recordatorio "N antes" de la fecha límite, p. ej. "recuérdame 2 horas antes". */
    val reminderOffsetMinutes: Int? = null,
    val recurrence: RecurrenceFrequency = RecurrenceFrequency.NONE,
    val recurrenceInterval: Int = 1,
    /** Días de repetición semanal (ISO 1..7, CSV), p. ej. "1,4" para lunes y jueves. */
    val recurrenceDays: String = "",
    /** Categoría inferida por contexto (trabajo, casa, compras, salud, personal). */
    val category: String = "",
    /** 1.0 si la captura es interpretable, 0.35 si es texto libre sin señales. */
    val confidence: Float = 1f
)

/** Momento del día expresado en lenguaje natural. */
private enum class DayPart(val periodName: String) {
    MORNING("mañana"),
    DAWN("madrugada"),
    AFTERNOON("tarde"),
    NIGHT("noche");

    fun implicitTime(): LocalTime = when (this) {
        MORNING -> LocalTime.of(9, 0)
        DAWN -> LocalTime.of(5, 0)
        AFTERNOON -> LocalTime.of(15, 0)
        NIGHT -> LocalTime.of(20, 0)
    }
}

object NaturalTaskParser {
    private val numericDatePattern = Regex("""\b([0-3]?\d)[/-]([01]?\d)(?:[/-](\d{2,4}))?\b""")
    private val weekdayPattern = Regex("""(?i)\b(?:el\s+)?(?:pr[oó]ximo\s+)?(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\b""")
    private val relativePattern = Regex("""(?i)\ben\s+(\d{1,3})\s*(minutos?|mins?|horas?|d[ií]as?)\b""")
    private val relativeWordPattern = Regex("""(?i)\ben\s+(media\s+hora|una\s+hora|un\s+hora)\b""")
    private val monthNamePattern = Regex("""(?i)\b(?:el\s+)?(\d{1,2})\s+de\s+([a-záéíóúüñ]+)(?:\s+de\s+(\d{2,4}))?\b""")
    private val timePatterns = listOf(
        Regex("""(?i)\ba\s+las\s+([01]?\d|2[0-3])(?::([0-5]\d))?\s*h?\s*(a\.?\s*m\.?|p\.?\s*m\.?)?\b"""),
        Regex("""(?i)\b([01]?\d|2[0-3]):([0-5]\d)\s*h?\s*(a\.?\s*m\.?|p\.?\s*m\.?)?\b"""),
        Regex("""(?i)\b(0?[1-9]|1[0-2])(?::([0-5]\d))?\s*(a\.?\s*m\.?|p\.?\s*m\.?)\b"""),
        Regex("""(?i)\b(?:2[0-3]|[01][0-9])\s*h\b(?!\s*\d)"""),
        Regex("""(?i)\bmediod[ií]a\b"""),
        Regex("""(?i)\bmedianoche\b""")
    )
    private val reminderPatterns = listOf(
        Regex("""(?i)\b(?:recuérdame|av[ií]same|notif[ií]came|recordatorio)\s*(?:con\s+)?(\d{1,3})\s*(minutos?|min|horas?|hora|d[ií]as?|d[ií]a)\s*(?:de\s+anticipaci[oó]n|antes|de\s+adelanto|adelanto|de)?\b"""),
        Regex("""(?i)\b(\d{1,3})\s*(minutos?|horas?|d[ií]as?)\s+antes\b""")
    )
    private val reminderWordPatterns = listOf(
        Regex("""(?i)\b(?:recuérdame|av[ií]same|notif[ií]came)\s+el\s+d[ií]a\s+anterior\b"""),
        Regex("""(?i)\b(?:recuérdame|av[ií]same|notif[ií]came)\s+a\s+primera\s+hora\b""")
    )
    private val durationPatterns = listOf(
        Regex("""(?i)\((\d{1,3})\s*(minutos?|min|horas?|hora)\)"""),
        Regex("""(?i)\b(?:durante|por)\s+(\d{1,3})\s*(minutos?|min|horas?|hora)\b"""),
        Regex("""(?i)\b(\d{1,3})\s*(minutos?|min)\b"""),
        Regex("""(?i)\b(\d{1,3})\s*(horas?)\b""")
    )
    private val hMinDurationPattern = Regex("""(?i)\b(\d{1,2})\s*h\s*(\d{1,2})\s*m\b""")
    private val wordDurationPattern = Regex("""(?i)\b(?:media\s+hora|(?:una\s+)?hora\s+y\s+(?:media|cuarto)|una\s+hora)\b""")
    private val decimalDurationPattern = Regex("""(?i)\b(\d+(?:[.,]\d+)?)\s+horas?\b""")

    /** "esta tarde", "a la noche", "por la mañana", "en la tarde", "esta madrugada". */
    private val dayPartPattern = Regex("""(?i)\b(?:esta|a\s+la|por\s+la|en\s+la)\s+(mañana|madrugada|tarde|noche)\b""")

    /** Cualificador de hora: "a las 3 de la tarde", "a las 9 de la noche". */
    private val deLaQualifierPattern = Regex("""(?i)\bde\s+la\s+(mañana|tarde|noche)\b""")

    private val weekendPattern = Regex("""(?i)\bfin\s+de\s+semana\b""")
    private val nextWeekPattern = Regex("""(?i)\bpr[oó]xim[oa]\s+semana\b""")
    private val nextMonthPattern = Regex("""(?i)\bpr[oó]xim[oa]\s+mes\b""")
    private val dayOfMonthPattern = Regex("""(?i)\bel\s+d[ií]a\s+(\d{1,2})\b""")
    private val monthOnlyPattern = Regex("""(?i)\ben\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)\b""")
    private val weekdayRangePattern = Regex("""(?i)\b(?:de|entre)\s+(lunes|martes|mi[eé]rcoles|jueves|viernes)\s+a\s+(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\b""")
    private val workdaysPattern = Regex("""(?i)\b(d[ií]as\s+laborables|entre\s+semana|d[ií]as\s+h[aá]biles)\b""")

    private val weekdays = mapOf(
        "lunes" to DayOfWeek.MONDAY,
        "martes" to DayOfWeek.TUESDAY,
        "miércoles" to DayOfWeek.WEDNESDAY,
        "miercoles" to DayOfWeek.WEDNESDAY,
        "jueves" to DayOfWeek.THURSDAY,
        "viernes" to DayOfWeek.FRIDAY,
        "sábado" to DayOfWeek.SATURDAY,
        "sabado" to DayOfWeek.SATURDAY,
        "domingo" to DayOfWeek.SUNDAY
    )

    private val months = mapOf(
        "enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4,
        "mayo" to 5, "junio" to 6, "julio" to 7, "agosto" to 8,
        "septiembre" to 9, "setiembre" to 9, "octubre" to 10,
        "noviembre" to 11, "diciembre" to 12
    )

    private val categories = listOf(
        "trabajo" to listOf("reunión", "reunion", "informe", "reporte", "cliente", "contrato", "presentación", "presentacion", "entregar", "proyecto", "oficina", "correo", "email", "junta", "gerente", "jefe"),
        "compras" to listOf("comprar", "compra", "supermercado", "mercado", "farmacia", "tienda", "recados", "mandado", "leche", "víveres", "viveres"),
        "salud" to listOf("médico", "medico", "doctor", "cita", "gimnasio", "ejercicio", "correr", "dentista", "salud", "medicina", "pastillas", "vacuna", "análisis", "analisis"),
        "casa" to listOf("limpiar", "cocinar", "lavar", "cocina", "casa", "hogar", "reparar", "jardín", "jardin", "basura", "tramitar", "luz", "agua", "gas"),
        "personal" to listOf("llamar a", "familia", "mamá", "mama", "papá", "papa", "herman", "pareja", "amigo", "amiga", "cumpleaños", "cumpleanos", "aniversario")
    )

    fun parse(text: String, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): ParsedTaskInput {
        val base = Instant.ofEpochMilli(now).atZone(zone)
        var working = text.trim()
        val original = text.trim()
        var hasTemporalSignal = false

        // Prioridad: tokens explícitos (!/#) y lenguaje natural ("urgente", "muy urgente", "importante").
        val priority = when {
            "!urgente" in working.lowercase() || "#urgente" in working.lowercase() ||
                Regex("""(?i)\b(muy\s+)?urgente\b""").containsMatchIn(working) -> TaskPriority.URGENT
            "!alta" in working.lowercase() || "#alta" in working.lowercase() ||
                Regex("""(?i)\bimportante\b""").containsMatchIn(working) -> TaskPriority.HIGH
            "!baja" in working.lowercase() || "#baja" in working.lowercase() -> TaskPriority.LOW
            else -> TaskPriority.NORMAL
        }
        working = working
            .replace(Regex("""(?i)(?:!|#)(urgente|alta|baja)\b"""), " ")
            .replace(Regex("""(?i)\b(muy\s+)?urgente\b"""), " ")
            .replace(Regex("""(?i)\bimportante\b"""), " ")

        // Recordatorio "N antes" (se extrae antes que la duración para no confundir unidades).
        val reminderOffsetMinutes = reminderPatterns.asSequence()
            .mapNotNull { it.find(working) }
            .minByOrNull { it.range.first }
            ?.let { match ->
                val amount = match.groupValues[1].toLongOrNull() ?: return@let null
                val unit = match.groupValues[2].lowercase()
                val minutes = when {
                    unit.startsWith("min") -> amount
                    unit.startsWith("hora") -> amount * 60
                    else -> amount * 24 * 60
                }
                minutes.toInt().coerceIn(1, 60 * 24 * 30)
            }
        reminderPatterns.forEach { pattern ->
            pattern.findAll(working).forEach { working = working.replace(it.value, " ") }
        }
        // Recordatorios con palabras: "el día anterior" / "a primera hora".
        reminderWordPatterns.forEach { pattern ->
            pattern.find(working)?.let { working = working.replace(it.value, " ") }
        }

        // Fecha relativa "en N minutos/horas/días" y "en media hora / en una hora".
        val relativeDueAt = relativePattern.find(working)?.let { match ->
            val amount = match.groupValues[1].toLongOrNull() ?: return@let null
            val unit = match.groupValues[2].lowercase()
            val minutes = when {
                unit.startsWith("min") -> amount
                unit.startsWith("hora") -> amount * 60
                else -> amount * 24 * 60
            }
            now + minutes * 60_000L
        } ?: relativeWordPattern.find(working)?.let { match ->
            val minutes = if ("media" in match.value.lowercase()) 30L else 60L
            now + minutes * 60_000L
        }
        val relativeMatch = relativePattern.find(working) ?: relativeWordPattern.find(working)
        relativeMatch?.let { working = working.replace(it.value, " ") }
        if (relativeDueAt != null) hasTemporalSignal = true

        // Momento del día en lenguaje natural: "esta tarde", "a la noche", "por la mañana",
        // y cualificadores de hora: "a las 3 de la tarde".
        // Se procesa antes que la fecha para que "mañana" de "por la mañana" no se lea como día.
        val dayPartMatch = dayPartPattern.find(working) ?: deLaQualifierPattern.find(working)
        val dayPart = dayPartMatch?.let { match ->
            val name = match.groupValues[1].lowercase()
            when (name) {
                "mañana" -> DayPart.MORNING
                "madrugada" -> DayPart.DAWN
                "tarde" -> DayPart.AFTERNOON
                else -> DayPart.NIGHT
            }
        }
        dayPartMatch?.let { working = working.replace(it.value, " ") }
        if (dayPart != null) hasTemporalSignal = true

        // Repetición: se procesa antes que la fecha para que "cada viernes" no se lea como fecha suelta.
        val recurrence = parseRecurrence(working)
        recurrence.phraseRanges.sortedByDescending { it.first }.forEach { range ->
            working = working.substring(0, range.first) + " " + working.substring(range.last + 1)
        }
        if (recurrence.frequency != RecurrenceFrequency.NONE) hasTemporalSignal = true

        val weekdayMatch = weekdayPattern.find(working)
        val numericDateMatch = numericDatePattern.find(working)
        val monthNameMatch = monthNamePattern.find(working)
        val weekendMatch = weekendPattern.find(working)
        val nextWeekMatch = nextWeekPattern.find(working)
        val nextMonthMatch = nextMonthPattern.find(working)
        val dayOfMonthMatch = dayOfMonthPattern.find(working)
        val monthOnlyMatch = monthOnlyPattern.find(working)
        val date = when {
            Regex("""(?i)\bpasado\s+mañana\b""").containsMatchIn(working) -> base.toLocalDate().plusDays(2)
            Regex("""(?i)\bmañana\b""").containsMatchIn(working) -> base.toLocalDate().plusDays(1)
            Regex("""(?i)\bhoy\b""").containsMatchIn(working) -> base.toLocalDate()
            weekendMatch != null -> nextSaturday(base.toLocalDate())
            nextWeekMatch != null -> base.toLocalDate().with(DayOfWeek.MONDAY).plusWeeks(1)
            nextMonthMatch != null -> base.toLocalDate().withDayOfMonth(1).plusMonths(1)
            weekdayMatch != null -> nextWeekday(base.toLocalDate(), weekdayMatch.groupValues[1].toDayOfWeek())
            monthNameMatch != null -> parseMonthNameDate(base.toLocalDate(), monthNameMatch)
            dayOfMonthMatch != null -> parseDayOfMonth(base.toLocalDate(), dayOfMonthMatch.groupValues[1].toIntOrNull())
            monthOnlyMatch != null -> parseMonthOnly(base.toLocalDate(), months[monthOnlyMatch.groupValues[1].lowercase()])
            numericDateMatch != null -> {
                val day = numericDateMatch.groupValues[1].toIntOrNull()
                val month = numericDateMatch.groupValues[2].toIntOrNull()
                val rawYear = numericDateMatch.groupValues[3].toIntOrNull()
                val year = when {
                    rawYear == null -> base.year
                    rawYear < 100 -> 2000 + rawYear
                    else -> rawYear
                }
                if (day == null || month == null) null else runCatching { LocalDate.of(year, month, day) }.getOrNull()
            }
            // Repetición semanal con días explícitos: primera ocurrencia futura.
            recurrence.frequency == RecurrenceFrequency.WEEKLY && recurrence.days.isNotEmpty() -> recurrence.days
                .mapNotNull { it.toDayOfWeekOrNull() }
                .map { nextWeekday(base.toLocalDate(), it) }
                .minOrNull()
            else -> null
        }
        if (date != null) hasTemporalSignal = true

        val timeMatch = timePatterns.asSequence().mapNotNull { it.find(working) }.minByOrNull { it.range.first }
        val parsedTime = timeMatch?.let { match ->
            when {
                match.value.lowercase().contains("mediodía") || match.value.lowercase().contains("mediodia") -> LocalTime.NOON
                match.value.lowercase().contains("medianoche") -> LocalTime.MIDNIGHT
                else -> {
                    var hour = match.groupValues[1].toInt()
                    val minute = match.groupValues[2].toIntOrNull() ?: 0
                    val meridiem = match.groupValues[3].lowercase().replace(".", "").replace(" ", "")
                    if (meridiem == "pm" && hour < 12) hour += 12
                    if (meridiem == "am" && hour == 12) hour = 0
                    // "a las 3 de la tarde" (sin meridiano) se desplaza según el momento del día.
                    if (meridiem.isEmpty() && dayPart != null) {
                        when (dayPart) {
                            DayPart.AFTERNOON -> if (hour != 12 && hour < 12) hour += 12
                            DayPart.NIGHT -> if (hour == 12) hour = 0 else if (hour < 12) hour += 12
                            else -> Unit
                        }
                    }
                    LocalTime.of(hour, minute)
                }
            }
        }
        // Sin hora explícita pero con momento del día: se usa la hora implícita.
        val effectiveTime = parsedTime ?: dayPart?.implicitTime()

        // Una hora (explícita o implícita) sin fecha concreta implica hoy.
        var effectiveDate = date ?: if (effectiveTime != null) base.toLocalDate() else null
        if (
            dayPartMatch != null && parsedTime == null && effectiveDate == base.toLocalDate() && effectiveTime != null
        ) {
            val planned = Instant.ofEpochMilli(DateRules.toEpochMillis(effectiveDate, effectiveTime, zone))
            if (planned.toEpochMilli() < now) effectiveDate = effectiveDate.plusDays(1)
        }
        val dueAt = relativeDueAt ?: effectiveDate?.let { DateRules.toEpochMillis(it, effectiveTime ?: LocalTime.of(9, 0), zone) }

        // Duración: no se aplica a "en N minutos" (esa es fecha relativa, ya eliminada).
        val (durationMatch, durationMinutes) = findDuration(working)
        durationMatch?.let { working = working.replace(it.value, " ") }

        val category = categories.firstOrNull { (_, keywords) -> keywords.any { working.contains(it, ignoreCase = true) } }?.first.orEmpty()

        // Limpieza de la frase para el título.
        working = working
            .replace(Regex("""(?i)\bpasado\s+mañana\b|\bmañana\b|\bhoy\b"""), " ")
            .let { value -> weekdayPattern.replace(value, " ") }
            .let { value -> monthNamePattern.replace(value, " ") }
            .let { value -> numericDatePattern.replace(value, " ") }
            .let { value -> weekendPattern.replace(value, " ") }
            .let { value -> nextWeekPattern.replace(value, " ") }
            .let { value -> nextMonthPattern.replace(value, " ") }
            .let { value -> dayOfMonthPattern.replace(value, " ") }
            .let { value -> monthOnlyPattern.replace(value, " ") }
            .let { value -> timePatterns.fold(value) { acc, pattern -> pattern.replace(acc, " ") } }
            .replace(Regex("""(?i)\bantes\s+del?\b|\bpara\s+el\b|\bpara\s+mañana\b|\bhasta\s+el\b"""), " ")
            .replace(Regex("""(?i)\b(en|a\s+la|por\s+la|de\s+la|para|el|la)\b\s*$"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', '.', '-')

        val confidence = when {
            relativeDueAt != null -> 1.0f
            dueAt != null && parsedTime != null -> 1.0f
            dueAt != null -> 0.9f
            priority != TaskPriority.NORMAL || durationMinutes != null || reminderOffsetMinutes != null ||
                recurrence.frequency != RecurrenceFrequency.NONE || category.isNotEmpty() || hasTemporalSignal -> 0.6f
            else -> 0.35f
        }

        return ParsedTaskInput(
            title = working.ifBlank { original }.take(240),
            dueAt = dueAt,
            priority = priority,
            durationMinutes = durationMinutes,
            reminderOffsetMinutes = reminderOffsetMinutes,
            recurrence = recurrence.frequency,
            recurrenceInterval = recurrence.interval,
            recurrenceDays = recurrence.days.joinToString(","),
            category = category,
            confidence = confidence
        )
    }

    private data class RecurrenceResult(
        val frequency: RecurrenceFrequency,
        val interval: Int,
        val days: List<Int>,
        val phraseRanges: List<IntRange>
    )

    private fun parseRecurrence(working: String): RecurrenceResult {
        val base = RecurrenceResult(RecurrenceFrequency.NONE, 1, emptyList(), emptyList())
        val phrases = mutableListOf<IntRange>()

        // "días laborables" / "entre semana" → lunes a viernes.
        workdaysPattern.find(working)?.let { match ->
            phrases += match.range
            return RecurrenceResult(RecurrenceFrequency.WEEKLY, 1, listOf(1, 2, 3, 4, 5), phrases)
        }

        // "de lunes a viernes" (rango de días).
        weekdayRangePattern.find(working)?.let { match ->
            val from = match.groupValues[1].toDayOfWeekOrNull()?.value ?: 1
            val to = match.groupValues[2].toDayOfWeekOrNull()?.value ?: 5
            val days = if (from <= to) (from..to).toList() else (from..7).toList() + (1..to).toList()
            phrases += match.range
            return RecurrenceResult(RecurrenceFrequency.WEEKLY, 1, days, phrases)
        }

        // "todos los viernes" / "cada lunes y jueves" / "los viernes"
        val weeklyDayPatterns = listOf(
            Regex("""(?i)\btodos\s+los\s+(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\b"""),
            Regex("""(?i)\bcada\s+(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)(?:\s+y\s+(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo))?\b"""),
            Regex("""(?i)\blos\s+(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\b""")
        )
        val weeklyMatch = weeklyDayPatterns.asSequence()
            .mapNotNull { pattern -> pattern.find(working) }
            .minByOrNull { it.range.first }
        if (weeklyMatch != null) {
            val days = (1..weeklyMatch.groupValues.lastIndex).mapNotNull { index ->
                weeklyMatch.groupValues[index].takeIf { it.isNotBlank() }?.toDayOfWeekOrNull()?.value
            }.distinct().sorted()
            if (days.isNotEmpty()) {
                phrases += weeklyMatch.range
                return RecurrenceResult(RecurrenceFrequency.WEEKLY, 1, days, phrases)
            }
        }

        // "cada N días/semanas/meses/años"
        val intervalPattern = Regex("""(?i)\bcada\s+(\d{1,3})\s*(d[ií]as?|semanas?|meses?|a[nñ]os?)\b""")
        intervalPattern.find(working)?.let { match ->
            val interval = match.groupValues[1].toIntOrNull()?.coerceIn(1, 366) ?: return@let
            val unit = match.groupValues[2].lowercase()
            val frequency = when {
                unit.startsWith("d") -> RecurrenceFrequency.DAILY
                unit.startsWith("s") -> RecurrenceFrequency.WEEKLY
                unit.startsWith("mes") -> RecurrenceFrequency.MONTHLY
                unit.contains("añ") || unit.startsWith("an") -> RecurrenceFrequency.YEARLY
                else -> return@let
            }
            phrases += match.range
            return RecurrenceResult(frequency, interval, emptyList(), phrases)
        }

        val fixedPatterns = listOf(
            Regex("""(?i)\btodos\s+los\s+d[ií]as\b|\bcada\s+d[ií]a\b|\bdiariamente\b""") to RecurrenceFrequency.DAILY,
            Regex("""(?i)\btodas\s+las\s+[sS]emanas\b|\bcada\s+[sS]emana\b|\bsemanalmente\b""") to RecurrenceFrequency.WEEKLY,
            Regex("""(?i)\btodos\s+los\s+meses\b|\bcada\s+mes\b|\bmensualmente\b""") to RecurrenceFrequency.MONTHLY,
            Regex("""(?i)\btodos\s+los\s+a[nñ]os\b|\bcada\s+a[nñ]o\b|\banualmente\b""") to RecurrenceFrequency.YEARLY
        )
        fixedPatterns.forEach { (pattern, frequency) ->
            pattern.find(working)?.let { match ->
                phrases += match.range
                return RecurrenceResult(frequency, 1, emptyList(), phrases)
            }
        }

        return base
    }

    /**
     * Localiza una duración y devuelve (match, minutos). Orden: combinada "1h30m",
     * palabras ("media hora", "una hora y media", "una hora"), decimal ("1,5 horas") y
     * las formas numéricas clásicas.
     */
    private fun findDuration(working: String): Pair<MatchResult?, Int?> {
        hMinDurationPattern.find(working)?.let { match ->
            val hours = match.groupValues[1].toIntOrNull() ?: 0
            val minutes = match.groupValues[2].toIntOrNull() ?: 0
            return match to (hours * 60 + minutes).coerceIn(5, 24 * 60)
        }
        wordDurationPattern.find(working)?.let { match ->
            val value = match.value.lowercase()
            val minutes = when {
                value.startsWith("media") -> 30
                "cuarto" in value -> 75
                else -> 90
            }
            return match to minutes
        }
        decimalDurationPattern.find(working)?.let { match ->
            val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull()
            if (amount != null) {
                val minutes = (amount * 60).toInt().coerceIn(5, 24 * 60)
                return match to minutes
            }
        }
        durationPatterns.forEach { pattern ->
            pattern.find(working)?.let { match ->
                val amount = match.groupValues[1].toIntOrNull()
                if (amount != null) {
                    val unit = match.groupValues[2].lowercase()
                    val minutes = if (unit.startsWith("hora")) amount * 60 else amount
                    val inRelativeContext = Regex("""(?i)\ben\s*$|dentro\s+de\s*$""")
                        .containsMatchIn(working.substring(0, match.range.first))
                    return match to (if (inRelativeContext) null else minutes.coerceIn(5, 24 * 60))
                }
            }
        }
        return null to null
    }

    private fun parseMonthNameDate(today: LocalDate, match: MatchResult): LocalDate? {
        val day = match.groupValues[1].toIntOrNull() ?: return null
        val month = months[match.groupValues[2].lowercase()] ?: return null
        val rawYear = match.groupValues[3].toIntOrNull()
        val year = when {
            rawYear == null -> today.year
            rawYear < 100 -> 2000 + rawYear
            else -> rawYear
        }
        var date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
        if (rawYear == null && date.isBefore(today)) date = date.plusYears(1)
        return date
    }

    private fun parseDayOfMonth(today: LocalDate, day: Int?): LocalDate? {
        val target = day ?: return null
        if (target !in 1..31) return null
        var date = runCatching { LocalDate.of(today.year, today.month, target) }.getOrNull()
            ?: runCatching { LocalDate.of(today.year, today.monthValue + 1, target) }.getOrNull()
            ?: return null
        if (date.isBefore(today)) date = date.plusMonths(1)
        return date
    }

    private fun parseMonthOnly(today: LocalDate, month: Int?): LocalDate? {
        val target = month ?: return null
        var date = runCatching { LocalDate.of(today.year, target, 1) }.getOrNull() ?: return null
        if (date.isBefore(today)) date = date.plusYears(1)
        return date
    }

    private fun nextSaturday(from: LocalDate): LocalDate {
        val delta = (DayOfWeek.SATURDAY.value - from.dayOfWeek.value + 7) % 7
        return from.plusDays(if (delta == 0) 7 else delta.toLong())
    }

    private fun nextWeekday(from: LocalDate, target: DayOfWeek): LocalDate {
        val delta = (target.value - from.dayOfWeek.value + 7) % 7
        return from.plusDays(if (delta == 0) 7 else delta.toLong())
    }

    private fun String.toDayOfWeek(): DayOfWeek = weekdays[this.lowercase()] ?: DayOfWeek.MONDAY

    private fun String.toDayOfWeekOrNull(): DayOfWeek? = weekdays[this.lowercase()]

    private fun Int.toDayOfWeekOrNull(): DayOfWeek? =
        if (this in 1..7) DayOfWeek.of(this) else null
}
