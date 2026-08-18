package com.ordia.app.domain

import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

object RecurrenceEngine {
    /**
     * Sentinel en `recurrenceDays` que codifica una recurrencia mensual anclada al
     * ÚLTIMO día real del mes ("cada fin de mes", c.257). A diferencia del anclaje por
     * día del mes (día 31), que salta meses sin 31 (febrero, abril, junio, septiembre,
     * noviembre) al siguiente mes con 31, este anclaje aterriza siempre en el último
     * día del mes objetivo (28/29 en febrero, 30 en meses cortos) sin omitir ciclos:
     * un pago/cierre mensual recurrente no se "salta" un mes. Sólo aplica a MONTHLY;
     * `nextMonthly` lo detecta y despacha a [nextMonthlyLastDay].
     */
    const val LAST_DAY_OF_MONTH = "EOM"

    fun isLastDayOfMonthEncoding(value: String): Boolean = value.trim() == LAST_DAY_OF_MONTH

    /**
     * Sentinel en `recurrenceDays` que codifica una recurrencia mensual anclada al
     * ÚLTIMO DÍA HÁBIL del mes ("el último día hábil/laborable de cada mes", c.575).
     * A diferencia de [LAST_DAY_OF_MONTH] (que aterriza en el último día REAL del mes,
     * incluido sábado/domingo), éste rueda al viernes anterior cuando el último día
     * del mes objetivo cae en fin de semana. Definición honesta: "hábil" = Lunes-Viernes
     * (sin festivos locales, que requerirían un calendario por jurisdicción que la app
     * no tiene — se documenta la limitación en lugar de fingirla). Un pago/nómina/alquiler
     * vencido "el último día hábil" no se programa en sábado (cuando el banco/entidad no
     * opera): se ancla al viernes previo. Sólo aplica a MONTHLY; `nextMonthly` lo detecta
     * y despacha a [nextMonthlyLastBusinessDay].
     */
    const val LAST_BUSINESS_DAY_OF_MONTH = "EOM-BD"

    fun isLastBusinessDayOfMonthEncoding(value: String): Boolean = value.trim() == LAST_BUSINESS_DAY_OF_MONTH

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
     * silenciosamente. Si [days] es [LAST_DAY_OF_MONTH] (`"EOM"`), se ancla al
     * último día REAL del mes objetivo (c.257): un cierre mensual no salta meses
     * cortos. Si [days] está vacío, conserva el anclaje al día del mes.
     */
    private fun nextMonthly(base: ZonedDateTime, interval: Long, days: String): ZonedDateTime {
        val ordinal = parseOrdinalWeekday(days)
        if (ordinal != null) return nextMonthlyOrdinal(base, interval, ordinal.first, ordinal.second)
        if (isLastDayOfMonthEncoding(days)) return nextMonthlyLastDay(base, interval)
        if (isLastBusinessDayOfMonthEncoding(days)) return nextMonthlyLastBusinessDay(base, interval)
        // c.315: lista de días del mes ("d:1,15") — quincena/nómina con varios días por ciclo.
        parseMonthlyDayList(days)?.let { return nextMonthlyDayList(base, interval, it) }
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
     * Avanza [interval] meses desde [base] y aterriza en el último día REAL de ese
     * mes objetivo (28/29 en febrero, 30 en meses cortos, 31 en largos), conservando
     * hora y zona de [base]. Así "cada fin de mes" no omite meses cortos: el anclaje
     * por día del mes (día 31) saltaría febrero entero; este anclaje lo respeta.
     */
    private fun nextMonthlyLastDay(base: ZonedDateTime, interval: Long): ZonedDateTime {
        val ym = YearMonth.from(base).plusMonths(interval.coerceAtLeast(1))
        return base.withYear(ym.year).withMonth(ym.monthValue).withDayOfMonth(ym.lengthOfMonth())
    }

    /**
     * Avanza [interval] meses desde [base] y aterriza en el ÚLTIMO DÍA HÁBIL
     * (Lunes-Viernes) de ese mes objetivo: si el último día real cae en fin de
     * semana (sábado/domingo), rueda al viernes anterior. Conserva hora y zona de
     * [base]. Simétrico a [nextMonthlyLastDay] (c.257), pero respeta la semana
     * laboral: "el último día hábil de cada mes" no agenda un pago el sábado (c.575).
     */
    private fun nextMonthlyLastBusinessDay(base: ZonedDateTime, interval: Long): ZonedDateTime {
        val ym = YearMonth.from(base).plusMonths(interval.coerceAtLeast(1))
        var day = ym.lengthOfMonth()
        while (LocalDate.of(ym.year, ym.monthValue, day).dayOfWeek == DayOfWeek.SATURDAY ||
            LocalDate.of(ym.year, ym.monthValue, day).dayOfWeek == DayOfWeek.SUNDAY) {
            day -= 1
        }
        return base.withYear(ym.year).withMonth(ym.monthValue).withDayOfMonth(day)
    }

    /**
     * Codificación ordinal mensual en `recurrenceDays`: `"ord:weekday"` (formato
     * literal `"$ord:$wd"`, p. ej. `"1:1"` = 1er lunes, `"-1:5"` = último viernes),
     * con `ord ∈ {1,2,3,4,5,-1,-2,-3}` (-1 = último, -2 = penúltimo, -3 =
     * antepenúltimo; 5 = "quinto", sólo existe en meses con 5 ocurrencias del
     * weekday) y `weekday ∈ 1..7` (ISO, 1=lunes). Devuelve `(ord, weekday)`
     * o `null` si [value] no casa (incluido el día del mes puro, que usa
     * `recurrenceDays` vacío). Compartido por el motor y las reglas de seguridad
     * de backup (validación única de la codificación). El parser
     * (`NaturalTaskParser`) emite exactamente este formato para MONTHLY ordinal;
     * cambiarlo aquí o allí rompería el anclaje anti-deriva (c.216).
     */
    fun parseOrdinalWeekday(value: String): Pair<Int, Int>? {
        val parts = value.trim().split(':')
        if (parts.size != 2) return null
        val ord = parts[0].toIntOrNull() ?: return null
        val wd = parts[1].toIntOrNull() ?: return null
        if (ord !in -3..5 || ord == 0) return null
        if (wd !in 1..7) return null
        return ord to wd
    }

    /**
     * Prefijo en `recurrenceDays` que codifica una recurrencia mensual anclada a una
     * LISTA de días del mes (`"d:N1,N2"`, p. ej. `"d:1,15"` = días 1 y 15 de cada mes,
     * c.315). A diferencia del anclaje por día del mes puro (vacío), que dispara UNA
     * ocurrencia por ciclo, éste dispara una ocurrencia por CADA día de la lista
     * dentro del mes (quincena/nómina/cobro: el 1 y el 15). Simétrico a los ordinales
     * `"ord:wd"` (c.216) y al sentinel `"EOM"` (c.257): se reutiliza `recurrenceDays`
     * (String) para un nuevo anclaje sin tocar Room. Antes el 2º día se perdía
     * silenciosamente (el parser sólo anclaba el 1º): un día de pago real nacía
     * olvidado (P1, pérdida de datos).
     */
    private const val MONTHLY_DAY_LIST_PREFIX = "d"

    /**
     * ¿Codifica [value] una lista mensual de días (`"d:N1,N2,…"`, c.315)?
     * Devuelve `true` sólo si el prefijo y la lista son válidos (días 1..31 únicos,
     * ≥1 elemento). Lo usa la validación de restore de [BackupManager] para aceptar
     * esta codificación (simétrico a `isLastDayOfMonthEncoding` y `parseOrdinalWeekday`).
     */
    fun isMonthlyDayListEncoding(value: String): Boolean = parseMonthlyDayList(value) != null

    /**
     * Decodifica una lista mensual de días (`"d:N1,N2"`, c.315) a `List<Int>` ordenada
     * ascendente y sin duplicados, o `null` si el formato o los días son inválidos
     * (días fuera de 1..31, lista vacía, prefijo ausente). Compartido por el motor
     * (`nextMonthlyDayList`) y la validación de restore.
     */
    fun parseMonthlyDayList(value: String): List<Int>? {
        val trimmed = value.trim()
        if (!trimmed.startsWith("$MONTHLY_DAY_LIST_PREFIX:")) return null
        val body = trimmed.substring("$MONTHLY_DAY_LIST_PREFIX:".length).trim()
        if (body.isEmpty()) return null
        val tokens = body.split(',')
        // Strict: si algún token no es entero o cae fuera de 1..31, la codificación
        // entera es inválida (null) — así el restore RECHAZA "d:1,99" en vez de
        // aceptarla silenciosamente como [1] y dejar la recurrencia derivando.
        val days = tokens.mapNotNull { token ->
            val n = token.trim().toIntOrNull()
            if (n != null && n in 1..31) n else null
        }
        if (days.size != tokens.size) return null
        return days.distinct().sorted().takeIf { it.isNotEmpty() }
    }

    /**
     * Avanza una recurrencia mensual con varios días del mes (`days` = lista
     * `"d:N1,N2"`, c.315). La próxima ocurrencia es el MENOR día de la lista que sea
     * ESTRICTAMENTE posterior a `base.dayOfMonth` DENTRO del mismo mes (p. ej. tras el
     * 1 → 15 del mismo mes). Si ningún día de la lista cabe en el mes restante (o no
     * existe en el mes por ser corto, p. ej. 30 en febrero), avanza `interval` meses y
     * aterriza en el menor día de la lista de ese mes objetivo (saltando meses cortos,
     * igual que el anclaje por día del mes puro, sin clampar). Conserva hora y zona de
     * [base].
     */
    private fun nextMonthlyDayList(base: ZonedDateTime, interval: Long, days: List<Int>): ZonedDateTime {
        val sameMonthNext = days.firstOrNull { it > base.dayOfMonth && it <= YearMonth.from(base).lengthOfMonth() }
        if (sameMonthNext != null) {
            return base.withDayOfMonth(sameMonthNext)
        }
        // No cabe otro día este mes (o no existe en mes corto): avanzar al menor día
        // de la lista en el mes objetivo, saltando meses donde el menor día no exista.
        val target = days.min()
        var ym = YearMonth.from(base).plusMonths(interval.coerceAtLeast(1))
        repeat(24) {
            if (target <= ym.lengthOfMonth()) {
                return base.withYear(ym.year).withMonth(ym.monthValue).withDayOfMonth(target)
            }
            ym = ym.plusMonths(1)
        }
        return base.plusMonths(interval.coerceAtLeast(1))
    }

    /**
     * Avanza [interval] meses desde [base] y recalcula el N-ésimo (`ord` 1..5) o
     * último (`ord` = -1) día de la semana `weekday` (ISO 1..7) de ese mes,
     * conservando hora y zona de [base]. Así el anclaje ordinal persiste ciclo a
     * ciclo sin deriva al día del mes. `ord=5` ("quinto") sólo existe en meses de
     * 31 días cuyo día 1 cae en el weekday adecuado; cuando el mes objetivo no
     * tiene 5ª ocurrencia se avanza mes a mes hasta hallar uno que sí (hasta 24
     * iteraciones), de modo que "el quinto viernes de cada mes" se programa sólo
     * en los meses que tienen quinto viernes en vez de colapsar o saltar al día 35
     * (que lanzaría `DateTimeException` y corrompería la recurrencia).
     */
    private fun nextMonthlyOrdinal(base: ZonedDateTime, interval: Long, ord: Int, weekday: Int): ZonedDateTime {
        var ym = YearMonth.from(base).plusMonths(interval.coerceAtLeast(1))
        if (ord >= 5) {
            var guard = 0
            while (nthWeekdayInMonth(ym, ord, weekday) == null && guard++ < 24) ym = ym.plusMonths(1)
        }
        val target = nthWeekdayInMonth(ym, ord, weekday) ?: nthWeekdayInMonth(ym, -1, weekday)!!
        return base.withYear(ym.year).withMonth(ym.monthValue).withDayOfMonth(target)
    }

    /**
     * Día del mes del N-ésimo (`n` 1..5), último (`n` = -1), penúltimo (`n` = -2)
     * o antepenúltimo (`n` = -3) día de la semana `weekday` (ISO 1..7) en [ym].
     * Devuelve `null` si la N-ésima ocurrencia no existe en [ym] (sólo posible
     * para `n >= 5`: un mes tiene 5 de un weekday dado únicamente si tiene 31
     * días y el día 1 cae en ese weekday o antes en la semana). Simétrico al
     * cálculo del parser para la 1ª ocurrencia, garantizando que motor y parser
     * acuerdan el mismo anclaje ciclo a ciclo.
     */
    private fun nthWeekdayInMonth(ym: YearMonth, n: Int, weekday: Int): Int? {
        if (n < 0) {
            val lastDay = ym.lengthOfMonth()
            val lastWd = ym.atDay(lastDay).dayOfWeek.value
            val back = (lastWd - weekday + 7) % 7
            // n = -1 → último; -2 → penúltimo (−1 semana); -3 → antepenúltimo (−2 semanas).
            return lastDay - back - (-n - 1) * 7
        }
        val first = ym.atDay(1)
        val firstWd = first.dayOfWeek.value
        val offset = (weekday - firstWd + 7) % 7
        val day = 1 + offset + (n - 1) * 7
        return if (day <= ym.lengthOfMonth()) day else null
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

    /**
     * Identifica la ocurrencia generada al completar [original] que debe revertirse
     * al des-completar la tarea, para evitar el duplicado huérfano: completar una
     * recurrente genera la próxima ocurrencia (c.223, fila nueva con
     * `createdAt = completedAt` y la misma huella de recurrencia); si el usuario
     * deshace, esa ocurrencia generada quedaba activa además de la original
     * restaurada → dos tareas por la misma recurrencia (BACKLOG c.259).
     *
     * Enlace implícito pero fiable: [nextOccurrence] fija `createdAt = completedAt`
     * y conserva la huella de recurrencia (frecuencia + intervalo + días). Como
     * `TaskMutationGate` serializa los toggles, no puede colisionar el instante de
     * creación entre recurrencias distintas; se refina además exigiendo que el
     * `dueAt` del candidato sea EXACTAMENTE el que [nextOccurrence] calcula.
     *
     * Datos sagrados: SÓLO se revierte si la ocurrencia generada sigue prístina
     * (status PLANNED inicial, no iniciada, no completada, no cancelada, no
     * archivada, mismo título y detalles). Si el usuario ya la editó/empezó/completó,
     * se conserva (no se pierde trabajo real) y se acepta el duplicado como mal menor.
     *
     * Devuelve el id a revertir, o `null` si no hay ocurrencia prístina que revertir.
     */
    fun spawnedOccurrenceToRevert(
        original: TaskEntity,
        allTasks: List<TaskEntity>,
        completedAt: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long? {
        if (original.recurrence == RecurrenceFrequency.NONE) return null
        if (completedAt <= 0L) return null
        val expected = nextOccurrence(original, completedAt, zone) ?: return null
        val expectedDue = expected.dueAt ?: return null
        return allTasks.firstOrNull { task ->
            task.id != original.id &&
                task.createdAt == completedAt &&
                task.recurrence == original.recurrence &&
                task.recurrenceInterval == original.recurrenceInterval &&
                task.recurrenceDays == original.recurrenceDays &&
                task.dueAt == expectedDue &&
                task.title == original.title &&
                task.details == original.details &&
                !task.completed &&
                !task.archived &&
                task.status == TaskStatus.PLANNED
        }?.id
    }

    private const val MIN_START_LEAD_MS = 60_000L
}
