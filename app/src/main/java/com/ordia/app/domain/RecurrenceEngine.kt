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
        RecurrenceFrequency.DAILY -> base.plusDays(interval)
        RecurrenceFrequency.WEEKLY -> nextWeekly(base, interval, days)
        RecurrenceFrequency.MONTHLY -> nextMonthly(base, interval)
        RecurrenceFrequency.YEARLY -> nextYearly(base, interval)
    }

    /**
     * Avanza una recurrencia mensual anclada al día del mes de [base], saltando los
     * meses que no contienen ese día (p. ej. "el 31 de cada mes" salta feb → mar 31)
     * en lugar de clampar a feb 28. Así el motor coincide con el anclaje que usa
     * `NaturalTaskParser.nextMonthlyDate`, evitando deriva silenciosa del día
     * (31 → 30 → 30…) tras el primer ciclo. Conserva la hora y zona de `base`.
     */
    private fun nextMonthly(base: ZonedDateTime, interval: Long): ZonedDateTime {
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
