package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1056 (delta de la lateral ABIERTA «sacar al perrito» — pin FUERA
 * c.1054 de [ContextIntentEngineSacarGatitoDiminutivoDeltaTest], hermano
 * simétrico del pin «perrito» original del piso perro c.740): diminutivo
 * cotidiano del ancla mascota. PRE medido con sonda efímera
 * `/tmp/probe1055/Probe.kt` (motor real vía `tools/run_probe.sh`): 6/6
 * candidatas puras NULL (gap confirmado; la keyword «perro» ya existe en
 * HOUSEHOLD desde c.740, así el gate TRIGGER_WORDS pasa y el hueco es
 * SOLO el ancla de objeto del piso; `\b` final la descartaba como pin).
 * Fix mínimo (lockstep TRES puntos, lección c.616/c.751): el ancla de
 * objeto del piso [HOUSEHOLD_PET_FLOOR] pasa de
 * `(?:perr[oa]|gatit[oa]|gat[oa])s?` a
 * `(?:perrit[oa]|perr[oa]|gatit[oa]|gat[oa])s?` en el piso, en la
 * cláusula de negación dedicada de [imperativeIsNegated] y en la
 * plantilla de título de [extractTitle]. CERO keywords nuevas.
 * Anti-overreach intacto: negación inmediata, envolvente c.1009, pasado,
 * hedge — todos NULL. Acotado (UNA por ciclo): la vía «pasear al
 * perrito» quedó RESUELTA en c.1057 (re-pin legítimo a «salir a
 * pasear» bivalente).
 */
class ContextIntentEngineSacarPerritoDiminutivoDeltaTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas (6, RED medido PRE) ----

    @Test
    fun `captura sacar al perrito manana`() {
        val i = analyze("sacar al perrito mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Sacar al perrito", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura sacar al perrito con hora`() {
        val i = analyze("sacar al perrito a las 8")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Sacar al perrito", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura sacar a la perrita`() {
        val i = analyze("sacar a la perrita esta tarde")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Sacar a la perrita", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura sacar a mi perrito posesivo`() {
        val i = analyze("sacar a mi perrito esta noche")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Sacar a mi perrito", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura sacar al perrito de mi madre`() {
        val i = analyze("sacar al perrito de mi madre")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Sacar al perrito de mi madre", i.title)
    }

    @Test
    fun `captura con articulo directo variante c756`() {
        val i = analyze("sacar el perrito a las 7")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Sacar el perrito", i.title)
        assertNotNull(i.dueAt)
    }

    // ---- Guards (NULL correctos, verdes desde RED) ----

    @Test
    fun `negacion inmediata bloqueada`() {
        assertNull(analyze("no sacar al perrito hoy"))
    }

    @Test
    fun `negacion de envolvente de plan bloqueada por guard c1009`() {
        assertNull(analyze("no voy a sacar al perrito"))
    }

    @Test
    fun `pasado no captura`() {
        assertNull(analyze("saqué al perrito ayer"))
    }

    @Test
    fun `hedge subjuntivo no captura`() {
        assertNull(analyze("quizá saque al perrito mañana"))
    }

    // ---- Regresiones (HIT intactas, verdes desde RED) ----

    @Test
    fun `regresion sacar al perro c740 intacta`() {
        val i = analyze("sacar al perro a las 8")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Sacar al perro", i.title)
    }

    @Test
    fun `regresion sacar al gatito c1054 intacta`() {
        val i = analyze("sacar al gatito a las 8")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Sacar al gatito", i.title)
    }

    // ---- Pin hermano (envolvente c.613 gobierna TASK, verde desde RED) ----

    @Test
    fun `tengo que sacar al perrito gobierna TASK envolvente c613`() {
        val i = analyze("tengo que sacar al perrito")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Sacar al perrito", i.title)
    }

    // ---- Pin FUERA byte-idéntico (lateral documentada, UNA por ciclo) ----

    @Test
    fun `salir a pasear bivalente fuera pin estructural`() {
        // c.1057 resolvió la vía pasear diminutiva «pasear al perrito»
        // (pin anterior); el nuevo FUERA es «pasear» bivalente sin objeto
        // mascota (salir a pasear uno mismo — pin estructural histórico).
        assertNull(analyze("salir a pasear mañana"))
    }
}
