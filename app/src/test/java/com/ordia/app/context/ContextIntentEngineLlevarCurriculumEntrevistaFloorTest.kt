package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.1174: descubrimiento (d-bis) de MI auditoría c.1165 (clase
 * DECIMOCTAVA, vida social — sonda persistida
 * `tools/probe/EighteenthClassSocialProbe.kt`, registrado en BACKLOG).
 * «llevar el currículum a la entrevista (mañana)» se DESCARTABA en
 * silencio: los pisos «llevar» están acotados (taller c.684, escolar
 * c.773/c.1170, médico c.776, estación c.1158, dispositivo c.1157) y el
 * objeto «el currículum» con destino «la entrevista» quedaba FUERA de
 * todos; la keyword «llevar» sola (0.12 + bono temporal) queda bajo
 * umbral. Olvido silencioso P1: la entrevista sin el currículum físico
 * es la hermana de acarreo del «echar el currículum» c.1148 y del
 * «preparar la entrevista» c.1155 del hermano.
 * Fix lockstep 2 puntos (lección c.616; CERO keywords nuevas — «llevar»
 * ya es keyword TASK histórica, gate c.751 satisfecho): piso NUEVO
 * acotado `ERRAND_INTERVIEW_RUN_FLOOR` (objeto EXIGIDO «currículum»,
 * destino EXIGIDO «a la entrevista») + MISMA forma en la plantilla
 * `matchInterviewRun` de [ContextIntentEngine.extractTitle] (grafía del
 * usuario preservada, doctrina c.653; la cola temporal la depura
 * sanitizeTitle). Kind ERRAND (acarreo físico, doctrina c.1144). UNA
 * forma por ciclo (anti-overreach): «el CV», «el informe» y otros
 * destinos («la reunión», «la oficina») quedan FUERA pineados NULL.
 * PRE medido (sonda efímera `/tmp/probe1174/LlevarCurriculumPreProbe.kt`,
 * motor real vía tools/run_probe.sh, HEAD `9220964`): 6/6 candidatas
 * NULL, 4/4 regresiones «llevar» HIT (taller/cole/fiesta-del-cole/
 * veterinario), 5/5 controles NULL correctos.
 */
class ContextIntentEngineLlevarCurriculumEntrevistaFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1000)
    )

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura curriculum entrevista manana`() {
        val intent = analyze("llevar el currículum a la entrevista mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el currículum a la entrevista", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura entrevista de trabajo`() {
        val intent = analyze("llevar el currículum a la entrevista de trabajo mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertTrue(intent.title.startsWith("Llevar el currículum a la entrevista"))
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura primera persona llevo`() {
        val intent = analyze("llevo el currículum a la entrevista el jueves")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevo el currículum a la entrevista", intent.title)
    }

    @Test
    fun `captura prefijo temporal`() {
        val intent = analyze("mañana llevar el currículum a la entrevista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el currículum a la entrevista", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura sin temporal`() {
        val intent = analyze("llevar el currículum a la entrevista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el currículum a la entrevista", intent.title)
        assertNull(intent.dueAt)
    }

    @Test
    fun `captura posesivo mi curriculum`() {
        val intent = analyze("llevar mi currículum a la entrevista mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar mi currículum a la entrevista", intent.title)
    }

    @Test
    fun `captura grafia sin tilde curriculum`() {
        val intent = analyze("llevar el curriculum a la entrevista mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el curriculum a la entrevista", intent.title)
    }

    // ---- Guards (NULLs correctos) ----

    @Test
    fun `guard negacion compuesta`() {
        assertNull(analyze("no voy a llevar el currículum a la entrevista mañana"))
    }

    @Test
    fun `guard negacion directa`() {
        assertNull(analyze("no llevo el currículum a la entrevista"))
    }

    @Test
    fun `guard duda subjuntivo`() {
        assertNull(analyze("quizá lleve el currículum a la entrevista"))
    }

    @Test
    fun `guard preterito`() {
        assertNull(analyze("llevé el currículum a la entrevista ayer"))
    }

    @Test
    fun `guard sustantivo aislado`() {
        assertNull(analyze("el currículum de la entrevista"))
    }

    @Test
    fun `guard verbo aislado`() {
        assertNull(analyze("llevar"))
    }

    // ---- Pines anti-overreach (NULL deliberado) ----

    @Test
    fun `pin CV abreviado fuera`() {
        assertNull(analyze("llevar el CV a la entrevista mañana"))
    }

    @Test
    fun `pin otro objeto fuera`() {
        assertNull(analyze("llevar el informe a la entrevista mañana"))
    }

    @Test
    fun `pin otro destino fuera`() {
        assertNull(analyze("llevar el currículum a la oficina mañana"))
    }

    // ---- Regresiones (pisos «llevar» hermanos intactos) ----

    @Test
    fun `regresion coche taller`() {
        val intent = analyze("llevar el coche al taller mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    @Test
    fun `regresion ninos cole`() {
        val intent = analyze("llevar a los niños al cole mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    @Test
    fun `regresion fiesta del cole c1170`() {
        val intent = analyze("llevar a los niños a la fiesta del cole el viernes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    @Test
    fun `regresion perro veterinario`() {
        val intent = analyze("llevar al perro al veterinario el sábado")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }
}
