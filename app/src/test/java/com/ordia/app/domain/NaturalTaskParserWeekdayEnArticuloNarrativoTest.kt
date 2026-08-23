package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.942: narrativa ordinal con preposición «en» + artículo AL INICIO del texto
 * y weekday genitivo + predicado («en la primera hora del lunes me quedé
 * dormido» = during the first hour of Monday I fell asleep). Lateral medida
 * FUERA en c.939 y verificada en este ciclo con sondas efímeras
 * `/tmp/probe942/PreProbe.kt` + `EdgeProbe.kt` (motor real vía
 * `tools/run_probe.sh`, now=domingo 2026-08-23 12:00 America/Santo_Domingo,
 * base c.941 `a8f7443`): PRE — 7/7 candidatas con DOBLE daño P1 (fecha FALSA
 * del weekday [«lun 09:00», «vie 18:00», «sáb 18:00», «dom 09:00», «sáb
 * 21:00» interior noche] y título mutilado [«en la me quedé dormido» —
 * ordinal + genitivo borrados: contenido del usuario]) + 2 bivalentes
 * («tengo clase»/«tengo ensayo»); 7/7 guards ancla correctos (verbo o nombre
 * precedente, fragmento sin predicado, «que viene», cláusula precedente);
 * 5/5 regresiones intactas.
 *
 * Doctrina (extensión simétrica de la H1-artículo c.939 y del genitivo
 * interior c.941): cuando el texto ARRANCA con «en» + artículo + ordinal
 * narrativo y el weekday genitivo (directo o tras genitivo interior de parte
 * del día) tiene predicado a continuación (sufijo no-blanco, misma convención
 * de c.939), la cadena completa es CONTENIDO narrativo: no ancla fecha ni se
 * borra del título. «en» al inicio es inequívoco de sujeto narrativo («en la
 * primera hora del lunes me quedé dormido»); con verbo/nombre/cláusula
 * precedente («avisar en la…», «reunión en la…», «quiero en la…», «creo que
 * en la…») la frase es ancla de tiempo y queda byte-idéntica. El fragmento
 * sin predicado, el weekday con modificador («que viene») y las colas
 * no-copulativas («tengo clase»/«tengo ensayo») siguen la convención c.939
 * (las colas no-copulativas cuentan como predicado → contenido). Determinista
 * (regex), cero random, cero IA fingida, cero UI.
 *
 * Lateral medida FUERA (registrada, UNA por ciclo): «en primera hora del
 * lunes me quedé dormido» (sin artículo → doble daño medido, título 'en me
 * quedé dormido'; requiere doctrina bivalente propia).
 */
class NaturalTaskParserWeekdayEnArticuloNarrativoTest {

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

    // ---- Capturas: «en» + artículo al inicio + weekday genitivo + predicado ----

    @Test fun enLaPrimeraHoraDelLunesMeQuedeDormido_esContenidoNarrativo() =
        assertNarrativeIntact("en la primera hora del lunes me quedé dormido")

    @Test fun enLasPrimerasHorasDelLunesTrabajeMejor_esContenidoNarrativo() =
        assertNarrativeIntact("en las primeras horas del lunes trabajé mejor")

    @Test fun enLaUltimaHoraDelViernesCerramosElTrato_esContenidoNarrativo() =
        assertNarrativeIntact("en la última hora del viernes cerramos el trato")

    @Test fun enLasUltimasHorasDelSabadoLlegoLaNoticia_esContenidoNarrativo() =
        assertNarrativeIntact("en las últimas horas del sábado llegó la noticia")

    @Test fun enElPrimerMomentoDelDomingoSupeQueEraElla_esContenidoNarrativo() =
        assertNarrativeIntact("en el primer momento del domingo supe que era ella")

    @Test fun enLaPrimeraHoraDeLaNocheDelSabadoSonoElTelefono_esContenidoNarrativo() =
        assertNarrativeIntact("en la primera hora de la noche del sábado sonó el teléfono")

    @Test fun enLaPrimeraHoraDelLunesTengoClase_esContenidoNarrativoPorSimetriaC939() =
        assertNarrativeIntact("en la primera hora del lunes tengo clase")

    @Test fun enLaPrimeraHoraDeLaNocheDelSabadoTengoEnsayo_esContenidoNarrativoPorSimetriaC941() =
        assertNarrativeIntact("en la primera hora de la noche del sábado tengo ensayo")

    // ---- Guards bivalentes: doctrina ancla vigente (byte-idénticos) ----

    @Test fun avisarEnLaPrimeraHoraDelLunes_sigueAncla() =
        assertDueAt("avisar en la primera hora del lunes", LocalDate.of(2026, 8, 24), 9, "avisar en la")

    @Test fun reunionEnLaPrimeraHoraDelLunes_sigueAncla() =
        assertDueAt("reunión en la primera hora del lunes", LocalDate.of(2026, 8, 24), 9, "reunión en la")

    @Test fun enLaPrimeraHoraDelLunesSinPredicado_sigueAncla() =
        assertDueAt("en la primera hora del lunes", LocalDate.of(2026, 8, 24), 9, "en la")

    @Test fun enLaPrimeraHoraDelLunesQueViene_sigueAncla() =
        assertDueAt("en la primera hora del lunes que viene me quedé dormido", LocalDate.of(2026, 8, 24), 9, "en la me quedé dormido")

    @Test fun enLaPrimeraHoraDeLaNocheDelSabadoSinPredicado_sigueAncla() =
        assertDueAt("en la primera hora de la noche del sábado", LocalDate.of(2026, 8, 29), 21, "en la")

    @Test fun quieroEnLaPrimeraHoraDelLunesMeAvies_sigueAnclaConVerboPrecedente() =
        assertDueAt("quiero en la primera hora del lunes me avises", LocalDate.of(2026, 8, 24), 9, "quiero en la me avises")

    @Test fun creoQueEnLaPrimeraHoraDelLunes_sigueAnclaConClausulaPrecedente() =
        assertDueAt("creo que en la primera hora del lunes me quedé dormido", LocalDate.of(2026, 8, 24), 9, "creo que en la me quedé dormido")

    // ---- Regresiones: doctrinas vigentes byte-idénticas ----

    @Test fun articuloSinDemostrativo_regresionC939() =
        assertNarrativeIntact("la primera hora del lunes fue aburrida")

    @Test fun demostrativoAlInicio_regresionC938() =
        assertNarrativeIntact("esa primera hora del lunes fue terrible")

    @Test fun h3CanonicoConWeekday_regresionC936() =
        assertNarrativeIntact("las primeras horas de la mañana del lunes son tranquilas")

    @Test fun genitivoInteriorParteDelDia_regresionC941() =
        assertNarrativeIntact("las primeras horas de la noche del sabado fueron magicas")

    @Test fun enArticuloConGenitivoCanonico_regresionH3Vigente() =
        assertNarrativeIntact("en la primera hora de la mañana del lunes me desperté")
}
