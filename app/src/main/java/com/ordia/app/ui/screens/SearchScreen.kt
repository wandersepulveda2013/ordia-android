package com.ordia.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ordia.app.R
import com.ordia.app.domain.CommandPaletteCatalog
import com.ordia.app.domain.CommandPaletteEntry
import com.ordia.app.domain.CommandPaletteId
import com.ordia.app.domain.SearchEngine
import com.ordia.app.domain.SearchKind
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.EmptyState
import com.ordia.app.ui.components.ScreenHeader

@Composable
fun SearchScreen(
    state: OrdiaUiState,
    vm: OrdiaViewModel,
    contentPadding: PaddingValues,
    initialQuery: String = "",
    onCapture: () -> Unit,
    onToday: () -> Unit,
    onCalendar: () -> Unit,
    onNotes: () -> Unit,
    onTask: (Long) -> Unit,
    onProject: (Long) -> Unit,
    onNote: (Long) -> Unit,
    onHabits: () -> Unit,
    onFocus: () -> Unit,
    onConversations: () -> Unit,
    onAutomations: () -> Unit,
    onSettings: () -> Unit,
    onPrivacy: () -> Unit,
    onIntelligence: () -> Unit
) {
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val commitments by vm.commitments.collectAsStateWithLifecycle()
    val automations by vm.automationRules.collectAsStateWithLifecycle()
    val commands = remember(query) { CommandPaletteCatalog.search(query) }
    val results = remember(query, state.tasks, state.projects, state.notes, state.habits, state.routines, state.routineSteps, state.tags, state.taskTags, conversations, commitments, automations) {
        SearchEngine.search(query, state.tasks, state.projects, state.notes, state.habits, conversations, commitments, automations, state.routines, state.routineSteps, state.tags, state.taskTags)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, contentPadding.calculateTopPadding() + 20.dp, 20.dp, contentPadding.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ScreenHeader(stringResource(R.string.search_header_eyebrow), stringResource(R.string.search_header_title), stringResource(R.string.search_palette_header_subtitle)) }
        item { OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.search_palette_field_label)) }, singleLine = true) }
        if (commands.isNotEmpty()) {
            item(key = "command-section") {
                Text(
                    stringResource(if (query.isBlank()) R.string.search_palette_frequent else R.string.search_palette_commands),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(commands, key = { "command-${it.id}" }) { command ->
                CommandCard(command) {
                    when (command.id) {
                        CommandPaletteId.CAPTURE -> onCapture()
                        CommandPaletteId.TODAY -> onToday()
                        CommandPaletteId.CALENDAR -> onCalendar()
                        CommandPaletteId.NOTES -> onNotes()
                        CommandPaletteId.HABITS -> onHabits()
                        CommandPaletteId.FOCUS -> onFocus()
                        CommandPaletteId.AUTOMATIONS -> onAutomations()
                        CommandPaletteId.SETTINGS -> onSettings()
                        CommandPaletteId.PRIVACY -> onPrivacy()
                        CommandPaletteId.INTELLIGENCE -> onIntelligence()
                    }
                }
            }
        }
        if (query.isNotBlank() && results.isNotEmpty()) {
            item(key = "entity-section") {
                Text(stringResource(R.string.search_palette_entities), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            items(results, key = { "${it.kind}-${it.id}" }) { result ->
                SearchResultCard(result.kind, result.title, result.subtitle) {
                    when (result.kind) {
                        SearchKind.TASK -> onTask(result.id)
                        SearchKind.PROJECT -> onProject(result.id)
                        SearchKind.NOTE -> onNote(result.id)
                        SearchKind.HABIT -> onHabits()
                        SearchKind.ROUTINE -> onHabits()
                        SearchKind.CONVERSATION, SearchKind.COMMITMENT -> onConversations()
                        SearchKind.AUTOMATION -> onAutomations()
                    }
                }
            }
        }
        if (query.isNotBlank() && commands.isEmpty() && results.isEmpty()) {
            item { EmptyState(stringResource(R.string.search_no_results_title), stringResource(R.string.search_no_results_subtitle)) }
        }
    }
}

@Composable
private fun CommandCard(command: CommandPaletteEntry, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(command.id.icon(), contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(command.id.title(), style = MaterialTheme.typography.titleMedium)
                Text(command.id.description(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.search_kind_command), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun SearchResultCard(kind: SearchKind, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                when (kind) {
                    SearchKind.TASK -> Icons.Outlined.CheckCircle
                    SearchKind.PROJECT -> Icons.Outlined.Folder
                    SearchKind.NOTE -> Icons.Outlined.Description
                    SearchKind.HABIT -> Icons.Outlined.Spa
                    SearchKind.ROUTINE -> Icons.Outlined.Loop
                    SearchKind.CONVERSATION, SearchKind.COMMITMENT -> Icons.Outlined.ChatBubbleOutline
                    SearchKind.AUTOMATION -> Icons.Outlined.Bolt
                },
                contentDescription = null
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(kind.label(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

private fun CommandPaletteId.icon() = when (this) {
    CommandPaletteId.CAPTURE -> Icons.Outlined.AddCircleOutline
    CommandPaletteId.TODAY -> Icons.Outlined.Home
    CommandPaletteId.CALENDAR -> Icons.Outlined.CalendarMonth
    CommandPaletteId.NOTES -> Icons.Outlined.Description
    CommandPaletteId.HABITS -> Icons.Outlined.Spa
    CommandPaletteId.FOCUS -> Icons.Outlined.Timer
    CommandPaletteId.AUTOMATIONS -> Icons.Outlined.Bolt
    CommandPaletteId.SETTINGS -> Icons.Outlined.Settings
    CommandPaletteId.PRIVACY -> Icons.Outlined.Security
    CommandPaletteId.INTELLIGENCE -> Icons.Outlined.AutoAwesome
}

@Composable
private fun CommandPaletteId.title(): String = stringResource(when (this) {
    CommandPaletteId.CAPTURE -> R.string.search_command_capture
    CommandPaletteId.TODAY -> R.string.search_command_today
    CommandPaletteId.CALENDAR -> R.string.search_command_calendar
    CommandPaletteId.NOTES -> R.string.search_command_notes
    CommandPaletteId.HABITS -> R.string.search_command_habits
    CommandPaletteId.FOCUS -> R.string.search_command_focus
    CommandPaletteId.AUTOMATIONS -> R.string.search_command_automations
    CommandPaletteId.SETTINGS -> R.string.search_command_settings
    CommandPaletteId.PRIVACY -> R.string.search_command_privacy
    CommandPaletteId.INTELLIGENCE -> R.string.search_command_intelligence
})

@Composable
private fun CommandPaletteId.description(): String = stringResource(when (this) {
    CommandPaletteId.CAPTURE -> R.string.search_command_capture_desc
    CommandPaletteId.TODAY -> R.string.search_command_today_desc
    CommandPaletteId.CALENDAR -> R.string.search_command_calendar_desc
    CommandPaletteId.NOTES -> R.string.search_command_notes_desc
    CommandPaletteId.HABITS -> R.string.search_command_habits_desc
    CommandPaletteId.FOCUS -> R.string.search_command_focus_desc
    CommandPaletteId.AUTOMATIONS -> R.string.search_command_automations_desc
    CommandPaletteId.SETTINGS -> R.string.search_command_settings_desc
    CommandPaletteId.PRIVACY -> R.string.search_command_privacy_desc
    CommandPaletteId.INTELLIGENCE -> R.string.search_command_intelligence_desc
})

@Composable
private fun SearchKind.label(): String = when (this) {
    SearchKind.TASK -> stringResource(R.string.search_kind_task)
    SearchKind.PROJECT -> stringResource(R.string.search_kind_project)
    SearchKind.NOTE -> stringResource(R.string.search_kind_note)
    SearchKind.HABIT -> stringResource(R.string.search_kind_habit)
    SearchKind.ROUTINE -> stringResource(R.string.search_kind_routine)
    SearchKind.CONVERSATION -> stringResource(R.string.search_kind_conversation)
    SearchKind.COMMITMENT -> stringResource(R.string.search_kind_commitment)
    SearchKind.AUTOMATION -> stringResource(R.string.search_kind_automation)
}
