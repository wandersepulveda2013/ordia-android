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
    /** Duración estimada en minutos, p. ej. "durante 45 minutos". */
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

object NaturalTaskParser {
    private val numericDatePattern = Regex("""\b([0-3]?\d)[/-]([01]?\d)(?:[/-](\d{2,4}))?\b""")
    private val weekdayPattern = Regex("""(?i)\b(?:el\s+|del\s+|de\s+)?(?:pr[oó]ximo\s+|pr[oó]xima\s+)?(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)(?:\s+que\s+viene|\s+pr[oó]ximos?|\s+pr[oó]ximas?)?\b""")
    /**
     * Fecha relativa: "en N minutos/horas/días" o "dentro de N ...". Acepta dígitos
     * o números escritos (una/un, dos, ..., doce). "una"/"un" → 1.
     */
    private val relativePattern = Regex(
        """(?i)\b(?:en|dentro\s+de)\s+(\d{1,3}|un|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce)\s*(minutos?|mins?|horas?|d[ií]as?)\b"""
    )
    private val monthNamePattern = Regex("""(?i)\b(?:el\s+)?(\d{1,2})\s+de\s+([a-záéíóúüñ]+)(?:\s+de\s+(\d{2,4}))?\b""")
    private val timePatterns = listOf(
        Regex("""(?i)\ba\s+las\s+([01]?\d|2[0-4])(?::([0-5]\d))?\s*(a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada)?\b"""),
        Regex("""(?i)\b([01]?\d|2[0-4]):([0-5]\d)\s*(a\.?\s*m\.?|p\.?\s*m\.?)?\b"""),
        Regex("""(?i)\b(0?[1-9]|1[0-2])(?::([0-5]\d))?\s*(a\.?\s*m\.?|p\.?\s*m\.?)\b"""),
        Regex("""(?i)\b(?:al\s+|a\s+la\s+)?mediod[ií]a\b"""),
        Regex("""(?i)\b(?:al\s+|a\s+la\s+)?medianoche\b""")
    )
    private val reminderPatterns = listOf(
        Regex("""(?i)\b(?:recuérdame|av[ií]same|notif[ií]came|recordatorio)\s*(?:con\s+)?(\d{1,3})\s*(minutos?|min|horas?|hora|d[ií]as?|d[ií]a)\s*(?:de\s+anticipaci[oó]n|antes|de\s+adelanto|adelanto|de)?\b"""),
        Regex("""(?i)\b(\d{1,3})\s*(minutos?|horas?|d[ií]as?)\s+antes\b""")
    )
    private val durationPatterns = listOf(
        Regex("""(?i)\((\d{1,3})\s*(minutos?|min|horas?|hora)\)"""),
        Regex("""(?i)\b(?:durante|por)\s+(\d{1,3})\s*(minutos?|min|horas?|hora)\b"""),
        Regex("""(?i)\b(\d{1,3})\s*(minutos?|min)\b"""),
        Regex("""(?i)\b(\d{1,3})\s*(horas?)\b""")
    )

    /** "urgente" como palabra inicial, para detección de prioridad sin prefijo. */
    private val leadingUrgentPattern = Regex("""(?i)^urgente\b""")

    /** Partes del día: "esta mañana/tarde/noche". Implican fecha=hoy + hora canónica. */
    private val partOfDayPattern = Regex("""(?i)\besta\s+(ma[nñ]ana|tarde|noche)\b""")
    private val partOfDayTimes = mapOf(
        "mañana" to LocalTime.of(9, 0),
        "manana" to LocalTime.of(9, 0),
        "tarde" to LocalTime.of(15, 0),
        "noche" to LocalTime.of(21, 0)
    )

    /**
     * Parte del día suelta: "a la tarde/noche/madrugada", "de la tarde/noche/madrugada".
     * NO fuerza fecha (solo hora del día sobre la fecha parseada). Sirve como contexto PM
     * para horas sin meridiem ("mañana a la tarde a las 4" → 16:00) y como hora canónica
     * de respaldo ("jugar tenis de la tarde" → 15:00). "mañana/madrugada" son AM.
     */
    private val standalonePartOfDayPattern = Regex("""(?i)\b(?:a\s+la|de\s+la)\s+(tarde|noche|madrugada)\b""")
    private val standalonePartOfDayTimes = mapOf(
        "tarde" to LocalTime.of(15, 0),
        "noche" to LocalTime.of(21, 0),
        "madrugada" to LocalTime.of(4, 0)
    )

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

        val lower = working.lowercase()
        val priority = when {
            "!urgente" in lower || "#urgente" in lower -> TaskPriority.URGENT
            "!alta" in lower || "#alta" in lower -> TaskPriority.HIGH
            "!baja" in lower || "#baja" in lower -> TaskPriority.LOW
            // "urgente" como palabra inicial (ej. "urgente enviar documento mañana")
            // sin prefijo. No se detecta a mitad de frase para evitar falsos positivos
            // como "no es urgente".
            leadingUrgentPattern.containsMatchIn(lower) -> TaskPriority.URGENT
            else -> TaskPriority.NORMAL
        }
        working = working.replace(Regex("""(?i)(?:!|#)(urgente|alta|baja)\b"""), " ")
            .replace(leadingUrgentPattern, " ")

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

        // Fecha relativa "en/dentro de N minutos/horas/días" (N = dígitos o palabra).
        val relativeMatch = relativePattern.find(working)
        val relativeDueAt = relativeMatch?.let { match ->
            val amount = parseWrittenNumber(match.groupValues[1]) ?: 0L
            val unit = match.groupValues[2].lowercase()
            val millis = when {
                unit.startsWith("min") -> amount * 60_000L
                unit.startsWith("hora") -> amount * 60 * 60_000L
                else -> amount * 24 * 60 * 60_000L
            }
            now + millis
        }
        relativeMatch?.let { working = working.replace(it.value, " ") }

        // Repetición: se procesa antes que la fecha para que "cada viernes" no se lea como fecha suelta.
        val recurrence = parseRecurrence(working)
        recurrence.phraseRanges.sortedByDescending { it.first }.forEach { range ->
            working = working.substring(0, range.first) + " " + working.substring(range.last + 1)
        }

        val weekdayMatch = weekdayPattern.find(working)
        val numericDateMatch = numericDatePattern.find(working)
        val monthNameMatch = monthNamePattern.find(working)
        val partOfDayMatch = partOfDayPattern.find(working)
        val partOfDayTime = partOfDayMatch?.let { partOfDayTimes[it.groupValues[1].lowercase()] }
        val standalonePartOfDayMatch = standalonePartOfDayPattern.find(working)
        val standalonePartOfDayKey = standalonePartOfDayMatch?.groupValues?.get(1)?.lowercase()
        val standalonePartOfDayTime = standalonePartOfDayKey?.let { standalonePartOfDayTimes[it] }
        // Contexto PM: una parte del día de tarde/noche (explícita "esta tarde" o suelta "a la noche")
        // aplica offset +12 a una hora sin meridiem ("esta tarde a las 4" → 16:00).
        val partOfDayPmKeys = setOf("tarde", "noche")
        val hasPartOfDayPmContext =
            partOfDayMatch?.let { it.groupValues[1].lowercase() in partOfDayPmKeys } == true ||
            standalonePartOfDayKey in partOfDayPmKeys
        val date = when {
            // Debe ir antes que el "mañana" genérico: "esta mañana" contiene "mañana"
            // y no debe interpretarse como "el día de mañana".
            partOfDayMatch != null -> base.toLocalDate()
            Regex("""(?i)\bpasado\s+mañana\b""").containsMatchIn(working) -> base.toLocalDate().plusDays(2)
            Regex("""(?i)\bmañana\b""").containsMatchIn(working) -> base.toLocalDate().plusDays(1)
            Regex("""(?i)\bhoy\b""").containsMatchIn(working) -> base.toLocalDate()
            weekdayMatch != null -> nextWeekday(base.toLocalDate(), weekdayMatch.groupValues[1].toDayOfWeek())
            monthNameMatch != null -> parseMonthNameDate(base.toLocalDate(), monthNameMatch)
            numericDateMatch != null -> {
                val day = numericDateMatch.groupValues[1].toIntOrNull()
                val month = numericDateMatch.groupValues[2].toIntOrNull()
                val rawYear = numericDateMatch.groupValues[3].toIntOrNull()
                val year = when {
                    rawYear == null -> base.year
                    rawYear < 100 -> 2000 + rawYear
                    else -> rawYear
                }
                if (day == null || month == null) null else {
                    runCatching { LocalDate.of(year, month, day) }.getOrNull()?.let { date ->
                        // Sin año explícito, una fecha pasada se entiende como del próximo año
                        // (consistente con parseMonthNameDate). Evita programar tareas en el
                        // pasado, donde los recordatorios nunca dispararían.
                        if (rawYear == null && date.isBefore(base.toLocalDate())) date.plusYears(1) else date
                    }
                }
            }
            // Repetición semanal con días explícitos: primera ocurrencia futura.
            recurrence.frequency == RecurrenceFrequency.WEEKLY && recurrence.days.isNotEmpty() -> recurrence.days
                .mapNotNull { it.toDayOfWeekOrNull() }
                .map { nextWeekday(base.toLocalDate(), it) }
                .minOrNull()
            else -> null
        }

        val timeMatch = timePatterns.asSequence().mapNotNull { it.find(working) }.minByOrNull { it.range.first }
        val explicitTimeData = timeMatch?.let { match ->
            val mv = match.value.lowercase()
            when {
                mv.contains("mediodía") || mv.contains("mediodia") -> LocalTime.NOON to true
                mv.contains("medianoche") -> LocalTime.MIDNIGHT to true
                else -> {
                    var hour = match.groupValues[1].toInt()
                    val minute = match.groupValues[2].toIntOrNull() ?: 0
                    // "a las 24" / "24:00" = medianoche (00:00), forma común en horarios.
                    // Se marca como meridiem explícito para evitar que el contexto PM de
                    // parte del día aplique un offset (24 ya es absoluto).
                    if (hour == 24) {
                        LocalTime.MIDNIGHT to true
                    } else {
                        val meridiem = match.groupValues[3].lowercase().replace(".", "").replace(" ", "")
                        // "de la tarde"/"de la noche" → 12h posterior; "de la mañana/madrugada" → am.
                        val isPm = meridiem == "pm" || meridiem == "delatarde" || meridiem == "delanoche"
                        val isAm = meridiem == "am" || meridiem == "delamañana" || meridiem == "delamanaana" || meridiem == "delamadrugada"
                        if (isPm && hour < 12) hour += 12
                        if (isAm && hour == 12) hour = 0
                        // "12 de la noche" = medianoche (00:00), no 12:00 del mediodía.
                        if (isPm && hour == 12 && meridiem == "delanoche") hour = 0
                        LocalTime.of(hour, minute) to meridiem.isNotEmpty()
                    }
                }
            }
        }
        val explicitTime = explicitTimeData?.first
        val hasExplicitMeridiem = explicitTimeData?.second == true
        // Un tiempo explícito tiene prioridad sobre la hora canónica de la parte del día.
        // Si la hora explícita vino sin meridiem (p.ej. "a las 4") y hay contexto PM de
        // parte del día ("esta tarde"/"a la noche"), se aplica el offset +12 ("esta tarde
        // a las 4" → 16:00, no 04:00).
        val parsedTime = explicitTime?.let { t ->
            if (!hasExplicitMeridiem && hasPartOfDayPmContext && t.hour in 1..11)
                t.plusHours(12) else t
        } ?: partOfDayTime ?: standalonePartOfDayTime
        val effectiveDate = date ?: if (parsedTime != null) base.toLocalDate() else null
        val dueAt = relativeDueAt ?: effectiveDate?.let { DateRules.toEpochMillis(it, parsedTime ?: LocalTime.of(9, 0), zone) }

        // Duración: no se aplica a "en N minutos" (esa es fecha relativa, ya eliminada).
        val durationMatch = durationPatterns.asSequence()
            .mapNotNull { it.find(working) }
            .minByOrNull { it.range.first }
            ?.takeIf { match -> !Regex("""(?i)\ben\s*$""").containsMatchIn(working.substring(0, match.range.first)) }
        val durationMinutes = durationMatch?.let { match ->
            val amount = match.groupValues[1].toIntOrNull() ?: return@let null
            val unit = match.groupValues[2].lowercase()
            (if (unit.startsWith("hora")) amount * 60 else amount).coerceIn(5, 24 * 60)
        }
        durationMatch?.let { working = working.replace(it.value, " ") }

        val category = categories.firstOrNull { (_, keywords) -> keywords.any { working.contains(it, ignoreCase = true) } }?.first.orEmpty()

        // Limpieza de la frase para el título.
        // Orden crítico: partOfDay ("esta mañana") y las horas ("a las 9 de la mañana")
        // deben eliminarse ANTES del borrado genérico de "mañana"/"hoy", porque ambos
        // contienen "mañana"; si se borra primero, dejan restos huérfanos ("esta", "de la").
        working = working
            .let { value -> partOfDayPattern.replace(value, " ") }
            .let { value -> timePatterns.fold(value) { acc, pattern -> pattern.replace(acc, " ") } }
            .let { value -> standalonePartOfDayPattern.replace(value, " ") }
            .replace(Regex("""(?i)\bpasado\s+mañana\b|\bmañana\b|\bhoy\b"""), " ")
            .let { value -> weekdayPattern.replace(value, " ") }
            // Solo se elimina la fecha "5 de marzo" si el mes es válido: así "9 de la"
            // (en "a las 9 de la tarde") no se destruye y deja restos en el título.
            .replace(monthNamePattern) { m ->
                if (months.any { (name, _) ->
                        m.groupValues[2].equals(name, ignoreCase = true)
                    }) " " else m.value
            }
            .let { value -> numericDatePattern.replace(value, " ") }
            .replace(Regex("""(?i)\bantes\s+del?\b|\bpara\s+el\b|\bpara\s+mañana\b|\bhasta\s+el\b"""), " ")
            .replace(Regex("""(?i)\b(para|el)\b\s*$"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', '.', '-')

        val confidence = when {
            relativeDueAt != null -> 1.0f
            dueAt != null && parsedTime != null -> 1.0f
            dueAt != null -> 0.9f
            priority != TaskPriority.NORMAL || durationMinutes != null || reminderOffsetMinutes != null ||
                recurrence.frequency != RecurrenceFrequency.NONE || category.isNotEmpty() -> 0.6f
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

    private fun nextWeekday(from: LocalDate, target: DayOfWeek): LocalDate {
        val delta = (target.value - from.dayOfWeek.value + 7) % 7
        return from.plusDays(if (delta == 0) 7 else delta.toLong())
    }

    private fun String.toDayOfWeek(): DayOfWeek = weekdays[this.lowercase()] ?: DayOfWeek.MONDAY

    private fun String.toDayOfWeekOrNull(): DayOfWeek? = weekdays[this.lowercase()]

    private fun Int.toDayOfWeekOrNull(): DayOfWeek? =
        if (this in 1..7) DayOfWeek.of(this) else null

    /** Convierte un grupo capturado (dígitos o número escrito en español) a Long. */
    private fun parseWrittenNumber(raw: String): Long? {
        raw.toLongOrNull()?.let { return it }
        return when (raw.lowercase().trim()) {
            "un", "una", "uno" -> 1L
            "dos" -> 2L
            "tres" -> 3L
            "cuatro" -> 4L
            "cinco" -> 5L
            "seis" -> 6L
            "siete" -> 7L
            "ocho" -> 8L
            "nueve" -> 9L
            "diez" -> 10L
            "once" -> 11L
            "doce" -> 12L
            else -> null
        }
    }
}
