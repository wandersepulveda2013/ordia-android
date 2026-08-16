package com.ordia.app.domain

/**
 * Command Palette móvil (sección 16): búsqueda fuzzy rápida sobre acciones.
 */
data class CommandItem(
    val id: String,
    val label: String,
    val category: String
)

object CommandPaletteEngine {

    val defaults: List<CommandItem> = listOf(
        CommandItem("create_task", "Crear tarea", "Crear"),
        CommandItem("create_note", "Crear nota", "Crear"),
        CommandItem("capture", "Capturar", "Captura"),
        CommandItem("mental_offload", "Descarga mental", "Captura"),
        CommandItem("search", "Buscar", "Buscar"),
        CommandItem("organize_today", "Organizar hoy", "Organizar"),
        CommandItem("organize_week", "Organiza mi semana", "Organizar"),
        CommandItem("review_pending", "Revisar pendientes", "Guardianes"),
        CommandItem("open_calendar", "Abrir calendario", "Navegar"),
        CommandItem("open_guardians", "Guardianes", "Navegar"),
        CommandItem("open_settings", "Configuración", "Navegar")
    )

    /**
     * Fuzzy match: every char of query must appear in order in the label.
     * Ranks by density (matched length / total length) and prefix bonus.
     */
    fun search(query: String, items: List<CommandItem> = defaults): List<CommandItem> {
        if (query.isBlank()) return items
        val q = query.lowercase().trim()
        return items.mapNotNull { item ->
            score(item.label.lowercase(), q)?.let { item to it }
        }.sortedByDescending { it.second }.map { it.first }
    }

    private fun score(label: String, query: String): Double? {
        var li = 0
        var qi = 0
        var matched = 0
        var firstMatch = -1
        while (li < label.length && qi < query.length) {
            if (label[li] == query[qi]) {
                if (firstMatch < 0) firstMatch = li
                matched++
                qi++
            }
            li++
        }
        if (qi < query.length) return null
        val density = matched.toDouble() / label.length
        val prefixBonus = if (firstMatch == 0) 0.3 else 0.0
        return density + prefixBonus
    }
}
