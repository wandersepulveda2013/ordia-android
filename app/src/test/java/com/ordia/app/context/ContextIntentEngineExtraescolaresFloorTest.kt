package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1146: lateral (b-bis) ABIERTA de c.1135 — «inscribir a <niño> en (las)?
 * extraescolares» era NULL medido (sonda c.1127/familia c.1135, re-medida
 * sonda efímera c.1146: 4/4 formas inscribir/inscríbete/apúntate NULL mientras
 * la hermana «campamento» capturaba EXERCISE 0.45). Plazo de inscripción a
 * extraescolares perdido = olvido real P1.
 *
 * Fix lockstep (hermano EXACTO de c.1135 «campamento», precedente c.616/
 * c.751): keyword «extraescolar» en ContextIntent.EXERCISE + término
 * `extraescolares?` en EXERCISE_VERBS (fuente única que alimenta piso de
 * posición libre, negación y guard de envolvente, lección c.648).
 *
 * COHERENCIA c.1145 (lockstep de guards): al entrar en EXERCISE_VERBS,
 * «las extraescolares empiezan en septiembre» pasaría a ser falso positivo
 * (keyword + piso) — el guard declarativo `declarativeActivityStartGoverns`
 * se extiende en el mismo ciclo con `extraescolares?` en su lista de
 * actividades (tests 7 y 8 lo pinean).
 *
 * Fuera de alcance (una forma por ciclo): la ruta «apuntar al niño a las
 * extraescolares…» ya captura como NOTE 0.45 vía keyword «apuntar»
 * (test 10 la pinea como está; el matiz NOTE-vs-EXERCISE es lateral
 * aparte, registrada en BACKLOG).
 *
 * PRE (sonda efímera c.1146): tests 1-4 NULL → RED. Tests 5-11 pines
 * (verdes en PRE y POST).
 */
class ContextIntentEngineExtraescolaresFloorTest {

    private fun analyze(text: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_000L)
        )

    // --- Familia (b-bis): RED en PRE (NULL), GREEN en POST (EXERCISE 0.45) ---

    @Test
    fun `inscribir al nino en las extraescolares en septiembre es EXERCISE`() {
        val intent = analyze("inscribir al niño en las extraescolares en septiembre")
        assertEquals(ContextIntentKind.EXERCISE, intent?.kind)
        assertEquals(0.45f, intent!!.confidence, 0.0001f)
    }

    @Test
    fun `inscribir a la nina en una extraescolar de futbol es EXERCISE`() {
        val intent = analyze("inscribir a la niña en una extraescolar de fútbol")
        assertEquals(ContextIntentKind.EXERCISE, intent?.kind)
        assertEquals(0.45f, intent!!.confidence, 0.0001f)
    }

    @Test
    fun `imperativo inscribete en las extraescolares del colegio es EXERCISE`() {
        val intent = analyze("inscríbete en las extraescolares del colegio")
        assertEquals(ContextIntentKind.EXERCISE, intent?.kind)
        assertEquals(0.45f, intent!!.confidence, 0.0001f)
    }

    @Test
    fun `imperativo apuntate a las extraescolares que empiezan en octubre es EXERCISE`() {
        val intent = analyze("apúntate a las extraescolares que empiezan en octubre")
        assertEquals(ContextIntentKind.EXERCISE, intent?.kind)
        assertEquals(0.45f, intent!!.confidence, 0.0001f)
    }

    // --- Pines (verdes en PRE y POST) ---

    @Test
    fun `negacion no inscribir en las extraescolares queda NULL`() {
        assertNull(analyze("no inscribir al niño en las extraescolares"))
    }

    @Test
    fun `hedge quiza inscriba en las extraescolares queda NULL`() {
        assertNull(analyze("quizá inscriba al niño en las extraescolares"))
    }

    @Test
    fun `declarativa las extraescolares empiezan en septiembre queda NULL`() {
        // Pin del guard c.1145 extendido: sin la extensión coherente este
        // test fallaría en POST (keyword + piso lo capturarían como FP).
        assertNull(analyze("las extraescolares empiezan en septiembre"))
    }

    @Test
    fun `declarativa con modificador las extraescolares del colegio empiezan en octubre queda NULL`() {
        assertNull(analyze("las extraescolares del colegio empiezan en octubre"))
    }

    @Test
    fun `hermana inscribir campamento sigue EXERCISE`() {
        val intent = analyze("inscribir al niño en el campamento en julio")
        assertEquals(ContextIntentKind.EXERCISE, intent?.kind)
        assertEquals(0.45f, intent!!.confidence, 0.0001f)
    }

    @Test
    fun `re-pin ruta apuntar captura como EXERCISE (era NOTE en PRE)`() {
        // Re-pin legítimo (precedente c.1035/c.1094): en PRE «apuntar…» era
        // NOTE 0.45 vía keyword «apuntar»; con la keyword «extraescolar» + piso
        // el kind correcto gana (actividad de los hijos, hermana de c.1135).
        val intent = analyze("apuntar al niño a las extraescolares este curso")
        assertEquals(ContextIntentKind.EXERCISE, intent?.kind)
        assertEquals(0.45f, intent!!.confidence, 0.0001f)
    }

    @Test
    fun `pin comportamiento PRE-EXISTENTE opinion creo que captura EXERCISE (FP de familia, lateral abierta)`() {
        // NO es un FP nuevo de c.1146: sonda medida — «creo que el campamento
        // es bueno…» / «creo que la natación es buena…» / «creo que las pesas
        // son buenas…» ya capturaban EXERCISE 0.45 ANTES de este ciclo. Es un
        // falso positivo DE FAMILIA (piso de posición libre sin guard de
        // opinión). Registrar en BACKLOG como lateral candidata (guard
        // «creo que <actividad>…», hermano del declarativo c.1145); corregirlo
        // aquí sería scope creep sobre TODA la familia (una forma por ciclo).
        val intent = analyze("creo que las extraescolares son buenas para los niños")
        assertEquals(ContextIntentKind.EXERCISE, intent?.kind)
        assertEquals(0.45f, intent!!.confidence, 0.0001f)
    }
}
