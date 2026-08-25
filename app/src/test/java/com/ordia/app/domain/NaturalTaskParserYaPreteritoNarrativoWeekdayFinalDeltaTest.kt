package com.ordia.app.domain

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1041 — DELTA residual de la lateral «weekday AL FINAL tras cadena
 * narrativa ya/ahora/ahorita + clíticos + pretérito inequívoco»
 * (ABIERTA tras c.1039, registrada en RUN_LOG): el weekday (p. ej. «el
 * lunes») cierra un RELATO de un hecho cumplido («ya me lo pagó el
 * lunes»); jamás es ancla de encargo. Guards conservadores (genitivo
 * «del lunes», dirección futura y presente) siguen byte-idénticos.
 */
class NaturalTaskParserYaPreteritoNarrativoWeekdayFinalDeltaTest {

    private val zone: ZoneId = ZoneId.of("America/Santo_Domingo")
    private val now: Long =
        LocalDateTime.of(2026, 8, 23, 12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun assertNarrativeIntact(input: String) {
        val result = NaturalTaskParser.parse(input, now, zone)
        assertNull("«$input» no debe tener fecha (es relato, no compromiso)", result.dueAt)
    }

    private fun assertWeekdayAnchored(input: String) {
        val result = NaturalTaskParser.parse(input, now, zone)
        assertEquals(
            java.time.Instant.ofEpochMilli(result.dueAt!!).atZone(zone).toLocalDate(),
            java.time.LocalDate.of(2026, 8, 24)
        )
    }

    @Test
    fun `ya cliticos verbo y weekday al final no ancla`() {
        assertNarrativeIntact("ya me lo pagó el lunes")
    }

    @Test
    fun `ya dos cliticos verbo y weekday al final no ancla`() {
        assertNarrativeIntact("ya se lo dije el viernes")
    }

    @Test
    fun `ahora narrativo con weekday al final no ancla`() {
        assertNarrativeIntact("ahora me lo dijo el martes")
    }

    @Test
    fun `ahorita narrativo con weekday al final no ancla`() {
        assertNarrativeIntact("ahorita llegó el paquete el sábado")
    }

    @Test
    fun `guard sin marca narrativa sigue anclando lunes`() {
        assertWeekdayAnchored("recoger el pago el lunes")
    }

    @Test
    fun `guard genitivo del lunes sigue anclando`() {
        assertWeekdayAnchored("la reunión del lunes sigue en pie")
    }

    @Test
    fun `guard presente con ya sigue anclando`() {
        assertWeekdayAnchored("ya pago el alquiler el lunes")
    }
}
