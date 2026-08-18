package com.ordia.app.conversations

import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class ChatMessage(
    val sender: String?,
    val text: String,
    val timestamp: Long? = null
)

data class ConversationPreview(
    val title: String,
    val participants: List<String>,
    val messages: List<ChatMessage>,
    val rawContent: String,
    val contentHash: String
)

/** Parser local y acotado para exportaciones TXT y JSON de chats. */
object ChatImportParser {
    const val MAX_IMPORT_CHARS = 2_000_000
    const val MAX_MESSAGES = 20_000

    private val whatsappLine = Regex(
        """^\[?(\d{1,2}/\d{1,2}/\d{2,4}),?\s+(\d{1,2}:\d{2}(?::\d{2})?)(?:\s*([ap]\.?\s*m\.?))?\]?\s*(?:-\s*)?([^:]{1,80}):\s*(.*)$""",
        RegexOption.IGNORE_CASE
    )
    private val senderLine = Regex("""^([^:\n]{1,80}):\s+(.+)$""")
    private val dateTimeFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("d/M/")
        .appendValueReduced(java.time.temporal.ChronoField.YEAR, 2, 4, 2000)
        .appendPattern(" H:mm")
        .optionalStart()
        .appendPattern(":ss")
        .optionalEnd()
        .toFormatter(Locale.ROOT)

    fun parse(raw: String, suggestedTitle: String = "Conversación importada"): ConversationPreview {
        val bounded = raw.take(MAX_IMPORT_CHARS).trim()
        require(bounded.isNotBlank()) { "La conversación está vacía." }
        val messages = if (bounded.startsWith("{")) {
            parseTelegramJson(bounded).ifEmpty { parseText(bounded) }
        } else {
            parseText(bounded)
        }.take(MAX_MESSAGES)
        require(messages.isNotEmpty()) { "No se encontraron mensajes legibles." }
        val participants = messages.mapNotNull { it.sender?.trim()?.takeIf(String::isNotBlank) }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(100)
        return ConversationPreview(
            title = suggestedTitle.substringBeforeLast('.').trim().ifBlank { "Conversación importada" }.take(160),
            participants = participants,
            messages = messages,
            rawContent = bounded,
            contentHash = sha256(bounded)
        )
    }

    fun looksLikeConversation(raw: String): Boolean {
        val sample = raw.lineSequence().take(40).toList()
        val structured = sample.count { whatsappLine.matches(it.trim()) }
        val attributed = sample.count { line ->
            val match = senderLine.matchEntire(line.trim())
            match != null && !match.groupValues[1].startsWith("http", ignoreCase = true)
        }
        return structured >= 2 || attributed >= 3
    }

    fun encodeParticipants(participants: List<String>): String =
        participants.map { it.replace('\n', ' ').trim() }.filter(String::isNotBlank).distinct().joinToString("\n")

    fun decodeParticipants(encoded: String): List<String> =
        encoded.lineSequence().map(String::trim).filter(String::isNotBlank).toList()

    private fun parseText(raw: String): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        raw.lineSequence().forEach { originalLine ->
            val line = originalLine.trimEnd()
            if (line.isBlank()) return@forEach
            val structured = whatsappLine.matchEntire(line.trim())
            if (structured != null) {
                val date = structured.groupValues[1]
                val time = structured.groupValues[2]
                val marker = structured.groupValues[3]
                messages += ChatMessage(
                    sender = structured.groupValues[4].trim().take(80),
                    text = structured.groupValues[5].trim().take(20_000),
                    timestamp = parseTimestamp(date, time, marker)
                )
                return@forEach
            }
            val attributed = senderLine.matchEntire(line.trim())
            if (attributed != null && !attributed.groupValues[1].startsWith("http", ignoreCase = true)) {
                messages += ChatMessage(
                    sender = attributed.groupValues[1].trim().take(80),
                    text = attributed.groupValues[2].trim().take(20_000)
                )
            } else if (messages.isNotEmpty()) {
                val previous = messages.removeAt(messages.lastIndex)
                messages += previous.copy(text = "${previous.text}\n${line.trim()}".take(20_000))
            } else {
                messages += ChatMessage(sender = null, text = line.trim().take(20_000))
            }
        }
        return messages.filter { it.text.isNotBlank() }
    }

    private fun parseTelegramJson(raw: String): List<ChatMessage> = runCatching {
        val root = JSONObject(raw)
        val direct = root.optJSONArray("messages")
        val nested = root.optJSONObject("chats")?.optJSONArray("list")?.optJSONObject(0)?.optJSONArray("messages")
        val array = direct ?: nested ?: return@runCatching emptyList()
        buildList {
            for (index in 0 until minOf(array.length(), MAX_MESSAGES)) {
                val item = array.optJSONObject(index) ?: continue
                if (item.optString("type", "message") != "message") continue
                val text = telegramText(item.opt("text")).trim().take(20_000)
                if (text.isBlank()) continue
                val epoch = item.optString("date_unixtime").toLongOrNull()?.times(1_000L)
                add(
                    ChatMessage(
                        sender = item.optString("from").trim().take(80).ifBlank { null },
                        text = text,
                        timestamp = epoch
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun telegramText(value: Any?): String = when (value) {
        is String -> value
        is JSONArray -> buildString {
            for (index in 0 until value.length()) {
                when (val part = value.opt(index)) {
                    is String -> append(part)
                    is JSONObject -> append(part.optString("text"))
                }
            }
        }
        is JSONObject -> value.optString("text")
        else -> ""
    }

    private fun parseTimestamp(date: String, time: String, marker: String): Long? = runCatching {
        val normalizedMarker = marker.lowercase(Locale.ROOT).replace(".", "").replace(" ", "")
        if (normalizedMarker.isBlank()) {
            LocalDateTime.parse("$date $time", dateTimeFormatter)
        } else {
            // Formatter con segundos opcionales (iOS exporta HH:MM:SS AM/PM).
            val formatter = DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern("d/M/")
                .appendValueReduced(java.time.temporal.ChronoField.YEAR, 2, 4, 2000)
                .appendPattern(" h:mm")
                .optionalStart()
                .appendPattern(":ss")
                .optionalEnd()
                .appendPattern(" a")
                .toFormatter(Locale.US)
            val amPm = if (normalizedMarker.startsWith("p")) "PM" else "AM"
            LocalDateTime.parse("$date $time $amPm", formatter)
        }.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
