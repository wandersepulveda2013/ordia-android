package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.932: narrativa ordinal con genitivo canónico GOBERNADO DENTRO del match
 * («las primeras horas de la mañana son las mejores» = the first hours of the
 * morning are the best). Lateral (i) medida FUERA en c.930 y c.931 (sondas
 * efímeras `/tmp/probe932/PreProbe.kt` + `EdgeProbe.kt`, motor real vía
 * `tools/run_probe.sh`, now=domingo 2026-08-23 12:00): 12/12 narrativas
 * robadas con DOBLE daño P1 — fecha FALSA (la nota saltaba como tarea de hoy
 * 09:00/15:00/18:00/21:00/04:00) y título mutilado («las son las mejores»).
 * Causa raíz apilada: (1) `primeraHoraPattern`/`ultimaHoraPattern` consumen el
 * genitivo canónico («de la mañana») dentro del propio match, así que el guard
 * c.930 no alcanzaba; (2) `standalonePartOfDayPattern` robaba la parte del día
 * interior de forma independiente. Doctrina (H3): el ordinal con genitivo
 * canónico dentro del match es CONTENIDO narrativo sólo con evidencia
 * inequívoca — determinante (artículo/demostrativo, opcional «en») AL INICIO
 * del texto (sin conector «a», sin verbo precedente) + predicado a
 * continuación — y entonces la parte del día gobernada se suprime también
 * (fecha y título) y la «mañana» interior queda protegida (G4). Sin predicado
 * («las primeras horas de la mañana») o con verbo precedente («avisar las
 * primeras horas de la mañana») sigue la doctrina vigente (bivalente/ancla,
 * pins byte-idénticos); el determinante tras cláusula de opinión («creo que
 * las…») se resolvió en c.935 (extensión de H3, ver
 * NaturalTaskParserOpinionClauseNarrativeTest).
 * Determinista (regex), cero random, cero IA fingida, cero UI.
 */
class NaturalTaskParserOrdinalHoraGenitivoGobernadoTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    // domingo 2026-08-23 12:00 (mismo now de la sonda del ciclo)
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 23), LocalTime.NOON, zone)

    private fun parse(text: String) = NaturalTaskParser.parse(text, now, zone)

    // ---- Capturas: sujeto narrativo con genitivo canónico gobernado ----

    @Test fun lasPrimerasHorasDeLaManana_esContenidoNarrativo() {
        val r = parse("las primeras horas de la mañana son las mejores")
        assertNull(r.dueAt)
        assertEquals("las primeras horas de la mañana son las mejores", r.title)
    }

    @Test fun lasUltimasHorasDeLaNoche_esContenidoNarrativo() {
        val r = parse("las últimas horas de la noche fueron tranquilas")
        assertNull(r.dueAt)
        assertEquals("las últimas horas de la noche fueron tranquilas", r.title)
    }

    @Test fun lasPrimerasHorasDelDia_esContenidoNarrativo() {
        val r = parse("las primeras horas del día son las más productivas")
        assertNull(r.dueAt)
        assertEquals("las primeras horas del día son las más productivas", r.title)
    }

    @Test fun lasUltimasHorasDeLaTarde_esContenidoNarrativo() {
        val r = parse("las últimas horas de la tarde se me hacen largas")
        assertNull(r.dueAt)
        assertEquals("las últimas horas de la tarde se me hacen largas", r.title)
    }

    @Test fun lasPrimerasHorasDeLaMadrugada_esContenidoNarrativo() {
        val r = parse("las primeras horas de la madrugada fueron duras")
        assertNull(r.dueAt)
        assertEquals("las primeras horas de la madrugada fueron duras", r.title)
    }

    @Test fun lasUltimasHorasDelDia_esContenidoNarrativo() {
        val r = parse("las últimas horas del día pasan volando")
        assertNull(r.dueAt)
        assertEquals("las últimas horas del día pasan volando", r.title)
    }

    @Test fun esasPrimerasHorasDeLaManana_demostrativoEsContenido() {
        val r = parse("esas primeras horas de la mañana fueron duras")
        assertNull(r.dueAt)
        assertEquals("esas primeras horas de la mañana fueron duras", r.title)
    }

    @Test fun aquellasUltimasHorasDelDia_demostrativoEsContenido() {
        val r = parse("aquellas últimas horas del día fueron eternas")
        assertNull(r.dueAt)
        assertEquals("aquellas últimas horas del día fueron eternas", r.title)
    }

    @Test fun laPrimeraHoraDeLaManana_singularEsContenido() {
        val r = parse("la primera hora de la mañana fue la mejor")
        assertNull(r.dueAt)
        assertEquals("la primera hora de la mañana fue la mejor", r.title)
    }

    @Test fun laUltimaHoraDelDia_singularEsContenido() {
        val r = parse("la última hora del día fue agotadora")
        assertNull(r.dueAt)
        assertEquals("la última hora del día fue agotadora", r.title)
    }

    @Test fun lasUltimasHorasDeLaJornada_esContenidoNarrativo() {
        val r = parse("las últimas horas de la jornada son pesadas")
        assertNull(r.dueAt)
        assertEquals("las últimas horas de la jornada son pesadas", r.title)
    }

    @Test fun enLasPrimerasHorasDelDia_preposicionEsContenido() {
        val r = parse("en las primeras horas del día trabajé")
        assertNull(r.dueAt)
        assertEquals("en las primeras horas del día trabajé", r.title)
    }

    // ---- Guards: las anclas legítimas NO se tocan ----

    @Test fun avisarAPrimerasHorasDeLaManana_conectorPluralAncla() {
        val r = parse("avisar a primeras horas de la mañana")
        assertEquals("avisar", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun avisarALaPrimeraHoraDeLaManana_conectorALaAnclaC931() {
        val r = parse("avisar a la primera hora de la mañana")
        assertEquals("avisar", r.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun terminarAPrimeraHoraDeLaManana_anclaC930Intacta() {
        val r = parse("terminar el informe a primera hora de la mañana")
        assertEquals("terminar el informe", r.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun reunionAUltimaHora_anclaIntacta() {
        val r = parse("reunión a última hora")
        assertEquals("reunión", r.title)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    // ---- Pins de alcance (byte-idénticos pre/post): doctrina conservadora ----

    @Test fun lasPrimerasHorasDeLaManana_sinPredicadoBivalentePin() {
        // Sin predicado a continuación: forma bivalente (¿objeto? ¿ancla
        // truncada?), doctrina c.931 — NO se protege. Pin de alcance.
        val r = parse("las primeras horas de la mañana")
        assertEquals("las", r.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun avisarLasPrimerasHorasDeLaManana_verboPrecedentePin() {
        // Verbo precedente: el ordinal es objeto bivalente (doctrina c.931,
        // hermana de «avisar la última hora») — NO se protege. Pin de alcance.
        val r = parse("avisar las primeras horas de la mañana")
        assertEquals("avisar las", r.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun creoQueLasPrimerasHoras_noAlInicioPin() {
        // Re-pin legítimo c.935: la cláusula de opinión inequívoca («creo
        // que») + determinante al inicio de la subordinada + predicado ES
        // evidencia narrativa suficiente (extensión de H3; lateral medida
        // FUERA en este ciclo c.932). El nominal es sujeto de la subordinada:
        // due=null + título íntegro (MÁS estricto). La familia completa vive
        // en NaturalTaskParserOpinionClauseNarrativeTest.
        val r = parse("creo que las primeras horas de la mañana son las mejores")
        assertNull(r.dueAt)
        assertEquals("creo que las primeras horas de la mañana son las mejores", r.title)
    }

    @Test fun avisarALasPrimerasHorasDeLaManana_articuloPluralSinConsumirPin() {
        // Re-pin legítimo c.933: «a las» (plural) pasa a ser conector del
        // patrón (lateral c.931/c.932 RESUELTA en c.933) — el match consume
        // «a las» y el título queda limpio. La familia completa vive en
        // NaturalTaskParserALasOrdinalHoraTest.
        val r = parse("avisar a las primeras horas de la mañana")
        assertEquals("avisar", r.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    // ---- Regresiones ----

    @Test fun laPrimeraHoraDeClase_regresionC930() {
        val r = parse("la primera hora de clase fue aburrida")
        assertNull(r.dueAt)
        assertEquals("la primera hora de clase fue aburrida", r.title)
    }

    @Test fun citaMananaALas9_regresion() {
        val r = parse("cita mañana a las 9")
        assertEquals("cita", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun avisarPorLaMananaMisma_regresionC925() {
        val r = parse("avisar a Juan por la mañana misma")
        assertEquals("avisar a Juan", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun terminarElViernesUltimaHora_pinC102Intacto() {
        val r = parse("terminar el viernes última hora")
        assertEquals("terminar", r.title)
        assertEquals(LocalDate.of(2026, 8, 28), DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }
}
