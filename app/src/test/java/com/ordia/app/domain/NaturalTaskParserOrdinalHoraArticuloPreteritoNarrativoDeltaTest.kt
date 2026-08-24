package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1023 [renumerado c.1017→c.1019→c.1020→c.1021→c.1022→c.1023 por SÉXTUPLE asignación del cycle-ID
 * durante el re-fetch pre-push: SU c.1017 «desparasitar al perro» contexto
 * (`d8a815e`/`f339c4a`), SU c.1018 «pasear al perro» contexto
 * (`23b2761`/`171e448`) y SU c.1019 delta parser CONVERGENTE
 * (`2825964`/`4edce73`, merge `8d16c9c`/`9857100`) + SU c.1020 contexto «pasear» (`fed4dbd`) + SU c.1021 assistant ARCHIVE (`5f7c77f`/`1012210`) + SU c.1022 assistant «borra» honestidad (`64fe8d3`/`1980dc7`); regiones SIEMPRE
 * DISJUNTAS, precedentes c.1000/c.1004/c.1008/c.1011/c.1016] — delta de
 * COBERTURA sobre la colisión convergente de la lateral «a la/las» + pretérito
 * (ordinal de hora con artículo en narrativa en pretérito):
 * este run implementó en paralelo la MISMA lateral (medida PRE independiente
 * con sonda efímera `/tmp/probe1017/Probe.kt`, HEAD `9920a22`: 8/8 candidatas
 * con doble daño) pero la producción del hermano (su c.1019) resultó
 * ESTRICTAMENTE SUPERIOR — su chequeo H4 va ANTES del retorno por sufijo
 * canónico y cubre también «a la primera hora de la mañana llegó…», que mi
 * versión dejaba anclada (pin FUERA mío erróneo, medido: 21/22 de mis tests
 * pasan sobre la implementación convergente; el único fallo era ese pin).
 * Precedente de colisión convergente c.1002/c.1003: se descarta la
 * implementación propia y se conserva la del hermano; este archivo SOLO añade
 * cobertura complementaria que la batería convergente
 * ([NaturalTaskParserOrdinalHoraALasPreteritoNarrativoTest], 18 tests) no
 * ejercita: prefijo «justo», pretérito plural («llegaron»), verbos distintos
 * («empezó», «llamó el banco»), ancla con modificador de futuro («mañana»),
 * encargo en posición sufijo, ambigua «comimos» (1ª plural, doctrina c.950) y
 * regresiones H3/H2-sin-conector. Todo verde sobre HEAD `5233834`.
 * Determinista (regex), cero random, cero IA fingida, cero UI.
 */
class NaturalTaskParserOrdinalHoraArticuloPreteritoNarrativoDeltaTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    // domingo 2026-08-23 12:00 (mismo now de la sonda del ciclo)
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 23), LocalTime.NOON, zone)

    private fun parse(text: String) = NaturalTaskParser.parse(text, now, zone)

    private fun assertNarrativeIntact(text: String) {
        val r = parse(text)
        assertNull(r.dueAt)
        assertEquals(text, r.title)
    }

    private fun assertDueAt(text: String, expectedDate: LocalDate, expectedHour: Int, expectedTitle: String) {
        val r = parse(text)
        assertEquals(expectedDate, DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(expectedHour, 0), DateRules.toLocalTime(r.dueAt!!, zone))
        assertEquals(expectedTitle, r.title)
    }

    // ---- Capturas complementarias: narrativa intacta (due=null + título) ----

    @Test fun justoALaPrimeraHoraLlego_esContenidoNarrativo() =
        assertNarrativeIntact("justo a la primera hora llegó el cartero")

    @Test fun aLasPrimerasHorasLlegaron_esContenidoNarrativo() =
        assertNarrativeIntact("a las primeras horas llegaron los invitados")

    @Test fun aLaPrimeraHoraEmpezoLaReunion_esContenidoNarrativo() =
        assertNarrativeIntact("a la primera hora empezó la reunión")

    @Test fun aLaUltimaHoraLlamoElBanco_esContenidoNarrativo() =
        assertNarrativeIntact("a la última hora llamó el banco")

    // ---- Guards ancla complementarios (encargo legítimo) ----

    @Test fun avisarALaPrimeraHora_sigueAncla() =
        assertDueAt("avisar a la primera hora", LocalDate.of(2026, 8, 23), 9, "avisar")

    @Test fun salirALaPrimeraHoraManana_sigueAncla() =
        assertDueAt("salir a la primera hora mañana", LocalDate.of(2026, 8, 24), 9, "salir")

    @Test fun llamarAlBancoALaPrimeraHora_sigueAncla() =
        assertDueAt("llamar al banco a la primera hora", LocalDate.of(2026, 8, 23), 9, "llamar al banco")

    // ---- Ambigua 1ª plural pretérito/presente: ancla conservadora (c.950) ----

    @Test fun comimosALaPrimeraHora_ambiguaSigueAncla() =
        assertDueAt("comimos a la primera hora", LocalDate.of(2026, 8, 23), 9, "comimos")

    // ---- Regresiones narrativas hermanas (c.932 H3 / c.931 H2 sin conector) ----

    @Test fun lasPrimerasHorasDeLaManana_regresionH3Intacta() =
        assertNarrativeIntact("las primeras horas de la mañana son las mejores")

    @Test fun laPrimeraHoraDeClase_regresionH2SinConectorIntacta() =
        assertNarrativeIntact("la primera hora de clase fue aburrida")
}
