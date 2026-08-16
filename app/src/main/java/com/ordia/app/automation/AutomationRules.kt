package com.ordia.app.automation

import com.ordia.app.data.local.AutomationAction
import com.ordia.app.data.local.AutomationCondition
import com.ordia.app.data.local.AutomationRuleEntity
import com.ordia.app.data.local.AutomationTrigger
import java.security.MessageDigest
import java.text.Normalizer

data class AutomationTemplate(
    val key: String,
    val name: String,
    val instruction: String,
    val trigger: AutomationTrigger,
    val condition: AutomationCondition,
    val action: AutomationAction,
    val explanation: String,
    val frequencyMinutes: Int,
    val maxRunsPerDay: Int
) {
    fun toEntity(now: Long = System.currentTimeMillis()) = AutomationRuleEntity(
        name = name,
        instruction = instruction,
        trigger = trigger,
        condition = condition,
        action = action,
        explanation = explanation,
        frequencyMinutes = frequencyMinutes.coerceIn(15, 10_080),
        maxRunsPerDay = maxRunsPerDay.coerceIn(1, 20),
        definitionHash = AutomationRuleCatalog.definitionHash(trigger, condition, action),
        createdAt = now,
        updatedAt = now
    )
}

sealed interface AutomationParseResult {
    data class Supported(val template: AutomationTemplate) : AutomationParseResult
    data class Unsupported(val reason: String) : AutomationParseResult
}

object AutomationRuleCatalog {
    val templates = listOf(
        AutomationTemplate(
            "morning_plan", "Preparar mi día", "Cada mañana prepara mi día",
            AutomationTrigger.DAILY_MORNING, AutomationCondition.HAS_INBOX_TASKS,
            AutomationAction.PLAN_DAY,
            "Ordena tareas pendientes en bloques realistas. Todos los cambios se pueden deshacer.",
            720, 1
        ),
        AutomationTemplate(
            "overdue_reset", "Reprogramar vencidas", "Si una tarea vence, muévela al próximo espacio disponible",
            AutomationTrigger.APP_OPEN, AutomationCondition.HAS_OVERDUE_TASKS,
            AutomationAction.RESCHEDULE_OVERDUE,
            "Reparte hasta ocho tareas vencidas entre los próximos días sin borrarlas.",
            360, 1
        ),
        AutomationTemplate(
            "quick_batch", "Agrupar tareas rápidas", "Agrupa las tareas de menos de 10 minutos",
            AutomationTrigger.MANUAL, AutomationCondition.HAS_QUICK_TASKS,
            AutomationAction.BATCH_QUICK_TASKS,
            "Coloca juntas hasta ocho tareas breves en el siguiente bloque disponible.",
            60, 3
        ),
        AutomationTemplate(
            "message_review", "Revisar compromisos", "Cada noche recuérdame responder mensajes pendientes",
            AutomationTrigger.DAILY_EVENING, AutomationCondition.HAS_PENDING_COMMITMENTS,
            AutomationAction.REVIEW_COMMITMENTS,
            "Crea una sola tarea reversible para revisar compromisos pendientes de conversaciones.",
            720, 1
        )
    )

    fun byKey(key: String): AutomationTemplate? = templates.firstOrNull { it.key == key }

    fun parse(instruction: String): AutomationParseResult {
        val clean = instruction.trim().replace(Regex("\\s+"), " ").take(500)
        if (clean.length < 8) return AutomationParseResult.Unsupported("Escribe una regla un poco más específica.")
        val normalized = Normalizer.normalize(clean.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        val template = when {
            ("cada manana" in normalized || "prepara mi dia" in normalized || "organiza mi dia" in normalized) -> templates[0]
            ("vence" in normalized || "vencid" in normalized) &&
                ("mueve" in normalized || "reprogram" in normalized || "proximo espacio" in normalized) -> templates[1]
            ("10 minutos" in normalized || "tareas rapidas" in normalized || "tareas breves" in normalized) -> templates[2]
            ("mensaje" in normalized || "compromiso" in normalized) &&
                ("responder" in normalized || "revis" in normalized || "recuerda" in normalized) -> templates[3]
            else -> null
        } ?: return AutomationParseResult.Unsupported(
            "Todavía no puedo ejecutar esa regla con seguridad. Usa una plantilla compatible o describe planificación, vencidas, tareas rápidas o compromisos."
        )
        return AutomationParseResult.Supported(template.copy(instruction = clean, name = clean.take(70)))
    }

    fun definitionHash(
        trigger: AutomationTrigger,
        condition: AutomationCondition,
        action: AutomationAction
    ): String = MessageDigest.getInstance("SHA-256")
        .digest("${trigger.name}|${condition.name}|${action.name}".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

enum class AutomationBlockReason { DISABLED, FREQUENCY_LIMIT, DAILY_LIMIT, LOOP_GUARD }

data class AutomationGuardDecision(val allowed: Boolean, val reason: AutomationBlockReason? = null)

object AutomationExecutionGuard {
    fun evaluate(
        rule: AutomationRuleEntity,
        now: Long,
        runsToday: Int,
        chainDepth: Int,
        manual: Boolean = false,
        test: Boolean = false
    ): AutomationGuardDecision {
        fun block(reason: AutomationBlockReason) = AutomationGuardDecision(false, reason)
        if (chainDepth > 1) return block(AutomationBlockReason.LOOP_GUARD)
        if (test) return AutomationGuardDecision(true)
        if (!rule.enabled && !manual) return block(AutomationBlockReason.DISABLED)
        if (runsToday >= rule.maxRunsPerDay.coerceIn(1, 20)) return block(AutomationBlockReason.DAILY_LIMIT)
        val elapsed = rule.lastRunAt?.let(now::minus) ?: Long.MAX_VALUE
        if (elapsed < rule.frequencyMinutes.coerceIn(15, 10_080) * 60_000L) {
            return block(AutomationBlockReason.FREQUENCY_LIMIT)
        }
        return AutomationGuardDecision(true)
    }
}
