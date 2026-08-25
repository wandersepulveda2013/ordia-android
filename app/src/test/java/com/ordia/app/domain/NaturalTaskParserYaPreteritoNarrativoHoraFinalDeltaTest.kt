package com.ordia.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1043 — DELTA de la lateral «hora numérica «a las H» AL FINAL tras
 * cadena narrativa ya/ahora/ahorita + clíticos + pretérito inequívoco»
 * (medida en RUN_LOG c.1042 sobre UNIÓN e66b419b): la hora cierra un
 * RELATO de un hecho cumplido («ya me llamó a las 8»); jamás es ancla
 * de encargo (la hora quedaba en el PASADO hoy y el título perdía «a
 * las 8»: doble daño, compromiso vencido falso + contenido mutilado).
 * Hermana simétrica de c.1041 (weekday final): el guard reutiliza el
 * MISMO prefijo narrativo. Guards conservadores (sin marca narrativa,
 * presente con «ya/ahora») siguen byte-idénticos.
 */
class NaturalTaskParserYaPreteritoNarrativoHoraFinalDeltaTest {

    private val zone: ZoneId = ZoneId.of("America/Santo_Domingo")
    private val now: Long =
        LocalDateTime.of(2026, 8, 23, 12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun assertNarrativeIntact(input: String) {
        val result = NaturalTaskParser.parse(input, now, zone)
        assertNull("«$input» no debe tener fecha (es relato, no compromiso)", result.dueAt)
        assertEquals("«$input» debe conservar el título íntegro", input, result.title)
    }

    private fun assertAnchoredToday(input: String, hour: Int, expectedTitle: String) {
        val result = NaturalTaskParser.parse(input, now, zone)
        val dt = Instant.ofEpochMilli(result.dueAt!!).atZone(zone)
        assertEquals(LocalDate.of(2026, 8, 23), dt.toLocalDate())
        assertEquals(hour, dt.hour)
        assertEquals(expectedTitle, result.title)
    }

    @Test
    fun `ya clitico verbo y hora al final no ancla`() {
        assertNarrativeIntact("ya me llamó a las 8")
    }

    @Test
    fun `ya verbo y hora al final no ancla`() {
        assertNarrativeIntact("ya sonó la alarma a las 7")
    }

    @Test
    fun `ahora narrativo con hora al final no ancla`() {
        assertNarrativeIntact("ahora llegó el cartero a las 9")
    }

    @Test
    fun `ahorita narrativo con hora al final no ancla`() {
        assertNarrativeIntact("ahorita me escribió a las 10")
    }

    @Test
    fun `ya dos cliticos verbo y hora al final no ancla`() {
        assertNarrativeIntact("ya me lo dijo a las 11")
    }

    @Test
    fun `guard sin marca narrativa sigue anclando hora`() {
        assertAnchoredToday("llámame a las 8", 8, "llámame")
    }

    @Test
    fun `guard presente con ahora sigue anclando hora`() {
        assertAnchoredToday("ahora llamo a las 9", 9, "llamo")
    }

    @Test
    fun `guard presente con ya sigue anclando hora`() {
        assertAnchoredToday("ya te aviso a las 8", 8, "te aviso")
    }
}
