package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Regresión c.625: la forma coloquial FUTURA "pasado el <día>" (modificador ANTES de
 * "el <día>") dejaba el residuo "pasado" pegado al título ("Llevar el coche al taller
 * pasado el lunes" → título "Llevar el coche al taller pasado"). La fecha (próximo
 * lunes) ya se calculaba bien; sólo el contenido quedaba degradado (P1: captura sucia).
 * El fix consume "pasado" y deja "el lunes" para weekdayPattern. Verifica también que
 * no regressione el uso PASADO ("el lunes pasado" = fecha anterior).
 */
class PassedWeekdayFutureTest {
    private val zone = ZoneId.of("America/Santo_Domingo")
    // 2026-07-29 = miércoles.
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.NOON, zone)

    @Test fun futureWeekdayPostArticle_noTitleResidue_monday() {
        val r = NaturalTaskParser.parse("Llevar el coche al taller pasado el lunes", now, zone)
        assertEquals("Llevar el coche al taller", r.title)
        assertNotNull("debe tener dueAt", r.dueAt)
        // Próximo lunes = 2026-08-03.
        val expected = DateRules.toEpochMillis(LocalDate.of(2026, 8, 3), LocalTime.of(9, 0), zone)
        assertEquals(expected, r.dueAt)
    }

    @Test fun futureWeekdayPostArticle_noTitleResidue_friday() {
        val r = NaturalTaskParser.parse("Llamar al cliente pasado el viernes", now, zone)
        assertEquals("Llamar al cliente", r.title)
        // Próximo viernes = 2026-07-31.
        val expected = DateRules.toEpochMillis(LocalDate.of(2026, 7, 31), LocalTime.of(9, 0), zone)
        assertEquals(expected, r.dueAt)
    }

    @Test fun futureWeekdayPostArticle_withTime() {
        val r = NaturalTaskParser.parse("Llamar al médico pasado el lunes a las 10", now, zone)
        assertEquals("Llamar al médico", r.title)
        val expected = DateRules.toEpochMillis(LocalDate.of(2026, 8, 3), LocalTime.of(10, 0), zone)
        assertEquals(expected, r.dueAt)
    }

    @Test fun futureWeekdayPostArticle_doesNotTouchContent() {
        // "pasado el informe" no es weekday → no se borra (preserva contenido).
        val r = NaturalTaskParser.parse("Revisar el contrato pasado el informe", now, zone)
        assertEquals("Revisar el contrato pasado el informe", r.title)
    }

    @Test fun pastWeekdayStillPast_noRegression() {
        // "el lunes pasado" = fecha PASADA (2026-07-27). El fix futuro no debe romperlo.
        val r = NaturalTaskParser.parse("Reunión el lunes pasado", now, zone)
        assertEquals("Reunión", r.title)
        val expected = DateRules.toEpochMillis(LocalDate.of(2026, 7, 27), LocalTime.of(9, 0), zone)
        assertEquals(expected, r.dueAt)
    }

    @Test fun pasadoMananaStillFuture_noRegression() {
        // "pasado mañana" no debe verse afectado (no hay "pasado el" ahí).
        val r = NaturalTaskParser.parse("Llamar a Ana pasado mañana", now, zone)
        assertEquals("Llamar a Ana", r.title)
        val expected = DateRules.toEpochMillis(LocalDate.of(2026, 7, 31), LocalTime.of(9, 0), zone)
        assertEquals(expected, r.dueAt)
    }
}
