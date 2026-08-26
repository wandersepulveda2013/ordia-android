package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1205: lateral ABIERTA documentada en la auditoría c.1195 (clase
 * VIGESIMOSEGUNDA mascotas), hermana terminante del piso «castrar» c.1202 —
 * sonda persistida `tools/probe/EsterilizarMascotaProbe.kt`, PRE 3/3 targets NULL —
 * olvido silencioso). «Esterilizar» es monosemántico veterinario
 * (la esterilización, tan cotidiana como la vacuna); el verbo se
 * ACOTA al objeto mascota (`(?:perr[oa]s?|gat[oa]s?)`) y el
 * destinatario humano queda FUERA (anti-overreach, precedente
 * estructural [HOUSEHOLD_DEWORM_FLOOR] c.1017); sinónimo directo de castración (esterilización). Lockstep 2 puntos:
 * piso + extractTitle (hermano c.1017 no añade keyword-verb; gate
 * c.751 intacto). Determinista (regex), sin IA fingida.
 * Kind: HOUSEHOLD (salud de la mascota = cuidado en el hogar,
 * misma deliberación c.757/c.1011/c.1017; TASK solo en envolvente
 * c.613).
 */
class ContextIntentEngineEsterilizarMascotaTest {

    @Test
    fun `captura esterilizar al perro mas temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "esterilizar al perro mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Esterilizar al perro", intent.title)
    }

    @Test
    fun `captura esterilizar destino a la`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "esterilizar a la gata esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Esterilizar a la gata", intent.title)
    }

    @Test
    fun `captura posesivo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "esterilizar a mi gata el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Esterilizar a mi gata", intent.title)
    }

    @Test
    fun `captura plural perros`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "esterilizar a los perros hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Esterilizar a los perros", intent.title)
    }

    @Test
    fun `regresion castrar intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "castrar al gato mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Castrar al gato", intent.title)
    }

    @Test
    fun `control negada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no esterilizar al perro este mes", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `control negada con envolvente de plan`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no voy a esterilizar al gato", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `control pasado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "ya esterilicé al perro ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `control nominalizacion`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la esterilización del perro", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `control destinatario humano`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "esterilizar a ella", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `envolvente gobierna TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame esterilizar al perro el lunes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
