package com.ordia.app.domain

import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.TaskEntity
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
}
