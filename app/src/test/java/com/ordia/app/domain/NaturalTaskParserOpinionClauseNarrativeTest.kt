package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.935: narrativa ordinal con cláusula de OPINIÓN precedente + determinante
 * no al inicio del texto («creo que las primeras horas de la mañana son las
 * mejores» = I think the first hours of the morning are the best). Lateral
 * medida FUERA en c.932 (pin conservador `creoQueLasPrimerasHoras_noAlInicioPin`)
 * y c.933. Sonda efímera `/tmp/probe934/PreProbe.kt` (23 casos, motor real vía
 * `tools/run_probe.sh`, now=domingo 2026-08-23 12:00 America/Santo_Domingo):
 * PRE — 8/8 candidatas robadas con DOBLE daño P1 (fecha FALSA de hoy
 * 09:00/15:00/18:00/21:00/04:00 + título mutilado «creo que las son las
 * mejores»); 15/15 guards/regresiones en su conducta vigente.
 * Doctrina (extensión de H3, c.932): el prefijo narrativo ya no exige el
 * determinante AL INICIO del texto — también lo es una cláusula de opinión
 * INEQUÍVOCA («creo/pienso/opino/considero/digo/diría/siento que», «me parece
 * que», «para mí», «a mi juicio», «en mi opinión») seguida del determinante
 * al inicio de la subordinada + predicado a continuación. El marcador de
 * opinión convierte el nominal en SUJETO de la subordinada: nunca es ancla
 * (el ancla siempre lleva conector «a», c.102/c.546/c.931/c.933). Sin
 * predicado («creo que las primeras horas de la mañana») o con verbo NO de
 * opinión («quiero trabajar las…», «avisar las…») sigue la doctrina
 * bivalente/ancla vigente (pins byte-idénticos). Determinista (regex), cero
 * random, cero IA fingida, cero UI.
 */
class NaturalTaskParserOpinionClauseNarrativeTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    // domingo 2026-08-23 12:00 (mismo now de la sonda del ciclo)
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 23), LocalTime.NOON, zone)

    private fun parse(text: String) = NaturalTaskParser.parse(text, now, zone)

    // ---- Capturas: cláusula de opinión + sujeto narrativo ----

    @Test fun creoQueLasPrimerasHorasDeLaManana_esContenidoNarrativo() {
        val r = parse("creo que las primeras horas de la mañana son las mejores")
        assertNull(r.dueAt)
        assertEquals("creo que las primeras horas de la mañana son las mejores", r.title)
    }

    @Test fun piensoQueLasPrimerasHorasDelDia_esContenidoNarrativo() {
        val r = parse("pienso que las primeras horas del día son las más productivas")
        assertNull(r.dueAt)
        assertEquals("pienso que las primeras horas del día son las más productivas", r.title)
    }

    @Test fun mePareceQueLasUltimasHorasDeLaTarde_esContenidoNarrativo() {
        val r = parse("me parece que las últimas horas de la tarde se hacen largas")
        assertNull(r.dueAt)
        assertEquals("me parece que las últimas horas de la tarde se hacen largas", r.title)
    }

    @Test fun sientoQueLasPrimerasHorasDeLaMadrugada_esContenidoNarrativo() {
        val r = parse("siento que las primeras horas de la madrugada fueron duras")
        assertNull(r.dueAt)
        assertEquals("siento que las primeras horas de la madrugada fueron duras", r.title)
    }

    @Test fun opinoQueLasUltimasHorasDelDia_esContenidoNarrativo() {
        val r = parse("opino que las últimas horas del día pasan volando")
        assertNull(r.dueAt)
        assertEquals("opino que las últimas horas del día pasan volando", r.title)
    }

    @Test fun creoQueLaPrimeraHoraDeLaManana_singularEsContenidoNarrativo() {
        val r = parse("creo que la primera hora de la mañana es la mejor")
        assertNull(r.dueAt)
        assertEquals("creo que la primera hora de la mañana es la mejor", r.title)
    }

    @Test fun paraMiLasPrimerasHorasDeLaManana_esContenidoNarrativo() {
        val r = parse("para mí las primeras horas de la mañana son sagradas")
        assertNull(r.dueAt)
        assertEquals("para mí las primeras horas de la mañana son sagradas", r.title)
    }

    @Test fun aMiJuicioLasUltimasHorasDeLaNoche_esContenidoNarrativo() {
        val r = parse("a mi juicio las últimas horas de la noche fueron tranquilas")
        assertNull(r.dueAt)
        assertEquals("a mi juicio las últimas horas de la noche fueron tranquilas", r.title)
    }

    // ---- Guards: conducta vigente que NO debe cambiar ----

    @Test fun avisarALasPrimerasHorasDeLaManana_anclaC933Intacta() {
        val r = parse("avisar a las primeras horas de la mañana")
        assertEquals("avisar", r.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun creoQueAPrimeraHoraEsMejor_conectorASigueAncla() {
        // El conector «a» es ancla por doctrina c.102/c.546 aun dentro de una
        // cláusula de opinión — pin de alcance, byte-idéntico.
        val r = parse("creo que a primera hora es mejor")
        assertEquals("creo que es mejor", r.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun creoQueLasPrimerasHorasDeLaManana_sinPredicadoSigueBivalente() {
        // Sin predicado a continuación el nominal es bivalente (fragmento) —
        // doctrina conservadora c.932, NO se protege. Pin de alcance.
        // Re-pin legítimo c.965: artículo huérfano «las» consumido con el ancla.
        val r = parse("creo que las primeras horas de la mañana")
        assertEquals("creo que", r.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun avisarLasPrimerasHorasDeLaManana_verboNoOpinionSigueBivalente() {
        // Re-pin legítimo c.965: artículo huérfano «las» consumido con el ancla.
        val r = parse("avisar las primeras horas de la mañana")
        assertEquals("avisar", r.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun quieroTrabajarLasPrimerasHoras_verboNoOpinionSigueBivalente() {
        // Re-pin legítimo c.965: artículo huérfano «las» consumido con el ancla.
        val r = parse("quiero trabajar las primeras horas de la mañana")
        assertEquals("quiero trabajar", r.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    // ---- Regresiones ----

    @Test fun lasPrimerasHorasDeLaManana_sujetoAlInicioRegresionC932() {
        val r = parse("las primeras horas de la mañana son las mejores")
        assertNull(r.dueAt)
        assertEquals("las primeras horas de la mañana son las mejores", r.title)
    }

    @Test fun creoQueLasPrimerasHorasDeClase_genitivoContenidoRegresionH2() {
        val r = parse("creo que las primeras horas de clase fueron aburridas")
        assertNull(r.dueAt)
        assertEquals("creo que las primeras horas de clase fueron aburridas", r.title)
    }

    @Test fun reunionAPrimerasHorasDeLaManana_regresionC400() {
        val r = parse("reunión a primeras horas de la mañana")
        assertEquals("reunión", r.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun citaMananaALas9_regresion() {
        val r = parse("cita mañana a las 9")
        assertEquals("cita", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }
}
