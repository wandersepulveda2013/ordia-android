package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.860: piso «responder el correo» — candidata 2/7 de la sonda persistida
 * c.857 `tools/probe/EighthClassAdminProbe.kt` (OCTAVA clase de gestiones de
 * adulto; comunicación pendiente, medición PRE sobre HEAD bebc7c2: «responder
 * el correo de Ana hoy» → NULL, 5/5 candidatas NULL). La comunicación
 * escrita pendiente («llamar»/«hablar» tienen CALL; «redactar el correo»
 * TASK desde c.721d) no tenía ruta: ni el verbo «responder» (bivalente:
 * responder en el examen/a la pregunta/por alguien) es keyword ni el objeto
 * la eleva (la keyword ERRAND «correo» —oficina postal— suma 0.12; con bono
 * temporal 0.22 < umbral). Fix mínimo en dos puntos lockstep (lección
 * c.616/c.751; CERO cambios en ContextIntent.kt: la keyword «correo» ya
 * lleva la frase al análisis): piso ACOTADO al objeto `correos?` en
 * [hasStrongTaskImperative] + plantilla de título (match arranca en el
 * verbo, acuse/prefijo temporal despojados, grafía del usuario preservada,
 * doctrina c.653). Guards heredados del patrón: negación («no responder…»),
 * duda («quizá…», hedge c.649), pasado («respondí…»), verbo aislado,
 * bivalentes («en el examen»/«a la pregunta»/«por él») FUERA por el objeto
 * acotado. Sin cláusula dedicada en [imperativeIsNegated]: 0.12+0.1=0.22 <
 * umbral (misma aritmética que c.859). Kind decidido: TASK, en deliberación
 * contra CALL/NOTE/ERRAND — es la acción de contestar un mensaje escrito
 * (gestión), no una llamada telefónica, no contenido capturado y no hay
 * desplazamiento; hermana de «redactar el correo» (c.721d).
 */
class ContextIntentEngineResponderCorreoFloorTest {

    @Test
    fun `captura base con fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder el correo de Ana hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder el correo de Ana", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con fecha sin posesivo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder el correo mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder el correo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura plural con franja`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder los correos esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder los correos", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, responder el correo de Ana mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder el correo de Ana", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hoy responder el correo del banco", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder el correo del banco", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `envolvente gobierna task`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame responder el correo", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `negada descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no responder el correo", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá responder el correo", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "respondí el correo ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `verbo aislado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `bivalente examen descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder en el examen", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `bivalente pregunta descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder a la pregunta", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `figurado responder por descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder por él", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `regresion redactar el correo sigue capturando`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "redactar el correo mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Redactar el correo", intent.title)
        assertNotNull(intent.dueAt)
    }
}
