package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Piso c.864 — «escanear el DNI»: sexto gap medido NULL en c.857 por
 * `tools/probe/EighthClassAdminProbe.kt` (octava clase: gestiones de la
 * vida adulta — gestión documental). Sin piso, «escanear el DNI mañana»
 * se DESCARTABA silenciosamente (escaneo documental con verbo monosemántico
 * sin keyword previa: 0.0). Consecuencia real: no digitalizar el DNI a
 * tiempo para el trámite que lo requiere (banco, notaría, ayuntamiento).
 *
 * El piso vive en [ContextIntentEngine.hasStrongTaskImperative] y exige:
 * ancla de inicio/acuse/prefijo temporal, guard anti-negación `(?<!no )`
 * y objeto ACOTADO con determinante opcional (el/la/los/las/mi/tu/su):
 * «dni» desde c.864; «contrato(s)», «notas» (escritas) y «código QR»
 * desde c.883 (laterales medidas NULL en su sonda PRE). El resto de
 * objetos desnudos sigue FUERA (doctrina anti-overreach: extensiones
 * acotadas, medidas NULL ciclo a ciclo).
 * Las formas compuestas («escanear el DNI y enviarlo al banco…»,
 * «escanear el DNI por las dos caras…») capturan con el piso: el match
 * arranca en el verbo y el título conserva el resto de la frase (son la
 * misma gestión, no un objeto bivalente).
 *
 * Kind decidido en este ciclo: TASK — el escaneo es una acción única
 * completable, no un desplazamiento (ERRAND; precedente hermano c.698
 * «renovar el DNI» — gestión documental TASK — ni c.863 «hacer la
 * declaración de la renta») y la envolvente «recuérdame escanear el
 * DNI esta tarde» ya rutea TASK vía «recuérdame» (c.613, así la forma
 * declarativa y la envolvente convergen en el mismo kind).
 *
 * Lockstep lección c.751 (verbo monosemántico, precedente c.752 «votar»):
 * keyword-VERBO «escanear» en [ContextIntentKind.TASK] — sin ella la
 * frase no alcanza el análisis en producción vía
 * [ContextIntent.TRIGGER_WORDS]. Subcadena inerte: «reescanear» contiene
 * «escanear» pero aislada puntúa 0.12 (< umbral 0.45) → NULL; el piso
 * anclado la excluye además por diseño (precedente «descargar»/«cargar»
 * c.853, «automóvil»/«móvil» c.851).
 *
 * Cobertura: las 6 formas de la candidata + 2 compuestas + 6 guards
 * (negación, duda, pasado, verbo aislado, sustantivo suelto, prefijo re-)
 * + regresiones hermanas (c.698, c.863, c.862, envolvente c.613).
 */
class ContextIntentEngineEscanearDniFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    @Test
    fun `escanear el DNI declarativo captura TASK con titulo limpio`() {
        val r1 = analyze("escanear el DNI esta tarde")
        assertNotNull("«escanear el DNI esta tarde» debe capturar (era NULL en c.857)", r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertEquals("Escanear el DNI", r1.title)
        assertNotNull("«esta tarde» debe anclar dueAt", r1.dueAt)

        val r2 = analyze("escanear el DNI mañana")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertEquals("Escanear el DNI", r2.title)
        assertNotNull("«mañana» debe anclar dueAt", r2.dueAt)

        val r3 = analyze("escanear mi DNI mañana")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.TASK, r3!!.kind)
        assertEquals("Escanear mi DNI", r3.title)

        // Prefijo de acuse y prefijo temporal se despojan del título
        // (lección c.616: el match arranca en el verbo).
        val r4 = analyze("vale, escanear el DNI esta tarde")
        assertNotNull(r4)
        assertEquals(ContextIntentKind.TASK, r4!!.kind)
        assertEquals("Escanear el DNI", r4.title)

        val r5 = analyze("mañana escanear el DNI")
        assertNotNull(r5)
        assertEquals(ContextIntentKind.TASK, r5!!.kind)
        assertEquals("Escanear el DNI", r5.title)

        // Forma desnuda sin fecha: captura igualmente (piso), dueAt nulo.
        val r6 = analyze("escanear el DNI")
        assertNotNull(r6)
        assertEquals(ContextIntentKind.TASK, r6!!.kind)
        assertEquals("Escanear el DNI", r6.title)
        assertNull("sin expresión temporal no debe anclar dueAt", r6.dueAt)
    }

    @Test
    fun `formas compuestas capturan conservando el resto de la frase`() {
        val r1 = analyze("escanear el DNI y enviarlo al banco mañana")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertEquals("Escanear el DNI y enviarlo al banco", r1.title)
        assertNotNull(r1.dueAt)

        val r2 = analyze("escanear el DNI por las dos caras esta tarde")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertEquals("Escanear el DNI por las dos caras", r2.title)
    }

    @Test
    fun `guards anti-overreach permanecen NULL`() {
        assertNull("negación", analyze("no escanear el DNI mañana"))
        assertNull("duda (hedge c.649)", analyze("quizá escanear el DNI mañana"))
        assertNull("narrativa pasado", analyze("escaneé el DNI ayer"))
        assertNull("verbo aislado", analyze("escanear"))
        assertNull("sustantivo suelto: keyword inerte 0.12 < umbral",
            analyze("el DNI está caducado"))
        assertNull("prefijo re-: el piso anclado lo excluye (lateral candidata)",
            analyze("reescanear el DNI mañana"))
    }

    @Test
    fun `extension c883 - contrato notas y codigo QR capturan TASK`() {
        // c.883: extensiones medidas NULL en la sonda PRE (/tmp/probe883/).
        // Contratos (gestión) y notas (escritas) y código QR convergen con
        // la envolvente c.613 en TASK.
        val r1 = analyze("escanear el contrato mañana")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertEquals("Escanear el contrato", r1.title)
        assertNotNull(r1.dueAt)

        val r2 = analyze("escanear las notas esta tarde")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertEquals("Escanear las notas", r2.title)

        val r3 = analyze("escanear el código QR mañana")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.TASK, r3!!.kind)
        assertEquals("Escanear el código QR", r3.title)

        val r4 = analyze("escanear el contrato y enviarlo mañana")
        assertNotNull(r4)
        assertEquals(ContextIntentKind.TASK, r4!!.kind)
        assertEquals("Escanear el contrato y enviarlo", r4.title)
        assertNotNull(r4.dueAt)

        assertNull("verbo distinto (lateral candidata)",
            analyze("fotocopiar el DNI mañana"))
    }

    @Test
    fun `regresiones hermanas de gestiones documentales y envolvente`() {
        val renovar = analyze("renovar el DNI la semana que viene") // c.698
        assertNotNull(renovar)
        assertEquals(ContextIntentKind.TASK, renovar!!.kind)

        val renta = analyze("hacer la declaración de la renta este mes") // c.863
        assertNotNull(renta)
        assertEquals(ContextIntentKind.TASK, renta!!.kind)

        val analisis = analyze("hacerme un análisis de sangre el lunes") // c.862
        assertNotNull(analisis)
        assertEquals(ContextIntentKind.ERRAND, analisis!!.kind)

        // La envolvente ya ruteaba TASK vía «recuérdame» (c.613); la forma
        // declarativa converge al mismo kind con el piso.
        val wrapper = analyze("recuérdame escanear el DNI esta tarde")
        assertNotNull(wrapper)
        assertEquals(ContextIntentKind.TASK, wrapper!!.kind)
        assertEquals("Escanear el DNI", wrapper.title)
    }

    @Test
    fun `lockstep keyword-VERBO escanear en TASK - leccion c751`() {
        assertTrue(ContextIntentKind.TRIGGER_WORDS.contains("escanear"))
    }
}
