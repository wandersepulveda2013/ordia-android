package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1011: forma DATIVA del cuidado veterinario de la mascota —
 * «ponerle la vacuna al perro» (dicho-como-se-habla: la vacunación
 * en dativo, de las citas de veterinario más cotidianas). Candidata
 * (a) documentada ABIERTA en la fila de la clase DÉCIMA mascotas
 * c.1007 (acotado deliberado, UNA por ciclo: (a) dativo «ponerle la
 * vacuna», (b) paráfrasis «la vacuna del perro tiene que estar
 * al día», (c) pasivo «al gato hay que cepillarlo», (d) temporal
 * «llevar al gato al veterinario la próxima semana», (e) conjunctivo
 * «bañar y cepillar al gato»). Esta unidad resuelve SOLO (a).
 * NULL PRE medido con sonda efímera `/tmp/probe1010/Probe.kt` sobre
 * el HEAD del run (motor real vía `tools/run_probe.sh`): las 6
 * formas dativas NULL (olvido silencioso P1 — la transitiva
 * «vacunar al perro» sí captura desde c.757 pero el dativo con
 * objeto sustantivo «vacuna» no casa por ninguna vía), mientras los
 * guards NULL correctos (negación, pasado «le puse…», destinatario
 * humano «al niño», sin objeto, hedge, sintagma nominal) y las
 * regresiones intactas HIT («vacunar al perro» c.757, «llevar al
 * perro al veterinario» c.747+c.755, «sacar al perro» c.740).
 * Fix mínimo (lockstep TRES puntos, lección c.616/c.751; precedente
 * dativo hermano c.1006 «cortarle el pelo al niño»): NUEVO piso
 * [HOUSEHOLD_VACCINE_DATIVE_FLOOR] acotado al objeto mascota
 * (`perr[oa]s?|gat[oa]s?`, familia c.757; «vacuna» suelta es
 * bivalente → el destinatario humano queda FUERA) + la MISMA extensión
 * en la cláusula de negación dedicada de [imperativeIsNegated]
 * (cinturón y tirantes, precedente c.829/c.842) + plantilla de título
 * dedicada en [extractTitle] (pronombre dativo conservado, doctrina
 * c.653; objeto = ANCLA, no se despoja; [sanitizeTitle] depura la
 * cola temporal). CERO keywords nuevas en `ContextIntent.kt`: el piso
 * basta (la keyword-mascota sola queda bajo el umbral — medido PRE)
 * y el objeto «vacuna» NO se añade como keyword para no capturar el
 * sintagma nominal «la vacuna del perro» (pin NULL).
 * Anti-overreach intacto: el destinatario humano («ponerle la vacuna
 * al niño») y la forma sin objeto («ponerle la vacuna») NULL (pin);
 * la negación inmediata la bloquean el lookbehind del piso Y la
 * cláusula; el guard c.1009 descarta «no voy a ponerle…» antes del
 * piso (pin); el pasado («le puse la vacuna…») no casa el infinitivo
 * literal; el hedge «quizá…» sigue NULL. Acotado deliberado (UNA
 * forma por ciclo): paráfrasis (b)–(e) quedan FUERA — candidatas
 * documentadas c.1007.
 */
class ContextIntentEnginePonerleVacunaFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas dativas (piso) ----

    @Test
    fun `captura dativo le con destinatario y fecha`() {
        val i = analyze("ponerle la vacuna al perro mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Ponerle la vacuna al perro", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura dativo le con posesivo y fecha`() {
        val i = analyze("ponerle la vacuna a mi gato el sábado")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Ponerle la vacuna a mi gato", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura dativo plural les`() {
        val i = analyze("ponerles la vacuna a los perros")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Ponerles la vacuna a los perros", i.title)
    }

    @Test
    fun `captura dativo le con plural vacunas`() {
        val i = analyze("ponerle las vacunas al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Ponerle las vacunas al perro", i.title)
    }

    @Test
    fun `captura tras acuse de escucha`() {
        val i = analyze("vale, ponerle la vacuna al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Ponerle la vacuna al perro", i.title)
    }

    @Test
    fun `captura sin fecha`() {
        val i = analyze("ponerle la vacuna al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Ponerle la vacuna al perro", i.title)
    }

    // ---- Guards (NULL correctos) ----

    @Test
    fun `negacion inmediata bloqueada`() {
        assertNull(analyze("no ponerle la vacuna al perro"))
    }

    @Test
    fun `negacion de envolvente de plan bloqueada por guard c1009`() {
        assertNull(analyze("no voy a ponerle la vacuna al perro"))
    }

    @Test
    fun `pasado no captura`() {
        assertNull(analyze("le puse la vacuna al perro ayer"))
    }

    @Test
    fun `destinatario humano fuera`() {
        assertNull(analyze("ponerle la vacuna al niño"))
    }

    @Test
    fun `dativo sin objeto ancla fuera`() {
        assertNull(analyze("ponerle la vacuna"))
    }

    @Test
    fun `hedge no captura`() {
        assertNull(analyze("quizá ponerle la vacuna al perro"))
    }

    @Test
    fun `sintagma nominal no captura`() {
        assertNull(analyze("la vacuna del perro"))
    }

    // ---- Regresiones (HIT intactas) ----

    @Test
    fun `regresion piso vacunar c757`() {
        val i = analyze("vacunar al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Vacunar al perro", i.title)
    }

    @Test
    fun `regresion piso vacunar gato con fecha c757`() {
        val i = analyze("vacunar al gato mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Vacunar al gato", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `regresion piso veterinario c747 c755`() {
        val i = analyze("llevar al perro al veterinario")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Llevar al perro al veterinario", i.title)
    }

    @Test
    fun `regresion piso sacar al perro c740`() {
        val i = analyze("sacar al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Sacar al perro", i.title)
    }
}
