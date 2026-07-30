package com.ordia.app.intelligence

import android.util.Log
import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine
import com.ordia.app.context.ContextIntentKind
import java.util.Locale

/**
 * Proveedor de análisis basado en reglas y expresiones regulares.
 *
 * Envuelve el motor [ContextIntentEngine] y mapea su salida al esquema
 * estructurado [IntelligenceSchema] que los modelos de lenguaje también usan.
 *
 * Este es el modo "básico" / fallback cuando el modelo local no está disponible.
 * NUNCA debe llamarse "inteligencia" — es un parser de reglas.
 *
 * @see IntelligenceProvider Interfaz que implementa
 * @see ContextIntentEngine Motor de reglas subyacente
 */
class BasicRuleProvider : IntelligenceProvider {

    override val displayName: String = "Modo básico (reglas)"
    override val providerId: ProviderSource = ProviderSource.BASIC_RULE
    override val isAvailable: Boolean get() = true

    override suspend fun analyze(request: IntelligenceRequest): IntelligenceResponse {
        val startTime = System.currentTimeMillis()

        // Sanitizar
        val safeText = IntelligenceSafetyGate.sanitize(request.originalText)

        // Crear ContextEvent desde la request
        val event = ContextEvent(
            source = request.source,
            rawText = safeText,
            sourcePackage = request.sourcePackage,
            timestampMs = request.timestampMs
        )

        // Usar el motor de reglas existente
        val intent = ContextIntentEngine.analyze(event)

        val schema = if (intent == null) {
            // Sin intención clara
            IntelligenceSchema(
                actor = Actor.YO,
                polarity = Polarity.POSITIVO,
                certainty = Certainty.CIERTO,
                temporalDirection = detectTemporalDirection(safeText),
                actionSuggested = ActionSuggested.NONE,
                followUpQuestion = null,
                privacyResult = IntelligenceSafetyGate.evaluate(request.originalText)
            )
        } else {
            // Mapear ContextIntent a IntelligenceSchema
            mapIntentToSchema(safeText, intent)
        }

        val processingTime = System.currentTimeMillis() - startTime

        return IntelligenceResponse(
            schema = schema,
            rawModelOutput = null,
            confidenceScore = intent?.confidence ?: 0f,
            providerSource = ProviderSource.BASIC_RULE,
            processingTimeMs = processingTime
        )
    }

    /**
     * Mapea un ContextIntent (del motor de reglas) al esquema unificado IntelligenceSchema.
     */
    private fun mapIntentToSchema(text: String, intent: com.ordia.app.context.ContextIntent): IntelligenceSchema {
        val lower = text.lowercase(Locale.ROOT)
        val sourceActor = detectActor(lower)
        val sourcePolarity = detectPolarity(lower)
        val sourceCertainty = detectCertainty(lower)
        val sourceTemporal = detectTemporalDirection(lower)
        val sourceAction = mapKindToAction(intent.kind)
        val sourceParams = buildActionParameters(lower, sourceAction)
        val sourceFollowUp = generateFollowUp(sourceCertainty, sourceAction, text)

        return IntelligenceSchema(
            actor = sourceActor,
            polarity = sourcePolarity,
            certainty = sourceCertainty,
            temporalDirection = sourceTemporal,
            actionSuggested = sourceAction,
            actionParameters = sourceParams,
            followUpQuestion = sourceFollowUp,
            privacyResult = PrivacyResult.SAFE
        )
    }

    private fun detectActor(lower: String): Actor {
        return when {
            Regex("""\b(yo|voy|iré|tengo|pienso|quiero|haré|compraré|llamaré|estudiaré|debo)\b""").containsMatchIn(lower) -> Actor.YO
            Regex("""\b(tú|vas|irás|tienes|quieres|harás)\b""").containsMatchIn(lower) -> Actor.ALGUIEN
            Regex("""\b(él|ella|juan|maría|pedro|ana|luis|carla|ellos|ellas|va|irá|tiene|quiere)\b""").containsMatchIn(lower) -> Actor.ALGUIEN_MAS
            Regex("""\b(nosotros|vamos|iremos|tenemos|queremos|nos)\b""").containsMatchIn(lower) -> Actor.NOSOTROS
            Regex("""\b(vamos|iremos|nos vemos|quedamos)\b""").containsMatchIn(lower) -> Actor.NOSOTROS
            else -> Actor.YO
        }
    }

    private fun detectPolarity(lower: String): Polarity {
        return when {
            Regex("""\b(no |nunca|jamás|tampoco|ni |sin |ningún|nadie)\b""").containsMatchIn(lower) -> Polarity.NEGATIVO
            else -> Polarity.POSITIVO
        }
    }

    private fun detectCertainty(lower: String): Certainty {
        return when {
            Regex("""\b(tal vez|quizá|quizás|a lo mejor|puede que|igual|capaz|posiblemente|probablemente)\b""").containsMatchIn(lower) -> Certainty.DUDOSO
            Regex("""\b(cuando|si |en cuanto|apenas|apenas |después de que|en caso de que)\b""").containsMatchIn(lower) -> Certainty.CONDICIONAL
            Regex("""\b(seguro|seguramente|claramente|definitivamente|sin duda)\b""").containsMatchIn(lower) -> Certainty.CIERTO
            else -> {
                // Verbos en condicional también indican condicional
                if (Regex("""\b(pasaría|iría|haría|compraría|llamaría|podría|debería)\b""").containsMatchIn(lower)) {
                    Certainty.CONDICIONAL
                } else Certainty.PROBABLE
            }
        }
    }

    internal fun detectTemporalDirection(lower: String): TemporalDirection {
        // Pasado
        if (Regex("""\b(ayer|anteayer|fuimos|fui|estuve|estuvimos|hice|hicimos|compré|compramos| llamé|llamamos|fue|eran|era|íbamos|fuiste)\b""").containsMatchIn(lower)) {
            return TemporalDirection.PASADO
        }
        if (Regex("""\b(la semana pasada|el mes pasado|el año pasado|hace \d+ (días|horas|minutos|meses|años))\b""").containsMatchIn(lower)) {
            return TemporalDirection.PASADO
        }

        // Condicional futuro (cuando + subjuntivo / condicional)
        if (Regex("""\b(cuando|en cuanto|apenas|después de que|tan pronto como)\b.*\b( salga|llegue|termine|vuelva|pueda|tenga|haga|salga|vaya|pase|acabe)\b""").containsMatchIn(lower)) {
            return TemporalDirection.CONDICIONAL_FUTURO
        }
        if (Regex("""\b(pasaría|iría|haría|compraría|llamaría|podría)\b""").containsMatchIn(lower)) {
            return TemporalDirection.CONDICIONAL_FUTURO
        }

        // Futuro cercano (esta semana, hoy)
        if (Regex("""\b(hoy|esta (noche|tarde|mañana|semana))\b""").containsMatchIn(lower)) {
            return TemporalDirection.FUTURO_CERCANO
        }

        // Futuro
        if (Regex("""\b(mañana|pasado mañana|la próxima semana|el próximo mes|el próximo año)\b""").containsMatchIn(lower)) {
            return TemporalDirection.FUTURO
        }
        if (Regex("""\b(iré|voy a |vamos a |iremos a |haré|compraré|llamaré|estudiaré|entregaré|pagaré|sacaré|limpiaré|cocinaré)\b""").containsMatchIn(lower)) {
            return TemporalDirection.FUTURO
        }
        if (Regex("""\b(el (lunes|martes|miércoles|jueves|viernes|sábado|domingo))\b""").containsMatchIn(lower)) {
            return TemporalDirection.FUTURO
        }

        return TemporalDirection.PRESENTE
    }

    private fun mapKindToAction(kind: ContextIntentKind): ActionSuggested = when (kind) {
        ContextIntentKind.TASK -> ActionSuggested.TASK
        ContextIntentKind.SHOPPING -> ActionSuggested.SHOPPING
        ContextIntentKind.APPOINTMENT -> ActionSuggested.APPOINTMENT
        ContextIntentKind.MEETING -> ActionSuggested.MEETING
        ContextIntentKind.REMINDER -> ActionSuggested.REMINDER
        ContextIntentKind.CALL -> ActionSuggested.CALL
        ContextIntentKind.PAYMENT -> ActionSuggested.PAYMENT
        ContextIntentKind.STUDY -> ActionSuggested.STUDY
        ContextIntentKind.EXERCISE -> ActionSuggested.EXERCISE
        ContextIntentKind.DEADLINE -> ActionSuggested.DEADLINE
        ContextIntentKind.HOUSEHOLD -> ActionSuggested.HOUSEHOLD
        else -> ActionSuggested.NONE
    }

    private fun buildActionParameters(lower: String, action: ActionSuggested): Map<String, String> {
        val params = mutableMapOf<String, String>()

        // Extraer lugar
        val placePatterns = listOf(
            Regex("""(al |a la |a los |del |de la )?(supermercado|farmacia|tienda|banco|hospital|clínica|gimnasio|oficina|escuela|universidad|biblioteca|parque|cine|restaurante|cafetería)""", RegexOption.IGNORE_CASE),
            Regex("""(a |en |para |del |de la |al )?(casa|trabajo|oficina|escuela|consultorio)""", RegexOption.IGNORE_CASE)
        )
        for (pattern in placePatterns) {
            val match = pattern.find(lower)
            if (match != null) {
                val place = match.groups[2]?.value ?: match.groups[1]?.value ?: continue
                params["place"] = place.lowercase()
                break
            }
        }

        // Extraer persona
        val personMatch = Regex("""\b(con |para |de |a )?([A-Z][a-záéíóú]+)\b""").find(lower)
        if (personMatch != null) {
            val name = personMatch.groupValues[2]
            if (name.length > 2 && name !in setOf("Hoy", "Mañana", "Día", "Las", "Los", "Una", "Un", "El", "La")) {
                params["person"] = name
            }
        }

        // Extraer ítem para compras
        if (action == ActionSuggested.SHOPPING) {
            val items = listOf("leche", "pan", "huevos", "detergente", "verduras", "fruta", "carne", "pollo", "pescado", "arroz", "frijoles", "azúcar", "sal", "aceite")
            items.forEach { item ->
                if (lower.contains(item)) {
                    params["item"] = item
                    return@forEach
                }
            }
        }

        return params
    }

    private fun generateFollowUp(certainty: Certainty, action: ActionSuggested, text: String): String? {
        if (certainty == Certainty.DUDOSO && action != ActionSuggested.NONE) {
            return "¿Confirmas que quieres $displayName(action)?"
        }
        if (certainty == Certainty.CONDICIONAL && action != ActionSuggested.NONE) {
            return "¿Quieres que te lo recuerde cuando se cumpla la condición?"
        }
        if (action == ActionSuggested.NONE) {
            val lower = text.lowercase()
            if (lower.contains("supermercado") || lower.contains("tienda") || lower.contains("comprar")) {
                return "¿Quieres que te recuerde ir al supermercado?"
            }
            if (lower.contains("llamar") || lower.contains("hablar")) {
                return "¿A quién quieres llamar?"
            }
        }
        return null
    }

    private fun displayName(action: ActionSuggested): String = when (action) {
        ActionSuggested.TASK -> "crear una tarea"
        ActionSuggested.SHOPPING -> "ir de compras"
        ActionSuggested.APPOINTMENT -> "agendar una cita"
        ActionSuggested.MEETING -> "programar una reunión"
        ActionSuggested.REMINDER -> "crear un recordatorio"
        ActionSuggested.CALL -> "hacer una llamada"
        ActionSuggested.PAYMENT -> "registrar un pago"
        ActionSuggested.STUDY -> "agendar estudio"
        ActionSuggested.EXERCISE -> "agendar ejercicio"
        ActionSuggested.DEADLINE -> "registrar un vencimiento"
        ActionSuggested.HOUSEHOLD -> "registrar una tarea del hogar"
        ActionSuggested.NONE -> "nada"
    }
}
