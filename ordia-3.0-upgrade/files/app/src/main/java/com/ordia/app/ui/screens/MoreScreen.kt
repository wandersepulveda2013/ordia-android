package com.ordia.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ordia.app.data.preferences.InterfaceMode
import com.ordia.app.domain.GuardianEngine
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.components.ActionCard
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.ui.components.SectionHeader
import com.ordia.app.ui.navigation.Destination

private data class MoreEntry(
    val destination: Destination,
    val description: String,
    val badge: String? = null,
    val accent: Color
)

@Composable
fun MoreScreen(state: OrdiaUiState, padding: PaddingValues, open: (String) -> Unit) {
    val guardian = GuardianEngine.snapshot(
        tasks = state.tasks,
        habits = state.habits,
        habitLogs = state.habitLogs,
        focusSessions = state.focusSessions,
        notes = state.notes,
        preferences = state.preferences
    )
    val daily = listOf(
        MoreEntry(Destination.Inbox, "Procesa capturas rápidas y decide su siguiente paso.", state.inboxTasks.size.toString(), MaterialTheme.colorScheme.primary),
        MoreEntry(Destination.Focus, "Temporizador, sesiones y tiempo de concentración real.", "${state.focusMinutesThisWeek}m", MaterialTheme.colorScheme.tertiary),
        MoreEntry(Destination.Search, "Encuentra tareas, proyectos, notas y hábitos desde un lugar.", null, MaterialTheme.colorScheme.secondary),
        MoreEntry(Destination.Guardian, "Visita el refugio, interactúa y observa cómo evoluciona tu compañero.", "Nv. ${guardian.level}", MaterialTheme.colorScheme.tertiary),
        MoreEntry(Destination.Contextual, "Detecta compromisos localmente desde texto compartido o notificaciones autorizadas.", null, MaterialTheme.colorScheme.secondary)
    )
    val organize = buildList {
        if (state.preferences.interfaceMode != InterfaceMode.SIMPLE) {
            add(MoreEntry(Destination.Projects, "Agrupa tareas y notas alrededor de resultados concretos.", state.projects.size.toString(), MaterialTheme.colorScheme.primary))
            add(MoreEntry(Destination.Habits, "Registra constancia, objetivos y rachas sin presión.", state.habits.size.toString(), MaterialTheme.colorScheme.tertiary))
        }
        add(MoreEntry(Destination.Notes, "Crea páginas flexibles para ideas, decisiones e información.", state.notes.size.toString(), MaterialTheme.colorScheme.secondary))
    }
    val review = buildList {
        if (state.preferences.interfaceMode == InterfaceMode.ADVANCED) {
            add(MoreEntry(Destination.Statistics, "Analiza tendencias reales de tareas, hábitos y enfoque.", "${state.completionRate}%", MaterialTheme.colorScheme.primary))
            add(MoreEntry(Destination.Archive, "Recupera o elimina elementos que ya no están activos.", state.archivedCount.toString(), MaterialTheme.colorScheme.secondary))
        }
    }
    val system = listOf(
        MoreEntry(Destination.Settings, "Personaliza tema, navegación, guardián, respaldo y preferencias.", null, MaterialTheme.colorScheme.tertiary)
    )

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            20.dp,
            padding.calculateTopPadding() + 20.dp,
            20.dp,
            padding.calculateBottomPadding() + 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                "CENTRO DE HERRAMIENTAS",
                "Explora Ordia",
                "Lo cotidiano permanece simple; las herramientas avanzadas están organizadas por propósito."
            )
        }

        item { SectionHeader("Acción diaria", "Captura, busca y protege tiempo de enfoque.") }
        daily.forEach { entry ->
            item(key = entry.destination.route) { ToolCard(entry, open) }
        }

        item { SectionHeader("Organización", "Conecta información y compromisos alrededor de objetivos.") }
        organize.forEach { entry ->
            item(key = entry.destination.route) { ToolCard(entry, open) }
        }

        if (review.isNotEmpty()) {
            item { SectionHeader("Revisión", "Aprende de tu actividad y recupera información archivada.") }
            review.forEach { entry ->
                item(key = entry.destination.route) { ToolCard(entry, open) }
            }
        }

        item { SectionHeader("Sistema", "Configura Ordia para que se adapte a tu forma de trabajar.") }
        system.forEach { entry ->
            item(key = entry.destination.route) { ToolCard(entry, open) }
        }
    }
}

@Composable
private fun ToolCard(entry: MoreEntry, open: (String) -> Unit) {
    ActionCard(
        title = entry.destination.label,
        description = entry.description,
        icon = entry.destination.icon,
        onClick = { open(entry.destination.route) },
        modifier = Modifier.fillMaxWidth(),
        badge = entry.badge,
        accent = entry.accent
    )
}
