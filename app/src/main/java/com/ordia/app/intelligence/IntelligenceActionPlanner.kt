package com.ordia.app.intelligence

import android.util.Log

/**
 * Planificador de acciones que traduce un esquema de inteligencia
 * en una acción ejecutable concreta.
 *
 * Separa la fase de "qué hacer" (Planner) de "cómo hacerlo" (Executor).
 * Esto permite que el planner funcione incluso sin permisos de ejecución.
 *
 * @property schema Esquema estructurado de la inteligencia
 * @property response Respuesta completa del proveedor
 */
data class IntelligenceActionPlannerResult(
    val actionType: ActionSuggested,
    val title: String,
    val description: String,
    val parameters: Map<String, String>,
    val requiresConfirmation: Boolean,
    val followUpQuestion: String?,
    val priority: ActionPriority
)

enum class ActionPriority(val value: Int) {
    LOW(0),
    NORMAL(1),
    HIGH(2),
    URGENT(3);

    companion object {
        fun fromCertainty(certainty: Certainty): ActionPriority = when (certainty) {
            Certainty.CIERTO -> HIGH
            Certainty.PROBABLE -> NORMAL
            Certainty.DUDOSO -> LOW
            Certainty.CONDICIONAL -> LOW
        }
    }
}

/**
 * Traduce una respuesta de inteligencia en un plan de acción concreto.
 */
object IntelligenceActionPlanner {

    private const val TAG = "IntelligenceActionPlanner"

    /**
     * Planifica una acción a partir de la respuesta del motor de inteligencia.
     *
     * @param response Respuesta del proveedor de inteligencia
     * @param originalText Texto original (solo para generar título, se descarta después)
     * @return Resultado del planeamiento, o null si no hay acción viable
     */
    fun plan(response: IntelligenceResponse, originalText: String): IntelligenceActionPlannerResult? {
        val schema = response.schema

        // No planear si está bloqueado por privacidad
        if (schema.privacyResult == PrivacyResult.BLOCKED) {
            Log.d(TAG, "Contenido bloqueado, no se planifica acción")
            return null
        }

        // No planear si no hay acción sugerida
        if (schema.actionSuggested == ActionSuggested.NONE) {
            Log.d(TAG, "Sin acción sugerida, no se planifica")
            return null
        }

        val title = generateTitle(schema, originalText)
        val description = generateDescription(schema, originalText)
        val requiresConfirmation = requiresUserConfirmation(schema, response)
        val priority = ActionPriority.fromCertainty(schema.certainty)

        return IntelligenceActionPlannerResult(
            actionType = schema.actionSuggested,
            title = title,
            description = description,
            parameters = schema.actionParameters,
            requiresConfirmation = requiresConfirmation,
            followUpQuestion = schema.followUpQuestion,
            priority = priority
        )
    }

    private fun generateTitle(schema: IntelligenceSchema, originalText: String): String {
        val prefix = when (schema.actionSuggested) {
            ActionSuggested.TASK -> if (schema.polarity == Polarity.NEGATIVO) "No hacer: " else ""
            ActionSuggested.SHOPPING -> "Comprar: "
            ActionSuggested.APPOINTMENT -> "Cita: "
            ActionSuggested.MEETING -> "Reunión: "
            ActionSuggested.REMINDER -> "Recordar: "
            ActionSuggested.CALL -> "Llamar: "
            ActionSuggested.PAYMENT -> "Pago: "
            ActionSuggested.STUDY -> "Estudio: "
            ActionSuggested.EXERCISE -> "Ejercicio: "
            ActionSuggested.DEADLINE -> "Vence: "
            ActionSuggested.HOUSEHOLD -> "Hogar: "
            ActionSuggested.NONE -> ""
        }

        val actorPrefix = when (schema.actor) {
            Actor.YO -> ""
            Actor.ALGUIEN -> "Alguien: "
            Actor.ALGUIEN_MAS -> when {
                schema.actionParameters.containsKey("person") -> "${schema.actionParameters["person"]}: "
                else -> "Alguien más: "
            }
            Actor.NOSOTROS -> "Nosotros: "
        }

        // Sanitizar título quitando palabras vacías
        val cleaned = originalText.trim()
            .replace(Regex("""\b(muy|más|tan|hay que|tengo que|voy a|vamos a|debo|para|tal vez|quizá|quizás|a lo mejor)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)

        return "$prefix$actorPrefix${cleaned}".trim()
    }

    private fun generateDescription(schema: IntelligenceSchema, originalText: String): String {
        val parts = mutableListOf<String>()

        when (schema.actor) {
            Actor.YO -> parts.add("Yo")
            Actor.ALGUIEN -> parts.add("Alguien")
            Actor.ALGUIEN_MAS -> parts.add("Alguien más")
            Actor.NOSOTROS -> parts.add("Nosotros")
        }

        if (schema.polarity == Polarity.NEGATIVO) {
            parts.add("NO")
        }

        parts.add("→ ${schema.actionSuggested.displayName}")

        return parts.joinToString(" ")
    }

    private fun requiresUserConfirmation(schema: IntelligenceSchema, response: IntelligenceResponse): Boolean {
        // Siempre requiere confirmación si:
        // - Certeza es dudosa o condicional
        if (schema.certainty in setOf(Certainty.DUDOSO, Certainty.CONDICIONAL)) return true
        // - Baja confianza
        if (response.confidenceScore < IntelligenceResponse.MIN_CONFIDENCE_FOR_ACTION) return true
        // - Polaridad negativa (el usuario está negando algo, confirmar antes de actuar)
        if (schema.polarity == Polarity.NEGATIVO) return true
        return false
    }
}
