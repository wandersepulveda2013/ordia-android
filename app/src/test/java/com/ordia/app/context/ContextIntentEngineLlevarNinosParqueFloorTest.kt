package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.852: destino de OCIO FAMILIAR «parque» del piso «llevar a los niños al
 * colegio» c.773 (diagonal coloquial «cole» c.850) — candidata 3/6 de la
 * sonda persistida c.845 `tools/probe/SeventhClassErrandProbe.kt` (NULL PRE
 * verificado por la sonda: «llevar a los niños al parque mañana» caía a
 * NULL mientras «…al colegio/cole» capturaba ERRAND — el paseo familiar
 * dicho como se habla se perdía en silencio). Levantamiento deliberado de
 * la restricción c.773 (piso acotado a destinos educativos): el backlog la
 * promovió a candidata propia (una forma por ciclo). Fix barato: la lista
 * de destinos del piso `ERRAND_SCHOOL_RUN_FLOOR` admite «parque» en los 2
 * puntos lockstep (piso + plantilla de título, lección c.717); keyword-
 * OBJETO «niños» ya existe (c.773) → lockstep coste-cero. Acotado
 * deliberado (una forma por ciclo): «al cine»/«al parque acuático» (con
 * calificador el `.*` de la plantilla ya lo preserva en el título) y otros
 * destinos de ocio quedan FUERA como candidatas propias.
 */
class ContextIntentEngineLlevarNinosParqueFloorTest {

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura parque manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños al parque mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al parque", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura presente llevo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevo a los niños al parque mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevo a los niños al parque", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, llevar a los niños al parque mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al parque", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana llevar a los niños al parque", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al parque", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura mis ninos tarde`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mis niños al parque esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a mis niños al parque", intent.title)
        assertNotNull(intent.dueAt)
    }

    // ---- Envolvente: c.613 gobierna TASK (lockstep WRAPPABLE_PATTERNS) ----

    @Test
    fun `envolvente c613 gobierna TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame llevar a los niños al parque", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llevar a los niños al parque", intent.title)
    }

    // ---- Guards (contratos anti-overreach / anti-falsos-positivos) ----

    @Test
    fun `no llevar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no llevar a los niños al parque mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá llevar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá llevar a los niños al parque mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado lleve descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevé a los niños al parque ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto mascota al parque sigue fuera`() {
        // Anti-solape con la familia mascota (c.747/c.756): el piso exige el
        // objeto `niñ[oa]s?`; «llevar al perro al parque» NO es la forma
        // sondeada (destino `veterinari[oa]s?` en c.747) y sigue NULL.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar al perro al parque mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasar por el parque sigue fuera`() {
        // Regresión cruzada c.718 (verbo «pasar por» — el parque no es
        // lugar de trámite): mi extensión no roba la forma de otro verbo.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "pasar por el parque mañana", 1000)
        )
        assertNull(intent)
    }

    // ---- Regresiones del piso c.773/c.850 (destinos previos intactos) ----

    @Test
    fun `regresión colegio intacta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al colegio", intent.title)
    }

    @Test
    fun `regresión cole intacta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños al cole mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al cole", intent.title)
    }
}
