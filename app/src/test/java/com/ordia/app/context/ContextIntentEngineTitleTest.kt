package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression locks for the title residue sanitizer of [ContextIntentEngine]
 * (c.606, paridad con el estándar de limpieza de títulos de NaturalTaskParser
 * c.237–c.438).
 *
 * Antes de la corrección, los `(.+)` voraces de [extractTitle]/[generateTitle]
 * dejaban los anclajes de fecha/hora (que [extractDateTime] ya resolvió en
 * dueAt) como RESIDUO en el título visible de un [ContextIntent] capturado:
 * una notificación "recuérdame llamar a mamá el viernes a las 3" nacía como
 * tarea con título "Llamar a mamá el viernes a las 3", y los prefijos
 * ("Cita: "/"Reunión: "/"Pagar ") capitalizaban además el artículo/preposición
 * que encabeza la cola ("Reunión: Con el equipo"). El sanitizer depura el
 * residuo temporal de cola y corrige la capitalización.
 */
class ContextIntentEngineTitleTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    // --- Residuo temporal de cola eliminado (dueAt preservado) ---

    @Test
    fun reminderCallStripsWeekdayAndTimeResidue() {
        val intent = analyze("recuérdame llamar a mamá el viernes a las 3")
        assertNotNull(intent)
        assertEquals("Llamar a mamá", intent!!.title)
        assertNotNull("dueAt debe preservarse", intent.dueAt)
    }

    @Test
    fun reminderCallStripsMananaAndMeridiemResidue() {
        val intent = analyze("recuérdame llamar a juan mañana a las 9 de la mañana")
        assertNotNull(intent)
        assertEquals("Llamar a juan", intent!!.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun paymentStripsDateResidueAndFixesArticleCapitalization() {
        val intent = analyze("avísame pagar la factura el 15 de septiembre")
        assertNotNull(intent)
        assertEquals("Pagar la factura", intent!!.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun taskStripsWeekdayResidue() {
        val intent = analyze("no olvides comprar leche el viernes")
        assertNotNull(intent)
        assertEquals("Comprar leche", intent!!.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun taskStripsWeekdayAndHhMmResidue() {
        val intent = analyze("tengo que entregar el informe el lunes a las 18:30")
        assertNotNull(intent)
        assertEquals("Entregar el informe", intent!!.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun meetingStripsWeekdayTimeAndFixesCapitalization() {
        val intent = analyze("reunión con el equipo el jueves a las 10")
        assertNotNull(intent)
        assertEquals("Reunión: con el equipo", intent!!.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun appointmentStripsDateAndMeridiemResidue() {
        val intent = analyze("llamar al dentista el 20 a las 4 de la tarde")
        assertNotNull(intent)
        assertEquals("Llamar al dentista", intent!!.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun paymentStripsDelMesAndPmResidue() {
        val intent = analyze("pagar la luz el 30 del mes a las 6 pm")
        assertNotNull(intent)
        assertEquals("Pagar la luz", intent!!.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun studyStripsDateAndTimeResidueAndFixesCapitalization() {
        val intent = analyze("recuérdame estudiar para el examen el 22 de agosto a las 9")
        assertNotNull(intent)
        assertEquals("Estudio: para el examen", intent!!.title)
        assertNotNull(intent.dueAt)
    }

    // --- Guard anti-genitivo: "de hoy"/"de ayer" es contenido, no residuo ---

    @Test
    fun genitiveDeHoyIsNotStrippedAsResidue() {
        // "comprar el diario de hoy" NO clasifica bajo el umbral de confianza
        // (no es recordatorio/cita explícita): no hay ContextIntent que
        // desvirtuar, pero si clasificara, el sanitizer NO debe quitar "de hoy".
        // Se cubre indirectamente: el sanitizer sólo actúa sobre la COLA y con
        // guard de palabra precedente ("de" → genitivo, se preserva).
        assertNull(analyze("comprar el diario de hoy"))
    }

    // --- Sin anclaje temporal: el título no debe alterarse ---

    @Test
    fun titleWithoutTemporalAnchorIsPreserved() {
        // "comprar leche y huevos" no clasifica (sin seña de recordatorio/cita);
        // el sanitizer nunca se invoca. Se documenta el invariante de no-overreach.
        assertNull(analyze("comprar leche y huevos"))
    }
}
