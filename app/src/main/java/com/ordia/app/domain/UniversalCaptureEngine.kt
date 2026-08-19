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
    // Los comandos de captura se reconocen como palabras completas (\b): sin el
    // límite, "ideal"/"idear", "notas", "notario" y "tareas" coincidían por
    // PREFIJO y la captura mutilaba el texto ("ideal proyecto" → nota "l
    // proyecto"). La captura nunca debe dañar datos del usuario.
    private val noteCommand = Regex("""(?i)^\s*(guardar\s+esto\s+como\s+nota|nota|idea)\b\s*[:\-]?\s*""")
    private val taskCommand = Regex("""(?i)^\s*(crear\s+)?(una\s+)?tarea\b\s*[:\-]?\s*""")
    // Encuadre reflexivo de recordatorio (c.678): "que no se me olvide X" /
    // "que no se me pase X" / "no dejes que se me olvide X" son las peticiones
    // de aviso más cotidianas junto a "recuérdame X". Simétrico con
    // NaturalTaskParser.bareReminderVerbPattern (que también limpia el título
    // y aplica el offset de respaldo con fecha). El pretérito "se me olvidó"
    // (contenido: un olvido pasado confesado) NO casa y no infiere aviso.
    private val reminderSignal = Regex("""(?i)\b(recu[eé]rdame|recordatorio|av[ií]same|no\s+dejes\s+que\s+(?:se\s+(?:me|te|le|les|nos|os)\s+)?olvide|no\s+se\s+(?:me|te|le|les|nos|os)\s+(?:olvides?|pasen?)|no\s+vaya\s+a\s+ser\s+que\s+se\s+(?:me|te|le|les|nos|os)\s+(?:olvides?|pasen?))\b""")
    // Verbos de acción cotidiana: sin fecha ni "tengo que/debo", una captura
    // como "hacer ejercicio" o "revisar contrato" es claramente una tarea, pero
    // antes caía a INBOX por no figurar aquí y exigía reclasificar a mano. Se
    // amplía con infinitivos CONCRETOS e inequívocos (formas -ar/-er/-ir son
    // verbos, no sustantivos: "limpiar"/"revisar" no se confunden con
    // "limpieza"/"revisión"). "ir a/al" cubre "ir al médico/banco". Se excluyen
    // verbos vagos/gerenciales ("organizar", "gestionar", "mirar"...) que aparecen
    // en texto NO accionable ("una idea que no sé organizar") y falsearían TASK.
    // Límites \b evitan coincidencia por prefijo (la captura nunca muta el texto).
    private val taskSignal = Regex("""(?i)\b(tengo\s+que|debo|hay\s+que|llamar|enviar|mandar|comprar|pagar|terminar|entregar|responder|reuni[oó]n|hacer|revisar|preparar|limpiar|arreglar|avisar|pedir|reservar|leer|escribir|estudiar|cocinar|visitar|ir\s+(?:a|al))\b""")
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
            CaptureTarget.TASK, CaptureTarget.REMINDER, CaptureTarget.INBOX ->
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
                else -> "Acción detectada"
            }
        )
    }

    private fun isList(value: String): Boolean =
        value.lineSequence().count { listPrefix.containsMatchIn(it) } >= 2

    const val MAX_CONTENT_CHARS = 100_000
    private const val MAX_TITLE_CHARS = 200
}
