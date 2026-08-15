package com.ordia.app.assistant

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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

    @Test fun forgottenIntent_missedStartOffersReplan_toRescheduleForgottenCommitment() {
        // Simetría con las vencidas: "¿qué olvidé?" ante un compromiso cuyo hueco
        // se pasó (sin plazo vencido) NOMBRA el olvido, pero antes NO ofrecía
        // acción — decía "Hazla o reagéndala" con action=NONE, dejando al usuario
        // reagendar a mano. Las vencidas sí ofrecían RUN_REPLAN (replanDay hoy),
        // y replanDay ya recupera missed-start (DayPlanner, c.246). Así que el
        // camino de recuperación existe; faltaba exponerlo. Un toque "Replanificar"
        // debe reagendar el olvido a un hueco de hoy, igual que una vencida.
        val now = 1_000_000_000_000L
        val missedStart = TaskEntity(
            id = 7, title = "Revisión de contrato",
            startAt = now - 60 * 60_000L, // hace 1 h
            durationMinutes = 25,         // ventana terminó hace ~35 min → hueco pasado
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "¿qué olvidé?",
            listOf(missedStart),
            emptyList(), emptyList(),
            now
        )
        assertEquals(
            "un olvido reagendable debe ofrecer reprogramar, como una vencida: ${answer.text}",
            AssistantAction.RUN_REPLAN, answer.action
        )
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

    @Test fun forgottenIntent_namesMostUrgentOverdueTask() {
        // "¿Qué olvidé?" con vencidas: el usuario quiere recuperar QUÉ se le pasó.
        // Antes la rama overdue daba sólo un conteo ("2 vencidas") sin nombrar la
        // más urgente — justo la información de recuperación que pedía. La más
        // urgente (URGENT, dueAt más antiguo) debe nombrarse antes de ofrecer
        // reprogramar el resto. Simétrico con la rama sin-vencidas (que nombra el
        // missed-start) y con "qué hago ahora" (que nombra una).
        val now = 1_000_000_000_000L
        val urgent = TaskEntity(id = 1, title = "Entrega crítica", dueAt = now - 2 * 86_400_000L, priority = TaskPriority.URGENT)
        val low = TaskEntity(id = 2, title = "Regar plantas", dueAt = now - 86_400_000L)
        val answer = AssistantEngine.answer(
            "¿qué olvidé?",
            listOf(low, urgent),
            emptyList(), emptyList(),
            now
        )
        assertTrue("nombra la más urgente: ${answer.text}", answer.text.contains("Entrega crítica"))
        assertEquals("ofrece reprogramar las vencidas", AssistantAction.RUN_REPLAN, answer.action)
        assertTrue("incluye las vencidas: ${answer.relatedTaskIds}", answer.relatedTaskIds.containsAll(listOf(1L, 2L)))
    }

    @Test fun forgottenIntent_singleOverdue_namesItAndOffersReplan() {
        // Una sola vencida + "¿qué olvidé?": la nombra (no sólo "1 vencida") y
        // sigue ofreciendo reprogramar. Menos conteo frío, más recuperación.
        val now = 1_000_000_000_000L
        val overdue = TaskEntity(id = 5, title = "Pagar factura", dueAt = now - 3 * 86_400_000L)
        val answer = AssistantEngine.answer(
            "¿qué olvidé?",
            listOf(overdue),
            emptyList(), emptyList(),
            now
        )
        assertTrue("nombra la vencida olvidada: ${answer.text}", answer.text.contains("Pagar factura"))
        assertEquals(AssistantAction.RUN_REPLAN, answer.action)
        assertEquals(listOf(5L), answer.relatedTaskIds)
    }

    @Test fun overdueIntent_keepsCountMessageForPureVencidasQuery() {
        // "vencidas" (sin intención de olvido) sigue dando el conteo, no nombra:
        // la partición vencida/olvido se mantiene. No regresa del fix de forgotten.
        val now = 1_000_000_000_000L
        val overdue = TaskEntity(id = 5, title = "Pagar factura", dueAt = now - 3 * 86_400_000L)
        val answer = AssistantEngine.answer(
            "vencidas",
            listOf(overdue),
            emptyList(), emptyList(),
            now
        )
        assertTrue("mantiene el conteo para 'vencidas': ${answer.text}", answer.text.contains("1 tarea vencida"))
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

    @Test fun forgottenIntent_recoversStaleInboxCaptureWhenNoOverdueOrMissedStart() {
        // "¿Qué olvidé?" debe recuperar la captura arrinconada en la bandeja SIN
        // fecha, igual que el guardián (c.201): una idea capturada hace semanas y
        // nunca agendada ES un olvido. Antes decía "No tienes tareas vencidas ni
        // compromisos olvidados" frente a una captura olvidada — mentía por
        // omisión en la superficie de recuperación explícita. Simétrico con
        // GuardianCoach.insight (RECUPERA EL CONTROL) y con la rama de missed-start.
        val zone = java.time.ZoneId.of("America/Santo_Domingo")
        val today = java.time.LocalDate.of(2026, 7, 29)
        val now = com.ordia.app.domain.DateRules.toEpochMillis(today, java.time.LocalTime.NOON, zone)
        val stale = TaskEntity(
            id = 11, title = "Idea capturada hace 3 semanas",
            createdAt = com.ordia.app.domain.DateRules.toEpochMillis(today.minusDays(21), java.time.LocalTime.of(9, 0), zone)
        )
        val answer = AssistantEngine.answer(
            "¿qué olvidé?",
            listOf(stale),
            emptyList(), emptyList(),
            now,
            zone
        )
        assertTrue("nombra la captura olvidada: ${answer.text}", answer.text.contains("Idea capturada hace 3 semanas"))
        assertEquals(listOf(11L), answer.relatedTaskIds)
    }

    @Test fun forgottenIntent_staleInboxBelowThresholdNotFlaggedAsForgotten() {
        // Una captura de hace 6 días (< umbral de 7) NO es "olvidada": sigue
        // siendo una idea reciente. "¿qué olvidé?" no debe fingir olvido — la
        // edad de la bandeja sin fecha tiene más margen que una vencida (que
        // incumple un plazo). Guard anti-falso-positivo (simétrico GuardianCoach).
        val zone = java.time.ZoneId.of("America/Santo_Domingo")
        val today = java.time.LocalDate.of(2026, 7, 29)
        val now = com.ordia.app.domain.DateRules.toEpochMillis(today, java.time.LocalTime.NOON, zone)
        val recent = TaskEntity(
            id = 12, title = "Captura de hace 6 días",
            createdAt = com.ordia.app.domain.DateRules.toEpochMillis(today.minusDays(6), java.time.LocalTime.of(9, 0), zone)
        )
        val answer = AssistantEngine.answer(
            "¿qué olvidé?",
            listOf(recent),
            emptyList(), emptyList(),
            now,
            zone
        )
        assertTrue("no finge olvido de captura reciente: ${answer.text}",
            answer.text.contains("No tienes tareas vencidas") || answer.text.contains("olvidad"))
    }

    @Test fun overdueIntent_doesNotFlagStaleInboxAsOverdue() {
        // "vencidas" (sin intención de olvido) pregunta por vencidas (dueAt
        // pasado). Una captura arrinconada SIN fecha NO es vencida: la respuesta
        // debe seguir siendo "No tienes tareas vencidas" (partición honesta:
        // vencida ≠ captura olvidada en bandeja). No se simula urgencia.
        val zone = java.time.ZoneId.of("America/Santo_Domingo")
        val today = java.time.LocalDate.of(2026, 7, 29)
        val now = com.ordia.app.domain.DateRules.toEpochMillis(today, java.time.LocalTime.NOON, zone)
        val stale = TaskEntity(
            id = 13, title = "Idea capturada hace 3 semanas",
            createdAt = com.ordia.app.domain.DateRules.toEpochMillis(today.minusDays(21), java.time.LocalTime.of(9, 0), zone)
        )
        val answer = AssistantEngine.answer(
            "vencidas",
            listOf(stale),
            emptyList(), emptyList(),
            now,
            zone
        )
        assertTrue("no finge vencida una captura sin fecha: ${answer.text}", answer.text.contains("No tienes tareas vencidas"))
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

    @Test fun organizeDay_doesNotInflatePendingWithSubtasks() {
        // Coherencia entre superficies: SummaryEngine, GuardianEngine y WhatNow
        // cuentan SOLO tareas raíz (parentTaskId == null) porque las subtareas son
        // anidadas y contarlas además del padre infla los números. El asistente
        // debe hacer lo mismo: un padre con 2 subtareas pendientes es 1 tarea
        // pendiente, no 3. Antes el asistente mentía al usuario ("3 tareas
        // pendientes" por una sola descompuesta en 2 partes).
        val parent = TaskEntity(id = 1, title = "Proyecto grande")
        val subA = TaskEntity(id = 2, title = "Paso A", parentTaskId = 1)
        val subB = TaskEntity(id = 3, title = "Paso B", parentTaskId = 1)
        val answer = AssistantEngine.answer(
            "organiza mi dia",
            listOf(parent, subA, subB),
            emptyList(), emptyList()
        )
        assertTrue("cuenta 1 pendiente (raíz), no 3 con subtareas: ${answer.text}", answer.text.contains("1 tarea pendiente"))
    }

    @Test fun overdueIntent_doesNotInflateOverdueCountWithSubtasks() {
        // "vencidas" debe contar 1 raíz vencida, no 3 (raíz + 2 subtareas vencidas).
        // Las subtareas forman parte del mismo trabajo del padre: contarlas como
        // vencidas independientes miente sobre cuántos compromisos reales se pasaron.
        val now = 1_000_000_000_000L
        val parent = TaskEntity(id = 1, title = "Entrega vencida", dueAt = now - 1L)
        val subA = TaskEntity(id = 2, title = "Subtarea A", parentTaskId = 1, dueAt = now - 1L)
        val subB = TaskEntity(id = 3, title = "Subtarea B", parentTaskId = 1, dueAt = now - 1L)
        val answer = AssistantEngine.answer(
            "vencidas",
            listOf(parent, subA, subB),
            emptyList(), emptyList(),
            now
        )
        assertTrue("cuenta 1 vencida (raíz), no 3 con subtareas: ${answer.text}", answer.text.contains("1 tarea vencida"))
    }

    @Test fun whatNow_doesNotInflateOtherOverdueWithSubtasks() {
        // La sugerida es una raíz vencida; otra raíz vencida tiene 2 subtareas
        // vencidas. "Además, tienes N vencidas" debe contar 1 (la otra raíz), no 3
        // (la otra raíz + sus 2 subtareas). Contar las subtareas inflaría el nudge
        // y confundiría al usuario sobre cuántos compromisos quedan.
        val suggested = TaskEntity(id = 1, title = "Urgente atrasada", dueAt = 1L, priority = TaskPriority.URGENT)
        val otherRoot = TaskEntity(id = 2, title = "Otra atrasada", dueAt = 2L)
        val subA = TaskEntity(id = 3, title = "Subtarea A", parentTaskId = 2, dueAt = 2L)
        val subB = TaskEntity(id = 4, title = "Subtarea B", parentTaskId = 2, dueAt = 2L)
        val answer = AssistantEngine.answer(
            "¿Qué hago ahora?",
            listOf(suggested, otherRoot, subA, subB),
            emptyList(), emptyList()
        )
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertTrue("además cuenta 1 vencida (la otra raíz), no 3: ${answer.text}", answer.text.contains("1 vencida"))
        assertTrue("no infla con 3 vencidas: ${answer.text}", !answer.text.contains("3 vencid"))
    }

    // --- "¿qué tengo mañana/hoy?" — agenda a demanda (c.230) ---

    private fun tomorrowNoon(now: Long): Long {
        val zone = ZoneId.systemDefault()
        val tomorrow = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1)
        return tomorrow.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    }

    private fun todayNoon(now: Long): Long {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    }

    @Test fun queTengoManana_listsTasksDueTomorrow() {
        val now = 1_000_000_000_000L
        val tomorrow = tomorrowNoon(now)
        val answer = AssistantEngine.answer(
            "¿qué tengo mañana?",
            listOf(
                TaskEntity(id = 1, title = "Reunión de equipo", dueAt = tomorrow),
                TaskEntity(id = 2, title = "Llamar al médico", dueAt = tomorrow, priority = TaskPriority.URGENT),
                TaskEntity(id = 3, title = "Tarea de hoy", dueAt = todayNoon(now))
            ),
            emptyList(), emptyList(),
            now
        )
        assertTrue("nombra las de mañana: ${answer.text}", answer.text.contains("Reunión de equipo"))
        assertTrue("nombra la urgente de mañana: ${answer.text}", answer.text.contains("Llamar al médico"))
        assertTrue("no mezcla con la de hoy: ${answer.text}", !answer.text.contains("Tarea de hoy"))
        assertTrue("relaciona solo las de mañana: ${answer.relatedTaskIds}", answer.relatedTaskIds.containsAll(listOf(1L, 2L)))
        assertTrue("no incluye la de hoy en ids: ${answer.relatedTaskIds}", !answer.relatedTaskIds.contains(3L))
    }

    @Test fun queTengoManana_whenEmpty_saysSoHonestly() {
        val now = 1_000_000_000_000L
        val answer = AssistantEngine.answer(
            "¿qué tengo mañana?",
            listOf(TaskEntity(id = 1, title = "Solo de hoy", dueAt = todayNoon(now))),
            emptyList(), emptyList(),
            now
        )
        assertTrue("no inventa tareas: ${answer.text}", answer.text.contains("mañana") && answer.text.contains("no tienes"))
        assertTrue("sin ids inventados: ${answer.relatedTaskIds}", answer.relatedTaskIds.isEmpty())
    }

    @Test fun queTengoManana_excludesSubtasks() {
        val now = 1_000_000_000_000L
        val tomorrow = tomorrowNoon(now)
        val parent = TaskEntity(id = 1, title = "Proyecto", dueAt = tomorrow)
        val sub = TaskEntity(id = 2, title = "Paso", parentTaskId = 1, dueAt = tomorrow)
        val answer = AssistantEngine.answer(
            "¿qué tengo para mañana?",
            listOf(parent, sub),
            emptyList(), emptyList(),
            now
        )
        assertTrue("cuenta solo la raíz, no la subtarea: ${answer.text}", answer.text.contains("Proyecto"))
        assertTrue("no infla con la subtarea: ${answer.text}", !answer.text.contains("Paso"))
        assertEquals(listOf(1L), answer.relatedTaskIds)
    }

    @Test fun queTengoHoy_listsTodaysTasksAndMentionsEarlierOverdue() {
        val now = 1_000_000_000_000L
        val today = todayNoon(now)
        val earlierOverdue = now - 3 * 86_400_000L // vencida hace 3 días (día anterior)
        val answer = AssistantEngine.answer(
            "¿qué tengo hoy?",
            listOf(
                TaskEntity(id = 1, title = "Cita médica", dueAt = today),
                TaskEntity(id = 2, title = "Entrega vieja", dueAt = earlierOverdue)
            ),
            emptyList(), emptyList(),
            now
        )
        assertTrue("nombra la de hoy: ${answer.text}", answer.text.contains("Cita médica"))
        assertTrue("avisa de las atrasadas previas: ${answer.text}", answer.text.contains("atrasada"))
    }

    @Test fun queTengoManana_keepsQuickPhrasesWorking() {
        // "qué hago ahora" y "plan mínimo" no deben romperse tras añadir el intent
        // de agenda: siguen su camino propio.
        val now = 1_000_000_000_000L
        val whatNow = AssistantEngine.answer(
            "¿qué hago ahora?",
            listOf(TaskEntity(id = 1, title = "X", priority = TaskPriority.URGENT)),
            emptyList(), emptyList(),
            now
        )
        assertEquals(listOf(1L), whatNow.relatedTaskIds)
        val plan = AssistantEngine.answer(
            "plan mínimo para hoy",
            listOf(TaskEntity(id = 1, title = "X")),
            emptyList(), emptyList(),
            now
        )
        assertEquals(listOf(1L), plan.relatedTaskIds)
    }

    @Test fun queTengoEstaSemana_listsTasksWithinIsoWeek() {
        // "esta semana" = semana ISO lun→dom que contiene hoy. Una tarea cuyo
        // vencimiento cae dentro de esa ventana aparece; una de la semana siguiente,
        // no. Determinista y honesto (no inventa nada, no mezcla semanas).
        val zone = ZoneId.systemDefault()
        val now = 1_000_000_000_000L
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val inWeek = monday.plusDays(2).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val nextWeek = monday.plusDays(9).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val answer = AssistantEngine.answer(
            "¿qué tengo esta semana?",
            listOf(
                TaskEntity(id = 1, title = "Médico", dueAt = inWeek),
                TaskEntity(id = 2, title = "Próxima semana", dueAt = nextWeek)
            ),
            emptyList(), emptyList(),
            now
        )
        assertTrue("nombra la de esta semana: ${answer.text}", answer.text.contains("Médico"))
        assertTrue("no incluye la de la próxima: ${answer.text}", !answer.text.contains("Próxima semana"))
        assertEquals(listOf(1L), answer.relatedTaskIds)
    }

    // ---- Veredicto del día a demanda ("¿voy bien?"/"¿da tiempo a todo?") ----
    //
    // Ordía YA calcula el veredicto del día (SummaryEngine.dayLoad:
    // LIGHT/ON_TRACK/FULL/OVERLOADED) y, bajo OVERLOADED, nombra la tarea de
    // hoy más posponible (deferralSuggestion). Pero esa inteligencia sólo
    // estaba en la tarjeta de resumen: el asistente a demanda caía al mensaje
    // genérico. Estos tests anclan que preguntar "¿voy bien?" / "¿da tiempo a
    // todo?" / "¿tengo mucho que hacer?" expone ese veredicto (y, cuando el día
    // no da para más, nombra QUÉ mover a mañana en vez de dejar al usuario
    // mirando una agenda saturada sin saber qué soltar).

    private val dayZone = ZoneId.of("America/Santo_Domingo")
    private val dayToday = LocalDate.of(2026, 7, 29)

    private fun dayAt(date: LocalDate, hour: Int): Long =
        date.atTime(hour, 0).atZone(dayZone).toInstant().toEpochMilli()

    @Test fun dayLoad_onTrack_saysGoingWell() {
        // 9:00 → 540 min libres; 90 min de hoy → ON_TRACK (cabe con holgura).
        val now = dayAt(dayToday, 9)
        val answer = AssistantEngine.answer(
            "¿voy bien hoy?",
            listOf(
                TaskEntity(id = 1, title = "A", dueAt = dayAt(dayToday, 11), durationMinutes = 45),
                TaskEntity(id = 2, title = "B", dueAt = dayAt(dayToday, 15), durationMinutes = 45)
            ),
            emptyList(), emptyList(),
            now, dayZone
        )
        assertTrue("explica que va con holgura: ${answer.text}", answer.text.contains("holgura"))
    }

    @Test fun dayLoad_full_saysDayIsFull() {
        // 12:00 → 360 min libres; 60 de hoy + 240 vencidas = 300 → FULL (180<300≤360).
        val now = dayAt(dayToday, 12)
        val answer = AssistantEngine.answer(
            "¿da tiempo a todo?",
            listOf(
                TaskEntity(id = 1, title = "Hoy", dueAt = dayAt(dayToday, 17), durationMinutes = 60),
                TaskEntity(id = 2, title = "Vencida1", dueAt = dayAt(dayToday.minusDays(1), 9), durationMinutes = 120),
                TaskEntity(id = 3, title = "Vencida2", dueAt = dayAt(dayToday.minusDays(2), 9), durationMinutes = 120)
            ),
            emptyList(), emptyList(),
            now, dayZone
        )
        assertTrue("explica que el día está lleno/justo: ${answer.text}",
            answer.text.contains("lleno") || answer.text.contains("justo"))
    }

    @Test fun dayLoad_overloaded_namesDeferralCandidate() {
        // 12:00 → 360 libres. 1 tarea LOW de hoy (60) + 5 vencidas de 120 →
        // OVERLOADED; la sugerencia nombra la de hoy (no vencida) para mover.
        val now = dayAt(dayToday, 12)
        val answer = AssistantEngine.answer(
            "tengo mucho que hacer",
            listOf(
                TaskEntity(id = 1, title = "Posponerme", dueAt = dayAt(dayToday, 17), durationMinutes = 60, priority = TaskPriority.LOW),
                TaskEntity(id = 2, title = "V1", dueAt = dayAt(dayToday.minusDays(1), 9), durationMinutes = 120),
                TaskEntity(id = 3, title = "V2", dueAt = dayAt(dayToday.minusDays(2), 9), durationMinutes = 120),
                TaskEntity(id = 4, title = "V3", dueAt = dayAt(dayToday.minusDays(3), 9), durationMinutes = 120),
                TaskEntity(id = 5, title = "V4", dueAt = dayAt(dayToday.minusDays(4), 9), durationMinutes = 120),
                TaskEntity(id = 6, title = "V5", dueAt = dayAt(dayToday.minusDays(5), 9), durationMinutes = 120)
            ),
            emptyList(), emptyList(),
            now, dayZone
        )
        assertTrue("dice que no da tiempo a todo: ${answer.text}", answer.text.contains("no da tiempo") || answer.text.contains("No da tiempo"))
        assertTrue("nombra la candidata a posponer: ${answer.text}", answer.text.contains("Posponerme"))
        assertEquals("relaciona exactamente la tarea sugerida", listOf(1L), answer.relatedTaskIds)
    }

    @Test fun dayLoad_light_saysDayIsClear() {
        // Sin trabajo que compita por la jornada → LIGHT.
        val now = dayAt(dayToday, 9)
        val answer = AssistantEngine.answer(
            "¿voy bien hoy?",
            emptyList(),
            emptyList(), emptyList(),
            now, dayZone
        )
        assertTrue("dice que el día está despejado: ${answer.text}", answer.text.contains("despejado"))
    }

    @Test fun dayLoad_doesNotSwallowOrganizeDayIntent() {
        // Regresión: el nuevo intent de carga no debe robar "organiza mi día".
        val answer = AssistantEngine.answer("organiza mi día", emptyList(), emptyList(), emptyList())
        assertEquals(AssistantAction.OPEN_PLANNER, answer.action)
    }

    @Test fun dayLoad_doesNotSwallowWhatNowIntent() {
        // Regresión: "¿qué hago ahora?" sigue dando la siguiente tarea, no el veredicto.
        val answer = AssistantEngine.answer(
            "¿Qué hago ahora?",
            listOf(TaskEntity(id = 1, title = "Urgente", priority = TaskPriority.URGENT)),
            emptyList(), emptyList()
        )
        assertEquals(listOf(1L), answer.relatedTaskIds)
    }
}
