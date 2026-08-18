package com.ordia.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object DateRules {
    fun toEpochMillis(date: LocalDate, time: LocalTime = LocalTime.of(9, 0), zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()


    /** Converts a date plus an absolute minute offset into epoch milliseconds.
     * Offsets at or beyond 24 hours continue on the following day.
     */
    fun toEpochMillis(date: LocalDate, absoluteMinute: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val safeMinute = absoluteMinute.coerceAtLeast(0)
        val dayOffset = safeMinute / (24 * 60)
        val minuteOfDay = safeMinute % (24 * 60)
        return toEpochMillis(
            date.plusDays(dayOffset.toLong()),
            LocalTime.of(minuteOfDay / 60, minuteOfDay % 60),
            zone
        )
    }

    fun toLocalDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    /**
     * Etiqueta legible de una antigüedad en días: "1 día", "2 días", "1 semana"
     * (7 días), "2 semanas" (14)… Acota a días/semanas/meses para evitar "30
     * días" cuando "4 semanas" comunica mejor cuánto se pospuso. Fuente única de
     * verdad para la edad de un olvido (vencida o captura arrinconada), compartida
     * por el guardián ([GuardianCoach]) y el asistente, de forma que ambas
     * superficies de recuperación muestren la misma etiqueta para la misma edad.
     */
    fun ageLabel(days: Int): String {
        val d = days.coerceAtLeast(1)
        if (d < 7) return "$d ${if (d == 1) "día" else "días"}"
        val weeks = d / 7
        if (weeks < 5) return "$weeks ${if (weeks == 1) "semana" else "semanas"}"
        val months = d / 30
        return "$months ${if (months == 1) "mes" else "meses"}"
    }

    fun toLocalTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalTime =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()

    fun formatDate(epochMillis: Long?, locale: Locale = Locale.getDefault()): String =
        epochMillis?.let {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
                .format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
        } ?: "Sin fecha"

    fun formatTime(epochMillis: Long?, locale: Locale = Locale.getDefault()): String =
        epochMillis?.let {
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
                .format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
        } ?: ""

    fun minutesToClock(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    /**
     * Rango [inicio, fin] de la semana calendario ISO (lun→dom) que contiene a
     * `today`. Fuente única de verdad para "esta semana" / "completadas esta
     * semana": SearchEngine (anclaje en completedAt con `fullCalendarWeek`) y el
     * asistente usan el mismo cálculo, de modo que el logro recuperado en el
     * diálogo coincida con el de la búsqueda. Día de semana ISO: lunes=1..domingo=7.
     * El `% 7` es crítico en domingo: `(7 - 7) % 7 = 0` → la semana termina hoy.
     */
    fun calendarWeekRange(today: LocalDate): Pair<LocalDate, LocalDate> {
        val daysToSunday = (7 - today.dayOfWeek.value) % 7
        val endOfWeek = today.plusDays(daysToSunday.toLong())
        val startOfWeek = today.minusDays((today.dayOfWeek.value - 1).toLong())
        return startOfWeek to endOfWeek
    }

    /**
     * Rango [inicio, fin] del mes natural (1..último día) que contiene a `today`.
     * Fuente única de verdad para "este mes": SearchEngine y el asistente comparten
     * el mismo cálculo (`YearMonth`), evitando que el recap del diálogo discrepe
     * con la búsqueda.
     */
    fun calendarMonthRange(today: LocalDate): Pair<LocalDate, LocalDate> {
        val month = YearMonth.from(today)
        return month.atDay(1) to month.atEndOfMonth()
    }

    /**
     * Rango [inicio, fin] de la semana calendario ISO (lun→dom) inmediatamente
     * anterior a la de `today`. Fuente única de verdad para "semana pasada":
     * SearchEngine (LAST_WEEK) y el recap del asistente usan el mismo cálculo,
     * de modo que "¿qué completé la semana pasada?" recupere el MISMO conjunto
     * que buscar "semana pasada" en lugar de caer a "esta semana" (mentira por
     * omisión del logro de la semana previa).
     */
    fun calendarLastWeekRange(today: LocalDate): Pair<LocalDate, LocalDate> {
        val thisWeek = calendarWeekRange(today)
        val endLastWeek = thisWeek.first.minusDays(1) // domingo pasado
        val startLastWeek = endLastWeek.minusDays(6) // lunes pasado
        return startLastWeek to endLastWeek
    }

    /**
     * Rango [inicio, fin] del mes natural (1..último día) inmediatamente anterior
     * al de `today`. Fuente única de verdad para "mes pasado": SearchEngine
     * (LAST_MONTH) y el recap del asistente comparten el cálculo, evitando que
     * "¿qué completé el mes pasado?" caiga a "este mes" y silencie el logro previo.
     */
    fun calendarLastMonthRange(today: LocalDate): Pair<LocalDate, LocalDate> {
        val lastMonth = YearMonth.from(today).minusMonths(1)
        return lastMonth.atDay(1) to lastMonth.atEndOfMonth()
    }
}
