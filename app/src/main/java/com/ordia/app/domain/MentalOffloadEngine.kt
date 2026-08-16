package com.ordia.app.domain

import com.ordia.app.data.local.TaskPriority
import java.time.Instant

/**
 * Tipo de elemento detectado durante una descarga mental.
 */
enum class OffloadItemKind(val displayKey: String) {
    TASK("offload_task"),
    PURCHASE("offload_purchase"),
    FOLLOWUP("offload_followup"),
    EVENT("offload_event"),
    NOTE("offload_note")
}

/**
 * Un elemento accionable extraído de un texto de descarga mental.
 */
data class OffloadItem(
    val kind: OffloadItemKind,
    val title: String,
    val dueAt: Long? = null,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val context: String = "",
    val confidence: Float = 1f
)

/**
 * Resultado del análisis de descarga mental.
 */
data class OffloadResult(
    val items: List<OffloadItem>,
    val rawText: String,
    val sharedContext: String
) {
    val count: Int get() = items.size
    val isEmpty: Boolean get() = items.isEmpty()
}

/**
 * Descarga mental 2026: separa un párrafo con varias intenciones en elementos
 * accionables distintos. 100 % local y determinista.
 *
 * Ejemplo:
 *   "mañana tengo que llamar al banco, comprar shampoo y preguntarle a
 *    Carlos por el documento antes del viernes"
 *
 * Produce:
 *   - Tarea: Llamar al banco (mañana)
 *   - Compra: Shampoo
 *   - Seguimiento: Preguntar a Carlos por documento (antes del viernes)
 */
object MentalOffloadEngine {

    private val conjunctionSplit = Regex("""(?i)\b(?:y|,\s*y|además|también|luego|después)\b""")
    private val commaSplit = Regex("""\s*,\s*""")
    private val purchaseSignal = Regex("""(?i)\b(comprar|compra|conseguir|traer|llevar)\s+(?:el\s+|la\s+|los\s+|las\s+|unos?\s+|unas?\s+)?""")
    private val followupVerb = Regex("""(?i)\b(preguntar(?:le)?|decirle|avisarle|recordarle|consultar(?:le)?|pedirle|llamar(?:le)?|escribirle|mandarle)\b""")
    private val properName = Regex("""\b([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)?)""")
    private val taskSignal = Regex("""(?i)\b(tengo\s+que|debo|hay\s+que|necesito|tengo\s+que\s+llamar|terminar|entregar|responder|enviar|pagar|preparar)\b""")
    private val beforeSignal = Regex("""(?i)\bantes\s+del?\s+(.+?)$""")
    private val byDeadline = Regex("""(?i)\b(?:para|para\s+el|antes\s+del?)\s+(.+?)$""")

    /**
     * Analiza un texto de descarga mental y devuelve elementos accionables.
     */
    fun parse(text: String, now: Long = System.currentTimeMillis()): OffloadResult {
        val cleaned = text.trim().replace(Regex("""\s+"""), " ")
        if (cleaned.isBlank()) return OffloadResult(emptyList(), text, "")

        val sharedContext = extractSharedContext(cleaned, now)
        val segments = splitIntoSegments(cleaned)
        val items = segments.mapNotNull { segment -> interpretSegment(segment, sharedContext, now) }
            .filter { it.title.isNotBlank() }

        return OffloadResult(items.distinctBy { it.title.lowercase() }, text, sharedContext)
    }

    private fun extractSharedContext(text: String, now: Long): String {
        // Busca un prefijo temporal compartido como "mañana", "hoy", "esta semana"
        val lower = text.lowercase()
        return when {
            lower.startsWith("mañana") || lower.contains(" mañana ") -> "mañana"
            lower.startsWith("hoy") || lower.contains(" hoy ") -> "hoy"
            lower.contains("esta semana") || lower.contains("esta semana ") -> "esta semana"
            lower.contains("próxima semana") || lower.contains("proxima semana") -> "próxima semana"
            else -> ""
        }
    }

    /**
     * Divide el texto en segmentos. Maneja verbos de compra distribuidos:
     * "comprar arroz, leche y avena" produce 3 compras, no solo la primera.
     */
    private fun splitIntoSegments(text: String): List<String> {
        // Detectar listas de compra: "comprar X, Y y Z" → distribuir el verbo
        val purchaseListMatch = Regex("""(?i)^(comprar|compra|conseguir|traer|llevar)\s+(.+)""").find(text.trim())
        if (purchaseListMatch != null) {
            val verb = purchaseListMatch.groupValues[1]
            val rest = purchaseListMatch.groupValues[2]
            val parts = rest.split(commaSplit).flatMap { it.split(conjunctionSplit) }
                .map { it.trim().trimEnd(',', '.', ';').trim() }
                .filter { it.length > 2 }
            if (parts.size >= 2) {
                return parts.map { "$verb $it" }
            }
        }

        // División normal por conjunciones y comas
        val byConjunction = text.split(conjunctionSplit).map { it.trim() }.filter(String::isNotBlank)
        return byConjunction.flatMap { segment ->
            segment.split(commaSplit).map { it.trim() }.filter(String::isNotBlank)
        }.filter { it.length > 2 }
    }

    private fun interpretSegment(segment: String, sharedContext: String, now: Long): OffloadItem? {
        val clean = segment.trim().trimEnd(',', '.', ';').trim()
        if (clean.length < 3) return null

        val dueAt = resolveDueDate(clean, sharedContext, now)

        // ¿Es una compra? "comprar shampoo" → Compra: Shampoo
        purchaseSignal.find(clean)?.let { match ->
            val item = clean.substring(match.range.last + 1).trim().trimEnd(',', '.')
            if (item.isNotBlank() && item.length <= 100) {
                return OffloadItem(
                    kind = OffloadItemKind.PURCHASE,
                    title = item.replaceFirstChar { it.uppercase() },
                    dueAt = dueAt,
                    context = sharedContext
                )
            }
        }

        // ¿Es un seguimiento a una persona? "preguntarle a Carlos por el documento"
        // Requiere un nombre propio real (mayúscula inicial) para evitar falsos
        // positivos como "llamar al banco".
        followupVerb.find(clean)?.let { verbMatch ->
            val afterVerb = clean.substring(verbMatch.range.last + 1)
            val nameMatch = properName.find(afterVerb)
            if (nameMatch != null) {
                val person = nameMatch.groupValues[1]
                val verb = verbMatch.value.lowercase().replaceFirstChar { it.uppercase() }
                val rest = afterVerb.substring(nameMatch.range.last + 1)
                    .replaceFirst(Regex("""^(?:por\s+(?:el|la|los|las)?\s*)"""), "")
                    .trimEnd(',', '.')
                val title = if (rest.isNotBlank()) "$verb a $person por $rest" else "$verb a $person"
                return OffloadItem(
                    kind = OffloadItemKind.FOLLOWUP,
                    title = title,
                    dueAt = dueAt,
                    context = sharedContext
                )
            }
        }

        // ¿Es una tarea explícita? "tengo que llamar al banco"
        if (taskSignal.containsMatchIn(clean)) {
            val title = clean
                .replaceFirst(Regex("""(?i)^(?:tengo\s+que\s+|debo\s+|hay\s+que\s+|necesito\s+)"""), "")
                .replaceFirstChar { if (it.isLowerCase()) it.uppercase() else it.toString() }
                .trimEnd(',', '.')
            return OffloadItem(
                kind = OffloadItemKind.TASK,
                title = title.ifBlank { clean.replaceFirstChar { it.uppercase() } },
                dueAt = dueAt,
                context = sharedContext
            )
        }

        // ¿Tiene una fecha/recordatorio? Tratar como tarea
        val parsed = NaturalTaskParser.parse(clean)
        if (parsed.dueAt != null || parsed.confidence >= 0.5f) {
            return OffloadItem(
                kind = OffloadItemKind.TASK,
                title = parsed.title.ifBlank { clean.replaceFirstChar { it.uppercase() } }.trimEnd(',', '.'),
                dueAt = parsed.dueAt ?: dueAt,
                priority = parsed.priority,
                context = sharedContext,
                confidence = parsed.confidence
            )
        }

        // De lo contrario, guardarlo como nota
        return OffloadItem(
            kind = OffloadItemKind.NOTE,
            title = clean.replaceFirstChar { it.uppercase() },
            context = sharedContext,
            confidence = 0.4f
        )
    }

    private fun resolveDueDate(segment: String, sharedContext: String, now: Long): Long? {
        val parsed = NaturalTaskParser.parse(segment)
        if (parsed.dueAt != null) return parsed.dueAt

        if (sharedContext.isBlank()) return null
        val today = java.time.LocalDate.now()
        val zone = java.time.ZoneId.systemDefault()
        return when (sharedContext) {
            "hoy" -> today.atStartOfDay(zone).toInstant().toEpochMilli()
            "mañana" -> today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            "esta semana" -> today.with(java.time.DayOfWeek.FRIDAY).atStartOfDay(zone).toInstant().toEpochMilli()
            "próxima semana", "proxima semana" -> today.plusWeeks(1).with(java.time.DayOfWeek.FRIDAY).atStartOfDay(zone).toInstant().toEpochMilli()
            else -> null
        }
    }
}
