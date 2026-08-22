package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.851: diagonal DIALECTAL «móvil» del piso «cargar el celular» c.751 —
 * candidata 4/6 de la sonda persistida c.845
 * `tools/probe/SeventhClassErrandProbe.kt` (NULL PRE re-verificado por la
 * sonda sobre HEAD 09ea0b1: «cargar el móvil esta noche» caía a NULL
 * mientras «cargar el celular hoy» (c.751) capturaba TASK — asimetría
 * dialectal: «móvil» es LA forma de España, hermana de la asimetría
 * coloquial «cole» c.850 y de la de artículo c.848). Fix barato: el objeto
 * del piso admite la alternancia `m[oó]vil` (con/sin tilde, hermana
 * `tensi[oó]n` c.772) en los 2 puntos lockstep (piso + plantilla de
 * título, lección c.717) + keyword-OBJETO «móvil» (lección c.751: sin
 * ella la notificación sin palabra gatillo ni llega al análisis en
 * producción; la subcadena de «automóvil» suma 0.12 inerte < umbral,
 * mismo argumento que «extensión»/«pretensión» c.772).
 * Acotado deliberado c.851 (una forma por ciclo): «cargar el coche»
 * (objeto bivalente real: equipaje del viaje vs carga del VE — requiere
 * deliberación propia) quedó FUERA como candidata propia — RESUELTO en
 * c.853 (la deliberación concluyó que ambas lecturas son deberes genuinos
 * y el título preserva las palabras del usuario; el guard de exclusión se
 * reescribió como regresión cruzada de captura, precedente c.852).
 */
class ContextIntentEngineCargarMovilFloorTest {

    // ---- Capturas directas (piso, diagonal «móvil») ----

    @Test
    fun `captura movil esta noche`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cargar el móvil esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar el móvil", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura posesivo mi movil`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cargar mi móvil mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar mi móvil", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, cargar el móvil", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar el móvil", intent.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana cargar el móvil", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar el móvil", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura sin tilde`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cargar el movil esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar el movil", intent.title)
        assertNotNull(intent.dueAt)
    }

    // ---- Envolvente: c.613 gobierna TASK (TASK no está en WRAPPABLE_PATTERNS) ----

    @Test
    fun `envolvente c613 gobierna TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame cargar el móvil esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar el móvil", intent.title)
    }

    // ---- Guards (contratos anti-overreach / anti-falsos-positivos) ----

    @Test
    fun `no cargar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no cargar el móvil esta noche", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá cargar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá cargar el móvil", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado cargue descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cargué el móvil anoche", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `regresión cruzada c853 coche captura`() {
        // Regresión cruzada c.853 (reescritura del guard de exclusión
        // deliberada c.851 — la restricción se levantó deliberadamente en
        // c.853 tras la deliberación de la bivalencia: ambas lecturas,
        // equipaje del viaje y carga del VE, son deberes genuinos y el
        // título preserva las palabras del usuario; ni eliminado ni
        // degradado, precedente c.852).
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cargar el coche antes del viaje", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar el coche antes del viaje", intent.title)
    }

    @Test
    fun `objeto bivalente archivo descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cargar el archivo mañana", 1000)
        )
        assertNull(intent)
    }

    // ---- Regresiones del piso c.751 (objeto «celular» intacto) ----

    @Test
    fun `regresión celular intacta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cargar el celular hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar el celular", intent.title)
    }

    @Test
    fun `regresión plural celulares intacta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cargar los celulares mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar los celulares", intent.title)
    }
}
