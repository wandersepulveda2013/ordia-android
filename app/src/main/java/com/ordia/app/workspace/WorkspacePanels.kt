package com.ordia.app.workspace

enum class WorkspacePanel { TASKS, NOTES, CONVERSATIONS, AUTOMATIONS, DAILY_PLAN, SEARCH }

data class WorkspaceState(
    val open: List<WorkspacePanel> = listOf(WorkspacePanel.TASKS, WorkspacePanel.NOTES),
    val active: WorkspacePanel? = WorkspacePanel.TASKS,
    val minimized: Set<WorkspacePanel> = emptySet()
) {
    fun visible(): List<WorkspacePanel> = open.filterNot(minimized::contains)
}

sealed interface WorkspaceEvent {
    data class Open(val panel: WorkspacePanel) : WorkspaceEvent
    data class Close(val panel: WorkspacePanel) : WorkspaceEvent
    data class Minimize(val panel: WorkspacePanel) : WorkspaceEvent
    data class Activate(val panel: WorkspacePanel) : WorkspaceEvent
}

object WorkspacePanels {
    fun reduce(state: WorkspaceState, event: WorkspaceEvent): WorkspaceState = when (event) {
        is WorkspaceEvent.Open -> state.copy(
            open = (state.open + event.panel).distinct(),
            active = event.panel,
            minimized = state.minimized - event.panel
        )
        is WorkspaceEvent.Activate -> if (event.panel !in state.open) reduce(state, WorkspaceEvent.Open(event.panel))
            else state.copy(active = event.panel, minimized = state.minimized - event.panel)
        is WorkspaceEvent.Close -> {
            val remaining = state.open - event.panel
            val visible = remaining.filterNot((state.minimized - event.panel)::contains)
            state.copy(open = remaining, active = if (state.active == event.panel) visible.lastOrNull() else state.active, minimized = state.minimized - event.panel)
        }
        is WorkspaceEvent.Minimize -> {
            val minimized = state.minimized + event.panel
            val visible = state.open.filterNot(minimized::contains)
            state.copy(active = if (state.active == event.panel) visible.lastOrNull() else state.active, minimized = minimized)
        }
    }

    fun encode(state: WorkspaceState): String = listOf(
        state.open.joinToString(",") { it.name },
        state.active?.name.orEmpty(),
        state.minimized.joinToString(",") { it.name }
    ).joinToString("|")

    fun decode(value: String): WorkspaceState = runCatching {
        val parts = value.split('|')
        fun panels(index: Int) = parts.getOrNull(index).orEmpty().split(',').filter(String::isNotBlank).map(WorkspacePanel::valueOf)
        val open = panels(0).distinct()
        val active = parts.getOrNull(1)?.takeIf(String::isNotBlank)?.let(WorkspacePanel::valueOf)?.takeIf(open::contains)
        WorkspaceState(open, active, panels(2).toSet().intersect(open.toSet()))
    }.getOrElse { WorkspaceState() }
}
