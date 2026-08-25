package com.ordia.app.context

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1043 — «el día N» (artículo + «día» + número) en la captura pasiva.
 *
 * Lateral medida R3 de la sonda persistida `EleventhClassDigitalProbe` (c.1026)
 * y verificada por sonda efímera `/tmp/probe1042/DiaNProbe.kt` (c.1043, base
 * `f0018b3`): «pagar la luz el día 5» producía intent PAYMENT pero SIN dueAt
 * (sin recordatorio ni What Now — P1 evitar olvidos), mientras la captura
 * manual (`NaturalTaskParser.dayOfMonthPattern`, que admite «el día N») SÍ
 * ancla la fecha. Paridad perdida entre motores. «el 5» (sin «día») ya
 * anclaba en ambos.
 *
 * Fix (UN punto): el `dayPattern` local de `extractDateTime` admite «día»
 * opcional entre «el» y el número, igual que el parser. El guard
 * («el»/mes obligatorio) y la resolución (mismo mes si futuro, si no mes
 * siguiente; «de <mes>» explícito) quedan byte-idénticos.
 */
class ContextIntentEngineElDiaTest {

    private val zone: ZoneId = ZoneId.of("America/Santo_Domingo")
    private val nowMillis: Long =
        LocalDateTime.of(2026, 8, 25, 8, 0).atZone(zone).toInstant().toEpochMilli()

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, nowMillis)
    )

    private fun expectedDueAt(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    // ---- Capturas (RED medido: 6 fallos exactos) ----

    @Test
    fun pagarLaLuzElDia5AnchorsNextMonthFifth() {
        val intent = analyze("pagar la luz el día 5")
        assertNotNull(intent)
        // now = 2026-08-25 → día 5 ya pasó → mes siguiente; la hora por defecto
        // es la del evento (08:00), no las 09:00 del parser (comportamiento vigente)
        assertEquals(expectedDueAt(2026, 9, 5, 8, 0), intent!!.dueAt)
    }

    @Test
    fun pagarLaLuzElDia28AnchorsThisMonth() {
        val intent = analyze("pagar la luz el día 28")
        assertNotNull(intent)
        assertEquals(expectedDueAt(2026, 8, 28, 8, 0), intent!!.dueAt)
    }

    @Test
    fun pagarElArriendoElDia30Anchors() {
        val intent = analyze("pagar el arriendo el día 30")
        assertNotNull(intent)
        assertNotNull(intent!!.dueAt)
    }

    @Test
    fun citaElDia12AnchorsNextMonth() {
        val intent = analyze("cita con el médico el día 12")
        assertNotNull(intent)
        assertNotNull(intent!!.dueAt)
    }

    @Test
    fun elDia5DeSeptiembreAnchorsExplicitMonth() {
        val intent = analyze("pagar la luz el día 5 de septiembre")
        assertNotNull(intent)
        assertNotNull(intent!!.dueAt)
    }

    @Test
    fun elDiaSinTildeAnchors() {
        val intent = analyze("pagar la luz el dia 5")
        assertNotNull(intent)
        assertNotNull(intent!!.dueAt)
    }

    // ---- Guards (verdes desde RED) ----

    @Test
    fun elDiaDeLaMadreDoesNotAnchor() {
        assertNull(analyze("el día de la madre"))
    }

    @Test
    fun trabajoElDiaCompletoDoesNotAnchor() {
        assertNull(analyze("trabajo el día completo"))
    }

    @Test
    fun looseNumberWithoutElDoesNotAnchor() {
        // El motor SÍ puede clasificar SHOPPING; el guard es que no invente fecha
        assertNull(analyze("comprar 2 kilos de arroz")?.dueAt)
    }

    // ---- Regresión (byte-idéntica, verde desde RED) ----

    @Test
    fun el5SinDiaKeepsAnchoring() {
        val intent = analyze("pagar la luz el 5")
        assertNotNull(intent)
        assertEquals(expectedDueAt(2026, 9, 5, 8, 0), intent!!.dueAt)
    }
}
