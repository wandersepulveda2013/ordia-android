package com.ordia.app.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursTest {
    @Test
    fun overnightWindow_isHandled() {
        assertTrue(QuietHours.contains(23 * 60, 22 * 60, 7 * 60))
        assertTrue(QuietHours.contains(6 * 60 + 30, 22 * 60, 7 * 60))
        assertFalse(QuietHours.contains(12 * 60, 22 * 60, 7 * 60))
    }

    @Test
    fun daytimeWindowAndDisabledWindow_areHandled() {
        assertTrue(QuietHours.contains(13 * 60, 12 * 60, 14 * 60))
        assertFalse(QuietHours.contains(15 * 60, 12 * 60, 14 * 60))
        assertFalse(QuietHours.contains(12 * 60, 12 * 60, 12 * 60))
    }

    // --- nextEndMillis: destino de reagendado cuando un recordatorio dispara
    // durante horas silenciosas (TaskReminderWorker y CommitmentDueWorker). Es la
    // ruta P1 que decide CUÁNDO vuelve a sonar un aviso diferido; sin tests, un
    // regresión silenciosa podría posponerlo a un instante pasado (re-disparo
    // inmediato) o al día equivocado (olvido de la cita). Se verifica con zona
    // fija para que la prueba sea determinista e independiente del huso de CI. ---

    private val zone: ZoneId = ZoneId.of("America/Santo_Domingo")

    private fun millis(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()

    private fun endMillis(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun nextEndOvernightLateNight_returnsNextMorningEnd() {
        // Ventana 22:00→07:00. A las 23:00 (pleno silencio) el fin es mañana 07:00.
        val now = millis(2026, 8, 18, 23, 0)
        val expected = endMillis(2026, 8, 19, 7, 0)

        assertEquals(expected, QuietHours.nextEndMillis(now, 22 * 60, 7 * 60, zone))
    }

    @Test
    fun nextEndOvernightEarlyMorning_returnsTodayEnd() {
        // A las 06:00 aún dentro del silencio nocturno: el fin es HOY 07:00 (futuro).
        val now = millis(2026, 8, 18, 6, 0)
        val expected = endMillis(2026, 8, 18, 7, 0)

        assertEquals(expected, QuietHours.nextEndMillis(now, 22 * 60, 7 * 60, zone))
    }

    @Test
    fun nextEndOvernightEarlyMorning_minuteBoundary_returnsTodayEnd() {
        // 06:59:30 (segundos no cero): el truncado a minutos sigue siendo < fin, así
        // que aterriza en HOY 07:00. Candado anti-regresión del cómputo a minutos.
        val now = ZonedDateTime.of(2026, 8, 18, 6, 59, 30, 0, zone).toInstant().toEpochMilli()
        val expected = endMillis(2026, 8, 18, 7, 0)

        assertEquals(expected, QuietHours.nextEndMillis(now, 22 * 60, 7 * 60, zone))
    }

    @Test
    fun nextEndDaytime_returnsTodayEnd() {
        // Ventana diurna 12:00→14:00. A las 13:00 el fin es HOY 14:00.
        val now = millis(2026, 8, 18, 13, 0)
        val expected = endMillis(2026, 8, 18, 14, 0)

        assertEquals(expected, QuietHours.nextEndMillis(now, 12 * 60, 14 * 60, zone))
    }

    @Test
    fun nextEndMidnightEnd_overnightLate_returnsMidnightTonight() {
        // Fin a las 00:00 (medianoche). A las 23:00 el próximo 00:00 es esta medianoche.
        val now = millis(2026, 8, 18, 23, 0)
        val expected = endMillis(2026, 8, 19, 0, 0)

        assertEquals(expected, QuietHours.nextEndMillis(now, 22 * 60, 0, zone))
    }

    @Test
    fun nextEndWhileInQuiet_alwaysStrictlyAfterNow() {
        // Invariante de contrato: cuando contains==true, el destino SIEMPRE es
        // estrictamente futuro (nunca pasado => nunca re-disparo inmediato en bucle).
        val cases = listOf(
            Triple(millis(2026, 8, 18, 23, 0), 22 * 60, 7 * 60),
            Triple(millis(2026, 8, 18, 6, 0), 22 * 60, 7 * 60),
            Triple(millis(2026, 8, 18, 13, 0), 12 * 60, 14 * 60),
            Triple(millis(2026, 8, 18, 23, 0), 22 * 60, 0)
        )
        for ((now, start, end) in cases) {
            val current = ZonedDateTime
                .ofInstant(java.time.Instant.ofEpochMilli(now), zone)
                .let { it.hour * 60 + it.minute }
            assertTrue("expected in quiet", QuietHours.contains(current, start, end))
            val target = QuietHours.nextEndMillis(now, start, end, zone)
            assertTrue("target must be after now", target > now)
        }
    }
}
