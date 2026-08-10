package com.ordia.app.intelligence

/**
 * Esquema de salida estructurada que debe cumplir cualquier proveedor de
 * inteligencia. En esta versión lo produce el motor determinista local;
 * no existe un modelo generativo conectado.
 *
 * @property actor Quién realiza la acción: yo, alguien, alguienMas, nosotros
 * @property polarity positivo (afirmativo) o negativo (negación explícita)
 * @property certainty grado de certeza: cierto, probable, dudoso, condicional
 * @property temporalDirection dirección temporal: pasado, presente, futuro,
 *   futuroCercano (esta semana), condicionalFuturo (cuando + subjuntivo)
 * @property actionSuggested tipo de acción sugerida o "none" si no aplica
 * @property actionParameters mapa de parámetros específicos de la acción
 * @property followUpQuestion pregunta de seguimiento si hay ambigüedad
 * @property privacyResult resultado del filtro de privacidad
 */
data class IntelligenceSchema(
    val actor: Actor = Actor.YO,
    val polarity: Polarity = Polarity.POSITIVO,
    val certainty: Certainty = Certainty.CIERTO,
    val temporalDirection: TemporalDirection = TemporalDirection.PRESENTE,
    val actionSuggested: ActionSuggested = ActionSuggested.NONE,
    val actionParameters: Map<String, String> = emptyMap(),
    val followUpQuestion: String? = null,
    val privacyResult: PrivacyResult = PrivacyResult.SAFE
) {
    /** Serializa a JSON válido para el prompt del modelo */
    fun toJson(): String = buildString {
        append("{")
        append("\"actor\":\"${actor.value}\",")
        append("\"polarity\":\"${polarity.value}\",")
        append("\"certainty\":\"${certainty.value}\",")
        append("\"temporalDirection\":\"${temporalDirection.value}\",")
        append("\"actionSuggested\":\"${actionSuggested.value}\",")
        append("\"actionParameters\":{")
        actionParameters.entries.forEachIndexed { i, (k, v) ->
            if (i > 0) append(",")
            append("\"$k\":\"${v.replace("\"", "\\\"")}\"")
        }
        append("},")
        append("\"followUpQuestion\":${followUpQuestion?.let { "\"$it\"" } ?: "null"},")
        append("\"privacyResult\":\"${privacyResult.value}\"")
        append("}")
    }

    companion object {
        /** Parsea un string JSON en IntelligenceSchema, o null si es inválido */
        fun fromJson(json: String): IntelligenceSchema? = runCatching {
            val cleaned = json.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```")
                .trim()
            val map = cleaned
                .removeSurrounding("{", "}")
                .split(",")
                .associate {
                    val parts = it.split(":", limit = 2)
                    if (parts.size != 2) "" to ""
                    else parts[0].trim().removeSurrounding("\"") to
                        parts[1].trim().removeSurrounding("\"")
                }
            IntelligenceSchema(
                actor = Actor.fromValue(map["actor"] ?: "yo"),
                polarity = Polarity.fromValue(map["polarity"] ?: "positivo"),
                certainty = Certainty.fromValue(map["certainty"] ?: "cierto"),
                temporalDirection = TemporalDirection.fromValue(map["temporalDirection"] ?: "presente"),
                actionSuggested = ActionSuggested.fromValue(map["actionSuggested"] ?: "none"),
                actionParameters = parseParameters(map["actionParameters"] ?: "{}"),
                followUpQuestion = if (map["followUpQuestion"] == "null") null else map["followUpQuestion"],
                privacyResult = PrivacyResult.fromValue(map["privacyResult"] ?: "segura")
            )
        }.getOrNull()

        private fun parseParameters(paramsStr: String): Map<String, String> {
            val cleaned = paramsStr.trim().removeSurrounding("{", "}")
            if (cleaned.isBlank()) return emptyMap()
            return cleaned.split(",").mapNotNull { pair ->
                val parts = pair.split(":", limit = 2)
                if (parts.size != 2) null
                else parts[0].trim().removeSurrounding("\"") to
                    parts[1].trim().removeSurrounding("\"")
            }.toMap()
        }
    }
}

enum class Actor(val value: String, val displayName: String) {
    YO("yo", "Yo"),
    ALGUIEN("alguien", "Alguien"),
    ALGUIEN_MAS("alguienMas", "Alguien más"),
    NOSOTROS("nosotros", "Nosotros");

    companion object {
        fun fromValue(v: String): Actor = entries.firstOrNull { it.value == v } ?: YO
    }
}

enum class Polarity(val value: String, val displayName: String) {
    POSITIVO("positivo", "Afirmativo"),
    NEGATIVO("negativo", "Negación");

    companion object {
        fun fromValue(v: String): Polarity = entries.firstOrNull { it.value == v } ?: POSITIVO
    }
}

enum class Certainty(val value: String, val displayName: String) {
    CIERTO("cierto", "Cierto"),
    PROBABLE("probable", "Probable"),
    DUDOSO("dudoso", "Dudoso"),
    CONDICIONAL("condicional", "Condicional");

    companion object {
        fun fromValue(v: String): Certainty = entries.firstOrNull { it.value == v } ?: CIERTO
    }
}

enum class TemporalDirection(val value: String, val displayName: String) {
    PASADO("pasado", "Pasado"),
    PRESENTE("presente", "Presente"),
    FUTURO("futuro", "Futuro"),
    FUTURO_CERCANO("futuroCercano", "Futuro cercano"),
    CONDICIONAL_FUTURO("condicionalFuturo", "Condicional futuro");

    companion object {
        fun fromValue(v: String): TemporalDirection = entries.firstOrNull { it.value == v } ?: PRESENTE
    }
}

enum class ActionSuggested(val value: String, val displayName: String) {
    TASK("task", "Tarea"),
    SHOPPING("shopping", "Compra"),
    APPOINTMENT("appointment", "Cita"),
    MEETING("meeting", "Reunión"),
    REMINDER("reminder", "Recordatorio"),
    CALL("call", "Llamada"),
    PAYMENT("payment", "Pago"),
    STUDY("study", "Estudio"),
    EXERCISE("exercise", "Ejercicio"),
    DEADLINE("deadline", "Vencimiento"),
    HOUSEHOLD("household", "Hogar"),
    NONE("none", "Ninguna");

    companion object {
        fun fromValue(v: String): ActionSuggested = entries.firstOrNull { it.value == v } ?: NONE
    }
}

enum class PrivacyResult(val value: String, val displayName: String) {
    SAFE("segura", "Segura"),
    BLOCKED("bloqueada", "Bloqueada");

    companion object {
        fun fromValue(v: String): PrivacyResult = entries.firstOrNull { it.value == v } ?: SAFE
    }
}
