package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1146: lateral (b-bis) ABIERTA de c.1135 — «inscribir a <niño> en (las)?
 * extraescolares». NULL medido en sonda efímera PRE (motor real, HEAD
 * 72dcc77): 4/4 variantes con señal temporal NULL («…en septiembre»,
 * «…la semana que viene», «…mañana», acuse «vale, …en octubre») mientras la
 * hermana «campamento» (c.1135) capturaba EXERCISE 0.45. El plazo de
 * inscripción perdido es olvido real (P1): las extraescolares del colegio
 * tienen ventana de matrícula.
 *
 * Fix hermano EXACTO de c.1135 (lockstep keyword↔fuente-única, lección
 * c.616/c.751): keyword «extraescolares» en ContextIntent.EXERCISE +
 * alternativa «extraescolares» en [ContextIntentEngine.EXERCISE_VERBS]
 * (fuente única que fluye al piso de posición libre, a la negación y al
 * guard de envolvente). CERO pisos nuevos, CERO plantillas nuevas.
 *
 * Extensión lockstep del guard declarativo c.1145
 * ([DECLARATIVE_ACTIVITY_START_PATTERN]): sin ella, la keyword nueva
 * convertiría «las extraescolares del colegio empiezan en septiembre»
 * (enunciado de hecho, sin compromiso del usuario) en EXERCISE 0.45 — la
 * misma corrupción P1 que c.1145 cerró para «campamento». CERO regiones de
 * marcadores activos tocadas.
 *
 * Títulos/dueAt pinneados a lo medido en las hermanas campamento/natación
 * (cola temporal real se recorta con dueAt; «en <mes>» queda en el título
 * sin dueAt — familia de colas ya documentada c.845/c.852/c.1079/c.1102).
 */
class ContextIntentEngineInscribirExtraescolaresFloorTest {

    // ---- Capturas (keyword «extraescolares» + piso posición libre) ----

    @Test
    fun `captura inscribir extraescolares en septiembre`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inscribir al niño en las extraescolares en septiembre", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Inscribir al niño en las extraescolares en septiembre", intent.title)
        assertNull(intent.dueAt)
    }

    @Test
    fun `captura inscribir ninos extraescolares semana que viene`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inscribir a los niños en las extraescolares la semana que viene", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Inscribir a los niños en las extraescolares", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura inscribir mi nina extraescolares manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inscribir a mi niña en extraescolares mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Inscribir a mi niña en extraescolares", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, inscribir al niño en las extraescolares en octubre", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        // Pin hermano del acuse c.1135: el camino keyword+piso no tiene
        // plantilla de título, así el acuse «vale, » queda en el título por
        // la capitalización genérica.
        assertEquals("Vale, inscribir al niño en las extraescolares en octubre", intent.title)
    }

    // ---- Pines hermanas (byte-exactos, cero regresión) ----

    @Test
    fun `pin hermana campamento intacta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inscribir al niño en el campamento en julio", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Inscribir al niño en el campamento en julio", intent.title)
        assertNull(intent.dueAt)
    }

    @Test
    fun `pin hermana natacion intacta`() {
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
    fun `extraescolares sin temporal sigue fuera`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inscribir al niño en las extraescolares", 1000)
        )
        assertNull(intent)
    }

    // ---- Guards (bajo umbral / anti-overreach, consistencia familiar) ----

    @Test
    fun `guard negacion sin temporal fuera`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no inscribir al niño en las extraescolares", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `guard duda subjuntivo fuera`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá inscriba al niño en las extraescolares en septiembre", 1000)
        )
        assertNull(intent)
    }

    // OVERREACH FAMILIAR CERRADO (c.1154, hallazgo c.1146-i): el pretérito
    // de 1ª persona «inscribí … ayer» capturaba EXERCISE 0.45 con dueAt en
    // el PASADO en TODAS las hermanas (pin original de este test: medido
    // POST c.1146 «inscribí al niño en el campamento ayer» / «…en natación
    // ayer» → EXERCISE 0.45, dueAt=ayer). El guard
    // [ContextIntentEngine.pastExerciseEnrollGoverns] c.1154 descarta la
    // keyword EXERCISE gobernada por pretérito; este pin se invirtió de
    // documentar-el-defecto a regresión-de-la-corrección (el comentario
    // original ya anticipaba el guard como «candidato de ciclo propio»).
    @Test
    fun `pin preterito primera persona hereda overreach familiar`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inscribí al niño en las extraescolares ayer", 1000)
        )
        assertNull(intent)
    }

    // Consecuencia medida y deliberada (mismo perfil que la familia
    // natación/campamento, medido POST: «apuntar a la niña a natación este
    // mes» / «…al campamento este mes» → EXERCISE 0.45): con keyword +
    // señal temporal, el objeto-actividad gana EXERCISE sobre la envolvente
    // débil «apuntar» (hermano del pin «llevar … al campamento» c.1135).
    @Test
    fun `pin apuntar con temporal captura EXERCISE`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "apuntar a la niña a las extraescolares este mes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Apuntar a la niña a las extraescolares este mes", intent.title)
    }

    // Guard declarativo c.1145 extendido en lockstep con la keyword nueva:
    // enunciado de hecho «<actividad> empieza/comienza…», sin compromiso del
    // usuario ⇒ NULL (misma corrupción que c.1145 cerró para «campamento»).
    @Test
    fun `guard declarativo empieza queda NULL`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "las extraescolares del colegio empiezan en septiembre", 1000)
        )
        assertNull(intent)
    }

    // ---- Pines de kinds vecinos (byte-exactos) ----

    @Test
    fun `pin envolvente recuerdame sigue TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame inscribir al niño en las extraescolares", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Inscribir al niño en las extraescolares", intent.title)
    }
}
