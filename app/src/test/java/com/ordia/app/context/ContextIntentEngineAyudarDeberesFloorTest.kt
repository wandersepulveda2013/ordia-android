package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1130: candidata (b) de la clase DECIMODUARTA (vida escolar de los hijos,
 * descubrimiento c.1127, sonda persistida
 * `tools/probe/FourteenthClassSchoolProbe.kt`) — «ayudar a <hijo> con los
 * deberes» caía a NULL (C26-C29, 4/4 NULL; re-medido PRE sobre `d20f6ae` con
 * sonda efímera: 8/8 targets NULL, 5/5 guards NULL, envolvente TASK 0.45 con
 * título limpio, regresiones intactas). Causa raíz: la keyword «deberes»
 * (STUDY c.898) existe, pero el piso `STUDY_HOMEWORK_FLOOR` sólo admite el
 * verbo «hacer» → keyword 0.12 + bono temporal 0.1 = 0.22 < 0.45 (olvido
 * silencioso P1: la sesión de deberes CON los hijos se dice «ayudar», no
 * «hacer»). Fix lockstep piso↔plantilla (lección c.616): alternativa
 * «ayudar a(l|la|los|las|mis|tus|sus) niñ[oa]s? con (los|las)? deberes» en
 * el piso (fuente única compartida por [WRAPPABLE_PATTERNS] vía
 * STUDY_FLOORS) + plantilla hermana en la rama STUDY de `extractTitle`.
 * CERO keywords nuevas («deberes» c.898 — gate c.751 satisfecho; «ayudar»
 * NO se añade: bivalente). Kind STUDY (hermano EXACTO del piso deberes
 * c.898: sesión de estudio, no desplazamiento). Acotado deliberado (UNA
 * forma por ciclo, doctrina anti-overreach): objetos EXIGIDOS «niñ[oa]s?» +
 * «deberes» (los bivalentes «ayudar a un amigo con la mudanza» / «ayudar a
 * los niños con la cena» quedan FUERA como guards NULL); el presente
 * «ayudo» queda lateral (b-bis). Determinista (regex), cero random, cero IA
 * fingida.
 */
class ContextIntentEngineAyudarDeberesFloorTest {

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura los ninos esta tarde`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "ayudar a los niños con los deberes esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.STUDY, intent!!.kind)
        assertEquals("Ayudar a los niños con los deberes", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura al nino manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "ayudar al niño con los deberes mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.STUDY, intent!!.kind)
        assertEquals("Ayudar al niño con los deberes", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura a la nina esta tarde`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "ayudar a la niña con los deberes esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.STUDY, intent!!.kind)
        assertEquals("Ayudar a la niña con los deberes", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura deberes de matematicas`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "ayudar a los niños con los deberes de matemáticas mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.STUDY, intent!!.kind)
        assertEquals("Ayudar a los niños con los deberes de matemáticas", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, ayudar a los niños con los deberes mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.STUDY, intent!!.kind)
        assertEquals("Ayudar a los niños con los deberes", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana ayudar a los niños con los deberes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.STUDY, intent!!.kind)
        assertEquals("Ayudar a los niños con los deberes", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura mis ninos`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "ayudar a mis niños con los deberes mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.STUDY, intent!!.kind)
        assertEquals("Ayudar a mis niños con los deberes", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura sin pista temporal`() {
        // El piso basta por sí solo (0.45 = MINIMUM_CONFIDENCE): la ausencia
        // de fecha no descarta la sesión de deberes (mismo contrato que el
        // piso hermano «hacer los deberes» c.898).
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "ayudar a los niños con los deberes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.STUDY, intent!!.kind)
        assertEquals("Ayudar a los niños con los deberes", intent.title)
    }

    // ---- Envolvente: c.613 gobierna TASK (lockstep WRAPPABLE_PATTERNS) ----

    @Test
    fun `envolvente c613 gobierna TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame ayudar a los niños con los deberes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Ayudar a los niños con los deberes", intent.title)
    }

    // ---- Guards (contratos anti-overreach / anti-falsos-positivos) ----

    @Test
    fun `no ayudar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no ayudar a los niños con los deberes mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá ayudar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá ayudar a los niños con los deberes mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado ayude descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "ayudé a los niños con los deberes ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `bivalente amigo mudanza descartado`() {
        // «ayudar» es bivalente: sin el objeto «niños» la frase queda FUERA
        // (acotamiento deliberado, una forma por ciclo).
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "ayudar a un amigo con la mudanza mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `bivalente ninos cena descartado`() {
        // Sin el objeto «deberes» la frase queda FUERA aunque mencione a los
        // niños (acotamiento deliberado, una forma por ciclo).
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "ayudar a los niños con la cena mañana", 1000)
        )
        assertNull(intent)
    }

    // ---- Regresiones (pinos byte-idénticos medidos PRE) ----

    @Test
    fun `regresión hacer deberes intacta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hacer los deberes mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.STUDY, intent!!.kind)
        assertEquals("Hacer los deberes", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `regresión estudiar examen intacta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "estudiar para el examen mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.STUDY, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `regresión school run intacta`() {
        // El piso escolar «llevar a los niños al colegio» (c.773, región del
        // marcador del hermano) no es robado por la alternativa «ayudar».
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al colegio", intent.title)
    }
}
