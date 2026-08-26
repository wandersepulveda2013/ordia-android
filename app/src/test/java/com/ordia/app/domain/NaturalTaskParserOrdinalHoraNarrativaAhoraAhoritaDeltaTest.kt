package com.ordia.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1048 — DELTA de la lateral «ordinal de hora («a primera/última hora»)
 * AL FINAL tras cadena narrativa ahora/ahorita + clíticos + pretérito
 * inequívoco» (medida en RUN_LOG c.1048 PRE sobre base 2d3320a4: la
 * aparición anclaba una hora ya PASADA hoy y el título perdía la ordinal:
 * doble daño, compromiso vencido falso + contenido mutilado). Hermana
 * simétrica de c.1041 (weekday final) y c.1045 (hora numérica final): el
 * guard reutiliza el MISMO prefijo narrativo ([narrativePreteritePrefix]).
 * La rama «ya» ya estaba cubierta por [ordinalHoraPreteriteNarrativeLonePrefix]
 * (pin byte-idéntico G5); «ahora/ahorita» quedaban fuera. Verbos pretérito
 * FUERA de la lista cerrada c.950 («avisó») siguen sin disparar en
 * TODAS las superficies — lateral ABIERTA documentada en RUN_LOG c.1048.
 * Guards conservadores (ancla real, futuro con «ahora», cita «quedar con»)
 * siguen byte-idénticos.
 */
class NaturalTaskParserOrdinalHoraNarrativaAhoraAhoritaDeltaTest {

    private val zone: ZoneId = ZoneId.of("America/Santo_Domingo")
    private val now: Long =
        LocalDateTime.of(2026, 8, 25, 12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun assertNarrativeIntact(input: String) {
        val result = NaturalTaskParser.parse(input, now, zone)
        assertNull("«$input» no debe tener fecha (es relato, no compromiso)", result.dueAt)
        assertEquals("«$input» debe conservar el título íntegro", input, result.title)
    }

    private fun assertAnchoredToday(input: String, hour: Int, expectedTitle: String) {
        val result = NaturalTaskParser.parse(input, now, zone)
        val dt = Instant.ofEpochMilli(result.dueAt!!).atZone(zone)
        assertEquals(LocalDate.of(2026, 8, 25), dt.toLocalDate())
        assertEquals(hour, dt.hour)
        assertEquals(expectedTitle, result.title)
    }

    @Test
    fun `ahora narrativo con ordinal primera hora al final no ancla`() {
        assertNarrativeIntact("ahora llegó el cartero a primera hora")
    }

    @Test
    fun `ahora clitico verbo y ordinal primera hora al final no ancla`() {
        assertNarrativeIntact("ahora me llamó a primera hora")
    }

    @Test
    fun `ahorita clitico verbo y ordinal ultima hora al final no ancla`() {
        assertNarrativeIntact("ahorita me escribió a última hora")
    }

    @Test
    fun `ahorita verbo y ordinal ultima hora al final no ancla`() {
        assertNarrativeIntact("ahorita sonó la alarma a última hora")
    }

    @Test
    fun `guard ancla real primera hora sigue anclando`() {
        assertAnchoredToday("avisar a primera hora", 9, "avisar")
    }

    @Test
    fun `guard ancla real ultima hora sigue anclando`() {
        assertAnchoredToday("avisar a última hora", 18, "avisar")
    }

    @Test
    fun `guard futuro con ahora sigue anclando`() {
        assertAnchoredToday("ahora avisaré a primera hora", 9, "avisaré")
    }

    @Test
    fun `guard cita quedar con sigue anclando`() {
        assertAnchoredToday("quedé con Ana a primera hora", 9, "quedé con Ana")
    }

    @Test
    fun `guard cita quedar con con ahora sigue anclando`() {
        assertAnchoredToday("ahora quedé con Ana a primera hora", 9, "ahora quedé con Ana")
    }

    @Test
    fun `pin rama ya narrativa sigue sin anclar`() {
        assertNarrativeIntact("ya me lo dijo a primera hora")
    }

    @Test
    fun `pin contenido c1016 sigue sin anclar`() {
        assertNarrativeIntact("a primera hora llegó el cartero")
    }
}
