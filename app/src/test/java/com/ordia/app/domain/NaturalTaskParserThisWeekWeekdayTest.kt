package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * "el viernes de esta semana" / "esta semana el viernes": día de la semana explícito
 * anclado a la SEMANA ACTUAL. Antes thisWeekPattern robaba "esta semana" como plazo blando
 * (domingo 09:00) y ese ancla ganaba la cascada effectiveRelativeDueAt sobre el weekday
 * explícito → "dentista el viernes de esta semana" (dicho un viernes) caía en el DOMINGO
 * (fecha errónea silenciosa, P1: cita agendada en día equivocado; medido en probe c.852).
 * El weekday explícito es más específico que el plazo blando: gobierna la fecha y el
 * calificador "de esta semana" fija la semana (ISO lunes→domingo). Si ese día ya pasó esta
 * semana, queda como tarea vencida honesta (misma doctrina que "el lunes pasado"); no se
 * rueda a la semana siguiente porque el calificador explícito ancla ESTA semana.
 * Casos de no-regresión: "esta semana" a solas sigue siendo plazo blando al domingo;
 * "el viernes" suelto y "el viernes de la semana que viene" conservan su resolución.
 */
class NaturalTaskParserThisWeekWeekdayTest {
    private val zone = ZoneId.of("America/Santiago")
    // viernes 2026-08-21 12:00 local (semana ISO: lunes 17 .. domingo 23)
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 21), LocalTime.NOON, zone)

    private fun fecha(r: ParsedTaskInput): LocalDate =
        Instant.ofEpochMilli(r.dueAt!!).atZone(zone).toLocalDate()

    @Test fun weekdayDeEstaSemanaFuturoAnclaEseDia() {
        // viernes 21/8 -> el sábado de esta semana = 22/8 (no el domingo blando 23/8).
        val r = NaturalTaskParser.parse("dentista el sábado de esta semana", now, zone)
        assertNotNull("el sábado de esta semana debe anclar fecha", r.dueAt)
        assertEquals("dentista", r.title.trim())
        assertEquals(LocalDate.of(2026, 8, 22), fecha(r))
    }

    @Test fun weekdayDeEstaSemanaHoyQuedaHoy() {
        // Dicho el propio viernes: el viernes de ESTA semana es hoy. El calificador
        // explícito fija esta semana, así que no rueda +7d como el weekday suelto.
        val r = NaturalTaskParser.parse("dentista el viernes de esta semana", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("dentista", r.title.trim())
        assertEquals(LocalDate.of(2026, 8, 21), fecha(r))
    }

    @Test fun weekdayDeEstaSemanaPasadoEsVencidaHonesta() {
        // viernes 21/8 -> el lunes de esta semana (17/8) ya pasó: vencida honesta,
        // no se proyecta al lunes de la semana que viene (esa es OTRA semana).
        val r = NaturalTaskParser.parse("llamar al banco el lunes de esta semana", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("llamar al banco", r.title.trim())
        assertEquals(LocalDate.of(2026, 8, 17), fecha(r))
    }

    @Test fun weekdayDeEstaSemanaDomingoEsFinDeSemana() {
        // El domingo de esta semana coincide con el plazo blando: 23/8.
        val r = NaturalTaskParser.parse("asado el domingo de esta semana", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("asado", r.title.trim())
        assertEquals(LocalDate.of(2026, 8, 23), fecha(r))
    }

    @Test fun weekdayEstaSemanaSinDe() {
        // "el viernes esta semana" (sin "de"): forma coloquial del mismo calificador.
        val r = NaturalTaskParser.parse("dentista el sábado esta semana", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("dentista", r.title.trim())
        assertEquals(LocalDate.of(2026, 8, 22), fecha(r))
    }

    @Test fun ordenInversoEstaSemanaElWeekday() {
        // "esta semana el viernes": mismo ancla, orden período+día.
        val r = NaturalTaskParser.parse("dentista esta semana el jueves", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("dentista", r.title.trim())
        assertEquals(LocalDate.of(2026, 8, 20), fecha(r))
    }

    @Test fun ordenInversoConGenitivoLimpiaTitulo() {
        // "de esta semana el viernes": el "de" genitivo se consume con la frase.
        val r = NaturalTaskParser.parse("dentista de esta semana el viernes", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("dentista", r.title.trim())
        assertEquals(LocalDate.of(2026, 8, 21), fecha(r))
    }

    @Test fun estaMismaSemanaElWeekday() {
        val r = NaturalTaskParser.parse("reunión esta misma semana el martes", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("reunión", r.title.trim())
        assertEquals(LocalDate.of(2026, 8, 18), fecha(r))
    }

    @Test fun respetaHoraExplicita() {
        val r = NaturalTaskParser.parse("reunión el viernes de esta semana a las 18", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("reunión", r.title.trim())
        val zdt = Instant.ofEpochMilli(r.dueAt!!).atZone(zone)
        assertEquals(LocalDate.of(2026, 8, 21), zdt.toLocalDate())
        assertEquals(18, zdt.hour)
    }

    @Test fun estaSemanaQueVieneNoSeToca() {
        // "esta semana que viene" es forma confusa que ya resuelve thisWeekPattern al
        // domingo de la SEMANA PRÓXIMA (c.488): el calificador nuevo no la captura.
        val r = NaturalTaskParser.parse("dentista el viernes de esta semana que viene", now, zone)
        assertNotNull(r.dueAt)
        // No debe anclar al viernes de esta semana (21/8): pertenece a la próxima.
        assertEquals(true, fecha(r) != LocalDate.of(2026, 8, 21))
    }

    // --- no-regresión ---

    @Test fun estaSemanaSolaSigueSiendoPlazoBlandoDomingo() {
        val r = NaturalTaskParser.parse("dentista esta semana", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("dentista", r.title.trim())
        assertEquals(LocalDate.of(2026, 8, 23), fecha(r))
    }

    @Test fun weekdaySueltoSinCalificadorNoCambia() {
        // "el viernes" dicho un viernes al mediodía rueda al viernes siguiente (doctrina
        // existente: 09:00 canónico ya pasó) — el calificador nuevo no lo altera.
        val r = NaturalTaskParser.parse("dentista el viernes", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("dentista", r.title.trim())
        assertEquals(LocalDate.of(2026, 8, 28), fecha(r))
    }

    @Test fun weekdayDeLaSemanaQueVieneNoCambia() {
        val r = NaturalTaskParser.parse("dentista el viernes de la semana que viene", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("dentista", r.title.trim())
        assertEquals(LocalDate.of(2026, 8, 28), fecha(r))
    }
}
