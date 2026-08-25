package com.ordia.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1049 — DELTA de la lateral ABIERTA medida en RUN_LOG c.1048: el
 * candado «quedar con» ([ordinalHoraQuedarConArrangement]) cubría sólo el
 * ordinal de hora; las narrativas «ya/ahora/ahorita quedé con …» que
 * cierran con weekday («ya quedé con Ana el lunes») u hora numérica
 * («ya quedé con Ana a las 8») eran suprimidas injustamente por el guard
 * narrativo ([narrativePreteritePrefix]) pese a ser citas futuras.
 * Sonda PRE (RUN_LOG c.1049): L1/L2/G3/G4 → dueAt=null (supresión
 * injusta); G1/G2/G5 → anclan correctamente. Fix: el MISMO candado
 * compartido se aplica en [weekdayOccurrenceIsPreteriteNarrative] y
 * [timeMatchIsPreteriteNarrative] (cero duplicación, doctrina c.1048).
 * Guards narrativos puros («ya me lo pagó el lunes», «ya me llamó a las
 * 8») y las superficies ya cubiertas quedan byte-idénticos.
 */
class NaturalTaskParserQuedarConCandadoWeekdayTimeMatchDeltaTest {

    private val zone: ZoneId = ZoneId.of("America/Santo_Domingo")
    private val now: Long =
        LocalDateTime.of(2026, 8, 25, 12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun assertAnchored(input: String, expectedDate: LocalDate, hour: Int, expectedTitle: String) {
        val result = NaturalTaskParser.parse(input, now, zone)
        val dt = Instant.ofEpochMilli(result.dueAt!!).atZone(zone)
        assertEquals("«$input» debe anclar el día esperado", expectedDate, dt.toLocalDate())
        assertEquals("«$input» debe anclar la hora esperada", hour, dt.hour)
        assertEquals("«$input» debe recortar la fecha del título", expectedTitle, result.title)
    }

    private fun assertNarrativeIntact(input: String) {
        val result = NaturalTaskParser.parse(input, now, zone)
        assertNull("«$input» no debe tener fecha (es relato, no compromiso)", result.dueAt)
        assertEquals("«$input» debe conservar el título íntegro", input, result.title)
    }

    @Test
    fun `ya quede con Ana el lunes ancla el proximo lunes`() {
        assertAnchored("ya quedé con Ana el lunes", LocalDate.of(2026, 8, 31), 9, "ya quedé con Ana")
    }

    @Test
    fun `ya quede con Ana a las 8 ancla hoy a las 8`() {
        assertAnchored("ya quedé con Ana a las 8", LocalDate.of(2026, 8, 25), 8, "ya quedé con Ana")
    }

    @Test
    fun `ahora quede con Ana el sabado ancla el proximo sabado`() {
        assertAnchored("ahora quedé con Ana el sábado", LocalDate.of(2026, 8, 29), 9, "ahora quedé con Ana")
    }

    @Test
    fun `ahorita quede con Ana a las 9 ancla hoy a las 9`() {
        assertAnchored("ahorita quedé con Ana a las 9", LocalDate.of(2026, 8, 25), 9, "ahorita quedé con Ana")
    }

    @Test
    fun `ya me lo pago el lunes sigue sin anclar (narrativa pura)`() {
        assertNarrativeIntact("ya me lo pagó el lunes")
    }

    @Test
    fun `ya me llamo a las 8 sigue sin anclar (narrativa pura)`() {
        assertNarrativeIntact("ya me llamó a las 8")
    }

    @Test
    fun `quede con Ana el lunes sin marcador sigue anclando (pin byte-identico)`() {
        assertAnchored("quedé con Ana el lunes", LocalDate.of(2026, 8, 31), 9, "quedé con Ana")
    }

    @Test
    fun `quede con Ana a las 8 sin marcador sigue anclando (pin byte-identico)`() {
        assertAnchored("quedé con Ana a las 8", LocalDate.of(2026, 8, 25), 8, "quedé con Ana")
    }

    @Test
    fun `ya quede con Ana a primera hora sigue anclando (pin c1048)`() {
        assertAnchored("ya quedé con Ana a primera hora", LocalDate.of(2026, 8, 25), 9, "ya quedé con Ana")
    }
}
