package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.854: dativo enclítico de «llevar/devolver» — candidata 5/6 de la sonda
 * persistida c.845 `tools/probe/SeventhClassErrandProbe.kt` (NULL PRE
 * re-verificado por la sonda sobre HEAD d403b59: «llevarle el almuerzo a
 * papá mañana» y «devolverle el dinero a Juan mañana» caían a NULL — 2 de
 * los 14 NULLs restantes— mientras la forma no enclítica «devolver las
 * llaves a Marta mañana» capturaba ERRAND vía el piso genérico
 * `ERRAND_VERBS` c.639). El dativo enclítico es LA forma cotidiana del
 * encargo a tercero: el verbo con «le/les» pegado no casa el piso genérico
 * (`\s` tras el verbo) ni activa keyword alguna en el caso de «llevarle»
 * («llevar» es bivalente y nunca fue keyword — c.773), así el encargo se
 * perdía en silencio (olvido silencioso P1). Fix: piso acotado
 * `ERRAND_DATIVE_FLOOR` (dativo «llevarle/llevarles/devolverle/
 * devolverles» + objeto, con guard anti-figurado «la contraria»/«ventaja»/
 * «la delantera») en los 3 puntos lockstep (piso + plantilla de título,
 * lección c.717, + cláusula de negación en [imperativeIsNegated],
 * cinturón y tirantes c.829) + keyword-VERBO-enclítico «llevarle»
 * (lección c.751: sin ella la notificación sin palabra gatillo ni llega al
 * análisis en producción; 0.12 sola queda bajo el umbral y con bono
 * temporal 0.22 < 0.45 — inerte sin piso; «devolverle» ya la cubre la
 * keyword preexistente «devolver» por subcadena). Acotado deliberado (una
 * forma por ciclo): la candidata 6/6 «apuntarse a» reflexivo queda FUERA
 * como candidata propia.
 */
class ContextIntentEngineLlevarleDevolverleFloorTest {

    // ---- Capturas directas (piso dativo) ----

    @Test
    fun `captura llevarle almuerzo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevarle el almuerzo a papá mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevarle el almuerzo a papá", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura devolverle dinero`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "devolverle el dinero a Juan mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Devolverle el dinero a Juan", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura plural llevarles`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevarles los documentos a mis padres el lunes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevarles los documentos a mis padres", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, devolverle el libro a Marta mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Devolverle el libro a Marta", intent.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana llevarle el abrigo a mamá", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevarle el abrigo a mamá", intent.title)
        assertNotNull(intent.dueAt)
    }

    // ---- Envolvente: c.613 gobierna TASK (TASK no está en WRAPPABLE_PATTERNS) ----

    @Test
    fun `envolvente c613 gobierna TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame llevarle el almuerzo a papá", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llevarle el almuerzo a papá", intent.title)
    }

    // ---- Guards (contratos anti-overreach / anti-falsos-positivos) ----

    @Test
    fun `no llevarle descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no llevarle el almuerzo a papá mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá devolverle descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá devolverle el dinero a Juan", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado le lleve descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "le llevé el almuerzo a papá ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `figurado llevarle la contraria descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevarle la contraria a papá otra vez", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `figurado llevarle ventaja descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevarle ventaja a Juan en la clasificación", 1000)
        )
        assertNull(intent)
    }

    // ---- Regresiones del piso genérico c.639 (forma NO enclítica) ----

    @Test
    fun `regresion devolver las llaves a Marta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "devolver las llaves a Marta mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `regresion devolver el libro a la biblioteca`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "devolver el libro a la biblioteca el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertNotNull(intent.dueAt)
    }
}
