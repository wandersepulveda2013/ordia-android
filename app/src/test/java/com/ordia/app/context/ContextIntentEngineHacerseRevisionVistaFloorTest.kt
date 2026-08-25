package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1125: candidata (e) del complemento c.1102 (clase DECIMOTERCERA
 * del BACKLOG, salud/autocuidado) — «hacerme la revisión de la vista
 * (este mes)» caía a NULL: olvido silencioso P1 (la revisión de la
 * vista —gafas, graduación— exige cita con profesional; perderla
 * tiene coste real). Medida PRE con sonda efímera
 * `/tmp/probe1125/Probe.kt` (motor real vía `tools/run_probe.sh`,
 * HEAD `583d6ac`): N1-N8 8/8 NULL, guards G1-G6 6/6 NULL, pines
 * P1-P4/P6/P7 HIT y P5 envolvente TASK 0.54 (candado c.613).
 * Fix en lockstep de DOS puntos (lección c.616; hermano EXACTO de
 * c.1044 «ponerme la vacuna» — mismo autocuidado de salud reflexivo):
 * (1) piso ACOTADO en [ContextIntentEngine.hasStrongTaskImperative]
 *     anclado al verbo reflexivo «hacer(me|te|se|nos)» + objeto
 *     `revisi[oó]n de la vista` (el verbo «hacer» es bivalente —
 *     la cama/la compra/el informe—, así el objeto es lo que acota;
 *     la grafía [oó] admite la forma sin tilde, precedente c.772);
 * (2) plantilla hermana en [ContextIntentEngine.extractTitle] (el
 *     verbo con su enclítico se preserva capitalizado, doctrina
 *     c.653; el residuo temporal de cola lo depura [sanitizeTitle]).
 * CERO keywords nuevas: «revisión» ya es keyword APPOINTMENT
 * (ContextIntent.kt l.310) y «hacer» es substring de «hacerme»
 * (doctrina c.862, medida c.1115) — CERO cambios en ContextIntent.kt.
 * Enclítico reflexivo EXIGIDO (hermandad con c.862/c.1115): la forma
 * desnuda «hacer la revisión de la vista» queda FUERA como lateral
 * (pin G4) — aunque aquí el objeto «de la vista» no es bivalente,
 * se conserva la disciplina conservadora de la familia (una forma
 * por ciclo).
 * Kind decidido: TASK (deliberación c.1044: autocuidado de salud
 * reflexivo — hermano de «ponerme la vacuna»; APPOINTMENT no tiene
 * pisos [bonus-kinds, c.653] y su región estaba marcada por c.1113;
 * ERRAND_BLOOD_TEST_FLOOR estaba marcada por c.1123).
 * Anti-overreach (alcance fijado por los pines de esta clase):
 * `(?<!no )` bloquea la negada directa; el pasado «me hice…», la
 * 3ª persona «mi madre se hace…», la duda «quizá hacerme…» (no casa
 * el ancla y HEDGE penaliza post-pisos, doctrina c.649) y el
 * sintagma nominal «la revisión de la vista es importante» (keyword
 * sola 0.12 < umbral) NO casan. La envolvente («recuérdame
 * hacerme…») sigue su propia vía TASK 0.54 (pin byte-idéntica P5).
 */
class ContextIntentEngineHacerseRevisionVistaFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- GAPs c.1125: «hacer(me|te|se|nos) (det)? revisión de la vista» captura como TASK 0.45 ----

    @Test
    fun `hacerme la revision de la vista este mes captura como tarea`() {
        val i = analyze("hacerme la revisión de la vista este mes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Hacerme la revisión de la vista este mes", i.title)
    }

    @Test
    fun `hacerse la revision de la vista manana captura como tarea`() {
        val i = analyze("hacerse la revisión de la vista mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Hacerse la revisión de la vista", i!!.title)
        assertEquals(true, i.dueAt != null)
    }

    @Test
    fun `hacerte la revision de la vista el jueves captura como tarea`() {
        val i = analyze("hacerte la revisión de la vista el jueves")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Hacerte la revisión de la vista", i!!.title)
        assertEquals(true, i.dueAt != null)
    }

    @Test
    fun `hacernos la revision de la vista la semana que viene captura`() {
        val i = analyze("hacernos la revisión de la vista la semana que viene")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Hacernos la revisión de la vista", i!!.title)
        assertEquals(true, i.dueAt != null)
    }

    @Test
    fun `hacerme una revision de la vista con indefinido captura`() {
        val i = analyze("hacerme una revisión de la vista mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Hacerme una revisión de la vista", i!!.title)
        assertEquals(true, i.dueAt != null)
    }

    @Test
    fun `acuse vale hacerme la revision de la vista captura`() {
        val i = analyze("vale, hacerme la revisión de la vista el martes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Hacerme la revisión de la vista", i!!.title)
        assertEquals(true, i.dueAt != null)
    }

    @Test
    fun `prefijo temporal manana hacerme la revision de la vista captura`() {
        val i = analyze("mañana hacerme la revisión de la vista")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Hacerme la revisión de la vista", i!!.title)
        assertEquals(true, i.dueAt != null)
    }

    @Test
    fun `hacerme la revision de la vista pelada captura sin fecha`() {
        val i = analyze("hacerme la revisión de la vista")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Hacerme la revisión de la vista", i!!.title)
    }

    // ---- Guards anti-overreach (NULL antes y después) ----

    @Test
    fun `negada directa no hacerme la revision no captura`() {
        assertNull(analyze("no hacerme la revisión de la vista mañana"))
    }

    @Test
    fun `duda quizas hacerme la revision no captura`() {
        assertNull(analyze("quizá hacerme la revisión de la vista"))
    }

    @Test
    fun `pasado me hice la revision no captura`() {
        assertNull(analyze("me hice la revisión de la vista ayer"))
    }

    @Test
    fun `desnuda hacer la revision de la vista queda fuera`() {
        // Lateral registrada: forma desnuda (sin enclítico reflexivo)
        // FUERA por hermandad conservadora con la doctrina c.862
        // (aunque el objeto «de la vista» no es bivalente; una forma
        // por ciclo).
        assertNull(analyze("hacer la revisión de la vista mañana"))
    }

    @Test
    fun `tercera persona mi madre se hace la revision no captura`() {
        assertNull(analyze("mi madre se hace la revisión de la vista mañana"))
    }

    @Test
    fun `sintagma nominal la revision de la vista no captura`() {
        assertNull(analyze("la revisión de la vista es importante"))
    }

    // ---- Pines byte-idénticos (regresiones y hermanos) ----

    @Test
    fun `pin analiticas c1115 sigue ERRAND`() {
        val i = analyze("hacerme las analíticas en ayunas la semana que viene")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Hacerme las analíticas en ayunas", i.title)
        assertEquals(true, i.dueAt != null)
    }

    @Test
    fun `pin vacuna c1044 sigue TASK`() {
        val i = analyze("ponerme la vacuna de la gripe")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Ponerme la vacuna de la gripe", i.title)
    }

    @Test
    fun `pin dieta c1111 sigue TASK`() {
        val i = analyze("empezar la dieta el lunes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Empezar la dieta", i!!.title)
        assertEquals(true, i.dueAt != null)
    }

    @Test
    fun `pin coche a revision sigue ERRAND`() {
        val i = analyze("llevar el coche a revisión el lunes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Llevar el coche a revisión", i.title)
        assertEquals(true, i.dueAt != null)
    }

    @Test
    fun `pin envolvente recuerdame hacerme la revision sigue TASK 054`() {
        // Vía propia del candado c.613 (envolvente de recordatorio):
        // el piso de este ciclo NO la toca (byte-idéntica).
        val i = analyze("recuérdame hacerme la revisión de la vista mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.54f, i.confidence)
        assertEquals("Hacerme la revisión de la vista", i.title)
        assertEquals(true, i.dueAt != null)
    }

    @Test
    fun `pin ir al medico sigue APPOINTMENT`() {
        val i = analyze("ir al médico mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.APPOINTMENT, i!!.kind)
        assertEquals("Ir al médico", i!!.title)
        assertEquals(true, i.dueAt != null)
    }

    @Test
    fun `pin sacar cita c1117 sigue TASK`() {
        val i = analyze("sacar cita para el médico mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Sacar cita para el médico", i!!.title)
        assertEquals(true, i.dueAt != null)
    }
}
