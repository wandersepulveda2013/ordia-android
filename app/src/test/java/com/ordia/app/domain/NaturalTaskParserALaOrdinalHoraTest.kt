package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.931: conector «a la» + ordinal de hora («avisar a la última hora»,
 * «llamar a la primera hora»). Lateral medida FUERA en c.930 (sondas efímeras
 * `/tmp/probe931/PreProbe.kt` y `EdgeProbe.kt`, motor real vía
 * `tools/run_probe.sh`, now=domingo 2026-08-23 12:00 America/Santo_Domingo):
 * 7/7 candidatas con la fecha BIEN resuelta (18:00/09:00 — el patrón casaba
 * «última/primera hora» sin el conector) pero con el título MUTILADO: el
 * conector «a la» no era consumible por el patrón (el «a» opcional no podía
 * saltar el artículo «la») y sobrevivía como residuo («avisar a la»).
 * Fix (3 puntos, doctrina SIMÉTRICA para ambos ordinales):
 *  (1) `primeraHoraPattern`/`ultimaHoraPattern`: el conector admite el
 *      artículo «la» — `(?:a\s+la\s+|a\s+)?` — NUNCA «la» desnuda sin «a»
 *      («avisar la última hora» = «avisar [de] la última hora [del partido]»,
 *      objeto bivalente: lateral registrada FUERA).
 *  (2) guard `ordinalHoraOccurrenceIsContent`: el conector «a» SIN artículo
 *      sigue siendo ancla por doctrina c.102/c.546, pero «a la» + genitivo de
 *      contenido a continuación («a la primera hora de clase me quedé
 *      dormido») es sustantivo narrativo — protegido hoy vía H2 y DEBE
 *      seguir protegido tras consumir «a la» en el match.
 *  (3) la resolución y el borrado del título fluyen del mismo patrón/guard
 *      (fecha y título nunca divergen, doctrina c.930).
 * FUERA (laterales registradas, byte-idénticas — pins de alcance abajo):
 *  «avisar la última hora» (objeto sin conector: residuo «la» preexistente)
 *  y «avisar a las primeras horas» (artículo plural «las»: otra forma).
 *  [c.933: la lateral del artículo plural se RESUELVE en c.933 — ver
 *  NaturalTaskParserALasOrdinalHoraTest; el pin queda re-pinneado abajo.]
 * Determinista (regex), cero random, cero IA fingida, cero UI.
 */
class NaturalTaskParserALaOrdinalHoraTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    // domingo 2026-08-23 12:00 (mismo now de la sonda del ciclo)
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 23), LocalTime.NOON, zone)

    private fun parse(text: String) = NaturalTaskParser.parse(text, now, zone)

    private fun assertDueAt(text: String, expectedHour: Int, expectedTitle: String) {
        val r = parse(text)
        assertEquals(LocalDate.of(2026, 8, 23), DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(expectedHour, 0), DateRules.toLocalTime(r.dueAt!!, zone))
        assertEquals(expectedTitle, r.title)
    }

    // ---- Capturas: conector «a la» + ancla → fecha canónica + título limpio ----

    @Test fun avisarALaUltimaHora_tituloLimpio() =
        assertDueAt("avisar a la última hora", 18, "avisar")

    @Test fun avisarALaPrimeraHora_tituloLimpio() =
        assertDueAt("avisar a la primera hora", 9, "avisar")

    @Test fun llamarALaUltimaHora_tituloLimpio() =
        assertDueAt("llamar a la última hora", 18, "llamar")

    @Test fun enviarElInformeALaPrimeraHora_tituloLimpio() =
        assertDueAt("enviar el informe a la primera hora", 9, "enviar el informe")

    @Test fun avisarALaPrimeraHoraDeLaManana_tituloLimpio() =
        assertDueAt("avisar a la primera hora de la mañana", 9, "avisar")

    @Test fun avisarALaUltimaHoraDelDia_tituloLimpio() =
        assertDueAt("avisar a la última hora del día", 18, "avisar")

    @Test fun avisarteJustoALaUltimaHora_tituloLimpio() =
        assertDueAt("avisarte justo a la última hora", 18, "avisarte")

    // ---- Guards: «a la» + genitivo de contenido → narrativa protegida ----

    @Test fun aLaPrimeraHoraDeClase_esContenidoNarrativo() {
        val r = parse("a la primera hora de clase me quedé dormido")
        assertNull(r.dueAt)
        assertEquals("a la primera hora de clase me quedé dormido", r.title)
    }

    @Test fun aLaUltimaHoraDelPartido_esContenidoNarrativo() {
        val r = parse("a la última hora del partido llegó el gol")
        assertNull(r.dueAt)
        assertEquals("a la última hora del partido llegó el gol", r.title)
    }

    @Test fun aLaPrimeraHoraDeLaPelicula_esContenidoNarrativo() {
        val r = parse("a la primera hora de la película no pasaba nada")
        assertNull(r.dueAt)
        assertEquals("a la primera hora de la película no pasaba nada", r.title)
    }

    // ---- Regresiones: ancla sin artículo y guard c.930, byte-idénticas ----

    @Test fun avisarAPrimeraHora_regresion() =
        assertDueAt("avisar a primera hora", 9, "avisar")

    @Test fun avisarAUltimaHora_regresion() =
        assertDueAt("avisar a última hora", 18, "avisar")

    @Test fun enviarElInformeAUltimaHoraDelDia_regresion() =
        assertDueAt("enviar el informe a última hora del día", 18, "enviar el informe")

    @Test fun laPrimeraHoraDeClase_guardC930Intacto() {
        val r = parse("la primera hora de clase fue aburrida")
        assertNull(r.dueAt)
        assertEquals("la primera hora de clase fue aburrida", r.title)
    }

    // ---- Pins de alcance: laterales FUERA, byte-idénticas al PRE ----

    @Test fun avisarLaUltimaHora_pinObjetoSinConector() =
        assertDueAt("avisar la última hora", 18, "avisar la")

    @Test fun avisarALasPrimerasHoras_pinArticuloPlural() =
        // Re-pin legítimo c.933: la lateral (artículo plural «a las») se
        // RESUELVE en c.933 — el conector admite «las» y el título queda
        // limpio. La captura completa vive en
        // NaturalTaskParserALasOrdinalHoraTest.
        assertDueAt("avisar a las primeras horas", 9, "avisar")
}
