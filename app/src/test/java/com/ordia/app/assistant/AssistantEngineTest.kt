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

    // --- c.356: "¿qué tengo hoy?" no debe callar un compromiso vencido de una
    // conversación (el cuarto olvido). La rama "hoy" de agendaAnswer ya rompía la
    // pureza "sólo agenda de hoy" para nombrar las atrasadas de días anteriores
    // (earlierOverdue) — pero silenciaba los compromisos vencidos, exactamente la
    // outlier que c.354 corrigió en "¿voy bien?". Todas las demás superficies del
    // asistente ("organiza mi día", "qué hago ahora", "qué olvidé", "vencidas",
    // "resume conversación", "¿voy bien?") ya anexaban overdueCommitmentTail;
    // agendaAnswer "hoy" era la única que callaba el olvido en la consulta de
    // agenda más común. Cola informativa, paralela a c.354/c.294/c.297.

    @Test fun queTengoHoy_warnsOverdueCommitmentWhenAgendaHasTasks() {
        // Con agenda de hoy: la cola informativa avisa del compromiso vencido sin
        // cambiar el foco de la agenda (no lo nombra — es "además", paralelo al
        // tail de atrasadas). La acción primaria sigue siendo la agenda de hoy.
        val now = 1_000_000_000_000L
        val today = todayNoon(now)
        val overdueDue = now - 86_400_000L
        val commitment = overdueCommitment(1, "te llamo el martes", overdueDue)
        val answer = AssistantEngine.answer(
            "¿qué tengo hoy?",
            listOf(TaskEntity(id = 10, title = "Cita médica", dueAt = today)),
            emptyList(), listOf(commitment),
            now
        )
        assertTrue("nombra la de hoy: ${answer.text}", answer.text.contains("Cita médica"))
        assertTrue("no calla el compromiso vencido: ${answer.text}", answer.text.contains("compromiso") && answer.text.contains("vencido"))
    }

    @Test fun queTengoHoy_warnsOverdueCommitmentWhenEmptyAgendaButEarlierOverdue() {
        // Sin agenda de hoy PERO con atrasada de tarea: ya nombraba la atrasada;
        // ahora debe además avisar del compromiso vencido (cola), no callarlo.
        val now = 1_000_000_000_000L
        val earlierOverdue = now - 3 * 86_400_000L
        val overdueDue = now - 86_400_000L
        val commitment = overdueCommitment(2, "envío el informe", overdueDue)
        val answer = AssistantEngine.answer(
            "¿qué tengo hoy?",
            listOf(TaskEntity(id = 20, title = "Entrega vieja", dueAt = earlierOverdue)),
            emptyList(), listOf(commitment),
            now
        )
        assertTrue("nombra la atrasada de tarea: ${answer.text}", answer.text.contains("Entrega vieja"))
        assertTrue("no calla el compromiso vencido: ${answer.text}", answer.text.contains("compromiso") && answer.text.contains("vencido"))
    }

    @Test fun queTengoHoy_recoversOverdueCommitmentWhenNoTaskOverdue() {
        // Agenda de hoy vacía y SIN atrasadas de tarea: antes decía "Para hoy no
        // tienes tareas agendadas." frente a una promesa vencida — mentía por
        // omisión en la superficie de agenda más común. Ahora la nombra (cola
        // informativa, no routing a overdueCommitmentAnswer: la consulta es de
        // agenda, no de olvidos; se avisa, no se cambia de tema).
        val now = 1_000_000_000_000L
        val overdueDue = now - 86_400_000L
        val commitment = overdueCommitment(3, "revisar el contrato", overdueDue)
        val answer = AssistantEngine.answer(
            "¿qué tengo hoy?",
            emptyList(),
            emptyList(), listOf(commitment),
            now
        )
        assertTrue("no miente 'no tienes nada': ${answer.text}", answer.text.contains("revisar el contrato"))
        assertTrue("menciona que es vencido: ${answer.text}", answer.text.contains("vencido"))
    }

    @Test fun queTengoHoy_doesNotInventCommitmentWhenNone() {
        // Guard anti-falso-positivo: sin compromiso vencido (ni tarea atrasada ni
        // agenda), la respuesta NO debe inventar "compromiso vencido".
        val now = 1_000_000_000_000L
        val answer = AssistantEngine.answer(
            "¿qué tengo hoy?",
            listOf(TaskEntity(id = 30, title = "Cita médica", dueAt = todayNoon(now))),
            emptyList(), emptyList(),
            now
        )
        assertTrue("no inventa compromiso: ${answer.text}", !answer.text.contains("compromiso"))
    }

    @Test fun queTengoManana_doesNotMentionOverdueCommitmentInFutureScope() {
        // "¿qué tengo mañana?" es alcance futuro: los atrasados/compromisos
        // vencidos NO son parte de "lo de mañana". El tail de compromisos sólo
        // aplica al alcance "hoy" (igual que earlierOverdue). Guard de coherencia
        // con el diseño existente: mañana/semana/mes no anexan atrasadas.
        val now = 1_000_000_000_000L
        val tomorrow = tomorrowNoon(now)
        val overdueDue = now - 86_400_000L
        val commitment = overdueCommitment(4, "te llamo el martes", overdueDue)
        val answer = AssistantEngine.answer(
            "¿qué tengo mañana?",
            listOf(TaskEntity(id = 40, title = "Reunión", dueAt = tomorrow)),
            emptyList(), listOf(commitment),
            now
        )
        assertTrue("nombra la de mañana: ${answer.text}", answer.text.contains("Reunión"))
        assertTrue("no mezcla el compromiso vencido en el alcance futuro: ${answer.text}", !answer.text.contains("te llamo el martes"))
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

    // --- Día de la semana a demanda ("¿qué tengo el viernes?") (c.350) ---
    //
    // Antes una consulta por weekday suelto NO se reconocía como agenda: caía al
    // mensaje genérico ("Puedo organizar tu día...") y el asistente callaba la
    // agenda de un día concreto pese a preguntarla — una mentira por omisión que
    // podía hacer olvidar lo que el usuario vino a planificar. Ahora resuelve el
    // día (simétrico con SearchEngine.resolveWeekdayTarget y el parser de captura):
    // inclusivo (incluye hoy si hoy es ese día) salvo "próximo"/"que viene" →
    // estricto. "hoy" en el helper es 2026-07-29 (miércoles).

    @Test fun queTengoElViernes_listsTasksDueThatWeekday() {
        // hoy miércoles 2026-07-29; viernes inclusivo = 2026-07-31.
        val viernes = LocalDate.of(2026, 7, 31)
        val jueves = LocalDate.of(2026, 7, 30) // mañana, no debe mezclarse
        val answer = agendaAnswerFor("¿qué tengo el viernes?", listOf(1L to jueves, 2L to viernes))
        assertTrue("nombra la del viernes: ${answer.text}", answer.text.contains("Tarea2"))
        assertTrue("no mezcla con jueves: ${answer.text}", !answer.text.contains("Tarea1"))
        assertEquals(listOf(2L), answer.relatedTaskIds)
    }

    @Test fun queTengoElViernes_empty_diceEseDiaHonesto() {
        // sólo hay algo el jueves; el viernes está vacío.
        val jueves = LocalDate.of(2026, 7, 30)
        val answer = agendaAnswerFor("¿qué tengo el viernes?", listOf(1L to jueves))
        assertTrue("dice viernes y que no hay: ${answer.text}",
            answer.text.contains("viernes") && answer.text.contains("no tienes"))
        assertTrue("no inventa la del jueves: ${answer.text}", !answer.text.contains("Tarea1"))
    }

    @Test fun proximoViernes_strictCoincideConParserYSearch() {
        // "próximo viernes" usa la MISMA semántica que NaturalTaskParser.nextWeekday
        // y SearchEngine.resolveWeekdayTarget: próxima ocurrencia, saltando SOLO si
        // hoy es ese día. En miércoles 2026-07-29, "próximo viernes" = 07-31 (el
        // viernes inminente), no 08-07. Sin esta simetría, asistente, búsqueda y
        // capturar discordarían sobre a qué viernes se refiere una misma frase.
        val esteViernes = LocalDate.of(2026, 7, 31)
        val viernesSiguiente = LocalDate.of(2026, 8, 7)
        val answer = agendaAnswerFor("¿qué tengo el próximo viernes?", listOf(1L to esteViernes, 2L to viernesSiguiente))
        assertTrue("nombra el viernes inminente (07-31): ${answer.text}", answer.text.contains("Tarea1"))
        assertTrue("no salta al viernes siguiente: ${answer.text}", !answer.text.contains("Tarea2"))
        assertEquals(listOf(1L), answer.relatedTaskIds)
    }

    @Test fun proximoMiercoles_strictSaltaHoy() {
        // hoy es miércoles 2026-07-29: "próximo miércoles" estricto debe saltar HOY
        // (+7) → 2026-08-05, no hoy. Este es el caso donde strict e inclusivo
        // divergen (delta==0). Sin el salto, "próximo miércoles" devolvería la
        // agenda de hoy — exactamente lo que el usuario NO pidió al decir "próximo".
        val hoy = LocalDate.of(2026, 7, 29)
        val proximoMiercoles = LocalDate.of(2026, 8, 5)
        val answer = agendaAnswerFor("¿qué tengo el próximo miércoles?", listOf(1L to hoy, 2L to proximoMiercoles))
        assertTrue("nombra el próximo miércoles (08-05), no hoy: ${answer.text}", answer.text.contains("Tarea2"))
        assertTrue("no muestra la de hoy: ${answer.text}", !answer.text.contains("Tarea1"))
        assertEquals(listOf(2L), answer.relatedTaskIds)
    }

    @Test fun weekdayInclusivo_cuandoEsHoy_reusaRamaHoyYMuestraAtrasadas() {
        // hoy miércoles 2026-07-29; "¿qué tengo el miércoles?" inclusivo = hoy.
        // Debe comportarse como "¿qué tengo hoy?": no callar lo atrasado de días
        // anteriores. Sólo hay una atrasada del martes → la nombra en vez de
        // "no tienes tareas agendadas".
        val martes = LocalDate.of(2026, 7, 28) // atrasada respecto al "hoy"=miércoles
        val answer = agendaAnswerFor("¿qué tengo el miércoles?", listOf(2L to martes))
        assertTrue("no miente 'no tienes': ${answer.text}", !answer.text.contains("no tienes tareas agendadas."))
        assertTrue("nombra la atrasada: ${answer.text}", answer.text.contains("Tarea2"))
    }

    @Test fun weekday_noRegresanFrasesRapidas() {
        // Añadir weekday a isAgendaQuery no debe romper "qué hago ahora" ni
        // "plan mínimo": siguen su camino propio.
        val now = 1_000_000_000_000L
        val whatNow = AssistantEngine.answer(
            "¿qué hago ahora?",
            listOf(TaskEntity(id = 1, title = "X", priority = TaskPriority.URGENT)),
            emptyList(), emptyList(), now
        )
        assertEquals(listOf(1L), whatNow.relatedTaskIds)
    }

    // --- Fin de semana a demanda ("¿qué tengo el finde?") (c.352) ---
    //
    // Antes "¿qué tengo el finde?"/"¿qué tengo el fin de semana?" NO se reconocía
    // como agenda: "finde" suelto no casaba ningún token, y "fin de semana" caía
    // al scope de semana completa (lun..dom) por la palabra "semana" — el
    // asistente callaba la agenda del finde o mentía mostrando toda la semana.
    // Ahora resuelve sábado+domingo del PRÓXIMO finde (estricto), simétrico con
    // SearchEngine.resolveWeekendTarget y el parser de captura (weekendPattern).
    // "hoy" en el helper es 2026-07-29 (miércoles): próximo sábado = 08-01,
    // domingo = 08-02.

    @Test fun queTengoElFinde_listsTasksDueSatOrSun() {
        // hoy miércoles 2026-07-29; próximo finde = sábado 08-01 + domingo 08-02.
        val sabado = LocalDate.of(2026, 8, 1)
        val domingo = LocalDate.of(2026, 8, 2)
        val viernes = LocalDate.of(2026, 7, 31) // viernes previo, no debe mezclarse
        val answer = agendaAnswerFor("¿qué tengo el finde?", listOf(1L to viernes, 2L to sabado, 3L to domingo))
        assertTrue("nombra la del sábado: ${answer.text}", answer.text.contains("Tarea2"))
        assertTrue("nombra la del domingo: ${answer.text}", answer.text.contains("Tarea3"))
        assertTrue("no mezcla con el viernes previo: ${answer.text}", !answer.text.contains("Tarea1"))
        assertEquals(listOf(2L, 3L), answer.relatedTaskIds)
    }

    @Test fun queTengoElFinDeSemana_noCaeASemanaCompleta() {
        // "fin de semana" contiene la palabra "semana": sin la rama weekend ANTES
        // que "semana", caía a "esta semana" (lun..dom) y mezclaba tareas de entre
        // semana. Debe listar SÓLO sábado+domingo del próximo finde.
        val sabado = LocalDate.of(2026, 8, 1)
        val martesProximo = LocalDate.of(2026, 8, 4) // entre semana siguiente
        val answer = agendaAnswerFor("¿qué tengo el fin de semana?", listOf(1L to sabado, 2L to martesProximo))
        assertTrue("nombra la del sábado del finde: ${answer.text}", answer.text.contains("Tarea1"))
        assertTrue("no mezcla con el martes siguiente: ${answer.text}", !answer.text.contains("Tarea2"))
        assertEquals(listOf(1L), answer.relatedTaskIds)
    }

    @Test fun finde_strictSaltaAlSiguienteCuandoHoyEsSabado() {
        // Simetría con SearchEngine.resolveWeekendTarget y el parser: si HOY es
        // sábado, "finde" resuelve al PRÓXIMO finde (no al de hoy que ya corre).
        // Sin el salto estricto, devolvería la agenda del sábado actual.
        val zone = agendaZone()
        val hoySabado = LocalDate.of(2026, 8, 1).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val esteSabado = LocalDate.of(2026, 8, 1)
        val sabadoSiguiente = LocalDate.of(2026, 8, 8)
        val domingoSiguiente = LocalDate.of(2026, 8, 9)
        val tasks = listOf(
            TaskEntity(id = 1, title = "Tarea1", dueAt = esteSabado.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()),
            TaskEntity(id = 2, title = "Tarea2", dueAt = sabadoSiguiente.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()),
            TaskEntity(id = 3, title = "Tarea3", dueAt = domingoSiguiente.atTime(9, 0).atZone(zone).toInstant().toEpochMilli())
        )
        val answer = AssistantEngine.answer("¿qué tengo el finde?", tasks, emptyList(), emptyList(), hoySabado, zone)
        assertTrue("nombra el sábado del próximo finde (08-08): ${answer.text}", answer.text.contains("Tarea2"))
        assertTrue("nombra el domingo del próximo finde (08-09): ${answer.text}", answer.text.contains("Tarea3"))
        assertTrue("no muestra el sábado de hoy: ${answer.text}", !answer.text.contains("Tarea1"))
    }

    @Test fun finde_empty_diceEseFindeHonesto() {
        // sólo hay algo el viernes previo al finde; el finde está vacío.
        val viernes = LocalDate.of(2026, 7, 31)
        val answer = agendaAnswerFor("¿qué tengo el finde?", listOf(1L to viernes))
        assertTrue("dice finde y que no hay: ${answer.text}",
            answer.text.contains("finde") && answer.text.contains("no tienes"))
        assertTrue("no inventa la del viernes: ${answer.text}", !answer.text.contains("Tarea1"))
    }

    @Test fun finde_noRegresanFrasesRapidas() {
        // Añadir weekend a isAgendaQuery no debe romper "qué hago ahora" ni
        // "plan mínimo": siguen su camino propio.
        val now = 1_000_000_000_000L
        val whatNow = AssistantEngine.answer(
            "¿qué hago ahora?",
            listOf(TaskEntity(id = 1, title = "X", priority = TaskPriority.URGENT)),
            emptyList(), emptyList(), now
        )
        assertEquals(listOf(1L), whatNow.relatedTaskIds)
    }


    // --- Parte del día a demanda ("¿qué tengo esta tarde?"/"esta noche"/"la
    // madrugada") (c.353) ---
    //
    // Antes "¿qué tengo esta noche?" NO se reconocía como agenda: "noche"/"tarde"/
    // "madrugada" no casaban ningún token de isAgendaQuery → caía al mensaje
    // genérico. El asistente callaba la agenda vespertina/nocturna pese a
    // preguntarla. SearchEngine YA filtraba por parte del día (DateScope.TARTE/
    // NOCHE/MADRUGADA, scopeBand 12..17 / 18..23 / 0..5); ahora el asistente es
    // simétrico. La franja es un modificador opcional: "esta noche" = hoy 18-23;
    // "el viernes en la noche" = viernes 18-23; "mañana en la noche" = mañana 18-23.
    // Las tareas "solo fecha" (hora 0) sólo casan con madrugada, igual que en
    // SearchEngine: no se afirme honestamente que pertenecen a la tarde/noche.
    // hoy en agendaAnswerFor es 2026-07-29 (miércoles) a las 12:00.

    private fun agendaAtHour(query: String, idsAndDateHour: List<Triple<Long, LocalDate, Int>>): com.ordia.app.assistant.AssistantAnswer {
        val zone = agendaZone()
        val now = LocalDate.of(2026, 7, 29).atTime(12, 0).atZone(zone).toInstant().toEpochMilli() // miércoles
        val tasks = idsAndDateHour.map { (id, date, hour) ->
            TaskEntity(id = id, title = "Tarea$id", dueAt = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli())
        }
        return AssistantEngine.answer(query, tasks, emptyList(), emptyList(), now, zone)
    }

    @Test fun queTengoEstaNoche_listsOnlyEveningTasksOfToday() {
        // hoy 2026-07-29. Tarea de hoy a las 20:00 (noche, 18-23) debe aparecer;
        // tarea de hoy a las 10:00 (mañana, fuera de banda) NO; tarea de mañana a
        // las 21:00 NO (otro día).
        val hoy = LocalDate.of(2026, 7, 29)
        val manana = LocalDate.of(2026, 7, 30)
        val answer = agendaAtHour("¿qué tengo esta noche?", listOf(
            Triple(1L, hoy, 20),    // noche → sí
            Triple(2L, hoy, 10),    // día, fuera de banda → no
            Triple(3L, manana, 21)  // otro día → no
        ))
        assertTrue("nombra la de esta noche: ${answer.text}", answer.text.contains("Tarea1"))
        assertTrue("no mezcla la diurna de hoy: ${answer.text}", !answer.text.contains("Tarea2"))
        assertTrue("no mezcla la de mañana: ${answer.text}", !answer.text.contains("Tarea3"))
        assertEquals(listOf(1L), answer.relatedTaskIds)
    }

    @Test fun queTengoEstaTarde_listsAfternoonBand() {
        // tarde = 12..17. Tarea de hoy 14:00 sí; hoy 19:00 (noche) no.
        val hoy = LocalDate.of(2026, 7, 29)
        val answer = agendaAtHour("¿qué tengo esta tarde?", listOf(
            Triple(1L, hoy, 14),  // tarde → sí
            Triple(2L, hoy, 19)   // noche, fuera de banda → no
        ))
        assertTrue("nombra la de esta tarde: ${answer.text}", answer.text.contains("Tarea1"))
        assertTrue("no mezcla la nocturna: ${answer.text}", !answer.text.contains("Tarea2"))
    }

    // --- c.385: agenda a demanda alinea membresía de fecha con el planificador
    // y con el conteo de carga del día. Antes, "¿qué tengo hoy?" miraba SÓLO
    // `dueAt` (isDueInRange/isDueInHourBand), mientras que el calendario del
    // planificador (PlannerCalendar.datesFor) y el veredicto "¿voy bien?"
    // (SummaryEngine.remainingToday) cuentan una tarea cuya hora prevista
    // (`startAt`) cae en el día. Así el usuario podía oír "el día está lleno"
    // (la cuenta incluía un slot de hoy) y, al preguntar "¿qué tengo hoy?", ver
    // una lista que omitía ese slot — mentía por omisión en la consulta más
    // común. Ahora las tres superficies acuerdan: una tarea es "del día X" si su
    // `startAt` o su `dueAt` cae en X; la franja horaria usa la marca que cae en
    // el rango (prefiere `startAt`, simétrico con PlannerCalendar.timestampOnDate).
    // Mismo `now`/`zone` que los tests de franja (2026-07-29 mié 12:00, Sto. Dgo).

    private fun agendaStartAt(query: String, startAt: Long?, dueAt: Long?, title: String = "Slot de hoy"): com.ordia.app.assistant.AssistantAnswer {
        val zone = agendaZone()
        val now = LocalDate.of(2026, 7, 29).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val task = TaskEntity(id = 1, title = title, startAt = startAt, dueAt = dueAt)
        return AssistantEngine.answer(query, listOf(task), emptyList(), emptyList(), now, zone)
    }

    private fun millisAt(date: LocalDate, hour: Int): Long =
        date.atTime(hour, 0).atZone(agendaZone()).toInstant().toEpochMilli()

    @Test fun queTengoHoy_incluyeTareaConStartAtHoyAunqueVenzaDespues() {
        // Slot agendado para HOY 15:00 pero con vencimiento el viernes (startAt
        // hoy, dueAt futuro). El planificador la muestra hoy; el asistente debe
        // nombrarla al preguntar "¿qué tengo hoy?".
        val hoy = LocalDate.of(2026, 7, 29)
        val viernes = hoy.plusDays(3)
        val answer = agendaStartAt("¿qué tengo hoy?", startAt = millisAt(hoy, 15), dueAt = millisAt(viernes, 18))
        assertTrue("nombra el slot de hoy: ${answer.text}", answer.text.contains("Slot de hoy"))
        assertTrue("relaciona el slot: ${answer.relatedTaskIds}", answer.relatedTaskIds.contains(1L))
    }

    @Test fun queTengoHoy_noCuentaStartAtDeOtroDiaComoHoy() {
        // startAt mañana, sin vencimiento hoy → no es "de hoy". No se inventa.
        val hoy = LocalDate.of(2026, 7, 29)
        val manana = hoy.plusDays(1)
        val answer = agendaStartAt("¿qué tengo hoy?", startAt = millisAt(manana, 10), dueAt = null, title = "Slot de mañana")
        assertTrue("no mezcla el slot de mañana como de hoy: ${answer.text}", !answer.text.contains("Slot de mañana"))
        assertTrue("sin ids inventados: ${answer.relatedTaskIds}", answer.relatedTaskIds.isEmpty())
    }

    @Test fun queTengoManana_incluyeStartAtMananaSinDueAt() {
        // startAt mañana, sin dueAt → pertenece a mañana (simétrico con el
        // planificador y con el conteo de carga).
        val manana = LocalDate.of(2026, 7, 30)
        val answer = agendaStartAt("¿qué tengo mañana?", startAt = millisAt(manana, 10), dueAt = null, title = "Slot de mañana")
        assertTrue("nombra el slot de mañana: ${answer.text}", answer.text.contains("Slot de mañana"))
        assertTrue("relaciona el slot: ${answer.relatedTaskIds}", answer.relatedTaskIds.contains(1L))
    }

    @Test fun queTengoEstaTarde_incluyeSlotDeHoyConStartAtEnFranja() {
        // Slot de hoy 15:00 (tarde 12-17), vencimiento el viernes. La franja usa
        // la marca que cae en el rango (startAt hoy 15:00) → 15 ∈ 12-17 → sí.
        val hoy = LocalDate.of(2026, 7, 29)
        val viernes = hoy.plusDays(3)
        val answer = agendaStartAt("¿qué tengo esta tarde?", startAt = millisAt(hoy, 15), dueAt = millisAt(viernes, 18))
        assertTrue("nombra el slot vespertino de hoy: ${answer.text}", answer.text.contains("Slot de hoy"))
    }

    @Test fun queTengoEstaTarde_excluyeSlotDeHoyEnOtraFranja() {
        // Slot de hoy 09:00 (mañana, fuera de 12-17), vencimiento viernes. La
        // franja cae fuera → no se nombra aunque la fecha sí sea hoy.
        val hoy = LocalDate.of(2026, 7, 29)
        val viernes = hoy.plusDays(3)
        val answer = agendaStartAt("¿qué tengo esta tarde?", startAt = millisAt(hoy, 9), dueAt = millisAt(viernes, 18), title = "Slot matutino")
        assertTrue("no mezcla el slot matutino en la tarde: ${answer.text}", !answer.text.contains("Slot matutino"))
    }

    @Test fun queTengoEstaTarde_prefiereStartAtSobreDueAtParaFranja() {
        // startAt hoy 09:00 (mañana), dueAt hoy 20:00 (noche). Ambas caen hoy.
        // La franja prefiere startAt (09:00, fuera de 12-17) → no se nombra en
        // "esta tarde", simétrico con PlannerCalendar.timestampOnDate (startAt
        // manda). Evita afirmar que la tarea "es de la tarde" por su vencimiento
        // cuando el usuario la agendó para la mañana.
        val hoy = LocalDate.of(2026, 7, 29)
        val answer = agendaStartAt("¿qué tengo esta tarde?", startAt = millisAt(hoy, 9), dueAt = millisAt(hoy, 20), title = "Slot matutino vence de noche")
        assertTrue("no mezcla por el dueAt nocturno: ${answer.text}", !answer.text.contains("Slot matutino vence de noche"))
    }


    @Test fun parteDelDia_hoyEnLaTarde_muestraEtiquetaEstaTarde() {
        // "hoy en la tarde" debe resolver a "esta tarde" (hoy + banda 12-17), no a
        // la etiqueta "hoy" (que no filtraría por hora).
        val hoy = LocalDate.of(2026, 7, 29)
        val answer = agendaAtHour("¿qué tengo hoy en la tarde?", listOf(
            Triple(1L, hoy, 15),   // tarde → sí
            Triple(2L, hoy, 9)     // mañana, fuera de banda → no
        ))
        assertTrue("usa la etiqueta de la tarde: ${answer.text}", answer.text.contains("Esta tarde"))
        assertTrue("nombra la vespertina: ${answer.text}", answer.text.contains("Tarea1"))
        assertTrue("no mezcla la de la mañana: ${answer.text}", !answer.text.contains("Tarea2"))
    }

    @Test fun parteDelDia_esViernesEnLaNoche_filtraViernesNoche() {
        // La franja es un MODIFICADOR: "el viernes en la noche" = viernes 18-23,
        // NO hoy noche. Antes "noche" no se reconocía; ahora debe resolver al
        // viernes (próximo viernes desde 2026-07-29 = 2026-07-31) y filtrar noche.
        val viernes = LocalDate.of(2026, 7, 31)
        val answer = agendaAtHour("¿qué tengo el viernes en la noche?", listOf(
            Triple(1L, viernes, 21),  // viernes noche → sí
            Triple(2L, viernes, 8)    // viernes mañana → no
        ))
        assertTrue("nombra la del viernes noche: ${answer.text}", answer.text.contains("Tarea1"))
        assertTrue("no mezcla la del viernes mañana: ${answer.text}", !answer.text.contains("Tarea2"))
    }

    @Test fun parteDelDia_mananaEnLaNoche_filtraMananaNoche() {
        // "mañana en la noche": la rama "mañana" gana (va antes que la parte del
        // día) y la franja nocturna se aplica encima → mañana 18-23.
        val manana = LocalDate.of(2026, 7, 30)
        val answer = agendaAtHour("¿qué tengo mañana en la noche?", listOf(
            Triple(1L, manana, 22),  // mañana noche → sí
            Triple(2L, manana, 11)   // mañana día → no
        ))
        assertTrue("nombra la de mañana noche: ${answer.text}", answer.text.contains("Tarea1"))
        assertTrue("no mezcla la diurna de mañana: ${answer.text}", !answer.text.contains("Tarea2"))
    }

    @Test fun parteDelDia_empty_diceEstaNocheHonesto() {
        // nada esta noche → "Para esta noche no tienes tareas agendadas."
        val hoy = LocalDate.of(2026, 7, 29)
        val answer = agendaAtHour("¿qué tengo esta noche?", listOf(Triple(1L, hoy, 10)))
        assertTrue("dice esta noche y que no hay: ${answer.text}",
            answer.text.contains("esta noche") && answer.text.contains("no tienes"))
        assertTrue("no inventa la diurna: ${answer.text}", !answer.text.contains("Tarea1"))
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

    // ---- dayLoad + olvidos silenciados (simetría con el resto del asistente) ----
    //
    // "¿voy bien?"/"¿da tiempo?" es la OUTLIER del asistente: las demás superficies
    // ("organiza mi día", "qué hago ahora", "qué olvidé") anexan colas informativas
    // de vencidas y compromisos vencidos, pero dayLoadAnswer los silenciaba. Un
    // usuario con 3 vencidas cuya carga "cabe" en la jornada leía "Vas bien con
    // holgura" sin saber que tiene vencidas acumuladas — mentira por omisión en una
    // superficie de alto tráfico (la que pregunta "¿voy bien?" justo cuando el
    // riesgo de olvidar vencidas es mayor). Estos tests anclan que el veredicto de
    // carga NUNCA calla los olvidos, igual que las demás ramas.

    @Test fun dayLoad_onTrack_namesOverdueTasksWhenPresent() {
        // 9:00 → 540 min libres; 1 vencida pequeña (45) + 1 de hoy (45) → cabe con
        // holgura (ON_TRACK), PERO hay una vencida: el veredicto no puede callarla.
        val now = dayAt(dayToday, 9)
        val answer = AssistantEngine.answer(
            "¿voy bien hoy?",
            listOf(
                TaskEntity(id = 1, title = "Hoy", dueAt = dayAt(dayToday, 11), durationMinutes = 45),
                TaskEntity(id = 2, title = "Vencida", dueAt = dayAt(dayToday.minusDays(1), 9), durationMinutes = 45)
            ),
            emptyList(), emptyList(),
            now, dayZone
        )
        assertTrue("explica que va con holgura: ${answer.text}", answer.text.contains("holgura"))
        assertTrue("no calla la vencida: ${answer.text}", answer.text.contains("vencida"))
    }

    @Test fun dayLoad_light_doesNotInventOverdueWhenNone() {
        // Sin trabajo ni vencidas → LIGHT; no debe inventar "Además, vencidas".
        val now = dayAt(dayToday, 9)
        val answer = AssistantEngine.answer(
            "¿voy bien hoy?",
            emptyList(),
            emptyList(), emptyList(),
            now, dayZone
        )
        assertTrue("dice que el día está despejado: ${answer.text}", answer.text.contains("despejado"))
        assertFalse("no inventa vencidas: ${answer.text}", answer.text.contains("vencida"))
    }

    @Test fun dayLoad_namesOverdueCommitmentEvenWhenDayIsLight() {
        // Sin tareas (día despejado) pero con una promesa vencida: el veredicto no
        // puede decir "despejado" sin recordar el compromiso vencido (4.º olvido).
        val now = dayAt(dayToday, 9)
        val overdueDue = dayAt(dayToday.minusDays(3), 10)
        val commitment = overdueCommitment(1, "te llamo el martes", overdueDue)
        val answer = AssistantEngine.answer(
            "¿voy bien hoy?",
            emptyList(),
            emptyList(), listOf(commitment),
            now, dayZone
        )
        assertTrue("dice que el día está despejado: ${answer.text}", answer.text.contains("despejado"))
        assertTrue("no calla el compromiso vencido: ${answer.text}",
            answer.text.contains("compromiso") && answer.text.contains("vencido"))
    }

    @Test fun dayLoad_overloaded_keepsDeferralCandidateAndNamesOverdue() {
        // OVERLOADED con vencidas: sigue nombrando la candidata a posponer (acción
        // primaria) Y no calla que hay vencidas (cola informativa). No-regresión de
        // relatedTaskIds (la candidata, no las vencidas).
        val now = dayAt(dayToday, 12)
        val answer = AssistantEngine.answer(
            "tengo mucho que hacer",
            listOf(
                TaskEntity(id = 1, title = "Posponerme", dueAt = dayAt(dayToday, 17), durationMinutes = 60, priority = TaskPriority.LOW),
                TaskEntity(id = 2, title = "V1", dueAt = dayAt(dayToday.minusDays(1), 9), durationMinutes = 120),
                TaskEntity(id = 3, title = "V2", dueAt = dayAt(dayToday.minusDays(2), 9), durationMinutes = 120),
                TaskEntity(id = 4, title = "V3", dueAt = dayAt(dayToday.minusDays(3), 9), durationMinutes = 120),
                TaskEntity(id = 5, title = "V4", dueAt = dayAt(dayToday.minusDays(4), 9), durationMinutes = 120)
            ),
            emptyList(), emptyList(),
            now, dayZone
        )
        assertTrue("nombra la candidata a posponer: ${answer.text}", answer.text.contains("Posponerme"))
        assertEquals("relaciona exactamente la tarea sugerida", listOf(1L), answer.relatedTaskIds)
        assertTrue("no calla las vencidas: ${answer.text}", answer.text.contains("vencida"))
    }

    // ---- dayLoad + olvido silencioso (missed-start) — paridad con c.407 ----
    //
    // c.407 corrigió la tarjeta de resumen de TodayScreen: la carga del día se
    // infla con el trabajo olvidado (missed-start suma a `loadMinutes`, c.247),
    // pero la tarjeta NUNCA nombraba el olvido y caía al consejo dañino "dejar
    // para mañana". El asistente lee ese MISMO veredicto (SummaryEngine) en
    // "¿voy bien?"/"¿da tiempo?", pero su cola sólo nombraba vencidas y
    // compromisos — callaba el olvido que inflaba la carga. Misma mentira por
    // omisión, misma superficie de alto tráfico. Estos tests anclan que la cola
    // de dayLoad ahora nombra el olvido silencioso (igual que overdue/
    // overdueCommitment), sin inventar nada cuando no lo hay.

    @Test fun dayLoad_namesMissedStartWhenLoadInflatedByForgottenWork() {
        // 12:00 → 360 min libres. 1 tarea con hueco hoy YA pasado (start 10:00,
        // 60 min → ventana cerró a las 11:00, ahora es olvido) sin dueAt vencido
        // → missed-start. Su duración (60) cabe con holgura (ON_TRACK), PERO el
        // veredicto no puede callar que hay un compromiso cuyo hueco ya pasó.
        val now = dayAt(dayToday, 12)
        val missed = TaskEntity(
            id = 1, title = "Llamada de ventas",
            startAt = dayAt(dayToday, 10),
            durationMinutes = 60, // ventana 10:00–11:00, ya cerrada a las 12:00
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "¿voy bien hoy?",
            listOf(missed),
            emptyList(), emptyList(),
            now, dayZone
        )
        assertTrue("no calla el olvido silencioso: ${answer.text}",
            answer.text.contains("hueco ya pasó"))
    }

    @Test fun dayLoad_missedStartUrgesRecoverNotDefer() {
        // El consejo para un olvido es recuperarlo o reagendarlo con intención,
        // NO posponerlo (posponer un olvido lo agrava — mostDeferrableTask ya lo
        // excluye). La cola debe advertirlo explícitamente, igual que la tarjeta
        // de c.407 ("no las pospongas").
        val now = dayAt(dayToday, 12)
        val missed = TaskEntity(
            id = 1, title = "Reunión de equipo",
            startAt = dayAt(dayToday, 10),
            durationMinutes = 60,
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "¿da tiempo a todo?",
            listOf(missed),
            emptyList(), emptyList(),
            now, dayZone
        )
        assertTrue("advierte no posponer el olvido: ${answer.text}",
            answer.text.contains("no la pospongas") || answer.text.contains("no las pospongas"))
    }

    @Test fun dayLoad_doesNotInventMissedStartWhenNone() {
        // Sin olvidos (una tarea de hoy aún no empezada no es missed-start):
        // la cola no debe inventar "hueco ya pasó".
        val now = dayAt(dayToday, 9)
        val upcoming = TaskEntity(
            id = 1, title = "Reunión futura",
            startAt = dayAt(dayToday, 15), // empieza más tarde, no olvidada
            durationMinutes = 60,
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "¿voy bien hoy?",
            listOf(upcoming),
            emptyList(), emptyList(),
            now, dayZone
        )
        assertFalse("no inventa olvido sin missed-start: ${answer.text}",
            answer.text.contains("hueco ya pasó"))
    }

    @Test fun dayLoad_overloadedPurelyByMissedStartNamesForgottenWork() {
        // Caso severo (el de c.407 para la tarjeta): saturación EXCLUSIVA por
        // olvidos. 13:00 → 300 min libres (hasta 18:00); 4 tareas con hueco hoy ya
        // pasado (start 10:00, 120 min → ventana cerró a las 12:00, ahora olvido),
        // sin dueAt vencido, sin tarea de hoy posponible → mostDeferrableTask=null.
        // Antes el asistente caía a "Revisa qué posponer o quitar" callando que
        // lo que saturaba eran 4 olvidos — consejo dañino (posponer olvido lo
        // agrava). Ahora nombra el olvido honestamente.
        val now = dayAt(dayToday, 13)
        val missed = (1..4).map { i ->
            TaskEntity(
                id = i.toLong(), title = "Olvido $i",
                startAt = dayAt(dayToday, 10),
                durationMinutes = 120, // ventana 10:00–12:00, ya cerrada a las 13:00; 4×120=480 > 300 libres → OVERLOADED
                status = com.ordia.app.data.local.TaskStatus.PLANNED
            )
        }
        val answer = AssistantEngine.answer(
            "tengo mucho que hacer",
            missed,
            emptyList(), emptyList(),
            now, dayZone
        )
        assertTrue("nombra los olvidos que saturan el día: ${answer.text}",
            answer.text.contains("hueco ya pasó") && answer.text.contains("4"))
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

    // --- c.357: "¿qué hago ahora?" no debe callar un compromiso vencido de una
    // conversación. Quinto olvido: la superficie de MAYOR tráfico (What Now)
    // sugería una tarea y silenciaba por completo los compromisos vencidos —la
    // misma mentira por omisión que c.356 corrigió en agenda "hoy" y c.354 en
    // dayLoad. Paridad con "organiza mi día"/"¿voy bien?"/"resume conversación",
    // que ya anexaban overdueCommitmentTail. La cola de What Now incluso se
    // documenta "Simétrica con … las colas de 'qué hago ahora'" (overdueCountTail
    // l.628) PERO la de compromisos NO estaba: el 5.º olvido.
    @Test fun whatNow_warnsOverdueCommitmentWhenSuggestingTask() {
        // Sugerencia de tarea normal + un compromiso vencido: el usuario pregunta
        // "¿qué hago ahora?" y el asistente le dice qué tarea empezar PERO calla la
        // promesa vencida — exactamente lo que c.356 corrigió en "¿qué tengo hoy?".
        val now = 1_000_000_000_000L
        val task = TaskEntity(id = 1, title = "Revisar correo", priority = TaskPriority.URGENT)
        val commitment = overdueCommitment(10, "te llamo el martes", now - 86_400_000L)
        val answer = AssistantEngine.answer(
            "¿qué hago ahora?",
            listOf(task),
            emptyList(),
            listOf(commitment),
            now
        )
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertTrue("nombra el compromiso vencido: ${answer.text}", answer.text.contains("compromiso"))
        assertTrue("es una cola de conteo, no nombra la acción (paridad con 'organiza mi día'/'¿voy bien?'): ${answer.text}",
            !answer.text.contains("te llamo el martes"))
    }

    @Test fun whatNow_recoversOverdueCommitmentWhenNoPendingTask() {
        // Sin tareas pendientes PERO con un compromiso vencido: antes decía
        // "No encuentro tareas pendientes. Puedes capturar algo nuevo o descansar."
        // — "descansar" frente a una promesa olvidada es la mentira por omisión MÁS
        // severa del 5.º olvido. Debe rutear a overdueCommitmentAnswer (nombrarlo +
        // OPEN_CONVERSATIONS), igual que "¿qué olvidé?"/agenda "hoy" sin tareas.
        val now = 1_000_000_000_000L
        val commitment = overdueCommitment(11, "envío el informe", now - 2 * 86_400_000L)
        val answer = AssistantEngine.answer(
            "¿qué hago ahora?",
            emptyList(),
            emptyList(),
            listOf(commitment),
            now
        )
        assertTrue("nombra el compromiso vencido: ${answer.text}", answer.text.contains("envío el informe"))
        assertTrue("no dice 'descansar' frente a una promesa vencida: ${answer.text}", !answer.text.contains("descansar"))
        assertEquals(AssistantAction.OPEN_CONVERSATIONS, answer.action)
    }

    @Test fun whatNow_warnsOverdueCommitmentAlongsideMissedStart() {
        // Sugerencia + missed-start (cola de tarea) + compromiso vencido (cola de
        // compromiso): ambas colas deben coexistir — no se oculta una detrás de la
        // otra. La sugerida es urgente; el missed-start y el compromiso son
        // recuperación adicional.
        val now = 1_000_000_000_000L
        val urgent = TaskEntity(id = 1, title = "Urgente", priority = TaskPriority.URGENT)
        val missed = TaskEntity(
            id = 2, title = "Llamada agendada",
            startAt = now - 90 * 60_000L, durationMinutes = 30,
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val commitment = overdueCommitment(12, "revisar el contrato", now - 86_400_000L)
        val answer = AssistantEngine.answer(
            "¿Qué hago ahora?",
            listOf(urgent, missed),
            emptyList(),
            listOf(commitment),
            now
        )
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertTrue("nombra el inicio olvidado: ${answer.text}", answer.text.contains("Llamada agendada"))
        assertTrue("nombra el compromiso vencido (cola de conteo): ${answer.text}", answer.text.contains("compromiso"))
    }

    @Test fun whatNow_doesNotInventCommitmentWhenNone() {
        // Guard anti-falso-positivo: sin compromiso vencido, la cola no debe
        // inventar "compromiso" (IA honesta). Una tarea urgente basta.
        val now = 1_000_000_000_000L
        val answer = AssistantEngine.answer(
            "¿qué hago ahora?",
            listOf(TaskEntity(id = 1, title = "Urgente", priority = TaskPriority.URGENT)),
            emptyList(), emptyList(),
            now
        )
        assertTrue("no inventa compromiso sin haberlo: ${answer.text}", !answer.text.contains("compromiso"))
    }

    @Test fun whatNow_doesNotMentionFutureCommitment() {
        // Guard de coherencia: un compromiso FUTURO (no vencido) no es un olvido —
        // mencionarlo en "¿qué hago ahora?" sería ruido, no recuperación. La cola
        // sólo aplica a vencidos (igual que en agenda "hoy" c.356).
        val now = 1_000_000_000_000L
        val task = TaskEntity(id = 1, title = "Revisar correo", priority = TaskPriority.URGENT)
        val future = overdueCommitment(13, "te llamo mañana", now + 86_400_000L)
        val answer = AssistantEngine.answer(
            "¿qué hago ahora?",
            listOf(task),
            emptyList(),
            listOf(future),
            now
        )
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertTrue("no menciona compromiso futuro como si estuviera vencido: ${answer.text}", !answer.text.contains("te llamo mañana"))
    }

    // --- c.358: "plan mínimo" no debe callar un compromiso vencido de una
    // conversación. Sexto olvido: la superficie de planificación mínima ("¿cuál
    // es mi plan mínimo para hoy?") es análoga a "¿qué hago ahora?" — el usuario
    // pide SU plan y el asistente callaba la promesa vencida. Con plan vacío decía
    // "Tu plan mínimo está vacío." (mentira por omisión: el plan NO está vacío si
    // hay una promesa olvidada); con plan con tareas, no anexaba la cola. Paridad
    // con "¿qué hago ahora?" (c.357), agenda "hoy" (c.356) y "organiza mi día".
    @Test fun planMinimo_recoversOverdueCommitmentWhenEmpty() {
        // Sin tareas activas PERO con un compromiso vencido: antes decía "Tu plan
        // mínimo está vacío." — "vacío" frente a una promesa olvidada es la mentira
        // por omisión del 6.º olvido. Debe rutear a overdueCommitmentAnswer
        // (nombrarlo + OPEN_CONVERSATIONS), igual que "¿qué hago ahora?" sin tareas.
        val now = 1_000_000_000_000L
        val commitment = overdueCommitment(20, "envío el informe", now - 2 * 86_400_000L)
        val answer = AssistantEngine.answer(
            "plan mínimo para hoy",
            emptyList(),
            emptyList(),
            listOf(commitment),
            now
        )
        assertTrue("nombra el compromiso vencido: ${answer.text}", answer.text.contains("envío el informe"))
        assertTrue("no dice 'vacío' frente a una promesa vencida: ${answer.text}", !answer.text.contains("vacío"))
        assertEquals(AssistantAction.OPEN_CONVERSATIONS, answer.action)
    }

    @Test fun planMinimo_warnsOverdueCommitmentWhenHasTasks() {
        // Plan con tareas + un compromiso vencido: el usuario pide su plan mínimo y
        // el asistente lo arma PERO calla la promesa vencida — la misma mentira por
        // omisión que c.357 corrigió en "¿qué hago ahora?". Debe anexar la cola de
        // conteo (no nombra la acción: es informativa, la acción primaria sigue
        // siendo mostrar el plan, no doble señalización).
        val now = 1_000_000_000_000L
        val task = TaskEntity(id = 1, title = "Revisar correo", priority = TaskPriority.HIGH)
        val commitment = overdueCommitment(21, "te llamo el martes", now - 86_400_000L)
        val answer = AssistantEngine.answer(
            "plan mínimo",
            listOf(task),
            emptyList(),
            listOf(commitment),
            now
        )
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertTrue("nombra el compromiso vencido (cola de conteo): ${answer.text}", answer.text.contains("compromiso"))
        assertTrue("es cola de conteo, no nombra la acción (paridad con 'organiza mi día'): ${answer.text}",
            !answer.text.contains("te llamo el martes"))
    }

    @Test fun planMinimo_doesNotInventCommitmentWhenNone() {
        // Guard anti-falso-positivo (IA honesta): sin compromiso vencido, la cola no
        // debe inventar "compromiso". Un plan con una tarea basta.
        val now = 1_000_000_000_000L
        val answer = AssistantEngine.answer(
            "plan mínimo para hoy",
            listOf(TaskEntity(id = 1, title = "Revisar correo", priority = TaskPriority.HIGH)),
            emptyList(), emptyList(),
            now
        )
        assertTrue("no inventa compromiso sin haberlo: ${answer.text}", !answer.text.contains("compromiso"))
    }

    // --- "¿tengo algo mañana?" / "¿hay algo el viernes?" — agenda con frases
    // cotidianas que el detector de agenda no reconocía. "que tengo"/"tengo para"/
    // "que hay" eran los únicos disparadores; "tengo algo" y "hay algo" (formas
    // igual de naturales) caían al mensaje genérico, callando la agenda pese a
    // preguntarla. Recupera la intención sin nueva pantalla.

    @Test fun tengoAlgoManana_listsTasksDueTomorrow() {
        val now = 1_000_000_000_000L
        val tomorrow = tomorrowNoon(now)
        val answer = AssistantEngine.answer(
            "¿tengo algo mañana?",
            listOf(
                TaskEntity(id = 1, title = "Reunión de equipo", dueAt = tomorrow),
                TaskEntity(id = 2, title = "Tarea de hoy", dueAt = todayNoon(now))
            ),
            emptyList(), emptyList(),
            now
        )
        assertTrue("nombra las de mañana: ${answer.text}", answer.text.contains("Reunión de equipo"))
        assertTrue("no mezcla con la de hoy: ${answer.text}", !answer.text.contains("Tarea de hoy"))
        assertTrue("relaciona solo las de mañana: ${answer.relatedTaskIds}", answer.relatedTaskIds.contains(1L))
        assertTrue("no incluye la de hoy en ids: ${answer.relatedTaskIds}", !answer.relatedTaskIds.contains(2L))
    }

    @Test fun hayAlgoManana_listsTasksDueTomorrow() {
        val now = 1_000_000_000_000L
        val tomorrow = tomorrowNoon(now)
        val answer = AssistantEngine.answer(
            "¿hay algo mañana?",
            listOf(TaskEntity(id = 1, title = "Cita médica", dueAt = tomorrow)),
            emptyList(), emptyList(),
            now
        )
        assertTrue("nombra la de mañana: ${answer.text}", answer.text.contains("Cita médica"))
        assertEquals(listOf(1L), answer.relatedTaskIds)
    }

    @Test fun tengoAlgoElViernes_listsWeekdayAgenda() {
        val now = 1_000_000_000_000L
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val friday = today.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.FRIDAY))
        val fridayNoon = friday.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val answer = AssistantEngine.answer(
            "¿tengo algo el viernes?",
            listOf(TaskEntity(id = 1, title = "Clase de inglés", dueAt = fridayNoon)),
            emptyList(), emptyList(),
            now
        )
        assertTrue("nombra la del viernes: ${answer.text}", answer.text.contains("Clase de inglés"))
    }

    @Test fun tengoAlgoSinFecha_noEsAgenda_noInventa() {
        // Guard anti-falso-positivo: "tengo algo que hacer" SIN scope de fecha
        // no es una pregunta de agenda (no hay día objetivo). No debe inventar
        // una agenda vacía ni listar todas las tareas como "agenda".
        val now = 1_000_000_000_000L
        val answer = AssistantEngine.answer(
            "¿tengo algo que hacer?",
            listOf(TaskEntity(id = 1, title = "Idea suelta")),
            emptyList(), emptyList(),
            now
        )
        assertTrue("no trata 'tengo algo' sin fecha como agenda: ${answer.text}",
            !answer.relatedTaskIds.contains(1L))
    }
}
