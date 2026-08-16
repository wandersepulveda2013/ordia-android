package com.ordia.app.domain

import com.ordia.app.data.local.CaptureTarget

/**
 * Sugerencias contextuales de captura (sección 5).
 * Ordia propone acciones accionables; no ejecuta decisiones ambiguas
 * sin confirmación.
 */
data class ContextSuggestion(
    val target: CaptureTarget,
    val label: String,
    val confidence: Float,
    val why: String
)

object ContextSuggestionEngine {

    private val timePlace = Regex("""(?i)\b(\d{1,2}[:h]\d{0,2})\b.*\b([A-ZÁÉÍÓÚÑ][\wáéíóúñ\s]{2,})""")
    private val dueDate = Regex("""(?i)\b(antes\s+del\s+\d{1,2}|para\s+el\s+\d{1,2}|vence\s+\d{1,2})""")
    private val shopping = Regex("""(?i)\b(arroz|leche|avena|pan|café|az[uú]car|huevos|shampoo|jab[oó]n|detergente)\b""")
    private val eventWords = Regex("""(?i)\b(cita|reuni[oó]n|cine|concierto|vuelo|reserva)\b""")

    fun suggest(raw: String): List<ContextSuggestion> {
        val out = mutableListOf<ContextSuggestion>()
        if (raw.isBlank()) return out

        if (eventWords.containsMatchIn(raw) || timePlace.containsMatchIn(raw)) {
            out += ContextSuggestion(
                CaptureTarget.EVENT,
                "Crear evento",
                0.8f,
                "Detecté hora y/o lugar"
            )
        }
        if (dueDate.containsMatchIn(raw) || raw.contains("recuérdame", ignoreCase = true) ||
            raw.contains("recordatorio", ignoreCase = true)
        ) {
            out += ContextSuggestion(
                CaptureTarget.REMINDER,
                "Crear recordatorio",
                0.85f,
                "Detecté una fecha límite o petición de recordatorio"
            )
        }
        if (shopping.containsMatchIn(raw)) {
            out += ContextSuggestion(
                CaptureTarget.NOTE,
                "Crear lista",
                0.75f,
                "Detecté posibles elementos de compra"
            )
        }
        return out
    }
}
