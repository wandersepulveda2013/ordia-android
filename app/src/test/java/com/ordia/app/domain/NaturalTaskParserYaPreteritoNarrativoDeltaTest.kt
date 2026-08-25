package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1030 — DELTA test-only sobre la implementación convergente del hermano
 * (c.1027, commit d375d99, [NaturalTaskParserYaPreteritoNarrativoTest]).
 *
 * Colisión convergente TOTAL: este ciclo implementó la MISMA lateral
 * «ya <pretérito>» narrativa de forma independiente (guard H5 idéntico en
 * espíritu: `takeUnless` sobre el match «ya» aislado + sufijo con pretérito
 * inequívoco de la lista cerrada c.950). Al integrar, la producción propia
 * resultó duplicada de la remota → descartada NO-destructivamente
 * (precedentes c.1014/c.1020/c.1023). Se conserva SOLO el delta de tests
 * con casos que la clase convergente NO ejercita:
 *
 *  - superficies narrativas adicionales («ya salió el sol», «ya me llamó el
 *    doctor», «ya pagué la factura»): due=null + título íntegro;
 *  - clase semántica «ya no …» (negación de estado presente: «ya no fumo»,
 *    «ya no debo nada», «ya no me llames»): NO es narrativa en pretérito →
 *    SIGUE anclando a AHORA y el «ya» se consume como marcador temporal
 *    (título conserva la negación). Ausente de las 22 pruebas convergentes.
 *
 * Los 6 casos fueron medidos PRE con sonda efímera `/tmp/probe1028/Probe2.kt`
 * contra la implementación remota (6/6 comportamiento esperado).
 *
 * Determinista (regex), cero random, cero IA fingida, cero UI.
 */
class NaturalTaskParserYaPreteritoNarrativoDeltaTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    // domingo 2026-08-23 12:00 (mismo now de la clase convergente)
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 23), LocalTime.NOON, zone)

    private fun parse(text: String) = NaturalTaskParser.parse(text, now, zone)

    @Test
    fun `c1030 narrativa ya salio el sol queda intacta sin fecha falsa`() {
        val r = parse("ya salió el sol")
        assertNull(r.dueAt)
        assertEquals("ya salió el sol", r.title)
    }

    @Test
    fun `c1030 narrativa ya me llamo el doctor queda intacta sin fecha falsa`() {
        val r = parse("ya me llamó el doctor")
        assertNull(r.dueAt)
        assertEquals("ya me llamó el doctor", r.title)
    }

    @Test
    fun `c1030 narrativa ya pague la factura queda intacta sin fecha falsa`() {
        val r = parse("ya pagué la factura")
        assertNull(r.dueAt)
        assertEquals("ya pagué la factura", r.title)
    }

    @Test
    fun `c1030 ya no fumo sigue anclando a ahora`() {
        val r = parse("ya no fumo")
        assertEquals(now, r.dueAt)
        assertEquals("no fumo", r.title)
    }

    @Test
    fun `c1030 ya no debo nada sigue anclando a ahora`() {
        val r = parse("ya no debo nada")
        assertEquals(now, r.dueAt)
        assertEquals("no debo nada", r.title)
    }

    @Test
    fun `c1030 ya no me llames sigue anclando a ahora`() {
        val r = parse("ya no me llames")
        assertEquals(now, r.dueAt)
        assertEquals("no me llames", r.title)
    }
}
