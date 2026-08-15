package com.ordia.app.domain

import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import java.time.Instant
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

object RecurrenceEngine {
    fun nextOccurrence(task: TaskEntity, completedAt: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): TaskEntity? {
        if (task.recurrence == RecurrenceFrequency.NONE) return null
        val interval = task.recurrenceInterval.coerceAtLeast(1).toLong()
        val base = task.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone) } ?: Instant.ofEpochMilli(completedAt).atZone(zone)
        var next = advance(base, interval, task.recurrence, task.recurrenceDays)
        var guard = 0
        while (next.toInstant().toEpochMilli() <= completedAt && guard++ < 10_000) {
            next = advance(next, interval, task.recurrence, task.recurrenceDays)
        }
        if (next.toInstant().toEpochMilli() <= completedAt) return null
        val nextDue = next.toInstant().toEpochMilli()
        val reminderOffset = if (task.dueAt != null && task.reminderAt != null) task.dueAt - task.reminderAt else null
        val startOffset = if (task.dueAt != null && task.startAt != null) task.dueAt - task.startAt else null
        // El offset de recordatorio se traslada a la próxima ocurrencia como
        // `nextDue - offset` ("25 días antes" sigue siendo 25 días antes). Pero si
        // ese instante cae en el pasado (offset grande + ocurrencia próxima por
        // haber completado tarde), ReminderSync lo descarta (trigger <= now) y la
        // nueva ocurrencia nacía SIN aviso -> olvido de la próxima cita. Cae al
        // default adaptativo past-safe (nunca en el pasado) cuando el trasladado
        // ya venció. Simétrico con ReminderRules.resolveReminderAt (c.183) y con
        // AutomationActionPlanner (c.187/c.188).
        val translatedReminder = reminderOffset?.let { nextDue - it }
        val resolvedReminder = when {
            translatedReminder == null -> null
            translatedReminder > completedAt -> translatedReminder
            else -> ReminderRules.defaultReminderAt(nextDue, completedAt)
        }
        // Offset de INICIO simétrico al de recordatorio (c.189): el `startAt`
        // trasladado = `nextDue - startOffset` ("empieza N antes del vencimiento").
        // Si la antelación es mayor que el intervalo a la próxima ocurrencia (p.ej.
        // tarea que empieza 6 semanas antes de un vencimiento mensual) y se completó
        // tarde, el instante trasladado cae en el PASADO y la nueva ocurrencia nacía
        // como "inicio perdido" (isMissedStart) sin que el usuario la hubiese
        // empezado todavía. Se conserva el offset exacto cuando el trasladado es
        // futuro; si no, se reclampa a un inicio útil (futuro, < due).
        val translatedStart = startOffset?.let { nextDue - it }
        val resolvedStart = when {
            translatedStart == null -> null
            translatedStart > completedAt -> translatedStart
            else -> pastSafeStart(nextDue, completedAt, startOffset)
        }
        return task.copy(
            id = 0,
            startAt = TaskRules.coerceStartAt(resolvedStart, nextDue),
            dueAt = nextDue,
            reminderAt = resolvedReminder,
            status = TaskStatus.PLANNED,
            completed = false,
            completedAt = null,
            createdAt = completedAt,
            updatedAt = completedAt
        )
    }

    /**
     * Inicio past-safe para una ocurrencia cuyo `startAt` trasladado quedó en el
     * pasado. Preserva la antelación preferida del usuario (`preferredLead`) cuando
     * es posible; si no, reclampa a la mitad del tiempo restante hasta el
     * vencimiento (piso de 1 min) para que la tarea nazca "a punto de empezar" en
     * lugar de "ya perdida". Devuelve `null` si no queda ventana útil antes del
     * vencimiento. Simétrico a [ReminderRules.defaultReminderAt].
     */
    private fun pastSafeStart(dueAt: Long, now: Long, preferredLead: Long): Long? {
        val ideal = dueAt - preferredLead
        if (ideal > now) return ideal
        val remaining = dueAt - now
        if (remaining <= MIN_START_LEAD_MS) return null
        val lead = minOf(preferredLead, maxOf(MIN_START_LEAD_MS, remaining / 2))
        val clamped = dueAt - lead
        return if (clamped > now) clamped else null
    }

    private fun advance(base: ZonedDateTime, interval: Long, frequency: RecurrenceFrequency, days: String): ZonedDateTime = when (frequency) {
        RecurrenceFrequency.NONE -> base
        RecurrenceFrequency.HOURLY -> base.plusHours(interval)
        RecurrenceFrequency.DAILY -> base.plusDays(interval)
        RecurrenceFrequency.WEEKLY -> nextWeekly(base, interval, days)
        RecurrenceFrequency.MONTHLY -> nextMonthly(base, interval, days)
        RecurrenceFrequency.YEARLY -> nextYearly(base, interval)
    }

    /**
     * Avanza una recurrencia mensual. Si [days] codifica un ordinal de día de la
     * semana (`"ord:weekday"`, p. ej. `"1:1"` = primer lunes, `"-1:5"` = último
     * viernes), la próxima ocurrencia se ancla al N-ésimo/último día de la semana
     * del mes objetivo (c.216) en lugar del día del mes; sin esto, "primer lunes
     * de cada mes" derivaba al día 7 de cada mes y la 2ª cita se desplazaba
     * silenciosamente. Si [days] está vacío, conserva el anclaje al día del mes.
     */
    private fun nextMonthly(base: ZonedDateTime, interval: Long, days: String): ZonedDateTime {
        val ordinal = parseOrdinalWeekday(days)
        if (ordinal != null) return nextMonthlyOrdinal(base, interval, ordinal.first, ordinal.second)
        val day = base.dayOfMonth
        var ym = YearMonth.from(base).plusMonths(interval)
        repeat(24) {
            if (day <= ym.lengthOfMonth()) {
                return base.withYear(ym.year).withMonth(ym.monthValue).withDayOfMonth(day)
            }
            ym = ym.plusMonths(1)
        }
        // Reserva: día ≤ 31 siempre halla mes válido en 24 iteraciones.
        return base.plusMonths(interval)
    }

    /**
     * Codificación ordinal mensual en `recurrenceDays`: `"ord:weekday"` (formato
     * literal `"$ord:$wd"`, p. ej. `"1:1"` = 1er lunes, `"-1:5"` = último viernes),
     * con `ord ∈ {1,2,3,4,-1}` (-1 = último) y `weekday ∈ 1..7` (ISO, 1=lunes).
     * Devuelve `(ord, weekday)` o `null` si [value] no casa (incluido el día del
     * mes puro, que usa `recurrenceDays` vacío). Compartido por el motor y las
     * reglas de seguridad de backup (validación única de la codificación). El
     * parser (`NaturalTaskParser`) emite exactamente este formato para MONTHLY
     * ordinal; cambiarlo aquí o allí rompería el anclaje anti-deriva (c.216).
     */
    fun parseOrdinalWeekday(value: String): Pair<Int, Int>? {
        val parts = value.trim().split(':')
        if (parts.size != 2) return null
        val ord = parts[0].toIntOrNull() ?: return null
        val wd = parts[1].toIntOrNull() ?: return null
        if (ord !in -1..4 || ord == 0) return null
        if (wd !in 1..7) return null
        return ord to wd
    }

    /**
     * Avanza [interval] meses desde [base] y recalcula el N-ésimo (`ord` 1..4) o
     * último (`ord` = -1) día de la semana `weekday` (ISO 1..7) de ese mes,
     * conservando hora y zona de [base]. Así el anclaje ordinal persiste ciclo a
     * ciclo sin deriva al día del mes.
     */
    private fun nextMonthlyOrdinal(base: ZonedDateTime, interval: Long, ord: Int, weekday: Int): ZonedDateTime {
        val ym = YearMonth.from(base).plusMonths(interval.coerceAtLeast(1))
        val target = nthWeekdayInMonth(ym, ord, weekday)
        return base.withYear(ym.year).withMonth(ym.monthValue).withDayOfMonth(target)
    }

    /**
     * Día del mes del N-ésimo (`n` 1..4) o último (`n` = -1) día de la semana
     * `weekday` (ISO 1..7) en [ym]. Simétrico al cálculo del parser para la 1ª
     * ocurrencia, garantizando que motor y parser acuerdan el mismo anclaje.
     */
    private fun nthWeekdayInMonth(ym: YearMonth, n: Int, weekday: Int): Int {
        val first = ym.atDay(1)
        val firstWd = first.dayOfWeek.value
        val offset = (weekday - firstWd + 7) % 7
        if (n == -1) {
            val lastDay = ym.lengthOfMonth()
            val lastWd = ym.atDay(lastDay).dayOfWeek.value
            val back = (lastWd - weekday + 7) % 7
            return lastDay - back
        }
        return 1 + offset + (n - 1) * 7
    }

    /**
     * Avanza una recurrencia anual conservando el ancla de [base]. Simétrico a
     * [nextMonthly]: para el 29 de febrero (única fecha que no existe en años no
     * bisiestos), `plusYears(interval)` clamparía a 28/2 y deriva el ancla para
     * siempre (29/2 → 28/2 → 28/2…), perdiendo cumpleaños/aniversarios caídos en
     * día bisiesto. Aquí se salta a los años en que el 29 de febrero sí existe,
     * respetando [interval] como paso mínimo. Cualquier otra fecha es estable con
     * `plusYears`, así que se usa directo. Conserva hora y zona de [base].
     */
    private fun nextYearly(base: ZonedDateTime, interval: Long): ZonedDateTime {
        if (base.monthValue != 2 || base.dayOfMonth != 29) return base.plusYears(interval)
        var y = base.year + interval.toInt().coerceAtLeast(1)
        repeat(8) {
            if (Year.isLeap(y.toLong())) {
                return base.withYear(y)
            }
            y++
        }
        // Reserva: siempre hay un año bisiesto en 8 iteraciones.
        return base.plusYears(interval)
    }

    private fun nextWeekly(base: ZonedDateTime, interval: Long, recurrenceDays: String): ZonedDateTime {
        val days = recurrenceDays.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.distinct().sorted()
        if (days.isEmpty()) return base.plusWeeks(interval)
        val current = base.dayOfWeek.value
        val later = days.firstOrNull { it > current }
        return if (later != null) base.plusDays((later - current).toLong())
        else base.plusWeeks(interval).minusDays((current - days.first()).toLong())
    }

    private const val MIN_START_LEAD_MS = 60_000L
}
