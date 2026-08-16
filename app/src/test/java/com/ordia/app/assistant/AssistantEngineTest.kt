package com.ordia.app.assistant

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // --- Cuarto olvido: un compromiso vencido de una conversación. Antes el
    // asistente contaba los PENDING sin mirar `dueAt`, así que "¿qué olvidé?"
    // / "vencidas" decía "no hay vencidas ni olvidadas" frente a una promesa
    // vencida sin convertir — mentía por omisión en la superficie de
    // recuperación. Un compromiso no es una tarea (no se reprograma, se
    // convierte o descarta), así que se recupera abriendo Conversaciones.

    private fun overdueCommitment(
        id: Long,
        action: String,
        dueAt: Long,
        status: com.ordia.app.data.local.CommitmentReviewStatus = com.ordia.app.data.local.CommitmentReviewStatus.PENDING
    ) = com.ordia.app.data.local.CommitmentEntity(
        id = id, conversationId = 1,
        kind = com.ordia.app.data.local.CommitmentKind.SELF_COMMITMENT,
        owner = com.ordia.app.data.local.CommitmentOwner.SELF,
        actor = "yo", action = action, dueAt = dueAt, confidence = 0.9f,
        reviewStatus = status, fingerprint = "fp$id", createdAt = dueAt - 1
    )

    @Test fun forgottenIntent_recoversOverdueCommitmentWhenNoTaskOverdue() {
        // Sin tareas vencidas/olvidadas, pero con una promesa vencida: "¿qué
        // olvidé?" nombra el compromiso y abre Conversaciones. No dice "no hay
        // olvidados" frente a una promesa vencida.
        val zone = java.time.ZoneId.of("America/Santo_Domingo")
        val today = java.time.LocalDate.of(2026, 7, 29)
        val now = com.ordia.app.domain.DateRules.toEpochMillis(today, java.time.LocalTime.NOON, zone)
        val overdueDue = com.ordia.app.domain.DateRules.toEpochMillis(today.minusDays(3), java.time.LocalTime.of(10, 0), zone)
        val commitment = overdueCommitment(1, "te llamo el martes", overdueDue)
        val answer = AssistantEngine.answer(
            "¿qué olvidé?",
            listOf(TaskEntity(id = 1, title = "Normal sin fecha")),
            emptyList(), listOf(commitment),
            now, zone
        )
        assertTrue("nombra el compromiso vencido: ${answer.text}", answer.text.contains("te llamo el martes"))
        assertTrue("marca como compromiso vencido: ${answer.text}", answer.text.contains("compromiso vencido"))
        assertEquals(AssistantAction.OPEN_CONVERSATIONS, answer.action)
    }

    @Test fun vencidas_recoversOverdueCommitmentWhenNoTaskOverdue() {
        // "vencidas" (sin intención de olvido) también debe recuperar la
        // promesa vencida: un compromiso vencido ES vencido. No dice "No tienes
        // tareas vencidas" frente a una promesa vencida.
        val zone = java.time.ZoneId.of("America/Santo_Domingo")
        val today = java.time.LocalDate.of(2026, 7, 29)
        val now = com.ordia.app.domain.DateRules.toEpochMillis(today, java.time.LocalTime.NOON, zone)
        val overdueDue = com.ordia.app.domain.DateRules.toEpochMillis(today.minusDays(2), java.time.LocalTime.of(10, 0), zone)
        val commitment = overdueCommitment(2, "envío el informe", overdueDue)
        val answer = AssistantEngine.answer(
            "vencidas",
            emptyList(),
            emptyList(), listOf(commitment),
            now, zone
        )
        assertTrue("nombra el compromiso vencido: ${answer.text}", answer.text.contains("envío el informe"))
        assertEquals(AssistantAction.OPEN_CONVERSATIONS, answer.action)
    }

    @Test fun forgottenIntent_appendsOverdueCommitmentTailWhenTaskOverdue() {
        // Con una tarea vencida Y una promesa vencida: nombra la tarea (RUN_REPLAN)
        // y NO calla el compromiso —lo menciona para no ocultarlo tras la tarea.
        val now = 1_000_000_000_000L
        val today = todayNoon(now)
        val overdueTask = TaskEntity(id = 1, title = "Entrega vieja", dueAt = now - 3 * 86_400_000L)
        val commitment = overdueCommitment(5, "te llamo el martes", now - 2 * 86_400_000L)
        val answer = AssistantEngine.answer(
            "¿qué olvidé?",
            listOf(overdueTask),
            emptyList(), listOf(commitment),
            now
        )
        assertTrue("nombra la tarea vencida: ${answer.text}", answer.text.contains("Entrega vieja"))
        assertTrue("no calla el compromiso vencido: ${answer.text}", answer.text.contains("compromiso vencido"))
        assertEquals(AssistantAction.RUN_REPLAN, answer.action)
    }

    @Test fun forgottenIntent_namesMissedStartCoexistingWithOverdue() {
        // "¿Qué olvidé?" con una vencida Y un missed-start DISTINTO: ambos son
        // olvidos. Antes la rama overdue nombraba SÓLO la vencida y silenciaba
        // el compromiso cuyo hueco pasó (no vencido) — el mismo olvido silencioso
        // de c.345, ahora en la superficie explícita de recuperación. No debe
        // esconder un olvido tras otro cuando el usuario pregunta por QUÉ olvidó.
        val now = 1_000_000_000_000L
        val overdueTask = TaskEntity(id = 1, title = "Entrega vieja", dueAt = now - 3 * 86_400_000L)
        val missedStart = TaskEntity(
            id = 7, title = "Reunión de seguimiento",
            startAt = now - 60 * 60_000L, // hace 1 h
            durationMinutes = 25,         // ventana terminó hace ~35 min → hueco pasado
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "¿qué olvidé?",
            listOf(overdueTask, missedStart),
            emptyList(), emptyList(),
            now
        )
        assertTrue("nombra la vencida: ${answer.text}", answer.text.contains("Entrega vieja"))
        assertTrue("también recupera el missed-start: ${answer.text}", answer.text.contains("Reunión de seguimiento"))
    }

    @Test fun forgottenIntent_doesNotRepeatMissedStartWhenItIsTheOverdue() {
        // Guard anti-repetición: isMissedStart excluye a isOverdue (TaskRules l.196),
        // así una tarea vencida nunca es missed-start. Si la única "olvidada" es la
        // propia vencida, la cola no debe duplicar "Además, «X» se pasó" tras
        // nombrarla como vencida. IA honesta: no infla ni repite.
        val now = 1_000_000_000_000L
        val overdueOnly = TaskEntity(id = 1, title = "Entrega vieja", dueAt = now - 3 * 86_400_000L)
        val answer = AssistantEngine.answer(
            "¿qué olvidé?",
            listOf(overdueOnly),
            emptyList(), emptyList(),
            now
        )
        assertFalse("no repite el olvido como cola: ${answer.text}", answer.text.contains("Además, «Entrega vieja»"))
    }

    @Test fun resumeConversacion_flagsOverdueCommitmentCount() {
        // "resume conversación" cuenta los compromisos por revisar y, si hay
        // vencidos, lo marca sin inflar la frase. Honestidad sobre el estado real.
        val now = 1_000_000_000_000L
        val conv = com.ordia.app.data.local.ConversationEntity(
            id = 1,
            sourceType = com.ordia.app.data.local.ConversationSourceType.IMPORTED,
            title = "Chat de proyecto",
            summary = "Dos mensajes con una solicitud pendiente.",
            contentHash = "a".repeat(64),
            createdAt = now, updatedAt = now
        )
        val overdue = overdueCommitment(1, "te llamo el martes", now - 86_400_000L)
        val answer = AssistantEngine.answer(
            "resume conversación",
            emptyList(),
            listOf(conv), listOf(overdue),
            now
        )
        assertTrue("marca el compromiso vencido: ${answer.text}", answer.text.contains("vencido"))
    }

    @Test fun forgottenIntent_doesNotFlagNonOverdueCommitment() {
        // Guard anti-falso-positivo: una promesa PENDING con dueAt FUTURO no es
        // un olvido; una CONVERTIDA/DISMISSED vencida tampoco (ya revisada).
        // "¿qué olvidé?" no debe fingir olvido donde no lo hay.
        val now = 1_000_000_000_000L
        val future = overdueCommitment(1, "te llamo mañana", now + 86_400_000L)
        val reviewed = overdueCommitment(
            2, "te llamo ayer", now - 86_400_000L,
            status = com.ordia.app.data.local.CommitmentReviewStatus.CONVERTED
        )
        val answer = AssistantEngine.answer(
            "¿qué olvidé?",
            listOf(TaskEntity(id = 1, title = "Normal sin fecha")),
            emptyList(), listOf(future, reviewed),
            now
        )
        assertTrue("no finge olvido con compromisos no vencidos: ${answer.text}",
            answer.text.contains("No tienes tareas vencidas") || answer.text.contains("olvidad"))
        assertTrue("no nombra el compromiso futuro: ${answer.text}", !answer.text.contains("te llamo"))
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

    @Test fun organizeDay_surfacesOverdueCommitmentAsTail() {
        // 4.º olvido (c.286) en la superficie de planificación: "organiza mi día"
        // conocía los compromisos vencidos (AssistantEngine.answer los calcula) pero
        // los silenciaba — justo donde más importa saberlo antes de decidir el plan.
        // Aquí: 0 tareas vencidas + 1 compromiso vencido. Antes decía "0 vencidas"
        // callando la promesa; ahora la anexa como cola informativa y mantiene
        // OPEN_PLANNER (no doble señalización: la promesa no se convierte a ciegas).
        val now = 1_000_000_000_000L
        val task = TaskEntity(id = 1, title = "Normal sin fecha")
        val commitment = overdueCommitment(7, "te llamo el martes", now - 86_400_000L)
        val answer = AssistantEngine.answer(
            "organiza mi dia",
            listOf(task),
            emptyList(), listOf(commitment),
            now
        )
        assertTrue("no miente con 0 vencidas callando el compromiso: ${answer.text}",
            answer.text.contains("compromiso vencido"))
        assertEquals(AssistantAction.OPEN_PLANNER, answer.action)
    }

    @Test fun organizeDay_doesNotMentionCommitmentWhenNoneOverdue() {
        // Guard anti-falso-positivo: sin compromisos vencidos la cola no aparece y la
        // frase base se mantiene limpia (no inventa olvidos donde no los hay).
        val now = 1_000_000_000_000L
        val task = TaskEntity(id = 1, title = "Normal sin fecha")
        val future = overdueCommitment(8, "envío el informe", now + 86_400_000L)
        val answer = AssistantEngine.answer(
            "organiza mi dia",
            listOf(task),
            emptyList(), listOf(future),
            now
        )
        assertTrue("no menciona compromiso sin estar vencido: ${answer.text}",
            !answer.text.contains("compromiso"))
        assertEquals(AssistantAction.OPEN_PLANNER, answer.action)
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

    @Test fun whatNow_surfacesMissedStartHiddenBehindMoreUrgentTask() {
        // Recuperación de olvidos en la superficie de mayor tráfico: "¿qué hago
        // ahora?" elige la tarea más prioritaria del momento. Si esa NO es el
        // inicio olvidado, el hueco incumplido quedaba oculto — el usuario no
        // reagendaba un compromiso al que le dio hora y se le pasó. La cola debe
        // nombrarlo (mismo orden que What Now / "¿qué olvidé?").
        val now = 1_000_000_000_000L
        val urgent = TaskEntity(id = 1, title = "Urgente sin fecha", priority = TaskPriority.URGENT)
        val missed = TaskEntity(
            id = 2, title = "Llamada agendada",
            startAt = now - 90 * 60_000L, // empezó hace 90 min
            durationMinutes = 30,         // ventana terminada → hueco pasado, no vencida (sin dueAt)
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "¿Qué hago ahora?",
            listOf(urgent, missed),
            emptyList(), emptyList(),
            now
        )
        // La sugerida sigue siendo la urgente (timeRank URGENT=2 > missed-start band 0).
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertTrue("nombra el inicio olvidado oculto: ${answer.text}", answer.text.contains("Llamada agendada"))
        assertTrue("describe el olvido: ${answer.text}", answer.text.contains("se pasó"))
    }

    @Test fun whatNow_doesNotRepeatMissedStartWhenSuggestedIsItself() {
        // Si la sugerida YA es el inicio olvidado, su reason ("tenía su hueco y se
        // pasó") ya lo explica: la cola no debe repetirlo ("además «X» tenía su
        // hueco…" sobre la misma X). Evita ruido/confusión.
        val now = 1_000_000_000_000L
        val missed = TaskEntity(
            id = 5, title = "Reunión perdida",
            startAt = now - 60 * 60_000L, // hace 1 h
            durationMinutes = 25,         // ventana terminada
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "¿qué hago ahora?",
            listOf(missed),
            emptyList(), emptyList(),
            now
        )
        assertEquals(listOf(5L), answer.relatedTaskIds)
        assertTrue("la sugerida explica su propio olvido: ${answer.text}", answer.text.contains("se pasó"))
        assertTrue("no repite la misma tarea como 'además': ${answer.text}", !answer.text.contains("Además"))
    }

    @Test fun whatNow_missedStartTailSilentWhenNone() {
        // Sin inicio olvidado, la cola no debe añadir "además … se pasó": no se
        // inventan olvidos (IA honesta). Una tarea urgente + una normal sin hueco.
        val answer = AssistantEngine.answer(
            "¿Qué hago ahora?",
            listOf(TaskEntity(id = 1, title = "Urgente", priority = TaskPriority.URGENT)),
            emptyList(), emptyList()
        )
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertTrue("no inventa olvido sin missed-start: ${answer.text}", !answer.text.contains("se pasó"))
    }

    @Test fun whatNow_namesMostUrgentMissedStartAmongSeveral() {
        // Con varios inicios olvidados, la cola nombra el MÁS urgente (mismo orden
        // que What Now / "¿qué olvidé?"), no uno arbitrario. Una URGENT sin fecha
        // encabeza la sugerencia; entre los missed-start, el de prioridad HIGH va
        // antes que el NORMAL.
        val now = 1_000_000_000_000L
        val urgent = TaskEntity(id = 1, title = "Urgente", priority = TaskPriority.URGENT)
        val missedHigh = TaskEntity(
            id = 2, title = "Llamada HIGH",
            startAt = now - 90 * 60_000L, durationMinutes = 30,
            priority = TaskPriority.HIGH,
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val missedNormal = TaskEntity(
            id = 3, title = "Llamada NORMAL",
            startAt = now - 90 * 60_000L, durationMinutes = 30,
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "¿Qué hago ahora?",
            listOf(urgent, missedHigh, missedNormal),
            emptyList(), emptyList(),
            now
        )
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertTrue("nombra el missed-start más urgente (HIGH): ${answer.text}", answer.text.contains("Llamada HIGH"))
        assertTrue("no nombra el menos urgente en la cola: ${answer.text}", !answer.text.contains("Llamada NORMAL"))
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

    // --- "próxima semana" / "semana que viene" / "semana pasada" (consistencia con
    // SearchEngine, que ya distingue NEXT_WEEK/LAST_WEEK). Antes el asistente caía al
    // `else` de agendaAnswer y respondía ESTA semana (mon..dom de hoy) con la etiqueta
    // "esta semana" aunque el usuario pidiera la próxima/pasada → mentía sobre qué
    // agenda mostraba. Un usuario que pregunta "¿qué tengo la próxima semana?" para
    // planificar veía los compromisos de esta semana y podía olvidar los de la próxima.

    private fun agendaZone(): ZoneId = ZoneId.of("America/Santo_Domingo")

    private fun agendaAnswerFor(query: String, idsAndDue: List<Pair<Long, LocalDate>>): com.ordia.app.assistant.AssistantAnswer {
        val zone = agendaZone()
        val now = LocalDate.of(2026, 7, 29).atTime(12, 0).atZone(zone).toInstant().toEpochMilli() // miércoles
        val tasks = idsAndDue.map { (id, date) ->
            TaskEntity(id = id, title = "Tarea$id", dueAt = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli())
        }
        return AssistantEngine.answer(query, tasks, emptyList(), emptyList(), now, zone)
    }

    @Test fun proximaSemana_listsNextWeekNotThisWeek() {
        val monday = LocalDate.of(2026, 7, 27) // lunes de esta semana
        val thisWeek = monday.plusDays(2) // miércoles esta semana
        val nextWeek = monday.plusDays(9) // jueves próxima semana
        val answer = agendaAnswerFor("¿qué tengo la próxima semana?", listOf(1L to thisWeek, 2L to nextWeek))
        assertTrue("nombra la de la próxima semana: ${answer.text}", answer.text.contains("Tarea2"))
        assertTrue("no mezcla con esta semana: ${answer.text}", !answer.text.contains("Tarea1"))
        assertEquals(listOf(2L), answer.relatedTaskIds)
    }

    @Test fun semanaQueViene_listsNextWeekNotThisWeek() {
        val monday = LocalDate.of(2026, 7, 27)
        val thisWeek = monday.plusDays(2)
        val nextWeek = monday.plusDays(8) // martes próxima semana
        val answer = agendaAnswerFor("¿qué tengo la semana que viene?", listOf(1L to thisWeek, 2L to nextWeek))
        assertTrue("nombra la de la semana que viene: ${answer.text}", answer.text.contains("Tarea2"))
        assertTrue("no mezcla con esta semana: ${answer.text}", !answer.text.contains("Tarea1"))
        assertEquals(listOf(2L), answer.relatedTaskIds)
    }

    @Test fun proximaSemana_empty_saysNextWeekHonestly() {
        val monday = LocalDate.of(2026, 7, 27)
        val thisWeek = monday.plusDays(2) // sólo esta semana
        val answer = agendaAnswerFor("¿qué tengo la próxima semana?", listOf(1L to thisWeek))
        assertTrue("dice próxima semana y que no hay: ${answer.text}",
            answer.text.contains("próxima") && answer.text.contains("no tienes"))
        assertTrue("no inventa la de esta semana: ${answer.text}", !answer.text.contains("Tarea1"))
    }

    @Test fun semanaPasada_recoversPreviousWeekTasks() {
        val monday = LocalDate.of(2026, 7, 27)
        val lastWeek = monday.minusDays(3) // viernes semana pasada
        val thisWeek = monday.plusDays(2)
        val answer = agendaAnswerFor("¿qué tengo la semana pasada?", listOf(1L to thisWeek, 2L to lastWeek))
        assertTrue("recupera la de la semana pasada: ${answer.text}", answer.text.contains("Tarea2"))
        assertTrue("no mezcla con esta semana: ${answer.text}", !answer.text.contains("Tarea1"))
        assertEquals(listOf(2L), answer.relatedTaskIds)
    }

    // --- meses (consistencia con SearchEngine THIS_MONTH/NEXT_MONTH/LAST_MONTH) ---
    // Antes el asistente ni siquiera reconocía "mes" como consulta de agenda, así que
    // "¿qué tengo el próximo mes?" caía fuera de agendaAnswer (respuesta genérica) en
    // vez de listar los compromisos del mes siguiente.

    @Test fun proximoMes_listsNextMonthNotThisMonth() {
        // hoy 2026-07-29 (julio); thisMonth=15-jul, nextMonth=15-ago
        val answer = agendaAnswerFor("¿qué tengo el próximo mes?", listOf(1L to LocalDate.of(2026, 7, 15), 2L to LocalDate.of(2026, 8, 15)))
        assertTrue("nombra la del próximo mes: ${answer.text}", answer.text.contains("Tarea2"))
        assertTrue("no mezcla con este mes: ${answer.text}", !answer.text.contains("Tarea1"))
        assertEquals(listOf(2L), answer.relatedTaskIds)
    }

    @Test fun mesQueViene_listsNextMonthNotThisMonth() {
        val answer = agendaAnswerFor("¿qué tengo el mes que viene?", listOf(1L to LocalDate.of(2026, 7, 15), 2L to LocalDate.of(2026, 8, 20)))
        assertTrue("nombra la del mes que viene: ${answer.text}", answer.text.contains("Tarea2"))
        assertTrue("no mezcla con este mes: ${answer.text}", !answer.text.contains("Tarea1"))
        assertEquals(listOf(2L), answer.relatedTaskIds)
    }

    @Test fun mesPasado_recoversPreviousMonthTasks() {
        val answer = agendaAnswerFor("¿qué tengo el mes pasado?", listOf(1L to LocalDate.of(2026, 7, 15), 2L to LocalDate.of(2026, 6, 15)))
        assertTrue("recupera la del mes pasado: ${answer.text}", answer.text.contains("Tarea2"))
        assertTrue("no mezcla con este mes: ${answer.text}", !answer.text.contains("Tarea1"))
        assertEquals(listOf(2L), answer.relatedTaskIds)
    }

    // "pasado mañana" contiene el substring "mañana"; antes la rama `"manana" in
    // query` del `when` ganaba y el asistente devolvía los compromisos de MAÑANA
    // para una consulta sobre PASADO MAÑANA — una mentira sobre la agenda que podía
    // hacer olvidar lo que el usuario vino a planificar. Ahora "pasado mañana" se
    // resuelve a hoy+2 antes de caer en la rama de "mañana".
    @Test fun pasadoManana_listsDayAfterTomorrowNotTomorrow() {
        // "hoy" en el helper es 2026-07-29 (miércoles).
        val manana = LocalDate.of(2026, 7, 30) // jueves = mañana
        val pasadoManana = LocalDate.of(2026, 7, 31) // viernes = pasado mañana
        val answer = agendaAnswerFor("¿qué tengo pasado mañana?", listOf(1L to manana, 2L to pasadoManana))
        assertTrue("nombra la de pasado mañana: ${answer.text}", answer.text.contains("Tarea2"))
        assertTrue("no mezcla con mañana: ${answer.text}", !answer.text.contains("Tarea1"))
        assertEquals(listOf(2L), answer.relatedTaskIds)
    }

    @Test fun pasadoManana_empty_saysDayAfterTomorrowHonestly() {
        // "hoy" en el helper es 2026-07-29; sólo hay algo mañana.
        val manana = LocalDate.of(2026, 7, 30)
        val answer = agendaAnswerFor("¿qué tengo pasado mañana?", listOf(1L to manana))
        assertTrue("dice pasado mañana y que no hay: ${answer.text}",
            answer.text.contains("pasado mañana") && answer.text.contains("no tienes"))
        assertTrue("no inventa la de mañana: ${answer.text}", !answer.text.contains("Tarea1"))
    }

    // "¿Qué tengo hoy?" cuando NO hay nada vencido hoy PERO sí atrasado de días
    // anteriores: antes devolvía "Para hoy no tienes tareas agendadas." y callaba
    // el trabajo atrasado — justo lo que el usuario tiene que hacer hoy. Ahora
    // nombra la atrasada más urgente (+ recuento) y deja ids para actuar.
    @Test fun hoy_conSoloAtrasadas_nombraLaMasUrgente() {
        val monday = LocalDate.of(2026, 7, 27)
        val ayer = monday.plusDays(1) // martes (atrasada respecto al "hoy"=miércoles)
        val answer = agendaAnswerFor("¿qué tengo hoy?", listOf(2L to ayer))
        assertTrue("nombra la atrasada: ${answer.text}", answer.text.contains("Tarea2"))
        assertTrue("no miente 'no tienes': ${answer.text}", !answer.text.contains("no tienes tareas agendadas."))
        assertEquals(listOf(2L), answer.relatedTaskIds)
    }

    @Test fun hoy_conVariasAtrasadas_nombraLaMasUrgenteYRecuenta() {
        val base = LocalDate.of(2026, 7, 27) // "hoy" en el helper es 2026-07-29
        val vieja = base.minusDays(3) // más urgente (más antigua)
        val ayer = base.plusDays(1)  // atrasada menos
        val answer = agendaAnswerFor("¿qué tengo hoy?", listOf(2L to vieja, 1L to ayer))
        assertTrue("nombra la más urgente (Tarea2): ${answer.text}", answer.text.contains("Tarea2"))
        assertTrue("recuenta 1 más: ${answer.text}", answer.text.contains("1 más atrasada"))
        assertTrue("no miente 'no tienes': ${answer.text}", !answer.text.contains("no tienes tareas agendadas."))
    }

    @Test fun hoy_sinAtrasadasNiHoy_diceVacioHonesto() {
        val answer = agendaAnswerFor("¿qué tengo hoy?", emptyList())
        assertTrue("agenda vacía honesta: ${answer.text}", answer.text.contains("Para hoy no tienes tareas agendadas."))
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

    @Test fun whatNow_inProgressWindow_saysContinueNotStart_andShowsRemainingTime() {
        // Bug de honestidad: si lo que sugiere "qué hago ahora" es una tarea cuya
        // ventana startAt..fin YA está activa ([WhatNowReason.IN_PROGRESS_NOW]),
        // el texto no puede decir "Empieza por … Estimo 25 minutos": contradice
        // "ya está en curso" (no se empieza lo que está en curso) y miente con el
        // tiempo (la duración planificada completa cuenta de más: ya se ha vivido
        // parte). Debe decir "Sigue con" y "Te quedan N minutos" (lo que FALTA).
        val now = 1_000_000_000_000L
        val inProgress = TaskEntity(
            id = 1, title = "Borrador",
            startAt = now - 15 * 60_000L, // empezó hace 15 min
            durationMinutes = 25,          // ventana activa, faltan 10 min
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "¿qué hago ahora?",
            listOf(inProgress),
            emptyList(), emptyList(),
            now
        )
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertTrue("dice 'Sigue con', no 'Empieza por': ${answer.text}", answer.text.contains("Sigue con"))
        assertTrue("no dice 'Empieza por': ${answer.text}", !answer.text.contains("Empieza por"))
        assertTrue("muestra el tiempo que falta, no el planificado: ${answer.text}", answer.text.contains("Te quedan 10 minutos"))
        assertTrue("no dice 'Estimo': ${answer.text}", !answer.text.contains("Estimo"))
    }

    @Test fun whatNow_inProgressManualFlag_saysContinueButKeepsEstimate() {
        // Caso complementario: tarea marcada IN_PROGRESS a mano (sin startAt).
        // El reason es IN_PROGRESS_NOW (por status), así que "Sigue con" es
        // honesto. Pero NO hay ventana activa → no sabemos cuánto se ha trabajado:
        // el tiempo debe seguir siendo "Estimo N" (no se simula elapsed desconocido).
        val manual = TaskEntity(
            id = 2, title = "Correo",
            durationMinutes = 20,
            status = com.ordia.app.data.local.TaskStatus.IN_PROGRESS
        )
        val answer = AssistantEngine.answer(
            "¿qué hago ahora?",
            listOf(manual),
            emptyList(), emptyList()
        )
        assertEquals(listOf(2L), answer.relatedTaskIds)
        assertTrue("dice 'Sigue con' (está en curso): ${answer.text}", answer.text.contains("Sigue con"))
        assertTrue("conserva 'Estimo' (no hay ventana activa): ${answer.text}", answer.text.contains("Estimo"))
        assertTrue("no finge 'Te quedan' sin saber el elapsed: ${answer.text}", !answer.text.contains("Te quedan"))
    }
}
