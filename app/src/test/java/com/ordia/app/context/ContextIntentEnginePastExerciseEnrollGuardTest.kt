package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1154: guard de INSCRIPCIÓN escolar/deportiva en PRETÉRITO — hallazgo P1
 * ABIERTO dejado por el hermano en c.1135 (commit 1b7c509): por el camino
 * keyword EXERCISE («campamento» c.1135, «natación», «extraescolar» c.1146)
 * la frase en pretérito de 1ª persona «inscribí/apunté al niño en … ayer»
 * capturaba EXERCISE 0.45 con dueAt PASADO: un hecho YA CUMPLIDO se
 * persistía como compromiso futuro con fecha en el pasado (misma corrupción
 * que c.1138 cerró para MEETING por camino piso por sustantivo). Clase
 * hermana EXACTA del guard [pastMeetingNarrativeGoverns] c.1138, pero por
 * camino KEYWORD (los pisos EXERCISE no casan: «inscribí…» sin temporal ya
 * era NULL — medido PRE; el keyword inerte 0.12 cruza el umbral solo con
 * el bono temporal pasado).
 *
 * Mediciones PRE (sonda efímera, motor real, HEAD 7e0e655):
 *   T1 «inscribí al niño en el campamento ayer»           → EXERCISE 0.45 dueAt ✓ (corrupción)
 *   T2 «inscribí a la niña en natación ayer»              → EXERCISE 0.45 dueAt ✓ (corrupción)
 *   T3 «apunté a los niños en extraescolares la semana pasada» → EXERCISE 0.45 dueAt ✓ (corrupción)
 *   T4 «no inscribí al niño en el campamento»             → NULL (ya, lookbehind + keyword)
 *   T5 «inscribí al niño en el campamento» (sin temporal) → NULL (keyword inerte < umbral)
 *   A4 «inscribieron a los niños en el campamento ayer»   → EXERCISE 0.45 dueAt ✓ (corrupción)
 *   A5 «apuntó a la niña en natación ayer»                → EXERCISE 0.45 dueAt ✓ (corrupción)
 *
 * Regresiones pineadas (byte-idénticas): infinitivo R1/R2, futuro
 * perifrástico R3, envolvente TASK R4/R5, presente 1ª X2.
 *
 * Anti-overreach deliberado: las formas AMBIGUAS presente/pretérito
 * («inscribimos/apuntamos») quedan FUERA — «inscribimos al niño en
 * natación en septiembre» (presente, plan futuro) es captura legítima
 * EXERCISE (A1 medido); su hermana pretérito con «ayer» (A2/A3 medido)
 * queda como lateral documentada, UNA forma por ciclo (doctrina c.1138:
 * primero las formas inequívocas).
 */
class ContextIntentEnginePastExerciseEnrollGuardTest {

    // ---- El guard descarta la inscripción YA narrada en pretérito ----

    @Test
    fun `pretérito inscribí campamento ayer descarta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inscribí al niño en el campamento ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pretérito inscribí natación ayer descarta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inscribí a la niña en natación ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pretérito apunté extraescolares semana pasada descarta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "apunté a los niños en extraescolares la semana pasada", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pretérito inscribieron campamento ayer descarta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inscribieron a los niños en el campamento ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pretérito apuntó natación ayer descarta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "apuntó a la niña en natación ayer", 1000)
        )
        assertNull(intent)
    }

    // ---- Pines de comportamiento ya correcto (idempotencia del guard) ----

    @Test
    fun `no inscribí ya era NULL`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no inscribí al niño en el campamento", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `inscribí sin temporal ya era NULL`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inscribí al niño en el campamento", 1000)
        )
        assertNull(intent)
    }

    // ---- Regresiones: presente/infinitivo/envolvente NO se tocan ----

    @Test
    fun `infinitivo inscribir campamento mañana captura EXERCISE`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inscribir al niño en el campamento mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `infinitivo inscribir natación el lunes captura EXERCISE`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inscribir a la niña en natación el lunes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `futuro perifrástico voy a inscribir captura EXERCISE`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "voy a inscribir al niño en natación mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `envolvente recuérdame inscribir captura TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame inscribir al niño en el campamento", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `obligación hay que inscribir captura TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hay que inscribir al niño en extraescolares", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `presente inscribo natación mañana captura EXERCISE`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inscribo al niño en natación mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    // ---- Anti-overreach: formas ambiguas presente/pretérito FUERA ----

    @Test
    fun `ambigua inscribimos en septiembre captura EXERCISE presente`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inscribimos al niño en natación en septiembre", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
    }
}
