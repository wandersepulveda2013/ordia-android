package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        // c.652: un envolvente ("recuérdame") ya no pierde el kind ante el piso
        // STUDY (posición libre c.647); el guard [imperativeIsWrapped] lo
        // mantiene en REMINDER y el título sale del extractor REMINDER.
        val intent = analyze("recuérdame estudiar para el examen el 22 de agosto a las 9")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertEquals("Estudiar para el examen", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Guard anti-genitivo: "de hoy"/"de ayer" es contenido, no residuo ---

    @Test
    fun genitiveDeHoyIsNotStrippedAsResidue() {
        // c.626: "comprar el diario de hoy" ahora CLASIFICA como COMPRA (piso de
        // imperativo "comprar <producto>"). El invariante que sigue vigente es que
        // el sanitizer NO debe quitar "de hoy" (genitivo, contenido). El título
        // preserva "de hoy" íntegro.
        val intent = analyze("comprar el diario de hoy")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.SHOPPING, intent!!.kind)
        assertEquals("Comprar el diario de hoy", intent.title)
    }

    // --- Sin anclaje temporal: el título no debe alterarse ---

    @Test
    fun titleWithoutTemporalAnchorIsPreserved() {
        // c.626: "comprar leche y huevos" ahora CLASIFICA como COMPRA (piso de
        // imperativo). El sanitizer nunca recibe residuo temporal (no hay anclaje),
        // así el título se preserva íntegro: el invariante de no-overreach sigue
        // cubierto (no se altera el contenido legítimo).
        val intent = analyze("comprar leche y huevos")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.SHOPPING, intent!!.kind)
        // c.653b: el objeto preserva su caso original.
        assertEquals("Comprar leche y huevos", intent.title)
    }
}
