package com.ordia.app.assistant

import com.ordia.app.data.local.FocusSessionEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.domain.DateRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    // --- c.416: "tareas rápidas"/"15 minutos" no debe callar un compromiso vencido
    // de una conversación. Séptimo olvido: la superficie de tareas rápidas ("¿qué
    // puedo hacer de 15 minutos?"/"tareas rápidas") es análoga a "¿qué hago ahora?"
    // y a "plan mínimo" — el usuario pide SU siguiente acción. Sin tareas rápidas
    // decía "No encuentro tareas de 15 minutos o menos." frente a una promesa
    // vencida (mentira por omisión: sí hay algo urgente que hacer, solo que no es
    // rápido); con tareas rápidas, no anexaba la cola. Paridad con "¿qué hago
    // ahora?" (c.357) y "plan mínimo" (c.358). Sin nueva pantalla.
    @Test fun quickTasks_recoversOverdueCommitmentWhenEmpty() {
        // Sin tareas rápidas PERO con un compromiso vencido: antes decía "No
        // encuentro tareas de 15 minutos o menos." — "no encuentro" frente a una
        // promesa olvidada es la mentira por omisión del 7.º olvido. Debe rutear a
        // overdueCommitmentAnswer (nombrarlo + OPEN_CONVERSATIONS), igual que "¿qué
        // hago ahora?" y "plan mínimo" sin tareas.
        val now = 1_000_000_000_000L
        val commitment = overdueCommitment(30, "envío el informe", now - 2 * 86_400_000L)
        val answer = AssistantEngine.answer(
            "tareas de 15 minutos",
            emptyList(),
            emptyList(),
            listOf(commitment),
            now
        )
        assertTrue("nombra el compromiso vencido: ${answer.text}", answer.text.contains("envío el informe"))
        assertTrue("no dice 'No encuentro' frente a una promesa vencida: ${answer.text}", !answer.text.contains("No encuentro"))
        assertEquals(AssistantAction.OPEN_CONVERSATIONS, answer.action)
    }

    @Test fun quickTasks_warnsOverdueCommitmentWhenHasTasks() {
        // Tareas rápidas + un compromiso vencido: el usuario pide tareas rápidas y
        // el asistente las lista PERO calla la promesa vencida — la misma mentira
        // por omisión que c.357/c.358 corrigieron. Debe anexar la cola de conteo
        // (no nombra la acción: es informativa, la acción primaria sigue siendo
        // mostrar las tareas rápidas).
        val now = 1_000_000_000_000L
        val task = TaskEntity(id = 1, title = "Responder un correo", durationMinutes = 10)
        val commitment = overdueCommitment(31, "te llamo el martes", now - 86_400_000L)
        val answer = AssistantEngine.answer(
            "tareas rápidas",
            listOf(task),
            emptyList(),
            listOf(commitment),
            now
        )
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertTrue("nombra el compromiso vencido (cola de conteo): ${answer.text}", answer.text.contains("compromiso"))
        assertTrue("es cola de conteo, no nombra la acción (paridad con 'plan mínimo'): ${answer.text}",
            !answer.text.contains("te llamo el martes"))
    }

    @Test fun quickTasks_doesNotInventCommitmentWhenNone() {
        // Guard anti-falso-positivo (IA honesta): sin compromiso vencido, la cola
        // no debe inventar "compromiso". Una tarea rápida basta.
        val now = 1_000_000_000_000L
        val answer = AssistantEngine.answer(
            "tareas de 15 minutos",
            listOf(TaskEntity(id = 1, title = "Responder un correo", durationMinutes = 10)),
            emptyList(), emptyList(),
            now
        )
        assertTrue("no inventa compromiso sin haberlo: ${answer.text}", !answer.text.contains("compromiso"))
    }

    // --- c.677: filtro por prioridad explícita. "¿tengo algo urgente?"/"¿qué es
    // lo más importante?" caía al menú genérico aunque el usuario YA marcó ese
    // dato (URGENT/HIGH) en la captura. IA honesta: responde con la señal que el
    // usuario mismo puso, no con una inferencia. ---
    @Test fun priorityUrgent_listsOnlyUrgentTier() {
        val normal = TaskEntity(id = 1, title = "Normal")
        val high = TaskEntity(id = 2, title = "Alta", priority = TaskPriority.HIGH)
        val urgent = TaskEntity(id = 3, title = "Entrega crítica", priority = TaskPriority.URGENT)
        val answer = AssistantEngine.answer(
            "¿tengo algo urgente?",
            listOf(normal, high, urgent),
            emptyList(), emptyList()
        )
        assertEquals(listOf(3L), answer.relatedTaskIds)
        assertTrue("habla de urgentes: ${answer.text}", answer.text.contains("urgente"))
        assertTrue("no lista la normal/alta: ${answer.text}", !answer.text.contains("Normal") && !answer.text.contains("Alta"))
    }

    @Test fun priorityImportant_includesHighAndUrgentTiers() {
        val normal = TaskEntity(id = 1, title = "Normal")
        val high = TaskEntity(id = 2, title = "Alta", priority = TaskPriority.HIGH)
        val urgent = TaskEntity(id = 3, title = "Crítica", priority = TaskPriority.URGENT)
        val answer = AssistantEngine.answer(
            "¿qué es lo más importante?",
            listOf(normal, high, urgent),
            emptyList(), emptyList()
        )
        assertEquals(listOf(3L, 2L), answer.relatedTaskIds)
        assertTrue("habla de importantes: ${answer.text}", answer.text.contains("importante"))
        assertTrue("no lista la normal: ${answer.text}", !answer.text.contains("Normal"))
    }

    @Test fun priorityUrgent_emptyIsHonestNotGeneric() {
        val answer = AssistantEngine.answer(
            "¿hay algo urgente?",
            listOf(TaskEntity(id = 1, title = "Normal")),
            emptyList(), emptyList()
        )
        assertTrue("no cae al menú genérico: ${answer.text}", !answer.text.contains("Puedo organizar tu día"))
        assertTrue("vacío honesto: ${answer.text}", answer.text.contains("No tienes tareas marcadas como urgentes"))
    }

    @Test fun priorityUrgent_recoversOverdueCommitmentWhenEmpty() {
        // Sin tareas urgentes PERO con un compromiso vencido: paridad con
        // "tareas de 15 minutos" (c.416) — "no tienes urgentes" frente a una
        // promesa vencida es mentira por omisión. Rutea a la recuperación.
        val now = 1_000_000_000_000L
        val commitment = overdueCommitment(32, "envío el informe", now - 2 * 86_400_000L)
        val answer = AssistantEngine.answer(
            "¿tengo algo urgente?",
            emptyList(),
            emptyList(),
            listOf(commitment),
            now
        )
        assertEquals(AssistantAction.OPEN_CONVERSATIONS, answer.action)
        assertTrue("nombra el compromiso vencido: ${answer.text}", answer.text.contains("envío el informe"))
    }

    // --- c.780: niveles EXACTOS de prioridad (paridad búsqueda↔asistente,
    // sonda diferencial c.779). "tareas de prioridad alta/baja" las filtra la
    // búsqueda por palabra (hasPriorityWord + alta/bajas → HIGH/LOW exacto)
    // pero el asistente caía al menú genérico. La guarda exige la palabra
    // "prioridad" (simétrica a SearchEngine), así "alta" sola ("alta médica")
    // ni "baja" sola ("baja del auto") disparan. ---
    @Test fun priorityAlta_listsOnlyHighTier_exactNotUrgent() {
        val low = TaskEntity(id = 1, title = "Menor", priority = TaskPriority.LOW)
        val high = TaskEntity(id = 2, title = "Alta pero no urgente", priority = TaskPriority.HIGH)
        val urgent = TaskEntity(id = 3, title = "Entrega crítica", priority = TaskPriority.URGENT)
        val answer = AssistantEngine.answer(
            "tareas de prioridad alta",
            listOf(low, high, urgent),
            emptyList(), emptyList()
        )
        assertEquals(listOf(2L), answer.relatedTaskIds)
        assertTrue("habla de prioridad alta: ${answer.text}", answer.text.contains("prioridad alta"))
        assertTrue("no lista low/urgent: ${answer.text}", !answer.text.contains("Menor") && !answer.text.contains("Entrega crítica"))
    }

    @Test fun priorityAlta_invertedWordOrder_altaPrioridad() {
        val high = TaskEntity(id = 2, title = "Alta", priority = TaskPriority.HIGH)
        val answer = AssistantEngine.answer(
            "¿qué tengo en alta prioridad?",
            listOf(TaskEntity(id = 1, title = "Normal"), high),
            emptyList(), emptyList()
        )
        assertEquals(listOf(2L), answer.relatedTaskIds)
        assertTrue("prioridad alta: ${answer.text}", answer.text.contains("prioridad alta"))
    }

    @Test fun priorityBaja_listsOnlyLowTier() {
        val low = TaskEntity(id = 1, title = "Paseo", priority = TaskPriority.LOW)
        val answer = AssistantEngine.answer(
            "tareas de prioridad baja",
            listOf(TaskEntity(id = 2, title = "Normal"), low),
            emptyList(), emptyList()
        )
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertTrue("prioridad baja: ${answer.text}", answer.text.contains("prioridad baja"))
        assertTrue("no lista normal: ${answer.text}", !answer.text.contains("Normal"))
    }

    @Test fun priorityAlta_wordGuard_noPriorityWord_noSeDispara() {
        // "alta" sin la palabra "prioridad" NO abre el filtro ("alta médica",
        // "alta en el sistema"); simétrico a la guarda de búsqueda. La
        // consulta sigue cayendo al menú genérico, no a un listado inventado.
        val high = TaskEntity(id = 2, title = "Alta", priority = TaskPriority.HIGH)
        val answer = AssistantEngine.answer("alta médica", listOf(high), emptyList(), emptyList())
        assertTrue("no lista por prioridad: ${answer.text}", !answer.text.contains("prioridad alta"))
        assertTrue("cae al menú honesto: ${answer.text}", answer.text.contains("Puedo organizar tu día"))
    }

    @Test fun priorityBaja_emptyIsHonestNotGeneric() {
        val answer = AssistantEngine.answer(
            "tareas de prioridad baja",
            listOf(TaskEntity(id = 1, title = "Normal")),
            emptyList(), emptyList()
        )
        assertTrue("no cae al menú genérico: ${answer.text}", !answer.text.contains("Puedo organizar tu día"))
        assertTrue("vacío honesto: ${answer.text}", answer.text.contains("No tienes tareas de prioridad baja"))
    }

    @Test fun priorityAlta_recoversOverdueCommitmentWhenEmpty() {
        // Paridad con la rama c.677: sin ese nivel pero CON un compromiso
        // vencido, "no hay de prioridad alta" sería mentira por omisión.
        val now = 1_000_000_000_000L
        val commitment = overdueCommitment(33, "envío el informe", now - 2 * 86_400_000L)
        val answer = AssistantEngine.answer(
            "tareas de prioridad alta",
            emptyList(),
            emptyList(),
            listOf(commitment),
            now
        )
        assertEquals(AssistantAction.OPEN_CONVERSATIONS, answer.action)
        assertTrue("nombra el compromiso vencido: ${answer.text}", answer.text.contains("envío el informe"))
    }

    // --- Sobrecarga ("estoy abrumado/agobiado": c.702): ante una señal de
    // saturación emocional la respuesta debe REDUCIR carga, no listar ni dar el
    // menú de capacidades. Una única acción (la ordenada por What Now), el
    // resto queda esperando. Determinista: reusa WhatNowEngine, sin random.

    @Test fun overwhelmed_suggestsSingleTaskNotMenu() {
        val answer = AssistantEngine.answer(
            "Estoy abrumado",
            listOf(
                TaskEntity(id = 1, title = "Normal"),
                TaskEntity(id = 2, title = "Urgente", priority = TaskPriority.URGENT)
            ),
            emptyList(), emptyList()
        )
        assertEquals(listOf(2L), answer.relatedTaskIds)
        assertTrue("nombra la única cosa: ${answer.text}", answer.text.contains("Urgente"))
        assertTrue("no cae al menú genérico: ${answer.text}", !answer.text.contains("Puedo organizar tu día"))
        assertTrue("cuenta el resto honestamente: ${answer.text}", answer.text.contains("queda 1"))
    }

    @Test fun overwhelmed_agobiadoVariantRoutes() {
        val answer = AssistantEngine.answer(
            "Estoy agobiada con tanto por hacer",
            listOf(TaskEntity(id = 1, title = "Solo")),
            emptyList(), emptyList()
        )
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertTrue("no cae al menú genérico: ${answer.text}", !answer.text.contains("Puedo organizar tu día"))
    }

    @Test fun overwhelmed_emptyIsHonestNotGeneric() {
        val answer = AssistantEngine.answer(
            "Estoy abrumado",
            emptyList(), emptyList(), emptyList()
        )
        assertTrue("no cae al menú genérico: ${answer.text}", !answer.text.contains("Puedo organizar tu día"))
        assertTrue("vacío honesto: ${answer.text}", answer.text.contains("No encuentro tareas pendientes"))
    }

    @Test fun overwhelmed_recoversOverdueCommitmentWhenEmpty() {
        // Paridad con c.357/c.416/c.680: vacío + promesa vencida → recuperación.
        val now = 1_000_000_000_000L
        val commitment = overdueCommitment(41, "envío el informe", now - 2 * 86_400_000L)
        val answer = AssistantEngine.answer(
            "Estoy abrumado",
            emptyList(),
            emptyList(),
            listOf(commitment),
            now
        )
        assertEquals(AssistantAction.OPEN_CONVERSATIONS, answer.action)
        assertTrue("nombra el compromiso vencido: ${answer.text}", answer.text.contains("envío el informe"))
    }

    // --- Recomendación/decisión (Cluster sonda assistant: "qué me recomiendas",
    // "recomiéndame algo", "ayúdame a decidir", "cuál me conviene hacer") — el
    // usuario pide UNA sugerencia; el asistente debe usar What Now, no listar
    // capacidades. Paridad con la familia overwhelmed (una sola cosa, resto
    // contado, vacío honesto, recuperación paridad c.357/c.416/c.680).
    @Test fun recommendation_suggestsNextTaskNotMenu() {
        val answer = AssistantEngine.answer(
            "¿Qué me recomiendas?",
            listOf(
                TaskEntity(id = 1, title = "Normal"),
                TaskEntity(id = 2, title = "Urgente", priority = TaskPriority.URGENT)
            ),
            emptyList(), emptyList()
        )
        assertEquals(listOf(2L), answer.relatedTaskIds)
        assertTrue("nombra la sugerida: ${answer.text}", answer.text.contains("Urgente"))
        assertTrue("no cae al menú genérico: ${answer.text}", !answer.text.contains("Puedo organizar tu día"))
        assertTrue("cuenta el resto honestamente: ${answer.text}", answer.text.contains("queda 1"))
    }

    @Test fun recommendation_decidirVariantRoutes() {
        val answer = AssistantEngine.answer(
            "Ayúdame a decidir",
            listOf(TaskEntity(id = 1, title = "Pagar la renta")),
            emptyList(), emptyList()
        )
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertTrue("no cae al menú genérico: ${answer.text}", !answer.text.contains("Puedo organizar tu día"))
        assertTrue("nombra la sugerida: ${answer.text}", answer.text.contains("Pagar la renta"))
    }

    @Test fun recommendation_convengoVariantRoutes() {
        val answer = AssistantEngine.answer(
            "¿Cuál me conviene hacer?",
            listOf(TaskEntity(id = 1, title = "Enviar el informe")),
            emptyList(), emptyList()
        )
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertTrue("no cae al menú genérico: ${answer.text}", !answer.text.contains("Puedo organizar tu día"))
    }

    @Test fun recommendation_emptyIsHonestNotGeneric() {
        val answer = AssistantEngine.answer(
            "Recomiéndame algo",
            emptyList(), emptyList(), emptyList()
        )
        assertTrue("no cae al menú genérico: ${answer.text}", !answer.text.contains("Puedo organizar tu día"))
        assertTrue("vacío honesto: ${answer.text}", answer.text.contains("No encuentro tareas pendientes"))
    }

    @Test fun recommendation_recoversOverdueCommitmentWhenEmpty() {
        // Paridad con c.357/c.416/c.680: vacío + promesa vencida → recuperación.
        val now = 1_000_000_000_000L
        val commitment = overdueCommitment(41, "envío el informe", now - 2 * 86_400_000L)
        val answer = AssistantEngine.answer(
            "¿Qué me recomiendas?",
            emptyList(),
            emptyList(),
            listOf(commitment),
            now
        )
        assertEquals(AssistantAction.OPEN_CONVERSATIONS, answer.action)
        assertTrue("nombra el compromiso vencido: ${answer.text}", answer.text.contains("envío el informe"))
    }

    // --- Posponer/defer (cluster sonda assistant: "qué puedo dejar para
    // mañana", "puedo posponer algo", "qué no puedo dejar para después",
    // "qué pasa si pospongo") — el usuario pregunta QUÉ puede posponerse; el
    // asistente debe nombrar la tarea de hoy MÁS posponible (fuente única:
    // SummaryEngine.deferralCandidate, la misma lógica que el veredicto
    // OVERLOADED nombra vía deferralSuggestion), no listar capacidades.
    // Paridad familia lie-by-omission: vacío honesto (NUNCA menú); vacío +
    // promesa vencida → recuperación (c.357/c.416/c.680).
    @Test fun deferral_suggestsMostDeferrableNotMenu() {
        val now = 1_787_140_800_000L // 2026-08-19T12:00:00Z (mediodía, sin borde de medianoche)
        val answer = AssistantEngine.answer(
            "¿Qué puedo dejar para mañana?",
            listOf(
                TaskEntity(id = 1, title = "Informe urgente", priority = TaskPriority.URGENT, dueAt = now + 3_600_000, durationMinutes = 20),
                TaskEntity(id = 2, title = "Ordenar el archivo", priority = TaskPriority.LOW, dueAt = now + 7_200_000, durationMinutes = 120)
            ),
            emptyList(), emptyList(), now
        )
        assertEquals(listOf(2L), answer.relatedTaskIds)
        assertTrue("nombra la más posponible: ${answer.text}", answer.text.contains("Ordenar el archivo"))
        assertTrue("no cae al menú genérico: ${answer.text}", !answer.text.contains("Puedo organizar tu día"))
    }

    @Test fun deferral_posponerVariantRoutes() {
        val now = 1_787_140_800_000L
        val answer = AssistantEngine.answer(
            "¿Puedo posponer algo?",
            listOf(TaskEntity(id = 1, title = "Revisar el borrador", dueAt = now + 3_600_000, durationMinutes = 30)),
            emptyList(), emptyList(), now
        )
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertTrue("no cae al menú genérico: ${answer.text}", !answer.text.contains("Puedo organizar tu día"))
        assertTrue("nombra la posponible: ${answer.text}", answer.text.contains("Revisar el borrador"))
    }

    @Test fun deferral_overdueTodayIsNotDeferrable() {
        // Una vencida NO es posponible (posponer lo vencido lo agrava); nombra la sana.
        val now = 1_787_140_800_000L
        val answer = AssistantEngine.answer(
            "¿Qué no puedo dejar para después?",
            listOf(
                TaskEntity(id = 1, title = "Pago vencido", priority = TaskPriority.URGENT, dueAt = now - 86_400_000),
                TaskEntity(id = 2, title = "Clasificar fotos", priority = TaskPriority.LOW, dueAt = now + 3_600_000, durationMinutes = 45)
            ),
            emptyList(), emptyList(), now
        )
        assertEquals(listOf(2L), answer.relatedTaskIds)
        assertTrue("nombra la sana, no la vencida: ${answer.text}", answer.text.contains("Clasificar fotos"))
    }

    @Test fun deferral_emptyIsHonestNotGeneric() {
        val answer = AssistantEngine.answer(
            "¿Qué puedo dejar para mañana?",
            emptyList(), emptyList(), emptyList()
        )
        assertTrue("no cae al menú genérico: ${answer.text}", !answer.text.contains("Puedo organizar tu día"))
        assertTrue("vacío honesto: ${answer.text}", answer.text.contains("No tienes tareas"))
    }

    @Test fun deferral_recoversOverdueCommitmentWhenEmpty() {
        // Paridad con c.357/c.416/c.680: vacío + promesa vencida → recuperación.
        val now = 1_000_000_000_000L
        val commitment = overdueCommitment(41, "envío el informe", now - 2 * 86_400_000L)
        val answer = AssistantEngine.answer(
            "¿Puedo posponer algo?",
            emptyList(),
            emptyList(),
            listOf(commitment),
            now
        )
        assertEquals(AssistantAction.OPEN_CONVERSATIONS, answer.action)
        assertTrue("nombra el compromiso vencido: ${answer.text}", answer.text.contains("envío el informe"))
    }

    // --- Tiempo invertido (cluster sonda assistant: "en qué gasto mi tiempo",
    // "en qué estoy gastando tiempo") — el usuario pregunta EN QUÉ invirtió su
    // tiempo; el asistente debe responder con datos REALES (sesiones de enfoque
    // completadas de hoy agregadas por tarea, fuente única: FocusRecap), no
    // listar capacidades. Paridad familia lie-by-omission: vacío honesto
    // (NUNCA menú). IA honesta: solo agrega minutos registrados, no infiere.
    @Test fun timeSpent_namesTopTasksWithMinutesNotMenu() {
        val now = 1_787_140_800_000L // 2026-08-19T12:00:00Z (mediodía UTC, sin borde de medianoche)
        val answer = AssistantEngine.answer(
            "¿En qué gasto mi tiempo?",
            listOf(
                TaskEntity(id = 1, title = "Informe trimestral"),
                TaskEntity(id = 2, title = "Ordenar el archivo")
            ),
            emptyList(), emptyList(), now,
            zone = java.time.ZoneOffset.UTC,
            focusSessions = listOf(
                FocusSessionEntity(id = 1, taskId = 1, startedAt = now - 5 * 3_600_000L, actualMinutes = 45, completed = true),
                FocusSessionEntity(id = 2, taskId = 1, startedAt = now - 3 * 3_600_000L, actualMinutes = 30, completed = true),
                FocusSessionEntity(id = 3, taskId = 2, startedAt = now - 2 * 3_600_000L, actualMinutes = 20, completed = true)
            )
        )
        assertTrue("total del día: ${answer.text}", answer.text.contains("1 h 35 min"))
        assertTrue("nombra la tarea con más minutos: ${answer.text}", answer.text.contains("Informe trimestral"))
        assertTrue("agrega las sesiones de la misma tarea (75 min): ${answer.text}", answer.text.contains("1 h 15 min"))
        assertTrue("no cae al menú genérico: ${answer.text}", !answer.text.contains("Puedo organizar tu día"))
    }

    @Test fun timeSpent_seMeVaVariantRoutes() {
        val now = 1_787_140_800_000L
        val answer = AssistantEngine.answer(
            "¿En qué se me va el tiempo?",
            listOf(TaskEntity(id = 1, title = "Informe trimestral")),
            emptyList(), emptyList(), now,
            zone = java.time.ZoneOffset.UTC,
            focusSessions = listOf(
                FocusSessionEntity(id = 1, taskId = 1, startedAt = now - 3_600_000L, actualMinutes = 40, completed = true)
            )
        )
        assertTrue("nombra la tarea: ${answer.text}", answer.text.contains("Informe trimestral"))
        assertTrue("no cae al menú genérico: ${answer.text}", !answer.text.contains("Puedo organizar tu día"))
    }

    @Test fun timeSpent_ignoresIncompleteAndOtherDays() {
        val now = 1_787_140_800_000L
        val answer = AssistantEngine.answer(
            "¿En qué estoy gastando tiempo?",
            listOf(TaskEntity(id = 1, title = "Informe trimestral"), TaskEntity(id = 2, title = "Ayer")),
            emptyList(), emptyList(), now,
            zone = java.time.ZoneOffset.UTC,
            focusSessions = listOf(
                FocusSessionEntity(id = 1, taskId = 1, startedAt = now - 2 * 3_600_000L, actualMinutes = 45, completed = false),
                FocusSessionEntity(id = 2, taskId = 2, startedAt = now - 30 * 3_600_000L, actualMinutes = 90, completed = true)
            )
        )
        // Ni la sesión en curso ni la de ayer cuentan: el día no registra foco
        // completado todavía → respuesta honesta, no datos inflados ni menú.
        assertTrue("vacío honesto: ${answer.text}", answer.text.contains("no registras"))
        assertTrue("no infla con ayer: ${answer.text}", !answer.text.contains("Ayer"))
        assertTrue("no cae al menú genérico: ${answer.text}", !answer.text.contains("Puedo organizar tu día"))
    }

    @Test fun timeSpent_emptyIsHonestNotGeneric() {
        val now = 1_787_140_800_000L
        val answer = AssistantEngine.answer(
            "¿En qué gasto mi tiempo?",
            listOf(TaskEntity(id = 1, title = "Informe trimestral")),
            emptyList(), emptyList(), now,
            zone = java.time.ZoneOffset.UTC,
            focusSessions = emptyList()
        )
        assertTrue("vacío honesto: ${answer.text}", answer.text.contains("no registras"))
        assertTrue("no cae al menú genérico: ${answer.text}", !answer.text.contains("Puedo organizar tu día"))
    }

    @Test fun timeSpent_limitsToThreeTopTasks() {
        val now = 1_787_140_800_000L
        val tasks = (1L..4L).map { TaskEntity(id = it, title = "Tarea $it") }
        val sessions = (1L..4L).map { FocusSessionEntity(id = it, taskId = it, startedAt = now - it * 3_600_000L, actualMinutes = (5 - it).toInt() * 10, completed = true) }
        val answer = AssistantEngine.answer(
            "¿En qué gasto mi tiempo?",
            tasks, emptyList(), emptyList(), now,
            zone = java.time.ZoneOffset.UTC,
            focusSessions = sessions
        )
        assertTrue("top 1: ${answer.text}", answer.text.contains("Tarea 1"))
        assertTrue("top 3: ${answer.text}", answer.text.contains("Tarea 3"))
        assertTrue("solo nombra 3 (respuesta corta): ${answer.text}", !answer.text.contains("Tarea 4"))
    }


    // --- Tiempo libre (Cluster C sonda assistant; c.416 cubre la forma literal
    // "tareas de 15 minutos": "tengo un rato/tiempo/hueco" o "tengo N minutos"
    // caía al menú genérico). Reusa la rama de tareas cortas con ventana del
    // usuario EXPLÍCITA (N minutos/horas/diez/veinte/media hora) o 15 min si
    // es suelta. Determinista: usa WhatNowEngine.ordered, no inferencia de IA.

    @Test fun freeTime_ratoSuggestsShortTasksNotMenu() {
        val answer = AssistantEngine.answer(
            "tengo un rato libre",
            listOf(
                TaskEntity(id = 1, title = "Responder un correo", durationMinutes = 10),
                TaskEntity(id = 2, title = "Informe largo", durationMinutes = 60)
            ),
            emptyList(), emptyList()
        )
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertTrue("no cae al menú genérico: ${answer.text}", !answer.text.contains("Puedo organizar tu día"))
    }

    @Test fun freeTime_digitWindowFiltersByExplicitMinutes() {
        val quarter = TaskEntity(id = 1, title = "Revisión corta", durationMinutes = 10)
        val half = TaskEntity(id = 2, title = "Trámite del banco", durationMinutes = 30)
        val answer = AssistantEngine.answer(
            "tengo 20 minutos",
            listOf(quarter, half),
            emptyList(), emptyList()
        )
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertTrue("menciona la ventana: ${answer.text}", answer.text.contains("20 minutos"))
        assertTrue("no lista la de 30 min: ${answer.text}", !answer.text.contains("Trámite del banco"))
    }

    @Test fun freeTime_wordWindowAndUnaHora() {
        val quickT = TaskEntity(id = 1, title = "Corta", durationMinutes = 15)
        val long = TaskEntity(id = 2, title = "Larga", durationMinutes = 45)
        val veinte = AssistantEngine.answer(
            "tengo veinte minutos", listOf(quickT, long), emptyList(), emptyList()
        )
        assertEquals(listOf(1L), veinte.relatedTaskIds)
        // "media hora" (30 min) excluye la de 45; "una hora" (60) la incluye.
        val media = AssistantEngine.answer(
            "tengo media hora libre", listOf(quickT, long), emptyList(), emptyList()
        )
        assertEquals(listOf(1L), media.relatedTaskIds)
        val unaHora = AssistantEngine.answer(
            "tengo una hora libre", listOf(quickT, long), emptyList(), emptyList()
        )
        assertEquals(listOf(1L, 2L), unaHora.relatedTaskIds)
    }

    @Test fun freeTime_nothingFitsIsHonestNotMenu() {
        val long = TaskEntity(id = 1, title = "Migrar servidor", durationMinutes = 120)
        val answer = AssistantEngine.answer(
            "tengo 10 minutos",
            listOf(long),
            emptyList(), emptyList()
        )
        assertTrue("no cae al menú genérico: ${answer.text}", !answer.text.contains("Puedo organizar tu día"))
        assertTrue("vacío honesto: ${answer.text}", answer.text.contains("Nada te cabe"))
    }

    @Test fun freeTime_recoversOverdueCommitmentWhenEmpty() {
        // Paridad con c.357/c.416/c.680/c.702: vacío + promesa vencida → recuperación.
        val now = 1_000_000_000_000L
        val commitment = overdueCommitment(42, "envío el informe", now - 2 * 86_400_000L)
        val answer = AssistantEngine.answer(
            "tengo un hueco",
            emptyList(),
            emptyList(),
            listOf(commitment),
            now
        )
        assertEquals(AssistantAction.OPEN_CONVERSATIONS, answer.action)
        assertTrue("nombra el compromiso vencido: ${answer.text}", answer.text.contains("envío el informe"))
    }

    // --- c.422: el menú genérico (consulta no reconocida) no debe callar un
    // compromiso vencido. Octavo olvido de la familia "lie-by-omission": el catch-all
    // es la superficie de mayor tránsito para un usuario confundido (escribió algo que
    // el asistente no entiende) y justo ahí callaba la promesa olvidada. Paridad con
    // las superficies que muestran una lista (c.357/c.358/c.421): anexa la cola de
    // conteo (no secuestra el menú de descubrimiento). Sin nueva pantalla.
    @Test fun genericFallback_warnsOverdueCommitmentWhenConfused() {
        // El usuario escribe algo que no casa con ninguna rama ("asdf"). Hay un
        // compromiso vencido: antes el menú de capacidades callaba la promesa —
        // el usuario confundido no sabía qué preguntar Y no se enteraba de que
        // debía algo. Debe anexar la cola de conteo.
        val now = 1_000_000_000_000L
        val commitment = overdueCommitment(32, "te paso el presupuesto", now - 3 * 86_400_000L)
        val answer = AssistantEngine.answer(
            "asdf qwerty",
            emptyList(),
            emptyList(),
            listOf(commitment),
            now
        )
        assertTrue("el menú genérico no calla el compromiso vencido: ${answer.text}",
            answer.text.contains("compromiso"))
        assertTrue("preserva el menú de capacidades (no secuestra): ${answer.text}",
            answer.text.contains("organizar tu día"))
    }

    @Test fun genericFallback_doesNotInventCommitmentWhenNone() {
        // Guard anti-falso-positivo (IA honesta): sin compromiso vencido, el menú
        // genérico no debe inventar "compromiso".
        val now = 1_000_000_000_000L
        val answer = AssistantEngine.answer(
            "asdf qwerty",
            emptyList(), emptyList(), emptyList(),
            now
        )
        assertTrue("no inventa compromiso sin haberlo: ${answer.text}",
            !answer.text.contains("compromiso"))
    }

    @Test fun queMeComprometi_nombraCompromisoVencidoMasUrgente() {
        // "¿qué me comprometí?" — la forma cotidiana de pedir recordar lo
        // prometido — antes caía al menú genérico. Con un compromiso vencido debe
        // nombrarlo (no callarlo) y abrir Conversaciones, igual que la cola de
        // olvidos/vencidas.
        val now = 1_000_000_000_000L
        val overdue = overdueCommitment(40, "te paso el presupuesto", now - 3 * 86_400_000L)
        val answer = AssistantEngine.answer(
            "¿Qué me comprometí?",
            emptyList(), emptyList(), listOf(overdue),
            now
        )
        assertTrue("nombra la promesa vencida: ${answer.text}", answer.text.contains("te paso el presupuesto"))
        assertEquals("abre Conversaciones para convertir/descartar: ${answer.text}",
            AssistantAction.OPEN_CONVERSATIONS, answer.action)
    }

    @Test fun quePrometi_listaPendientesNoVencidos() {
        // "¿qué prometí?" con compromisos pendientes PERO no vencidos: antes el
        // menú genérico no los mencionaba. Debe dar el conteo y abrir
        // Conversaciones (no inventa "vencido" si no lo hay).
        val now = 1_000_000_000_000L
        // dueAt futuro → pendiente pero NO vencido
        val futuro = now + 2 * 86_400_000L
        val pendientes = listOf(
            overdueCommitment(41, "enviar el informe", futuro),
            overdueCommitment(42, "llamar a ana", futuro)
        )
        val answer = AssistantEngine.answer(
            "¿Qué prometí?",
            emptyList(), emptyList(), pendientes,
            now
        )
        assertTrue("da el conteo de pendientes: ${answer.text}", answer.text.contains("2 compromisos pendientes"))
        assertEquals("abre Conversaciones: ${answer.text}", AssistantAction.OPEN_CONVERSATIONS, answer.action)
        assertTrue("no inventa 'vencido' si no lo hay: ${answer.text}", !answer.text.contains("vencido"))
    }

    @Test fun quePrometi_honestoCuandoNoHayCompromisos() {
        // Guard IA-honesta: sin compromisos pendientes ni vencidos, "¿qué prometí?"
        // dice "no tienes compromisos pendientes" — no inventa, no cae al menú.
        val now = 1_000_000_000_000L
        val answer = AssistantEngine.answer(
            "¿Qué prometí?",
            emptyList(), emptyList(), emptyList(),
            now
        )
        assertTrue("mensaje honesto sin compromisos: ${answer.text}",
            answer.text.contains("No tienes compromisos pendientes"))
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

    // --- "se me pasó": la frase coloquial del olvido, en el asistente ---
    // Búsqueda↔asistente deben hablar el MISMO vocabulario coloquial que el
    // usuario (paridad, misión). La frase "¿qué se me pasó?" es la forma MÁS
    // natural de preguntar por lo que se te olvidó, pero el guard de olvido
    // solo miraba subcadena "olvid", así que caía al fallback de agenda
    // ("No es una consulta concreta") aunque el guardián y la búsqueda sí
    // recuperaban huecos pasados ([TaskRules.isMissedStart]). Detección por
    // frase normalizada, nunca por el token suelto "paso" (que es stop-word
    // justo para no secuestrar detecciones).
    @Test fun forgottenIntent_seMePaso_phraseNamesMissedStart() {
        val now = 1_000_000_000_000L
        val missedStart = TaskEntity(
            id = 5, title = "Llamada agendada",
            startAt = now - 90 * 60_000L,
            durationMinutes = 30,
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "¿qué se me pasó?",
            listOf(missedStart),
            emptyList(), emptyList(),
            now
        )
        assertTrue("nombra el compromiso del que se pasó: ${answer.text}", answer.text.contains("Llamada agendada"))
        assertEquals(AssistantAction.RUN_REPLAN, answer.action)
        assertEquals(listOf(5L), answer.relatedTaskIds)
    }

    @Test fun forgottenIntent_seMePasaron_plural_phraseNamesMissedStart() {
        val now = 1_000_000_000_000L
        val missedStart = TaskEntity(
            id = 6, title = "Cita médica",
            startAt = now - 120 * 60_000L,
            durationMinutes = 20,
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "¿qué se me pasaron?",
            listOf(missedStart),
            emptyList(), emptyList(),
            now
        )
        assertTrue("nombra el compromiso del que se pasaron: ${answer.text}", answer.text.contains("Cita médica"))
        assertEquals(listOf(6L), answer.relatedTaskIds)
    }

    // c.786: extensión simétrica a primera persona plural ("se nos pasó").
    // c.785 solo cubrió 1.ª singular ("se me"); la forma plural caía al
    // fallback del menú (mentira por omisión en la recuperación).
    @Test fun forgottenIntent_seNosPaso_phraseNamesMissedStart() {
        val now = 1_000_000_000_000L
        val missedStart = TaskEntity(
            id = 7, title = "Recoger la documentación",
            startAt = now - 90 * 60_000L,
            durationMinutes = 15,
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "¿qué se nos pasó?",
            listOf(missedStart),
            emptyList(), emptyList(),
            now
        )
        assertTrue("nombra el compromiso del que se nos pasó: ${answer.text}", answer.text.contains("Recoger la documentación"))
        assertEquals(listOf(7L), answer.relatedTaskIds)
    }

    // Guard: "paso" suelto (p. ej. "el paso decisivo") NO es intención de olvido
    // cuando la frase "se me pas" no está presente.
    @Test fun pasoGuard_doesNotTriggerForgottenIntent() {
        val now = 1_000_000_000_000L
        val missedStart = TaskEntity(
            id = 9, title = "Llamada agendada",
            startAt = now - 90 * 60_000L,
            durationMinutes = 30,
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "hacer el paso decisivo",
            listOf(missedStart),
            emptyList(), emptyList(),
            now
        )
        assertTrue(answer.relatedTaskIds.isEmpty() || answer.relatedTaskIds.none { it == 9L })
        assertTrue(answer.action != AssistantAction.RUN_REPLAN)
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

    // c.438: "¿qué tengo atrasado?" / "atrasadas" — la palabra más natural en
    // español para "overdue" — caía al MENÚ GENÉRICO en vez de la rama de
    // recuperación (que nombra la vencida más urgente y ofrece reprogramar).
    // La rama sólo reconocía "vencid"; "atrasad" es disjunto y se perdía.
    @Test fun atrasado_recoversMostUrgentOverdueTask() {
        val now = 1_000_000_000_000L
        val overdue = TaskEntity(id = 7, title = "Pagar factura", dueAt = now - 3 * 86_400_000L)
        val answer = AssistantEngine.answer(
            "¿qué tengo atrasado?",
            listOf(overdue),
            emptyList(), emptyList(),
            now
        )
        assertTrue("nombra la atrasada: ${answer.text}", answer.text.contains("Pagar factura"))
        assertEquals("ofrece reprogramar como la rama de olvido: ${answer.action}", AssistantAction.RUN_REPLAN, answer.action)
    }

    @Test fun atrasadas_recoversOverdueCount() {
        // Plural "atrasadas" también es intención de recuperación: nombra/enumera,
        // no cae al menú genérico.
        val now = 1_000_000_000_000L
        val a = TaskEntity(id = 1, title = "Atrasada A", dueAt = now - 2 * 86_400_000L)
        val b = TaskEntity(id = 2, title = "Atrasada B", dueAt = now - 86_400_000L)
        val answer = AssistantEngine.answer(
            "atrasadas",
            listOf(a, b),
            emptyList(), emptyList(),
            now
        )
        assertFalse("no cae al menú genérico: ${answer.text}", answer.text.contains("Puedo organizar tu día"))
        assertEquals(AssistantAction.RUN_REPLAN, answer.action)
    }

    @Test fun atrasado_doesNotInventWhenNothingOverdue() {
        // Guard IA-honesta: sin vencidas, "atrasado" no inventa olvidos.
        val now = 1_000_000_000_000L
        val plain = TaskEntity(id = 1, title = "Normal sin fecha")
        val answer = AssistantEngine.answer(
            "atrasado",
            listOf(plain),
            emptyList(), emptyList(),
            now
        )
        assertFalse("no inventa atrasos: ${answer.text}", answer.text.contains("Pagar factura"))
        assertTrue("dice honestamente que no hay: ${answer.text}",
            answer.text.contains("No tienes tareas vencidas") || answer.text.contains("olvidad"))
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

    // --- Formas cotidianas de "¿qué hago ahora?" (c.415) ---
    // La consulta de mayor valor sólo reconocía "qué hago ahora"/"siguiente acción".
    // Formas comunes ("¿qué sigue?", "¿qué me toca?", "¿qué hago?") caían al mensaje
    // genérico y el usuario perdía la sugerencia de What Now.

    private val whatNowUrgent = listOf(TaskEntity(id = 1, title = "Urgente", priority = TaskPriority.URGENT))

    @Test fun whatNow_recognizesQueSigue() {
        val answer = AssistantEngine.answer("¿Qué sigue?", whatNowUrgent, emptyList(), emptyList())
        assertEquals("¿qué sigue? debe sugerir la tarea urgente: ${answer.text}", listOf(1L), answer.relatedTaskIds)
    }

    @Test fun whatNow_recognizesQueMeToca() {
        val answer = AssistantEngine.answer("¿Qué me toca hacer?", whatNowUrgent, emptyList(), emptyList())
        assertEquals("¿qué me toca? debe sugerir la tarea urgente: ${answer.text}", listOf(1L), answer.relatedTaskIds)
    }

    @Test fun whatNow_recognizesQueTengoQueHacer() {
        // "¿qué tengo que hacer?" — la forma más cotidiana de preguntar por la
        // siguiente tarea — antes caía al menú genérico por no contener
        // "hago"/"toca"/"sigue". Debe sugerir la tarea urgente, igual que "qué hago".
        val answer = AssistantEngine.answer("¿Qué tengo que hacer?", whatNowUrgent, emptyList(), emptyList())
        assertEquals("¿qué tengo que hacer? debe sugerir la urgente: ${answer.text}", listOf(1L), answer.relatedTaskIds)
    }

    @Test fun whatNow_recognizesQueMeFaltaPorHacer() {
        // "¿qué me falta por hacer?" nombra exactamente lo pendiente; antes caía al
        // menú genérico. Debe sugerir la tarea urgente.
        val answer = AssistantEngine.answer("¿Qué me falta por hacer?", whatNowUrgent, emptyList(), emptyList())
        assertEquals("¿qué me falta por hacer? debe sugerir la urgente: ${answer.text}", listOf(1L), answer.relatedTaskIds)
    }

    @Test fun whatNow_queTengoQueHacer_conTimeframeVaAAgendaNoWhatNow() {
        // "¿qué tengo que hacer mañana?" lleva timeframe: la agenda debe ganar
        // sobre la rama What Now. Con una tarea con dueAt mañana, agenda debe
        // listarla; sin relatedTaskIds genéricos de "menú".
        val manana = System.currentTimeMillis() + 25 * 60 * 60 * 1000L
        val tareaManana = TaskEntity(id = 5, title = "Entrega", priority = TaskPriority.URGENT, dueAt = manana)
        val answer = AssistantEngine.answer("¿qué tengo que hacer mañana?", listOf(tareaManana), emptyList(), emptyList())
        // agenda lista la tarea del día; el menú genérico diría "Puedo organizar...".
        assertFalse("con timeframe va a agenda, no a menú genérico: ${answer.text}",
            answer.text.contains("Puedo organizar"))
    }

    @Test fun whatNow_recognizesBareQueHago() {
        // La forma desnuda "¿qué hago?" (sin "ahora") antes no se reconocía.
        val answer = AssistantEngine.answer("¿Qué hago?", whatNowUrgent, emptyList(), emptyList())
        assertEquals("¿qué hago? debe sugerir la tarea urgente: ${answer.text}", listOf(1L), answer.relatedTaskIds)
    }

    @Test fun whatNow_verbsAloneAreNotWhatNow() {
        // El verbo suelto NO es intención: "sigue" y "toca" sin "qué" no deben
        // disparar What Now (evita falsos positivos sobre "sigue lloviendo", etc.).
        // Con tareas activas, What Now sugeriría una; el mensaje genérico no lista
        // relatedTaskIds, así que verificamos que NO se activa la rama de sugerencia.
        for (q in listOf("sigue lloviendo", "me toca el turno")) {
            val answer = AssistantEngine.answer(q, whatNowUrgent, emptyList(), emptyList())
            assertTrue("'$q' no debe activar What Now: ${answer.text}", answer.relatedTaskIds.isEmpty())
        }
    }

    // --- c.556: interrogativo "cuál" + "¿qué sigo?" + "¿qué viene después?" ---
    // La familia what-now casaba "qué hago"/"qué sigue"/"qué me toca" pero NO el
    // interrogativo "cuál" (sinónimo natural: "¿cuál hago?"/"¿cuál hago primero?"/
    // "¿cuál es la siguiente?") ni "¿qué sigo?"/"¿qué viene después?". Caían al
    // menú genérico: el usuario pedía su siguiente acción y recibía la lista de
    // capacidades. Mismo motor (WhatNowEngine.suggest), sólo detección.

    @Test fun whatNow_recognizesCualHago() {
        val answer = AssistantEngine.answer("¿Cuál hago?", whatNowUrgent, emptyList(), emptyList())
        assertEquals("¿cuál hago? debe sugerir la urgente: ${answer.text}", listOf(1L), answer.relatedTaskIds)
    }

    @Test fun whatNow_recognizesCualHagoPrimero() {
        val answer = AssistantEngine.answer("¿Cuál hago primero?", whatNowUrgent, emptyList(), emptyList())
        assertEquals("¿cuál hago primero? debe sugerir la urgente: ${answer.text}", listOf(1L), answer.relatedTaskIds)
    }

    @Test fun whatNow_recognizesCualEsLaSiguiente() {
        val answer = AssistantEngine.answer("¿Cuál es la siguiente?", whatNowUrgent, emptyList(), emptyList())
        assertEquals("¿cuál es la siguiente? debe sugerir la urgente: ${answer.text}", listOf(1L), answer.relatedTaskIds)
    }

    @Test fun whatNow_recognizesQueSigo() {
        val answer = AssistantEngine.answer("¿Qué sigo?", whatNowUrgent, emptyList(), emptyList())
        assertEquals("¿qué sigo? debe sugerir la urgente: ${answer.text}", listOf(1L), answer.relatedTaskIds)
    }

    @Test fun whatNow_recognizesQueVieneDespues() {
        val answer = AssistantEngine.answer("¿Qué viene después?", whatNowUrgent, emptyList(), emptyList())
        assertEquals("¿qué viene después? debe sugerir la urgente: ${answer.text}", listOf(1L), answer.relatedTaskIds)
    }

    @Test fun whatNow_cualNoSeActivaConVerboSuelto() {
        // "cuál" suelto sin verbo de acción NO es intención what-now (evita
        // falsos positivos sobre "¿cuál es el problema?" / "no sé cuál").
        for (q in listOf("cuál es el problema", "no sé cuál elegir del catálogo")) {
            val answer = AssistantEngine.answer(q, whatNowUrgent, emptyList(), emptyList())
            assertTrue("'$q' no debe activar What Now: ${answer.text}", answer.relatedTaskIds.isEmpty())
        }
    }

    @Test fun whatNow_recognizesQueSigueSinTareas() {
        // Sin tareas, la nueva forma sigue dando el mensaje útil (no el genérico).
        val answer = AssistantEngine.answer("¿qué sigue ahora?", emptyList(), emptyList(), emptyList())
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue("mensaje útil sin tareas: ${answer.text}", answer.text.contains("descansar") || answer.text.contains("pendiente"))
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

    // --- "¿qué tengo en la mañana?" (preposición + artículo): mañana de HOY ---

    private fun todayAtHour(now: Long, hour: Int): Long {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return today.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    }

    @Test fun queTengoEnLaManana_showsTodayMorningNotTomorrow() {
        // "en la mañana"/"por la mañana": la mañana (franja 6-11) de HOY, jamás
        // tomorrow — la misma lectura que el parser de captura (hoy 09:00) y
        // SearchEngine. Antes el token suelto "manana" robaba la consulta a la
        // agenda de MAÑANA (mentira cruzada con la captura).
        val now = 1_000_000_000_000L
        val tasks = listOf(
            TaskEntity(id = 1, title = "Correr en el parque", dueAt = todayAtHour(now, 8)),
            TaskEntity(id = 2, title = "Comprar aguacates", dueAt = todayAtHour(now, 15)),
            TaskEntity(id = 3, title = "Reunión de equipo", dueAt = tomorrowNoon(now))
        )
        for (q in listOf("¿qué tengo en la mañana?", "¿qué tengo por la mañana?")) {
            val answer = AssistantEngine.answer(q, tasks, emptyList(), emptyList(), now)
            assertTrue("'$q' nombra la de esta mañana: ${answer.text}", answer.text.contains("Correr en el parque"))
            assertTrue("'$q' no mezcla la tarde: ${answer.text}", !answer.text.contains("Comprar aguacates"))
            assertTrue("'$q' no muestra mañana: ${answer.text}", !answer.text.contains("Reunión de equipo"))
        }
    }

    @Test fun queTengoMananaEnLaManana_stillShowsTomorrow() {
        // Control: "mañana en la mañana" = tomorrow (el primer "mañana" gana; el
        // lookbehind de la regex bloquea la lectura preposicional).
        val now = 1_000_000_000_000L
        val answer = AssistantEngine.answer(
            "¿qué tengo mañana en la mañana?",
            listOf(
                TaskEntity(id = 1, title = "Correr en el parque", dueAt = todayAtHour(now, 8)),
                TaskEntity(id = 3, title = "Reunión de equipo", dueAt = tomorrowNoon(now))
            ),
            emptyList(), emptyList(),
            now
        )
        assertTrue("nombra la de mañana: ${answer.text}", answer.text.contains("Reunión de equipo"))
        assertTrue("no mezcla con la de hoy: ${answer.text}", !answer.text.contains("Correr en el parque"))
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

    // --- c.409: "¿qué tengo hoy?" no debe callar un inicio olvidado (missed-start)
    // cuyo hueco se pasó en un día ANTERIOR. Todas las demás superficies de
    // recuperación (What Now, "¿qué olvidé?", "¿voy bien?") ya lo nombran; la
    // agenda "hoy" sólo nombraba las vencidas por dueAt (earlierOverdue) y los
    // compromisos vencidos. Un compromiso al que el usuario le dio hueco el lunes
    // (startAt) para el viernes (dueAt) y NO lo hizo quedaba invisible el martes en
    // "¿qué tengo hoy?" pese a ser trabajo olvidado que debe hacer hoy. Coherencia
    // con el resto de la familia missed-start (c.202/243/247/373/407). Sólo alcance
    // "hoy": futuro/pasado no anexa olvidos (paralelo a earlierOverdue).

    @Test fun queTengoHoy_warnsMissedStartWhoseWindowPassedOnEarlierDay() {
        // Con agenda de hoy rellena: la cola informativa avisa del inicio olvidado
        // sin cambiar el foco de la agenda (no lo lista — es "además", paralelo al
        // tail de atrasadas). startAt hace 2 días (día anterior), ventana terminada,
        // sin dueAt → missed-start puro, NO vencida (no aparece como atrasada).
        val now = 1_000_000_000_000L
        val today = todayNoon(now)
        val missedStart = TaskEntity(
            id = 7, title = "Llamada de seguimiento",
            startAt = now - 2 * 86_400_000L, // empezó hace 2 días
            durationMinutes = 30,            // ventana terminada hace 2 días
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "¿qué tengo hoy?",
            listOf(TaskEntity(id = 1, title = "Cita médica", dueAt = today), missedStart),
            emptyList(), emptyList(),
            now
        )
        assertTrue("nombra la de hoy: ${answer.text}", answer.text.contains("Cita médica"))
        assertTrue("no calla el inicio olvidado: ${answer.text}", answer.text.contains("Llamada de seguimiento"))
        assertTrue("describe el olvido: ${answer.text}", answer.text.contains("se pasó"))
    }

    @Test fun queTengoHoy_recoversMissedStartWhenNoAgendaAndNoOverdue() {
        // Agenda de hoy vacía y SIN atrasadas de tarea: antes decía "Para hoy no
        // tienes tareas agendadas." frente a un inicio olvidado — mentía por
        // omisión sobre trabajo olvidado que debe hacer hoy. Ahora lo nombra.
        val now = 1_000_000_000_000L
        val missedStart = TaskEntity(
            id = 8, title = "Reunión perdida",
            startAt = now - 2 * 86_400_000L,
            durationMinutes = 25,
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "¿qué tengo hoy?",
            listOf(missedStart),
            emptyList(), emptyList(),
            now
        )
        assertTrue("no miente 'no tienes nada': ${answer.text}", answer.text.contains("Reunión perdida"))
    }

    @Test fun queTengoHoy_doesNotMentionMissedStartInFutureScope() {
        // "¿qué tengo mañana?" es alcance futuro: un inicio olvidado NO es parte de
        // "lo de mañana" (igual que earlierOverdue/compromisos vencidos). Guard de
        // coherencia: el tail de missed-start sólo aplica al alcance "hoy".
        val now = 1_000_000_000_000L
        val tomorrow = tomorrowNoon(now)
        val missedStart = TaskEntity(
            id = 9, title = "Llamada agendada",
            startAt = now - 2 * 86_400_000L,
            durationMinutes = 30,
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "¿qué tengo mañana?",
            listOf(TaskEntity(id = 10, title = "Reunión", dueAt = tomorrow), missedStart),
            emptyList(), emptyList(),
            now
        )
        assertTrue("nombra la de mañana: ${answer.text}", answer.text.contains("Reunión"))
        assertTrue("no mezcla el olvido en el alcance futuro: ${answer.text}", !answer.text.contains("Llamada agendada"))
    }

    @Test fun queTengoHoy_doesNotInventMissedStartWhenNone() {
        // Guard anti-falso-positivo: sin inicio olvidado, la respuesta NO debe
        // inventar "se pasó". Una tarea de hoy sana no dispara la cola.
        val now = 1_000_000_000_000L
        val answer = AssistantEngine.answer(
            "¿qué tengo hoy?",
            listOf(TaskEntity(id = 11, title = "Cita médica", dueAt = todayNoon(now))),
            emptyList(), emptyList(),
            now
        )
        assertTrue("no inventa olvido sin missed-start: ${answer.text}", !answer.text.contains("se pasó"))
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

    // --- Tareas sin fecha (paridad búsqueda↔asistente). SearchEngine ya las
    // recupera con su scope UNDATED ("sin fecha"), pero el asistente caía al
    // menú genérico: una tarea de bandeja sin vencimiento era invisible para
    // la superficie conversacional pese a que el usuario la pedía a gritos.

    @Test fun undated_listsTasksWithoutDueDate() {
        val answer = AssistantEngine.answer(
            "tareas sin fecha",
            listOf(
                TaskEntity(id = 1, title = "Idea suelta"),
                TaskEntity(id = 2, title = "Con fecha", dueAt = 1L)
            ),
            emptyList(), emptyList()
        )
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertTrue("nombra la sin fecha: ${answer.text}", answer.text.contains("Idea suelta"))
        assertTrue("no incluye la con fecha: ${answer.text}", !answer.text.contains("Con fecha"))
    }

    @Test fun undated_variantPhrases() {
        val tasks = listOf(TaskEntity(id = 1, title = "Suelta"))
        for (q in listOf("¿qué tengo sin fecha?", "sin fecha", "pendientes sin plazo", "tareas sin día")) {
            val answer = AssistantEngine.answer(q, tasks, emptyList(), emptyList())
            assertTrue("$q → ${answer.text}", answer.text.contains("Suelta"))
            assertEquals("$q", listOf(1L), answer.relatedTaskIds)
        }
    }

    @Test fun undated_noneIsHonestNotMenu() {
        val answer = AssistantEngine.answer(
            "tareas sin fecha",
            listOf(TaskEntity(id = 1, title = "Con fecha", dueAt = 1L)),
            emptyList(), emptyList()
        )
        assertTrue("vacío honesto: ${answer.text}", answer.text.contains("No tienes tareas sin fecha"))
    }

    @Test fun undated_compromisoSinFechaStillRoutesToCommitments() {
        // "compromiso sin fecha" habla de compromisos de conversación, no de
        // tareas: la rama anterior (OPEN_CONVERSATIONS) debe ganar a la nueva.
        val commitment = com.ordia.app.data.local.CommitmentEntity(
            id = 7, conversationId = 1,
            kind = com.ordia.app.data.local.CommitmentKind.SELF_COMMITMENT,
            owner = com.ordia.app.data.local.CommitmentOwner.SELF,
            actor = "yo", action = "algo", dueAt = null, confidence = 0.9f,
            reviewStatus = com.ordia.app.data.local.CommitmentReviewStatus.PENDING,
            fingerprint = "fp7", createdAt = 1L
        )
        val answer = AssistantEngine.answer(
            "compromiso sin fecha",
            listOf(TaskEntity(id = 1, title = "Idea suelta")),
            emptyList(), listOf(commitment)
        )
        assertEquals(AssistantAction.OPEN_CONVERSATIONS, answer.action)
    }

    // --- "próxima semana" / "semana que viene" / "semana pasada" (consistencia con
    // SearchEngine, que ya distingue NEXT_WEEK/LAST_WEEK). Antes el asistente caía al
    // `else` de agendaAnswer y respondía ESTA semana (mon..dom de hoy) con la etiqueta
    // "esta semana" aunque el usuario pidiera la próxima/pasada → mentía sobre qué
    // agenda mostraba. Un usuario que pregunta "¿qué tengo la próxima semana?" para
    // planificar veía los compromisos de esta semana y podía olvidar los de la próxima.

    // --- c.783: forma desnuda "tareas de <fecha>" — la búsqueda ya entendía la
    // expresión (DateRules); el asistente la mandaba al menú (gap (iv) sonda c.779) ---

    private fun agendaZone(): ZoneId = ZoneId.of("America/Santo_Domingo")

    private fun agendaAnswerFor(query: String, idsAndDue: List<Pair<Long, LocalDate>>): com.ordia.app.assistant.AssistantAnswer {
        val zone = agendaZone()
        val now = LocalDate.of(2026, 7, 29).atTime(12, 0).atZone(zone).toInstant().toEpochMilli() // miércoles
        val tasks = idsAndDue.map { (id, date) ->
            TaskEntity(id = id, title = "Tarea$id", dueAt = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli())
        }
        return AssistantEngine.answer(query, tasks, emptyList(), emptyList(), now, zone)
    }

    @Test fun tareasDeHoy_bareForm_resolvesAsAgenda() {
        val hoy = LocalDate.of(2026, 7, 29) // miércoles
        val answer = agendaAnswerFor("tareas de hoy", listOf(1L to hoy, 2L to hoy.plusDays(2)))
        assertTrue("nombra la de hoy: ${answer.text}", answer.text.contains("Tarea1"))
        assertTrue("no mezcla con otra fecha: ${answer.text}", !answer.text.contains("Tarea2"))
        assertEquals(listOf(1L), answer.relatedTaskIds)
    }

    @Test fun tareasDeManana_bareForm_resolvesAsAgenda() {
        val manana = LocalDate.of(2026, 7, 30)
        val answer = agendaAnswerFor("tareas de mañana", listOf(1L to manana, 2L to manana.plusDays(1)))
        assertTrue("nombra la de mañana: ${answer.text}", answer.text.contains("Tarea1"))
        assertEquals(listOf(1L), answer.relatedTaskIds)
    }

    @Test fun tareasDePasadoManana_bareForm_resolvesAsAgenda() {
        val pasadoManana = LocalDate.of(2026, 7, 31)
        val answer = agendaAnswerFor("tareas de pasado mañana", listOf(1L to pasadoManana, 2L to LocalDate.of(2026, 8, 3)))
        assertTrue("nombra la de pasado mañana: ${answer.text}", answer.text.contains("Tarea1"))
        assertEquals(listOf(1L), answer.relatedTaskIds)
    }

    @Test fun tareasDelViernes_bareForm_resolvesAsAgenda() {
        val viernes = LocalDate.of(2026, 7, 31)
        val jueves = LocalDate.of(2026, 7, 30)
        val answer = agendaAnswerFor("tareas del viernes", listOf(1L to jueves, 2L to viernes))
        assertTrue("nombra la del viernes: ${answer.text}", answer.text.contains("Tarea2"))
        assertTrue("no mezcla con jueves: ${answer.text}", !answer.text.contains("Tarea1"))
        assertEquals(listOf(2L), answer.relatedTaskIds)
    }

    @Test fun tareasDeLaProximaSemana_bareForm_resolvesAsAgenda() {
        val monday = LocalDate.of(2026, 7, 27)
        val thisWeek = monday.plusDays(2) // miércoles esta semana
        val nextWeek = monday.plusDays(9) // jueves próxima semana
        val answer = agendaAnswerFor("tareas de la próxima semana", listOf(1L to thisWeek, 2L to nextWeek))
        assertTrue("nombra la de la próxima semana: ${answer.text}", answer.text.contains("Tarea2"))
        assertTrue("no mezcla con esta semana: ${answer.text}", !answer.text.contains("Tarea1"))
        assertEquals(listOf(2L), answer.relatedTaskIds)
    }

    @Test fun tareasDeEsteMes_bareForm_resolvesAsAgenda() {
        val inMonth = LocalDate.of(2026, 7, 31) // julio
        val nextMonth = LocalDate.of(2026, 8, 20)
        val answer = agendaAnswerFor("tareas de este mes", listOf(1L to inMonth, 2L to nextMonth))
        assertTrue("nombra la de este mes: ${answer.text}", answer.text.contains("Tarea1"))
        assertTrue("no mezcla con agosto: ${answer.text}", !answer.text.contains("Tarea2"))
        assertEquals(listOf(1L), answer.relatedTaskIds)
    }

    @Test fun tareasDelFinDeSemana_bareForm_resolvesAsAgenda() {
        // Miércoles 29: el finde entrante es sábado 1 de agosto + domingo 2.
        val sabado = LocalDate.of(2026, 8, 1)
        val viernes = LocalDate.of(2026, 7, 31)
        val answer = agendaAnswerFor("tareas del fin de semana", listOf(1L to viernes, 2L to sabado))
        assertTrue("nombra la del finde: ${answer.text}", answer.text.contains("Tarea2"))
        assertTrue("no mezcla con viernes: ${answer.text}", !answer.text.contains("Tarea1"))
        assertEquals(listOf(2L), answer.relatedTaskIds)
    }

    @Test fun tareasDeSinAlcanceTemporal_fallsBackToMenu() {
        // Guardia: "tareas de matemáticas" (sin alcance temporal) NO es agenda —
        // la búsqueda la resuelve por contenido; el asistente sigue al menú
        // (cierre del hueco de contenido = otro gap, no c.783).
        val answer = agendaAnswerFor("tareas de matemáticas", emptyList())
        assertTrue("menú genérico, no agenda: ${answer.text}", answer.text.contains("Puedo organizar"))
        assertTrue("sin ids: ${answer.relatedTaskIds}", answer.relatedTaskIds.isEmpty())
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

    // Paridad con SearchEngine y el recap: el modificador masculino "pasado" debe
    // reconocerse igual en las tres superficies. Antes la agenda usaba un set de 4
    // (sin "pasado"/"pasados") y "¿qué tengo la semana pasado?" caía a esta
    // semana; la búsqueda y el recap sí lo aceptaban (6). Ahora la agenda delega
    // en DateRules.LAST_WEEK_MODIFIERS (fuente única) y coincide con ambos.
    @Test fun semanaPasado_masculino_recoverPreviousWeekTasks_paridadConBusquedaYRecap() {
        val monday = LocalDate.of(2026, 7, 27)
        val lastWeek = monday.minusDays(3) // viernes semana pasada
        val thisWeek = monday.plusDays(2)
        val answer = agendaAnswerFor("¿qué tengo la semana pasado?", listOf(1L to thisWeek, 2L to lastWeek))
        assertTrue("recupera la de la semana pasada pese al masculino: ${answer.text}", answer.text.contains("Tarea2"))
        assertTrue("no mezcla con esta semana: ${answer.text}", !answer.text.contains("Tarea1"))
        assertEquals(listOf(2L), answer.relatedTaskIds)
    }

    @Test fun semanaPasados_plural_masculino_recoversPreviousWeek() {
        val monday = LocalDate.of(2026, 7, 27)
        val lastWeek = monday.minusDays(3)
        val thisWeek = monday.plusDays(2)
        val answer = agendaAnswerFor("¿qué tengo las semanas pasados?", listOf(1L to thisWeek, 2L to lastWeek))
        assertTrue("recupera la de la semana pasada: ${answer.text}", answer.text.contains("Tarea2"))
        assertTrue("no mezcla con esta semana: ${answer.text}", !answer.text.contains("Tarea1"))
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

    // --- Weekday en plural ("¿qué tengo los viernes?") (c.429) ---
    //
    // Los weekday españoles son invariables en plural ("los viernes" = mismo
    // vocablo). Antes "¿qué tengo los viernes?" casaba el token suelto y
    // devolvía SOLO el próximo viernes — el usuario que pregunta por el patrón
    // recurrente no veía sus compromisos de los viernes siguientes (uno
    // quincenal caía siempre invisible) y podía olvidar lo que vino a
    // planificar. Ahora resuelve un rango de los próximos 4 viernes, reusando la
    // maquinaria de rango existente — sin nueva pantalla ni botón. "hoy" en el
    // helper es 2026-07-29 (miércoles): próximos viernes = 07-31, 08-07,
    // 08-14, 08-21.

    @Test fun queTengoLosViernes_listaCuatroViernesInclusoQuincenal() {
        // Un compromiso quincenal cae en el 2.º viernes (08-07): antes era
        // invisible porque sólo se mostraba el próximo viernes (07-31).
        val viernes1 = LocalDate.of(2026, 7, 31)
        val viernes2 = LocalDate.of(2026, 8, 7) // quincenal
        val jueves = LocalDate.of(2026, 7, 30) // no debe mezclarse
        val answer = agendaAnswerFor("¿qué tengo los viernes?", listOf(1L to viernes1, 2L to viernes2, 3L to jueves))
        assertTrue("nombra el viernes 07-31: ${answer.text}", answer.text.contains("Tarea1"))
        assertTrue("nombra el viernes quincenal 08-07: ${answer.text}", answer.text.contains("Tarea2"))
        assertTrue("no mezcla el jueves: ${answer.text}", !answer.text.contains("Tarea3"))
        assertTrue("etiqueta honesta plural: ${answer.text}", answer.text.contains("próximos viernes"))
        assertEquals(listOf(1L, 2L), answer.relatedTaskIds)
    }

    @Test fun queTengoLosViernes_noMezclaViernesFueraDelHorizonte() {
        // El 5.º viernes (08-28) está fuera del horizonte de 4 semanas: no se
        // lista. Límite honesto, no infinito.
        val viernes1 = LocalDate.of(2026, 7, 31)
        val viernes5 = LocalDate.of(2026, 8, 28) // 5.º viernes
        val answer = agendaAnswerFor("¿qué tengo los viernes?", listOf(1L to viernes1, 2L to viernes5))
        assertTrue("nombra el 1.º viernes: ${answer.text}", answer.text.contains("Tarea1"))
        assertTrue("no lista el 5.º viernes (fuera de horizonte): ${answer.text}", !answer.text.contains("Tarea2"))
    }

    @Test fun queTengoLosViernes_noMezclaOtroDiaDentroDelHorizonte() {
        // Un miércoles (08-05) cae DENTRO del horizonte de 4 semanas pero NO es
        // viernes: el rango plural CONTINUO [07-31..08-21] lo incluía por error
        // (mentía por exceso: mostraba trabajo de otro día bajo "los viernes").
        // El plural debe resolver sólo los viernes del horizonte, no todo el
        // intervalo calendario. Regresión de c.433 (que usaba rango continuo).
        val viernes1 = LocalDate.of(2026, 7, 31)
        val miercolesDentro = LocalDate.of(2026, 8, 5) // miércoles dentro del horizonte
        val answer = agendaAnswerFor("¿qué tengo los viernes?", listOf(1L to viernes1, 2L to miercolesDentro))
        assertTrue("nombra el viernes 07-31: ${answer.text}", answer.text.contains("Tarea1"))
        assertTrue("no mezcla un miércoles del horizonte: ${answer.text}", !answer.text.contains("Tarea2"))
    }

    @Test fun queTengoLosViernes_empty_diceLosProximosViernesHonesto() {
        // Sólo hay algo el jueves; ningún viernes en el horizonte.
        val jueves = LocalDate.of(2026, 7, 30)
        val answer = agendaAnswerFor("¿qué tengo los viernes?", listOf(1L to jueves))
        assertTrue("dice los próximos viernes y que no hay: ${answer.text}",
            answer.text.contains("próximos viernes") && answer.text.contains("no tienes"))
        assertTrue("no inventa la del jueves: ${answer.text}", !answer.text.contains("Tarea1"))
    }

    @Test fun queTengoLosProximosViernes_rangoPluralConModificadorEstricto() {
        // "los próximos viernes" conserva el rango plural (no se colapsa a un
        // solo día) y el modificador estricto no rompe el horizonte: como hoy es
        // miércoles, el viernes inminente (07-31) encabeza y el siguiente (08-07)
        // también aparece. El caso estricto real (hoy=viernes → salta al siguiente)
        // no se ejercita aquí porque el helper fija hoy=miércoles; se cubre por
        // simetría con resolveAgendaWeekday (misma fórmula delta/strict).
        val viernesInminente = LocalDate.of(2026, 7, 31)
        val viernesSiguiente = LocalDate.of(2026, 8, 7)
        val answer = agendaAnswerFor("¿qué tengo los próximos viernes?", listOf(1L to viernesInminente, 2L to viernesSiguiente))
        assertTrue("nombra el viernes inminente: ${answer.text}", answer.text.contains("Tarea1"))
        assertTrue("nombra también el siguiente (rango plural): ${answer.text}", answer.text.contains("Tarea2"))
        assertEquals(listOf(1L, 2L), answer.relatedTaskIds)
    }

    @Test fun queTengoLosMiercoles_inclusivo_incluyeHoy() {
        // Hoy es miércoles 2026-07-29: "los miércoles" inclusivo incluye hoy
        // (delta==0, sin modificador estricto) → el rango empieza HOY. El 1.º de los
        // 4 miércoles es hoy; los siguientes 08-05, 08-12, 08-19.
        val hoy = LocalDate.of(2026, 7, 29)
        val tercerMiercoles = LocalDate.of(2026, 8, 12)
        val answer = agendaAnswerFor("¿qué tengo los miércoles?", listOf(1L to hoy, 2L to tercerMiercoles))
        assertTrue("incluye hoy (1.º miércoles): ${answer.text}", answer.text.contains("Tarea1"))
        assertTrue("incluye el 3.º miércoles (08-12): ${answer.text}", answer.text.contains("Tarea2"))
        assertEquals(listOf(1L, 2L), answer.relatedTaskIds)
    }

    @Test fun elViernes_singularSigueSiendoUnSoloDia() {
        // Regresión: el singular "el viernes" NO debe convertirse en rango
        // plural. Sigue devolviendo SOLO el próximo viernes.
        val viernes1 = LocalDate.of(2026, 7, 31)
        val viernes2 = LocalDate.of(2026, 8, 7)
        val answer = agendaAnswerFor("¿qué tengo el viernes?", listOf(1L to viernes1, 2L to viernes2))
        assertTrue("singular nombra sólo el próximo viernes: ${answer.text}", answer.text.contains("Tarea1"))
        assertTrue("singular NO lista el 2.º viernes: ${answer.text}", !answer.text.contains("Tarea2"))
        assertEquals(listOf(1L), answer.relatedTaskIds)
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

    // ---- phrasing natural de "¿cuánto tiempo me queda?" — paridad con "¿voy bien?" ----
    //
    // "¿cuánto tiempo me queda?"/"¿cuánto tiempo libre tengo?"/"¿cuánto me queda?"/
    // "¿tengo tiempo libre?" son LAS preguntas de planificación más naturales para
    // pedir el veredicto de carga del día. Antes caían al menú genérico (la
    // superficie dayLoad sólo reconocía "voy bien"/"da tiempo"/"cabe todo"/...).
    // Como "da tiempo" es subcadena de "queda tiempo", "me queda tiempo" SÍ
    // funcionaba por accidente — pero la forma más común con "cuánto tiempo" no.
    // Verifican que ahora dan el mismo veredicto honesto (con sus colas), sin
    // nueva pantalla/botón: reusan dayLoadAnswer.
    @Test fun dayLoad_cuantoTiempoMeQueda_daVeredicto() {
        val now = dayAt(dayToday, 9)
        val answer = AssistantEngine.answer(
            "¿cuánto tiempo me queda?",
            listOf(TaskEntity(id = 1, title = "A", dueAt = dayAt(dayToday, 11), durationMinutes = 45)),
            emptyList(), emptyList(),
            now, dayZone
        )
        assertTrue("da un veredicto de carga, no el menú genérico: ${answer.text}",
            answer.text.contains("holgura") || answer.text.contains("despejado") ||
                answer.text.contains("lleno") || answer.text.contains("no da tiempo"))
    }

    @Test fun dayLoad_cuantoTiempoLibre_daVeredicto() {
        val now = dayAt(dayToday, 12)
        val answer = AssistantEngine.answer(
            "¿cuánto tiempo libre tengo?",
            listOf(
                TaskEntity(id = 1, title = "Hoy", dueAt = dayAt(dayToday, 17), durationMinutes = 60),
                TaskEntity(id = 2, title = "V1", dueAt = dayAt(dayToday.minusDays(1), 9), durationMinutes = 120),
                TaskEntity(id = 3, title = "V2", dueAt = dayAt(dayToday.minusDays(2), 9), durationMinutes = 120)
            ),
            emptyList(), emptyList(),
            now, dayZone
        )
        assertTrue("da un veredicto de carga, no el menú genérico: ${answer.text}",
            answer.text.contains("holgura") || answer.text.contains("despejado") ||
                answer.text.contains("lleno") || answer.text.contains("no da tiempo"))
    }

    @Test fun dayLoad_cuantoMeQueda_daVeredicto() {
        val now = dayAt(dayToday, 9)
        val answer = AssistantEngine.answer(
            "¿cuánto me queda?",
            emptyList(), emptyList(), emptyList(),
            now, dayZone
        )
        assertTrue("día despejado sin trabajo: ${answer.text}", answer.text.contains("despejado"))
    }

    @Test fun dayLoad_tengoTiempoLibre_daVeredicto() {
        val now = dayAt(dayToday, 9)
        val answer = AssistantEngine.answer(
            "¿tengo tiempo libre?",
            listOf(TaskEntity(id = 1, title = "A", dueAt = dayAt(dayToday, 11), durationMinutes = 45)),
            emptyList(), emptyList(),
            now, dayZone
        )
        assertTrue("da un veredicto de carga, no el menú genérico: ${answer.text}",
            answer.text.contains("holgura") || answer.text.contains("despejado") ||
                answer.text.contains("lleno") || answer.text.contains("no da tiempo"))
    }

    // Guard anti-colisión: "tengo tiempo" suelto (sin "libre"/"cuánto") NO es
    // veredicto de carga inequívoco ("¿tengo tiempo para X?" pide capacidad para
    // una tarea concreta, no el panorama del día) → se deja fuera para no robar
    // otras intenciones. Sólo la forma "tengo tiempo libre" activa el veredicto.
    @Test fun dayLoad_tengoTiempoSoloNoEsVeredictoForzado() {
        val answer = AssistantEngine.answer("tengo tiempo", emptyList(), emptyList(), emptyList())
        // Sin "libre": nunca se fuerza un veredicto de carga. c.703 lo rutea a
        // la superficie de hueco libre (Cluster C sonda assistant) como estado
        // honesto — con lista vacía, "Nada te cabe…" (menú genérico prohibido).
        assertTrue("no se inventa un veredicto para 'tengo tiempo' ambiguo: ${answer.text}",
            !answer.text.contains("Puedo organizar") && answer.text.contains("Nada te cabe"))
    }

    // "¿cómo voy?" / "¿cómo voy hoy?" — la forma cotidiana por excelencia de
    // preguntar cómo va el día. Antes sólo "voy bien"/"voy mal" (veredicto
    // afirmado) la activaban: "¿cómo voy?" caía al menú genérico justo cuando
    // el usuario pide el panorama. Reusa dayLoadAnswer; sin nueva pantalla.
    @Test fun dayLoad_comoVoy_daVeredicto() {
        val now = dayAt(dayToday, 9)
        val answer = AssistantEngine.answer(
            "¿cómo voy?",
            listOf(TaskEntity(id = 1, title = "A", dueAt = dayAt(dayToday, 11), durationMinutes = 45)),
            emptyList(), emptyList(),
            now, dayZone
        )
        assertTrue("da un veredicto de carga, no el menú genérico: ${answer.text}",
            answer.text.contains("holgura") || answer.text.contains("despejado") ||
                answer.text.contains("lleno") || answer.text.contains("no da tiempo"))
    }

    @Test fun dayLoad_comoVoyHoy_daVeredicto() {
        val now = dayAt(dayToday, 9)
        val answer = AssistantEngine.answer(
            "¿cómo voy hoy?",
            listOf(TaskEntity(id = 1, title = "A", dueAt = dayAt(dayToday, 11), durationMinutes = 45)),
            emptyList(), emptyList(),
            now, dayZone
        )
        assertTrue("da un veredicto de carga, no el menú genérico: ${answer.text}",
            answer.text.contains("holgura") || answer.text.contains("despejado") ||
                answer.text.contains("lleno") || answer.text.contains("no da tiempo"))
    }

    // Guard anti-colisión: "¿cómo voy a ...?" ("cómo voy a llegar/pagar/hacer")
    // NO pide el panorama del día — pide el modo de lograr algo. La regex de
    // isDayLoadQuery excluye "como voy" seguido de " a" para no robar esa
    // intención. Paridad con el guard de "tengo tiempo" suelto.
    @Test fun dayLoad_comoVoyA_noEsVeredictoForzado() {
        val answer = AssistantEngine.answer("¿cómo voy a llegar?", emptyList(), emptyList(), emptyList())
        assertFalse("no se inventa un veredicto para 'cómo voy a ...': ${answer.text}",
            answer.text.contains("despejado") || answer.text.contains("holgura") ||
                answer.text.contains("lleno") || answer.text.contains("no da tiempo"))
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

    // --- c.411: "¿qué hago ahora?" no debe callar las capturas de bandeja
    // arrinconadas (3.er olvido). Paridad con el nudge del guardián
    // ([GuardianEngine.withStaleInboxTail], c.410), que ya las nombra como cola
    // en todas sus ramas con acción, y simétrica con las colas de vencidas,
    // missed-start y compromisos que ya viven en esta superficie. Antes, un
    // usuario con una tarea urgente que hacer AHORA y seis ideas arrinconadas
    // leía "empieza por lo urgente" sin señal de que las está olvidando.

    private fun staleCapture(
        id: Long,
        title: String,
        daysOld: Long,
        zone: java.time.ZoneId,
        today: java.time.LocalDate
    ): TaskEntity = TaskEntity(
        id = id, title = title,
        createdAt = com.ordia.app.domain.DateRules.toEpochMillis(
            today.minusDays(daysOld), java.time.LocalTime.of(9, 0), zone)
    )

    @Test fun whatNow_warnsStaleInboxWhenSuggestingAnotherTask() {
        // Sugerencia urgente + capturas arrinconadas: "¿qué hago ahora?" debe
        // nombrar la tarea a hacer PERO no callar las ideas olvidadas en la
        // bandeja — la misma mentira por omisión que c.357 cerró para
        // compromisos en esta misma superficie. Es cola de conteo, no nombra
        // títulos (la acción primaria es la urgente).
        val zone = java.time.ZoneId.of("America/Santo_Domingo")
        val today = java.time.LocalDate.of(2026, 7, 29)
        val now = com.ordia.app.domain.DateRules.toEpochMillis(today, java.time.LocalTime.NOON, zone)
        val urgent = TaskEntity(id = 1, title = "Revisar correo", priority = TaskPriority.URGENT)
        val answer = AssistantEngine.answer(
            "¿qué hago ahora?",
            listOf(
                urgent,
                staleCapture(2, "Idea vieja A", 21, zone, today),
                staleCapture(3, "Idea vieja B", 14, zone, today)
            ),
            emptyList(), emptyList(),
            now, zone
        )
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertTrue("no calla las capturas arrinconadas: ${answer.text}",
            answer.text.contains("2 capturas"))
        assertTrue("es cola de conteo, no nombra títulos: ${answer.text}",
            !answer.text.contains("Idea vieja"))
    }

    @Test fun whatNow_warnsSingleStaleInboxCapture() {
        // Una sola captura arrinconada: la cola debe concordar en singular
        // ("1 captura ... lleva"). Guard de coherencia gramatical.
        val zone = java.time.ZoneId.of("America/Santo_Domingo")
        val today = java.time.LocalDate.of(2026, 7, 29)
        val now = com.ordia.app.domain.DateRules.toEpochMillis(today, java.time.LocalTime.NOON, zone)
        val answer = AssistantEngine.answer(
            "¿qué hago ahora?",
            listOf(
                TaskEntity(id = 1, title = "Revisar correo", priority = TaskPriority.URGENT),
                staleCapture(2, "Idea vieja", 21, zone, today)
            ),
            emptyList(), emptyList(),
            now, zone
        )
        assertTrue("cola en singular: ${answer.text}", answer.text.contains("1 captura"))
        assertTrue("verbo en singular: ${answer.text}", answer.text.contains("lleva"))
    }

    @Test fun whatNow_doesNotCountSuggestedStaleCaptureInTail() {
        // Si la propia sugerida es la captura arrinconada (no hay nada más
        // time-sensitive), no se cuenta a sí misma en la cola — su posición
        // como sugerida ya la explica, igual que overdueTail excluye la vencida
        // sugerida. Sin duplicar la señal.
        val zone = java.time.ZoneId.of("America/Santo_Domingo")
        val today = java.time.LocalDate.of(2026, 7, 29)
        val now = com.ordia.app.domain.DateRules.toEpochMillis(today, java.time.LocalTime.NOON, zone)
        val onlyStale = staleCapture(1, "Idea vieja", 21, zone, today)
        val answer = AssistantEngine.answer(
            "¿qué hago ahora?",
            listOf(onlyStale),
            emptyList(), emptyList(),
            now, zone
        )
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertTrue("no se cuenta a sí misma: ${answer.text}",
            !answer.text.contains("captura"))
    }

    @Test fun whatNow_doesNotInventStaleInboxWhenNone() {
        // Guard anti-falso-positivo (IA honesta): sin capturas arrinconadas, la
        // cola no debe inventar "capturas". Una captura reciente (< 7 días) NO
        // es olvidada, así que tampoco se cuenta.
        val zone = java.time.ZoneId.of("America/Santo_Domingo")
        val today = java.time.LocalDate.of(2026, 7, 29)
        val now = com.ordia.app.domain.DateRules.toEpochMillis(today, java.time.LocalTime.NOON, zone)
        val answer = AssistantEngine.answer(
            "¿qué hago ahora?",
            listOf(
                TaskEntity(id = 1, title = "Revisar correo", priority = TaskPriority.URGENT),
                staleCapture(2, "Idea reciente", 6, zone, today)
            ),
            emptyList(), emptyList(),
            now, zone
        )
        assertTrue("no inventa capturas olvidadas: ${answer.text}",
            !answer.text.contains("captura"))
    }

    @Test fun whatNow_warnsStaleInboxAlongsideOverdueAndCommitment() {
        // Coexistencia de las colas: vencidas + missed-start + capturas
        // arrinconadas + compromiso vencido. Ningún olvido debe ocultarse
        // detrás de otro en la superficie de mayor tráfico.
        val zone = java.time.ZoneId.of("America/Santo_Domingo")
        val today = java.time.LocalDate.of(2026, 7, 29)
        val now = com.ordia.app.domain.DateRules.toEpochMillis(today, java.time.LocalTime.NOON, zone)
        // La sugerida es la vencida URGENT (las vencidas se priorizan sobre
        // las urgentes no vencidas); otra vencida alimenta la cola de conteo.
        val suggestedOverdue = TaskEntity(id = 1, title = "Atrasada urgente", dueAt = 1L, priority = TaskPriority.URGENT)
        val otherOverdue = TaskEntity(id = 2, title = "Otra atrasada", dueAt = 2L)
        val missed = TaskEntity(
            id = 3, title = "Llamada agendada",
            startAt = now - 90 * 60_000L, durationMinutes = 30,
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val commitment = overdueCommitment(10, "te llamo el martes", now - 86_400_000L)
        val answer = AssistantEngine.answer(
            "¿qué hago ahora?",
            listOf(suggestedOverdue, otherOverdue, missed, staleCapture(4, "Idea vieja", 21, zone, today)),
            emptyList(),
            listOf(commitment),
            now, zone
        )
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertTrue("nombra la otra vencida: ${answer.text}", answer.text.contains("1 vencida"))
        assertTrue("nombra el inicio olvidado: ${answer.text}", answer.text.contains("Llamada agendada"))
        assertTrue("nombra las capturas arrinconadas: ${answer.text}", answer.text.contains("1 captura"))
        assertTrue("nombra el compromiso vencido: ${answer.text}", answer.text.contains("compromiso"))
    }

    // --- c.417: "¿qué tengo hoy?" y "¿voy bien?" no deben callar las capturas de
    // bandeja arrinconadas (3.er olvido). Paridad con "¿qué hago ahora?" (c.411) y
    // el nudge del guardián (c.410): estas dos superficies ya nombraban vencidas,
    // missed-start y compromisos como colas, PERO callaban las ideas arrinconadas
    // — la misma mentir por omisión. Aquí la acción primaria NO es una tarea
    // sugerida (es la agenda listada / el veredicto de carga), así la cola cuenta
    // TODAS las capturas arrinconadas (no hay sugerida que excluir); y como
    // isStaleInbox exige dueAt==null && startAt==null, ninguna aparece en la
    // agenda listada ni suma a loadMinutes → no hay doble señalización.

    @Test fun queTengoHoy_warnsStaleInboxCapturesAlongsideAgenda() {
        // Agenda de hoy rellena + capturas arrinconadas: la cola debe avisar del
        // 3.er olvido sin cambiar el foco de la agenda (no las lista — son
        // "además", paralelo al tail de atrasadas/missed-start/compromisos).
        val zone = java.time.ZoneId.of("America/Santo_Domingo")
        val today = java.time.LocalDate.of(2026, 7, 29)
        val now = com.ordia.app.domain.DateRules.toEpochMillis(today, java.time.LocalTime.NOON, zone)
        val answer = AssistantEngine.answer(
            "¿qué tengo hoy?",
            listOf(
                TaskEntity(id = 1, title = "Cita médica", dueAt = now),
                staleCapture(2, "Idea vieja A", 21, zone, today),
                staleCapture(3, "Idea vieja B", 14, zone, today)
            ),
            emptyList(), emptyList(),
            now, zone
        )
        assertTrue("nombra la de hoy: ${answer.text}", answer.text.contains("Cita médica"))
        assertTrue("no calla las capturas arrinconadas: ${answer.text}",
            answer.text.contains("2 capturas"))
        assertTrue("es cola de conteo, no nombra títulos: ${answer.text}",
            !answer.text.contains("Idea vieja"))
    }

    @Test fun queTengoHoy_warnsSingleStaleInboxCapture() {
        // Una sola captura arrinconada: la cola debe concordar en singular.
        val zone = java.time.ZoneId.of("America/Santo_Domingo")
        val today = java.time.LocalDate.of(2026, 7, 29)
        val now = com.ordia.app.domain.DateRules.toEpochMillis(today, java.time.LocalTime.NOON, zone)
        val answer = AssistantEngine.answer(
            "¿qué tengo hoy?",
            listOf(
                TaskEntity(id = 1, title = "Cita médica", dueAt = now),
                staleCapture(2, "Idea vieja", 21, zone, today)
            ),
            emptyList(), emptyList(),
            now, zone
        )
        assertTrue("cola en singular: ${answer.text}", answer.text.contains("1 captura"))
        assertTrue("verbo en singular: ${answer.text}", answer.text.contains("lleva"))
    }

    @Test fun queTengoHoy_doesNotInventStaleInboxWhenRecent() {
        // Guard anti-falso-positivo (IA honesta): una captura reciente (< 7 días)
        // NO es olvidada, así la cola no debe inventar "capturas".
        val zone = java.time.ZoneId.of("America/Santo_Domingo")
        val today = java.time.LocalDate.of(2026, 7, 29)
        val now = com.ordia.app.domain.DateRules.toEpochMillis(today, java.time.LocalTime.NOON, zone)
        val answer = AssistantEngine.answer(
            "¿qué tengo hoy?",
            listOf(
                TaskEntity(id = 1, title = "Cita médica", dueAt = now),
                staleCapture(2, "Idea reciente", 6, zone, today)
            ),
            emptyList(), emptyList(),
            now, zone
        )
        assertTrue("no inventa capturas olvidadas: ${answer.text}",
            !answer.text.contains("captura"))
    }

    @Test fun dayLoad_namesStaleInboxEvenWhenDayIsLight() {
        // Día despejado (sin tareas con carga) pero con capturas arrinconadas: el
        // veredicto no puede decir "despejado" sin recordar las ideas olvidadas
        // (3.er olvido). Paridad con dayLoad_namesOverdueCommitmentEvenWhenDayIsLight
        // (4.º olvido) y con la tarjeta de resumen (que ya expone los conteos).
        val zone = dayZone
        val today = dayToday
        val now = dayAt(today, 9)
        val answer = AssistantEngine.answer(
            "¿voy bien hoy?",
            listOf(
                staleCapture(1, "Idea vieja A", 21, zone, today),
                staleCapture(2, "Idea vieja B", 14, zone, today)
            ),
            emptyList(), emptyList(),
            now, zone
        )
        assertTrue("dice que el día está despejado: ${answer.text}", answer.text.contains("despejado"))
        assertTrue("no calla las capturas arrinconadas: ${answer.text}",
            answer.text.contains("2 capturas"))
    }

    @Test fun planificaMiDia_opensPlannerLikeOrganiza() {
        // "planifica mi día" es la forma más natural en español del verbo de
        // planificación, pero caía al mensaje genérico en vez de abrir el
        // planificador. Debe comportarse igual que "organiza mi día" (OPEN_PLANNER
        // + conteo honesto de pendientes/vencidas).
        val task = TaskEntity(id = 1, title = "Revisar correo")
        val answer = AssistantEngine.answer(
            "planifica mi dia",
            listOf(task), emptyList(), emptyList()
        )
        assertEquals(AssistantAction.OPEN_PLANNER, answer.action)
        assertTrue("cuenta la tarea pendiente: ${answer.text}", answer.text.contains("1 tarea pendiente"))
    }

    @Test fun planificarElDia_opensPlanner() {
        // Forma infinitiva + "el día": paridad con "organiza el día".
        val answer = AssistantEngine.answer(
            "planificar el dia",
            emptyList(), emptyList(), emptyList()
        )
        assertEquals(AssistantAction.OPEN_PLANNER, answer.action)
        assertTrue("0 pendientes sin inflar: ${answer.text}", answer.text.contains("0 tareas pendientes"))
    }

    @Test fun armaMiDia_opensPlanner() {
        // "arma mi día"/"armar el plan": forma coloquial de planificación.
        val answer = AssistantEngine.answer(
            "arma mi dia",
            emptyList(), emptyList(), emptyList()
        )
        assertEquals(AssistantAction.OPEN_PLANNER, answer.action)
    }

    @Test fun planificaDoesNotStealPlanMinimo() {
        // Guard anti-falso-positivo: "plan mínimo" sigue siendo la lista de 3
        // (RUN_REPLAN/relatedTaskIds), NO abre el planificador. La nueva rama
        // "planifica" no debe robar "plan mínimo".
        val task = TaskEntity(id = 1, title = "Algo")
        val answer = AssistantEngine.answer(
            "plan minimo para hoy",
            listOf(task), emptyList(), emptyList()
        )
        assertTrue("plan mínimo sigue listando la tarea: ${answer.text}",
            answer.relatedTaskIds.contains(1L))
        assertNotEquals(AssistantAction.OPEN_PLANNER, answer.action)
    }

    @Test fun preparameUnPlan_opensPlanner() {
        // "prepárame un plan" es la forma cotidiana de pedir planificación, pero
        // caía al mensaje genérico (que incluso anuncia "preparar un plan" como
        // capacidad). Paridad con "organiza mi día" → OPEN_PLANNER.
        val task = TaskEntity(id = 1, title = "Revisar correo")
        val answer = AssistantEngine.answer(
            "preparame un plan",
            listOf(task), emptyList(), emptyList()
        )
        assertEquals(AssistantAction.OPEN_PLANNER, answer.action)
        assertTrue("cuenta la tarea pendiente: ${answer.text}", answer.text.contains("1 tarea pendiente"))
    }

    @Test fun hazmeUnPlan_opensPlanner() {
        // Sinónimo coloquial de planificación.
        val answer = AssistantEngine.answer(
            "hazme un plan",
            emptyList(), emptyList(), emptyList()
        )
        assertEquals(AssistantAction.OPEN_PLANNER, answer.action)
    }

    @Test fun ordenaMiDia_opensPlanner() {
        // "ordena mi día": variante verbal natural de planificación.
        val answer = AssistantEngine.answer(
            "ordena mi dia",
            emptyList(), emptyList(), emptyList()
        )
        assertEquals(AssistantAction.OPEN_PLANNER, answer.action)
    }

    @Test fun planificameMiDia_opensPlanner() {
        // Forma reflexiva/imperativa "planifícame mi día": variante natural que
        // antes caía al mensaje genérico. Paridad con "planifica mi día".
        val answer = AssistantEngine.answer(
            "planificame mi dia",
            emptyList(), emptyList(), emptyList()
        )
        assertEquals(AssistantAction.OPEN_PLANNER, answer.action)
    }

    @Test fun preparameUnPlanMinimo_doesNotStealPlanMinimo() {
        // Guard anti-falso-positivo: aunque "preparame un plan" ahora abre el
        // planificador, "prepárame un plan mínimo" debe seguir siendo la lista
        // de 3 (relatedTaskIds), no el planificador. La guarda `"plan minimo" !in query`
        // evita el robo de rama.
        val task = TaskEntity(id = 1, title = "Algo")
        val answer = AssistantEngine.answer(
            "preparame un plan minimo",
            listOf(task), emptyList(), emptyList()
        )
        assertTrue("plan mínimo sigue listando la tarea: ${answer.text}",
            answer.relatedTaskIds.contains(1L))
        assertNotEquals(AssistantAction.OPEN_PLANNER, answer.action)
    }

    @Test fun dameUnPlan_opensPlanner() {
        // "dame un plan" es la forma imperativa cotidiana más natural de pedir
        // planificación (paralela a "dame un resumen"), pero caía al mensaje
        // genérico. Paridad con "organiza mi día" → OPEN_PLANNER.
        val answer = AssistantEngine.answer(
            "dame un plan",
            emptyList(), emptyList(), emptyList()
        )
        assertEquals(AssistantAction.OPEN_PLANNER, answer.action)
    }

    @Test fun quieroUnPlan_opensPlanner() {
        // "quiero un plan": forma declarativa natural. Debe abrir el planificador
        // como las formas imperativas, no caer al menú genérico.
        val task = TaskEntity(id = 1, title = "Revisar correo")
        val answer = AssistantEngine.answer(
            "quiero un plan",
            listOf(task), emptyList(), emptyList()
        )
        assertEquals(AssistantAction.OPEN_PLANNER, answer.action)
        assertTrue("cuenta la tarea pendiente: ${answer.text}", answer.text.contains("1 tarea pendiente"))
    }

    @Test fun necesitoUnPlan_opensPlanner() {
        // "necesito un plan": variante declarativa, paridad con "quiero un plan".
        val answer = AssistantEngine.answer(
            "necesito un plan",
            emptyList(), emptyList(), emptyList()
        )
        assertEquals(AssistantAction.OPEN_PLANNER, answer.action)
    }

    @Test fun quieroUnPlanNoAbreSiHayCalificador() {
        // Guard anti-falso-positivo: "quiero un plan estratégico" se refiere a un
        // documento/plan específico, no a abrir el planificador. La rama
        // declarativa exige "un plan" al final del enunciado (sin calificador
        // posterior), así no roba esta frase legítima.
        val answer = AssistantEngine.answer(
            "quiero un plan estrategico para mañana",
            emptyList(), emptyList(), emptyList()
        )
        assertNotEquals(AssistantAction.OPEN_PLANNER, answer.action)
    }

    @Test fun quieroUnPlanMinimo_doesNotStealPlanMinimo() {
        // Guard anti-falso-positivo: aunque "quiero un plan" ahora abre el
        // planificador, "quiero un plan mínimo" debe seguir siendo la lista de 3
        // (relatedTaskIds), no el planificador. La guarda `"plan minimo" !in query`
        // evita el robo de rama (paridad con preparameUnPlanMinimo).
        val task = TaskEntity(id = 1, title = "Algo")
        val answer = AssistantEngine.answer(
            "quiero un plan minimo",
            listOf(task), emptyList(), emptyList()
        )
        assertTrue("plan mínimo sigue listando la tarea: ${answer.text}",
            answer.relatedTaskIds.contains(1L))
        assertNotEquals(AssistantAction.OPEN_PLANNER, answer.action)
    }

    @Test fun dayLoad_doesNotInventStaleInboxWhenRecent() {
        // Guard anti-falso-positivo: una captura reciente (< 7 días) no debe
        // disparar la cola. Sin ideas arrinconadas, el veredicto calla el 3.er olvido.
        val zone = dayZone
        val today = dayToday
        val now = dayAt(today, 9)
        val answer = AssistantEngine.answer(
            "¿voy bien hoy?",
            listOf(staleCapture(1, "Idea reciente", 6, zone, today)),
            emptyList(), emptyList(),
            now, zone
        )
        assertTrue("no inventa capturas olvidadas: ${answer.text}",
            !answer.text.contains("captura"))
    }

    // --- Recap de logros: "¿qué hice hoy?"/"¿qué completé hoy?"/"¿qué hice ayer?" ---
    // Antes estas consultas cotidianas caían al menú genérico ("Puedo organizar tu
    // día…") incluso con tareas completadas hace minutos: el asistente callaba el
    // logro que el usuario pidió recuperar. Ahora lista los títulos (raíces, mismo
    // predicado canónico que TaskRules.completedTodayCount) ordenados por completedAt
    // desc. Determinista y local (sin IA fingida), sin nueva pantalla.

    private fun completedTask(id: Long, title: String, completedAt: Long): TaskEntity =
        TaskEntity(
            id = id, title = title, completed = true,
            status = com.ordia.app.data.local.TaskStatus.COMPLETED,
            completedAt = completedAt, parentTaskId = null
        )

    @Test fun completedRecap_today_namesCompletedTasks() {
        val now = dayAt(dayToday, 15)
        val done = listOf(
            completedTask(1, "Revisar propuesta", dayAt(dayToday, 10)),
            completedTask(2, "Enviar factura", dayAt(dayToday, 14))
        )
        val answer = AssistantEngine.answer("¿qué hice hoy?", done, emptyList(), emptyList(), now, dayZone)
        assertTrue("menciona el logro: ${answer.text}", answer.text.contains("Hoy completaste 2"))
        assertTrue("nombra los títulos: ${answer.text}",
            answer.text.contains("Revisar propuesta") && answer.text.contains("Enviar factura"))
        assertEquals(AssistantAction.NONE, answer.action)
    }

    @Test fun completedRecap_acceptsSynonyms() {
        val now = dayAt(dayToday, 15)
        val done = listOf(completedTask(1, "Llamar cliente", dayAt(dayToday, 11)))
        for (q in listOf("¿qué completé hoy?", "¿qué terminé hoy?", "¿qué completado hoy?")) {
            val answer = AssistantEngine.answer(q, done, emptyList(), emptyList(), now, dayZone)
            assertTrue("[$q] nombra el logro: ${answer.text}", answer.text.contains("completaste"))
        }
    }

    @Test fun completedRecap_noneToday_saysSoHonestly() {
        val now = dayAt(dayToday, 15)
        val answer = AssistantEngine.answer("¿qué hice hoy?", emptyList(), emptyList(), emptyList(), now, dayZone)
        assertTrue("dice que no hay: ${answer.text}", answer.text.contains("no has completado"))
        assertFalse("no inventa logros: ${answer.text}", answer.text.contains("«"))
    }

    @Test fun completedRecap_moreThanThree_summarizesRest() {
        val now = dayAt(dayToday, 18)
        val done = (1..5).map { completedTask(it.toLong(), "Tarea $it", dayAt(dayToday, 8 + it)) }
        val answer = AssistantEngine.answer("¿qué completé hoy?", done, emptyList(), emptyList(), now, dayZone)
        assertTrue("cuenta 5: ${answer.text}", answer.text.contains("completaste 5"))
        assertTrue("resume el resto: ${answer.text}", answer.text.contains("y 2 más"))
        // Orden desc por completedAt: los 3 más recientes (5,4,3) se nombran.
        assertTrue("nombra los 3 más recientes: ${answer.text}",
            answer.text.contains("Tarea 5") && answer.text.contains("Tarea 3"))
        assertFalse("no nombra el más antiguo: ${answer.text}", answer.text.contains("Tarea 1"))
    }

    @Test fun completedRecap_yesterday_listsYesterdayNotToday() {
        val now = dayAt(dayToday, 9)
        val yesterday = dayToday.minusDays(1)
        val done = listOf(
            completedTask(1, "De ayer", dayAt(yesterday, 16)),
            completedTask(2, "De hoy", dayAt(dayToday, 8))
        )
        val answer = AssistantEngine.answer("¿qué hice ayer?", done, emptyList(), emptyList(), now, dayZone)
        assertTrue("dice Ayer: ${answer.text}", answer.text.startsWith("Ayer"))
        assertTrue("nombra la de ayer: ${answer.text}", answer.text.contains("De ayer"))
        assertFalse("no nombra la de hoy: ${answer.text}", answer.text.contains("De hoy"))
    }

    @Test fun completedRecap_excludesSubtasksArchivedCancelled() {
        // Solo raíces, no archivadas, no canceladas: mismo predicado canónico que
        // TaskRules.completedTodayCount. Subtareas completadas no inflan el recap.
        val now = dayAt(dayToday, 15)
        val root = completedTask(1, "Raíz", dayAt(dayToday, 10))
        val subtask = completedTask(2, "Subtarea", dayAt(dayToday, 11)).copy(parentTaskId = 1)
        val archived = completedTask(3, "Archivada", dayAt(dayToday, 12)).copy(archived = true)
        val cancelled = TaskEntity(
            id = 4, title = "Cancelada", completed = true,
            status = com.ordia.app.data.local.TaskStatus.CANCELLED,
            completedAt = dayAt(dayToday, 13), parentTaskId = null
        )
        val done = listOf(root, subtask, archived, cancelled)
        val answer = AssistantEngine.answer("¿qué hice hoy?", done, emptyList(), emptyList(), now, dayZone)
        assertTrue("cuenta solo 1 raíz: ${answer.text}", answer.text.contains("completaste 1"))
        assertTrue("nombra solo la raíz: ${answer.text}", answer.text.contains("Raíz"))
        assertFalse("no cuenta subtarea: ${answer.text}", answer.text.contains("Subtarea"))
        assertFalse("no cuenta archivada: ${answer.text}", answer.text.contains("Archivada"))
        assertFalse("no cuenta cancelada: ${answer.text}", answer.text.contains("Cancelada"))
    }

    @Test fun completedRecap_doesNotHijackAgendaQuery() {
        // "¿qué tengo hoy?" es agenda, NO recap. La rama nueva no debe secuestrarla.
        val now = dayAt(dayToday, 15)
        val pending = TaskEntity(id = 1, title = "Pendiente", dueAt = dayAt(dayToday, 18))
        val done = completedTask(2, "Hecha", dayAt(dayToday, 9))
        val answer = AssistantEngine.answer("¿qué tengo hoy?", listOf(pending, done), emptyList(), emptyList(), now, dayZone)
        assertFalse("no responde recap a agenda: ${answer.text}", answer.text.contains("completaste"))
    }

    // --- Recap de logros ampliado a períodos ("esta semana"/"este mes"/"anteayer") ---
    // Antes "¿qué completé esta semana?" caía a completedAnswer pero, al no reconocer
    // el scope, lo resolvía como HOY: silenciaba lo terminado el lunes (mentira por
    // omisión del logro, justo lo que el recap existente corrige para "hoy"/"ayer").
    // Ahora recupera el logro del período entero con el MISMO predicado canónico
    // (raíces, COMPLETED, !archived, !CANCELLED) y los mismos límites de semana/mes
    // que SearchEngine (lun→dom; mes natural). Sin nueva pantalla. Determinista/local.

    @Test fun completedRecap_thisWeek_recoversWholeCalendarWeek() {
        // dayToday = mié 2026-07-29 → semana lun 07-27..dom 08-02.
        val now = dayAt(dayToday, 15)
        val monday = LocalDate.of(2026, 7, 27)
        val prevSunday = LocalDate.of(2026, 7, 26) // semana pasada, excluida
        val done = listOf(
            completedTask(1, "Miércoles", dayAt(dayToday, 10)),
            completedTask(2, "Lunes", dayAt(monday, 9)),
            completedTask(3, "Domingo pasado", dayAt(prevSunday, 9))
        )
        val answer = AssistantEngine.answer("¿qué completé esta semana?", done, emptyList(), emptyList(), now, dayZone)
        assertTrue("etiqueta de semana: ${answer.text}", answer.text.startsWith("Esta semana"))
        assertTrue("cuenta 2 (no 3): ${answer.text}", answer.text.contains("completaste 2"))
        assertTrue("nombra la de hoy: ${answer.text}", answer.text.contains("Miércoles"))
        assertTrue("nombra la del lunes: ${answer.text}", answer.text.contains("Lunes"))
        assertFalse("excluye semana pasada: ${answer.text}", answer.text.contains("Domingo pasado"))
        assertEquals(AssistantAction.NONE, answer.action)
    }

    @Test fun completedRecap_thisMonth_recoversWholeCalendarMonth() {
        // dayToday = 2026-07-29 → mes jul 01..31.
        val now = dayAt(dayToday, 15)
        val done = listOf(
            completedTask(1, "Principio", dayAt(LocalDate.of(2026, 7, 1), 9)),
            completedTask(2, "Hoy", dayAt(dayToday, 10)),
            completedTask(3, "De junio", dayAt(LocalDate.of(2026, 6, 30), 9)) // mes pasado
        )
        val answer = AssistantEngine.answer("¿qué completé este mes?", done, emptyList(), emptyList(), now, dayZone)
        assertTrue("etiqueta de mes: ${answer.text}", answer.text.startsWith("Este mes"))
        assertTrue("cuenta 2: ${answer.text}", answer.text.contains("completaste 2"))
        assertTrue("nombra principio de mes: ${answer.text}", answer.text.contains("Principio"))
        assertFalse("excluye junio: ${answer.text}", answer.text.contains("De junio"))
    }

    @Test fun completedRecap_anteayer_listsDayBeforeYesterday() {
        // dayToday = mié 07-29 → anteayer = lun 07-27.
        val now = dayAt(dayToday, 15)
        val anteayer = dayToday.minusDays(2)
        val yesterday = dayToday.minusDays(1)
        val done = listOf(
            completedTask(1, "De anteayer", dayAt(anteayer, 9)),
            completedTask(2, "De ayer", dayAt(yesterday, 9)),
            completedTask(3, "De hoy", dayAt(dayToday, 9))
        )
        val answer = AssistantEngine.answer("¿qué hice anteayer?", done, emptyList(), emptyList(), now, dayZone)
        assertTrue("etiqueta anteayer: ${answer.text}", answer.text.startsWith("Anteayer"))
        assertTrue("cuenta 1: ${answer.text}", answer.text.contains("completaste 1"))
        assertTrue("nombra la de anteayer: ${answer.text}", answer.text.contains("De anteayer"))
        assertFalse("no nombra la de ayer: ${answer.text}", answer.text.contains("De ayer"))
    }

    @Test fun completedRecap_thisWeek_empty_saysSoHonestly() {
        val now = dayAt(dayToday, 15)
        // Una completada pero en semana pasada: esta semana vacía.
        val done = listOf(completedTask(1, "Otra semana", dayAt(LocalDate.of(2026, 7, 26), 9)))
        val answer = AssistantEngine.answer("¿qué completé esta semana?", done, emptyList(), emptyList(), now, dayZone)
        assertTrue("dice que no hay: ${answer.text}", answer.text.contains("no has completado"))
        assertFalse("no inventa logros: ${answer.text}", answer.text.contains("«"))
    }

    // --- Recap de logros ampliado a períodos pasados ("semana pasada"/"mes pasado") ---
    // Antes "¿qué completé la semana pasada?" caía a completedAnswer, pero como la
    // rama "semana" se resolvía al período en curso, respondía "Esta semana" y
    // listaba lo de la semana actual: una mentira por omisión del logro de la
    // semana previa (la búsqueda SÍ lo recuperaba via LAST_WEEK — el asistente y la
    // búsqueda discrepaban). Ahora el recap reconoce el modificador de pasado y va
    // al período anterior con la MISMA fuente de verdad (DateRules → SearchEngine
    // LAST_WEEK/LAST_MONTH). Sin nueva pantalla. Determinista/local.

    @Test fun completedRecap_lastWeek_recoversPreviousCalendarWeek() {
        // dayToday = mié 2026-07-29 → esta semana lun 07-27..dom 08-02;
        // semana pasada = lun 07-20..dom 07-26.
        val now = dayAt(dayToday, 15)
        val lastMonday = LocalDate.of(2026, 7, 20)
        val thisTuesday = LocalDate.of(2026, 7, 28) // esta semana, excluida
        val done = listOf(
            completedTask(1, "De la semana pasada", dayAt(lastMonday, 9)),
            completedTask(2, "De esta semana", dayAt(thisTuesday, 10))
        )
        val answer = AssistantEngine.answer("¿qué completé la semana pasada?", done, emptyList(), emptyList(), now, dayZone)
        assertTrue("etiqueta pasada: ${answer.text}", answer.text.startsWith("La semana pasada"))
        assertTrue("cuenta 1 (no 2): ${answer.text}", answer.text.contains("completaste 1"))
        assertTrue("nombra la de la semana pasada: ${answer.text}", answer.text.contains("De la semana pasada"))
        assertFalse("excluye esta semana: ${answer.text}", answer.text.contains("De esta semana"))
        assertEquals(AssistantAction.NONE, answer.action)
    }

    // --- Recap en forma adjetival ("tareas completadas/terminadas/hechas") ---
    // Es la MISMA intención de recap sin verbo recap: la forma adjetival es la
    // más cotidiana. Antes caía al menú genérico callando el logro (gap medido
    // por sonda diferencial búsqueda-vs-asistente: la búsqueda las recupera vía
    // COMPLETED_TOKENS, el asistente no). Sin ancla temporal lista los logros
    // recientes SIN filtrar por hoy (filtrar por hoy mentiría por omisión);
    // con ancla usa el período igual que las formas verbales.

    @Test fun completedRecap_adjectiveBare_listsRecentAcrossDays() {
        val now = dayAt(dayToday, 15)
        val done = listOf(
            completedTask(1, "De hoy", dayAt(dayToday, 10)),
            completedTask(2, "De ayer", dayAt(dayToday.minusDays(1), 12)),
            completedTask(3, "De anteayer", dayAt(dayToday.minusDays(2), 9))
        )
        val answer = AssistantEngine.answer("tareas completadas", done, emptyList(), emptyList(), now, dayZone)
        assertTrue("cuenta 3: ${answer.text}", answer.text.contains("Has completado 3"))
        assertTrue("nombra la de hoy: ${answer.text}", answer.text.contains("De hoy"))
        assertTrue("nombra la de ayer (sin filtro de hoy): ${answer.text}", answer.text.contains("De ayer"))
        assertTrue("nombra la de anteayer: ${answer.text}", answer.text.contains("De anteayer"))
        assertFalse("no cae al menú: ${answer.text}", answer.text.contains("Puedo organizar tu día"))
        assertFalse("no filtra por hoy: ${answer.text}", answer.text.startsWith("Hoy"))
        assertEquals(AssistantAction.NONE, answer.action)
    }

    @Test fun completedRecap_adjective_acceptsSynonyms() {
        val now = dayAt(dayToday, 15)
        val done = listOf(completedTask(1, "Revisar propuesta", dayAt(dayToday, 11)))
        for (q in listOf("tareas completadas", "tareas terminadas", "tareas hechas", "tareas finalizadas", "tareas acabadas", "mis tareas completadas")) {
            val answer = AssistantEngine.answer(q, done, emptyList(), emptyList(), now, dayZone)
            assertTrue("[$q] nombra el logro: ${answer.text}", answer.text.contains("Revisar propuesta"))
        }
    }

    @Test fun completedRecap_adjective_moreThanThree_summarizesRest() {
        val now = dayAt(dayToday, 18)
        val done = (1..5).map { completedTask(it.toLong(), "Tarea $it", dayAt(dayToday, 8 + it)) }
        val answer = AssistantEngine.answer("tareas hechas", done, emptyList(), emptyList(), now, dayZone)
        assertTrue("cuenta 5: ${answer.text}", answer.text.contains("Has completado 5"))
        assertTrue("resume el resto: ${answer.text}", answer.text.contains("y 2 más"))
        assertTrue("nombra los 3 más recientes: ${answer.text}",
            answer.text.contains("Tarea 5") && answer.text.contains("Tarea 3"))
        assertFalse("no nombra el más antiguo: ${answer.text}", answer.text.contains("Tarea 1"))
    }

    @Test fun completedRecap_adjectiveWithAnchor_usesPeriodNotToday() {
        // dayToday = mié 2026-07-29 → semana pasada lun 07-20..dom 07-26.
        val now = dayAt(dayToday, 15)
        val done = listOf(
            completedTask(1, "De la semana pasada", dayAt(LocalDate.of(2026, 7, 22), 10)),
            completedTask(2, "De esta semana", dayAt(LocalDate.of(2026, 7, 28), 10))
        )
        val answer = AssistantEngine.answer("tareas completadas la semana pasada", done, emptyList(), emptyList(), now, dayZone)
        assertTrue("etiqueta pasada: ${answer.text}", answer.text.startsWith("La semana pasada"))
        assertTrue("nombra la de la semana pasada: ${answer.text}", answer.text.contains("De la semana pasada"))
        assertFalse("excluye esta semana: ${answer.text}", answer.text.contains("De esta semana"))
    }

    @Test fun completedRecap_adjective_empty_saysSoHonestly() {
        val now = dayAt(dayToday, 15)
        val answer = AssistantEngine.answer("tareas completadas", emptyList(), emptyList(), emptyList(), now, dayZone)
        assertTrue("dice que no hay: ${answer.text}", answer.text.contains("Aún no tienes tareas completadas."))
        assertFalse("no inventa logros: ${answer.text}", answer.text.contains("«"))
        assertFalse("no cae al menú: ${answer.text}", answer.text.contains("Puedo organizar tu día"))
    }

    @Test fun completedRecap_adjective_excludesSubtasksArchivedCancelled() {
        // Mismo predicado canónico que el recap verbal: solo raíces, no
        // archivadas, no canceladas.
        val now = dayAt(dayToday, 15)
        val root = completedTask(1, "Raíz", dayAt(dayToday, 10))
        val subtask = completedTask(2, "Subtarea", dayAt(dayToday, 11)).copy(parentTaskId = 1)
        val archived = completedTask(3, "Archivada", dayAt(dayToday, 12)).copy(archived = true)
        val cancelled = TaskEntity(
            id = 4, title = "Cancelada", completed = true,
            status = com.ordia.app.data.local.TaskStatus.CANCELLED,
            completedAt = dayAt(dayToday, 13), parentTaskId = null
        )
        val answer = AssistantEngine.answer("tareas terminadas", listOf(root, subtask, archived, cancelled), emptyList(), emptyList(), now, dayZone)
        assertTrue("cuenta solo 1 raíz: ${answer.text}", answer.text.contains("Has completado 1"))
        assertFalse("no cuenta subtarea: ${answer.text}", answer.text.contains("Subtarea"))
        assertFalse("no cuenta archivada: ${answer.text}", answer.text.contains("Archivada"))
        assertFalse("no cuenta cancelada: ${answer.text}", answer.text.contains("Cancelada"))
    }

    @Test fun completedRecap_adjective_doesNotHijackInfinitive() {
        // El infinitivo "completar/terminar" es acción pendiente, NO logro:
        // no debe disparar el recap (emparejamiento por palabra, no subcadena).
        val now = dayAt(dayToday, 15)
        val done = listOf(completedTask(1, "Hecha", dayAt(dayToday, 10)))
        for (q in listOf("quiero completar la tarea mañana", "debo terminar la tarea", "tengo que acabar la tarea")) {
            val answer = AssistantEngine.answer(q, done, emptyList(), emptyList(), now, dayZone)
            assertFalse("[$q] no responde recap: ${answer.text}",
                answer.text.contains("Has completado") || answer.text.contains("completaste"))
        }
    }

    @Test fun completedRecap_adjective_wordBoundary_determinadaIsNotTerminada() {
        // "determinada" CONTIENE "terminada" como subcadena pero no es un
        // adjetivo de completado; el emparejamiento por palabra lo excluye.
        val now = dayAt(dayToday, 15)
        val done = listOf(completedTask(1, "Hecha", dayAt(dayToday, 10)))
        val answer = AssistantEngine.answer("la tarea determinada", done, emptyList(), emptyList(), now, dayZone)
        assertFalse("no responde recap: ${answer.text}",
            answer.text.contains("Has completado") || answer.text.contains("completaste"))
    }

    @Test fun completedRecap_lastWeek_acceptsUltimaVariant() {
        // "última semana" (plegado a "ultima") es sinónimo coloquial de "semana
        // pasada": mismo alcance, misma etiqueta. La consulta ya viene sin acentos.
        val now = dayAt(dayToday, 15)
        val lastMonday = LocalDate.of(2026, 7, 20)
        val done = listOf(completedTask(1, "Logro previo", dayAt(lastMonday, 9)))
        val answer = AssistantEngine.answer("¿qué hice la ultima semana?", done, emptyList(), emptyList(), now, dayZone)
        assertTrue("etiqueta pasada: ${answer.text}", answer.text.startsWith("La semana pasada"))
        assertTrue("cuenta 1: ${answer.text}", answer.text.contains("completaste 1"))
        assertTrue("nombra el logro: ${answer.text}", answer.text.contains("Logro previo"))
    }

    @Test fun completedRecap_lastMonth_recoversPreviousCalendarMonth() {
        // dayToday = 2026-07-29 → mes pasado = junio 01..30.
        val now = dayAt(dayToday, 15)
        val done = listOf(
            completedTask(1, "De junio", dayAt(LocalDate.of(2026, 6, 15), 9)),
            completedTask(2, "De julio", dayAt(LocalDate.of(2026, 7, 1), 9)) // este mes
        )
        val answer = AssistantEngine.answer("¿qué completé el mes pasado?", done, emptyList(), emptyList(), now, dayZone)
        assertTrue("etiqueta mes pasado: ${answer.text}", answer.text.startsWith("El mes pasado"))
        assertTrue("cuenta 1: ${answer.text}", answer.text.contains("completaste 1"))
        assertTrue("nombra lo de junio: ${answer.text}", answer.text.contains("De junio"))
        assertFalse("excluye julio: ${answer.text}", answer.text.contains("De julio"))
    }

    @Test fun completedRecap_lastWeek_empty_saysSoHonestly() {
        val now = dayAt(dayToday, 15)
        // Una completada pero en esta semana: la semana pasada está vacía.
        val done = listOf(completedTask(1, "Esta semana", dayAt(LocalDate.of(2026, 7, 28), 9)))
        val answer = AssistantEngine.answer("¿qué completé la semana pasada?", done, emptyList(), emptyList(), now, dayZone)
        assertTrue("dice que no hay: ${answer.text}", answer.text.contains("no has completado"))
        assertFalse("no inventa logros: ${answer.text}", answer.text.contains("«"))
    }

    @Test fun completedRecap_bareSemana_notTreatedAsPast() {
        // Guard anti-falso-positivo: "esta semana" (sin modificador de pasado) NO
        // debe interpretarse como semana pasada. "este"/"esta" no están en
        // PAST_PERIOD_MODIFIERS, así que sigue siendo la semana en curso.
        val now = dayAt(dayToday, 15)
        val thisMonday = LocalDate.of(2026, 7, 27)
        val done = listOf(completedTask(1, "De esta semana", dayAt(thisMonday, 9)))
        val answer = AssistantEngine.answer("¿qué completé esta semana?", done, emptyList(), emptyList(), now, dayZone)
        assertTrue("sigue siendo esta semana: ${answer.text}", answer.text.startsWith("Esta semana"))
        assertTrue("cuenta 1: ${answer.text}", answer.text.contains("completaste 1"))
    }

    @Test fun completedRecap_ultimoDiaDeLaSemana_notTreatedAsLastWeek() {
        // Guard anti-falso-positivo de PARIDAD con la búsqueda: "el último día de
        // la semana" habla del último DÍA de ESTA semana (p.ej. el domingo), NO de
        // la semana pasada. SearchEngine deliberadamente EXCLUYE "ultimo"/
        // "ultimos" de LAST_WEEK_TOKENS justo para no secuestrar esta frase hacia
        // "semana pasada" (la semana es femenina: "última semana" sí es pasado;
        // "último día de la semana" no). El asistente (c.611) usaba un superset
        // único PAST_PERIOD_MODIFIERS para semana Y mes, así que "último" casaba
        // en la rama de semana y la mentía por omisión: etiquetaba "La semana
        // pasada" y excluía la tarea completada esta semana que el usuario pedía.
        // Ahora la rama de semana usa el MISMO set que SearchEngine.LAST_WEEK
        // (sin ultimo/ultimos) y la frase cae a "Esta semana", como la búsqueda.
        val now = dayAt(dayToday, 15) // mié 2026-07-29 → esta semana 07-27..08-02
        val thisWednesday = LocalDate.of(2026, 7, 29)
        val done = listOf(completedTask(1, "Cerrado hoy", dayAt(thisWednesday, 9)))
        val answer = AssistantEngine.answer("¿qué completé el último día de la semana?", done, emptyList(), emptyList(), now, dayZone)
        assertFalse("no miente 'La semana pasada': ${answer.text}", answer.text.startsWith("La semana pasada"))
        assertTrue("es esta semana: ${answer.text}", answer.text.startsWith("Esta semana"))
        assertTrue("cuenta la de esta semana: ${answer.text}", answer.text.contains("completaste 1"))
        assertTrue("nombra el logro: ${answer.text}", answer.text.contains("Cerrado hoy"))
    }

    @Test fun completedRecap_lastMonth_acceptsUltimoVariant() {
        // Paridad con SearchEngine.LAST_MONTH: "el último mes" (plegado a
        // "ultimo") SÍ es pasado — el mes es masculino, así que "último" es el
        // modificador natural (a diferencia de la semana, donde "último día de
        // la semana" no lo es). Se cubre que el split por período no rompa la
        // rama masculina del mes.
        val now = dayAt(dayToday, 15) // 2026-07-29 → mes pasado = junio
        val done = listOf(completedTask(1, "Logro de junio", dayAt(LocalDate.of(2026, 6, 15), 9)))
        val answer = AssistantEngine.answer("¿qué completé el último mes?", done, emptyList(), emptyList(), now, dayZone)
        assertTrue("etiqueta mes pasado: ${answer.text}", answer.text.startsWith("El mes pasado"))
        assertTrue("cuenta 1: ${answer.text}", answer.text.contains("completaste 1"))
        assertTrue("nombra el logro: ${answer.text}", answer.text.contains("Logro de junio"))
    }

    @Test fun entityLookup_aQueHoraTengo_respondeHoraDelStartAt() {
        // «¿a qué hora tengo la reunión?» con un slot agendado (startAt 11:00 Sto.Dgo).
        val now = dayAt(dayToday, 9)
        val start = dayAt(dayToday, 11)
        val reunion = TaskEntity(
            id = 1, title = "Reunión de equipo",
            startAt = start, dueAt = dayAt(dayToday, 12),
            durationMinutes = 60, status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "¿a qué hora tengo la reunión?",
            listOf(reunion), emptyList(), emptyList(), now, dayZone
        )
        val expected = DateRules.formatTime(start)
        assertFalse("no cae al menú genérico: ${answer.text}", answer.text.contains("Puedo organizar"))
        assertTrue("nombra la tarea: ${answer.text}", answer.text.contains("Reunión de equipo"))
        assertTrue("da la hora ($expected): ${answer.text}", answer.text.contains(expected))
        assertEquals("relaciona la tarea: ${answer.relatedTaskIds}", listOf(1L), answer.relatedTaskIds)
    }

    @Test fun entityLookup_aQueHoraEs_respondeHoraDelDueAtSinStartAt() {
        // Sin startAt: la hora del vencimiento es la única marca de reloj.
        val now = dayAt(dayToday, 9)
        val due = dayAt(dayToday, 15) + 30 * 60_000L
        val cita = TaskEntity(id = 2, title = "Cita médica", dueAt = due)
        val answer = AssistantEngine.answer(
            "¿a qué hora es la cita médica?",
            listOf(cita), emptyList(), emptyList(), now, dayZone
        )
        val expected = DateRules.formatTime(due)
        assertFalse("no cae al menú: ${answer.text}", answer.text.contains("Puedo organizar"))
        assertTrue("nombra la cita: ${answer.text}", answer.text.contains("Cita médica"))
        assertTrue("da la hora ($expected): ${answer.text}", answer.text.contains(expected))
    }

    @Test fun entityLookup_aQueHora_tareaSoloFecha_diceSinHoraFija() {
        // dueAt a medianoche = solo fecha, no hora de reloj: honesto, no inventa «00:00».
        val now = dayAt(dayToday, 9)
        val soloFecha = TaskEntity(id = 3, title = "Entrega informe", dueAt = dayToday.atTime(0, 0).atZone(dayZone).toInstant().toEpochMilli())
        val answer = AssistantEngine.answer(
            "¿a qué hora tengo la entrega?",
            listOf(soloFecha), emptyList(), emptyList(), now, dayZone
        )
        assertFalse("no cae al menú: ${answer.text}", answer.text.contains("Puedo organizar"))
        assertTrue("avisa sin hora fija: ${answer.text}", answer.text.contains("hora fija"))
        assertFalse("no inventa medianoche: ${answer.text}", answer.text.contains("00:00"))
    }

    @Test fun entityLookup_cuandoPago_respondeFecha() {
        // «¿cuándo pago la luz?» → fecha del vencimiento.
        val now = dayAt(dayToday, 9)
        val due = dayAt(LocalDate.of(2026, 9, 15), 12)
        val luz = TaskEntity(id = 4, title = "Pagar luz", dueAt = due)
        val answer = AssistantEngine.answer(
            "¿cuándo pago la luz?",
            listOf(luz), emptyList(), emptyList(), now, dayZone
        )
        val expected = DateRules.formatDate(due)
        assertFalse("no cae al menú: ${answer.text}", answer.text.contains("Puedo organizar"))
        assertTrue("nombra la tarea: ${answer.text}", answer.text.contains("Pagar luz"))
        assertTrue("da la fecha ($expected): ${answer.text}", answer.text.contains(expected))
    }

    @Test fun entityLookup_multiplesCoincidencias_pideDisambiguar() {
        // Dos tareas con «reunión»: no elige a ciegas, nombra ambas para desambiguar.
        val now = dayAt(dayToday, 9)
        val r1 = TaskEntity(id = 10, title = "Reunión de equipo", startAt = dayAt(dayToday, 11))
        val r2 = TaskEntity(id = 11, title = "Reunión con cliente", startAt = dayAt(dayToday, 16))
        val answer = AssistantEngine.answer(
            "¿a qué hora tengo la reunión?",
            listOf(r1, r2), emptyList(), emptyList(), now, dayZone
        )
        assertFalse("no cae al menú: ${answer.text}", answer.text.contains("Puedo organizar"))
        assertTrue("pide desambiguar: ${answer.text}", answer.text.contains("varias"))
        assertTrue("nombra la primera: ${answer.text}", answer.text.contains("Reunión de equipo"))
        assertTrue("nombra la segunda: ${answer.text}", answer.text.contains("Reunión con cliente"))
    }

    @Test fun entityLookup_noEncuentra_noInventa() {
        // La entidad preguntada no existe entre las tareas: honesto, no inventa.
        val now = dayAt(dayToday, 9)
        val otra = TaskEntity(id = 20, title = "Comprar pan", dueAt = dayAt(dayToday, 18))
        val answer = AssistantEngine.answer(
            "¿a qué hora tengo la reunión?",
            listOf(otra), emptyList(), emptyList(), now, dayZone
        )
        assertFalse("no cae al menú: ${answer.text}", answer.text.contains("Puedo organizar"))
        assertTrue("dice que no la encuentra: ${answer.text}", answer.text.contains("No encuentro"))
        assertFalse("no inventa una hora: ${answer.text}", answer.text.contains("está a las"))
    }

    @Test fun entityLookup_noRobaAgendaNiWhatNow() {
        // «¿qué tengo mañana?» (agenda) y «¿qué hago ahora?» (what-now) siguen
        // ruteándose a sus ramas, no a la búsqueda de entidad.
        val now = dayAt(dayToday, 9)
        val reunion = TaskEntity(id = 1, title = "Reunión de equipo", startAt = dayAt(dayToday, 11))
        val agendaAnswer = AssistantEngine.answer(
            "¿qué tengo mañana?",
            listOf(reunion), emptyList(), emptyList(), now, dayZone
        )
        assertFalse("agenda no dice «no encuentro»: ${agendaAnswer.text}", agendaAnswer.text.contains("No encuentro"))
        val whatNowAnswer = AssistantEngine.answer(
            "¿qué hago ahora?",
            listOf(reunion), emptyList(), emptyList(), now, dayZone
        )
        assertFalse("what-now no dice «no encuentro»: ${whatNowAnswer.text}", whatNowAnswer.text.contains("No encuentro"))
    }

    @Test fun entityLookup_dondeEs_nombraEntidad() {
        // «¿dónde es la cita?» cae a la rama de búsqueda (no al menú genérico) y
        // nombra la entidad encontrada; no fabrica un lugar, solo la identifica.
        val now = dayAt(dayToday, 9)
        val cita = TaskEntity(id = 30, title = "Cita médica", dueAt = dayAt(dayToday, 15))
        val answer = AssistantEngine.answer(
            "¿dónde es la cita médica?",
            listOf(cita), emptyList(), emptyList(), now, dayZone
        )
        assertFalse("no cae al menú: ${answer.text}", answer.text.contains("Puedo organizar"))
        assertTrue("nombra la cita: ${answer.text}", answer.text.contains("Cita médica"))
    }

    // --- c.552: contexto de "¿qué hago ahora?" — hueco hasta la próxima cita.
    // El asistente cruza la duración estimada de la sugerida con la próxima cita
    // (startAt futuro de otra raíz activa) y decide en una frase. Inteligencia
    // local honesta (no IA fingida): dos comparaciones sobre enteros.
    // La sugerida es URGENT sin startAt (timeRank 2); la cita es una raíz con
    // startAt futuro MÁS ALLÁ de la ventana inminente (15 min) → isScheduledLater
    // (rank -1), así queda como "próxima cita" sin robarle el puesto a la urgente.
    @Test fun whatNow_warnsWhenTaskDoesNotFitBeforeNextCommitment() {
        // Sugerida: urgente de 40 min. Próxima cita en 30 min. No cabe → el
        // asistente AVISA ("ojo: tu próxima cita es en ~30 min") para que el
        // usuario no arranque algo que la cita interrumpirá.
        val now = 1_000_000_000_000L
        val urgente = TaskEntity(
            id = 1, title = "Informe",
            durationMinutes = 40,
            priority = TaskPriority.URGENT
        )
        val reunion = TaskEntity(
            id = 2, title = "Reunión",
            startAt = now + 30 * 60_000L, // 30 min: fuera de ventana inminente (15)
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "¿qué hago ahora?",
            listOf(urgente, reunion), emptyList(), emptyList(), now
        )
        assertTrue("avisa que la cita interrumpirá: ${answer.text}", answer.text.lowercase().contains("tu próxima cita es en ~30 min"))
        assertTrue("no miente con 'te alcanza': ${answer.text}", !answer.text.lowercase().contains("te alcanza"))
    }

    @Test fun whatNow_warnsImminentCommitmentUnderFiveMin() {
        // Caso de borde c.553: cita inminente en 3 min. PRE-fix, `minutesUntilNext
        // Commitment` truncaba 3→0 y devolvía null → el asistente CALLABA aunque
        // la cita estaba a punto de empezar. La sugerida es una tarea EN CURSO
        // (rank 6, gana sobre la cita inminente rank 4) con 25 min restantes, así
        // la cita no es la sugerida y queda como "próxima cita". 25 > 3 → el
        // asistente AVISA "ojo: tu próxima cita es en ~3 min" (valor exacto, no
        // truncado). Sin c.553 este test fallaría: gap era null → sin aviso.
        val now = 1_000_000_000_000L
        val enCurso = TaskEntity(
            id = 1, title = "Informe",
            startAt = now - 5 * 60_000L, // empezó hace 5 min, ventana activa
            durationMinutes = 30,         // 30 planificados → 25 restantes
            priority = TaskPriority.URGENT
        )
        val cita = TaskEntity(
            id = 2, title = "Cita",
            startAt = now + 3 * 60_000L, // 3 min: inminente (<5), no truncado a 0
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "¿qué hago ahora?",
            listOf(enCurso, cita), emptyList(), emptyList(), now
        )
        assertTrue("avisa cita inminente (3 min): ${answer.text}", answer.text.lowercase().contains("tu próxima cita es en ~3 min"))
        assertTrue("la sugerencia es la tarea en curso: ${answer.text}", answer.text.lowercase().contains("sigue con"))
    }

    @Test fun whatNow_confirmsWhenTaskSnuglyFitsBeforeNextCommitment() {
        // Sugerida: 25 min. Próxima cita en 40 min. Cabe y el margen es corto
        // (25 ocupa más de la mitad de 40) → confirma "te alcanza antes de tu
        // próxima cita" para que el usuario se decida a arrancar.
        val now = 1_000_000_000_000L
        val urgente = TaskEntity(
            id = 1, title = "Informe",
            durationMinutes = 25,
            priority = TaskPriority.URGENT
        )
        val reunion = TaskEntity(
            id = 2, title = "Reunión",
            startAt = now + 40 * 60_000L,
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "¿qué hago ahora?",
            listOf(urgente, reunion), emptyList(), emptyList(), now
        )
        assertTrue("confirma que alcanza: ${answer.text}", answer.text.lowercase().contains("te alcanza antes de tu próxima cita"))
        assertTrue("no avisa con 'ojo': ${answer.text}", !answer.text.lowercase().contains("ojo"))
    }

    @Test fun whatNow_silentWhenTaskFitsComfortablyBeforeNextCommitment() {
        // Sugerida: 10 min (mínimo). Próxima cita en 200 min. Cabe de SOBRA → el
        // asistente CALLA: un "te alcanza" trivial sólo añadiría ruido; el
        // silencio es honesto (no hay decisión difícil que señalar).
        val now = 1_000_000_000_000L
        val urgente = TaskEntity(
            id = 1, title = "Informe",
            durationMinutes = 10,
            priority = TaskPriority.URGENT
        )
        val reunion = TaskEntity(
            id = 2, title = "Reunión",
            startAt = now + 200 * 60_000L,
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "¿qué hago ahora?",
            listOf(urgente, reunion), emptyList(), emptyList(), now
        )
        assertFalse("no mete ruido de cita: ${answer.text}", answer.text.lowercase().contains("próxima cita"))
        assertFalse("no miente con 'te alcanza': ${answer.text}", answer.text.lowercase().contains("te alcanza"))
    }

    @Test fun whatNow_silentWhenNoUpcomingCommitment() {
        // Sin ninguna startAt futura → no hay cita cercana → el motor devuelve
        // null y el asistente no menciona "próxima cita" (sin ruido).
        val now = 1_000_000_000_000L
        val urgente = TaskEntity(
            id = 1, title = "Informe",
            durationMinutes = 40,
            priority = TaskPriority.URGENT
        )
        val answer = AssistantEngine.answer(
            "¿qué hago ahora?",
            listOf(urgente), emptyList(), emptyList(), now
        )
        assertFalse("sin cita, sin mención: ${answer.text}", answer.text.lowercase().contains("próxima cita"))
        assertTrue("sigue dando la sugerencia: ${answer.text}", answer.text.lowercase().contains("empieza por"))
    }

    // --- c.557: "¿qué hago ahora?" — cuando la sugerida NO cabe antes de la
    // próxima cita PERO existe otra tarea que SÍ cabe, el asistente la nombra
    // como alternativa accionable ("antes cabe «X»"). Inteligencia contextual
    // honesta (no IA fingida): convierte el aviso pasivo en una micro-decisión
    // productiva, sin nueva pantalla/botón. Reusa WhatNowEngine.ordered (fuente
    // única) para elegir la mejor tarea que cabe. Sólo dispara cuando hay algo
    // que genuinamente cabe (plannedDuration <= gap); si no, mantiene el aviso
    // simple de c.552 (no inventa una alternativa que no existe).
    @Test fun whatNow_suggestsFittingAlternativeBeforeCommitment() {
        // Sugerida: urgente de 40 min. Próxima cita en 30 min (no cabe). Hay una
        // tarea rápida de 10 min (cabría) de menor prioridad → el asistente la
        // nombra ("antes cabe «Responder»") en vez de sólo avisar.
        val now = 1_000_000_000_000L
        val urgente = TaskEntity(
            id = 1, title = "Informe",
            durationMinutes = 40,
            priority = TaskPriority.URGENT
        )
        val rapida = TaskEntity(
            id = 3, title = "Responder",
            durationMinutes = 10,
            priority = TaskPriority.NORMAL
        )
        val reunion = TaskEntity(
            id = 2, title = "Reunión",
            startAt = now + 30 * 60_000L, // >15 min: no inminente → la urgente es la sugerida
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "¿qué hago ahora?",
            listOf(urgente, rapida, reunion), emptyList(), emptyList(), now
        )
        assertTrue("avisa la cita: ${answer.text}", answer.text.lowercase().contains("tu próxima cita es en ~30 min"))
        assertTrue("nombra la alternativa que cabe: ${answer.text}", answer.text.contains("Responder"))
        assertTrue("frase de 'antes cabe': ${answer.text}", answer.text.lowercase().contains("antes cabe"))
    }

    @Test fun whatNow_noAlternativeWhenNothingFitsBeforeCommitment() {
        // Sugerida: urgente de 40 min. Próxima cita en 30 min (no cabe). La única
        // otra tarea activa también dura 40 min (no cabe en 30) → NO se inventa
        // una alternativa; el asistente mantiene el aviso simple de c.552.
        val now = 1_000_000_000_000L
        val urgente = TaskEntity(
            id = 1, title = "Informe",
            durationMinutes = 40,
            priority = TaskPriority.URGENT
        )
        val otra = TaskEntity(
            id = 3, title = "Propuesta",
            durationMinutes = 40,
            priority = TaskPriority.NORMAL
        )
        val reunion = TaskEntity(
            id = 2, title = "Reunión",
            startAt = now + 30 * 60_000L,
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "¿qué hago ahora?",
            listOf(urgente, otra, reunion), emptyList(), emptyList(), now
        )
        assertTrue("avisa la cita: ${answer.text}", answer.text.lowercase().contains("tu próxima cita es en ~30 min"))
        assertFalse("no inventa alternativa que no cabe: ${answer.text}", answer.text.contains("Propuesta"))
        assertFalse("no frasea 'antes cabe' sin alternativa: ${answer.text}", answer.text.lowercase().contains("antes cabe"))
    }

    @Test fun whatNow_alternativeExcludesScheduledLaterTask() {
        // La alternativa NO debe ser una tarea con startAt futuro (tiene su
        // propio hueco): aunque su duración quepa en el gap, no es algo que el
        // usuario pueda arrancar AHORA. Aquí la "rápida" (10 min) tiene startAt
        // futuro (+200) → aunque cabría en 30 min, se excluye; queda el aviso
        // simple sin "antes cabe".
        val now = 1_000_000_000_000L
        val urgente = TaskEntity(
            id = 1, title = "Informe",
            durationMinutes = 40,
            priority = TaskPriority.URGENT
        )
        val rapidaProgramada = TaskEntity(
            id = 3, title = "Responder",
            durationMinutes = 10,
            priority = TaskPriority.NORMAL,
            startAt = now + 200 * 60_000L, // futuro: tiene su propio hueco
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val reunion = TaskEntity(
            id = 2, title = "Reunión",
            startAt = now + 30 * 60_000L, // próxima cita (30 < 200)
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "¿qué hago ahora?",
            listOf(urgente, rapidaProgramada, reunion), emptyList(), emptyList(), now
        )
        assertTrue("avisa la cita: ${answer.text}", answer.text.lowercase().contains("tu próxima cita es en ~30 min"))
        assertFalse("no ofrece una tarea con hueco propio: ${answer.text}", answer.text.lowercase().contains("antes cabe"))
        assertFalse("no nombra la programada como alternativa: ${answer.text}", answer.text.contains("Responder"))
    }

    @Test fun whatNow_noAlternativeWhenSuggestedFitsSnugly() {
        // Guard de regresión (c.557): la alternativa sólo se ofrece en la rama
        // "no cabe" (taskMinutes > gap). Si la sugerida CABE aunque sea justo
        // (25 en 40 → snug), el asistente confirma "te alcanza" y NO introduce
        // "antes cabe" aunque exista una rápida de 10 min: la sugerida ya es la
        // decisión correcta y añadir una alternativa sería ruido contradictorio.
        val now = 1_000_000_000_000L
        val urgente = TaskEntity(
            id = 1, title = "Informe",
            durationMinutes = 25,
            priority = TaskPriority.URGENT
        )
        val rapida = TaskEntity(
            id = 3, title = "Responder",
            durationMinutes = 10,
            priority = TaskPriority.NORMAL
        )
        val reunion = TaskEntity(
            id = 2, title = "Reunión",
            startAt = now + 40 * 60_000L,
            status = com.ordia.app.data.local.TaskStatus.PLANNED
        )
        val answer = AssistantEngine.answer(
            "¿qué hago ahora?",
            listOf(urgente, rapida, reunion), emptyList(), emptyList(), now
        )
        assertTrue("confirma que alcanza: ${answer.text}", answer.text.lowercase().contains("te alcanza antes de tu próxima cita"))
        assertFalse("no ofrece alternativa cuando la sugerida ya cabe: ${answer.text}", answer.text.lowercase().contains("antes cabe"))
    }

    // ---- Panorama del día a demanda ("¿resumen del día?"/"¿cuántas tengo hoy?") ----
    //
    // El asistente respondía al veredicto ("¿voy bien?") y a la lista de agenda
    // ("¿qué tengo hoy?"), pero la forma más natural de pedir el PANORAMA —
    // cuántas hechas/pendientes/vencidas + cómo va el día — caía al menú genérico.
    // Ordía YA calcula esos conteos en SummaryEngine (fuente única de la tarjeta de
    // Hoy); ahora el asistente los expone a demanda reusando el MISMO motor, para
    // que el diálogo y la tarjeta nunca discrepen. Sin nueva pantalla/botón.

    @Test fun daySummary_resumenDelDia_daRecuentoYVeredicto() {
        val now = dayAt(dayToday, 9)
        val answer = AssistantEngine.answer(
            "¿resumen del día?",
            listOf(
                TaskEntity(id = 1, title = "Hecha", dueAt = dayAt(dayToday, 11), durationMinutes = 30, status = com.ordia.app.data.local.TaskStatus.COMPLETED, completed = true, completedAt = now - 1000),
                TaskEntity(id = 2, title = "Pend", dueAt = dayAt(dayToday, 15), durationMinutes = 30)
            ),
            emptyList(), emptyList(),
            now, dayZone
        )
        assertFalse("no cae al menú genérico: ${answer.text}", answer.text.contains("Puedo organizar"))
        assertTrue("menciona las completadas: ${answer.text}", answer.text.contains("hecha"))
        assertTrue("menciona las pendientes: ${answer.text}", answer.text.contains("pendiente"))
    }

    @Test fun daySummary_cuantasTengoHoy_daRecuentoConDuracion() {
        val now = dayAt(dayToday, 9)
        val answer = AssistantEngine.answer(
            "¿cuántas tareas tengo hoy?",
            listOf(
                TaskEntity(id = 1, title = "A", dueAt = dayAt(dayToday, 11), durationMinutes = 90),
                TaskEntity(id = 2, title = "B", dueAt = dayAt(dayToday, 15), durationMinutes = 90)
            ),
            emptyList(), emptyList(),
            now, dayZone
        )
        assertFalse("no cae al menú: ${answer.text}", answer.text.contains("Puedo organizar"))
        assertTrue("cuenta 2 pendientes: ${answer.text}", answer.text.contains("2 pendientes"))
        assertTrue("incluye la duración estimada: ${answer.text}", answer.text.contains("~3h"))
    }

    @Test fun daySummary_noRobaAgendaNiWhatNowNiRecap() {
        val now = dayAt(dayToday, 9)
        val reunion = TaskEntity(id = 1, title = "Reunión", dueAt = dayAt(dayToday, 11))
        // Agenda ("¿qué tengo hoy?") sigue listando tareas, no dando recuento.
        val agenda = AssistantEngine.answer(
            "¿qué tengo hoy?",
            listOf(reunion), emptyList(), emptyList(), now, dayZone
        )
        assertTrue("agenda lista el título (no recuento): ${agenda.text}", agenda.text.contains("Reunión"))
        assertFalse("agenda no cuenta 'pendientes': ${agenda.text}", agenda.text.contains("pendientes"))
        // What-now sigue dando la siguiente tarea.
        val whatNow = AssistantEngine.answer(
            "¿qué hago ahora?",
            listOf(reunion), emptyList(), emptyList(), now, dayZone
        )
        assertEquals("what-now relaciona la tarea", listOf(1L), whatNow.relatedTaskIds)
        // Recap ("¿qué hice hoy?") sigue siendo logro, no recuento pendiente.
        val recap = AssistantEngine.answer(
            "¿qué hice hoy?",
            listOf(reunion), emptyList(), emptyList(), now, dayZone
        )
        assertTrue("recap habla de completadas/vacío: ${recap.text}",
            recap.text.contains("completaste") || recap.text.contains("no has completado"))
    }

    @Test fun daySummary_noRobaAgendaDeOtroDia() {
        // "¿cuántas tengo mañana?" NO activa el recuento de hoy: exige "hoy".
        // (Tampoco es agenda: "cuántas tengo" no contiene "qué tengo" — cae al
        // menú genérico, lo cual es correcto: no se inventa un recuento de mañana.)
        val now = dayAt(dayToday, 9)
        val answer = AssistantEngine.answer(
            "¿cuántas tengo mañana?",
            listOf(TaskEntity(id = 1, title = "Mañana", dueAt = dayAt(dayToday.plusDays(1), 11))),
            emptyList(), emptyList(),
            now, dayZone
        )
        assertFalse("no cuenta hoy: ${answer.text}", answer.text.contains("Hoy:"))
        assertTrue("cae al menú genérico (no inventa recuento): ${answer.text}", answer.text.contains("Puedo organizar"))
    }

    @Test fun daySummary_noRepiteVencidasComoCola() {
        // Con vencidas: se cuentan inline como métrica primaria y NO se repiten
        // como "Además, N vencidas" (anti-doble-señalización, paridad c.409/c.410).
        val now = dayAt(dayToday, 9)
        val answer = AssistantEngine.answer(
            "¿cómo va el día?",
            listOf(
                TaskEntity(id = 1, title = "Hoy", dueAt = dayAt(dayToday, 11), durationMinutes = 30),
                TaskEntity(id = 2, title = "Vencida", dueAt = dayAt(dayToday.minusDays(1), 9), durationMinutes = 30)
            ),
            emptyList(), emptyList(),
            now, dayZone
        )
        assertTrue("cuenta la vencida inline: ${answer.text}", answer.text.contains("vencida"))
        assertFalse("no repite vencida como 'Además': ${answer.text}", answer.text.contains("Además"))
    }

    @Test fun daySummary_vacio_esHonesto() {
        val now = dayAt(dayToday, 9)
        val answer = AssistantEngine.answer(
            "¿resumen del día?",
            emptyList(), emptyList(), emptyList(),
            now, dayZone
        )
        assertTrue("dice que no hay pendientes: ${answer.text}", answer.text.contains("no tienes tareas pendientes"))
    }

    @Test fun daySummary_noCallaCompromisoVencido() {
        // Recuento despejado pero con un compromiso vencido: no calla la promesa
        // (paridad con dayLoad — la cola informativa se mantiene).
        val now = dayAt(dayToday, 9)
        val overdueDue = dayAt(dayToday.minusDays(3), 10)
        val commitment = overdueCommitment(1, "te llamo el martes", overdueDue)
        val answer = AssistantEngine.answer(
            "¿resumen del día?",
            emptyList(), emptyList(), listOf(commitment),
            now, dayZone
        )
        assertTrue("no calla el compromiso vencido: ${answer.text}",
            answer.text.contains("compromiso") && answer.text.contains("vencido"))
    }

    // --- c.604: el asistente RECIBE `zone` pero lo silenciaba en el ranking.
    // WhatNowEngine.ordered/suggest tienen `zone = ZoneId.systemDefault()`;
    // AssistantEngine.answer(...) los llamaba SIN pasar `zone`, así que toda la
    // lógica de "vence hoy"/"se pasó el arranque" (que depende de LocalDate, que
    // depende de la zona) se evaluaba con la zona del dispositivo, no con la que
    // el llamante (y el usuario) indicó. Un plazo a las 23:00 UTC que es "hoy" en
    // Honolulu (UTC-10) pero "mañana" en Tokio (UTC+9) se etiquetaba igual en
    // ambas → el asistente mentía sobre urgencia y un compromiso "de hoy" podía
    // caer al cajón neutro de la bandeja y olvidarse. Test diferencial: dos zonas
    // que producen LocalDate distinto para el mismo epoch deben dar respuestas
    // distintas; si se ignora `zone`, ambas coinciden (bug). Robusto frente a la
    // zona del contenedor: el bug produce respuestas idénticas, el fix produce
    // la diferencia esperada.
    @Test fun whatNow_usesPassedZoneForDueTodayRanking() {
        // now = 2026-08-18 12:00 UTC → Honolulu (UTC-10) = 18/08 02:00; Tokio (+9) = 18/08 21:00.
        val now = Instant.parse("2026-08-18T12:00:00Z").toEpochMilli()
        // dueAt = 2026-08-18 23:00 UTC → Honolulu = 18/08 13:00 (HOY, futuro); Tokio = 19/08 08:00 (MAÑANA).
        val dueAt = Instant.parse("2026-08-18T23:00:00Z").toEpochMilli()
        val task = TaskEntity(id = 1, title = "Llamada cliente", createdAt = now, dueAt = dueAt)

        val honolulu = AssistantEngine.answer(
            "¿qué hago ahora?",
            listOf(task), emptyList(), emptyList(),
            now, ZoneId.of("Pacific/Honolulu")
        )
        val tokio = AssistantEngine.answer(
            "¿qué hago ahora?",
            listOf(task), emptyList(), emptyList(),
            now, ZoneId.of("Asia/Tokyo")
        )

        // En Honolulu vence hoy → etiqueta "vence hoy".
        assertTrue("Honolulu (vence hoy) debe decir 'vence hoy': ${honolulu.text}",
            honolulu.text.contains("vence hoy"))
        // En Tokio vence mañana → NO es "vence hoy" (cae a "es lo siguiente de la bandeja").
        assertFalse("Tokio (vence mañana) NO debe decir 'vence hoy': ${tokio.text}",
            tokio.text.contains("vence hoy"))
        // Guardia anti-bug: las dos zonas deben diferir (si coinciden, se ignoró `zone`).
        assertNotEquals("las dos zonas deben dar respuestas distintas: ${honolulu.text} | ${tokio.text}",
            honolulu.text, tokio.text)
    }

    // --- c.707: "tengo algo pronto" (último cluster de la sonda assistant) — el
    // usuario pregunta por lo PRÓXIMO agendado sin alcance de fecha concreto;
    // caía al menú genérico pese a que la respuesta (la próxima cita/tarea) ya
    // existe en los datos. Vacío honesto, NUNCA menú; vacío + promesa vencida →
    // recuperación (paridad familia lie-by-omission c.357/c.416/c.680).
    @Test fun upcoming_prontoListsNextScheduledNotMenu() {
        val now = Instant.parse("2026-08-19T12:00:00Z").toEpochMilli()
        val zone = ZoneId.of("UTC")
        val answer = AssistantEngine.answer(
            "tengo algo pronto",
            listOf(
                TaskEntity(id = 1, title = "Dentista", dueAt = now + 2 * 3_600_000),
                TaskEntity(id = 2, title = "Informe", dueAt = now + 26 * 3_600_000),
                TaskEntity(id = 3, title = "Sin fecha")
            ),
            emptyList(), emptyList(), now, zone
        )
        assertEquals(listOf(1L, 2L), answer.relatedTaskIds)
        assertTrue("no cae al menú genérico: ${answer.text}", !answer.text.contains("Puedo organizar tu día"))
        assertTrue("nombra lo más próximo primero: ${answer.text}",
            answer.text.indexOf("Dentista") in 0 until answer.text.indexOf("Informe"))
        assertTrue("usa etiquetas relativas honestas: ${answer.text}",
            answer.text.contains("hoy") && answer.text.contains("mañana"))
        assertTrue("excluye la sin fecha: ${answer.text}", !answer.text.contains("Sin fecha"))
    }

    @Test fun upcoming_prontoExcludesOverdueAndUsesStartAt() {
        val now = Instant.parse("2026-08-19T12:00:00Z").toEpochMilli()
        val zone = ZoneId.of("UTC")
        val answer = AssistantEngine.answer(
            "tengo algo pronto",
            listOf(
                TaskEntity(id = 1, title = "Pago vencido", dueAt = now - 3_600_000),
                TaskEntity(id = 2, title = "Clase de yoga", startAt = now + 4 * 3_600_000),
                TaskEntity(id = 3, title = "Vuelo", dueAt = now + 50 * 3_600_000)
            ),
            emptyList(), emptyList(), now, zone
        )
        assertEquals(listOf(2L, 3L), answer.relatedTaskIds)
        assertTrue("no lista lo vencido como próximo: ${answer.text}", !answer.text.contains("Pago vencido"))
        assertTrue("incluye hueco agendado sin dueAt: ${answer.text}", answer.text.contains("Clase de yoga"))
    }

    @Test fun upcoming_prontoHoyKeepsAgenda() {
        // "tengo algo pronto hoy" tiene alcance de día explícito: sigue siendo
        // agenda de hoy (evaluada antes), no la rama de "próximo".
        val now = Instant.parse("2026-08-19T12:00:00Z").toEpochMilli()
        val zone = ZoneId.of("UTC")
        val answer = AssistantEngine.answer(
            "tengo algo pronto hoy",
            listOf(TaskEntity(id = 1, title = "Dentista", dueAt = now + 2 * 3_600_000)),
            emptyList(), emptyList(), now, zone
        )
        assertTrue("rutea a agenda de hoy: ${answer.text}", answer.text.startsWith("Hoy:"))
        assertTrue("no usa la rama de próximos: ${answer.text}", !answer.text.contains("Lo más próximo"))
    }

    @Test fun upcoming_emptyIsHonestNotGeneric() {
        val answer = AssistantEngine.answer(
            "tengo algo pronto",
            emptyList(), emptyList(), emptyList()
        )
        assertTrue("no cae al menú genérico: ${answer.text}", !answer.text.contains("Puedo organizar tu día"))
        assertTrue("vacío honesto: ${answer.text}", answer.text.contains("No tienes nada agendado próximamente."))
    }

    @Test fun upcoming_recoversOverdueCommitmentWhenEmpty() {
        val now = 1_000_000_000_000L
        val commitment = overdueCommitment(41, "envío el informe", now - 2 * 86_400_000L)
        val answer = AssistantEngine.answer(
            "tengo algo pronto",
            emptyList(), emptyList(), listOf(commitment), now
        )
        assertEquals(AssistantAction.OPEN_CONVERSATIONS, answer.action)
        assertTrue("nombra el compromiso vencido: ${answer.text}", answer.text.contains("envío el informe"))
    }

    // --- c.780: paridad buscador↔asistente para tareas marcadas ("tareas
    // marcadas"/"destacadas"/"las que marqué"). La búsqueda las recupera vía
    // SearchEngine.FLAGGED_TOKENS (task.flagged), pero el asistente caía al menú
    // genérico — mentía por omisión sobre la señal más explícita que el usuario
    // pone (marcó la tarea él mismo, a veces TODAS las de un proyecto). Ruta como
    // la familia de prioridad c.677: lista sólo las marcadas activas (raíz),
    // ordenadas por What Now, vacío honesto (NUNCA menú), recuperación de
    // compromisos vencidos ante el vacío (paridad c.357/c.416). Los infinitivos
    // ("marcar", "destacar" — acción por hacer) NO la detonan. ---

    @Test fun flagged_listsOnlyFlaggedTasks() {
        val flagged = TaskEntity(id = 1, title = "Revisar el contrato", flagged = true, priority = TaskPriority.LOW)
        val normal = TaskEntity(id = 2, title = "Comprar leche", priority = TaskPriority.HIGH)
        val anotherFlagged = TaskEntity(id = 3, title = "Pagar la tarjeta", flagged = true)
        val answer = AssistantEngine.answer(
            "tareas marcadas",
            listOf(flagged, normal, anotherFlagged),
            emptyList(), emptyList()
        )
        assertEquals(listOf(1L, 3L), answer.relatedTaskIds.sorted())
        assertTrue("habla de marcadas: ${answer.text}", answer.text.contains("marcada"))
        assertTrue("no lista la normal: ${answer.text}", !answer.text.contains("leche"))
        assertTrue("no cae al menú genérico: ${answer.text}", !answer.text.contains("Puedo organizar tu día"))
    }

    @Test fun flagged_acceptsSynonyms() {
        val flagged = TaskEntity(id = 1, title = "Revisar el contrato", flagged = true)
        // Paridad estricta con SearchEngine.FLAGGED_TOKENS (participios; el
        // infinitivo "marcar"/"destacar" queda fuera por palabra exacta).
        // Nota: "marqué" (pretérito 1.ª persona) sigue siendo gap del BUSCADOR
        // (BACKLOG c.780); aquí no se añade para que asistente y buscador
        // compartan exactamente el mismo vocabulario de marcado.
        for (q in listOf("tareas destacadas", "destacados", "tareas marcadas", "¿cuáles tengo marcadas?")) {
            val answer = AssistantEngine.answer(q, listOf(flagged), emptyList(), emptyList())
            assertEquals("«$q» recupera la marcada: ${answer.text}", listOf(1L), answer.relatedTaskIds)
        }
    }

    @Test fun flagged_emptyIsHonestNotMenu() {
        val answer = AssistantEngine.answer(
            "tareas marcadas",
            listOf(TaskEntity(id = 1, title = "Normal", priority = TaskPriority.HIGH)),
            emptyList(), emptyList()
        )
        assertTrue("no cae al menú genérico: ${answer.text}", !answer.text.contains("Puedo organizar tu día"))
        assertTrue("vacío honesto: ${answer.text}", answer.text.contains("No tienes tareas marcadas"))
    }

    @Test fun flagged_recoversOverdueCommitmentWhenEmpty() {
        val now = 1_000_000_000_000L
        val commitment = overdueCommitment(32, "envío el informe", now - 2 * 86_400_000L)
        val answer = AssistantEngine.answer(
            "tareas marcadas",
            emptyList(), emptyList(), listOf(commitment), now
        )
        assertEquals(AssistantAction.OPEN_CONVERSATIONS, answer.action)
        assertTrue("nombra el compromiso vencido: ${answer.text}", answer.text.contains("envío el informe"))
    }

    @Test fun flagged_doesNotHijackInfinitive() {
        // "marcar"/"destacar" (infinitivo, acción POR HACER) no es pedir las
        // marcadas (adjetivo /participio). La marca proyectual la hizo c.779 en
        // la familia complete-recap; aquí el guardia es por palabra exacta.
        val flagged = TaskEntity(id = 1, title = "Revisar el contrato", flagged = true)
        for (q in listOf("quiero marcar una tarea", "destacar las prioridades", "debería marcar esto")) {
            val answer = AssistantEngine.answer(q, listOf(flagged), emptyList(), emptyList())
            assertTrue("«$q» no lista la marcada: ${answer.text}", answer.relatedTaskIds.isEmpty())
        }
    }

    @Test fun flagged_mixedWithPrioritySignal_keepsPriorityRouting() {
        // "marcadas como urgentes": la señal de prioridad (alta/urgente) es más
        // específica que la de marcado; la rama de prioridad responde y filtra.
        val flaggedNormal = TaskEntity(id = 1, title = "Revisar el contrato", flagged = true, priority = TaskPriority.HIGH)
        val flaggedUrgent = TaskEntity(id = 2, title = "Entrega crítica", flagged = true, priority = TaskPriority.URGENT)
        val answer = AssistantEngine.answer(
            "tareas marcadas como importantes",
            listOf(flaggedNormal, flaggedUrgent),
            emptyList(), emptyList()
        )
        assertEquals(listOf(2L, 1L), answer.relatedTaskIds)
        assertTrue("usa la rama de prioridad: ${answer.text}", answer.text.contains("importante"))
    }

    // Recurrentes por adjetivos ("recurrente(s)"/"repetitiva(s)" por palabra,
    // como RECURRING_TOKENS de SearchEngine — paridad flagged de c.781): el
    // usuario las marcó explícitamente y el asistente las devolvía al menú.

    @Test fun recurringRecurrentes_listsOnlyRecurring() {
        val recurring = TaskEntity(id = 1, title = "Backup semanal", recurrence = RecurrenceFrequency.WEEKLY)
        val daily = TaskEntity(id = 4, title = "Gimnasio cada día", recurrence = RecurrenceFrequency.DAILY)
        val ordinary = TaskEntity(id = 2, title = "Factura única")
        val answer = AssistantEngine.answer(
            "¿qué tareas recurrentes tengo?",
            listOf(recurring, ordinary, daily),
            emptyList(), emptyList()
        )
        assertEquals(listOf(1L, 4L), answer.relatedTaskIds)
        assertTrue("lista only recurrents: ${answer.text}", !answer.text.contains("Factura única"))
    }

    @Test fun recurringRepetitivas_sameVocabulary() {
        for (q in listOf("tareas repetitivas", "las recurrentes", "¿tienes recurrentes?")) {
            val answer = AssistantEngine.answer(q, emptyList(), emptyList(), emptyList())
            assertTrue("[$q] respuesta vaciable no-menu: ${answer.text}", answer.text.contains("No tienes tareas recurrentes."))
        }
    }

    @Test fun recurringEmpty_isHonestNotMenu() {
        val answer = AssistantEngine.answer("¿qué tareas recurrentes tengo?", emptyList(), emptyList(), emptyList())
        assertTrue("no menu: ${answer.text}", answer.text.startsWith("No tienes tareas recurrentes."))
    }

    @Test fun recurring_doesNotHijackNounOrVerb() {
        // "repetición" (sustantivo) y "repetir" no son pedir las recurrentes:
        // evita que "la repetición de la clase" abra la rama.
        val ordinary = TaskEntity(id = 1, title = "la repetición de la clase")
        val answer = AssistantEngine.answer("preparar la repetición de la clase", listOf(ordinary), emptyList(), emptyList())
        assertTrue("noun does not list recurring: ${answer.text}", answer.relatedTaskIds.isEmpty())
    }

}
