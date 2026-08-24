package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1015: forma DATIVA de la higiene de la mascota —
 * «cortarle las uñas al gato» (dicho-como-se-habla: las uñas en
 * dativo, de los cuidados de mascota más cotidianos). Candidata
 * (c) documentada ABIERTA en la fila de la clase DÉCIMA mascotas
 * c.1007 (acotado deliberado, UNA por ciclo: (a) dativo «ponerle la
 * vacuna» RESUELTA c.1011, (b) dativo «darle la pastilla» RESUELTA
 * c.1012, (c) dativo «cortarle las uñas» [ciclo RENUMERADO c.1013->c.1015 por colisión cycle-ID convergente con el hermano — su c.1013 lateral «cabello» + su c.1014 «una vacuna» fijaron primero], (d) temporal «llevar al
 * gato al veterinario la próxima semana», (e) conjunctivo «bañar y
 * cepillar al gato»). Esta unidad resuelve SOLO (c).
 * NULL PRE medido con sonda efímera `/tmp/probe1015/Probe.kt` sobre
 * el HEAD del run (motor real vía `tools/run_probe.sh`, post-UNION
 * c.1012): las 6 formas dativas NULL (olvido silencioso P1 — la
 * hermana humana «cortarle el pelo (al niño)» sí captura ERRAND
 * desde c.842/c.1006 pero el dativo de higiene de la mascota con
 * objeto «uñas» no casa por ninguna vía: «cortar» no es keyword
 * (bivalente, ContextIntent c.731) y «uñas» no es keyword suelta),
 * mientras los guards NULL correctos (negación, «no voy a…» c.1009,
 * pasado «le corté…», destinatario humano «al niño», sin objeto
 * ancla, hedge, sintagma nominal) y las regresiones intactas HIT
 * («cortarle el pelo al niño» c.1006, «cortarme el pelo» c.842,
 * «darle la pastilla al perro» c.1012, «ponerle la vacuna al
 * perro» c.1011).
 * Fix mínimo (lockstep TRES puntos, lección c.616/c.751; hermano
 * directo de los pisos c.1011/c.1012): NUEVO piso
 * [HOUSEHOLD_NAIL_DATIVE_FLOOR] acotado al objeto `uñas?` +
 * destinatario mascota (`perr[oa]s?|gat[oa]s?`, familia c.757;
 * «cortar»/«uñas» sueltos son bivalentes, así el destinatario
 * humano queda FUERA) + la MISMA extensión en la cláusula de
 * negación dedicada de [imperativeIsNegated] (cinturón y tirantes,
 * precedente c.829/c.842/c.1011/c.1012) + plantilla de título
 * dedicada en [extractTitle] (pronombre dativo conservado, doctrina
 * c.653; objeto = ANCLA, no se despoja; [sanitizeTitle] depura la
 * cola temporal). CERO keywords nuevas en `ContextIntent.kt`: el
 * piso basta (la keyword-mascota sola queda bajo el umbral —
 * medido PRE) y el objeto «uñas» NO se añade como keyword para no
 * capturar el sintagma nominal «las uñas del gato» (pin NULL).
 * Anti-overreach intacto: el destinatario humano («cortarle las
 * uñas al niño») y la forma sin ancla («cortarle las uñas») NULL
 * (pin); la negación inmediata la bloquean el lookbehind del piso
 * Y la cláusula; el guard c.1009 descarta «no voy a cortarle…»
 * antes del piso (pin); el pasado («le corté las uñas…») no casa
 * el infinitivo literal; el hedge «quizá…» sigue NULL; la forma
 * sin dativo con genitivo mascota «cortar las uñas del gato» quedó
 * RESUELTA en c.1024 (extensión in-situ de este piso: dativo
 * opcional + conector genitivo — re-pin legítimo más abajo); la
 * forma sin destinatario («cortar las uñas» — uñas propias) sigue
 * FUERA.
 * Acotado deliberado (UNA forma por ciclo): (d)–(e) quedan FUERA —
 * candidatas documentadas c.1007.
 */
class ContextIntentEngineCortarleUnasFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas dativas (piso) ----

    @Test
    fun `captura dativo le con destinatario y fecha`() {
        val i = analyze("cortarle las uñas al gato mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Cortarle las uñas al gato", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura dativo le con perro y posesivo`() {
        val i = analyze("cortarle las uñas a mi perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Cortarle las uñas a mi perro", i.title)
    }

    @Test
    fun `captura dativo plural les con fecha`() {
        val i = analyze("cortarles las uñas a los perros el sábado")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Cortarles las uñas a los perros", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura tras acuse de escucha`() {
        val i = analyze("vale, cortarle las uñas al gato")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Cortarle las uñas al gato", i.title)
    }

    @Test
    fun `captura sin fecha`() {
        val i = analyze("cortarle las uñas al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Cortarle las uñas al perro", i.title)
    }

    // ---- Guards (NULL correctos) ----

    @Test
    fun `negacion inmediata bloqueada`() {
        assertNull(analyze("no cortarle las uñas al gato"))
    }

    @Test
    fun `negacion de envolvente de plan bloqueada por guard c1009`() {
        assertNull(analyze("no voy a cortarle las uñas al gato"))
    }

    @Test
    fun `pasado no captura`() {
        assertNull(analyze("le corté las uñas al gato ayer"))
    }

    @Test
    fun `destinatario humano fuera`() {
        assertNull(analyze("cortarle las uñas al niño"))
    }

    @Test
    fun `dativo sin objeto ancla fuera`() {
        assertNull(analyze("cortarle las uñas"))
    }

    @Test
    fun `hedge no captura`() {
        assertNull(analyze("quizá cortarle las uñas al gato"))
    }

    @Test
    fun `sintagma nominal no captura`() {
        assertNull(analyze("las uñas del gato"))
    }

    @Test
    fun `forma sin dativo con genitivo captura c1024`() {
        // Re-pin c.1024 (legitimo, MAS estricto): esta lateral quedo
        // RESUELTA — el piso se extendio (dativo opcional + conector
        // genitivo `del|de (art.)`). Conducta byte-identica medida POST:
        // HOUSEHOLD, titulo limpio, sin cola temporal -> dueAt null.
        val i = analyze("cortar las uñas del gato")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Cortar las uñas del gato", i.title)
        assertNull(i.dueAt)
    }

    // ---- Regresiones (HIT intactas) ----

    @Test
    fun `regresion piso dativo pelo c1006`() {
        val i = analyze("cortarle el pelo al niño")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarle el pelo al niño", i.title)
    }

    @Test
    fun `regresion piso cortarme el pelo c842`() {
        val i = analyze("cortarme el pelo mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarme el pelo", i.title)
    }

    @Test
    fun `regresion piso dativo pastilla c1012`() {
        val i = analyze("darle la pastilla al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Darle la pastilla al perro", i.title)
    }

    @Test
    fun `regresion piso dativo vacuna c1011`() {
        val i = analyze("ponerle la vacuna al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Ponerle la vacuna al perro", i.title)
    }
}
