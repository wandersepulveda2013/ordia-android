package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.929: ancla parte-del-día + sufijo «siguiente» («lo necesito para la mañana
 * siguiente», «llamar al médico en la tarde siguiente»). «La mañana/tarde/noche
 * siguiente» = la parte del día del día SIGUIENTE (the next morning/afternoon),
 * forma cotidiana del vencimiento al día siguiente. Lateral medida FUERA en
 * c.928 (sonda efímera `/tmp/probe929/PreProbe.kt`, motor real vía
 * `tools/run_probe.sh`, now=domingo 2026-08-23 12:00): 5/5 candidatas rotas —
 * con «mañana», el rewrite del pleonasmo «mañana siguiente»→«mañana» (c.148)
 * robaba «siguiente» pese al artículo «la» y la ancla resolvía la parte del
 * día de HOY (p.ej. 09:00 ya pasada a las 12:00 → tarea vencida al nacer, P1
 * evitar-olvidos); con «tarde»/«noche», además «siguiente» sobrevivía como
 * residuo en el título («llamar al médico siguiente»). Fix: (1) guard del
 * pleonasmo con lookbehind anti-artículo «la» (el pleonasmo = tomorrow sólo
 * sin artículo: «envío mañana siguiente» intacto); (2) sufijo aditivo
 * «siguiente(s)» en `standalonePartOfDayPattern` con desplazamiento de fecha
 * +1d (doctrina SIMÉTRICA para TODOS los conectores: a/de/por/en/entrando/
 * entrada/para/durante la); (3) G3 en `mananaOccurrenceIsContent`: «la mañana
 * siguiente» sin conector (narrativa: «la mañana siguiente me desperté tarde»)
 * es CONTENIDO — antes sufría robo de fecha (+1d) y título mutilado; ahora
 * due=null con título íntegro. Determinista (regex), cero random, cero IA
 * fingida, cero UI.
 */
class NaturalTaskParserParteDelDiaSiguienteTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    // domingo 2026-08-23 12:00 (mismo now de la sonda del ciclo)
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 23), LocalTime.NOON, zone)

    private fun parse(text: String) = NaturalTaskParser.parse(text, now, zone)

    // ---- Capturas: «<conector> la <parte> siguiente» → parte del día de MAÑANA ----

    @Test fun paraLaMananaSiguiente_resuelveDiaSiguienteYLimpia() {
        val r = parse("lo necesito para la mañana siguiente")
        assertEquals("lo necesito", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun porLaMananaSiguiente_resuelveDiaSiguienteYLimpia() {
        val r = parse("entregar el informe por la mañana siguiente")
        assertEquals("entregar el informe", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun enLaTardeSiguiente_resuelveDiaSiguienteYSinResiduo() {
        val r = parse("llamar al médico en la tarde siguiente")
        assertEquals("llamar al médico", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun deLaMananaSiguiente_resuelveDiaSiguiente() {
        val r = parse("terminar la tarea de la mañana siguiente")
        assertEquals("terminar la tarea", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun aLaNocheSiguiente_resuelveDiaSiguiente() {
        val r = parse("avisar a Ana a la noche siguiente")
        assertEquals("avisar a Ana", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun primeraHoraDeLaMananaSiguiente_resuelveDiaSiguiente() {
        val r = parse("revisar el contrato a primera hora de la mañana siguiente")
        assertEquals("revisar el contrato", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun tardeSiguienteConHoraExplicita_aplicaAlDiaSiguiente() {
        val r = parse("llamar en la tarde siguiente a las 5")
        assertEquals("llamar", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(17, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    // ---- Narrativa: «la mañana siguiente» sin conector es CONTENIDO (G3) ----

    @Test fun narrativaLaMananaSiguiente_esContenidoIntacto() {
        val r = parse("la mañana siguiente me desperté tarde")
        assertNull(r.dueAt)
        assertEquals("la mañana siguiente me desperté tarde", r.title)
    }

    // ---- Guards: no tocar contenido ni formas hermanas ----

    @Test fun siguienteSinParteDelDia_esContenido() {
        val r = parse("leer el capítulo siguiente")
        assertNull(r.dueAt)
        assertEquals("leer el capítulo siguiente", r.title)
    }

    // ---- Regresiones: pleonasmo sin artículo, período siguiente, planos ----

    @Test fun pleonasmoSinArticulo_sigueSiendoManana() {
        val r = parse("envío mañana siguiente")
        assertEquals("envío", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), DateRules.toLocalDate(r.dueAt!!, zone))
    }

    @Test fun semanaSiguiente_intacta() {
        val r = parse("reunión la semana siguiente")
        assertEquals("reunión", r.title)
        assertEquals(LocalDate.of(2026, 8, 30), DateRules.toLocalDate(r.dueAt!!, zone))
    }

    @Test fun porLaManana_plano_intacto() {
        val r = parse("salir a correr por la mañana")
        assertEquals("salir a correr", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun paraLaManana_plano_c928_intacto() {
        val r = parse("lo necesito para la mañana")
        assertEquals("lo necesito", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }
}
