package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.773: forma "llevar a los niños al colegio" (sonda
 * `tools/probe/FifthClassLifeProbe.kt`, QUINTA clase — familia/niños; elegida
 * por dispersión determinista epoch-day 20686 % 4 = 2 sobre el pool OPEN
 * residual de 4 ítems). NULL PRE verificado por la sonda sobre HEAD dda9251.
 * El desplazamiento escolar diario es una diligencia familiar canónica:
 * capturarlo evita el olvido (hermano SIMÉTRICO de "recoger a los niños"
 * —ERRAND vía `ERRAND_VERBS`—, la misma diligencia en dirección contraria).
 * Piso ERRAND acotado a la forma completa objeto+destino `niñ[oa]s?` +
 * `colegio|escuela|guarder[ií]a`: el verbo "llevar" es bivalente (el coche al
 * taller —`ERRAND_CARRY_FLOOR` c.684—, al perro al veterinario —HOUSEHOLD
 * c.747—, a María al cine —persona/ocio— quedan FUERA; una forma por ciclo,
 * doctrina de la sonda). Kind: ERRAND (deliberación contra HOUSEHOLD: no es
 * quehacer doméstico; contra TASK: es desplazamiento, like "ir al banco").
 * Lockstep keyword-OBJETO "niños" en ERRAND (lección c.713/c.751/c.765; NO el
 * verbo "llevar" — bivalente). Negación sin cláusula dedicada: keyword 0.12 +
 * bono temporal 0.1 = 0.22 < umbral (hermana c.765→c.772); el piso además
 * lleva el guard `(?<!no )` de la familia.
 */
class ContextIntentEngineLlevarNinosSchoolRunFloorTest {

    @Test
    fun `captura base manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al colegio", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura escuela hoy`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños a la escuela hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños a la escuela", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, llevar a los niños al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al colegio", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana llevar a los niños al colegio", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al colegio", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura primera persona guarderia`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevo a mis niños a la guardería mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevo a mis niños a la guardería", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `envolvente gobierna task`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame llevar a los niños al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `negada descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no llevar a los niños al colegio mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá llevar a los niños al colegio mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevé a los niños al colegio ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto bivalente persona ocio descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a María al cine mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `sin destino descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `declarativo suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "los niños van al colegio mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `regresion recoger a los ninos intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recoger a los niños a las 5", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    @Test
    fun `regresion llevar el coche al taller intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el coche al taller mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }
}
