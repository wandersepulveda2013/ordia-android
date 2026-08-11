package com.ordia.app.domain

import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class NaturalTaskParserTest {
    private val zone = ZoneId.of("America/Santo_Domingo")
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.NOON, zone)

    @Test fun parsesTomorrowTimeAndPriority() {
        val result = NaturalTaskParser.parse("Llamar a Ana mañana a las 9:30 !alta", now, zone)
        assertEquals("Llamar a Ana", result.title)
        assertEquals(TaskPriority.HIGH, result.priority)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun textWithoutCommand_remainsUnchanged() {
        val result = NaturalTaskParser.parse("Revisar informe de calidad", now, zone)
        assertEquals("Revisar informe de calidad", result.title)
        assertEquals(null, result.dueAt)
    }

    @Test fun parsesNextWeekdayInSpanish() {
        val result = NaturalTaskParser.parse("Entregar reporte el viernes a las 15:00", now, zone)
        assertEquals("Entregar reporte", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun parsesRelativeDuration() {
        val result = NaturalTaskParser.parse("Revisar el horno en 45 minutos", now, zone)
        assertEquals("Revisar el horno", result.title)
        assertEquals(now + 45 * 60_000L, result.dueAt)
        assertNull("La fecha relativa no debe leerse como duración", result.durationMinutes)
    }

    @Test fun parsesWeeklyRecurrence() {
        val result = NaturalTaskParser.parse("Preparar informe todos los viernes", now, zone)
        assertEquals("Preparar informe", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("5", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun parsesMultipleWeekdaysRecurrence() {
        val result = NaturalTaskParser.parse("Revisar reporte cada lunes y jueves", now, zone)
        assertEquals("Revisar reporte", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,4", result.recurrenceDays)
        // Desde miércoles 29-jul, la primera ocurrencia es el jueves 30-jul.
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun parsesDailyRecurrence() {
        val result = NaturalTaskParser.parse("Tomar vitaminas cada día", now, zone)
        assertEquals("Tomar vitaminas", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
        assertNull(result.dueAt)
    }

    @Test fun parsesIntervalRecurrence() {
        val result = NaturalTaskParser.parse("Lavar el coche cada 2 semanas", now, zone)
        assertEquals("Lavar el coche", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNull(result.dueAt)
    }

    @Test fun parsesReminderOffsetBeforeDue() {
        val result = NaturalTaskParser.parse("Presentar propuesta el viernes recuérdame 2 horas antes", now, zone)
        assertEquals("Presentar propuesta", result.title)
        assertEquals(120, result.reminderOffsetMinutes)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun parsesDurationPhrase() {
        val result = NaturalTaskParser.parse("Preparar presentación durante 45 minutos", now, zone)
        assertEquals("Preparar presentación", result.title)
        assertEquals(45, result.durationMinutes)
        assertNull(result.dueAt)
    }

    @Test fun parsesStandaloneDuration() {
        val result = NaturalTaskParser.parse("Estudiar 30 minutos", now, zone)
        assertEquals("Estudiar", result.title)
        assertEquals(30, result.durationMinutes)
    }

    @Test fun parsesMonthNameDate() {
        val result = NaturalTaskParser.parse("Entregar reporte antes del 5 de agosto", now, zone)
        assertEquals("Entregar reporte", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun monthNameDateBeforeTodayRollsToNextYear() {
        val result = NaturalTaskParser.parse("Llamar al dentista el 5 de julio", now, zone)
        assertEquals("Llamar al dentista", result.title)
        assertEquals(LocalDate.of(2027, 7, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun detectsCategoryByContext() {
        val result = NaturalTaskParser.parse("Comprar leche en el supermercado", now, zone)
        assertEquals("compras", result.category)
        assertEquals(0.6f, result.confidence, 0.001f)
    }

    @Test fun plainTextWithoutSignalsHasLowConfidence() {
        val result = NaturalTaskParser.parse("Revisar la factura del teléfono", now, zone)
        assertEquals("", result.category)
        assertEquals(0.35f, result.confidence, 0.001f)
    }

    @Test fun explicitTimeHasFullConfidence() {
        val result = NaturalTaskParser.parse("Llamar a Ana a las 9:30", now, zone)
        assertEquals("Llamar a Ana", result.title)
        assertEquals(1.0f, result.confidence, 0.001f)
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun highPriorityWithoutDateStaysInboxCandidate() {
        val result = NaturalTaskParser.parse("Revisar informe de calidad", now, zone)
        assertEquals("Revisar informe de calidad", result.title)
        assertEquals(null, result.dueAt)
        assertEquals("trabajo", result.category)
        assertEquals(0.6f, result.confidence, 0.001f)
    }

    // ── Regresión BUG1: fecha numérica sin año en el pasado rueda al año siguiente ──

    @Test fun numericDateWithoutYearInPastRollsToNextYear() {
        val result = NaturalTaskParser.parse("Pagar factura 5/3", now, zone)
        assertEquals("Pagar factura", result.title)
        // 5/3 = 5 de marzo; hoy es 29-jul-2026 → debe rodar a 2027.
        assertEquals(LocalDate.of(2027, 3, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun numericDateWithoutYearInFutureStaysThisYear() {
        val result = NaturalTaskParser.parse("Pagar factura 15/12", now, zone)
        assertEquals(LocalDate.of(2026, 12, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun numericDateTodayDoesNotRollForward() {
        val result = NaturalTaskParser.parse("Pagar factura 29/7", now, zone)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun numericDateWithExplicitYearNeverRolls() {
        // Año explícito en el pasado: no se interpreta como futuro (el usuario lo dijo).
        val result = NaturalTaskParser.parse("Revisar contrato 10/1/2024", now, zone)
        assertEquals(LocalDate.of(2024, 1, 10), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // ── Regresión BUG2: "esta mañana/tarde/noche" ──

    @Test fun estaNocheSetsTonightCanonicalTime() {
        val result = NaturalTaskParser.parse("Pagar factura esta noche", now, zone)
        assertEquals("Pagar factura", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun estaTardeSetsAfternoonCanonicalTime() {
        val result = NaturalTaskParser.parse("Pagar factura esta tarde", now, zone)
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun estaMananaIsNotMistakenForTomorrow() {
        // "esta mañana" contiene "mañana"; antes se interpretaba como "el día de mañana".
        val result = NaturalTaskParser.parse("Pagar factura esta mañana", now, zone)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun explicitTimeOverridesPartOfDayCanonicalTime() {
        val result = NaturalTaskParser.parse("Pagar factura esta noche a las 22:15", now, zone)
        assertEquals(LocalTime.of(22, 15), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun partOfDayIsAccentTolerant() {
        val result = NaturalTaskParser.parse("Pagar factura esta manana", now, zone)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // ── Regresión BUG4: "urgente" como palabra inicial (sin prefijo !/#) ──

    @Test fun leadingUrgenteSetsUrgentPriority() {
        val result = NaturalTaskParser.parse("urgente enviar documento mañana", now, zone)
        assertEquals(TaskPriority.URGENT, result.priority)
        // "urgente" se limpia del título.
        assertEquals("enviar documento", result.title)
    }

    @Test fun midSentenceUrgenteDoesNotSetPriority() {
        // "es urgente" a mitad de frase NO debe activar URGENT (evita falsos positivos).
        val result = NaturalTaskParser.parse("Revisar si es urgente el documento", now, zone)
        assertEquals(TaskPriority.NORMAL, result.priority)
        assertEquals("Revisar si es urgente el documento", result.title)
    }

    // ── Regresión BUG3: números escritos en expresiones relativas ──

    @Test fun writtenNumberRelativeHoursParsesDueAt() {
        val result = NaturalTaskParser.parse("Llamar a cliente en dos horas", now, zone)
        assertEquals("Llamar a cliente", result.title)
        assertEquals(now + 2 * 60 * 60_000L, result.dueAt)
    }

    @Test fun writtenNumberRelativeDaysParsesDueAt() {
        val result = NaturalTaskParser.parse("Enviar propuesta en tres días", now, zone)
        assertEquals("Enviar propuesta", result.title)
        assertEquals(now + 3 * 24 * 60 * 60_000L, result.dueAt)
    }

    @Test fun writtenNumberUnaHoraParsesDueAt() {
        val result = NaturalTaskParser.parse("Revisar correo en una hora", now, zone)
        assertEquals("Revisar correo", result.title)
        assertEquals(now + 1 * 60 * 60_000L, result.dueAt)
    }

    @Test fun writtenNumberUnDiaParsesDueAt() {
        val result = NaturalTaskParser.parse("Comprar pan en un día", now, zone)
        assertEquals("Comprar pan", result.title)
        assertEquals(now + 1 * 24 * 60 * 60_000L, result.dueAt)
    }

    @Test fun dentroDeWrittenNumberParsesDueAt() {
        val result = NaturalTaskParser.parse("Reunión dentro de dos horas", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(now + 2 * 60 * 60_000L, result.dueAt)
    }

    @Test fun dentroDeDigitsParsesDueAt() {
        val result = NaturalTaskParser.parse("Reunión dentro de 30 minutos", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(now + 30 * 60_000L, result.dueAt)
    }

    @Test fun writtenNumberUpToTwelveParsesDueAt() {
        val result = NaturalTaskParser.parse("Entregar en doce horas", now, zone)
        assertEquals("Entregar", result.title)
        assertEquals(now + 12 * 60 * 60_000L, result.dueAt)
    }

    @Test fun digitRelativeStillParsesAfterWrittenNumberSupport() {
        // Regresión: el soporte de números escritos no debe romper los dígitos.
        val result = NaturalTaskParser.parse("Revisar el horno en 45 minutos", now, zone)
        assertEquals("Revisar el horno", result.title)
        assertEquals(now + 45 * 60_000L, result.dueAt)
    }

    // --- Meridiem "de la tarde/noche/mañana" (antes se ignoraba: hora errónea + título sucio) ---

    @Test fun deLaTardeAppliesPmOffset() {
        val result = NaturalTaskParser.parse("Cita hoy a las 4 de la tarde", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(16, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun deLaNocheAppliesPmOffset() {
        val result = NaturalTaskParser.parse("Reunión hoy a las 7 de la noche", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(19, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun deLaMananaKeepsAmHour() {
        val result = NaturalTaskParser.parse("Desayuno hoy a las 9 de la mañana", now, zone)
        assertEquals("Desayuno", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun deLaTardeWithMinutesAppliesPmOffset() {
        val result = NaturalTaskParser.parse("Cita hoy a las 4:30 de la tarde", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(16, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun deLaTardeDoesNotBreakTitle() {
        val result = NaturalTaskParser.parse("Cita a las 9 de la tarde", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- Frases "al mediodía" / "a la medianoche" limpias en el título ---

    @Test fun alMediodiaParsesNoonAndCleanTitle() {
        val result = NaturalTaskParser.parse("Almuerzo mañana al mediodía", now, zone)
        assertEquals("Almuerzo", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.NOON, DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aLaMedianocheParsesMidnightAndCleanTitle() {
        val result = NaturalTaskParser.parse("Entregar tarea a la medianoche", now, zone)
        assertEquals("Entregar tarea", result.title)
        assertEquals(LocalTime.MIDNIGHT, DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- "esta mañana" no debe dejar "esta" huérfano en el título ---

    @Test fun estaMananaCleanedFullyFromTitle() {
        val result = NaturalTaskParser.parse("Correo al jefe esta mañana", now, zone)
        assertEquals("Correo al jefe", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- Contexto PM de parte del día aplicado a hora sin meridiem (ciclo 6) ---

    @Test fun estaTardeConHoraSinMeridiemAplicaPm() {
        val result = NaturalTaskParser.parse("Reunión esta tarde a las 4", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(16, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun estaNocheConHoraSinMeridiemAplicaPm() {
        val result = NaturalTaskParser.parse("Llamar hoy esta noche a las 9", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aLaTardeSueltaDefineHoraYNoFuerzaFecha() {
        val result = NaturalTaskParser.parse("Ver a juan mañana a la tarde", now, zone)
        assertEquals("Ver a juan", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun deLaTardeSueltaDaHoraCanonicaHoy() {
        val result = NaturalTaskParser.parse("Jugar tenis de la tarde", now, zone)
        assertEquals("Jugar tenis", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- "12 de la noche" = medianoche (00:00), no 12:00 del mediodía (ciclo 6) ---

    @Test fun doceDeLaNocheEsMedianoche() {
        val result = NaturalTaskParser.parse("Fiesta a las 12 de la noche", now, zone)
        assertEquals("Fiesta", result.title)
        assertEquals(LocalTime.MIDNIGHT, DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun deLaMadrugadaEsAmYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Despertar a las 4 de la madrugada", now, zone)
        assertEquals("Despertar", result.title)
        assertEquals(LocalTime.of(4, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- "a las 24" / "24:00" = medianoche (ciclo 7) ---

    @Test fun aLas24EsMedianocheYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Comprar pan a las 24", now, zone)
        assertEquals("Comprar pan", result.title)
        assertEquals(LocalTime.MIDNIGHT, DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLas24ConMinutosEsMedianoche() {
        val result = NaturalTaskParser.parse("Reunión a las 24:00", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.MIDNIGHT, DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLas24DeLaNocheLimpiaTituloYEsMedianoche() {
        val result = NaturalTaskParser.parse("Cena a las 24 de la noche", now, zone)
        assertEquals("Cena", result.title)
        assertEquals(LocalTime.MIDNIGHT, DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- Limpieza de prefijos/sufijos de día de la semana (ciclo 8) ---
    // "del jueves", "el viernes que viene", "el miércoles próximo" dejaban
    // residuos ("del", "que viene", "próximo") en el título.

    @Test fun delWeekdayLimpiaPrefijoDel() {
        val result = NaturalTaskParser.parse("Reunión del jueves", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun delWeekdayConHoraLimpiaPrefijoDel() {
        val result = NaturalTaskParser.parse("Reunión a las 3pm del jueves", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun elWeekdayQueVieneLimpiaSufijo() {
        val result = NaturalTaskParser.parse("Llamar a mamá el viernes que viene", now, zone)
        assertEquals("Llamar a mamá", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elWeekdayProximoLimpiaSufijo() {
        val result = NaturalTaskParser.parse("Ir al dentista el miércoles próximo", now, zone)
        assertEquals("Ir al dentista", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun delWeekdayQueVieneConHoraLimpiaTodo() {
        val result = NaturalTaskParser.parse("Clase de yoga del viernes que viene a las 8", now, zone)
        assertEquals("Clase de yoga", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(8, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun elWeekdaySinSufijoSigueFuncionando() {
        val result = NaturalTaskParser.parse("Entregar reporte el viernes a las 15:00", now, zone)
        assertEquals("Entregar reporte", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }
}
