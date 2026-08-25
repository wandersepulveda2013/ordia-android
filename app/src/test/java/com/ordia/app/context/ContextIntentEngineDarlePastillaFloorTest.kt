package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1012: forma DATIVA de la medicación de la mascota —
 * «darle la pastilla al perro» (dicho-como-se-habla: la pastilla
 * en dativo, de los cuidados de mascota más cotidianos). Candidata
 * (b) documentada ABIERTA en la fila de la clase DÉCIMA mascotas
 * c.1007 (acotado deliberado, UNA por ciclo: (a) dativo «ponerle la
 * vacuna» RESUELTA c.1011, (b) dativo «darle la pastilla», (c) pasivo
 * «al gato hay que cepillarlo», (d) temporal «llevar al gato al
 * veterinario la próxima semana», (e) conjunctivo «bañar y cepillar
 * al gato»). Esta unidad resuelve SOLO (b).
 * NULL PRE medido con sonda efímera `/tmp/probe1012/Probe2.kt` sobre
 * el HEAD del run (motor real vía `tools/run_probe.sh`, post-UNION
 * c.1011): las 6 formas dativas NULL (olvido silencioso P1 — la
 * hermana humana «tomar la medicación» sí captura TASK desde c.859
 * pero el dativo de mascota con objeto sustantivo «pastilla» no casa
 * por ninguna vía: «pastilla» no es keyword suelta, solo objeto del
 * piso TASK «tomar/tomarme»), mientras los guards NULL correctos
 * (negación, «no voy a…» c.1009, pasado «le di…», destinatario
 * humano «al niño», sin objeto, hedge, sintagma nominal) y las
 * regresiones intactas HIT («ponerle la vacuna al perro» c.1011,
 * «vacunar al perro» c.757, «tomar la medicación» c.859, «llevar al
 * perro al veterinario» c.747+c.755).
 * Fix mínimo (lockstep TRES puntos, lección c.616/c.751; hermano
 * directo del piso c.1011 «ponerle la vacuna»): NUEVO piso
 * [HOUSEHOLD_PILL_DATIVE_FLOOR] acotado al objeto mascota
 * (`perr[oa]s?|gat[oa]s?`, familia c.757; «pastilla» suelta es
 * bivalente — pastilla humana, objeto del piso TASK c.859 — así el
 * destinatario humano queda FUERA) + la MISMA extensión en la
 * cláusula de negación dedicada de [imperativeIsNegated] (cinturón
 * y tirantes, precedente c.829/c.842/c.1011) + plantilla de título
 * dedicada en [extractTitle] (pronombre dativo conservado, doctrina
 * c.653; objeto = ANCLA, no se despoja; [sanitizeTitle] depura la
 * cola temporal). CERO keywords nuevas en `ContextIntent.kt`: el
 * piso basta (la keyword-mascota sola queda bajo el umbral — medido
 * PRE) y el objeto «pastilla» NO se añade como keyword para no
 * capturar el sintagma nominal «la pastilla del perro» (pin NULL).
 * Anti-overreach intacto: el destinatario humano («darle la pastilla
 * al niño») y la forma sin objeto («darle la pastilla») NULL (pin);
 * la negación inmediata la bloquean el lookbehind del piso Y la
 * cláusula; el guard c.1009 descarta «no voy a darle…» antes del
 * piso (pin); el pasado («le di la pastilla…») no casa el infinitivo
 * literal; el hedge «quizá…» sigue NULL. El imperativo 2ª persona
 * «dale la pastilla al perro» quedó FUERA pineado aquí y fue
 * RESUELTO en c.1050 (re-pin legítimo — la alternancia del piso
 * admite «dale/dales»; la captura pasiva de la delegación familiar
 * es la vía real del motor). Acotado deliberado (UNA forma por
 * ciclo): (c)–(e) quedan FUERA — candidatas documentadas c.1007.
 */
class ContextIntentEngineDarlePastillaFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas dativas (piso) ----

    @Test
    fun `captura dativo le con destinatario y fecha`() {
        val i = analyze("darle la pastilla al perro mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Darle la pastilla al perro", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura dativo le con plural pastillas y posesivo`() {
        val i = analyze("darle las pastillas a mi gato")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Darle las pastillas a mi gato", i.title)
    }

    @Test
    fun `captura dativo plural les con fecha`() {
        val i = analyze("darles la pastilla a los perros el sábado")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Darles la pastilla a los perros", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura tras acuse de escucha`() {
        val i = analyze("vale, darle la pastilla al gato")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Darle la pastilla al gato", i.title)
    }

    @Test
    fun `captura sin fecha`() {
        val i = analyze("darle la pastilla al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Darle la pastilla al perro", i.title)
    }

    // ---- Guards (NULL correctos) ----

    @Test
    fun `negacion inmediata bloqueada`() {
        assertNull(analyze("no darle la pastilla al perro"))
    }

    @Test
    fun `negacion de envolvente de plan bloqueada por guard c1009`() {
        assertNull(analyze("no voy a darle la pastilla al perro"))
    }

    @Test
    fun `pasado no captura`() {
        assertNull(analyze("le di la pastilla al perro ayer"))
    }

    @Test
    fun `destinatario humano fuera`() {
        assertNull(analyze("darle la pastilla al niño"))
    }

    @Test
    fun `dativo sin objeto ancla fuera`() {
        assertNull(analyze("darle la pastilla"))
    }

    @Test
    fun `hedge no captura`() {
        assertNull(analyze("quizá darle la pastilla al perro"))
    }

    @Test
    fun `sintagma nominal no captura`() {
        assertNull(analyze("la pastilla del perro"))
    }

    @Test
    fun `imperativo segunda persona RESUELTO c1050`() {
        val i = analyze("dale la pastilla al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Dale la pastilla al perro", i.title)
    }

    // ---- Regresiones (HIT intactas) ----

    @Test
    fun `regresion piso dativo vacuna c1011`() {
        val i = analyze("ponerle la vacuna al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Ponerle la vacuna al perro", i.title)
    }

    @Test
    fun `regresion piso vacunar c757`() {
        val i = analyze("vacunar al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Vacunar al perro", i.title)
    }

    @Test
    fun `regresion piso tomar medicacion c859`() {
        val i = analyze("tomar la medicación")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Tomar la medicación", i.title)
    }

    @Test
    fun `regresion piso veterinario c747 c755`() {
        val i = analyze("llevar al perro al veterinario")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Llevar al perro al veterinario", i.title)
    }
}
