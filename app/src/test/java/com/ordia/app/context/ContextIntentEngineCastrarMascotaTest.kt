package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1202: forma transitiva «castrar al perro/gato» (candidata (a)
 * DESCUBIERTA en c.1195, clase VIGESIMOSEGUNDA mascotas — sonda persistida
 * `tools/probe/CastrarMascotaProbe.kt`, PRE 4/4 NULL targets —
 * olvido silencioso). «Castrar» es monosemántico veterinario
 * (la esterilización, tan cotidiana como la vacuna); el verbo se
 * ACOTA al objeto mascota (`(?:perr[oa]s?|gat[oa]s?)`) y el
 * destinatario humano queda FUERA (anti-overreach, precedente
 * estructural [HOUSEHOLD_DEWORM_FLOOR] c.1017). Lockstep 2 puntos:
 * piso + extractTitle (hermano c.1017 no añade keyword-verb; gate
 * c.751 intacto). Determinista (regex), sin IA fingida.
 * Kind: HOUSEHOLD (salud de la mascota = cuidado en el hogar,
 * misma deliberación c.757/c.1011/c.1017; TASK solo en envolvente
 * c.613).
 */
class ContextIntentEngineCastrarMascotaTest {

    @Test
    fun `captura castrar al gato plus fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "castrar al gato mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Castrar al gato", intent.title)
    }

    @Test
    fun `captura castrar al perro`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "castrar al perro esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Castrar al perro", intent.title)
    }

    @Test
    fun `captura posesivo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "castrar a mi gata el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Castrar a mi gata", intent.title)
    }

    @Test
    fun `captura plural perros`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "castrar a los perros hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Castrar a los perros", intent.title)
    }

    @Test
    fun `regresion desparasitar intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "desparasitar al gato mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Desparasitar al gato", intent.title)
    }

    @Test
    fun `control negada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no castrar al perro este mes", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `control negada con envolvente de plan`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no voy a castrar al gato", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `control pasado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "castré al gato ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `control nominalizacion`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la castración del perro", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `control objeto no mascota`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "castrar a los niños este mes", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `envolvente gobierna TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame castrar al gato el lunes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
