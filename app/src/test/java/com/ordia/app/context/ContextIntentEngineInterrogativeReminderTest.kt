package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Envolvente interrogativa de recordatorio "¿te acuerdas de <infinitivo>?"
 * en la captura contextual (c.687).
 *
 * La sonda de descubrimiento (tools/probe/InterrogativeReminderProbe.kt)
 * constató olvido silencioso P1 en [ContextIntentEngine.analyze]: "te
 * acuerdas de pagar la renta?" se DESCARTABA (NULL), "te acuerdas de llamar
 * al banco?" robaba el kind a CALL y "¿te acuerdas de recoger el paquete?"
 * capturaba ERRAND con título sucio (la envolvente interrogativa entera se
 * filtraba al título). En español, preguntarse "¿te acuerdas de X?" es la
 * forma cotidiana de auto-recordatorio: equivale a "acuérdate de X" (piso
 * c.619 de REMINDER ya cubre "acordarme de"). Una forma por ciclo, doctrina
 * anti-overreach de c.681.
 *
 * Anti-overreach: el verbo que sigue a "te acuerdas de" debe ser INFINITIVO
 * ("te acuerdas de pagar…", "te acuerdas de llamar…"). Así la evocación del
 * pasado ("te acuerdas de cuando íbamos al parque", "¿te acuerdas de la
 * película que vimos?", "¿te acuerdas de mi cumpleaños?") NO se captura: es
 * conversación, no intención organizativa. La negación ("no te acuerdas de
 * nada") queda bloqueada por el lookbehind `(?<!no )`.
 *
 * Las formas con kind subordinado ceden el kind al envolvente (lección
 * c.653): "te acuerdas de llamar al banco?" es un RECORDATORIO de llamar,
 * no una llamada autónoma (vía WRAPPER_PATTERN + imperativeIsWrapped).
 */
class ContextIntentEngineInterrogativeReminderTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    // --- Captura: "te acuerdas de <infinitivo>?" debe ser REMINDER ---

    @Test
    fun teAcuerdasDePagarLaRentaIsCapturedAsReminder() {
        val intent = analyze("te acuerdas de pagar la renta?")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertEquals("Pagar la renta", intent.title)
    }

    @Test
    fun invertedQuestionMarkVariantIsCapturedAsReminder() {
        val intent = analyze("¿te acuerdas de comprar leche?")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertEquals("Comprar leche", intent.title)
    }

    @Test
    fun interrogativeWithTemporalAnchorResolvesDueAtAndCleansTitle() {
        val intent = analyze("te acuerdas de renovar el seguro mañana?")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertNotNull(intent.dueAt)
        assertEquals("Renovar el seguro", intent.title)
    }

    // --- Robo de kind: el envolvente gobierna sobre el verbo subordinado ---

    @Test
    fun subordinatedCallYieldsKindToReminderWrapper() {
        val intent = analyze("te acuerdas de llamar al banco?")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertEquals("Llamar al banco", intent.title)
    }

    @Test
    fun subordinatedErrandYieldsKindToReminderWrapper() {
        val intent = analyze("¿te acuerdas de recoger el paquete?")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertEquals("Recoger el paquete", intent.title)
    }

    // --- Anti-overreach: evocación del pasado y negación NO capturan ---

    @Test
    fun pastRecallWithCuandoIsNotCaptured() {
        assertNull(analyze("te acuerdas de cuando íbamos al parque"))
    }

    @Test
    fun pastRecallOfMovieIsNotCaptured() {
        assertNull(analyze("te acuerdas de la película que vimos?"))
    }

    @Test
    fun nounComplementIsNotCaptured() {
        assertNull(analyze("¿te acuerdas de mi cumpleaños?"))
    }

    @Test
    fun negatedFormIsNotCaptured() {
        assertNull(analyze("no te acuerdas de nada"))
        assertNull(analyze("no te acuerdas de pagar la renta"))
    }

    // --- Regresión: wrappers existentes no cambian ---

    @Test
    fun imperativeRecuerdaStillCapturesAsTask() {
        val intent = analyze("recuerda comprar leche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Comprar leche", intent.title)
    }

    @Test
    fun imperativeAvisameStillCapturesAsReminder() {
        val intent = analyze("avísame llamar al banco")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
    }
}
