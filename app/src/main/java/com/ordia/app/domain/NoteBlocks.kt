package com.ordia.app.domain

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class NoteBlockType {
    PARAGRAPH, HEADING, HEADING_2, HEADING_3, SUBTITLE,
    CHECKLIST, QUOTE, BULLET, NUMBERED, DIVIDER, CODE,
    IMAGE, FILE, LINK, AUDIO, DRAWING, HANDWRITING, SCANNER, TABLE
}

/**
 * Formato en línea para runs de texto dentro de un bloque de texto.
 * Se aplica a toda la porción señalada; el editor usa spans de Compose para
 * representarlo. `colorHex` vacío = color por defecto del tema.
 */
data class NoteSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val highlight: Boolean = false,
    val colorHex: String = "",
    val link: String = ""
)

/**
 * Alineación y estilo de párrafo aplicables a bloques de texto.
 */
enum class NoteAlign { LEFT, CENTER, RIGHT, JUSTIFY }

data class NoteParagraphStyle(
    val align: NoteAlign = NoteAlign.LEFT,
    val indent: Int = 0,
    val lineSpacing: Float = 1f
)

/**
 * Bloque del documento. Internamente rico (soporta formato, spans, adjuntos),
 * pero visualmente el usuario percibe un DOCUMENTO CONTINUO.
 *
 * `text` se mantiene para retrocompatibilidad con el cuerpo plano de notas
 * antiguas; `spans` añade formato en línea cuando existe. El codec prioriza
 * `spans` si no es nulo; si lo es, usa `text`.
 */
data class NoteBlock(
    val id: String = UUID.randomUUID().toString(),
    val type: NoteBlockType = NoteBlockType.PARAGRAPH,
    val text: String = "",
    val spans: List<NoteSpan>? = null,
    val checked: Boolean = false,
    val style: NoteParagraphStyle = NoteParagraphStyle(),
    val attachmentUri: String = "",
    val attachmentName: String = "",
    val mimeType: String = "",
    val language: String = "",
    val tableRows: List<List<String>> = emptyList(),
    val tableHeader: Boolean = false,
    val linkTitle: String = "",
    val linkDomain: String = "",
    val indent: Int = 0
) {
    /** Texto plano del bloque (ignorando formato en línea). */
    val plainText: String
        get() = if (spans.isNullOrEmpty()) text else spans.joinToString("") { it.text }
}

object NoteBlockCodec {
    fun encode(blocks: List<NoteBlock>): String {
        val array = JSONArray()
        blocks.forEach { block ->
            array.put(JSONObject().apply {
                put("id", block.id)
                put("type", block.type.name)
                put("text", block.text)
                put("checked", block.checked)
                if (block.spans != null) {
                    put("spans", JSONArray().apply {
                        block.spans.forEach { span ->
                            put(JSONObject().apply {
                                put("text", span.text)
                                put("bold", span.bold)
                                put("italic", span.italic)
                                put("underline", span.underline)
                                put("strikethrough", span.strikethrough)
                                put("highlight", span.highlight)
                                put("colorHex", span.colorHex)
                                put("link", span.link)
                            })
                        }
                    })
                }
                put("align", block.style.align.name)
                put("indent", block.style.indent)
                put("lineSpacing", block.style.lineSpacing)
                put("attachmentUri", block.attachmentUri)
                put("attachmentName", block.attachmentName)
                put("mimeType", block.mimeType)
                put("language", block.language)
                if (block.tableRows.isNotEmpty()) {
                    put("tableRows", JSONArray().apply {
                        block.tableRows.forEach { row ->
                            put(JSONArray().apply { row.forEach { put(it) } })
                        }
                    })
                }
                if (block.tableHeader) put("tableHeader", true)
                put("linkTitle", block.linkTitle)
                put("linkDomain", block.linkDomain)
                put("blockIndent", block.indent)
            })
        }
        return array.toString()
    }

    fun decode(data: String, fallbackBody: String = ""): List<NoteBlock> {
        if (data.isBlank()) return fallbackBody.lines().filter { it.isNotBlank() }.map { NoteBlock(text = it) }
            .ifEmpty { listOf(NoteBlock()) }
        return runCatching {
            val array = JSONArray(data)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val type = runCatching { NoteBlockType.valueOf(item.optString("type")) }
                        .getOrDefault(NoteBlockType.PARAGRAPH)
                    add(
                        NoteBlock(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            type = type,
                            text = item.optString("text"),
                            spans = if (item.has("spans") && !item.isNull("spans")) {
                                val spansArray = item.getJSONArray("spans")
                                buildList {
                                    for (s in 0 until spansArray.length()) {
                                        val sp = spansArray.getJSONObject(s)
                                        add(
                                            NoteSpan(
                                                text = sp.optString("text"),
                                                bold = sp.optBoolean("bold", false),
                                                italic = sp.optBoolean("italic", false),
                                                underline = sp.optBoolean("underline", false),
                                                strikethrough = sp.optBoolean("strikethrough", false),
                                                highlight = sp.optBoolean("highlight", false),
                                                colorHex = sp.optString("colorHex"),
                                                link = sp.optString("link")
                                            )
                                        )
                                    }
                                }.takeIf { it.isNotEmpty() }
                            } else null,
                            checked = item.optBoolean("checked", false),
                            style = NoteParagraphStyle(
                                align = runCatching { NoteAlign.valueOf(item.optString("align")) }
                                    .getOrDefault(NoteAlign.LEFT),
                                indent = item.optInt("indent", 0),
                                lineSpacing = item.optDouble("lineSpacing", 1.0).toFloat()
                            ),
                            attachmentUri = item.optString("attachmentUri"),
                            attachmentName = item.optString("attachmentName"),
                            mimeType = item.optString("mimeType"),
                            language = item.optString("language"),
                            tableRows = if (item.has("tableRows") && !item.isNull("tableRows")) {
                                val rowsArray = item.getJSONArray("tableRows")
                                buildList {
                                    for (r in 0 until rowsArray.length()) {
                                        val rowArray = rowsArray.getJSONArray(r)
                                        add(buildList {
                                            for (c in 0 until rowArray.length()) {
                                                add(rowArray.getString(c))
                                            }
                                        })
                                    }
                                }
                            } else emptyList(),
                            tableHeader = item.optBoolean("tableHeader", false),
                            linkTitle = item.optString("linkTitle"),
                            linkDomain = item.optString("linkDomain"),
                            indent = item.optInt("blockIndent", 0)
                        )
                    )
                }
            }
        }.getOrElse { listOf(NoteBlock(text = fallbackBody)) }.ifEmpty { listOf(NoteBlock()) }
    }

    fun toPlainText(blocks: List<NoteBlock>): String = blocks.joinToString("\n") { block ->
        when (block.type) {
            NoteBlockType.HEADING, NoteBlockType.HEADING_2, NoteBlockType.HEADING_3, NoteBlockType.SUBTITLE -> block.plainText
            NoteBlockType.CHECKLIST -> "${if (block.checked) "[x]" else "[ ]"} ${block.plainText}"
            NoteBlockType.QUOTE -> "> ${block.plainText}"
            NoteBlockType.BULLET -> "• ${block.plainText}"
            NoteBlockType.NUMBERED -> block.plainText
            NoteBlockType.DIVIDER -> "---"
            NoteBlockType.CODE -> block.plainText
            NoteBlockType.IMAGE -> "[imagen: ${block.attachmentName.ifBlank { block.attachmentUri }}]"
            NoteBlockType.FILE -> "[archivo: ${block.attachmentName.ifBlank { block.attachmentUri }}]"
            NoteBlockType.AUDIO -> "[audio: ${block.attachmentName.ifBlank { block.attachmentUri }}]"
            NoteBlockType.DRAWING -> "[dibujo]"
            NoteBlockType.HANDWRITING -> "[escritura a mano]"
            NoteBlockType.SCANNER -> "[documento escaneado: ${block.attachmentName}]"
            NoteBlockType.TABLE -> block.tableRows.joinToString("\n") { it.joinToString("\t") }
            NoteBlockType.LINK -> block.linkTitle.ifBlank { block.attachmentUri }
            NoteBlockType.PARAGRAPH -> block.plainText
        }
    }

    /** Convierte los bloques a Markdown razonable (títulos, listas, tablas, código, citas). */
    fun toMarkdown(blocks: List<NoteBlock>): String = buildString {
        blocks.forEachIndexed { index, block ->
            if (index > 0) append("\n")
            when (block.type) {
                NoteBlockType.HEADING -> { append("# ").append(block.plainText) }
                NoteBlockType.HEADING_2 -> { append("## ").append(block.plainText) }
                NoteBlockType.HEADING_3 -> { append("### ").append(block.plainText) }
                NoteBlockType.SUBTITLE -> { append("#### ").append(block.plainText) }
                NoteBlockType.CHECKLIST -> { append("- [").append(if (block.checked) "x" else " ").append("] ").append(block.plainText) }
                NoteBlockType.BULLET -> { append("- ").append(block.plainText) }
                NoteBlockType.NUMBERED -> { append("${index + 1}. ").append(block.plainText) }
                NoteBlockType.QUOTE -> { append("> ").append(block.plainText) }
                NoteBlockType.DIVIDER -> { append("---") }
                NoteBlockType.CODE -> { append("```\n").append(block.plainText).append("\n```") }
                NoteBlockType.IMAGE -> { append("![${block.attachmentName}](${block.attachmentUri})") }
                NoteBlockType.FILE -> { append("[📄 ${block.attachmentName}](${block.attachmentUri})") }
                NoteBlockType.AUDIO -> { append("[🔊 ${block.attachmentName}](${block.attachmentUri})") }
                NoteBlockType.DRAWING -> { append("[🎨 dibujo]") }
                NoteBlockType.HANDWRITING -> { append("[✍ escritura a mano]") }
                NoteBlockType.SCANNER -> { append("[📄 ${block.attachmentName}](${block.attachmentUri})") }
                NoteBlockType.TABLE -> {
                    if (block.tableRows.isNotEmpty()) {
                        val header = block.tableRows.first()
                        append("| ").append(header.joinToString(" | ")).append(" |")
                        append("\n| ").append(header.joinToString(" | ") { "---" }).append(" |")
                        block.tableRows.drop(1).forEach { row ->
                            append("\n| ").append(row.joinToString(" | ")).append(" |")
                        }
                    }
                }
                NoteBlockType.LINK -> { append("[${block.linkTitle.ifBlank { block.attachmentUri }}](${block.attachmentUri})") }
                NoteBlockType.PARAGRAPH -> append(block.plainText)
            }
        }
    }

    /** Escapa HTML básico para evitar inyección accidental al exportar. */
    private fun htmlEscape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    /** Convierte los bloques a un documento HTML autónomo y razonable. */
    fun toHtml(blocks: List<NoteBlock>, title: String = ""): String = buildString {
        append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
        if (title.isNotBlank()) append("<title>").append(htmlEscape(title)).append("</title>")
        append("<style>body{font-family:system-ui,sans-serif;max-width:720px;margin:24px auto;line-height:1.5;color:#1a1a1a}h1,h2,h3{line-height:1.25}blockquote{border-left:3px solid #ccc;margin:0;padding-left:12px;color:#555}code{background:#f4f4f4;padding:2px 4px;border-radius:4px}pre{background:#f4f4f4;padding:12px;border-radius:8px;overflow:auto}table{border-collapse:collapse}td,th{border:1px solid #ccc;padding:6px 10px}hr{border:none;border-top:1px solid #ccc}</style>")
        append("</head><body>")
        if (title.isNotBlank()) append("<h1>").append(htmlEscape(title)).append("</h1>")
        blocks.forEach { block ->
            val text = htmlEscape(block.plainText)
            when (block.type) {
                NoteBlockType.HEADING -> append("<h1>").append(text).append("</h1>")
                NoteBlockType.HEADING_2 -> append("<h2>").append(text).append("</h2>")
                NoteBlockType.HEADING_3 -> append("<h3>").append(text).append("</h3>")
                NoteBlockType.SUBTITLE -> append("<h4>").append(text).append("</h4>")
                NoteBlockType.CHECKLIST -> {
                    val box = if (block.checked) "checked" else ""
                    append("<div><input type=\"checkbox\" $box disabled> ").append(text).append("</div>")
                }
                NoteBlockType.BULLET -> append("<ul><li>").append(text).append("</li></ul>")
                NoteBlockType.NUMBERED -> append("<ol><li>").append(text).append("</li></ol>")
                NoteBlockType.QUOTE -> append("<blockquote>").append(text).append("</blockquote>")
                NoteBlockType.DIVIDER -> append("<hr>")
                NoteBlockType.CODE -> append("<pre><code>").append(text).append("</code></pre>")
                NoteBlockType.IMAGE -> append("<p><img src=\"").append(block.attachmentUri).append("\" alt=\"").append(htmlEscape(block.attachmentName)).append("\"></p>")
                NoteBlockType.FILE, NoteBlockType.SCANNER -> append("<p>📄 <a href=\"").append(block.attachmentUri).append("\">").append(htmlEscape(block.attachmentName)).append("</a></p>")
                NoteBlockType.AUDIO -> append("<p>🔊 <a href=\"").append(block.attachmentUri).append("\">").append(htmlEscape(block.attachmentName)).append("</a></p>")
                NoteBlockType.DRAWING -> append("<p>🎨 dibujo</p>")
                NoteBlockType.HANDWRITING -> append("<p>✍ escritura a mano</p>")
                NoteBlockType.TABLE -> {
                    if (block.tableRows.isNotEmpty()) {
                        append("<table>")
                        block.tableRows.forEachIndexed { idx, row ->
                            append("<tr>")
                            row.forEach { cell -> append(if (idx == 0) "<th>" else "<td>").append(htmlEscape(cell)).append(if (idx == 0) "</th>" else "</td>") }
                            append("</tr>")
                        }
                        append("</table>")
                    }
                }
                NoteBlockType.LINK -> append("<p><a href=\"").append(block.attachmentUri).append("\">").append(htmlEscape(block.linkTitle.ifBlank { block.attachmentUri })).append("</a></p>")
                NoteBlockType.PARAGRAPH -> if (text.isNotBlank()) append("<p>").append(text).append("</p>")
            }
        }
        append("</body></html>")
    }

    /**
     * Importa Markdown a bloques. Soporta: títulos (numerales), checklist (- [x] / - [ ]),
     * viñetas (- o asterisco), numeradas (N.), citas (>), código fence (tres backticks),
     * separadores (tres guiones) y párrafos. Es tolerante: lo que no reconoce queda como párrafo.
     */
    fun parseMarkdown(markdown: String): List<NoteBlock> {
        val lines = markdown.replace("\r\n", "\n").split("\n")
        val result = mutableListOf<NoteBlock>()
        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trimEnd()
            val trimmed = line.trim()
            if (trimmed.isEmpty()) { i++; continue }
            when {
                trimmed.startsWith("```") -> {
                    val sb = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        sb.append(lines[i]).append("\n")
                        i++
                    }
                    if (i < lines.size) i++ // cerrar fence
                    result.add(NoteBlock(type = NoteBlockType.CODE, text = sb.toString().trimEnd { it == '\n' }))
                    continue
                }
                trimmed.matches(Regex("^#{4}\\s+.*")) ->
                    result.add(NoteBlock(type = NoteBlockType.SUBTITLE, text = trimmed.removePrefix("####").trim()))
                trimmed.matches(Regex("^###\\s+.*")) ->
                    result.add(NoteBlock(type = NoteBlockType.HEADING_3, text = trimmed.removePrefix("###").trim()))
                trimmed.matches(Regex("^##\\s+.*")) ->
                    result.add(NoteBlock(type = NoteBlockType.HEADING_2, text = trimmed.removePrefix("##").trim()))
                trimmed.matches(Regex("^#\\s+.*")) ->
                    result.add(NoteBlock(type = NoteBlockType.HEADING, text = trimmed.removePrefix("#").trim()))
                trimmed.matches(Regex("^-\\s+\\[[xX ]]\\s+.*")) -> {
                    val checked = trimmed.substringAfter("[").firstOrNull()?.equals('x', ignoreCase = true) == true
                    val text = trimmed.substringAfter("]").trim()
                    result.add(NoteBlock(type = NoteBlockType.CHECKLIST, text = text, checked = checked))
                }
                trimmed.matches(Regex("^[-*]\\s+.*")) ->
                    result.add(NoteBlock(type = NoteBlockType.BULLET, text = trimmed.dropWhile { it == '-' || it == '*' }.trim()))
                trimmed.matches(Regex("^\\d+\\.\\s+.*")) ->
                    result.add(NoteBlock(type = NoteBlockType.NUMBERED, text = trimmed.substringAfter(".").trim()))
                trimmed.startsWith(">") ->
                    result.add(NoteBlock(type = NoteBlockType.QUOTE, text = trimmed.removePrefix(">").trim()))
                trimmed.matches(Regex("^(---|\\*\\*\\*|___)$")) ->
                    result.add(NoteBlock(type = NoteBlockType.DIVIDER))
                else ->
                    result.add(NoteBlock(type = NoteBlockType.PARAGRAPH, text = line))
            }
            i++
        }
        return result
    }

    /**
     * Importa HTML a bloques de forma tolerante. Extrae encabezados (h1-h4),
     * listas (ul/ol), citas, divisores, bloques de código y párrafos.
     * No intenta renderizar tablas ni multimedia; esos elementos se dejan
     * como texto plano razonable. Diseñado para HTML simple exportado por
     * blocs de notas, no como un parser HTML completo.
     */
    fun parseHtml(html: String): List<NoteBlock> {
        val result = mutableListOf<NoteBlock>()
        // Normalizar y separar por etiquetas de bloque en líneas.
        val normalized = html
            .replace("(?i)<br\\s*/?>".toRegex(), "\n")
            .replace("(?i)</p>".toRegex(), "\n")
            .replace("(?i)</li>".toRegex(), "\n")
            .replace("(?i)</h[1-6]>".toRegex(), "\n")
            .replace("(?i)<hr\\s*/?>".toRegex(), "\n---\n")
            .replace("(?i)<pre[^>]*>".toRegex(), "\n```\n")
            .replace("(?i)</pre>".toRegex(), "\n```\n")
        val lines = normalized.split("\n")
        var inCode = false
        val codeBuf = StringBuilder()
        for (raw in lines) {
            val tag = raw.trim()
            if (tag.isEmpty()) continue
            if (inCode) {
                if (tag.startsWith("```")) {
                    result.add(NoteBlock(type = NoteBlockType.CODE, text = codeBuf.toString().trimEnd()))
                    codeBuf.clear()
                    inCode = false
                } else {
                    codeBuf.append(stripTags(raw)).append("\n")
                }
                continue
            }
            when {
                tag.startsWith("```") -> { inCode = true; codeBuf.clear() }
                tag == "---" -> result.add(NoteBlock(type = NoteBlockType.DIVIDER))
                tag.matches("(?i)^<h1[^>]*>.*".toRegex()) ->
                    result.add(NoteBlock(type = NoteBlockType.HEADING, text = stripTags(tag).trim()))
                tag.matches("(?i)^<h2[^>]*>.*".toRegex()) ->
                    result.add(NoteBlock(type = NoteBlockType.HEADING_2, text = stripTags(tag).trim()))
                tag.matches("(?i)^<h3[^>]*>.*".toRegex()) ->
                    result.add(NoteBlock(type = NoteBlockType.HEADING_3, text = stripTags(tag).trim()))
                tag.matches("(?i)^<h[456][^>]*>.*".toRegex()) ->
                    result.add(NoteBlock(type = NoteBlockType.SUBTITLE, text = stripTags(tag).trim()))
                tag.matches("(?i)^<blockquote[^>]*>.*".toRegex()) ->
                    result.add(NoteBlock(type = NoteBlockType.QUOTE, text = stripTags(tag).trim()))
                tag.matches("(?i)^<ul[^>]*>.*".toRegex()) ||
                    tag.matches("(?i)^<li[^>]*>.*".toRegex()) ->
                    result.add(NoteBlock(type = NoteBlockType.BULLET, text = stripTags(tag).trim()))
                tag.matches("(?i)^<ol[^>]*>.*".toRegex()) ->
                    result.add(NoteBlock(type = NoteBlockType.NUMBERED, text = stripTags(tag).trim()))
                else -> {
                    val text = stripTags(tag).trim()
                    if (text.isNotEmpty()) result.add(NoteBlock(type = NoteBlockType.PARAGRAPH, text = text))
                }
            }
        }
        return result
    }

    /** Elimina etiquetas HTML y decodifica entidades básicas. */
    private fun stripTags(text: String): String {
        return text
            .replace("<[^>]*>".toRegex(), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .trim()
    }
}
