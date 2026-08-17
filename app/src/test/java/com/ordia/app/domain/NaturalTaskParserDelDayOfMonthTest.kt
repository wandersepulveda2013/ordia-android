package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * "del N" como día del mes suelto ("pago del 15", "cita del 20"): el artículo contracto
 * "del" (= de + el) introduce una fecha de vencimiento, igual que "el N"/"para el N".
 * Antes no se reconocía → dueAt=null → vencimiento olvidado (P1). Casos de no-regresión:
 * rangos ("del 20 al 25") siguen sin inventar fecha; "antes del 30" y "del 15 de
 * septiembre" los resuelven sus patrones propios.
 */
class NaturalTaskParserDelDayOfMonthTest {
    private val zone = ZoneId.of("America/Santiago")
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 17), java.time.LocalTime.NOON, zone)

    @Test fun delDiaSueltoAnclaDiaDelMes() {
        val r = NaturalTaskParser.parse("pago del 15", now, zone)
        assertNotNull("del 15 debe anclar fecha", r.dueAt)
        assertEquals("pago", r.title.trim())
        val fecha = Instant.ofEpochMilli(r.dueAt!!).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 9, 15), fecha)
    }

    @Test fun delDiaSueltoRuedaAlMesSiguienteSiYaPaso() {
        // hoy 17/8: el 15 ya pasó → próxima ocurrencia = 15/9.
        val r = NaturalTaskParser.parse("cita del 15", now, zone)
        assertNotNull(r.dueAt)
        val fecha = Instant.ofEpochMilli(r.dueAt!!).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 9, 15), fecha)
    }

    @Test fun delDiaConHora() {
        val r = NaturalTaskParser.parse("pago del 15 a las 10", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("pago", r.title.trim())
        val zdt = Instant.ofEpochMilli(r.dueAt!!).atZone(zone)
        assertEquals(LocalDate.of(2026, 9, 15), zdt.toLocalDate())
        assertEquals(10, zdt.hour)
    }

    @Test fun delDia30Ancla() {
        val r = NaturalTaskParser.parse("entrega del 30", now, zone)
        assertNotNull(r.dueAt)
        val fecha = Instant.ofEpochMilli(r.dueAt!!).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 8, 30), fecha)
    }

    @Test fun rangoDelAlSinMesSigueSinInventarFecha() {
        // No-regresión: "del 20 al 25" sin mes sigue SIN fecha (honesto).
        val r = NaturalTaskParser.parse("congreso del 20 al 25", now, zone)
        assertNull(r.dueAt)
        assertEquals("congreso del 20 al 25", r.title.trim())
    }

    @Test fun delDiaConNombreMesUsaMonthName() {
        // "del 15 de septiembre" lo resuelve monthNamePattern (no el día suelto).
        val r = NaturalTaskParser.parse("pago del 15 de septiembre", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("pago", r.title.trim())
        val fecha = Instant.ofEpochMilli(r.dueAt!!).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 9, 15), fecha)
    }

    @Test fun antesDelDiaSigueResolviendoBeforeDeadline() {
        // No-regresión: "antes del 30" sigue funcionando (no sombreado por el nuevo patrón).
        val r = NaturalTaskParser.parse("pago antes del 30", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("pago", r.title.trim())
        val fecha = Instant.ofEpochMilli(r.dueAt!!).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 8, 30), fecha)
    }

    @Test fun delBancoDel15ConservaContenido() {
        // "del banco" (contenido) NO se toca; el "del 15" (fecha) sí se limpia.
        val r = NaturalTaskParser.parse("llamada del banco del 15", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("llamada del banco", r.title.trim())
        val fecha = Instant.ofEpochMilli(r.dueAt!!).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 9, 15), fecha)
    }
}
