package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.847: piso MEETING «quedar con|para <persona/plan>» (plan social) —
 * candidata 1/6 de la sonda persistida c.845
 * `tools/probe/SeventhClassErrandProbe.kt` (SÉPTIMA clase de gestión
 * cotidiana; NULL PRE verificado por la propia sonda: las 4 formas
 * «quedar con Ana el viernes», «quedar con el dentista el lunes»,
 * «quedar para cenar el sábado» y «quedamos con Ana el viernes» daban
 * NULL — keyword «quedar con» 0.12 + bono específico 0.2 + fecha 0.1
 * = 0.42 < 0.45, asimetría de ruta hermana de c.616…c.842). Olvido
 * silencioso P1: «quedar con X» es EL plan social canónico en español.
 * Kind deliberado: MEETING (la keyword «quedar con» y el bono
 * específico «quedar|vernos|quedamos|encuentro (con|en|a las)» ya
 * enrutaban ahí; el piso cierra la forma DESNUDA). «quedar» es
 * polivalente (acuerdo «quedar pendiente», figurado «quedar bien»,
 * dativo «quédate con», pasado «quedé»), así el piso se ACOTA a
 * infinitivo «quedar» + presente pactado «quedamos» seguidos de
 * con/para + objeto (criterio c.684/c.731). El título PRESERVA el
 * verbo del usuario («Quedar con Ana», doctrina c.653 — un plan social
 * no es una reunión formal). Lockstep: keywords «quedamos con» /
 * «quedar para» en MEETING (lección c.751) + cláusula de negación
 * dedicada en [imperativeIsNegated] (cinturón y tirantes, precedente
 * c.829) + plantilla de título (lección c.616). Acotado deliberado
 * (una forma por ciclo): pasado «quedamos con X ayer» (el dueAt pasado
 * hereda el patrón del piso hermano «reunión con el equipo ayer» —
 * candidata documentada), dativo «quedarle», locativo «quedar en» y
 * envolvente de obligación «tengo que quedar…» quedan FUERA.
 */
class ContextIntentEngineQuedarFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura quedar con persona y fecha`() {
        val i = analyze("quedar con Ana el viernes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.MEETING, i!!.kind)
        assertEquals("Quedar con Ana", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura quedar con profesional y fecha`() {
        val i = analyze("quedar con el dentista el lunes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.MEETING, i!!.kind)
        assertEquals("Quedar con el dentista", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura quedar para plan y fecha`() {
        val i = analyze("quedar para cenar el sábado")
        assertNotNull(i)
        assertEquals(ContextIntentKind.MEETING, i!!.kind)
        assertEquals("Quedar para cenar", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura quedamos pactado con fecha`() {
        val i = analyze("quedamos con Ana el viernes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.MEETING, i!!.kind)
        assertEquals("Quedamos con Ana", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura sin fecha`() {
        val i = analyze("quedar con Ana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.MEETING, i!!.kind)
        assertEquals("Quedar con Ana", i.title)
    }

    @Test
    fun `captura con acuse vale`() {
        val i = analyze("vale, quedar con Ana mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.MEETING, i!!.kind)
        assertEquals("Quedar con Ana", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val i = analyze("hoy quedar con Ana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.MEETING, i!!.kind)
        assertEquals("Quedar con Ana", i.title)
        assertNotNull(i.dueAt)
    }

    // ---- Envolvente (ruta hermana intacta) ----

    @Test
    fun `envolvente recordame enruta TASK`() {
        val i = analyze("recuérdame quedar con Ana mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Quedar con Ana", i.title)
        assertNotNull(i.dueAt)
    }

    // ---- Guards: deben permanecer NULL ----

    @Test
    fun `negada no captura`() {
        assertNull(analyze("no quedar con Ana el viernes"))
    }

    @Test
    fun `duda quizas no captura`() {
        assertNull(analyze("quizá quedar con Ana"))
    }

    @Test
    fun `narrativa pasado no captura`() {
        assertNull(analyze("quedé con Ana ayer"))
    }

    @Test
    fun `verbo suelto no captura`() {
        assertNull(analyze("quedar"))
    }

    @Test
    fun `figurado quedar bien no captura`() {
        assertNull(analyze("quedar bien en la reunión"))
    }

    @Test
    fun `acuerdo quedar pendiente no captura`() {
        assertNull(analyze("quedar pendiente de la respuesta"))
    }

    @Test
    fun `dativo quedate no captura`() {
        assertNull(analyze("quédate con el cambio"))
    }

    @Test
    fun `locativo quedar en queda fuera`() {
        // Acotado deliberado (una forma por ciclo): candidata documentada.
        assertNull(analyze("quedamos en la playa mañana"))
    }

    // ---- Regresiones (pisos hermanos intactos) ----

    @Test
    fun `regresion reunion con el equipo sigue MEETING`() {
        val i = analyze("reunión con el equipo mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.MEETING, i!!.kind)
        assertEquals("Reunión: con el equipo", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `regresion pedir cita con el medico sigue APPOINTMENT`() {
        val i = analyze("pedir cita con el médico mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.APPOINTMENT, i!!.kind)
        assertNotNull(i.dueAt)
    }

    // ---- Lockstep keywords (lección c.751) ----

    @Test
    fun `keywords quedamos con y quedar para en trigger words`() {
        assertTrue(ContextIntentKind.TRIGGER_WORDS.contains("quedamos con"))
        assertTrue(ContextIntentKind.TRIGGER_WORDS.contains("quedar para"))
    }
}
