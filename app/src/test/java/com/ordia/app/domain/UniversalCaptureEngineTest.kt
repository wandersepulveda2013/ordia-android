package com.ordia.app.domain

import com.ordia.app.data.local.CaptureTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalCaptureEngineTest {
    @Test fun explicitNoteCommandBecomesNote() {
        val result = UniversalCaptureEngine.interpret("Guardar esto como nota: idea para la auditoría")
        assertEquals(CaptureTarget.NOTE, result.target)
        assertEquals("idea para la auditoría", result.title)
    }

    @Test fun multilineListBecomesChecklistNote() {
        val result = UniversalCaptureEngine.interpret("- arroz\n- café\n- leche")
        assertEquals(CaptureTarget.NOTE, result.target)
        assertTrue(result.checklist)
    }

    @Test fun reminderKeepsParsedDateSignals() {
        val result = UniversalCaptureEngine.interpret("Recuérdame llamar mañana a las 8")
        assertEquals(CaptureTarget.REMINDER, result.target)
        assertTrue(result.parsedTask?.dueAt != null)
    }

    @Test fun uncertainTextFallsBackToInboxWithoutLoss() {
        val raw = "Una idea suelta que todavía no sé organizar"
        val result = UniversalCaptureEngine.interpret(raw)
        assertEquals(CaptureTarget.INBOX, result.target)
        assertEquals(raw, result.body)
    }

    @Test fun fingerprintIsStableAndIncludesAttachment() {
        val one = UniversalCaptureEngine.fingerprint("  Hola   mundo ")
        val two = UniversalCaptureEngine.fingerprint("hola mundo")
        assertEquals(one, two)
        assertNotEquals(one, UniversalCaptureEngine.fingerprint("hola mundo", "content://imagen/1"))
    }

    @Test fun attachmentWithoutActionBecomesNote() {
        val result = UniversalCaptureEngine.interpret(
            raw = "Referencia visual",
            hasAttachment = true
        )

        assertEquals(CaptureTarget.NOTE, result.target)
    }

    @Test fun eventNounsWithTemporalSignalBecomeEvent() {
        val result = UniversalCaptureEngine.interpret("Reunión con María mañana a las 10")
        assertEquals(CaptureTarget.EVENT, result.target)
        assertTrue("El evento se resuelve con tarea y fecha", result.parsedTask?.dueAt != null)
    }

    @Test fun eventWithWeekdayBecomesEvent() {
        val result = UniversalCaptureEngine.interpret("Cena familiar el viernes")
        assertEquals(CaptureTarget.EVENT, result.target)
    }

    @Test fun explicitEventRequestStaysEvent() {
        val result = UniversalCaptureEngine.interpret(
            raw = "Almuerzo con el equipo",
            requested = CaptureTarget.EVENT
        )
        assertEquals(CaptureTarget.EVENT, result.target)
    }
}
