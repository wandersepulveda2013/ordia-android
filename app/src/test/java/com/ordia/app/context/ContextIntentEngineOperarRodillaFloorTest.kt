package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1131: candidata (m) del complemento c.1102 (clase DECIMOTERCERA
 * del BACKLOG, salud/autocuidado) — «operar la rodilla» caía a NULL:
 * olvido silencioso P1 (una operación programada es de los
 * compromisos de salud con mayor coste de olvido; «operar» no tenía
 * piso y «rodilla» no era keyword → sin palabra gatillo ni llegaba al
 * análisis, gate c.751). Medida PRE con sonda efímera
 * `/tmp/probe1131/Probe.kt` (motor real vía `tools/run_probe.sh`,
 * HEAD `3d9782d`): T1-T8 8/8 NULL, guards G1-G9 9/9 NULL correctos,
 * pines P1-P8 intactos (P6 «recuérdame operar la rodilla mañana» ya
 * capturaba vía la keyword «recuérdame» → TASK 0.45 título «Operar
 * la rodilla»; P8 «tengo que operar la rodilla» idem vía «tengo
 * que»).
 * Fix en lockstep de TRES puntos (lección c.713/c.751/c.765/c.1111):
 * (1) keyword-OBJETO «rodilla» en ContextIntent.kt TASK (sin ella la
 *     notificación sin palabra gatillo ni llega al análisis);
 * (2) piso ACOTADO en [ContextIntentEngine.hasStrongTaskImperative]
 *     anclado al verbo «operar» (en sus formas naturales: infinitivo
 *     desnudo, reflexivo con enclítico, dativo 3ª plural «me/nos
 *     operan» y perífrasis «me/nos van a operar») + objeto `rodilla`
 *     — el verbo es bivalente («operar la máquina», «operar en
 *     bolsa»), así el objeto es lo que acota; otros objetos
 *     corporales («cadera», «hombro»…) quedan FUERA como laterales,
 *     una forma por ciclo (doctrina anti-overreach);
 * (3) plantilla hermana en [ContextIntentEngine.extractTitle] (el
 *     verbo capturado con su prefijo/enclítico se preserva
 *     capitalizado, doctrina c.653; el residuo temporal de cola lo
 *     depura [sanitizeTitle]).
 * Anti-overreach (alcance fijado por los pines de esta clase):
 * `(?<!no )` bloquea la negada directa («no operar la rodilla…»); el
 * pasado («operé…», «me operaron…»), la 3ª persona («mi madre
 * opera…») y la duda («quizá operar…») NO casan por alternancia de
 * verbo cerrada + ancla; el sustantivo («la operación de rodilla») y
 * la keyword sola («la rodilla me duele») quedan a 0.12 < umbral.
 */
class ContextIntentEngineOperarRodillaFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- GAPs c.1131: «operar (…) la rodilla» captura como TASK 0.45 ----

    @Test
    fun `operar la rodilla manana captura como tarea`() {
        val i = analyze("operar la rodilla mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Operar la rodilla", i.title)
        assertEquals(true, i.dueAt != null)
    }

    @Test
    fun `operar la rodilla en enero captura sin dueAt`() {
        // C24 de la sonda persistida ThirteenthClassHealthProbeComplement:
        // los month-hints no anclan (observación c.1102-complemento) y la
        // cola se conserva en el título (doctrina c.653).
        val i = analyze("operar la rodilla en enero")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Operar la rodilla en enero", i.title)
        assertEquals(false, i.dueAt != null)
    }

    @Test
    fun `operar la rodilla con acuse captura`() {
        val i = analyze("vale, operar la rodilla el viernes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Operar la rodilla", i!!.title)
        assertEquals(true, i.dueAt != null)
    }

    @Test
    fun `operarme de la rodilla captura con enclitico`() {
        val i = analyze("operarme de la rodilla el lunes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Operarme de la rodilla", i!!.title)
        assertEquals(true, i.dueAt != null)
    }

    @Test
    fun `operarse de la rodilla captura reflexivo`() {
        val i = analyze("operarse de la rodilla la semana que viene")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Operarse de la rodilla", i!!.title)
        assertEquals(true, i.dueAt != null)
    }

    @Test
    fun `me operan de la rodilla captura dativo`() {
        val i = analyze("me operan de la rodilla el jueves")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Me operan de la rodilla", i!!.title)
        assertEquals(true, i.dueAt != null)
    }

    @Test
    fun `me van a operar de la rodilla captura perifrasis`() {
        val i = analyze("me van a operar de la rodilla en diciembre")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Me van a operar de la rodilla en diciembre", i!!.title)
        assertEquals(false, i.dueAt != null)
    }

    @Test
    fun `manana operar la rodilla captura con prefijo temporal`() {
        val i = analyze("mañana operar la rodilla")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Operar la rodilla", i!!.title)
        assertEquals(true, i.dueAt != null)
    }

    @Test
    fun `operar la rodilla en mayusculas captura`() {
        val i = analyze("OPERAR LA RODILLA MAÑANA")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals(true, i.dueAt != null)
    }

    // ---- Pines NULL: bivalencias y variantes FUERA de alcance ----

    @Test
    fun `operar la rodilla negada sigue null`() {
        assertNull(analyze("no operar la rodilla mañana"))
    }

    @Test
    fun `operar la rodilla con duda sigue null`() {
        assertNull(analyze("quizá operar la rodilla mañana"))
    }

    @Test
    fun `operar la rodilla con duda subjuntivo sigue null`() {
        assertNull(analyze("quizá opere la rodilla mañana"))
    }

    @Test
    fun `me operaron de la rodilla en pasado sigue null`() {
        assertNull(analyze("me operaron de la rodilla ayer"))
    }

    @Test
    fun `opere la rodilla en pasado sigue null`() {
        assertNull(analyze("operé la rodilla ayer"))
    }

    @Test
    fun `operar la rodilla en tercera persona sigue null`() {
        assertNull(analyze("mi madre opera la rodilla mañana"))
    }

    @Test
    fun `operar la maquina bivalente sigue null`() {
        // «operar» es bivalente (la máquina/en bolsa): el objeto
        // `rodilla` es lo que acota el piso.
        assertNull(analyze("operar la máquina nueva mañana"))
    }

    @Test
    fun `operar en bolsa bivalente sigue null`() {
        assertNull(analyze("operar en bolsa"))
    }

    @Test
    fun `operar la cadera sigue fuera como candidata lateral`() {
        // Lateral (m-bis) registrada: «cadera» ni es keyword ni objeto
        // del piso (una forma por ciclo, doctrina anti-overreach).
        assertNull(analyze("operar la cadera en febrero"))
    }

    @Test
    fun `operacion de rodilla nominal sigue null`() {
        // Sintagma nominal: la keyword «rodilla» sola suma 0.12 <
        // umbral (lockstep c.751).
        assertNull(analyze("la operación de rodilla es en enero"))
    }

    @Test
    fun `rodilla como sustantivo suelto sigue null`() {
        // La keyword-OBJETO «rodilla» sola suma 0.12 < umbral: el
        // estado no es compromiso.
        assertNull(analyze("la rodilla me duele"))
    }

    // ---- Pines HIT byte-idénticas: envolventes que YA capturaban ----

    @Test
    fun `recuerdame operar la rodilla conserva destino y sube a 0_54`() {
        // P6 medida PRE a TASK 0.45 «Operar la rodilla» vía la keyword
        // «recuérdame»; POST: kind/título/dueAt BYTE-IDÉNTICOS, sólo la
        // confianza sube 0.45 → 0.54 porque la keyword-OBJETO «rodilla»
        // nueva suma su bono aditivo — el MISMO 0.54 documentado para
        // las envolventes hermanas (c.788: «recuérdame hacerme la
        // prueba…» → TASK 0.54 vía candado c.613). Dirección positiva,
        // misma vía, cero robo de kind.
        val i = analyze("recuérdame operar la rodilla mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.54f, i.confidence)
        assertEquals("Operar la rodilla", i.title)
        assertEquals(true, i.dueAt != null)
    }

    @Test
    fun `tengo que operar la rodilla queda byte identica`() {
        // P8 medida PRE: la keyword «tengo que» ya la capturaba con el
        // MISMO destino (TASK 0.45, título «Operar la rodilla»).
        val i = analyze("tengo que operar la rodilla")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Operar la rodilla", i!!.title)
        assertEquals(false, i.dueAt != null)
    }

    // ---- Regresiones: formas hermanas INTACTAS ----

    @Test
    fun `ponerme la vacuna captura intacto`() {
        val i = analyze("ponerme la vacuna mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Ponerme la vacuna", i.title)
    }

    @Test
    fun `empezar la dieta captura intacto`() {
        val i = analyze("empezar la dieta el lunes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Empezar la dieta", i!!.title)
    }

    @Test
    fun `hacerme la revision de la vista captura intacto`() {
        val i = analyze("hacerme la revisión de la vista")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertEquals("Hacerme la revisión de la vista", i!!.title)
    }

    @Test
    fun `sacar cita para el medico captura intacto`() {
        val i = analyze("sacar cita para el médico mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Sacar cita para el médico", i!!.title)
    }

    @Test
    fun `cita con el medico sigue appointment intacto`() {
        val i = analyze("cita con el médico mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.APPOINTMENT, i!!.kind)
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
}
