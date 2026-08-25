package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1050 (delta de la lateral ABIERTA «sacar al gato», registrada
 * en c.1046 como lateral documentada del piso hermano [HOUSEHOLD_PET_FLOOR]
 * c.740 — pin FUERA `sacar al gato fuera lateral documentada piso hermano
 * perro-only c740` en [ContextIntentEnginePasearGatoDeltaTest]): el paseo
 * del gato (arnés y correa, cada vez más cotidiano) es el MISMO quehacer
 * de mascota que el del perro; el piso c.740 se acotó perro-only por la
 * doctrina UNA por ciclo (la vía «pasear» se resolvió en c.1046; ésta es
 * la vía «sacar», hermana simétrica). Medida PRE con sonda efímera
 * `/tmp/probe1044/SacarGatoPreProbe.kt` (motor real vía
 * `tools/run_probe.sh`, suite base UNIÓN OK 7715): 6/6 candidatas puras
 * NULL (gap confirmado — la keyword-mascota «gato»/«gata» YA existe en
 * HOUSEHOLD desde c.744 «alimentar al gato», así el gate TRIGGER_WORDS
 * pasa y el hueco es SOLO el ancla de objeto del piso; «tengo que sacar
 * al gato» ya captura TASK 0.45 vía keyword genérica con título correcto
 * — la envolvente c.613 gobierna, igual que su hermano), 7/7 guards NULL
 * correctos, 6/6 regresiones HIT intactas. Fix mínimo (lockstep TRES
 * puntos, lección c.616/c.751; mismo patrón que c.1046): el ancla de
 * objeto del piso pasa de `perr[oa]s?` a `(?:perr[oa]|gat[oa])s?` en el
 * piso, en la cláusula de negación dedicada de [imperativeIsNegated] y en
 * la plantilla de título de [extractTitle]. CERO keywords nuevas
 * (gato/gata ya existen, c.744). Anti-overreach intacto: destinatario
 * humano («sacar al bebé») NULL, objeto no mascota («sacar las
 * entradas») NULL, negación inmediata bloqueada por lookbehind +
 * cláusula, «no voy a…» por el guard c.1009, pasado/hedge no casan el
 * infinitivo literal, sintagma nominal NULL. Acotado deliberado (UNA por
 * ciclo): el diminutivo «sacar al gatito» sigue FUERA (`\b` final —
 * hermano simétrico del pin «perrito» c.740, lateral documentada).
 */
class ContextIntentEngineSacarGatoDeltaTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas (6, RED medido PRE) ----

    @Test
    fun `captura sacar al gato manana`() {
        val i = analyze("sacar al gato mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Sacar al gato", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura sacar al gato con hora`() {
        val i = analyze("sacar al gato a las 8")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Sacar al gato", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura sacar a la gata`() {
        val i = analyze("sacar a la gata esta tarde")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Sacar a la gata", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura sacar a los gatos plural`() {
        val i = analyze("sacar a los gatos el sábado")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Sacar a los gatos", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura sacar a mi gato posesivo`() {
        val i = analyze("sacar a mi gato esta noche")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Sacar a mi gato", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura con articulo directo variante c756`() {
        val i = analyze("sacar la gata a las 7")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Sacar la gata", i.title)
        assertNotNull(i.dueAt)
    }

    // ---- Guards (NULL correctos, verdes desde RED) ----

    @Test
    fun `negacion inmediata bloqueada`() {
        assertNull(analyze("no sacar al gato hoy"))
    }

    @Test
    fun `negacion de envolvente de plan bloqueada por guard c1009`() {
        assertNull(analyze("no voy a sacar al gato"))
    }

    @Test
    fun `pasado no captura`() {
        assertNull(analyze("saqué al gato ayer"))
    }

    @Test
    fun `hedge subjuntivo no captura`() {
        assertNull(analyze("quizá saque al gato mañana"))
    }

    // ---- Regresión (HIT intacta, verde desde RED) ----

    @Test
    fun `regresion sacar al perro c740 intacta`() {
        val i = analyze("sacar al perro a las 8")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Sacar al perro", i.title)
    }

    // ---- Pin hermano (envolvente c.613 gobierna TASK, verde desde RED) ----

    @Test
    fun `tengo que sacar al gato gobierna TASK envolvente c613`() {
        // Idéntico al hermano c.740: la envolvente de obligación descarta
        // el piso HOUSEHOLD vía imperativeIsWrapped; el piso TASK c.613
        // gobierna con el MISMO título acotado al objeto mascota.
        val i = analyze("tengo que sacar al gato")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Sacar al gato", i.title)
    }

    // ---- Pin FUERA byte-idéntico (lateral documentada, UNA por ciclo) ----

    @Test
    fun `sacar al gatito fuera lateral documentada diminutivo hermano perrito c740`() {
        assertNull(analyze("sacar al gatito mañana"))
    }
}
