package com.ordia.app.domain

import com.ordia.app.data.local.CaptureTarget
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * c.1101 — mayúsculas con tilde en las señales de la captura universal.
 *
 * `reminderSignal`/`taskSignal` usaban «(?i)» inline, que en la JVM es
 * ASCII-only: las alternativas acentuadas (recu[eé]rdame, av[ií]same,
 * reuni[oó]n) NO casaban en caps («RECUÉRDAME…», «AVÍSAME…», «REUNIÓN…»)
 * y el recordatorio se degradaba a tarea genérica / la acción caía a INBOX
 * (evitar-olvidos P1; mismo bug sistémico resuelto en AssistantEngine c.1096).
 * Fix: «(?i)» → «(?iu)» (UNICODE_CASE añade fold Unicode; semántica ASCII
 * idéntica; \b intacto). Determinista (regex), cero random, cero IA fingida.
 */
class UniversalCaptureEngineMayusculasTildeSignalTest {

    // Capturas GAP medidas PRE con sonda efímera /tmp/probe1101.kt:
    // TASK/TASK/INBOX donde correspondía REMINDER/REMINDER/TASK.

    @Test
    fun capsAccentedRecuerdameSignalBecomesReminder() {
        val raw = "RECUÉRDAME LLAMAR A MAMÁ"
        val result = UniversalCaptureEngine.interpret(raw)
        assertEquals(CaptureTarget.REMINDER, result.target)
        assertEquals(raw, result.body)
    }

    @Test
    fun capsAccentedAvisameSignalBecomesReminder() {
        val raw = "AVÍSAME MAÑANA DE LLAMAR AL BANCO"
        val result = UniversalCaptureEngine.interpret(raw)
        assertEquals(CaptureTarget.REMINDER, result.target)
        assertEquals(raw, result.body)
    }

    @Test
    fun capsAccentedReunionSignalBecomesTask() {
        val raw = "REUNIÓN CON ANA"
        val result = UniversalCaptureEngine.interpret(raw)
        assertEquals(CaptureTarget.TASK, result.target)
        assertEquals(raw, result.body)
    }

    // Pines byte-equivalentes: comportamiento ya correcto que NO debe cambiar.

    @Test
    fun lowercaseRecuerdameSignalStillReminder() {
        assertEquals(CaptureTarget.REMINDER, UniversalCaptureEngine.interpret("recuérdame llamar a mamá").target)
    }

    @Test
    fun capsUnaccentedRecordatorioSignalStillReminder() {
        assertEquals(CaptureTarget.REMINDER, UniversalCaptureEngine.interpret("RECORDATORIO COMPRAR LECHE").target)
    }

    @Test
    fun capsReflexiveOlvideSignalStillReminder() {
        assertEquals(CaptureTarget.REMINDER, UniversalCaptureEngine.interpret("QUE NO SE ME OLVIDE COMPRAR LECHE").target)
    }

    @Test
    fun capsNoteCommandStillNote() {
        assertEquals(CaptureTarget.NOTE, UniversalCaptureEngine.interpret("GUARDAR ESTO COMO NOTA: IDEAS").target)
    }

    @Test
    fun capsTaskCommandStillTask() {
        assertEquals(CaptureTarget.TASK, UniversalCaptureEngine.interpret("CREAR UNA TAREA: COMPRAR LECHE").target)
    }

    @Test
    fun capsUnsignaledTextStillFallsBackToInbox() {
        val raw = "UNA IDEA SUELTA QUE TODAVÍA NO SÉ ORGANIZAR"
        val result = UniversalCaptureEngine.interpret(raw)
        assertEquals(CaptureTarget.INBOX, result.target)
        assertEquals(raw, result.body)
    }
}
