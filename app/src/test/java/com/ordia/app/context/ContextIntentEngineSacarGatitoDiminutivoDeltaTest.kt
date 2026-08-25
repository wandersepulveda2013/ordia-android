package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1054 (delta de la lateral ABIERTA «sacar al gatito» — pin FUERA
 * c.1050/c.1052 de [ContextIntentEngineSacarGatoDeltaTest]): diminutivo
 * cotidiano del ancla mascota, hermano simétrico del pin «perrito» del
 * piso perro c.740. PRE medido con sonda efímera `/tmp/probe1053/Probe.kt`
 * (motor real vía `tools/run_probe.sh`): 6/6 candidatas puras NULL (gap
 * confirmado; la keyword «gato» ya existe en HOUSEHOLD desde c.744, así
 * el gate TRIGGER_WORDS pasa y el hueco es SOLO el ancla de objeto del
 * piso; `\b` final la descartaba como pin). Fix mínimo (lockstep TRES
 * puntos, lección c.616/c.751): el ancla de objeto del piso
 * [HOUSEHOLD_PET_FLOOR] pasa de `(?:perr[oa]|gat[oa])s?` a
 * `(?:perr[oa]|gatit[oa]|gat[oa])s?` en el piso, en la cláusula de
 * negación dedicada de [imperativeIsNegated] y en la plantilla de título
 * de [extractTitle]. CERO keywords nuevas. Anti-overreach intacto:
 * negación inmediata, envolvente c.1009, pasado, hedge — todos NULL.
 * Acotado (UNA por ciclo): el diminutivo perro «sacar al perrito» quedó
 * RESUELTO en c.1056 (re-pin legítimo); la vía pasear del diminutivo
 * («pasear al gatito» / «pasear al perrito») sigue FUERA (pins,
 * laterales documentadas).
 */
class ContextIntentEngineSacarGatitoDiminutivoDeltaTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas (6, RED medido PRE) ----

    @Test
    fun `captura sacar al gatito manana`() {
        val i = analyze("sacar al gatito mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Sacar al gatito", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura sacar al gatito con hora`() {
        val i = analyze("sacar al gatito a las 8")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Sacar al gatito", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura sacar a la gatita`() {
        val i = analyze("sacar a la gatita esta tarde")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Sacar a la gatita", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura sacar a mi gatito posesivo`() {
        val i = analyze("sacar a mi gatito esta noche")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Sacar a mi gatito", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura sacar al gatito de mi madre`() {
        val i = analyze("sacar al gatito de mi madre")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Sacar al gatito de mi madre", i.title)
    }

    @Test
    fun `captura con articulo directo variante c756`() {
        val i = analyze("sacar el gatito a las 7")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Sacar el gatito", i.title)
        assertNotNull(i.dueAt)
    }

    // ---- Guards (NULL correctos, verdes desde RED) ----

    @Test
    fun `negacion inmediata bloqueada`() {
        assertNull(analyze("no sacar al gatito hoy"))
    }

    @Test
    fun `negacion de envolvente de plan bloqueada por guard c1009`() {
        assertNull(analyze("no voy a sacar al gatito"))
    }

    @Test
    fun `pasado no captura`() {
        assertNull(analyze("saqué al gatito ayer"))
    }

    @Test
    fun `hedge subjuntivo no captura`() {
        assertNull(analyze("quizá saque al gatito mañana"))
    }

    // ---- Regresión (HIT intacta, verde desde RED) ----

    @Test
    fun `regresion sacar al gato c1052 intacta`() {
        val i = analyze("sacar al gato a las 8")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Sacar al gato", i.title)
    }

    // ---- Pin hermano (envolvente c.613 gobierna TASK, verde desde RED) ----

    @Test
    fun `tengo que sacar al gatito gobierna TASK envolvente c613`() {
        val i = analyze("tengo que sacar al gatito")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Sacar al gatito", i.title)
    }

    // ---- Pin FUERA byte-idéntico (laterales documentadas, UNA por ciclo) ----

    @Test
    fun `pasear al perrito fuera lateral documentada via pasear c1018`() {
        // c.1056 resolvió «sacar al perrito» (pin anterior); el nuevo FUERA
        // es la vía pasear del diminutivo perro (piso hermano c.1018 intacto).
        assertNull(analyze("pasear al perrito mañana"))
    }

    @Test
    fun `pasear al gatito fuera lateral documentada via pasear c1043`() {
        assertNull(analyze("pasear al gatito mañana"))
    }
}
