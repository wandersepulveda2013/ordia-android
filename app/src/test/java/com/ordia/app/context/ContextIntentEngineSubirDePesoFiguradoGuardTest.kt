package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1038: guard anti-figurado «subir de peso» — candidata (d) de la clase
 * UNDÉCIMA (sonda persistida `tools/probe/EleventhClassDigitalProbe.kt`
 * c.1026, control G7): «subir de peso este verano» capturaba como TASK
 * 0.45 — FALSO POSITIVO medido (el piso abierto «subir <objeto>» c.724
 * casa el figurado corporal; «subir de peso» es ganar peso, una
 * condición, no un encargo con objeto transferible). Mismo patrón que el
 * G1 de la DÉCIMA que originó la candidata S c.1009. Ruido P2 de
 * precisión: una condición corporal persistida como tarea ensucia la
 * lista y What Now.
 *
 * Fix mínimo UN punto (hermano de los lookaheads anti-figurado del piso
 * dativo c.854 «(?!la contraria/la delantera/ventaja)» y del piso «traer»
 * c.900): lookahead `(?!de\s+peso\b)` en el piso c.724. Acotado al
 * figurado MEDIDO (una forma por ciclo, doctrina anti-overreach): la
 * lateral «subir de nivel» (figurada distinta, no medida) queda FUERA y
 * sigue capturando — pin byte-idéntico en esta batería. La plantilla de
 * título c.724 no necesita guard propio: sólo se evalúa cuando el piso ya
 * clasificó (lección c.616, punto único de verdad). Negación/pasado/
 * sustantivo ya eran NULL antes del fix (guards verdes desde RED).
 */
class ContextIntentEngineSubirDePesoFiguradoGuardTest {

    @Test
    fun `figurado subir de peso este verano es NULL`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "subir de peso este verano", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `figurado suelto al inicio es NULL`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "subir de peso", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `figurado con prefijo temporal es NULL`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana subir de peso", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `figurado con acuse es NULL`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, subir de peso", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `negacion no subir de peso sigue NULL`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no subir de peso", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado subi de peso sigue NULL`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "subí de peso", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `sustantivo subida de peso sigue NULL`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "subida de peso", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `regresion subir las fotos manana captura TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "subir las fotos mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Subir las fotos", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `regresion subir el archivo esta noche captura TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "subir el archivo esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Subir el archivo", intent.title)
    }

    @Test
    fun `regresion acuse subir el documento captura TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, subir el documento", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Subir el documento", intent.title)
    }

    @Test
    fun `pin FUERA lateral subir de nivel sigue capturando`() {
        // Anti-overreach: el guard se acota al figurado MEDIDO «de peso»;
        // «subir de nivel» (figurada distinta, no medida por la sonda)
        // conserva el comportamiento del piso c.724 byte-idéntico.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "subir de nivel este mes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
