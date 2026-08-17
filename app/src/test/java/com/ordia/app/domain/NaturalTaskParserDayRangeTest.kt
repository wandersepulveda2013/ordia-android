package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.ordia.app.data.local.RecurrenceFrequency
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Regresión del rango de días multi-evento ("del N al M de mes"):
 * vacaciones, viajes, cursos y ferias. Antes [NaturalTaskParser] capturaba
 * sólo el día final y dejaba "del N al" como residuo pegado al título
 * (contenido mutilado, P1 captura/datos); o, sin día final reconocible,
 * caía a `dueAt=null` con título basura. Se normaliza al CIERRE del rango.
 */
class NaturalTaskParserDayRangeTest {
    private val now = ZonedDateTime.of(2026, 8, 17, 12, 0, 0, 0, ZoneId.of("UTC"))
    private val nowMs = now.toInstant().toEpochMilli()
    private val zone = ZoneId.of("UTC")

    private fun parse(text: String) = NaturalTaskParser.parse(text, nowMs, zone)
    private fun dueDate(text: String): LocalDate? =
        parse(text).dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

    @Test fun rangoAnclaAlCierreYlimpiaTitulo() {
        val r = parse("vacaciones del 15 al 20 de diciembre")
        assertEquals("vacaciones", r.title.trim())
        assertEquals(LocalDate.of(2026, 12, 20), dueDate("vacaciones del 15 al 20 de diciembre"))
    }

    @Test fun rangoConAnioExplicito() {
        val r = parse("viaje del 3 al 8 de enero del 2027")
        assertEquals("viaje", r.title.trim())
        assertEquals(LocalDate.of(2027, 1, 8), r.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() })
    }

    @Test fun rangoRespetaHoraExplicita() {
        val r = parse("trabajo del 2 al 4 de septiembre a las 9")
        assertEquals("trabajo", r.title.trim())
        val dt = r.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
        assertNotNull("debe producir vencimiento", dt)
        assertEquals(LocalDate.of(2026, 9, 4), dt!!.toLocalDate())
        assertEquals(9, dt.hour)
    }

    @Test fun rangoNoDejaResiduoDelAl() {
        val title = parse("curso del 10 al 14 de marzo").title
        assertFalse("el título no debe contener residuo 'al'", title.contains("al"))
        assertFalse("el título no debe contener el día inicial 10", title.contains("10"))
    }

    @Test fun rangoSinMesNoInventaFecha() {
        // "congreso del 20 al 25" sin mes: no se agenda una fecha inventada (honesto),
        // coherente con no falsar compromisos sin ancla de mes real.
        val r = parse("congreso del 20 al 25")
        assertNull("sin mes explícito no se debe inventar vencimiento", r.dueAt)
        assertEquals("congreso del 20 al 25", r.title.trim())
    }

    @Test fun rangoEntreSinMesNoInventaFechaEnExtremos() {
        // "entre el N de <X> y el M" / "entre el N y el M" sin mes válido: los patrones de
        // rango los dejan intactos (no hay ancla de mes real) pero, sin la guarda de
        // dayOfMonthPattern, el extremo "el M" caía como día suelto y se programaba una
        // fecha espuria (p. ej. 5 de septiembre) dejando el título roto ("...entre ... y").
        // Regresión P1: vencimiento falso + contenido mutilado.
        val r1 = parse("feria entre el 3 de unidades y el 5")
        assertNull("mes cualificador inválido (unidades) no inventa fecha en el cierre", r1.dueAt)
        assertEquals("feria entre el 3 de unidades y el 5", r1.title.trim())

        val r2 = parse("comprar 3 cajas entre el 5 y el 10")
        assertNull("rango entre..y sin mes no inventa fecha en ningún extremo", r2.dueAt)
        assertEquals("comprar 3 cajas entre el 5 y el 10", r2.title.trim())
    }

    @Test fun rangoPreservaContenidoDespuesDeLaFecha() {
        val r = parse("feria del 1 al 5 de octubre en madrid")
        assertTrue("el contenido 'madrid' debe sobrevivir", r.title.contains("madrid", ignoreCase = true))
    }

    @Test fun rangoConCualificadorRelativoMesQueViene() {
        val r = parse("feria del 1 al 5 del mes que viene")
        assertEquals("feria", r.title.trim())
        assertEquals(LocalDate.of(2026, 9, 5), dueDate("feria del 1 al 5 del mes que viene"))
    }

    @Test fun rangoConCualificadorRelativoProximoMes() {
        val r = parse("taller del 10 al 12 del próximo mes")
        assertEquals("taller", r.title.trim())
        assertEquals(LocalDate.of(2026, 9, 12), r.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() })
    }

    // c.380: "hasta" como conector de cierre de rango, simétrico a "al"/"a".
    // Antes "del 15 hasta el 20 de diciembre" no casaba y dejaba "hasta" como residuo
    // en el título (contenido mutilado, P1 captura/datos).

    @Test fun rangoHastaAnclaAlCierreYlimpiaTitulo() {
        val r = parse("vacaciones del 15 hasta el 20 de diciembre")
        assertEquals("vacaciones", r.title.trim())
        assertEquals(LocalDate.of(2026, 12, 20), r.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() })
    }

    @Test fun rangoHastaConAnioExplicito() {
        val r = parse("viaje del 3 hasta el 8 de enero del 2027")
        assertEquals("viaje", r.title.trim())
        assertEquals(LocalDate.of(2027, 1, 8), r.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() })
    }

    @Test fun rangoHastaNoDejaResiduoConector() {
        val title = parse("curso del 10 hasta el 14 de marzo").title
        assertFalse("el título no debe contener residuo 'hasta'", title.contains("hasta"))
        assertFalse("el título no debe contener el día inicial 10", title.contains("10"))
    }

    // Rangos que cruzan de mes (o de año): "del 28 de febrero al 1 de marzo".
    // Antes el [dayRangePattern] sólo casaba la forma "del N al M de <mesÚnico>"
    // (un solo mes al final); al llevar cada extremo su propio mes, no casaba, cada
    // fecha caía separada y el vencimiento se anclaba al día INICIAL (28 de febrero)
    // en vez del CIERRE (1 de marzo), perdiendo la fecha real del compromiso y
    // dejando "del al" como residuo del título. Se ancla al CIERRE (P1 captura/datos).

    @Test fun rangoCruzandoMesAnclaAlCierre() {
        val r = parse("feria del 28 de febrero al 1 de marzo")
        assertEquals("feria", r.title.trim())
        // 1 de marzo de 2026 ya pasó (now=2026-08-17) -> rueda al año siguiente.
        assertEquals(LocalDate.of(2027, 3, 1), dueDate("feria del 28 de febrero al 1 de marzo"))
    }

    @Test fun rangoCruzandoMesNoDejaResiduoDelAl() {
        val title = parse("feria del 28 de febrero al 1 de marzo").title
        assertFalse("el título no debe contener residuo 'al'", title.contains(" al"))
        assertFalse("el título no debe contener el día inicial 28", title.contains("28"))
    }

    @Test fun rangoCruzandoAnioAnclaAlCierre() {
        val r = parse("conferencia del 31 de diciembre al 2 de enero")
        assertEquals("conferencia", r.title.trim())
        assertEquals(LocalDate.of(2027, 1, 2), dueDate("conferencia del 31 de diciembre al 2 de enero"))
    }

    @Test fun rangoCruzandoMesRespetaHoraExplicita() {
        val r = parse("trabajo del 31 de enero al 1 de febrero a las 9")
        assertEquals("trabajo", r.title.trim())
        val dt = r.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
        assertNotNull("debe producir vencimiento", dt)
        // 1 de febrero ya pasó este año (now=2026-08-17) -> rueda al año siguiente.
        assertEquals(LocalDate.of(2027, 2, 1), dt!!.toLocalDate())
        assertEquals(9, dt.hour)
    }

    @Test fun rangoCruzandoMesConAnioExplicito() {
        val r = parse("viaje del 28 de diciembre del 2026 al 3 de enero del 2027")
        assertEquals("viaje", r.title.trim())
        assertEquals(LocalDate.of(2027, 1, 3), r.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() })
    }

    @Test fun rangoWeekdayHastaAnclaAlCierreYlimpiaTitulo() {
        // "del lunes hasta el viernes": evento único anclado al cierre (viernes).
        val r = parse("reunión del lunes hasta el viernes")
        assertEquals("reunión", r.title.trim())
        val dt = r.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
        assertNotNull("debe producir vencimiento", dt)
        assertEquals(LocalDate.of(2026, 8, 21), dt!!.toLocalDate())
    }

    @Test fun rangoWeekdayHastaSinArticulo() {
        // "del lunes hasta viernes" (sin "el"): misma resolución.
        val r = parse("taller del martes hasta jueves")
        assertEquals("taller", r.title.trim())
        val dt = r.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
        assertNotNull("debe producir vencimiento", dt)
        assertEquals(LocalDate.of(2026, 8, 20), dt!!.toLocalDate())
    }

    @Test fun semanaLaboralHastaEsRecurrente() {
        // "de lunes hasta viernes": semana laboral recurrente Lun-Vie, simétrico a
        // "de lunes a viernes" (no evento único). c.380.
        val r = parse("gym de lunes hasta viernes")
        assertEquals("gym", r.title.trim())
        assertEquals(RecurrenceFrequency.WEEKLY, r.recurrence)
        assertEquals("1,2,3,4,5", r.recurrenceDays)
    }

    @Test fun hastaSueltoNoEsRango() {
        // "hasta el viernes" suelto (fecha límite) NO debe casar como rango: exige
        // weekday a ambos lados. Regresión de seguridad: el conector de plazo sigue
        // funcionando y no se falsifica un rango sin día inicial.
        val r = parse("entregar hasta el viernes")
        assertFalse("no debe dejar residuo 'hasta'", r.title.contains("hasta"))
    }

    // Rangos con conector "entre...y" (c.444): misma intención que "del ... al ..."
    // pero con conector cotidiano alternativo. Antes NO se reconocían: [monthNamePattern]
    // consumía el extremo INICIAL y anclaba el vencimiento al día de APERTURA en vez del
    // CIERRE, y "entre ... y ..." sobrevivía como residuo del título ("feria entre y").
    // Misma clase de bug que c.443 cross-mes, conector distinto. Se ancla al CIERRE.

    @Test fun rangoEntreYMismoMesAnclaAlCierre() {
        val r = parse("feria entre el 15 y el 20 de diciembre")
        assertEquals("feria", r.title.trim())
        assertEquals(LocalDate.of(2026, 12, 20), dueDate("feria entre el 15 y el 20 de diciembre"))
    }

    @Test fun rangoEntreYMismoMesNoDejaResiduoEntre() {
        val title = parse("feria entre el 15 y el 20 de diciembre").title
        assertFalse("el título no debe contener residuo 'entre'", title.contains("entre"))
        assertFalse("el título no debe contener el día inicial 15", title.contains("15"))
    }

    @Test fun rangoEntreYCrossMesAnclaAlCierre() {
        val r = parse("feria entre el 28 de febrero y el 1 de marzo")
        assertEquals("feria", r.title.trim())
        assertEquals(LocalDate.of(2027, 3, 1), dueDate("feria entre el 28 de febrero y el 1 de marzo"))
    }

    @Test fun rangoEntreYCrossMesNoDejaResiduoEntreY() {
        val title = parse("feria entre el 28 de febrero y el 1 de marzo").title
        assertFalse("el título no debe contener residuo 'entre'", title.contains("entre"))
        assertFalse("el título no debe contener residuo 'y' suelto", title.contains(" y "))
    }

    @Test fun rangoEntreYCrossAnioAnclaAlCierre() {
        val r = parse("reunión entre el 15 de diciembre y el 5 de enero")
        assertEquals("reunión", r.title.trim())
        assertEquals(LocalDate.of(2027, 1, 5), dueDate("reunión entre el 15 de diciembre y el 5 de enero"))
    }

    @Test fun rangoEntreYConAnioExplicitoAnclaAlCierre() {
        val r = parse("feria entre el 5 de enero de 2027 y el 10 de febrero de 2027")
        assertEquals("feria", r.title.trim())
        assertEquals(LocalDate.of(2027, 2, 10), r.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() })
    }

    @Test fun rangoEntreYRespetaHoraExplicita() {
        val r = parse("feria entre el 28 de febrero y el 1 de marzo a las 9")
        assertEquals("feria", r.title.trim())
        val dt = r.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
        assertNotNull("debe producir vencimiento", dt)
        assertEquals(LocalDate.of(2027, 3, 1), dt!!.toLocalDate())
        assertEquals(9, dt.hour)
    }

    @Test fun rangoEntreYMesRelativoAnclaAlCierre() {
        // "entre el 20 y el 25 del mes que viene": mismo mes relativo al final.
        val r = parse("reunión entre el 20 y el 25 del mes que viene")
        assertEquals("reunión", r.title.trim())
        assertEquals(LocalDate.of(2026, 9, 25), dueDate("reunión entre el 20 y el 25 del mes que viene"))
    }

    @Test fun rangoEntreYNoAgendaContenidoNoMes() {
        // "entre 3 y 5 cajas" no es rango de fecha (sin mes): no agenda nada falso.
        // Protección de contenido: los conectores no deben inventar fechas.
        val r = parse("comprar entre 3 y 5 cajas")
        assertNull("sin mes no debe agendar fecha falsa", r.dueAt)
    }

    @Test fun rangoEntreYRangoHoraNoSeAfecta() {
        // "entre las 3 y las 5 de la tarde" es rango de HORA, no de fecha. El rewriter
        // de hora (entreRangeNormalizerRewriter) corre antes y NO debe ser roto por los
        // nuevos patrones de fecha. Regresión de no-colisión.
        val r = parse("reunión entre las 3 y las 5 de la tarde")
        // Rango horario produce vencimiento hoy (3-5pm) o se trata como bloque de tiempo;
        // lo importante: el título no contiene "entre" como residuo.
        assertFalse("el título no debe dejar residuo 'entre'", r.title.contains("entre"))
    }

    // Rangos con mes en el extremo INICIAL y día de CIERRE suelto (c.445): "del 15 de
    // diciembre al 20", "entre el 15 de diciembre y el 20". Antes NO casaban: crossMonth
    // exige mes en ambos lados, dayRange exige mes al final. Caían a monthNamePattern que
    // anclaba al INICIAL y dejaba "del al 20"/"entre y" como residuo del título.

    @Test fun rangoMesInicioDiaCierreSueltoAnclaAlCierre() {
        val r = parse("feria del 15 de diciembre al 20")
        assertEquals("feria", r.title.trim())
        assertEquals(LocalDate.of(2026, 12, 20), dueDate("feria del 15 de diciembre al 20"))
    }

    @Test fun rangoMesInicioDiaCierreSueltoNoDejaResiduo() {
        val title = parse("feria del 15 de diciembre al 20").title
        assertFalse("el título no debe contener residuo 'del'", title.contains("del"))
        assertFalse("el título no debe contener el día inicial 15", title.contains("15"))
    }

    @Test fun rangoEntreMesInicioDiaCierreSueltoAnclaAlCierre() {
        val r = parse("feria entre el 15 de diciembre y el 20")
        assertEquals("feria", r.title.trim())
        assertEquals(LocalDate.of(2026, 12, 20), dueDate("feria entre el 15 de diciembre y el 20"))
    }

    @Test fun rangoMesInicioDiaCierreSueltoRespetaHoraExplicita() {
        val r = parse("feria del 15 de diciembre al 20 a las 9")
        assertEquals("feria", r.title.trim())
        val dt = r.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
        assertNotNull("debe producir vencimiento", dt)
        assertEquals(LocalDate.of(2026, 12, 20), dt!!.toLocalDate())
        assertEquals(9, dt.hour)
    }

    @Test fun rangoMesInicioConAnioExplicitoDiaCierreSuelto() {
        val r = parse("feria del 15 de diciembre del 2026 al 20")
        assertEquals("feria", r.title.trim())
        assertEquals(LocalDate.of(2026, 12, 20), r.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() })
    }

    @Test fun rangoMesInicioDiaCierreSueltoNoColisionaConCrossMes() {
        // "del 15 de diciembre al 20 de enero" tiene mes en el CIERRE → cross-mes c.443.
        // El nuevo patrón bare-end NO debe robarlo (lookahead lo evita): ancla al 20 de enero.
        val r = parse("feria del 15 de diciembre al 20 de enero")
        assertEquals("feria", r.title.trim())
        assertEquals(LocalDate.of(2027, 1, 20), dueDate("feria del 15 de diciembre al 20 de enero"))
    }

    @Test fun rangoMesInicioDiaCierreSueltoNoAgendaContenidoNoMes() {
        // "del 3 de unidades al 5" no es rango de fecha (mes inválido): no agenda nada falso.
        val r = parse("feria del 3 de unidades al 5")
        assertNull("sin mes válido no debe agendar fecha falsa", r.dueAt)
    }

    // --- c.446-regression: día de CIERRE suelto con D2 < D1 cruza al mes SIGUIENTE ---
    // Antes el cierre caía en el MISMO mes ("del 31 de diciembre al 2" → 2 de diciembre),
    // quedando ANTES que la apertura (31). El cierre correcto es el mes siguiente (enero).
    // Sin año explícito, monthNamePattern aplica su roll anual desde `now` (2026-08-17).

    @Test fun rangoMesInicioDiaCierreSueltoCruzaMesSiguiente() {
        assertEquals(
            LocalDate.of(2027, 1, 2),
            dueDate("feria del 31 de diciembre al 2")
        )
    }

    @Test fun rangoMesInicioDiaCierreSueltoCruzaMesSiguienteEntre() {
        assertEquals(
            LocalDate.of(2027, 1, 2),
            dueDate("feria entre el 31 de diciembre y el 2")
        )
    }

    @Test fun rangoMesInicioDiaCierreSueltoCruzaFebreroAMarzo() {
        assertEquals(
            LocalDate.of(2027, 3, 2),
            dueDate("feria del 28 de febrero al 2")
        )
    }

    @Test fun rangoMesInicioDiaCierreSueltoCruzaEneroAFebrero() {
        assertEquals(
            LocalDate.of(2027, 2, 3),
            dueDate("feria del 30 de enero al 3")
        )
    }

    @Test fun rangoMesInicioDiaCierreSueltoCruzaMesAnioExplicito() {
        // Año explícito + cruce diciembre→enero: el cierre rola al año siguiente.
        assertEquals(
            LocalDate.of(2027, 1, 2),
            dueDate("feria del 31 de diciembre del 2026 al 2")
        )
    }

    @Test fun rangoMesInicioDiaCierreSueltoCruzaMesRespetaHora() {
        val r = parse("feria del 31 de diciembre al 2 a las 9")
        assertEquals("feria", r.title.trim())
        val dt = r.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
        assertNotNull("debe producir vencimiento", dt)
        assertEquals(LocalDate.of(2027, 1, 2), dt!!.toLocalDate())
        assertEquals(9, dt.hour)
    }

    @Test fun rangoMesInicioDiaCierreSueltoMismoMesCuandoD2Mayor() {
        // D2 >= D1 → mismo mes (no debe regresar a la rama cross-mes).
        assertEquals(
            LocalDate.of(2026, 12, 20),
            dueDate("feria del 15 de diciembre al 20")
        )
    }
}
