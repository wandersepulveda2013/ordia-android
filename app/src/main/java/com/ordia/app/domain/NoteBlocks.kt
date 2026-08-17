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
}
