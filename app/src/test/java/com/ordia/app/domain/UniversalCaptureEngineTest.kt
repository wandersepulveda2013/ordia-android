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

    // Los comandos de captura ("nota", "idea", "tarea") deben reconocerse solo
    // como palabras completas: "ideal", "idear", "notas", "tareas", "notario"
    // son contenido legítimo, no comandos. Antes el regex hacía coincidencia
    // por prefijo y mutaba el texto ("ideal proyecto" → nota "l proyecto",
    // "tareas de casa" → tarea "s de casa"). La captura nunca debe dañar datos.
    @Test fun contentStartingWithCommandWordPrefixIsNotCorrupted() {
        val ideal = UniversalCaptureEngine.interpret("ideal proyecto")
        assertEquals("ideal proyecto", ideal.title)
        assertEquals("ideal proyecto", ideal.body)

        val idear = UniversalCaptureEngine.interpret("idear una solución")
        assertEquals("idear una solución", idear.title)

        val notas = UniversalCaptureEngine.interpret("notas musicales")
        assertEquals("notas musicales", notas.title)
        assertEquals("notas musicales", notas.body)

        val tareas = UniversalCaptureEngine.interpret("tareas de casa")
        assertEquals("tareas de casa", tareas.title)
        assertEquals("tareas de casa", tareas.body)

        val notario = UniversalCaptureEngine.interpret("notario público")
        assertEquals("notario público", notario.title)
    }

    @Test fun singularCommandWordStillStripsAsCommand() {
        val note = UniversalCaptureEngine.interpret("nota importante")
        assertEquals(CaptureTarget.NOTE, note.target)
        assertEquals("importante", note.title)

        val idea = UniversalCaptureEngine.interpret("idea: comprar pan")
        assertEquals(CaptureTarget.NOTE, idea.target)
        assertEquals("comprar pan", idea.title)

        val tarea = UniversalCaptureEngine.interpret("tarea: llamar al dentista")
        assertEquals(CaptureTarget.TASK, tarea.target)
        assertEquals("llamar al dentista", tarea.title)
    }

    // c.583: verbos de acción cotidiana sin fecha ni "tengo que/debo" caían a
    // INBOX y exigían reclasificar a mano. "hacer"/"revisar" están entre los
    // verbos de tarea más frecuentes en español; su ausencia era fricción real.
    // Ahora los infinitivos inequívocos (hacer, revisar, preparar, limpiar,
    // avisar, pedir, leer, escribir, cocinar, ir a/al...) promueven a TASK.
    @Test fun accionVerbalInfinitivoSinFechaEsTask() {
        val casos = listOf(
            "hacer ejercicio",
            "revisar contrato",
            "preparar la cena",
            "limpiar la cocina",
            "avisar a mamá",
            "pedir cita",
            "leer el informe",
            "escribir el reporte",
            "cocinar",
            "ir al médico",
            "ir a la escuela",
            "mandar email"
        )
        for (raw in casos) {
            val r = UniversalCaptureEngine.interpret(raw)
            assertEquals("'$raw' debería ser TASK, no ${r.target}", CaptureTarget.TASK, r.target)
            assertEquals(raw, r.body)
        }
    }

    // La promoción a TASK por verbo NO muta el texto: la captura nunca daña datos.
    @Test fun accionVerbalPreservaTituloYBody() {
        val r = UniversalCaptureEngine.interpret("revisar contrato")
        assertEquals("revisar contrato", r.title)
        assertEquals("revisar contrato", r.body)
    }

    // Texto NO accionable sigue en INBOX: la heurística discrimina, no infla TASK.
    @Test fun textoNoAccionableSigueEnInbox() {
        val r = UniversalCaptureEngine.interpret("Una idea suelta sin organizar")
        assertEquals(CaptureTarget.INBOX, r.target)
    }
}
