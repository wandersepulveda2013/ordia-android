package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.943: narrativa ordinal con ARTÍCULO INDEFINIDO al inicio del texto
 * («una/un/unas») + weekday genitivo (directo o tras genitivo interior de
 * parte del día) + predicado («una primera hora del lunes fue rara» = a first
 * hour of Monday was weird). Lateral medida FUERA en c.939 y verificada en
 * este ciclo con sonda efímera `/tmp/probe943/PreProbe.kt` (motor real vía
 * `tools/run_probe.sh`, now=domingo 2026-08-23 12:00 America/Santo_Domingo,
 * base c.942 `f396f54`): PRE — 8/8 candidatas con DOBLE daño P1 (fecha FALSA
 * del weekday [«lun 09:00», «vie 18:00», «mar 09:00», «jue 18:00», «sáb
 * 09:00», «dom 18:00», «sáb 21:00» interior noche, «sáb 15:00» interior
 * tarde] y título mutilado [«una fue rara» — ordinal + genitivo borrados:
 * contenido del usuario]); 5/5 guards bivalentes ancla correctos (verbo
 * precedente, fragmento sin predicado, «que viene», conector «a la»); 5/5
 * regresiones intactas; laterales H3-canónico con indefinido («unas primeras
 * horas de la mañana son duras», «una última hora de la tarde fue eterna»)
 * y «en primera hora del lunes…» sin artículo medidas FUERA (pins
 * byte-idénticos).
 *
 * Doctrina (extensión simétrica de la H1-artículo c.939 y de «en»+artículo
 * c.942): cuando el texto ARRANCA con un artículo INDEFINIDO y el ordinal
 * narrativo tiene weekday genitivo (directo o interior) con predicado a
 * continuación (sufijo no-blanco, misma convención de c.939), la cadena
 * completa es CONTENIDO narrativo: no ancla fecha ni se borra del título.
 * «una» al inicio es inequívoco de sujeto narrativo («una primera hora del
 * lunes fue rara»); con verbo precedente («quiero/prefiero una…») el prefijo
 * no empieza en el indefinido y la forma sigue la doctrina ancla
 * (byte-idéntica). El fragmento sin predicado y el weekday con modificador
 * («que viene») siguen la doctrina vigente. El genitivo de CONTENIDO H2 con
 * indefinido («una primera hora de clase…») NO se admite (no medido —
 * doctrina conservadora, lateral). Determinista (regex), cero random, cero IA
 * fingida, cero UI.
 */
class NaturalTaskParserWeekdayIndefinidoNarrativoTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    // domingo 2026-08-23 12:00 (mismo now de las sondas del ciclo)
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 23), LocalTime.NOON, zone)

    private fun parse(text: String) = NaturalTaskParser.parse(text, now, zone)

    private fun assertDueAt(text: String, date: LocalDate, hour: Int, expectedTitle: String) {
        val r = parse(text)
        assertEquals(date, DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(hour, 0), DateRules.toLocalTime(r.dueAt!!, zone))
        assertEquals(expectedTitle, r.title)
    }

    private fun assertNarrativeIntact(text: String) {
        val r = parse(text)
        assertNull(r.dueAt)
        assertEquals(text, r.title)
    }

    // ---- Capturas: indefinido al inicio + weekday genitivo DIRECTO + predicado ----

    @Test fun unaPrimeraHoraDelLunesFueRara_esContenidoNarrativo() =
        assertNarrativeIntact("una primera hora del lunes fue rara")

    @Test fun unaUltimaHoraDelViernesFueReveladora_esContenidoNarrativo() =
        assertNarrativeIntact("una última hora del viernes fue reveladora")

    @Test fun unasPrimerasHorasDelMartesFueronTranquilas_esContenidoNarrativo() =
        assertNarrativeIntact("unas primeras horas del martes fueron tranquilas")

    @Test fun unasUltimasHorasDelJuevesFueronLentas_esContenidoNarrativo() =
        assertNarrativeIntact("unas últimas horas del jueves fueron lentas")

    @Test fun unPrimerMomentoDelSabadoFueEspecial_esContenidoNarrativo() =
        assertNarrativeIntact("un primer momento del sábado fue especial")

    @Test fun unUltimoMomentoDelDomingoFueAmargo_esContenidoNarrativo() =
        assertNarrativeIntact("un último momento del domingo fue amargo")

    // ---- Capturas: genitivo INTERIOR de parte del día + weekday genitivo + predicado ----

    @Test fun unaPrimeraHoraDeLaNocheDelSabadoFueTranquila_esContenidoNarrativo() =
        assertNarrativeIntact("una primera hora de la noche del sábado fue tranquila")

    @Test fun unasPrimerasHorasDeLaTardeDelSabadoFueronSuaves_esContenidoNarrativo() =
        assertNarrativeIntact("unas primeras horas de la tarde del sábado fueron suaves")

    // ---- Guards bivalentes: ancla BYTE-IDÉNTICA (pins de la doctrina vigente) ----

    @Test fun quieroUnaPrimeraHoraDelLunesLibre_sigueAncla() =
        assertDueAt("quiero una primera hora del lunes libre", LocalDate.of(2026, 8, 24), 9, "quiero una libre")

    @Test fun prefieroUnaUltimaHoraDelViernes_sigueAncla() =
        assertDueAt("prefiero una última hora del viernes", LocalDate.of(2026, 8, 28), 18, "prefiero una")

    @Test fun unaPrimeraHoraDelLunesSinPredicado_sigueAncla() =
        assertDueAt("una primera hora del lunes", LocalDate.of(2026, 8, 24), 9, "una")

    @Test fun unaPrimeraHoraDelLunesQueViene_sigueAncla() =
        assertDueAt("una primera hora del lunes que viene", LocalDate.of(2026, 8, 24), 9, "una")

    @Test fun avisarALaPrimeraHora_sigueAncla() =
        assertDueAt("avisar a la primera hora", LocalDate.of(2026, 8, 23), 9, "avisar")

    // ---- Regresiones: familia ya protegida (c.932/c.938/c.939/c.942) ----

    @Test fun laPrimeraHoraDelLunesFueAburrida_sigueProtegida() =
        assertNarrativeIntact("la primera hora del lunes fue aburrida")

    @Test fun enLaPrimeraHoraDelLunesMeQuedeDormido_sigueProtegida() =
        assertNarrativeIntact("en la primera hora del lunes me quedé dormido")

    @Test fun esaPrimeraHoraDelLunesFueTerrible_sigueProtegida() =
        assertNarrativeIntact("esa primera hora del lunes fue terrible")

    @Test fun lasPrimerasHorasDeLaMananaSonLasMejores_sigueProtegida() =
        assertNarrativeIntact("las primeras horas de la mañana son las mejores")

    @Test fun citaMananaALas9_sigueAncla() =
        assertDueAt("cita mañana a las 9", LocalDate.of(2026, 8, 24), 9, "cita")

    // ---- Pines byte-idénticos de laterales medidas FUERA (doctrina vigente) ----

    @Test fun unasPrimerasHorasDeLaMananaSonDuras_pinLateralFuera() =
        assertDueAt("unas primeras horas de la mañana son duras", LocalDate.of(2026, 8, 23), 9, "unas son duras")

    @Test fun unaUltimaHoraDeLaTardeFueEterna_pinLateralFuera() =
        assertDueAt("una última hora de la tarde fue eterna", LocalDate.of(2026, 8, 23), 15, "una fue eterna")
}
