cat << 'INNER_EOF' > app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt
package com.ordia.app.domain

import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.TaskPriority
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Analizador pequeño y determinista para capturas rápidas en español. No usa red. */
data class ParsedTaskInput(
    val title: String,
    val dueAt: Long?,
    val priority: TaskPriority,
    val recurrence: RecurrenceFrequency = RecurrenceFrequency.NONE,
    val recurrenceDays: String = ""
)

object NaturalTaskParser {
    private val numericDatePattern = Regex("""\b([0-3]?\d)[/-]([01]?\d)(?:[/-](\d{2,4}))?\b""")
    private val weekdayPattern = Regex("""(?i)\b(?:el\s+)?(?:pr[oó]ximo\s+)?(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\b""")
    private val relativePattern = Regex("""(?i)\ben\s+(\d{1,3})\s*(minutos?|mins?|horas?|d[ií]as?)\b""")

    // Additional recurrence patterns
    private val recurrenceEveryDayPattern = Regex("""(?i)\b(?:todos\s+los\s+d[ií]as|cada\s+d[ií]a|diariamente)\b""")
    private val recurrenceEveryWeekPattern = Regex("""(?i)\b(?:todas\s+las\s+semanas|cada\s+semana|semanalmente)\b""")
    private val recurrenceEveryMonthPattern = Regex("""(?i)\b(?:todos\s+los\s+meses|cada\s+mes|mensualmente)\b""")
    private val recurrenceEveryWeekdayPattern = Regex("""(?i)\btodos\s+los\s+(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)s?\b""")

    private val timePatterns = listOf(
        Regex("""(?i)\ba\s+las\s+([01]?\d|2[0-3])(?::([0-5]\d))?\s*(a\.?\s*m\.?|p\.?\s*m\.?)?\b"""),
        Regex("""(?i)\b([01]?\d|2[0-3]):([0-5]\d)\s*(a\.?\s*m\.?|p\.?\s*m\.?)?\b"""),
        Regex("""(?i)\b(0?[1-9]|1[0-2])(?::([0-5]\d))?\s*(a\.?\s*m\.?|p\.?\s*m\.?)\b""")
    )

    fun parse(text: String, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): ParsedTaskInput {
        val base = Instant.ofEpochMilli(now).atZone(zone)
        var working = text.trim()
        val lower = working.lowercase()
        val priority = when {
            "!urgente" in lower || "#urgente" in lower -> TaskPriority.URGENT
            "!alta" in lower || "#alta" in lower -> TaskPriority.HIGH
            "!baja" in lower || "#baja" in lower -> TaskPriority.LOW
            else -> TaskPriority.NORMAL
        }
        working = working.replace(Regex("""(?i)(?:!|#)(urgente|alta|baja)\b"""), " ")

        val relativeMatch = relativePattern.find(working)
        val relativeDueAt = relativeMatch?.let { match ->
            val amount = match.groupValues[1].toLongOrNull() ?: 0L
            val unit = match.groupValues[2].lowercase()
            val millis = when {
                unit.startsWith("min") -> amount * 60_000L
                unit.startsWith("hora") -> amount * 60 * 60_000L
                else -> amount * 24 * 60 * 60_000L
            }
            now + millis
        }

        val recurrenceWeekdayMatch = recurrenceEveryWeekdayPattern.find(working)
        val weekdayMatch = weekdayPattern.find(working)
        val numericDateMatch = numericDatePattern.find(working)

        var recurrence = RecurrenceFrequency.NONE
        var recurrenceDays = ""

        val date = when {
            Regex("""(?i)\bpasado\s+mañana\b""").containsMatchIn(working) -> base.toLocalDate().plusDays(2)
            Regex("""(?i)\bmañana\b""").containsMatchIn(working) -> base.toLocalDate().plusDays(1)
            Regex("""(?i)\bhoy\b""").containsMatchIn(working) -> base.toLocalDate()
            recurrenceWeekdayMatch != null -> {
                recurrence = RecurrenceFrequency.WEEKLY
                recurrenceDays = recurrenceWeekdayMatch.groupValues[1].toDayOfWeek().value.toString()
                nextWeekday(
                    base.toLocalDate(),
                    recurrenceWeekdayMatch.groupValues[1].toDayOfWeek()
                )
            }
            weekdayMatch != null -> nextWeekday(
                base.toLocalDate(),
                weekdayMatch.groupValues[1].toDayOfWeek()
            )
            recurrenceEveryDayPattern.containsMatchIn(working) -> {
                recurrence = RecurrenceFrequency.DAILY
                base.toLocalDate().plusDays(1)
            }
            recurrenceEveryWeekPattern.containsMatchIn(working) -> {
                recurrence = RecurrenceFrequency.WEEKLY
                base.toLocalDate().plusWeeks(1)
            }
            recurrenceEveryMonthPattern.containsMatchIn(working) -> {
                recurrence = RecurrenceFrequency.MONTHLY
                base.toLocalDate().plusMonths(1)
            }
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
            else -> null
        }

        val timeMatch = timePatterns.asSequence().mapNotNull { it.find(working) }.minByOrNull { it.range.first }
        val parsedTime = timeMatch?.let { match ->
            var hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            val meridiem = match.groupValues[3].lowercase().replace(".", "").replace(" ", "")
            if (meridiem == "pm" && hour < 12) hour += 12
            if (meridiem == "am" && hour == 12) hour = 0
            LocalTime.of(hour, minute)
        }
        val effectiveDate = date ?: if (parsedTime != null) base.toLocalDate() else null
        val dueAt = relativeDueAt ?: effectiveDate?.let { DateRules.toEpochMillis(it, parsedTime ?: LocalTime.of(9, 0), zone) }

        relativeMatch?.value?.let { working = working.replace(it, " ") }
        recurrenceWeekdayMatch?.value?.let { working = working.replace(it, " ") }
        recurrenceEveryDayPattern.find(working)?.value?.let { working = working.replace(it, " ") }
        recurrenceEveryWeekPattern.find(working)?.value?.let { working = working.replace(it, " ") }
        recurrenceEveryMonthPattern.find(working)?.value?.let { working = working.replace(it, " ") }
        weekdayMatch?.value?.let { working = working.replace(it, " ") }
        timeMatch?.value?.let { working = working.replace(it, " ") }

        working = working
            .replace(Regex("""(?i)\bpasado\s+mañana\b|\bmañana\b|\bhoy\b"""), " ")
            .let { value -> numericDatePattern.replace(value, " ") }
            .replace(Regex("""(?i)\b(para|el)\b\s*$"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', '.', '-')

        return ParsedTaskInput(
            title = working.ifBlank { text.trim() }.take(240),
            dueAt = dueAt,
            priority = priority,
            recurrence = recurrence,
            recurrenceDays = recurrenceDays
        )
    }


    private fun nextWeekday(from: LocalDate, target: DayOfWeek): LocalDate {
        val delta = (target.value - from.dayOfWeek.value + 7) % 7
        return from.plusDays(if (delta == 0) 7 else delta.toLong())
    }

    private fun String.toDayOfWeek(): DayOfWeek = when (lowercase()) {
        "lunes" -> DayOfWeek.MONDAY
        "martes" -> DayOfWeek.TUESDAY
        "miércoles", "miercoles" -> DayOfWeek.WEDNESDAY
        "jueves" -> DayOfWeek.THURSDAY
        "viernes" -> DayOfWeek.FRIDAY
        "sábado", "sabado" -> DayOfWeek.SATURDAY
        else -> DayOfWeek.SUNDAY
    }
}
INNER_EOF
