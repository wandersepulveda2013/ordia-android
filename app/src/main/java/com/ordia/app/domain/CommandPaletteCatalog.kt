package com.ordia.app.domain

enum class CommandPaletteId {
    CAPTURE,
    TODAY,
    CALENDAR,
    NOTES,
    HABITS,
    FOCUS,
    AUTOMATIONS,
    SETTINGS,
    PRIVACY,
    INTELLIGENCE
}

data class CommandPaletteEntry(
    val id: CommandPaletteId,
    val aliases: Set<String>,
    val frequentRank: Int? = null
)

/** Pure, deterministic catalog used by the command palette UI. */
object CommandPaletteCatalog {
    private val entries = listOf(
        CommandPaletteEntry(
            CommandPaletteId.CAPTURE,
            setOf("capturar", "captura", "captura rapida", "anotar", "entrada rapida"),
            frequentRank = 0
        ),
        CommandPaletteEntry(
            CommandPaletteId.TODAY,
            setOf("hoy", "mi dia", "inicio", "ahora"),
            frequentRank = 1
        ),
        CommandPaletteEntry(
            CommandPaletteId.CALENDAR,
            setOf("calendario", "agenda", "planificador", "planner"),
            frequentRank = 2
        ),
        CommandPaletteEntry(
            CommandPaletteId.NOTES,
            setOf("notas", "nota", "apuntes"),
            frequentRank = 3
        ),
        CommandPaletteEntry(
            CommandPaletteId.HABITS,
            setOf("habitos", "habito", "rutinas", "rutina")
        ),
        CommandPaletteEntry(
            CommandPaletteId.FOCUS,
            setOf("enfoque", "focus", "concentracion", "temporizador"),
            frequentRank = 4
        ),
        CommandPaletteEntry(
            CommandPaletteId.AUTOMATIONS,
            setOf("automatizaciones", "automatizacion", "reglas", "regla")
        ),
        CommandPaletteEntry(
            CommandPaletteId.SETTINGS,
            setOf("ajustes", "configuracion", "preferencias", "opciones")
        ),
        CommandPaletteEntry(
            CommandPaletteId.PRIVACY,
            setOf("privacidad", "seguridad", "proteccion de datos", "datos privados")
        ),
        CommandPaletteEntry(
            CommandPaletteId.INTELLIGENCE,
            setOf("inteligencia", "inteligencia local", "ia", "modelo local", "asistente local")
        )
    )

    fun search(query: String): List<CommandPaletteEntry> {
        val normalizedQuery = query.normalizePaletteText()
        if (normalizedQuery.isBlank()) {
            return entries.filter { it.frequentRank != null }.sortedBy { it.frequentRank }
        }
        val words = normalizedQuery.split(' ').filter(String::isNotBlank)
        return entries.mapIndexedNotNull { index, entry ->
            val score = entry.aliases.minOfOrNull { alias ->
                matchScore(alias.normalizePaletteText(), normalizedQuery, words) ?: Int.MAX_VALUE
            }?.takeUnless { it == Int.MAX_VALUE } ?: return@mapIndexedNotNull null
            RankedCommand(entry, score, index)
        }.sortedWith(compareBy<RankedCommand> { it.score }.thenBy { it.catalogOrder })
            .map { it.entry }
    }

    private fun matchScore(alias: String, query: String, words: List<String>): Int? = when {
        alias == query -> 0
        alias.startsWith(query) -> 1
        query.length >= 3 && alias.contains(query) -> 2
        words.size > 1 && words.all(alias::contains) -> 3
        else -> null
    }

    private data class RankedCommand(
        val entry: CommandPaletteEntry,
        val score: Int,
        val catalogOrder: Int
    )
}

internal fun String.normalizePaletteText(): String =
    foldForSearch()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
