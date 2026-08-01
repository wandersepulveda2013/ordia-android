package com.ordia.app.backup

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Pure limits and graph/JSON checks used before a backup may replace local data. */
object BackupSecurityRules {
    const val MAX_UTF8_BYTES = 10 * 1024 * 1024
    const val MAX_ITEMS_PER_COLLECTION = 100_000
    const val MAX_TOTAL_ITEMS = 250_000
    const val MAX_JSON_DEPTH = 64
    const val MAX_SAFE_EPOCH_MILLIS = 32_503_680_000_000L // 3000-01-01 UTC
    const val CURRENT_EXPORT_VERSION = 6

    /** Versión del formato de copia con checksum SHA-256 obligatorio. */
    const val CHECKSUM_VERSION = 4

    val legacyCollections = setOf(
        "projects", "tasks", "notes", "habits", "habitLogs", "focusSessions",
        "routines", "routineSteps", "tags", "taskTags", "attachments"
    )
    val captureCollections = legacyCollections + setOf("captures", "captureDrafts")
    val requiredCollections = captureCollections + setOf("conversations", "commitments")

    fun supportsVersion(version: Int): Boolean = version in 2..CURRENT_EXPORT_VERSION
    fun inputSizeAllowed(utf8Bytes: Int): Boolean = utf8Bytes in 2..MAX_UTF8_BYTES
    fun collectionSizeAllowed(size: Int): Boolean = size in 0..MAX_ITEMS_PER_COLLECTION
    fun totalSizeAllowed(size: Int): Boolean = size in 0..MAX_TOTAL_ITEMS
    fun requiredCollectionsFor(version: Int): Set<String> = when {
        version >= 6 -> requiredCollections
        version >= 5 -> captureCollections
        else -> legacyCollections
    }
    fun hasAllCollections(names: Set<String>, version: Int = CURRENT_EXPORT_VERSION): Boolean =
        requiredCollectionsFor(version).all(names::contains)
    fun hasDuplicatePairs(pairs: Collection<Pair<Long, Long>>): Boolean = pairs.toSet().size != pairs.size

    /**
     * SHA-256 hexadecimal en minúsculas de un contenido (ORD-031).
     * Se usa para detectar corrupción o modificación en copias de seguridad.
     */
    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    /** ¿El valor tiene el formato correcto de un checksum SHA-256 (64 hex)? */
    fun isValidChecksumFormat(value: String): Boolean =
        value.length == 64 && value.all { it.isDigit() || it in 'a'..'f' }

    fun decodeUtf8Strict(bytes: ByteArray): String? = runCatching {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull()

    fun hasValidUnicodeScalars(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            when {
                char.isHighSurrogate() -> {
                    if (index + 1 >= value.length || !value[index + 1].isLowSurrogate()) return false
                    index += 2
                }
                char.isLowSurrogate() -> return false
                else -> index++
            }
        }
        return true
    }

    /**
     * Performs a bounded, allocation-light pass before JSONObject parsing.
     * It rejects excessive nesting and duplicate top-level keys, including escaped aliases
     * such as "tasks" and "\\u0074asks" that JSONObject would otherwise overwrite silently.
     */
    fun validateJsonEnvelope(raw: String): String? {
        data class Frame(val closing: Char, val keys: MutableSet<String>?)
        val first = raw.indexOfFirst { !it.isWhitespace() }
        val last = raw.indexOfLast { !it.isWhitespace() }
        if (first < 0 || last < 0 || raw[first] != '{' || raw[last] != '}') {
            return "La copia debe contener un único objeto JSON raíz."
        }
        val stack = mutableListOf<Frame>()
        var inString = false
        var escaped = false
        var stringStart = -1
        var rootClosed = false
        var index = 0
        while (index < raw.length) {
            val char = raw[index]
            if (rootClosed) {
                if (!char.isWhitespace()) return "La copia contiene datos después del objeto JSON raíz."
                index++
                continue
            }
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> {
                        val token = raw.substring(stringStart, index)
                        inString = false
                        var lookahead = index + 1
                        while (lookahead < raw.length && raw[lookahead].isWhitespace()) lookahead++
                        val frame = stack.lastOrNull()
                        if (frame?.keys != null && lookahead < raw.length && raw[lookahead] == ':') {
                            val key = decodeJsonString(token)
                                ?: return "La copia contiene una clave JSON con escape inválido."
                            if (!frame.keys.add(key)) return "La copia contiene una clave JSON duplicada: $key."
                        }
                    }
                }
            } else {
                when (char) {
                    '"' -> { inString = true; stringStart = index + 1 }
                    '{' -> {
                        if (stack.isEmpty() && index != first) return "La copia contiene más de un objeto JSON raíz."
                        stack += Frame('}', mutableSetOf())
                    }
                    '[' -> {
                        if (stack.isEmpty()) return "La copia contiene una lista fuera del objeto raíz."
                        stack += Frame(']', null)
                    }
                    '}', ']' -> {
                        val frame = stack.lastOrNull() ?: return "La estructura JSON de la copia no es válida."
                        if (frame.closing != char) return "La estructura JSON mezcla cierres incompatibles."
                        stack.removeAt(stack.lastIndex)
                        if (stack.isEmpty()) {
                            if (char != '}' || index != last) return "La copia contiene datos después del objeto JSON raíz."
                            rootClosed = true
                        }
                    }
                    else -> if (stack.isEmpty() && !char.isWhitespace()) {
                        return "La copia contiene datos fuera del objeto JSON raíz."
                    }
                }
                if (stack.size > MAX_JSON_DEPTH) return "La copia supera la profundidad JSON permitida."
            }
            index++
        }
        if (inString || escaped || stack.isNotEmpty() || !rootClosed) return "La estructura JSON de la copia está incompleta."
        return null
    }

    fun parseUniqueDayList(value: String, allowed: IntRange): Set<Int>? {
        if (value.isBlank()) return emptySet()
        val parts = value.split(',').map { it.trim() }
        if (parts.any { it.isBlank() }) return null
        val days = parts.map { it.toIntOrNull() ?: return null }
        if (days.any { it !in allowed } || days.toSet().size != days.size) return null
        return days.toSet()
    }

    /** Detects self-references and longer cycles without recursion or stack-overflow risk. */
    fun hasParentCycle(parentById: Map<Long, Long?>): Boolean {
        val checked = mutableSetOf<Long>()
        for (start in parentById.keys) {
            if (start in checked) continue
            val path = mutableSetOf<Long>()
            var current: Long? = start
            while (current != null && current in parentById) {
                if (!path.add(current)) return true
                if (current in checked) break
                current = parentById[current]
            }
            checked += path
        }
        return false
    }

    private fun decodeJsonString(value: String): String? {
        val output = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index++]
            if (char != '\\') {
                if (char.code < 0x20) return null
                output.append(char)
                continue
            }
            if (index >= value.length) return null
            when (val escaped = value[index++]) {
                '"', '\\', '/' -> output.append(escaped)
                'b' -> output.append('\b')
                'f' -> output.append('\u000c')
                'n' -> output.append('\n')
                'r' -> output.append('\r')
                't' -> output.append('\t')
                'u' -> {
                    if (index + 4 > value.length) return null
                    val code = value.substring(index, index + 4).toIntOrNull(16) ?: return null
                    output.append(code.toChar())
                    index += 4
                }
                else -> return null
            }
        }
        return output.toString().takeIf(::hasValidUnicodeScalars)
    }
}
