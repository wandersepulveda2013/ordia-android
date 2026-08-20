package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.761 (número provisional, confirmado al fetch pre-push final): forma
 * "bañar al perro/gato" (sonda paralela `FourthClassVerbDiscoveryProbe.kt`
 * c.740, PRE NULL, mascota 7/8 tras los cierres acumulados) — piso
 * `HOUSEHOLD_BATHE_PET_FLOOR` acotado al objeto mascota
 * (perro/gato, ambos géneros y plurales), familia [HOUSEHOLD_PET_FLOOR]
 * c.740 / [HOUSEHOLD_VACCINE_FLOOR] c.757. El verbo "bañar" es bivalente
 * (al bebé/a los niños/reflectivo "bañarse"), por lo que el piso se acota
 * al objeto mascota. Lockstep keyword↔piso: los objetos "perro/gato" ya
 * existían (c.740/c.744), el lockstep añade el VERBO "bañar" (precedente
 * c.748 "podar", c.757 "vacunar"). Guard de negación dedicado en
 * `imperativeIsNegated` (precedente c.757): la keyword-verbo +
 * keyword-mascota + bono temporal elevarían el score por encima del umbral
 * sin pasar por el piso (`(?<!no )`).
 */
class ContextIntentEngineBanarMascotaFloorTest {

    @Test
    fun `captura banar al perro plus fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "bañar al perro el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Bañar al perro", intent.title)
    }

    @Test
    fun `captura a la perra`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "bañar a la perra mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Bañar a la perra", intent.title)
    }

    @Test
    fun `captura gato`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "bañar al gato hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Bañar al gato", intent.title)
    }

    @Test
    fun `captura posesivo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "bañar a mi perro a las 6", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Bañar a mi perro", intent.title)
    }

    @Test
    fun `captura plural perros`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "bañar a los perros el domingo", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Bañar a los perros", intent.title)
    }

    @Test
    fun `regresion sacar al perro intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "sacar al perro mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    @Test
    fun `regresion vacunar al perro intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vacunar al perro este mes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    @Test
    fun `control negada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no bañar al perro mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `control duda`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá bañar al perro mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `control pasado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "bañé al perro ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `control objeto no mascota`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "bañar al bebé mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `envolvente gobierna TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame bañar al perro el lunes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
