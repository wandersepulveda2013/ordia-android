package com.ordia.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ordia.app.data.preferences.InterfaceMode
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.components.OrdiaScreenHeader
import com.ordia.app.ui.navigation.Destination

@Composable
fun MoreScreen(state: OrdiaUiState, padding: PaddingValues, open: (String) -> Unit) {
    val common = listOf(
        Triple(Destination.Inbox, "Capturas rápidas sin organizar", state.inboxTasks.size.toString()),
        Triple(Destination.Notes, "Páginas flexibles por bloques", state.notes.size.toString()),
        Triple(Destination.Focus, "Temporizador y registro de sesiones", "${state.focusMinutesThisWeek}m"),
        Triple(Destination.Search, "Busca en todo Ordia", "")
    )
    val organized = listOf(
        Triple(Destination.Projects, "Objetivos con tareas y notas", state.projects.size.toString()),
        Triple(Destination.Habits, "Hábitos y rutinas reutilizables", state.habits.size.toString())
    )
    val advanced = listOf(
        Triple(Destination.Statistics, "Tendencias de tu semana", "${state.completionRate}%"),
        Triple(Destination.Archive, "Recupera o elimina elementos archivados", state.archivedCount.toString())
    )
    val items = when (state.preferences.interfaceMode) {
        InterfaceMode.SIMPLE -> common
        InterfaceMode.ORGANIZED -> common + organized
        InterfaceMode.ADVANCED -> common + organized + advanced
    } + Triple(Destination.Settings, "Tema, guardián, copias y preferencias", "")
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, padding.calculateTopPadding() + 20.dp, 20.dp, padding.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { OrdiaScreenHeader("TODAS TUS HERRAMIENTAS", "Más", "Ordia muestra primero lo cotidiano y deja lo demás a un toque.") }
        items.forEach { (destination, description, count) ->
            item {
                Card(onClick = { open(destination.route) }) {
                    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(destination.icon, null)
                        Column(Modifier.weight(1f)) {
                            Text(destination.label, style = MaterialTheme.typography.titleMedium)
                            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (count.isNotBlank()) Text(count, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}
