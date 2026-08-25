package com.ordia.app.assistant

import com.ordia.app.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * c.1109 — aguja de entity-lookup limpia el relleno «es lo del/la», «es la/el».
 * Pre-fix (sonda efímera /tmp, 7/7 fallando): «¿cuándo es lo del dentista?»
 * buscaba «es lo del dentista» por SUBCADENA del título → «No encuentro nada
 * que sea …» aunque la tarea existía (mentira por omisión). El fix recorta el
 * envoltorio determinante (es / lo del / de / la / el…) de la aguja Y del
 * casefold del título (paridad «Lo del dentista»), sin tocar sustantivos.
 * NOTA de fixtures: ningún título empieza por un determinante cortable en la
 * posición del PIN, para que la arquitectura de coincidencia pre-c.1109 ya lo
 * hubiera resuelto igual (si el PIN cambia, el corte nuevo es responsable).
 */
class AssistantEngineEntityNeedleFillerTest {

    private val zone: ZoneId = ZoneId.of("America/Santo_Domingo")
    private val now: Long =
        ZonedDateTime.of(2026, 8, 26, 10, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    private fun ask(q: String, tasks: List<TaskEntity>): AssistantAnswer =
        AssistantEngine.answer(q, tasks, emptyList(), emptyList(), now = now, zone = zone)

    // ---- GAPS pre-fix (relleno envenenaba la subcadena) ----

    @Test fun cuandoEsLoDel_resuelveEntidadExistente() {
        val tasks = listOf(TaskEntity(id = 1, title = "Cita con el dentista", dueAt = at(2026, 8, 28, 16, 0)))
        val answer = ask("¿cuándo es lo del dentista?", tasks)
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertFalse("no miente 'no encuentro': ${answer.text}", answer.text.startsWith("No encuentro"))
    }

    @Test fun aQueHoraEsLoDel_resuelveEntidadExistente() {
        val tasks = listOf(TaskEntity(id = 1, title = "Cita con el dentista", dueAt = at(2026, 8, 28, 16, 0)))
        val answer = ask("¿a qué hora es lo del dentista?", tasks)
        assertEquals(listOf(1L), answer.relatedTaskIds)
    }

    @Test fun cuandoEsLa_resuelveEntidadExistente() {
        val tasks = listOf(TaskEntity(id = 1, title = "Reunión con Ana", dueAt = at(2026, 8, 27, 11, 0)))
        val answer = ask("¿cuándo es la reunión?", tasks)
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertFalse(answer.text.startsWith("No encuentro"))
    }

    @Test fun queFechaEsLoDel_resuelveEntidadExistente() {
        val tasks = listOf(TaskEntity(id = 1, title = "Cita con el dentista", dueAt = at(2026, 8, 28, 16, 0)))
        val answer = ask("¿qué fecha es lo del dentista?", tasks)
        assertEquals(listOf(1L), answer.relatedTaskIds)
    }

    @Test fun dondeEsLoDeLa_resuelveEntidadExistente() {
        val tasks = listOf(TaskEntity(id = 1, title = "Recoger lo de la tintorería", dueAt = at(2026, 8, 27, 12, 0)))
        val answer = ask("¿dónde es lo de la tintorería?", tasks)
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertFalse(answer.text.startsWith("No encuentro"))
    }

    // ---- Paridad de envoltorio en el título (el casefold también se corta) ----

    @Test fun tituloConLoDel_sigueResolviendoConRellenoEnConsulta() {
        val tasks = listOf(TaskEntity(id = 1, title = "Lo del dentista", dueAt = at(2026, 8, 28, 16, 0)))
        val answer = ask("¿cuándo es lo del dentista?", tasks)
        assertEquals("paridad: aguja limpia + casefold limpio", listOf(1L), answer.relatedTaskIds)
        assertFalse(answer.text.startsWith("No encuentro"))
    }

    @Test fun tituloConLoDel_noSecuestraAlBuscarEntidadLimpia() {
        // La paridad no debe hacer que «lo del» desaparezca del título a la
        // hora de competir: «Lo del dentista» (casefold limpio «dentista») no
        // puede ganar a la consulta limpia «cita con el dentista».
        val tasks = listOf(
            TaskEntity(id = 1, title = "Lo del dentista", dueAt = at(2026, 8, 28, 16, 0)),
            TaskEntity(id = 2, title = "Cita con el dentista", dueAt = at(2026, 8, 28, 18, 0))
        )
        val answer = ask("¿cuándo es la cita con el dentista?", tasks)
        assertEquals(listOf(2L), answer.relatedTaskIds)
    }

    // ---- Ausentes: aguja limpia en el mensaje (adiós «es la sesion») ----

    @Test fun ausente_mensajeConAgujaLimpiaSinRelleno() {
        val answer = ask("¿cuándo es la sesión de fotos?", emptyList())
        assertTrue("cita la aguja limpia: ${answer.text}", answer.text.contains("«sesion de fotos»"))
        assertFalse("sin relleno: ${answer.text}", answer.text.contains("«es "))
    }

    // ---- PINS: lo que ya funcionaba no se mueve ----

    @Test fun pin_aQueHoraTengoLaReunion() {
        val tasks = listOf(TaskEntity(id = 1, title = "Reunión con Ana", startAt = at(2026, 8, 27, 11, 0)))
        val answer = ask("¿a qué hora tengo la reunión?", tasks)
        assertEquals(listOf(1L), answer.relatedTaskIds)
    }

    @Test fun pin_aQueHoraEsLaCena() {
        val tasks = listOf(TaskEntity(id = 1, title = "Cena de empresa", dueAt = at(2026, 8, 29, 21, 0)))
        val answer = ask("¿a qué hora es la cena de empresa?", tasks)
        assertEquals(listOf(1L), answer.relatedTaskIds)
    }

    @Test fun pin_cuandoPagoLaLuz() {
        val tasks = listOf(TaskEntity(id = 1, title = "Pagar luz", dueAt = at(2026, 9, 15, 12, 0)))
        val answer = ask("¿cuándo pago la luz?", tasks)
        assertEquals(listOf(1L), answer.relatedTaskIds)
    }

    @Test fun pin_cuandoTengoLaCita() {
        val tasks = listOf(TaskEntity(id = 1, title = "Cita con el dentista", dueAt = at(2026, 8, 28, 16, 0)))
        val answer = ask("¿cuándo tengo la cita con el dentista?", tasks)
        assertEquals(listOf(1L), answer.relatedTaskIds)
    }

    @Test fun pin_cuandoEsPelado_caeAlFallbackDeSiempre() {
        // Aguja vacía → NO es entity-lookup; sigue cayendo al fallback de fecha
        // más próxima (comportamiento preexistente, no inventado por c.1109).
        val tasks = listOf(
            TaskEntity(id = 1, title = "Cena de empresa", dueAt = at(2026, 8, 29, 21, 0)),
            TaskEntity(id = 2, title = "Lejos", dueAt = at(2026, 9, 10, 9, 0))
        )
        val answer = ask("¿cuándo es?", tasks)
        assertEquals(listOf(1L), answer.relatedTaskIds)
    }

    @Test fun pin_agendaYWhatNowIntactos() {
        val tasks = listOf(TaskEntity(id = 1, title = "Tarea para mañana", dueAt = at(2026, 8, 27, 9, 0)))
        val agenda = ask("¿qué tengo mañana?", tasks)
        assertTrue("agenda no secuestrada: ${agenda.text}", agenda.text.startsWith("Mañana:"))
        val whatNow = ask("¿qué hago ahora?", tasks)
        assertFalse(whatNow.text.startsWith("No encuentro"))
    }

    @Test fun pin_verboSustantivadoInicial_noSeCorta() {
        // «es la marcha»: «marcha» aquí es sustantivo; la aguja limpia es
        // «marcha» (se corta «es la »), pero una tarea «Marcha blanca» sigue
        // resolviendo y ningún token real desaparece.
        val tasks = listOf(TaskEntity(id = 1, title = "Marcha blanca", dueAt = at(2026, 8, 28, 9, 0)))
        val answer = ask("¿cuándo es la marcha?", tasks)
        assertEquals(listOf(1L), answer.relatedTaskIds)
    }
}
