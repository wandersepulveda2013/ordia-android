package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.757 (número provisional, confirmado al fetch pre-push final): forma
 * "vacunar al perro/gato" (sonda paralela `FourthClassVerbDiscoveryProbe.kt`
 * c.740, PRE NULL, mascota ~6/8→7/8) — piso `HOUSEHOLD_VACCINE_FLOOR`
 * acotado al objeto mascota
 * (perro/gato, ambos géneros y plurales), familia [HOUSEHOLD_PET_FLOOR]
 * c.740 / [HOUSEHOLD_VET_FLOOR] c.747+c.755. El verbo "vacunar" es bivalente
 * (al bebé/a los niños), por lo que el piso se acota al objeto mascota.
 * Lockstep keyword↔piso: el objeto "perro/gato" ya existía (c.740/c.744),
 * el lockstep añade el VERBO "vacunar" (precedente c.748 "podar").
 * Guard de negación dedicado en `imperativeIsNegated` (precedente c.748):
 * la keyword-verbo + keyword-mascota + bono temporal elevarían el score
 * por encima del umbral sin pasar por el piso (`(?<!no )`).
 */
class ContextIntentEngineVacunarMascotaFloorTest {

    @Test
    fun `captura vacunar al perro plus fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vacunar al perro el domingo", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Vacunar al perro", intent.title)
    }

    @Test
    fun `captura a la gata plus fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vacunar a la gata el domingo", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Vacunar a la gata", intent.title)
    }

    @Test
    fun `captura posesivo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vacunar a mi perro a las 6", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Vacunar a mi perro", intent.title)
    }

    @Test
    fun `captura plural gatos`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vacunar a los gatos el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Vacunar a los gatos", intent.title)
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
    fun `control negada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no vacunar al perro este mes", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `control duda`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá vacunar al perro este mes", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `control pasado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vacuné al perro ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `control objeto no mascota`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vacunar al bebé este mes", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `envolvente gobierna TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame vacunar al perro el lunes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
