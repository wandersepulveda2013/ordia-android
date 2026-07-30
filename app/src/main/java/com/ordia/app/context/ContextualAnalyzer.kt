package com.ordia.app.context

import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.text.Normalizer

object ContextualAnalyzer {
    private const val MAX_INPUT = 4_000
    private val sensitive = Regex("(?i)\\b(?:contrasena|contrase.a|password|pin|cvv|cvc|otp|codigo de seguridad|token bancario|clave bancaria|numero de tarjeta)\\b")
    private val study = Regex("(?i)\\b(?:estudi(?:o|ar|ando|are)|repasar|examen|clase|curso|tarea de la universidad|investigar)\\b")
    private val event = Regex("(?i)\\b(?:ire|voy a|iremos|nos vemos|quedamos|visitare?|cita|reunion|llegare|pasare)\\b")
    private val task = Regex("(?i)\\b(?:tengo que|debo|hay que|recordar|comprar|pagar|llamar|enviar|entregar|hacer|terminar|completar)\\b")
    private val timeColon = Regex("(?i)\\b([01]?\\d|2[0-3]):([0-5]\\d)\\s*(a\\.?\\s*m\\.?|p\\.?\\s*m\\.?)?\\b")
    private val timeWords = Regex("(?i)\\ba\\s+las?\\s+(\\d{1,2})(?:[:.]([0-5]\\d))?\\s*(a\\.?\\s*m\\.?|p\\.?\\s*m\\.?)?\\b")
    private val explicitDate = Regex("\\b(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\b")

    fun analyze(
        raw: String,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        sourcePackage: String? = null
    ): ContextualSuggestion? {
        val clean = raw.replace(Regex("\\s+"), " ").trim().take(MAX_INPUT)
        if (clean.length < 3 || sensitive.containsMatchIn(clean)) return null
        val lower = normalize(clean)
        val kind = when {
            study.containsMatchIn(lower) -> ContextualKind.STUDY
            event.containsMatchIn(lower) -> ContextualKind.EVENT
            task.containsMatchIn(lower) -> ContextualKind.TASK
            else -> ContextualKind.NOTE
        }
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val date = parseDate(lower, now.toLocalDate())
        val time = parseTime(lower)
        val dueAt = when {
            date != null -> LocalDateTime.of(date, time ?: LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli()
            time != null -> LocalDateTime.of(now.toLocalDate().plusDays(if (time <= now.toLocalTime()) 1 else 0), time)
                .atZone(zone).toInstant().toEpochMilli()
            else -> null
        }
        val hasIntent = kind != ContextualKind.NOTE
        val confidence = (0.42 + (if (hasIntent) 0.26 else 0.0) + (if (date != null) 0.18 else 0.0) + (if (time != null) 0.10 else 0.0))
            .coerceIn(0.0, 0.98)
        val title = buildTitle(clean, kind)
        if (title.isBlank()) return null
        return ContextualSuggestion(
            id = fingerprint(lower, sourcePackage),
            kind = kind,
            title = title,
            dueAt = dueAt,
            confidence = confidence,
            sourcePackage = sourcePackage?.take(180)
        )
    }

    fun containsSensitiveContent(raw: String): Boolean = sensitive.containsMatchIn(normalize(raw))

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")

    private fun parseDate(text: String, today: LocalDate): LocalDate? {
        when {
            "pasado manana" in text -> return today.plusDays(2)
            "manana" in text -> return today.plusDays(1)
            Regex("\\bhoy\\b").containsMatchIn(text) -> return today
        }
        explicitDate.find(text)?.let { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@let
            val month = match.groupValues[2].toIntOrNull() ?: return@let
            var year = match.groupValues[3].toIntOrNull() ?: today.year
            if (year in 0..99) year += 2000
            runCatching { LocalDate.of(year, month, day) }.getOrNull()?.let { return it }
        }
        val weekdays = linkedMapOf(
            "lunes" to DayOfWeek.MONDAY, "martes" to DayOfWeek.TUESDAY,
            "miercoles" to DayOfWeek.WEDNESDAY,
            "jueves" to DayOfWeek.THURSDAY, "viernes" to DayOfWeek.FRIDAY,
            "sabado" to DayOfWeek.SATURDAY,
            "domingo" to DayOfWeek.SUNDAY
        )
        weekdays.entries.firstOrNull { Regex("\\b${it.key}\\b").containsMatchIn(text) }?.let {
            return today.with(TemporalAdjusters.nextOrSame(it.value)).let { d -> if (d == today) d.plusWeeks(1) else d }
        }
        return null
    }

    private fun parseTime(text: String): LocalTime? {
        val match = timeWords.find(text) ?: timeColon.find(text) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
        val meridiem = match.groupValues.getOrNull(3).orEmpty().lowercase()
        if (meridiem.startsWith("p") && hour in 1..11) hour += 12
        if (meridiem.startsWith("a") && hour == 12) hour = 0
        return runCatching { LocalTime.of(hour, minute) }.getOrNull()
    }

    private fun buildTitle(clean: String, kind: ContextualKind): String {
        val first = clean.substringBefore('\n').substringBefore('.').trim()
        val stripped = first
            .replace(Regex("(?i)\\b(?:hoy|mañana|manana|pasado mañana|pasado manana)\\b"), "")
            .replace(timeWords, "")
            .replace(timeColon, "")
            .replace(Regex("\\s+"), " ")
            .trim(' ', ',', ';', ':', '-')
        val value = stripped.ifBlank {
            when (kind) {
                ContextualKind.STUDY -> "Sesión de estudio"
                ContextualKind.EVENT -> "Compromiso"
                ContextualKind.TASK -> "Tarea contextual"
                ContextualKind.NOTE -> "Nota contextual"
            }
        }
        return value.take(100).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es")) else it.toString() }
    }

    private fun fingerprint(normalized: String, sourcePackage: String?): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("${sourcePackage.orEmpty()}|$normalized".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
