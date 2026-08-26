package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1170: candidata (b) FUERTE de la auditoría c.1165 (clase DECIMOCTAVA,
 * vida social — sonda persistida `tools/probe/EighteenthClassSocialProbe.kt`
 * C12, medida NULL; la candidata (a) «felicitar» la fijó el hermano en
 * c.1167). «llevar a los niños a la fiesta del cole el viernes» se
 * DESCARTABA en silencio: el piso escolar c.773 casa el objeto «a los
 * niños» pero su alternancia de destinos es CERRADA (colegio|cole|escuela|
 * guardería|parque) sin «fiesta del cole», y la keyword «llevar» sola
 * (0.12 + bono temporal) queda bajo umbral. Olvido silencioso P1: la
 * fiesta del cole olvidada es el coste parental canónico (disfraces,
 * compañeros, plazo cerrado).
 * Fix lockstep 2 puntos (lección c.616; CERO keywords nuevas — «llevar» ya
 * es keyword TASK histórica, gate c.751 satisfecho): destino NUEVO acotado
 * «fiesta del cole/colegio» en la alternancia del piso escolar c.773
 * `ERRAND_SCHOOL_RUN_FLOOR` + MISMO destino en la plantilla `matchSchoolRun`
 * de [ContextIntentEngine.extractTitle] (grafía del usuario preservada,
 * doctrina c.653; la cola temporal la depura sanitizeTitle). Kind ERRAND
 * (acarreo físico, doctrina de la familia c.1144). UNA forma por ciclo:
 * genitivo escolar EXIGIDO — «fiesta de cumpleaños» y «fiesta» pelada
 * quedan FUERA pineadas NULL; objeto «a mi hija» lateral ABIERTA.
 * PRE medido (sonda efímera motor real, HEAD `62e34ba`): 6/6 capturas
 * NULL, 4/4 guards NULL, 5/5 regresiones HIT, pines NULL, envolvente
 * «tengo que…» TASK 0.49.
 */
class ContextIntentEngineLlevarNinosFiestaColeFloorTest {

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura fiesta cole el viernes`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños a la fiesta del cole el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños a la fiesta del cole", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura fiesta colegio manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños a la fiesta del colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños a la fiesta del colegio", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura cola temporal larga`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños a la fiesta del cole mañana por la tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños a la fiesta del cole", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse de recibo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, llevar a los niños a la fiesta del cole el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños a la fiesta del cole", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura presente llevo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevo a los niños a la fiesta del cole mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevo a los niños a la fiesta del cole", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "el viernes llevar a los niños a la fiesta del cole", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños a la fiesta del cole", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura pelada sin temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños a la fiesta del cole", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños a la fiesta del cole", intent.title)
        assertNull(intent.dueAt)
    }

    // ---- Guards (deben seguir NULL) ----

    @Test
    fun `guard negacion`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no llevo a los niños a la fiesta del cole", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `guard preterito`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevé a los niños a la fiesta del cole ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `guard duda`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no sé si llevar a los niños a la fiesta del cole", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `guard nominal copulativa`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la fiesta del cole es el viernes", 1000)
        )
        assertNull(intent)
    }

    // ---- Regresiones (pisan la misma región; deben seguir HIT) ----

    @Test
    fun `regresion colegio c773`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al colegio", intent.title)
    }

    @Test
    fun `regresion aeropuerto c1158`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños al aeropuerto mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al aeropuerto", intent.title)
    }

    @Test
    fun `regresion medico c776`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a la niña al médico mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a la niña al médico", intent.title)
    }

    @Test
    fun `regresion portatil c1157`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el portátil al trabajo mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el portátil al trabajo", intent.title)
    }

    @Test
    fun `regresion parque c773`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños al parque esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al parque", intent.title)
    }

    // ---- Pines anti-overreach (deben seguir en su estado PRE) ----

    @Test
    fun `pin fiesta de cumpleanos sin genitivo escolar sigue fuera`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños a la fiesta de cumpleaños el sábado", 1000)
        )
        assertNull(intent)
    }

    // c.1172: re-pin legítimo de este pin (misma doctrina que los re-pins
    // c.1133/c.1141/c.1144 de esta familia): la lateral «a mi hija» se
    // cierra con el objeto posesivo singular del piso escolar — pasa de
    // pin NULL a captura ERRAND verificada (también cubierta por
    // ContextIntentEngineLlevarMiHijoEscuelaFloorTest).
    @Test
    fun `repin c1172 objeto mi hija capturado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mi hija a la fiesta del cole el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a mi hija a la fiesta del cole", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `pin fiesta pelada sin genitivo sigue fuera`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños a la fiesta mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pin envolvente tengo que camino generico`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tengo que llevar a los niños a la fiesta del cole el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llevar a los niños a la fiesta del cole", intent.title)
    }
}
