package com.ordia.app.domain

import com.ordia.app.data.local.RecurrenceFrequency
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Modismos adverbiales de prioridad "primero que nada" / "antes que nada" ("first of
 * all / first thing") son puro énfasis, SIN semántica de fecha/hora ni de contenido.
 * Antes sobrevivían íntegros como residuo del título en toda posición (P1: título
 * limpio / captura ultrarrápida). c.573 los elimina incondicionalmente.
 *
 * Casos de no-regresión: NO se tocan "primero de mes" (fecha = día 1), "primer lunes
 * del mes" (ordinal+weekday), "lo primero que haré" / "lo primero de la lista" ("lo
 * primero" = contenido legítimo, sin "que nada") ni "para empezar" (ambiguo).
 */
class NaturalTaskParserPriorityIdiomTest {
    private val zone = ZoneId.of("America/Santiago")
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 17), java.time.LocalTime.NOON, zone)

    @Test fun primeroQueNadaAlFinalSeEliminaDelTitulo() {
        val r = NaturalTaskParser.parse("reunión mañana primero que nada", now, zone)
        assertEquals("reunión", r.title.trim())
    }

    @Test fun primeroQueNadaAlInicioSeEliminaDelTitulo() {
        val r = NaturalTaskParser.parse("primero que nada pagar la luz", now, zone)
        assertEquals("pagar la luz", r.title.trim())
    }

    @Test fun antesQueNadaSeEliminaDelTitulo() {
        val r = NaturalTaskParser.parse("reunión antes que nada mañana", now, zone)
        assertEquals("reunión", r.title.trim())
    }

    @Test fun antesQueNadaAlInicioSeEliminaDelTitulo() {
        val r = NaturalTaskParser.parse("antes que nada pagar la luz", now, zone)
        assertEquals("pagar la luz", r.title.trim())
    }

    @Test fun primeroQueNadaNoInterfiereConFechaMensual() {
        // El modismo como prefijo NO debe romper el parseo de la cadencia mensual.
        val r = NaturalTaskParser.parse("primero que nada el 15 de cada mes pagar la luz", now, zone)
        assertEquals("pagar la luz", r.title.trim())
        org.junit.Assert.assertNotNull("debe anclar cadencia mensual", r.dueAt)
        assertEquals(RecurrenceFrequency.MONTHLY, r.recurrence)
        val fecha = Instant.ofEpochMilli(r.dueAt!!).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 9, 15), fecha)
    }

    // --- No-regresiones: expresiones legítimas que NO deben tocarse ---

    @Test fun primeroDeMesSigueSiendoFecha() {
        // "primero de mes" = día 1 (sin "que nada"). No se confunde con el modismo.
        val r = NaturalTaskParser.parse("primero de mes pagar la renta", now, zone)
        assertEquals("pagar la renta", r.title.trim())
        val fecha = Instant.ofEpochMilli(r.dueAt!!).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 9, 1), fecha)
    }

    @Test fun loPrimeroQueHareSeConservaComoContenido() {
        // "lo primero" (sin "que nada") es contenido legítimo, NO el modismo.
        val r = NaturalTaskParser.parse("lo primero que haré es llamar", now, zone)
        assertEquals("lo primero que haré es llamar", r.title.trim())
    }

    @Test fun loPrimeroDeLaListaSeConservaComoContenido() {
        val r = NaturalTaskParser.parse("hacer lo primero de la lista", now, zone)
        assertEquals("hacer lo primero de la lista", r.title.trim())
    }

    @Test fun paraEmpezarSeConservaEsAmbiguo() {
        // "para empezar" puede ser idiomático (ante verbo) o contenido (ante sustantivo);
        // NO se elimina para no mutilar "para empezar el proyecto".
        val r = NaturalTaskParser.parse("para empezar revisar el correo", now, zone)
        assertEquals("para empezar revisar el correo", r.title.trim())
    }
}
