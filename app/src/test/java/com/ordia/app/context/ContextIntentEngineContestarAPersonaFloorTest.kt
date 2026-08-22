package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.861: piso «contestar a <persona>» — candidata 3/7 de la sonda
 * persistida c.857 `tools/probe/EighthClassAdminProbe.kt` (OCTAVA clase de
 * gestiones de adulto; medición PRE sobre HEAD b4e12fb con sonda efímera
 * `/tmp/probe861/ContestarAProbe.kt`: 5/5 candidatas NULL, 8/8 controles
 * NULL, 5/5 regresiones HIT). «contestar a Juan esta tarde» es la variante
 * coloquial de «responder el correo» (c.860): la comunicación pendiente
 * dicha a la PERSONA, no al objeto. El verbo «contestar» es bivalente
 * (a la pregunta/en el examen/al teléfono/a tiempo) → NUNCA keyword suelta
 * y piso ACOTADO: tras «a» sólo se admite persona en referencia directa
 * (nombre propio o posesivo «mi/tu/su»); los artículos quedan FUERA por
 * lookahead («contestar a la pregunta»/«a las preguntas» son examen, no
 * comunicación pendiente) igual que el adverbial «a tiempo». Lockstep en
 * TRES puntos (lección c.616/c.751; aquí SÍ hace falta keyword, a
 * diferencia de c.860 — «contestar a Juan» no contiene ninguna keyword
 * previa y en producción ni llegaría al análisis): piso en
 * [hasStrongTaskImperative] + plantilla de título (match arranca en el
 * verbo, acuse/prefijo temporal despojados, grafía del usuario preservada,
 * doctrina c.653) + keyword-FRASE «contestar a» en ContextIntent.kt
 * (multi-palabra, hermana de «llamar a»/«hablar con»: subcadena inerte de
 * 0.12 < umbral sin piso; «contestar al…»/«contestar a la pregunta» la
 * contienen pero el piso las rechaza). Sin cláusula dedicada en
 * [imperativeIsNegated]: 0.12+0.1=0.22 < umbral (aritmética c.859/c.860) y
 * el piso lleva su propio lookbehind `(?<!no )`. Kind decidido: TASK, en
 * deliberación contra CALL/NOTE/ERRAND — paridad c.860: es la acción de
 * contestar una comunicación pendiente (gestión), no una llamada
 * telefónica, no contenido capturado y no hay desplazamiento.
 */
class ContextIntentEngineContestarAPersonaFloorTest {

    @Test
    fun `captura base con franja`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar a Juan esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Contestar a Juan", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar a Ana mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Contestar a Ana", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con posesivo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar a mi madre esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Contestar a mi madre", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, contestar a Juan esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Contestar a Juan", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana contestar a Juan", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Contestar a Juan", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `envolvente gobierna task`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame contestar a Juan mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `negada descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no contestar a Juan mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá contestar a Juan", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contesté a Juan ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `verbo aislado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `bivalente pregunta descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar a la pregunta del examen", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `bivalente en el examen descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar en el examen", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `adverbial a tiempo descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar a tiempo los correos", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `regresion responder el correo c860`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder el correo de Ana hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder el correo de Ana", intent.title)
    }
}
