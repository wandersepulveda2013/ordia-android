package com.ordia.app.assistant

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantEngineTest {
    @Test fun blankRequest_isHelpfulAndOffline() {
        val answer = AssistantEngine.answer("", emptyList(), emptyList(), emptyList())
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.text.isNotBlank())
    }

    @Test fun whatNow_usesRealPriority() {
        val answer = AssistantEngine.answer(
            "¿Qué hago ahora?",
            listOf(TaskEntity(id = 1, title = "Normal"), TaskEntity(id = 2, title = "Urgente", priority = TaskPriority.URGENT)),
            emptyList(), emptyList()
        )
        assertEquals(listOf(2L), answer.relatedTaskIds)
    }

    @Test fun whatNow_explainsWhyAndMentionsOverdue() {
        // La sugerida es la vencida → "está vencida" ya lo dice; el "además"
        // no debe contarla (evita "además tienes 1 vencida" = la misma).
        val overdue = TaskEntity(id = 1, title = "Atrasada", dueAt = 1L)
        val answer = AssistantEngine.answer(
            "¿Qué hago ahora?",
            listOf(overdue, TaskEntity(id = 2, title = "Normal")),
            emptyList(), emptyList()
        )
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertTrue("explica la razón: ${answer.text}", answer.text.contains("vencida"))
        assertTrue("no repite la vencida como 'además': ${answer.text}", !answer.text.contains("Además"))
    }

    @Test fun whatNow_mentionsOtherOverdueWhenSuggestedIsAlsoOverdue() {
        // Sugerida vencida + otra vencida distinta → "además tienes 1 vencida".
        val suggested = TaskEntity(id = 1, title = "Atrasada", dueAt = 1L, priority = TaskPriority.URGENT)
        val other = TaskEntity(id = 2, title = "Otra atrasada", dueAt = 2L)
        val answer = AssistantEngine.answer(
            "¿Qué hago ahora?",
            listOf(suggested, other),
            emptyList(), emptyList()
        )
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertTrue("menciona la otra vencida: ${answer.text}", answer.text.contains("1 vencida"))
    }

    @Test fun planMinimo_ranksOverdueFirst() {
        val normal = TaskEntity(id = 1, title = "Normal", priority = TaskPriority.HIGH)
        val overdue = TaskEntity(id = 2, title = "Atrasada", dueAt = 1L)
        val answer = AssistantEngine.answer(
            "plan mínimo para hoy",
            listOf(normal, overdue),
            emptyList(), emptyList()
        )
        assertEquals(listOf(2L, 1L), answer.relatedTaskIds)
    }

    @Test fun quickTasks_rankOverdueFirst() {
        val normal = TaskEntity(id = 1, title = "Trámite corto", durationMinutes = 10)
        val overdue = TaskEntity(id = 2, title = "Llamada atrasada", dueAt = 1L, durationMinutes = 10)
        val answer = AssistantEngine.answer(
            "tareas de 15 minutos",
            listOf(normal, overdue),
            emptyList(), emptyList()
        )
        assertEquals(listOf(2L, 1L), answer.relatedTaskIds)
    }

    @Test fun whatNow_estimatesClampedDurationForZeroDurationTask() {
        val answer = AssistantEngine.answer(
            "¿Qué hago ahora?",
            listOf(TaskEntity(id = 1, title = "Sin duración", durationMinutes = 0)),
            emptyList(), emptyList()
        )
        assertTrue("usa el mínimo planificado, no 0: ${answer.text}", answer.text.contains("10 minutos"))
        assertTrue("no dice ' 0 minutos': ${answer.text}", !answer.text.contains(" 0 minutos"))
    }

    @Test fun whatNow_estimatesClampedDurationForOversizedTask() {
        val answer = AssistantEngine.answer(
            "¿Qué hago ahora?",
            listOf(TaskEntity(id = 1, title = "Maratón", durationMinutes = 600)),
            emptyList(), emptyList()
        )
        assertTrue("acota al máximo planificado: ${answer.text}", answer.text.contains("180 minutos"))
        assertTrue("no dice 600 minutos: ${answer.text}", !answer.text.contains("600 minutos"))
    }

    @Test fun createNote_requiresContentAndThenOffersAction() {
        assertEquals(AssistantAction.NONE, AssistantEngine.answer("Guardar como nota", emptyList(), emptyList(), emptyList()).action)
        assertEquals(AssistantAction.CREATE_NOTE, AssistantEngine.answer("Guardar como nota: idea privada", emptyList(), emptyList(), emptyList()).action)
    }

    @Test fun forgottenIntent_namesMissedStartCommitmentWhenNoOverdue() {
        // "¿Qué olvidé?" no debe mentir por omisión: un compromiso agendado cuyo
        // hueco ya pasó (sin dueAt vencido) ES un olvido — el "olvido silencioso"
        // de TaskRules.isMissedStart. Antes decía "No tienes tareas vencidas"
        // aunque el usuario tuviera una llamada agendada que se le pasó.
        val now = 1_000_000_000_000L // 2001-09-09 ~01:46 UTC
        val missedStart = TaskEntity(
            id = 1, title = "Llamada agendada",
            startAt = now - 90 * 60_000L, // empezó hace 90 min
            durationMinutes = 30,          // ventana terminó hace ~60 min → hueco pasado
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "¿qué olvidé?",
            listOf(missedStart),
            emptyList(), emptyList(),
            now
        )
        // La tarea de inicio olvidado debe nombrarse, no descartarse como "sin vencidas".
        assertTrue("nombra el compromiso olvidado: ${answer.text}", answer.text.contains("Llamada agendada"))
        assertTrue("describe el olvido honestamente: ${answer.text}", answer.text.contains("se pasó"))
        assertEquals(listOf(1L), answer.relatedTaskIds)
    }

    @Test fun forgottenIntent_missedStartStartAfterWindowPassedIsNamed() {
        // Caso limpio de isMissedStart: el hueco ya terminó (start + duración < now)
        // y no hay dueAt vencido. "¿Qué olvidé?" debe recuperarlo.
        val now = 1_000_000_000_000L
        val missedStart = TaskEntity(
            id = 7, title = "Revisión de contrato",
            startAt = now - 60 * 60_000L, // hace 1 h
            durationMinutes = 25,          // ventana terminó hace ~35 min
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "¿qué olvidé?",
            listOf(missedStart),
            emptyList(), emptyList(),
            now
        )
        assertTrue("recupera el compromiso cuyo hueco pasó: ${answer.text}", answer.text.contains("Revisión de contrato"))
        assertEquals(listOf(7L), answer.relatedTaskIds)
    }

    @Test fun overdueIntent_doesNotPretendMissedStartIsOverdue() {
        // "vencidas" pregunta por vencidas (dueAt pasado). Un compromiso cuyo hueco
        // pasó (isMissedStart genuino: start+duración < now, sin dueAt vencido) NO es
        // vencida: la respuesta debe seguir siendo "No tienes tareas vencidas"
        // (honestidad: vencida ≠ hueco olvidado). No se simula urgencia.
        val now = 1_000_000_000_000L
        val missedStart = TaskEntity(
            id = 3, title = "Llamada agendada",
            startAt = now - 90 * 60_000L, // empezó hace 90 min
            durationMinutes = 30,         // ventana terminó hace ~60 min → hueco pasado
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "vencidas",
            listOf(missedStart),
            emptyList(), emptyList(),
            now
        )
        assertTrue("no finge vencida una tarea de hueco olvidado: ${answer.text}", answer.text.contains("No tienes tareas vencidas"))
    }

    @Test fun forgottenIntent_whenNothingForgotten_saysSoHonestly() {
        // Sin vencidas ni missed-start: "¿qué olvidé?" no debe inventar nada.
        val now = 1_000_000_000_000L
        val plain = TaskEntity(id = 1, title = "Normal sin fecha")
        val answer = AssistantEngine.answer(
            "¿qué olvidé?",
            listOf(plain),
            emptyList(), emptyList(),
            now
        )
        assertTrue("no inventa olvidos: ${answer.text}", answer.text.contains("No tienes tareas vencidas") || answer.text.contains("olvidad"))
    }

    @Test fun cancelledTaskIsNotCountedAsPending() {
        // Una tarea cancelada no debe contarse como pendiente al organizar el
        // día ni aparecer en el plan mínimo: el usuario ya la descartó.
        // Coherente con TaskRules/ReminderSync, que excluyen CANCELLED.
        val cancelled = TaskEntity(
            id = 1, title = "Cancelada",
            status = com.ordia.app.data.local.TaskStatus.CANCELLED
        )
        val plan = AssistantEngine.answer("plan mínimo para hoy", listOf(cancelled), emptyList(), emptyList())
        assertTrue("plan mínimo vacío sin contar la cancelada: ${plan.text}", plan.relatedTaskIds.isEmpty())

        val organize = AssistantEngine.answer("organiza mi dia", listOf(cancelled), emptyList(), emptyList())
        assertTrue("no cuenta la cancelada como pendiente: ${organize.text}", organize.text.contains("0 tareas pendientes"))
    }
}
