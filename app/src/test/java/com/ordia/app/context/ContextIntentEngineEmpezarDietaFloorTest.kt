package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1111: candidata (n) del complemento c.1102 (clase DECIMOTERCERA
 * del BACKLOG, salud/autocuidado) — «empezar (con )?(la )?dieta» caía
 * a NULL: olvido silencioso P1 (empezar una dieta es EL compromiso de
 * autocuidado con fecha de inicio; «empezar» no tenía piso y «dieta»
 * no era keyword). Medida PRE con sonda efímera
 * `/tmp/probe1111/Probe.kt` (motor real vía `tools/run_probe.sh`,
 * HEAD `a7fa35e`): N1-N8 8/8 NULL, pines P1-P7/P9/P10 NULL, regresiones
 * R1-R8 HIT (R8 «debería empezar la dieta mañana» ya capturaba vía la
 * envolvente condicional c.835 con el mismo destino TASK 0.45).
 * Fix en lockstep de TRES puntos (lección c.713/c.751/c.765):
 * (1) keyword-OBJETO «dieta» en ContextIntent.kt (sin ella la
 *     notificación sin palabra gatillo ni llega al análisis);
 * (2) piso ACOTADO en [ContextIntentEngine.hasStrongTaskImperative]
 *     anclado al verbo «empezar» + objeto `dieta` (el verbo es
 *     bivalente — el libro/la serie/la carrera—, así el objeto es lo
 *     que acota; «régimen» queda FUERA como candidata lateral propia,
 *     una forma por ciclo);
 * (3) plantilla hermana en [ContextIntentEngine.extractTitle] (el
 *     verbo gobierna el contenido y se preserva capitalizado; el
 *     residuo temporal de cola lo depura [sanitizeTitle]).
 * Anti-overreach (alcance fijado por los pines de esta clase):
 * `(?<!no )` bloquea la negada directa («no empezar la dieta…»); el
 * pasado «empecé…», la 3ª persona «mi madre empieza…», la duda
 * «quizá empiece…», el envolvente de plan «voy a empezar…» (lateral
 * registrada) y los objetos bivalentes («el libro», «la serie»,
 * «a estudiar» — que sigue en STUDY) NO casan; la keyword sola
 * («la dieta mediterránea es sana») queda a 0.12 < umbral.
 */
class ContextIntentEngineEmpezarDietaFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- GAPs c.1111: «empezar (con )?(la )?dieta» captura como TASK 0.45 ----

    @Test
    fun `empezar la dieta el lunes captura como tarea`() {
        val i = analyze("empezar la dieta el lunes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Empezar la dieta", i.title)
        assertEquals(true, i.dueAt != null)
    }

    @Test
    fun `empezar la dieta manana captura como tarea`() {
        val i = analyze("empezar la dieta mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Empezar la dieta", i.title)
        assertEquals(true, i.dueAt != null)
    }

    @Test
    fun `empezar con la dieta captura preservando con`() {
        val i = analyze("empezar con la dieta el lunes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Empezar con la dieta", i!!.title)
        assertEquals(true, i.dueAt != null)
    }

    @Test
    fun `empezar la dieta la semana que viene captura`() {
        val i = analyze("empezar la dieta la semana que viene")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Empezar la dieta", i!!.title)
        assertEquals(true, i.dueAt != null)
    }

    @Test
    fun `empezar la dieta sin fecha captura sin dueAt`() {
        val i = analyze("empezar la dieta")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Empezar la dieta", i.title)
        assertEquals(false, i.dueAt != null)
    }

    @Test
    fun `empezar la dieta con acuse captura`() {
        val i = analyze("vale, empezar la dieta el lunes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Empezar la dieta", i!!.title)
        assertEquals(true, i.dueAt != null)
    }

    @Test
    fun `empezar la dieta en mayusculas captura`() {
        val i = analyze("EMPEZAR LA DIETA EL LUNES")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals(true, i.dueAt != null)
    }

    @Test
    fun `empezar mi dieta captura con posesivo`() {
        val i = analyze("empezar mi dieta el lunes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Empezar mi dieta", i!!.title)
        assertEquals(true, i.dueAt != null)
    }

    // ---- Pines NULL: bivalencias y variantes FUERA de alcance ----

    @Test
    fun `empezar el libro sigue null`() {
        assertNull(analyze("empezar el libro el lunes"))
    }

    @Test
    fun `empezar la serie sigue null`() {
        assertNull(analyze("empezar la serie mañana"))
    }

    @Test
    fun `empezar la dieta negada sigue null`() {
        assertNull(analyze("no empezar la dieta el lunes"))
    }

    @Test
    fun `empezar la dieta en pasado sigue null`() {
        assertNull(analyze("empecé la dieta el lunes"))
    }

    @Test
    fun `empezar el regimen sigue fuera como candidata lateral`() {
        // Lateral (n-bis) registrada: «régimen» ni es keyword ni objeto del
        // piso (una forma por ciclo, doctrina anti-overreach).
        assertNull(analyze("empezar el régimen el lunes"))
    }

    @Test
    fun `empezar la dieta en tercera persona sigue null`() {
        assertNull(analyze("mi madre empieza la dieta el lunes"))
    }

    @Test
    fun `dieta como sustantivo suelto sigue null`() {
        // La keyword-OBJETO «dieta» sola suma 0.12 < umbral (lockstep
        // c.751): el sustantivo sin verbo no es compromiso.
        assertNull(analyze("la dieta mediterránea es sana"))
    }

    @Test
    fun `voy a empezar la dieta sigue fuera como lateral`() {
        // Lateral registrada: el envolvente «voy a» no está en el ancla
        // del piso (una forma por ciclo; el ancla exige inicio/acuse/
        // prefijo temporal). Candidata propia si se mide.
        assertNull(analyze("voy a empezar la dieta el lunes"))
    }

    @Test
    fun `empezar la dieta con duda sigue null`() {
        assertNull(analyze("quizá empiece la dieta el lunes"))
    }

    // ---- Pin HIT byte-idéntica: objeto bivalente con kind propio ----

    @Test
    fun `empezar a estudiar sigue en study intacto`() {
        val i = analyze("empezar a estudiar el lunes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.STUDY, i!!.kind)
        assertEquals(0.45f, i.confidence)
    }

    // ---- Regresiones: formas hermanas INTACTAS ----

    @Test
    fun `tomar la medicina captura intacto`() {
        val i = analyze("tomar la medicina a las 8")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Tomar la medicina", i!!.title)
    }

    @Test
    fun `inflar las ruedas de la bici captura intacto`() {
        val i = analyze("inflar las ruedas de la bici mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Inflar las ruedas de la bici", i!!.title)
    }

    @Test
    fun `ponerme la vacuna captura intacto`() {
        val i = analyze("ponerme la vacuna mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
    }

    @Test
    fun `llamar a mama captura intacto`() {
        val i = analyze("llamar a mamá esta noche")
        assertNotNull(i)
        assertEquals(ContextIntentKind.CALL, i!!.kind)
    }

    @Test
    fun `pagar la luz captura intacto`() {
        val i = analyze("pagar la luz el día 5")
        assertNotNull(i)
        assertEquals(ContextIntentKind.PAYMENT, i!!.kind)
    }

    @Test
    fun `ir al medico captura intacto`() {
        val i = analyze("ir al médico el lunes a las 5")
        assertNotNull(i)
        assertEquals(ContextIntentKind.APPOINTMENT, i!!.kind)
    }

    @Test
    fun `comprar leche captura intacto`() {
        val i = analyze("comprar leche esta tarde")
        assertNotNull(i)
        assertEquals(ContextIntentKind.SHOPPING, i!!.kind)
    }

    @Test
    fun `deberia empezar la dieta queda byte identica`() {
        // R8 medida PRE: la envolvente condicional c.835 ya la capturaba
        // con el MISMO destino (TASK 0.45, título «Empezar la dieta»);
        // el piso nuevo la alcanza por la misma vía de título — pin
        // byte-idéntica, cero cambio visible.
        val i = analyze("debería empezar la dieta mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Empezar la dieta", i!!.title)
    }
}
