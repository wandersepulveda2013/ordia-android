package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1123: extensión del objeto del piso reflexivo «hacerse» (familia
 * c.862/c.876/c.881/c.882/c.889/c.1115) al sustantivo «ecograf[ií]a(s)» —
 * candidata (g) de la clase DECIMOTERCERA, medida NULL y registrada en la
 * sonda persistida c.1102 (`tools/probe/ThirteenthClassHealthProbe.kt`,
 * caso C13). «Hacerse la ecografía» es LA forma cotidiana de referirse a la
 * ecografía (embarazo, diagnóstico por imagen) en el habla común; su olvido
 * tiene coste real (P1, evitar olvidos: desplazamiento al centro,
 * preparación —vejiga llena en obstétricas—, cita perdida). Medición PRE
 * con sonda efímera `/tmp/probe1123/Probe.kt` (motor real vía
 * tools/run_probe.sh) sobre HEAD `267648a9` ff: 8/8 candidatas NULL
 * (miércoles/mañana/una/hacerte/hacernos/acuse vale/del bebé/sin tilde),
 * 7/7 pines NULL (no…/quizá…/«me hice…ayer»/forma desnuda «hacer la
 * ecografía»/declarativa «la ecografía del bebé es el jueves»/sustantivo
 * aislado/estado «mi ecografía salió bien»), envolvente «recuérdame
 * hacerme la ecografía mañana» → TASK 0.54 vía candado c.613, 6/6
 * regresiones HIT (análisis c.862, analíticas c.1115, prueba de sonido
 * c.889, tatuaje c.881, médico APPOINTMENT, llamar). Decisión de dominio:
 * ERRAND heredada (doctrina «la diligencia gobierna» c.842/c.862: misma
 * gestión con desplazamiento que «análisis»/«analíticas»). Lockstep en
 * DOS puntos (lección c.616): piso + plantilla de título. CERO cambios en
 * [ContextIntent.kt]: «hacer» es substring de «hacerme/hacerse» (hermana
 * de c.862/c.1115). Sin cláusula dedicada en [imperativeIsNegated]
 * (aritmética c.859/c.860/c.862). Acotado deliberado (una forma por
 * ciclo): la forma desnuda «hacer la ecografía» sigue FUERA por doctrina
 * c.862 (enclítico exigido) y «ecógrafo»/«ecografía 3D» quedan laterales.
 */
class ContextIntentEngineHacerseEcografiaFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura base con dia de semana`() {
        // Caso C13 exacto de la sonda persistida c.1102.
        val i = analyze("hacerse la ecografía el miércoles")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerse la ecografía", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura hacerme con manana`() {
        val i = analyze("hacerme la ecografía mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme la ecografía", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura indefinido una`() {
        val i = analyze("hacerme una ecografía el jueves")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme una ecografía", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura dativo te la semana que viene`() {
        val i = analyze("hacerte la ecografía la semana que viene")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerte la ecografía", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura dativo nos plural`() {
        val i = analyze("hacernos las ecografías el lunes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacernos las ecografías", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura acuse vale con hora`() {
        val i = analyze("vale, hacerme la ecografía mañana a las 10")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme la ecografía", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura con complemento conservado`() {
        // «del bebé» es contenido, no residuo temporal: se conserva.
        val i = analyze("hacerme la ecografía del bebé el jueves")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme la ecografía del bebé", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura sin tilde`() {
        val i = analyze("hacerme la ecografia el martes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme la ecografia", i.title)
        assertNotNull(i.dueAt)
    }

    // ---- Controles (siguen NULL) ----

    @Test
    fun `negada descartada`() {
        assertNull(analyze("no hacerme la ecografía mañana"))
    }

    @Test
    fun `duda descartada`() {
        assertNull(analyze("quizá hacerme la ecografía mañana"))
    }

    @Test
    fun `pasado me hice descartado`() {
        assertNull(analyze("me hice la ecografía ayer"))
    }

    @Test
    fun `forma desnuda descartada`() {
        // Sin enclítico reflexivo la forma queda fuera (doctrina c.862).
        assertNull(analyze("hacer la ecografía mañana"))
    }

    @Test
    fun `declarativa con verbo ser descartada`() {
        assertNull(analyze("la ecografía del bebé es el jueves"))
    }

    @Test
    fun `sustantivo aislado descartado`() {
        assertNull(analyze("la ecografía"))
    }

    @Test
    fun `estado pasado descartado`() {
        assertNull(analyze("mi ecografía salió bien"))
    }

    // ---- Pin envolvente (byte-equivalente, candado c.613) ----

    @Test
    fun `envolvente sigue task`() {
        val i = analyze("recuérdame hacerme la ecografía mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Hacerme la ecografía", i.title)
        assertNotNull(i.dueAt)
    }

    // ---- Regresiones ----

    @Test
    fun `regresion analisis c862`() {
        val i = analyze("hacerme un análisis de sangre el lunes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme un análisis de sangre", i.title)
    }

    @Test
    fun `regresion analiticas c1115`() {
        val i = analyze("hacerme las analíticas en ayunas la semana que viene")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme las analíticas en ayunas", i.title)
    }

    @Test
    fun `regresion prueba sonido c889`() {
        val i = analyze("hacerse la prueba de sonido el viernes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerse la prueba de sonido", i.title)
    }

    @Test
    fun `regresion tatuaje c881`() {
        val i = analyze("hacerse un tatuaje mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerse un tatuaje", i.title)
    }

    @Test
    fun `regresion medico appointment`() {
        val i = analyze("ir al médico mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.APPOINTMENT, i!!.kind)
    }
}
