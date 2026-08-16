package com.ordia.app.domain

import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class RecurrenceEngineTest {
    private val zone = ZoneId.of("America/Santo_Domingo")

    @Test fun none_hasNoNextOccurrence() {
        assertNull(RecurrenceEngine.nextOccurrence(TaskEntity(title = "Una vez"), zone = zone))
    }

    @Test fun daily_preservesTimeAndReminderOffset() {
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.of(9, 30), zone)
        val task = TaskEntity(title = "Diaria", dueAt = due, reminderAt = due - 30 * 60_000L, recurrence = RecurrenceFrequency.DAILY)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(next.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(next.dueAt, zone))
        assertEquals(30 * 60_000L, next.dueAt - next.reminderAt!!)
        assertFalse(next.completed)
    }

    // "cada otro día" / "un día sí y otro no" (DAILY interval=2): al completar avanza
    // exactamente 2 días, no 1. Valida que el intervalo desde el parser se respeta en
    // el motor (medicación cada-dos-días no se vuelve diaria al completar).
    @Test fun dailyInterval2_advancesTwoDays() {
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.of(9, 30), zone)
        val task = TaskEntity(title = "Pastilla cada otro día", dueAt = due, recurrence = RecurrenceFrequency.DAILY, recurrenceInterval = 2)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(next.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(next.dueAt, zone))
    }

    @Test fun weekly_usesSelectedWeekdays() {
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.NOON, zone) // miércoles
        val task = TaskEntity(title = "Lunes y viernes", dueAt = due, recurrence = RecurrenceFrequency.WEEKLY, recurrenceDays = "1,5")
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(next.dueAt!!, zone))
    }

    @Test fun monthly_anchorsToDayOfMonthAndSkipsMonthsLackingIt() {
        // "el 31 de cada mes": ene 31 + 1 mes NO debe dar feb 28 (clamp),
        // sino saltar a mar 31 (feb no tiene 31). Coincide con el anclaje del parser.
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 1, 31), LocalTime.of(8, 0), zone)
        val task = TaskEntity(title = "Mensual 31", dueAt = due, recurrence = RecurrenceFrequency.MONTHLY)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2026, 3, 31), DateRules.toLocalDate(next.dueAt!!, zone))
        assertEquals(LocalTime.of(8, 0), DateRules.toLocalTime(next.dueAt, zone))
    }

    @Test fun monthly_preservesDayForCommonDays() {
        // Dias 1-28: comportamiento estable, sin deriva (caso mas comun: "el 15 de cada mes").
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 1, 15), LocalTime.of(9, 0), zone)
        val task = TaskEntity(title = "Renta", dueAt = due, recurrence = RecurrenceFrequency.MONTHLY)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2026, 2, 15), DateRules.toLocalDate(next.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(next.dueAt, zone))
    }

    @Test fun monthly_advancesPastCompletedAt() {
        // Si se completa despues del vencimiento, no regresa al pasado.
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 1, 31), LocalTime.NOON, zone)
        val task = TaskEntity(title = "Mensual 31", dueAt = due, recurrence = RecurrenceFrequency.MONTHLY)
        val late = DateRules.toEpochMillis(LocalDate.of(2026, 3, 1), LocalTime.NOON, zone)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = late, zone = zone))
        assertEquals(LocalDate.of(2026, 3, 31), DateRules.toLocalDate(next.dueAt!!, zone))
    }

    // "cada fin de mes" (recurrenceDays = "EOM"): anclaje al ÚLTIMO día real de cada
    // mes, sin saltar meses cortos. A diferencia del anclaje por día del mes (día 31),
    // que salta febrero (28/29) y abril/junio/sept/nov (30) al mes siguiente con 31,
    // EOM aterriza siempre en el último día del mes objetivo. Sin esto, "cada fin de
    // mes" completado el 31/1 saltaba al 31/3 (omitía fin de febrero: 28/2) → pago/
    // cierre mensual olvidado (P1). c.257.
    @Test fun monthly_lastDayOfMonth_anchorsToActualLastDayNotSkippingShortMonths() {
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 1, 31), LocalTime.of(9, 0), zone)
        val task = TaskEntity(title = "Reporte cada fin de mes", dueAt = due,
            recurrence = RecurrenceFrequency.MONTHLY, recurrenceDays = RecurrenceEngine.LAST_DAY_OF_MONTH)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        // enero 31 → febrero NO tiene 31, pero EOM aterriza en 28/2 (no salta a 31/3).
        assertEquals(LocalDate.of(2026, 2, 28), DateRules.toLocalDate(next.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(next.dueAt, zone))
    }

    @Test fun monthly_lastDayOfMonth_advancesAcrossShortAndLongMonthsStably() {
        // Cadena fin de mes: 28/2 → 31/3 → 30/4 → 31/5 (cada uno el último día real).
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 2, 28), LocalTime.of(9, 0), zone)
        val task = TaskEntity(title = "Cierre", dueAt = due,
            recurrence = RecurrenceFrequency.MONTHLY, recurrenceDays = RecurrenceEngine.LAST_DAY_OF_MONTH)
        var current = task
        current = requireNotNull(RecurrenceEngine.nextOccurrence(current, completedAt = current.dueAt!!, zone = zone))
        assertEquals(LocalDate.of(2026, 3, 31), DateRules.toLocalDate(current.dueAt!!, zone))
        current = requireNotNull(RecurrenceEngine.nextOccurrence(current, completedAt = current.dueAt!!, zone = zone))
        assertEquals(LocalDate.of(2026, 4, 30), DateRules.toLocalDate(current.dueAt!!, zone))
        current = requireNotNull(RecurrenceEngine.nextOccurrence(current, completedAt = current.dueAt!!, zone = zone))
        assertEquals(LocalDate.of(2026, 5, 31), DateRules.toLocalDate(current.dueAt!!, zone))
    }

    @Test fun monthly_lastDayOfMonth_handlesLeapFebruary() {
        // 2028 es bisiesto: fin de mes de enero (31/1) → 29/2/2028 (no 28).
        val due = DateRules.toEpochMillis(LocalDate.of(2028, 1, 31), LocalTime.of(9, 0), zone)
        val task = TaskEntity(title = "Cierre bisiesto", dueAt = due,
            recurrence = RecurrenceFrequency.MONTHLY, recurrenceDays = RecurrenceEngine.LAST_DAY_OF_MONTH)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2028, 2, 29), DateRules.toLocalDate(next.dueAt!!, zone))
    }

    @Test fun yearly_leapDayAnchorSkipsNonLeapYears() {
        // "aniversario el 29 de febrero de cada año": 29/2/2024 +1 año NO debe dar
        // 28/2/2025 (clamp que deriva el ancla para siempre), sino saltar al próximo
        // 29 de febrero real (2028). Simétrico al mensual 31 de ciclo 18.
        val due = DateRules.toEpochMillis(LocalDate.of(2024, 2, 29), LocalTime.of(12, 0), zone)
        val task = TaskEntity(title = "Aniversario bisiesto", dueAt = due, reminderAt = due - 60 * 60_000L, recurrence = RecurrenceFrequency.YEARLY)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2028, 2, 29), DateRules.toLocalDate(next.dueAt!!, zone))
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(next.dueAt, zone))
        assertEquals(60 * 60_000L, next.dueAt - next.reminderAt!!)
    }

    @Test fun yearly_leapDayAnchorDoesNotDriftAcrossCycles() {
        // Tras completar la ocurrencia de 2028, la siguiente vuelve a ser un 29 de
        // febrero (2032), no 28/2: el ancla NO deriva. Confirma la no-regresión del
        // comportamiento de clamp anterior (que habría dado 28/2/2029).
        val due = DateRules.toEpochMillis(LocalDate.of(2028, 2, 29), LocalTime.of(12, 0), zone)
        val task = TaskEntity(title = "Aniversario bisiesto", dueAt = due, recurrence = RecurrenceFrequency.YEARLY)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2032, 2, 29), DateRules.toLocalDate(next.dueAt!!, zone))
    }

    @Test fun yearly_nonLeapDayAnchorUsesPlainPlusYears() {
        // Cualquier fecha común (no 29/2) es estable con +1 año: sin cambio de
        // comportamiento. Regresión de que la nueva rama no altera el caso normal.
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 8, 15), LocalTime.of(9, 0), zone)
        val task = TaskEntity(title = "Cumple", dueAt = due, recurrence = RecurrenceFrequency.YEARLY)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2027, 8, 15), DateRules.toLocalDate(next.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(next.dueAt, zone))
    }

    @Test fun yearly_leapDayAnchorRespectsInterval() {
        // "cada 4 años" anclado a 29/2: el paso mínimo se respeta; como 29/2/2024 + 4
        // = 29/2/2028 (bisiesto), no necesita saltar más. Valida que interval > 1 no
        // se ignora.
        val due = DateRules.toEpochMillis(LocalDate.of(2024, 2, 29), LocalTime.of(12, 0), zone)
        val task = TaskEntity(title = "Olimpiadas", dueAt = due, recurrence = RecurrenceFrequency.YEARLY, recurrenceInterval = 4)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2028, 2, 29), DateRules.toLocalDate(next.dueAt!!, zone))
    }

    // Past-safe del reminder trasladado al completar una recurrente (c.189).
    // El offset de recordatorio se traslada a la próxima ocurrencia como
    // `nextDue - offset`. Si ese instante cae en el pasado (offset grande +
    // ocurrencia próxima), ReminderSync lo descarta (trigger <= now) y la nueva
    // ocurrencia nacía SIN aviso -> olvido de la próxima cita. Simétrico con
    // ReminderRules.resolveReminderAt (c.183) y AutomationActionPlanner (c.187/c.188).
    @Test fun monthly_largeReminderOffsetCompletedLate_neverPastReminder() {
        // Vence el 15 de cada mes a las 09:00, recordatorio 25 días antes (offset
        // alcanzable: el parser admite "recuérdame N días antes", clamp hasta 30).
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 8, 15), LocalTime.of(9, 0), zone)
        val offsetDays = 25L
        val dayMs = 24L * 60 * 60_000L
        val task = TaskEntity(
            title = "Pagar tarjeta",
            dueAt = due,
            reminderAt = due - offsetDays * dayMs,
            recurrence = RecurrenceFrequency.MONTHLY
        )
        // Completada TARDE el 22 (una semana de retraso): la próxima ocurrencia es
        // el 15 de septiembre. Trasladar 25 días -> 21 de agosto, que YA pasó
        // (completada el 22). Sin past-safe el reminder queda en el PASADO.
        val completedAt = DateRules.toEpochMillis(LocalDate.of(2026, 8, 22), LocalTime.of(12, 0), zone)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = completedAt, zone = zone))
        assertEquals(LocalDate.of(2026, 9, 15), DateRules.toLocalDate(next.dueAt!!, zone))
        assertNotNull("La próxima ocurrencia debe conservar un recordatorio", next.reminderAt)
        assertTrue("El recordatorio no debe quedar en el pasado", next.reminderAt!! > completedAt)
        assertTrue("El recordatorio debe preceder al vencimiento", next.reminderAt!! < next.dueAt)
    }

    @Test fun monthly_largeReminderOffsetKeepsOffsetWhenTranslatedIsFuture() {
        // No-regresión: si el instante trasladado SÍ es futuro, se conserva el
        // offset explícito del usuario (no se reemplaza por el default).
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 8, 15), LocalTime.of(9, 0), zone)
        val offsetDays = 5L
        val dayMs = 24L * 60 * 60_000L
        val task = TaskEntity(
            title = "Pagar tarjeta",
            dueAt = due,
            reminderAt = due - offsetDays * dayMs,
            recurrence = RecurrenceFrequency.MONTHLY
        )
        // Completada a tiempo el 15: próxima ocurrencia 15 de septiembre, offset 5
        // días -> 10 de septiembre (futuro). Se conserva el offset exacto.
        val completedAt = DateRules.toEpochMillis(LocalDate.of(2026, 8, 15), LocalTime.of(9, 0), zone)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = completedAt, zone = zone))
        assertEquals(LocalDate.of(2026, 9, 15), DateRules.toLocalDate(next.dueAt!!, zone))
        assertEquals(offsetDays * dayMs, next.dueAt - next.reminderAt!!)
    }

    @Test fun monthly_legacyCorruptStartAfterDue_doesNotPropagateStartAfterDue() {
        // Defensa en profundidad (simétrico c.193/c.194): una fila LEGADA con
        // `startAt > dueAt` (estado que BackupManager rechaza al restaurar, posible
        // en datos anteriores a c.193/c.194) NO debe propagar el invariante roto a
        // la próxima ocurrencia. `startOffset = dueAt - startAt` sería NEGATIVO, así
        // que `nextDue - startOffset > nextDue` dejaría la nueva ocurrencia con
        // `startAt > dueAt` -> backup irrestaurable + autoperpetuación en cada ciclo.
        val start = DateRules.toEpochMillis(LocalDate.of(2026, 8, 15), LocalTime.of(14, 0), zone)
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 8, 15), LocalTime.of(9, 0), zone) // due ANTES de start (corrupto)
        val task = TaskEntity(
            title = "Legado corrupto",
            startAt = start,
            dueAt = due,
            recurrence = RecurrenceFrequency.MONTHLY
        )
        val completedAt = DateRules.toEpochMillis(LocalDate.of(2026, 8, 15), LocalTime.of(9, 30), zone)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = completedAt, zone = zone))
        assertEquals(LocalDate.of(2026, 9, 15), DateRules.toLocalDate(next.dueAt!!, zone))
        assertTrue(
            "La próxima ocurrencia no debe heredar startAt > dueAt (invariante que BackupManager exige al restaurar)",
            next.startAt == null || next.startAt <= next.dueAt
        )
    }

    @Test fun monthly_largeStartOffsetCompletedLate_birthsPastSafeStart() {
        // Simétrico a c.189 (recordatorio de offset grande) pero para el OFFSET DE
        // INICIO. Una tarea recurrente con un `startAt` MUY anterior al `dueAt`
        // (antelación mayor que el intervalo de recurrencia, p.ej. empieza 6 semanas
        // antes del vencimiento mensual) completada TARDE -> la próxima ocurrencia
        // nace con `startAt = nextDue - startOffset` que cae en el PASADO (porque
        // startOffset > intervalo a la próxima ocurrencia). Sin past-safe, la nueva
        // tarea nacería ya como "inicio perdido" (isMissedStart) aunque el usuario
        // acaba de generarla y aún está dentro de su ventana. Se conserva el offset
        // EXACTO cuando el instante trasladado es futuro; si no, se reclampa a un
        // inicio útil (futuro, < due).
        val dayMs = 24L * 60 * 60_000L
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 8, 15), LocalTime.of(9, 0), zone)
        val startOffset = 45L * dayMs // 45 días de antelación (mayor que el mes de recurrencia)
        val start = due - startOffset // ~1 de julio
        val task = TaskEntity(
            title = "Proyecto trimestral",
            startAt = start,
            dueAt = due,
            recurrence = RecurrenceFrequency.MONTHLY
        )
        // Completada tarde el 14 (víspera del vencimiento): próxima ocurrencia
        // 15 de septiembre. startAt trasladado = 15-sep - 45 días = 1-ago, que es
        // PASADO respecto al 14-ago (completedAt). Debe reclamparse al futuro.
        val completedAt = DateRules.toEpochMillis(LocalDate.of(2026, 8, 14), LocalTime.of(18, 0), zone)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = completedAt, zone = zone))
        assertEquals(LocalDate.of(2026, 9, 15), DateRules.toLocalDate(next.dueAt!!, zone))
        assertNotNull("La próxima ocurrencia debe conservar un startAt (no perder el intento de inicio)", next.startAt)
        assertTrue(
            "El startAt no debe quedar en el pasado (la tarea no debe nacer como inicio perdido)",
            next.startAt!! > completedAt
        )
        assertTrue("El startAt debe preceder al vencimiento (no start>=due)", next.startAt!! < next.dueAt)
    }

    @Test fun monthly_largeStartOffsetCompletedEarly_keepsExactOffset() {
        // No-regresión: si el instante trasladado SÍ es futuro, se conserva el
        // offset de inicio EXACTO del usuario (no se reemplaza por un default).
        val dayMs = 24L * 60 * 60_000L
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 8, 15), LocalTime.of(9, 0), zone)
        val startOffset = 5L * dayMs // antelación pequeña: trasladado queda futuro
        val start = due - startOffset
        val task = TaskEntity(
            title = "Proyecto viernes",
            startAt = start,
            dueAt = due,
            recurrence = RecurrenceFrequency.MONTHLY
        )
        // Completada a tiempo el 10: próxima 15-sep, startAt trasladado = 10-sep,
        // futuro respecto al 10-ago. Se conserva el offset exacto.
        val completedAt = DateRules.toEpochMillis(LocalDate.of(2026, 8, 10), LocalTime.of(9, 30), zone)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = completedAt, zone = zone))
        assertEquals(LocalDate.of(2026, 9, 15), DateRules.toLocalDate(next.dueAt!!, zone))
        assertEquals(startOffset, next.dueAt - next.startAt!!)
    }

    // ─── Recurrencia mensual ORDINAL (c.215) ─────────────────────────────────
    // "primer lunes de cada mes", "último viernes de cada mes", "tercer miércoles
    // de cada 2 meses": la ocurrencia NO se ancla al día del mes de la 1ª fecha,
    // sino al N-ésimo (o último) día de la semana del mes. Sin persistencia del
    // ordinal, el motor derivaba al día del mes y la 2ª cita se desplazaba
    // silenciosamente ("evitar olvidos"/"rutinas", P1). Codificación: MONTHLY con
    // recurrenceDays = "ord:weekday" (ord∈{1,2,3,4,-1}, weekday∈1..7 ISO). El día
    // del mes puro sigue usando recurrenceDays vacío (regresión cubierta arriba).

    @Test fun monthlyOrdinal_firstMonday_advancesToFirstMondayNextMonth() {
        // "primer lunes de cada mes": 1er lunes de sep 2026 = 07-sep. La próxima NO
        // debe ser 07-oct (día del mes), sino el 1er lunes de oct 2026 = 05-oct.
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 9, 7), LocalTime.of(9, 0), zone)
        val task = TaskEntity(
            title = "Pago primer lunes",
            dueAt = due,
            recurrence = RecurrenceFrequency.MONTHLY,
            recurrenceDays = "1:1" // 1er lunes (ISO lunes=1)
        )
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2026, 10, 5), DateRules.toLocalDate(next.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(next.dueAt, zone))
    }

    @Test fun monthlyOrdinal_lastFriday_advancesToLastFridayNextMonth() {
        // "último viernes de cada mes": último viernes de ago 2026 = 28-ago. Próxima
        // = último viernes de sep 2026 = 25-sep (NO 28-sep).
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 8, 28), LocalTime.of(9, 0), zone)
        val task = TaskEntity(
            title = "Reunión último viernes",
            dueAt = due,
            recurrence = RecurrenceFrequency.MONTHLY,
            recurrenceDays = "-1:5" // último viernes (ISO viernes=5)
        )
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2026, 9, 25), DateRules.toLocalDate(next.dueAt!!, zone))
    }

    @Test fun monthlyOrdinal_interval2_advancesTwoMonths() {
        // "tercer miércoles de cada 2 meses": 3er miércoles de ago 2026 = 19-ago.
        // Próxima = 3er miércoles de OCT 2026 (intervalo 2) = 21-oct.
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 8, 19), LocalTime.of(9, 0), zone)
        val task = TaskEntity(
            title = "Tercer miércoles cada 2 meses",
            dueAt = due,
            recurrence = RecurrenceFrequency.MONTHLY,
            recurrenceInterval = 2,
            recurrenceDays = "3:3" // 3er miércoles (ISO miércoles=3)
        )
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2026, 10, 21), DateRules.toLocalDate(next.dueAt!!, zone))
    }

    @Test fun monthlyOrdinal_interval3_trimestral_advancesThreeMonths() {
        // c.272: "trimestral el primer lunes" → MONTHLY interval=3, days="1:1". La 1ª
        // cita = 1er lunes sep 2026 = 07-sep. Al completarla, la 2ª debe avanzar 3
        // meses (NO 1) al 1er lunes de DIC 2026 = 07-dic. Antes del fix del parser, el
        // ordinal no se capturaba (days='') → el motor anclaba al día 7 y avanzaba 1
        // mes (07-oct), perdiendo la cadencia trimestral y el weekday (P1: deriva).
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 9, 7), LocalTime.of(9, 0), zone)
        val task = TaskEntity(
            title = "Reunión trimestral primer lunes",
            dueAt = due,
            recurrence = RecurrenceFrequency.MONTHLY,
            recurrenceInterval = 3,
            recurrenceDays = "1:1" // 1er lunes (ISO lunes=1)
        )
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2026, 12, 7), DateRules.toLocalDate(next.dueAt!!, zone))
        assertEquals("1:1", next.recurrenceDays)
        assertEquals(3, next.recurrenceInterval)
    }

    @Test fun monthlyOrdinal_advancesPastCompletedAt() {
        // Completar tarde (varios meses después) avanza hasta la 1ª ocurrencia
        // ordinal futura, no atasca ni retrocede. 1er lunes nov 2026 = 02-nov; al
        // completar el 05-nov esa ocurrencia ya pasó → avanza al 1er lunes dic.
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 9, 7), LocalTime.of(9, 0), zone)
        val task = TaskEntity(
            title = "Pago primer lunes",
            dueAt = due,
            recurrence = RecurrenceFrequency.MONTHLY,
            recurrenceDays = "1:1"
        )
        // Completa el 05-nov: la 1ª ocurrencia ordinal futura = 1er lunes dic 2026 = 07-dic.
        val late = DateRules.toEpochMillis(LocalDate.of(2026, 11, 5), LocalTime.NOON, zone)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = late, zone = zone))
        assertEquals(LocalDate.of(2026, 12, 7), DateRules.toLocalDate(next.dueAt!!, zone))
    }

    @Test fun monthlyOrdinal_propagatesEncodingToNextOccurrence() {
        // La nueva ocurrencia debe conservar la codificación ordinal para que la
        // 3ª cita tampoco derive (de lo contrario el bug reaparece en el 2º ciclo).
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 9, 7), LocalTime.of(9, 0), zone)
        val task = TaskEntity(
            title = "Pago primer lunes",
            dueAt = due,
            recurrence = RecurrenceFrequency.MONTHLY,
            recurrenceDays = "1:1"
        )
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals("1:1", next.recurrenceDays)
    }

    @Test fun monthlyOrdinal_staysAnchoredAcrossMultipleCycles() {
        // Guardián anti-deriva: encadena `nextOccurrence` 3 ciclos consecutivos y
        // verifica que cada cita sigue siendo el 1.er LUNES del mes (no el día 7,
        // que es a lo que derivaba el motor antes de anclarlo al ordinal). El bug
        // original reaparecía silenciosamente en el 2.º/3.er ciclo: la 1.ª cita se
        // calculaba bien, pero al completarla la siguiente tomaba `dueAt.dayOfMonth`
        // (7) como ancla y se desplazaba al "7 de cada mes". Este test fallaría con
        // ese comportamiento (07-oct / 07-nov en vez de 05-oct / 02-nov).
        // 1.er lunes: sep=07, oct=05, nov=02, dic=07 (2026).
        var task = TaskEntity(
            title = "Pago primer lunes de cada mes",
            dueAt = DateRules.toEpochMillis(LocalDate.of(2026, 9, 7), LocalTime.of(9, 0), zone),
            recurrence = RecurrenceFrequency.MONTHLY,
            recurrenceDays = "1:1"
        )
        val expected = listOf(
            LocalDate.of(2026, 10, 5),
            LocalDate.of(2026, 11, 2),
            LocalDate.of(2026, 12, 7)
        )
        for (cycle in expected.indices) {
            task = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = task.dueAt!!, zone = zone)) {
                "Ciclo ${cycle + 1}: se esperaba una próxima ocurrencia"
            }
            assertEquals(
                "Ciclo ${cycle + 1} debe caer en el 1.er lunes del mes (sin derivar al día 7)",
                expected[cycle],
                DateRules.toLocalDate(task.dueAt!!, zone)
            )
            assertEquals("La codificación ordinal debe conservarse en cada ciclo", "1:1", task.recurrenceDays)
            assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(task.dueAt, zone))
        }
    }

    // HOURLY ("cada 8 horas"): recurrencia sub-diaria REAL para medicación. Antes era
    // NONE + dosis única y la 2ª/3ª dosis se olvidaban. Ahora al completar avanza
    // exactamente N horas, preservando minuto y offset de recordatorio. La 1ª dosis
    // vence ahora; la siguiente se genera +8h.
    @Test fun hourlyInterval8_advancesEightHours() {
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.of(15, 0), zone)
        val task = TaskEntity(title = "Antibiótico cada 8 horas", dueAt = due, reminderAt = due - 15 * 60_000L, recurrence = RecurrenceFrequency.HOURLY, recurrenceInterval = 8)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(next.dueAt!!, zone))
        assertEquals(LocalTime.of(23, 0), DateRules.toLocalTime(next.dueAt, zone))
        assertEquals(15 * 60_000L, next.dueAt - next.reminderAt!!)
        assertFalse(next.completed)
    }

    // "cada 12 horas": dos dosis/día. 1ª a las 8:00 → siguiente 20:00 (mismo día).
    @Test fun hourlyInterval12_advancesTwelveHours() {
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.of(8, 0), zone)
        val task = TaskEntity(title = " cada 12 horas", dueAt = due, recurrence = RecurrenceFrequency.HOURLY, recurrenceInterval = 12)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(next.dueAt!!, zone))
        assertEquals(LocalTime.of(20, 0), DateRules.toLocalTime(next.dueAt, zone))
    }

    // Cadena de 3 dosis "cada 8 horas" desde las 15:00: 15:00 → 23:00 → 07:00(+1d).
    // Verifica que la recurrencia horaria NO se traba ni deriva a diario al iterar.
    @Test fun hourlyInterval8_threeDosesChainAcrossMidnight() {
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.of(15, 0), zone)
        var task = TaskEntity(title = "cada 8 horas", dueAt = due, recurrence = RecurrenceFrequency.HOURLY, recurrenceInterval = 8)
        val expected = listOf(
            LocalTime.of(23, 0) to LocalDate.of(2026, 7, 29),
            LocalTime.of(7, 0) to LocalDate.of(2026, 7, 30),
            LocalTime.of(15, 0) to LocalDate.of(2026, 7, 30)
        )
        for (cycle in expected.indices) {
            task = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = task.dueAt!!, zone = zone)) {
                "Ciclo ${cycle + 1}: se esperaba próxima dosis horaria"
            }
            val (t, d) = expected[cycle]
            assertEquals("Ciclo ${cycle + 1} día", d, DateRules.toLocalDate(task.dueAt!!, zone))
            assertEquals("Ciclo ${cycle + 1} hora", t, DateRules.toLocalTime(task.dueAt, zone))
            assertEquals("El intervalo horario se conserva al iterar", 8, task.recurrenceInterval)
        }
    }

    // --- spawnedOccurrenceToRevert: evitar el duplicado huérfano al des-completar ---
    // Al completar una recurrente se genera la próxima ocurrencia (fila nueva,
    // createdAt == completedAt, misma huella de recurrencia, status PLANNED). Si el
    // usuario deshace (des-completa), esa ocurrencia generada debe revertirse o queda
    // un duplicado activo además de la original restaurada. La regla la identifica
    // SÓLO si sigue prístina (el usuario no la tocó): datos sagrados.

    @Test fun spawnedToRevert_findsPristineSpawnedOccurrence() {
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.of(9, 30), zone)
        val completedAt = due
        val original = TaskEntity(
            id = 1, title = "Gym", dueAt = due, recurrence = RecurrenceFrequency.DAILY,
            completed = true, completedAt = completedAt
        )
        val spawn = RecurrenceEngine.nextOccurrence(original, completedAt = completedAt, zone = zone)!!
            .copy(id = 2, createdAt = completedAt)
        val all = listOf(original, spawn)
        assertEquals(2L, RecurrenceEngine.spawnedOccurrenceToRevert(original, all, completedAt, zone))
    }

    @Test fun spawnedToRevert_noneTaskReturnsNull() {
        val original = TaskEntity(id = 1, title = "Una vez", completed = true, completedAt = 1000L)
        assertNull(RecurrenceEngine.spawnedOccurrenceToRevert(original, listOf(original), 1000L, zone))
    }

    @Test fun spawnedToRevert_nullCompletedAtReturnsNull() {
        val original = TaskEntity(
            id = 1, title = "Gym", dueAt = 1000L, recurrence = RecurrenceFrequency.DAILY,
            completed = true, completedAt = null
        )
        assertNull(RecurrenceEngine.spawnedOccurrenceToRevert(original, listOf(original), 0L, zone))
    }

    @Test fun spawnedToRevert_noSpawnPresentReturnsNull() {
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.of(9, 30), zone)
        val original = TaskEntity(
            id = 1, title = "Gym", dueAt = due, recurrence = RecurrenceFrequency.DAILY,
            completed = true, completedAt = due
        )
        // Sólo la original: nada que revertir.
        assertNull(RecurrenceEngine.spawnedOccurrenceToRevert(original, listOf(original), due, zone))
    }

    @Test fun spawnedToRevert_editedSpawnIsKept() {
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.of(9, 30), zone)
        val completedAt = due
        val original = TaskEntity(
            id = 1, title = "Gym", dueAt = due, recurrence = RecurrenceFrequency.DAILY,
            completed = true, completedAt = completedAt
        )
        // El usuario renombró la ocurrencia generada: no se pierde trabajo real.
        val edited = RecurrenceEngine.nextOccurrence(original, completedAt = completedAt, zone = zone)!!
            .copy(id = 2, createdAt = completedAt, title = "Gym modificado")
        assertNull(RecurrenceEngine.spawnedOccurrenceToRevert(original, listOf(original, edited), completedAt, zone))
    }

    @Test fun spawnedToRevert_startedSpawnIsKept() {
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.of(9, 30), zone)
        val completedAt = due
        val original = TaskEntity(
            id = 1, title = "Gym", dueAt = due, recurrence = RecurrenceFrequency.DAILY,
            completed = true, completedAt = completedAt
        )
        // El usuario la inició (IN_PROGRESS): no se borra.
        val started = RecurrenceEngine.nextOccurrence(original, completedAt = completedAt, zone = zone)!!
            .copy(id = 2, createdAt = completedAt, status = TaskStatus.IN_PROGRESS)
        assertNull(RecurrenceEngine.spawnedOccurrenceToRevert(original, listOf(original, started), completedAt, zone))
    }

    @Test fun spawnedToRevert_completedSpawnIsKept() {
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.of(9, 30), zone)
        val completedAt = due
        val original = TaskEntity(
            id = 1, title = "Gym", dueAt = due, recurrence = RecurrenceFrequency.DAILY,
            completed = true, completedAt = completedAt
        )
        // El usuario ya completó la ocurrencia generada: contiene trabajo real, no se toca.
        val done = RecurrenceEngine.nextOccurrence(original, completedAt = completedAt, zone = zone)!!
            .copy(id = 2, createdAt = completedAt, completed = true, status = TaskStatus.COMPLETED)
        assertNull(RecurrenceEngine.spawnedOccurrenceToRevert(original, listOf(original, done), completedAt, zone))
    }

    @Test fun spawnedToRevert_unrelatedTaskWithSameCreatedAtIsIgnored() {
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.of(9, 30), zone)
        val completedAt = due
        val original = TaskEntity(
            id = 1, title = "Gym", dueAt = due, recurrence = RecurrenceFrequency.DAILY,
            completed = true, completedAt = completedAt
        )
        // Otra tarea creada el mismo instante pero con recurrencia/distinto dueAt:
        // no debe confundirse con la ocurrencia generada.
        val other = TaskEntity(
            id = 9, title = "Gym", dueAt = due + 99L, recurrence = RecurrenceFrequency.WEEKLY,
            createdAt = completedAt, status = TaskStatus.PLANNED
        )
        assertNull(RecurrenceEngine.spawnedOccurrenceToRevert(original, listOf(original, other), completedAt, zone))
    }

    @Test fun spawnedToRevert_weeklyFingerprintMatch() {
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.NOON, zone) // miércoles
        val completedAt = due
        val original = TaskEntity(
            id = 1, title = "Lunes y viernes", dueAt = due, recurrence = RecurrenceFrequency.WEEKLY,
            recurrenceDays = "1,5", completed = true, completedAt = completedAt
        )
        val spawn = RecurrenceEngine.nextOccurrence(original, completedAt = completedAt, zone = zone)!!
            .copy(id = 2, createdAt = completedAt)
        assertEquals(2L, RecurrenceEngine.spawnedOccurrenceToRevert(original, listOf(original, spawn), completedAt, zone))
    }

    // "el 1 y 15 de cada mes", "cobro los días 15 y 30 de cada mes": recurrencia
    // mensual con VARIOS días del mes. Codificación MONTHLY + recurrenceDays="d:N1,N2"
    // (c.315, simétrico a los ordinales "ord:wd" c.216 y "EOM" c.257). Antes el 2º día
    // se perdía silenciosamente (el parser sólo anclaba el 1º y dejaba el 2º como fecha
    // suelta descartada + residuo " y" en el título): un día de cobro/nómina quincenal
    // real nacía olvidado (P1: pérdida de datos). Ahora ambos días disparan ocurrencia.
    @Test fun monthlyDayList_advancesToSecondDaySameMonth() {
        // "renta el 1 y 15 de cada mes": 1ª cita = 01-sep. Al completarla, la próxima
        // NO es 01-oct (perdería el 15), sino 15-sep (2º día del MISMO mes).
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 9, 1), LocalTime.of(9, 0), zone)
        val task = TaskEntity(
            title = "Renta el 1 y 15 de cada mes",
            dueAt = due,
            recurrence = RecurrenceFrequency.MONTHLY,
            recurrenceDays = "d:1,15"
        )
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2026, 9, 15), DateRules.toLocalDate(next.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(next.dueAt, zone))
        assertEquals("d:1,15", next.recurrenceDays)
    }

    @Test fun monthlyDayList_advancesToFirstDayNextMonth() {
        // Tras el 15-sep (2º día), la próxima = 01-oct (1er día del mes siguiente).
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 9, 15), LocalTime.of(9, 0), zone)
        val task = TaskEntity(
            title = "Renta el 1 y 15 de cada mes",
            dueAt = due,
            recurrence = RecurrenceFrequency.MONTHLY,
            recurrenceDays = "d:1,15"
        )
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2026, 10, 1), DateRules.toLocalDate(next.dueAt!!, zone))
    }

    @Test fun monthlyDayList_skipsDayAbsentInShortMonth() {
        // "los días 15 y 30 de cada mes": 30 no existe en febrero. Tras el 15-feb, la
        // próxima NO es 30-feb (inexistente), sino 15-mar (simétrico al anclaje por día
        // del mes que salta meses cortos). El motor debe avanzar, no clampar a 28-feb.
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 2, 15), LocalTime.of(9, 0), zone)
        val task = TaskEntity(
            title = "Cobro los días 15 y 30",
            dueAt = due,
            recurrence = RecurrenceFrequency.MONTHLY,
            recurrenceDays = "d:15,30"
        )
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2026, 3, 15), DateRules.toLocalDate(next.dueAt!!, zone))
    }
}
