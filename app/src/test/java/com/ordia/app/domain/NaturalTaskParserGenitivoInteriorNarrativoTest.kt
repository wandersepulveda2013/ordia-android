package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.941: narrativa ordinal con genitivo INTERIOR de parte del día antes del
 * weekday genitivo («las primeras horas de la noche del sábado fueron
 * mágicas» = the first hours of Saturday night were magical). Lateral medida
 * en c.940 (sonda, base c.939 `3486ae8`, 3 candidatas) y verificada en este
 * ciclo con sondas efímeras `/tmp/probe941/PreProbe.kt` + `EdgeProbe.kt`
 * (motor real vía `tools/run_probe.sh`, now=domingo 2026-08-23 12:00
 * America/Santo_Domingo): PRE — las formas primera con genitivo interior
 * «de la noche/tarde» (NO admitidas por el sufijo canónico de
 * [primeraHoraPattern], que sólo cubre mañana/madrugada) sufrían DOBLE daño
 * P1: fecha FALSA del weekday + parte del día («sáb 21:00»/«sáb 15:00») y
 * título mutilado (ordinal + ambos genitivos borrados: «las fueron mágicas»).
 * Las formas «última/s» y las primeras con «mañana/madrugada» ya quedaban
 * protegidas por la doctrina H3 vigente (sufijo canónico dentro del match).
 *
 * Doctrina (extensión de la H1-artículo c.939, simétrica): cuando el ordinal
 * narrativo (determinante demostrativo/artículo AL INICIO) gobierna un
 * genitivo interior de parte del día y luego un weekday genitivo, y hay
 * predicado a continuación (sufijo no-blanco, misma convención de c.939), la
 * cadena completa es CONTENIDO narrativo: no ancla fecha ni se borra del
 * título. El fragmento sin predicado, el weekday con modificador («que
 * viene») y las formas con verbo/cláusula precedente («avisar las…»,
 * «quiero las…») siguen la doctrina ancla vigente (pins byte-idénticos). La
 * cola no-copulativa «tengo ensayo» cuenta como predicado por simetría con
 * la decisión doctrinal c.939 («la primera hora del lunes tengo reunion» →
 * contenido). Determinista (regex), cero random, cero IA fingida, cero UI.
 */
class NaturalTaskParserGenitivoInteriorNarrativoTest {

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

    // ---- Capturas: genitivo interior de parte del día + weekday genitivo ----

    @Test fun lasPrimerasHorasDeLaNocheDelSabado_esContenidoNarrativo() =
        assertNarrativeIntact("las primeras horas de la noche del sabado fueron magicas")

    @Test fun lasPrimerasHorasDeLaTardeDelSabado_esContenidoNarrativo() =
        assertNarrativeIntact("las primeras horas de la tarde del sabado fueron tranquilas")

    @Test fun laPrimeraHoraDeLaNocheDelSabado_esContenidoNarrativo() =
        assertNarrativeIntact("la primera hora de la noche del sabado fue tranquila")

    @Test fun elPrimerMomentoDeLaNocheDelSabado_esContenidoNarrativo() =
        assertNarrativeIntact("el primer momento de la noche del sábado fue clave")

    @Test fun esaPrimeraHoraDeLaNocheDelSabado_esContenidoNarrativo() =
        assertNarrativeIntact("esa primera hora de la noche del sábado fue rara")

    @Test fun lasPrimerasHorasDeLaNocheDeSabadoSinContraccion_esContenidoNarrativo() =
        assertNarrativeIntact("las primeras horas de la noche de sábado fueron mágicas")

    @Test fun lasPrimerasHorasDeNocheSinArticuloInterior_esContenidoNarrativo() =
        assertNarrativeIntact("las primeras horas de noche del sábado fueron mágicas")

    // ---- Guards: anclas (byte-idénticos a la medida PRE) ----

    @Test fun sinPredicadoTrasWeekday_pinBivalente() =
        // Re-pin legítimo c.965: el artículo huérfano se consume con el ancla
        // y el título queda vacío → fallback resucita el original íntegro.
        assertDueAt(
            "las primeras horas de la noche del sábado",
            LocalDate.of(2026, 8, 29), 21, "las primeras horas de la noche del sábado"
        )

    @Test fun weekdayConModificadorQueViene_pinBivalente() =
        // Re-pin legítimo c.965: artículo huérfano «las» consumido con el ancla.
        assertDueAt(
            "las primeras horas de la noche del sábado que viene fue tranquila",
            LocalDate.of(2026, 8, 29), 21, "fue tranquila"
        )

    @Test fun verboPrecedente_pinBivalente() =
        // Re-pin legítimo c.965: artículo huérfano «las» consumido con el ancla.
        assertDueAt(
            "avisar las primeras horas de la noche del sábado",
            LocalDate.of(2026, 8, 29), 21, "avisar"
        )

    @Test fun verboYClausula_pinBivalente() =
        // Re-pin legítimo c.965: artículo huérfano «las» consumido con el ancla.
        assertDueAt(
            "quiero las primeras horas de la noche del sábado para estudiar",
            LocalDate.of(2026, 8, 29), 21, "quiero para estudiar"
        )

    // ---- Regresiones: doctrinas hermanas intactas ----

    @Test fun narrativaH3ConCanonico_sigueContenido() =
        assertNarrativeIntact("las primeras horas de la mañana del lunes son tranquilas")

    @Test fun narrativaH1ArticuloDirecta_sigueContenido() =
        assertNarrativeIntact("las primeras horas del lunes son tranquilas")

    @Test fun narrativaH1Demostrativo_sigueContenido() =
        assertNarrativeIntact("esa primera hora del lunes fue terrible")

    @Test fun narrativaH2Compuesta_sigueContenido() =
        assertNarrativeIntact("la primera hora de clase del lunes fue aburrida")

    @Test fun anclaCanonicaAPrimeraHora_sigueAnclando() =
        assertDueAt(
            "llamar al banco a primera hora del lunes",
            LocalDate.of(2026, 8, 24), 9, "llamar al banco"
        )
}
