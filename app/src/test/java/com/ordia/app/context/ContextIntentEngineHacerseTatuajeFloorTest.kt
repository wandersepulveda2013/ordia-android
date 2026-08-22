package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Objeto «tatuaje» en la familia «hacerse» c.881 — lateral medida NULL en la
 * sonda c.862 (hermana de `análisis`/`prueba de sangre` c.876): gestión
 * personal acotada al objeto. Consecuencia real: una cita/gestión propia
 * olvidada.
 *
 * Lockstep TRES puntos (lección c.616/c.751): (1) piso `ERRAND_BLOOD_TEST_FLOOR`
 * con `tatuajes?`; (2) plantilla `extractTitle` (grafía preservada, doctrina
 * c.653); (3) keyword-OBJETO «tatuaje» en `ContextIntent.kt` (0.12 sola
 * inerte y el piso exige verbo reflexivo + objeto, anti-overreach).
 *
 * Sonda efímera `/tmp/probe881/PreTatuajeProbe.kt` sobre HEAD caba490: PRE
 * 3/3 candidatas NULL, 1/1 guard NULL, 2/2 regresiones HIT; POST 6/6 PASS.
 */
class ContextIntentEngineHacerseTatuajeFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    @Test
    fun `hacerse un tatuaje captura`() {
        val r = analyze("hacerse un tatuaje el lunes")
        assertNotNull("«hacerse un tatuaje el lunes» (era NULL en sonda PRE)", r)
        assertEquals(ContextIntentKind.ERRAND, r!!.kind)
        assertEquals("Hacerse un tatuaje", r.title)
    }

    @Test
    fun `hacerme un tatuaje captura`() {
        val r = analyze("hacerme un tatuaje mañana")
        assertNotNull("«hacerme un tatuaje mañana» (era NULL en sonda PRE)", r)
        assertEquals(ContextIntentKind.ERRAND, r!!.kind)
        assertEquals("Hacerme un tatuaje", r.title)
    }

    @Test
    fun `hacerse el tatuaje determinante captura`() {
        val r = analyze("hacerse el tatuaje el sábado")
        assertNotNull("«hacerse el tatuaje el sábado» (era NULL en sonda PRE)", r)
        assertEquals(ContextIntentKind.ERRAND, r!!.kind)
        assertEquals("Hacerse el tatuaje", r.title)
    }

    // -- Guardas (esperado: NULL) --
    @Test
    fun `negada descarta`() {
        assertNull(analyze("no hacerse el tatuaje"))
    }

    // -- Regresiones hermanas (esperado: HIT propio) --
    @Test
    fun `envolvente candado c613 intacta`() {
        val r = analyze("recuérdame hacerme un tatuaje mañana")
        assertNotNull("envolvente c.613 debe seguir HIT", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun `regresión hermana c876 prueba sangre captura`() {
        val r = analyze("hacerme la prueba de sangre mañana")
        assertNotNull("hermana c.876 debe seguir HIT", r)
        assertEquals(ContextIntentKind.ERRAND, r!!.kind)
    }
}
