package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.684 (regresión, ítem c.681 "una forma por ciclo"): la construcción
 * transportativa de mantenimiento "llevar/llevo <vehículo> a(l) taller|
 * mecánica|revisión" es una diligencia inequívoca pero caía a NULL en la
 * captura pasiva (sólo keywords sueltas, ~0.12–0.3, < MINIMUM_CONFIDENCE).
 *
 * Verificado empíricamente con sonda JVM de la fuente real PRE-fix:
 *  - "llevar el coche al taller" -> NULL (olvido silencioso, P1)
 *  - "el lunes llevo el coche a revisión" -> NULL
 *  - "llevar el coche a mecánica" -> NULL
 *  - "llevar las ruedas al taller mañana" -> NULL
 * Controles (ya correctos pre-fix, deben permanecer):
 *  - "no llevar el coche al taller" -> NULL (negación inmediata)
 *  - "recuérdame llevar el coche al taller" -> TASK
 *  - "avísame llevar el coche al taller" -> REMINDER
 *  - "quizá llevar el coche al taller" -> NULL (duda c.649)
 *  - "si puedo llevar el coche al taller" -> NULL (condicional c.650)
 *  - "llevar a María al cine" -> NULL (objeto persona, destino ocio)
 *  - "llevar paquetes a correos" -> NULL (congela comportamiento pre-c.684)
 *
 * Fix: tercer piso en ERRAND_FLOORS (lista cerrada de vehículos + lista
 * cerrada de destinos de mantenimiento, verbos "llevar|llevo"). La lista
 * compartida propaga a piso (hasStrongErrandImperative), guard de envolvente
 * (WRAPPABLE_PATTERNS -> imperativeIsWrapped) y lookbehind (?<!no ).
 */
class ContextIntentEngineErrandCarryTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.SHARED_TEXT, text, 1000L)
    )

    @Test
    fun carryCarToGarageIsCaptured() {
        val intent = analyze("llevar el coche al taller")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el coche al taller", intent.title)
    }

    @Test
    fun carryCarToMechanicIsCaptured() {
        val intent = analyze("llevar el coche a mecánica")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el coche a mecánica", intent.title)
    }

    @Test
    fun carryCarToRevisionIsCaptured() {
        val intent = analyze("llevar el coche a revisión")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el coche a revisión", intent.title)
    }

    @Test
    fun carryWheelsWithTemporalAnchorIsCaptured() {
        val intent = analyze("llevar las ruedas al taller mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    @Test
    fun carryPresentTenseWithDayAnchorIsCaptured() {
        val intent = analyze("el lunes llevo el coche a revisión")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    // ---- Controles anti-overreach ----

    @Test
    fun negatedCarryIsNotCaptured() {
        assertNull(analyze("no llevar el coche al taller"))
    }

    @Test
    fun carryInsideTaskWrapperIsTask() {
        val intent = analyze("recuérdame llevar el coche al taller")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun carryInsideReminderWrapperIsReminder() {
        val intent = analyze("avísame llevar el coche al taller")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
    }

    @Test
    fun doubtfulCarryIsNotCaptured() {
        assertNull(analyze("quizá llevar el coche al taller"))
    }

    @Test
    fun conditionalCarryIsNotCaptured() {
        assertNull(analyze("si puedo llevar el coche al taller"))
    }

    @Test
    fun carryPersonToLeisureIsNotCaptured() {
        assertNull(analyze("llevar a María al cine"))
    }

    @Test
    fun carryParcelsToMailStaysNotCaptured() {
        assertNull(analyze("llevar paquetes a correos"))
    }
}
