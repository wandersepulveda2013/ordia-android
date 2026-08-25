package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1135: candidata (b) de la clase DECIMODUARTA (vida escolar de los hijos)
 * — «inscribir al niño en el campamento». NULL PRE medido por la sonda
 * persistida c.1127 `tools/probe/FourteenthClassSchoolProbe.kt` (C19:
 * «inscribir al niño en el campamento en julio» NULL) y re-medido en sonda
 * efímera (motor real, HEAD 96a82eb): 5/5 NULL en las variantes con señal
 * temporal. Las hermanas con keyword capturan EXERCISE por el camino
 * keyword+bono (medido: «inscribir al niño en natación en septiembre» →
 * EXERCISE 0.45). Fix hermano EXACTO de ese camino: keyword «campamento»
 * (ContextIntent.kt) + alternativa «campamento» en el bono EXERCISE de
 * scoreKind. CERO pisos nuevos, CERO plantillas nuevas. UNA forma por ciclo:
 * la actividad hermana «extraescolares» queda FUERA como lateral (b-bis).
 * Títulos/dueAt pinneados a lo medido en las hermanas natación (cola
 * temporal real se recorta con dueAt; «en <mes>» queda en el título sin
 * dueAt — familia de colas ya documentada c.845/c.852/c.1079/c.1102).
 */
class ContextIntentEngineInscribirCampamentoFloorTest {

    // ---- Capturas (keyword «campamento» + bono EXERCISE) ----

    @Test
    fun `captura inscribir campamento en julio`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inscribir al niño en el campamento en julio", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Inscribir al niño en el campamento en julio", intent.title)
        assertNull(intent.dueAt)
    }

    @Test
    fun `captura inscribir ninos campamento semana que viene`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inscribir a los niños en el campamento la semana que viene", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Inscribir a los niños en el campamento", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura inscribir nina campamento en agosto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inscribir a la niña en el campamento en agosto", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Inscribir a la niña en el campamento en agosto", intent.title)
        assertNull(intent.dueAt)
    }

    @Test
    fun `captura inscribir mi nino campamento manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inscribir a mi niño en el campamento mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Inscribir a mi niño en el campamento", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, inscribir al niño en el campamento en julio", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        // Pin a lo medido (POST sonda efímera): el camino keyword+piso no
        // tiene plantilla de título, así el acuse «vale, » queda en el
        // título por la capitalización genérica — mismo perfil que la
        // hermana «natación» por esta vía.
        assertEquals("Vale, inscribir al niño en el campamento en julio", intent.title)
    }

    // ---- Pines hermanas natación (byte-exactos, cero regresión) ----

    @Test
    fun `pin hermana natacion con temporal intacta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inscribir al niño en natación en septiembre", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Inscribir al niño en natación en septiembre", intent.title)
        assertNull(intent.dueAt)
    }

    @Test
    fun `pin hermana natacion sin temporal sigue fuera`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inscribir al niño en natación", 1000)
        )
        assertNull(intent)
    }

    // ---- Consistencia familiar (umbral exige señal temporal) ----

    @Test
    fun `campamento sin temporal sigue fuera`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inscribir al niño en el campamento", 1000)
        )
        assertNull(intent)
    }

    // ---- Guards (bajo umbral, consistencia familiar) ----

    @Test
    fun `guard negacion sin temporal fuera`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no inscribir al niño en el campamento", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `guard duda subjuntivo fuera`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá inscriba al niño en el campamento en julio", 1000)
        )
        assertNull(intent)
    }

    // RE-PIN c.1145 (precedente c.1035/c.1041/c.1094): este pin capturaba el
    // falso positivo declarativo documentado aquí mismo como hallazgo
    // BACKLOG — c.1145 lo cerró con el guard `declarativeActivityStartGoverns`
    // (enunciado de hecho «<actividad> empieza/comienza…», sin compromiso del
    // usuario ⇒ NULL; la envolvente imperativa no anclada sigue capturando).
    @Test
    fun `pin declarativo empieza queda NULL tras guard c1145`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "el campamento de verano del colegio empieza en julio", 1000)
        )
        assertNull(intent)
    }

    // ---- Pines de kinds vecinos (byte-exactos) ----

    @Test
    fun `pin apuntar transitivo sigue NOTE`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "apuntar al niño al campamento", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.NOTE, intent!!.kind)
        assertEquals("Apuntar al niño al campamento", intent.title)
    }

    @Test
    fun `pin envolvente recuerdame sigue TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame inscribir al niño en el campamento", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Inscribir al niño en el campamento", intent.title)
    }

    // ---- Consecuencia medida y deliberada (mismo perfil que la familia
    // natación, medido N6: «llevar a los niños a natación mañana» →
    // EXERCISE 0.45): el destino «campamento» con keyword gana captura. ----

    @Test
    fun `pin llevar ninos al campamento captura EXERCISE`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños al campamento mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Llevar a los niños al campamento", intent.title)
        assertNotNull(intent.dueAt)
    }
}
