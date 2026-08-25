package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Ancla de banda horaria con conector en la captura pasiva (c.1041).
 *
 * Defecto medido (sonda efímera c.1041 + observación lateral medida C6/R8 en
 * `tools/probe/EleventhClassDigitalProbe.kt` c.1026): «reiniciar el router por
 * la tarde» / «cargar el móvil por la noche» / «vaciar la bandeja por la
 * mañana» capturaban el compromiso (HIT) pero nacían SIN dueAt (dueAt=false)
 * → sin recordatorio ni What Now (P1 evitar olvidos). El parser de captura
 * manual SÍ ancla estas bandas (NaturalTaskParser.standalonePartOfDayPattern,
 * l.2522): la captura pasiva perdía la paridad.
 *
 * Fix: fallback en [ContextIntentEngine.extractDateTime] bajo `targetTime ==
 * null` que fija la hora canónica de la banda (mañana→09:00, tarde→15:00,
 * noche→21:00, madrugada→04:00 — mismas canónicas que el parser). La fecha se
 * sigue resolviendo por sus reglas propias («mañana por la tarde» → mañana
 * 15:00; «por la tarde» → hoy 15:00). Una hora numérica explícita siempre
 * gana (más específica). Determinista (regex), sin IA fingida.
 */
class ContextIntentEnginePorLaBandaTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    private fun dateTimeOf(millis: Long): ZonedDateTime =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)

    // --- Capturas: la banda con conector ancla la hora canónica (RED) ---

    @Test
    fun porLaTardeAnchorsTodayAtThreePm() {
        val due = ContextIntentEngine.extractDateTime("reiniciar el router por la tarde")
        assertNotNull("«por la tarde» debe anclar la hora canónica 15:00", due)
        val z = dateTimeOf(due!!)
        assertEquals(15, z.hour)
        assertEquals(0, z.minute)
        assertEquals(LocalDate.now(), z.toLocalDate())
    }

    @Test
    fun porLaNocheAnchorsTodayAtNinePm() {
        val due = ContextIntentEngine.extractDateTime("cargar el móvil por la noche")
        assertNotNull("«por la noche» debe anclar la hora canónica 21:00", due)
        val z = dateTimeOf(due!!)
        assertEquals(21, z.hour)
        assertEquals(LocalDate.now(), z.toLocalDate())
    }

    @Test
    fun porLaMananaAnchorsTodayAtNineAm() {
        val due = ContextIntentEngine.extractDateTime("vaciar la bandeja por la mañana")
        assertNotNull("«por la mañana» debe anclar la hora canónica 09:00", due)
        val z = dateTimeOf(due!!)
        assertEquals(9, z.hour)
        assertEquals(
            "«por la mañana» es hora del día (hoy), no «mañana» = día siguiente",
            LocalDate.now(), z.toLocalDate()
        )
    }

    @Test
    fun aLaTardeAnchorsTodayAtThreePm() {
        val due = ContextIntentEngine.extractDateTime("reunión a la tarde")
        assertNotNull("«a la tarde» debe anclar la hora canónica 15:00", due)
        val z = dateTimeOf(due!!)
        assertEquals(15, z.hour)
        assertEquals(LocalDate.now(), z.toLocalDate())
    }

    @Test
    fun deLaNocheAnchorsTodayAtNinePm() {
        val due = ContextIntentEngine.extractDateTime("llegada de la noche")
        assertNotNull("«de la noche» debe anclar la hora canónica 21:00", due)
        val z = dateTimeOf(due!!)
        assertEquals(21, z.hour)
        assertEquals(LocalDate.now(), z.toLocalDate())
    }

    @Test
    fun enLaTardeAnchorsTodayAtThreePm() {
        val due = ContextIntentEngine.extractDateTime("hoy en la tarde")
        assertNotNull("«en la tarde» (forma caribeña) debe anclar 15:00", due)
        val z = dateTimeOf(due!!)
        assertEquals(15, z.hour)
        assertEquals(LocalDate.now(), z.toLocalDate())
    }

    @Test
    fun deLaMadrugadaAnchorsTodayAtFourAm() {
        val due = ContextIntentEngine.extractDateTime("salida de la madrugada")
        assertNotNull("«de la madrugada» debe anclar la hora canónica 04:00", due)
        val z = dateTimeOf(due!!)
        assertEquals(4, z.hour)
        assertEquals(LocalDate.now(), z.toLocalDate())
    }

    @Test
    fun mananaPorLaTardeIsTomorrowAtThreePm() {
        val due = ContextIntentEngine.extractDateTime("pagar la luz mañana por la tarde")
        assertNotNull("«mañana por la tarde» debe anclar mañana 15:00", due)
        val z = dateTimeOf(due!!)
        assertEquals(15, z.hour)
        assertEquals(LocalDate.now().plusDays(1), z.toLocalDate())
    }

    @Test
    fun endToEndCaptureAnchorsDueAt() {
        val i = analyze("reiniciar el router por la tarde")
        assertNotNull("la captura pasiva con «por la tarde» debe nacer con dueAt", i)
        assertNotNull(i!!.dueAt)
        assertEquals(15, dateTimeOf(i.dueAt!!).hour)
    }

    // --- Guards: prioridad de la hora numérica y anti-overreach (verdes desde RED) ---

    @Test
    fun explicitNumericTimeKeepsPriority() {
        val due = ContextIntentEngine.extractDateTime("reunión a las 4 de la tarde")
        assertNotNull(due)
        assertEquals(
            "la hora numérica explícita conserva su prioridad sobre la banda",
            16, dateTimeOf(due!!).hour
        )
    }

    @Test
    fun negatedPlanWrapperStillDiscards() {
        assertNull(
            "guard c.1009: «no voy a … por la tarde» sigue descartado",
            analyze("no voy a reiniciar el router por la tarde")
        )
    }

    @Test
    fun bareBandWordWithoutConnectorDoesNotAnchor() {
        assertNull(
            "«tarde» suelta sin conector no debe inventar una hora",
            ContextIntentEngine.extractDateTime("trabajo tarde")
        )
    }
}
