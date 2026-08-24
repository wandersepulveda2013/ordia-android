package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1017 (número provisional, confirmado al fetch pre-push final): forma
 * transitiva «desparasitar al perro/gato» (candidata (d) de la fila clase
 * DÉCIMA c.1007, sonda `TenthClassPetProbe.kt`, PRE 6/6 NULL — olvido
 * silencioso) — piso HOUSEHOLD acotado al objeto mascota
 * (`(?:perr[oa]s?|gat[oa]s?)`, familia [HOUSEHOLD_VACCINE_FLOOR] c.757:
 * la desparasitación es veterinaria, tan cotidiana como la vacuna).
 * «Desparasitar» es monosemántico de salud de la mascota (antiparasitario);
 * el destinatario humano queda FUERA (anti-overreach — se desparasita
 * perros y gatos). Lockstep 3 puntos: piso + `HOUSEHOLD_FLOORS` +
 * extractTitle (hermano estructural c.757). Determinista (regex), sin IA
 * fingida.
 * Kind: HOUSEHOLD (salud de la mascota = cuidado de la mascota en el
 * hogar, misma deliberación c.757/c.1011; TASK solo en envolvente c.613).
 */
class ContextIntentEngineDesparasitarMascotaFloorTest {

    @Test
    fun `captura desparasitar al perro plus fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "desparasitar al perro mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Desparasitar al perro", intent.title)
    }

    @Test
    fun `captura al gato con franja hora`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "desparasitar al gato esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Desparasitar al gato", intent.title)
    }

    @Test
    fun `captura posesivo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "desparasitar a mi gata el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Desparasitar a mi gata", intent.title)
    }

    @Test
    fun `captura plural perros`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "desparasitar a los perros hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Desparasitar a los perros", intent.title)
    }

    @Test
    fun `regresion vacunar intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vacunar al perro mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    @Test
    fun `control negada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no desparasitar al perro este mes", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `control negada con envolvente de plan`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no voy a desparasitar al perro", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `control duda`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá desparasitar al perro este mes", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `control pasado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "desparasité al gato ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `control nominalizacion`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la desparasitación del perro", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `control objeto no mascota`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "desparasitar a los niños este mes", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `envolvente gobierna TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame desparasitar al perro el lunes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
