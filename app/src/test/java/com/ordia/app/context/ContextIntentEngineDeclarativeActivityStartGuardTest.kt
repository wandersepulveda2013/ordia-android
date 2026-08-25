package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1145: guard anti-overreach «empieza/comienza» declarativo (EXERCISE).
 * Hallazgo P1 abierto de la auditoría c.1135: el enunciado de HECHO «el
 * campamento de los niños empieza en julio» (sin ningún compromiso del
 * usuario) se persistía como intención firme EXERCISE 0.45 por el camino
 * keyword+bono temporal. Medición PRE con sonda efímera (motor real vía
 * `tools/run_probe.sh`, HEAD ceae81c): 7/7 declarativas EXERCISE 0.45
 * («el campamento de los niños empieza en julio», «las clases de natación
 * empiezan en septiembre», «el campamento empieza la semana que viene»,
 * «la natación empieza en septiembre», «las pesas empiezan a las siete»,
 * «el campamento comienza en julio», «las clases de natación comienzan en
 * septiembre») — bandeja degradada con hechos que el usuario nunca se
 * comprometió a hacer (misma clase P1 que el pretérito+MEETING c.1138,
 * pero por bono keyword+temporal, no por piso). Fix: guard acotado
 * `declarativeActivityStartGoverns` (hermano de `pastMeetingNarrativeGoverns`
 * c.1138) — patrón ANCLADO al inicio «(¿)? (det)? <sujeto…con
 * campamento|natación|pesas…> (empieza|empiezan|comienza|comienzan)»
 * descarta el kind EXERCISE. CERO keywords nuevas, CERO pisos, CERO
 * plantillas. Lo NO anclado no se toca (pines anti-regresión medidos PRE):
 * «apúntate a natación que empieza en septiembre» / «inscríbete en el
 * campamento que empieza en julio» / «empezar el campamento de los niños
 * en julio» siguen EXERCISE 0.45; «recuérdame que el campamento empieza
 * en julio» sigue TASK 0.45 (envolvente legítima).
 */
class ContextIntentEngineDeclarativeActivityStartGuardTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1000)
    )

    // ---- Declarativas bloqueadas (hecho, no compromiso) ----

    @Test
    fun `declarativa campamento ninos empieza en julio queda NULL`() {
        assertNull(analyze("el campamento de los niños empieza en julio"))
    }

    @Test
    fun `declarativa clases de natacion empiezan en septiembre queda NULL`() {
        assertNull(analyze("las clases de natación empiezan en septiembre"))
    }

    @Test
    fun `declarativa campamento empieza semana que viene queda NULL`() {
        assertNull(analyze("el campamento empieza la semana que viene"))
    }

    @Test
    fun `declarativa natacion empieza en septiembre queda NULL`() {
        assertNull(analyze("la natación empieza en septiembre"))
    }

    @Test
    fun `declarativa pesas empiezan a las siete queda NULL`() {
        assertNull(analyze("las pesas empiezan a las siete"))
    }

    @Test
    fun `declarativa campamento comienza en julio queda NULL`() {
        assertNull(analyze("el campamento comienza en julio"))
    }

    @Test
    fun `declarativa clases de natacion comienzan en septiembre queda NULL`() {
        assertNull(analyze("las clases de natación comienzan en septiembre"))
    }

    // ---- Pines anti-regresión (compromisos legítimos, NO anclados) ----

    @Test
    fun `imperativa apuntate a natacion que empieza sigue EXERCISE`() {
        val intent = analyze("apúntate a natación que empieza en septiembre")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
    }

    @Test
    fun `imperativa inscribete campamento que empieza sigue EXERCISE`() {
        val intent = analyze("inscríbete en el campamento que empieza en julio")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
    }

    @Test
    fun `envolvente recuerdame campamento empieza sigue TASK`() {
        val intent = analyze("recuérdame que el campamento empieza en julio")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `imperativa empezar campamento ninos sigue EXERCISE`() {
        val intent = analyze("empezar el campamento de los niños en julio")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
    }

    @Test
    fun `inscribir campamento c1135 sigue EXERCISE`() {
        val intent = analyze("inscribir al niño en el campamento en julio")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
    }

    @Test
    fun `voy a empezar natacion sigue EXERCISE`() {
        val intent = analyze("voy a empezar natación en septiembre")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
    }

    @Test
    fun `apuntarse a natacion sigue EXERCISE`() {
        val intent = analyze("apuntarse a natación en septiembre")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
    }

    // ---- Cross-kind: el guard sólo descarta EXERCISE ----

    @Test
    fun `empezar la dieta sigue TASK`() {
        val intent = analyze("empezar la dieta el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `mi dieta empieza en agosto sigue NULL`() {
        assertNull(analyze("mi dieta empieza en agosto"))
    }
}
