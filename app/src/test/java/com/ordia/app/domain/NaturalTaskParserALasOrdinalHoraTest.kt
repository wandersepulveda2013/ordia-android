package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.933: conector «a las» (artículo plural) + ordinal de hora plural
 * («avisar a las primeras horas», «llamar a las últimas horas»). Lateral
 * medida FUERA en c.931 y c.932 (sonda efímera `/tmp/probe933/PreProbe.kt`,
 * motor real vía `tools/run_probe.sh`, now=domingo 2026-08-23 12:00
 * America/Santo_Domingo): 7/7 candidatas con la fecha BIEN resuelta
 * (09:00/18:00/15:00/04:00 — el patrón casaba «primeras/últimas horas» sin
 * el conector, forma plural c.400) pero con el título MUTILADO: el conector
 * «a las» no era consumible por el patrón (el «a la» de c.931 no saltaba el
 * artículo plural «las») y sobrevivía como residuo («avisar a las»).
 * Fix (2 puntos, doctrina SIMÉTRICA de c.931 para ambos ordinales):
 *  (1) `primeraHoraPattern`/`ultimaHoraPattern`: el conector admite el
 *      artículo plural — `(?:a\s+las?\s+|a\s+)?` — NUNCA «las» desnuda sin
 *      «a» (objeto bivalente, hermano del pin «avisar la última hora»).
 *  (2) guard `ordinalHoraOccurrenceIsContent`: «a las» + genitivo de
 *      contenido a continuación («a las primeras horas de clase me quedé
 *      dormido») es sustantivo narrativo — ya protegido PRE vía H2 (match
 *      sin conector + artículo precedente) y DEBE seguir protegido tras
 *      consumir «a las» en el match: el chequeo «a la» pasa a «a las?».
 * La resolución y el borrado del título fluyen del mismo patrón/guard
 * (fecha y título nunca divergen, doctrina c.930).
 * FUERA (lateral registrada, byte-idéntica — pin de alcance abajo):
 * «avisar la última hora» (objeto sin conector: residuo «la» preexistente).
 * Determinista (regex), cero random, cero IA fingida, cero UI.
 */
class NaturalTaskParserALasOrdinalHoraTest {

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

    // ---- Capturas: conector «a las» + ancla → fecha canónica + título limpio ----

    @Test fun avisarALasPrimerasHoras_tituloLimpio() =
        assertDueAt("avisar a las primeras horas", 9, "avisar")

    @Test fun avisarALasPrimerasHorasDeLaManana_tituloLimpio() =
        assertDueAt("avisar a las primeras horas de la mañana", 9, "avisar")

    @Test fun avisarALasUltimasHoras_tituloLimpio() =
        assertDueAt("avisar a las últimas horas", 18, "avisar")

    @Test fun avisarALasUltimasHorasDelDia_tituloLimpio() =
        assertDueAt("avisar a las últimas horas del día", 18, "avisar")

    @Test fun avisarteJustoALasPrimerasHoras_tituloLimpio() =
        assertDueAt("avisarte justo a las primeras horas", 9, "avisarte")

    @Test fun llamarALasUltimasHorasDeLaTarde_parteDelDiaGana() =
        assertDueAt("llamar a las últimas horas de la tarde", 15, "llamar")

    @Test fun enviarElInformeALasPrimerasHorasDeLaMadrugada_tituloLimpio() =
        assertDueAt("enviar el informe a las primeras horas de la madrugada", 4, "enviar el informe")

    // ---- Guards: «a las» + genitivo de contenido → narrativa protegida ----

    @Test fun aLasPrimerasHorasDeClase_esContenidoNarrativo() {
        val r = parse("a las primeras horas de clase me quedé dormido")
        assertNull(r.dueAt)
        assertEquals("a las primeras horas de clase me quedé dormido", r.title)
    }

    @Test fun aLasUltimasHorasDelPartido_esContenidoNarrativo() {
        val r = parse("a las últimas horas del partido llegó el gol")
        assertNull(r.dueAt)
        assertEquals("a las últimas horas del partido llegó el gol", r.title)
    }

    @Test fun meDesperteALasPrimerasHorasDeClase_esContenidoNarrativo() {
        val r = parse("me desperté a las primeras horas de clase")
        assertNull(r.dueAt)
        assertEquals("me desperté a las primeras horas de clase", r.title)
    }

    // ---- Guards bivalentes: sin «horas/momento» el patrón no casa ----

    @Test fun avisarALasPrimerasPersonas_sinAncla() {
        val r = parse("avisar a las primeras personas")
        assertNull(r.dueAt)
        assertEquals("avisar a las primeras personas", r.title)
    }

    @Test fun avisarALasPrimeras_sinAncla() {
        val r = parse("avisar a las primeras")
        assertNull(r.dueAt)
        assertEquals("avisar a las primeras", r.title)
    }

    @Test fun llamarALasUltimas_sinAncla() {
        val r = parse("llamar a las últimas")
        assertNull(r.dueAt)
        assertEquals("llamar a las últimas", r.title)
    }

    // ---- Regresiones: c.931 (singular), c.400 (plural sin artículo),
    //      c.932 (H3), pin c.102 — byte-idénticas ----

    @Test fun avisarALaPrimeraHora_regresionC931() =
        assertDueAt("avisar a la primera hora", 9, "avisar")

    @Test fun reunionAPrimerasHorasDeLaManana_regresionC400() =
        assertDueAt("reunión a primeras horas de la mañana", 9, "reunión")

    @Test fun lasPrimerasHorasDeLaMananaSonLasMejores_regresionC932() {
        val r = parse("las primeras horas de la mañana son las mejores")
        assertNull(r.dueAt)
        assertEquals("las primeras horas de la mañana son las mejores", r.title)
    }

    @Test fun terminarElViernesUltimaHora_regresionPinC102() {
        val r = parse("terminar el viernes última hora")
        assertEquals(LocalDate.of(2026, 8, 28), DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(r.dueAt!!, zone))
        assertEquals("terminar", r.title)
    }

    // ---- Pin de alcance: lateral FUERA, byte-idéntica al PRE ----

    @Test fun avisarLaUltimaHora_pinObjetoSinConector() =
        assertDueAt("avisar la última hora", 18, "avisar la")
}
