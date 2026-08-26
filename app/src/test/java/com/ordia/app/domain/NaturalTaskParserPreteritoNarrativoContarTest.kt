package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * c.1210 (lateral ABIERTA registrada c.1095, familia «contar» ausente de la
 * lista cerrada c.950): narrativas en pretérito con «contar» anclaban a
 * «ahora/lunes próximo/ordinal-hora» y MUTILABAN el título (doble daño).
 * Extensión simétrica de la MISMA familia que c.1034 («abrir», cierre de
 * lateral) y c.1041/1045/1048/1049. Los 4 verbos de la familia abarcan las
 * cuatro guardas-DEL-MARCADOR (ya/ahora/weekday-final/ordinal-hora) en UNO
 * de sus formas inequívocas. Pins conservadores byte-idénticos: comandos
 * imperativos, infinitivos, presentes ambiguos, «ya/ahora» + ambiguo y el
 * pin nominal de c.1041 siguen anclando igual.
 */
class NaturalTaskParserPreteritoNarrativoContarTest {

    @Test
    fun narrativa_contar_no_ancla_ni_mutila() {
        val zone = zoneId
        val now = nowMillis(zone)
        assertNoAnchor(NaturalTaskParser.parse("ya me contaste el plan", now, zone))
        assertNoAnchor(NaturalTaskParser.parse("ahorita me contó el plan", now, zone))
        assertNoAnchor(NaturalTaskParser.parse("me contó el plan el lunes", now, zone))
        assertNoAnchor(NaturalTaskParser.parse("me contó a primera hora", now, zone))
    }

    @Test
    fun guard_comandos_e_infinitivos_siguen_anclando() {
        val zone = zoneId
        val now = nowMillis(zone)
        assertAnchors(NaturalTaskParser.parse("cuéntame el plan mañana", now, zone))
        assertAnchors(NaturalTaskParser.parse("quiero contarte mañana", now, zone))
    }

    @Test
    fun guard_presente_ambiguo_sigue_anclando() {
        val zone = zoneId
        val now = nowMillis(zone)
        assertAnchors(NaturalTaskParser.parse("contamos todos los días", now, zone))
        assertAnchors(NaturalTaskParser.parse("ya salimos", now, zone))
        assertAnchors(NaturalTaskParser.parse("te conté mañana", now, zone))
    }

    @Test
    fun regresion_nominal_pin_c1041_resuelto_c1229() {
        // Re-pin c.1229 (precedente pin→resuelto c.1033/c.1035): el prefijo con
        // SUJETO nominal se resolvió con [NaturalTaskParser.narrativeSubjectPrefixHead];
        // suite UNIÓN en [NaturalTaskParserWeekdayFinalSubjectPrefixNarrativaTest].
        val zone = zoneId
        val now = nowMillis(zone)
        val r = NaturalTaskParser.parse("el paquete llegó el lunes", now, zone)
        assertNoAnchor(r)
        assertEquals("el paquete llegó el lunes", r.title)
    }

    private fun assertNoAnchor(r: ParsedTaskInput) {
        assertNull("«${r.title}» no debe anclar", r.dueAt)
    }

    private fun assertAnchors(r: ParsedTaskInput) {
        assertEquals("«${r.title}» debe anclar (guard byte-idéntico)", true, r.dueAt != null)
    }

    private val zoneId get() = ZoneId.of("America/Santo_Domingo")

    private fun nowMillis(zone: ZoneId): Long =
        LocalDateTime.of(2026, 8, 22, 12, 0).atZone(zone).toInstant().toEpochMilli()
}
