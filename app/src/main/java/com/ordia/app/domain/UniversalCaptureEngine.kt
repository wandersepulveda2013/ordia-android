package com.ordia.app.domain

import com.ordia.app.data.local.CaptureTarget
import java.security.MessageDigest
import java.util.Locale

data class CaptureInterpretation(
    val target: CaptureTarget,
    val title: String,
    val body: String,
    val parsedTask: ParsedTaskInput?,
    val confidence: Float,
    val checklist: Boolean,
    val explanation: String
)

/**
 * Intérprete local y determinista de la captura universal. No descarta datos:
 * solo propone un destino; el repositorio conserva el texto antes de ejecutar
 * la propuesta. No usa red ni necesita una clave de IA.
 */
object UniversalCaptureEngine {
    private val noteCommand = Regex("""(?i)^\s*(guardar\s+esto\s+como\s+nota|nota|idea)\s*[:\-]?\s*""")
    private val taskCommand = Regex("""(?i)^\s*(crear\s+)?(una\s+)?tarea\s*[:\-]?\s*""")
    private val reminderSignal = Regex("""(?i)\b(recu[eé]rdame|recordarme|recordatorio|av[ií]same|hazme\s+acordar|no\s+dejes\s+que\s+olvide)\b""")
    private val taskSignal = Regex("""(?i)\b(tengo\s+que|debo|hay\s+que|llamar|enviar|comprar|pagar|terminar|entregar|responder|reuni[oó]n)\b""")
    private val eventSignal = Regex("""(?i)\b(cita|reuni[oó]n|junta|dentista|m[eé]dico|doctor|almuerzo|cena|desayuno|viaje|fiesta|concierto|entrevista|boda|aniversario|clase|curso|cumplea[nñ]os)\b""")
    private val urlOnly = Regex("""(?i)^https?://\S+$""")
    private val listPrefix = Regex("""^\s*(?:[-*•]|\d+[.)]|\[\s?])\s+""")

    fun interpret(
        raw: String,
        requested: CaptureTarget = CaptureTarget.AUTO,
        hasAttachment: Boolean = false
    ): CaptureInterpretation {
        val clean = raw.trim().take(MAX_CONTENT_CHARS)
        val inferred = if (requested == CaptureTarget.AUTO) inferTarget(clean, hasAttachment) else requested
        return when (inferred) {
            CaptureTarget.NOTE -> noteInterpretation(clean, hasAttachment)
            CaptureTarget.TASK, CaptureTarget.REMINDER, CaptureTarget.EVENT, CaptureTarget.INBOX ->
                taskInterpretation(clean, inferred, hasAttachment)
            CaptureTarget.AUTO -> error("AUTO debe resolverse antes de construir la interpretación")
        }
    }

    fun fingerprint(raw: String, attachmentUri: String = ""): String {
        val normalized = raw.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        val bytes = "$normalized\n${attachmentUri.trim()}".toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }

    private fun inferTarget(clean: String, hasAttachment: Boolean): CaptureTarget {
        if (noteCommand.containsMatchIn(clean)) return CaptureTarget.NOTE
        if (taskCommand.containsMatchIn(clean)) return CaptureTarget.TASK
        if (reminderSignal.containsMatchIn(clean)) return CaptureTarget.REMINDER
        if (isList(clean) || urlOnly.matches(clean)) return CaptureTarget.NOTE
        val parsed = clean.takeIf(String::isNotBlank)?.let { NaturalTaskParser.parse(it) }
        // Sustantivo de evento + señal temporal → evento (se resuelve como tarea con fecha).
        if (parsed != null && eventSignal.containsMatchIn(clean) &&
            (parsed.dueAt != null || parsed.confidence >= 0.62f)
        ) {
            return CaptureTarget.EVENT
        }
        if (parsed != null && (parsed.dueAt != null || parsed.confidence >= 0.62f || taskSignal.containsMatchIn(clean))) {
            return CaptureTarget.TASK
        }
        if (hasAttachment) return CaptureTarget.NOTE
        return CaptureTarget.INBOX
    }

    private fun noteInterpretation(clean: String, hasAttachment: Boolean): CaptureInterpretation {
        val withoutCommand = clean.replaceFirst(noteCommand, "").trim().ifBlank { clean }
        val checklist = isList(withoutCommand)
        val firstLine = withoutCommand.lineSequence()
            .map { it.replaceFirst(listPrefix, "").trim() }
            .firstOrNull(String::isNotBlank)
        val title = firstLine?.take(MAX_TITLE_CHARS)
            ?: if (hasAttachment) "Adjunto capturado" else "Nota rápida"
        return CaptureInterpretation(
            target = CaptureTarget.NOTE,
            title = title,
            body = withoutCommand,
            parsedTask = null,
            confidence = if (noteCommand.containsMatchIn(clean) || checklist) 0.95f else 0.75f,
            checklist = checklist,
            explanation = if (checklist) "Lista detectada" else "Contenido de nota"
        )
    }

    private fun taskInterpretation(
        clean: String,
        target: CaptureTarget,
        hasAttachment: Boolean
    ): CaptureInterpretation {
        val withoutCommand = clean.replaceFirst(taskCommand, "").trim().ifBlank { clean }
        val parserInput = withoutCommand.ifBlank { if (hasAttachment) "Revisar adjunto" else "Revisar captura" }
        val parsed = NaturalTaskParser.parse(parserInput)
        return CaptureInterpretation(
            target = target,
            title = parsed.title.take(MAX_TITLE_CHARS),
            body = clean,
            parsedTask = parsed,
            confidence = when (target) {
                CaptureTarget.REMINDER -> maxOf(parsed.confidence, 0.8f)
                CaptureTarget.INBOX -> minOf(parsed.confidence, 0.49f)
                else -> parsed.confidence
            },
            checklist = false,
            explanation = when (target) {
                CaptureTarget.REMINDER -> "Recordatorio detectado"
                CaptureTarget.INBOX -> "Guardado para organizar después"
                CaptureTarget.EVENT -> "Evento detectado"
                else -> "Acción detectada"
            }
        )
    }

    private fun isList(value: String): Boolean =
        value.lineSequence().count { listPrefix.containsMatchIn(it) } >= 2

    const val MAX_CONTENT_CHARS = 100_000
    private const val MAX_TITLE_CHARS = 200
}
