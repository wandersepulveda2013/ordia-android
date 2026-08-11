package com.ordia.app.domain

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class NoteBlockType { PARAGRAPH, HEADING, CHECKLIST, QUOTE, BULLET, NUMBERED, DIVIDER }

data class NoteBlock(
    val id: String = UUID.randomUUID().toString(),
    val type: NoteBlockType = NoteBlockType.PARAGRAPH,
    val text: String = "",
    val checked: Boolean = false
)

object NoteBlockCodec {
    fun encode(blocks: List<NoteBlock>): String {
        val array = JSONArray()
        blocks.forEach { block ->
            array.put(JSONObject().apply {
                put("id", block.id)
                put("type", block.type.name)
                put("text", block.text)
                put("checked", block.checked)
            })
        }
        return array.toString()
    }

    fun decode(data: String, fallbackBody: String = ""): List<NoteBlock> {
        if (data.isBlank()) return fallbackBody.lines().filter { it.isNotBlank() }.map { NoteBlock(text = it) }
            .ifEmpty { listOf(NoteBlock()) }
        // Si el JSON raíz no es un array válido (truncado, corrupto), degradamos
        // a la representación en texto plano (fallbackBody). Pero si el array es
        // válido, conservamos todos los bloques sanos y descartamos solo los
        // elementos malformados: antes, un único elemento corrupto hacía perder
        // TODOS los bloques (data loss silencioso).
        val array = runCatching { JSONArray(data) }.getOrNull()
            ?: return listOf(NoteBlock(text = fallbackBody)).ifEmpty { listOf(NoteBlock()) }
        val blocks = buildList {
            for (index in 0 until array.length()) {
                val item = runCatching { array.getJSONObject(index) }.getOrNull() ?: continue
                add(
                    NoteBlock(
                        id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                        type = runCatching { NoteBlockType.valueOf(item.optString("type")) }.getOrDefault(NoteBlockType.PARAGRAPH),
                        text = item.optString("text"),
                        checked = item.optBoolean("checked", false)
                    )
                )
            }
        }
        return blocks.ifEmpty { listOf(NoteBlock(text = fallbackBody)).ifEmpty { listOf(NoteBlock()) } }
    }

    fun toPlainText(blocks: List<NoteBlock>): String = blocks.joinToString("\n") { block ->
        when (block.type) {
            NoteBlockType.HEADING -> block.text
            NoteBlockType.CHECKLIST -> "${if (block.checked) "[x]" else "[ ]"} ${block.text}"
            NoteBlockType.QUOTE -> "> ${block.text}"
            NoteBlockType.BULLET -> "• ${block.text}"
            NoteBlockType.NUMBERED -> block.text
            NoteBlockType.DIVIDER -> "---"
            NoteBlockType.PARAGRAPH -> block.text
        }
    }
}
