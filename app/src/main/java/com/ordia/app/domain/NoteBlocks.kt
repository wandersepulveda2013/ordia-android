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
        return runCatching {
            val array = JSONArray(data)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
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
        }.getOrElse { listOf(NoteBlock(text = fallbackBody)) }.ifEmpty { listOf(NoteBlock()) }
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
