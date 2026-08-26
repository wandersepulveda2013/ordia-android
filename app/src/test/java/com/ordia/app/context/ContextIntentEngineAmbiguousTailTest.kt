package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1193: residuo de temporal RELATIVO NO-CALIFICADO en el título
 * (medido con sonda persistida `TwentiethClassHouseholdProbe`):
 * "… después de la comida / del almuerzo" dejaba su cola en el
 * título visible y no genera dueAt (por diseño). Ahora `ambiguousTail`
 * despoja el residuo con genitivo marcado y el dueAt sigue nulo
 * (consistente con calificador explícito requerido). Las variantes
 * «esta semana/este finde/este mes/después de cenar/comer/desayunar/
 * almorzar» PERMANECEN en el título por decisión previa documentada.
 */
class ContextIntentEngineAmbiguousTailTest {

    @Test
    fun `residuo con genitivo se despoja del titulo`() {
        val r = analyze("avisar al jefe después de la comida")
        assertEquals("Avisar al jefe", r!!.title)
        assertNull(r.dueAt)
    }

    @Test
    fun `variante el almuerzo despojada`() {
        val r = analyze("avisar al jefe después del almuerzo")
        assertEquals("Avisar al jefe", r!!.title)
        assertNull(r.dueAt)
    }

    @Test
    fun `bare después de comer se conserva por decision previa`() {
        val r = analyze("pasear al perro después de cenar")
        assertEquals("Pasear al perro después de cenar", r!!.title)
        assertNull(r.dueAt)
    }

    private fun analyze(text: String) =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
        )
}
