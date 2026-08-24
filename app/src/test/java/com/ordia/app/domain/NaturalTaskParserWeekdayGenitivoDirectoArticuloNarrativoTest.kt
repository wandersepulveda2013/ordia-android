package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * c.939: narrativa ordinal con ARTÍCULO al inicio del texto (sin demostrativo)
 * seguida de weekday genitivo DIRECTO — sin genitivo de contenido intermedio —
 * y predicado: «la primera hora del lunes fue aburrida». El ordinal y el
 * weekday pertenecen al sujeto narrativo: no son ancla de fecha ni tokens
 * borrables (extensión de la doctrina c.938, que cubría el demostrativo al
 * inicio — H1; el artículo al inicio + predicado tras el weekday es la misma
 * evidencia inequívoca de sujeto narrativo, doctrina H1-artículo).
 *
 * Medida PRE (sonda efímera /tmp/probe939/PreProbe.kt, motor real vía
 * tools/run_probe.sh, now=domingo 2026-08-23 12:00 America/Santo_Domingo):
 * 9/9 candidatas con DOBLE daño — fecha FALSA del weekday (lun/mar/mié/jue/
 * vie/sáb/dom 09:00/18:00) y título mutilado («la fue aburrida» — el ordinal
 * y el weekday borrados: contenido del usuario). Guards, anclas y regresiones
 * en conducta vigente (ver tests).
 *
 * Anti-overreach (doctrina c.615, simétrica a c.938): el artículo debe estar
 * AL INICIO del texto. Con verbo precedente («quiero/prefiero/avisar la
 * primera hora del lunes…») la forma es bivalente (petición de hueco) y sigue
 * la doctrina ancla — pins byte-idénticos. Sin predicado tras el weekday
 * («la primera hora del lunes») o con modificador («del lunes que viene») el
 * fragmento sigue la doctrina vigente (ancla). «en la…»/«una…» quedan FUERA
 * (laterales medidas: «en la primera hora del lunes me quedé dormido» →
 * due=lun 09:00 título 'en la me quedé dormido'; «una primera hora del lunes
 * fue rara» → due=lun 09:00 título 'una fue rara').
 */
class NaturalTaskParserWeekdayGenitivoDirectoArticuloNarrativoTest {

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

    // ---- Capturas: weekday genitivo DIRECTO tras artículo al inicio ----

    @Test fun laPrimeraHoraDelLunes_esContenidoNarrativo() =
        assertNarrativeIntact("la primera hora del lunes fue aburrida")

    @Test fun laUltimaHoraDelViernes_esContenidoNarrativo() =
        assertNarrativeIntact("la última hora del viernes fue eterna")

    @Test fun lasPrimerasHorasDelLunes_esContenidoNarrativo() =
        assertNarrativeIntact("las primeras horas del lunes fueron duras")

    @Test fun elPrimerMomentoDelSabado_esContenidoNarrativo() =
        assertNarrativeIntact("el primer momento del sábado fue clave")

    @Test fun laUltimaHoraDelMiercoles_esContenidoNarrativo() =
        assertNarrativeIntact("la última hora del miércoles me costó mucho")

    @Test fun lasUltimasHorasDelJueves_esContenidoNarrativo() =
        assertNarrativeIntact("las últimas horas del jueves fueron lentas")

    @Test fun elPrimerMomentoDelDomingo_esContenidoNarrativo() =
        assertNarrativeIntact("el primer momento del domingo fue especial")

    @Test fun laPrimeraHoraDelMartes_esContenidoNarrativo() =
        assertNarrativeIntact("la primera hora del martes fue tranquila")

    @Test fun laPrimeraHoraDeLunesSinContraccion_esContenidoNarrativo() =
        assertNarrativeIntact("la primera hora de lunes fue dura")

    // ---- Guards bivalentes: siguen ancla (byte-idénticos) ----

    @Test fun articuloSinPredicadoTrasWeekday_pinBivalente() =
        // Re-pin legítimo c.965: artículo huérfano consumido y título vacío →
        // fallback resucita el original íntegro.
        assertDueAt("la primera hora del lunes", LocalDate.of(2026, 8, 24), 9, "la primera hora del lunes")

    @Test fun articuloWeekdayQueViene_pinBivalente() =
        // Re-pin legítimo c.965: artículo huérfano consumido y título vacío →
        // fallback resucita el original íntegro.
        assertDueAt("la primera hora del lunes que viene", LocalDate.of(2026, 8, 24), 9, "la primera hora del lunes que viene")

    @Test fun verboMasArticuloSinPredicado_pinBivalente() =
        // Re-pin legítimo c.965: artículo huérfano «la» consumido con el ancla.
        assertDueAt("avisar la primera hora del lunes", LocalDate.of(2026, 8, 24), 9, "avisar")

    @Test fun verboMasArticuloConPredicado_pinBivalente() =
        // Re-pin legítimo c.965: artículo huérfano «la» consumido con el ancla.
        assertDueAt("quiero la primera hora del lunes para estudiar", LocalDate.of(2026, 8, 24), 9, "quiero para estudiar")

    @Test fun verboPrefieroMasArticulo_pinBivalente() =
        // Re-pin legítimo c.965: artículo huérfano «la» consumido con el ancla.
        assertDueAt("prefiero la primera hora del lunes", LocalDate.of(2026, 8, 24), 9, "prefiero")

    // ---- Guards ancla hermanos (byte-idénticos) ----

    @Test fun llamarAlBancoAPrimeraHoraDelLunes_sigueAnclando() =
        assertDueAt("llamar al banco a primera hora del lunes", LocalDate.of(2026, 8, 24), 9, "llamar al banco")

    @Test fun reunionElLunesAPrimeraHora_sigueAnclando() =
        assertDueAt("reunión el lunes a primera hora", LocalDate.of(2026, 8, 24), 9, "reunión")

    @Test fun terminarElViernesUltimaHora_sigueAnclando() =
        assertDueAt("terminar el viernes última hora", LocalDate.of(2026, 8, 28), 18, "terminar")

    @Test fun citaMananaALas9_sigueAnclando() =
        assertDueAt("cita mañana a las 9", LocalDate.of(2026, 8, 24), 9, "cita")

    @Test fun reunionElLunes_sigueAnclando() =
        assertDueAt("reunión el lunes", LocalDate.of(2026, 8, 24), 9, "reunión")

    // ---- Regresiones: doctrinas hermanas intactas ----

    @Test fun narrativaH1Demostrativo_sigueProtegida() =
        assertNarrativeIntact("esa primera hora del lunes fue terrible")

    @Test fun narrativaH2ConWeekday_sigueProtegida() =
        assertNarrativeIntact("la primera hora de clase del lunes fue aburrida")

    @Test fun narrativaH3ConWeekday_sigueProtegida() =
        assertNarrativeIntact("las primeras horas de la mañana del lunes son tranquilas")

    @Test fun narrativaOpinionH3_sigueProtegida() =
        assertNarrativeIntact("creo que las primeras horas de la mañana son las mejores")

    @Test fun narrativaH2SinWeekday_sigueProtegida() =
        assertNarrativeIntact("la primera hora de clase fue aburrida")
}
