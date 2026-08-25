package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1043 (delta de la lateral ABIERTA «pasear al gato», registrada
 * en c.1018 como lateral documentada del piso [HOUSEHOLD_WALK_DOG_FLOOR]
 * — pin FUERA `gato fuera lateral documentada piso hermano perro-only
 * c740` en [ContextIntentEnginePasearPerroFloorTest]; en las
 * "próximas prioridades" desde c.1040): el paseo del gato (arnés y
 * correa, cada vez más cotidiano) es el MISMO quehacer de mascota
 * que el del perro; el piso c.1018 se acotó perro-only por la
 * doctrina UNA por ciclo. Medida PRE con sonda efímera
 * `/tmp/probe1043/PasearGatoPreProbe.kt` (motor real vía
 * `tools/run_probe.sh`, suite base UNIÓN OK 7667): 7/7 candidatas
 * NULL (gap confirmado — la keyword-mascota «gato»/«gata» YA existe
 * en HOUSEHOLD desde c.744 «alimentar al gato», así el gate
 * TRIGGER_WORDS pasa y el hueco es SOLO el ancla de objeto del
 * piso), 7/7 guards NULL correctos, 6/6 regresiones HIT intactas.
 * Fix mínimo (lockstep TRES puntos, lección c.616/c.751; mismo
 * patrón que la extensión de objeto del hermano c.1015): el ancla
 * de objeto del piso pasa de `perr[oa]s?` a `(?:perr[oa]|gat[oa])s?`
 * en el piso, en la cláusula de negación dedicada de
 * [imperativeIsNegated] y en la plantilla de título de
 * [extractTitle]. CERO keywords nuevas (gato/gata ya existen,
 * c.744). Anti-overreach intacto: destinatario humano («pasear al
 * bebé») NULL, forma sin mascota («salir a pasear») NULL, negación
 * inmediata bloqueada por lookbehind + cláusula, «no voy a…» por el
 * guard c.1009, pasado/hedge no casan el infinitivo literal,
 * sintagma nominal NULL. Acotado deliberado (UNA por ciclo): el
 * piso hermano «sacar al perro» c.740 sigue perro-only — «sacar al
 * gato» queda FUERA (lateral documentada, pin byte-idéntico abajo).
 */
class ContextIntentEnginePasearGatoDeltaTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas (7, RED medido PRE) ----

    @Test
    fun `captura pasear al gato manana`() {
        val i = analyze("pasear al gato mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Pasear al gato", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura pasear al gato con hora`() {
        val i = analyze("pasear al gato a las 8")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Pasear al gato", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura pasear a la gata`() {
        val i = analyze("pasear a la gata esta tarde")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Pasear a la gata", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura pasear a los gatos plural`() {
        val i = analyze("pasear a los gatos el sábado")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Pasear a los gatos", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura pasear a mi gato posesivo`() {
        val i = analyze("pasear a mi gato esta noche")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Pasear a mi gato", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val i = analyze("mañana pasear al gato")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Pasear al gato", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura con articulo directo variante c756`() {
        val i = analyze("pasear la gata a las 7")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Pasear la gata", i.title)
        assertNotNull(i.dueAt)
    }

    // ---- Guards (NULL correctos, verdes desde RED) ----

    @Test
    fun `negacion inmediata bloqueada`() {
        assertNull(analyze("no pasear al gato hoy"))
    }

    @Test
    fun `negacion de envolvente de plan bloqueada por guard c1009`() {
        assertNull(analyze("no voy a pasear al gato"))
    }

    @Test
    fun `pasado no captura`() {
        assertNull(analyze("paseé al gato ayer"))
    }

    @Test
    fun `hedge subjuntivo no captura`() {
        assertNull(analyze("quizá pasee al gato mañana"))
    }

    // ---- Regresión (HIT intacta, verde desde RED) ----

    @Test
    fun `regresion pasear al perro c1018 intacta`() {
        val i = analyze("pasear al perro a las 8")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Pasear al perro", i.title)
    }

    // ---- Pin FUERA byte-idéntico (lateral documentada, UNA por ciclo) ----

    @Test
    fun `sacar al gato fuera lateral documentada piso hermano perro-only c740`() {
        assertNull(analyze("sacar al gato mañana"))
    }
}
