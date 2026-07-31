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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ordia.app.R
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
        MoreEntry(Destination.Inbox, stringResource(R.string.more_inbox_desc), state.inboxTasks.size.toString(), MaterialTheme.colorScheme.primary),
        MoreEntry(Destination.Focus, stringResource(R.string.more_focus_desc), stringResource(R.string.more_focus_minutes_badge, state.focusMinutesThisWeek), MaterialTheme.colorScheme.tertiary),
        MoreEntry(Destination.Search, stringResource(R.string.more_search_desc), null, MaterialTheme.colorScheme.secondary),
        MoreEntry(Destination.Guardian, stringResource(R.string.more_guardian_desc), stringResource(R.string.more_guardian_level_badge, guardian.level), MaterialTheme.colorScheme.tertiary),
        MoreEntry(Destination.Contextual, stringResource(R.string.more_contextual_desc), null, MaterialTheme.colorScheme.secondary)
    )
    val organize = buildList {
        if (state.preferences.interfaceMode != InterfaceMode.SIMPLE) {
            add(MoreEntry(Destination.Projects, stringResource(R.string.more_projects_desc), state.projects.size.toString(), MaterialTheme.colorScheme.primary))
            add(MoreEntry(Destination.Habits, stringResource(R.string.more_habits_desc), state.habits.size.toString(), MaterialTheme.colorScheme.tertiary))
        }
        add(MoreEntry(Destination.Notes, stringResource(R.string.more_notes_desc), state.notes.size.toString(), MaterialTheme.colorScheme.secondary))
    }
    val review = buildList {
        if (state.preferences.interfaceMode == InterfaceMode.ADVANCED) {
            add(MoreEntry(Destination.Statistics, stringResource(R.string.more_statistics_desc), stringResource(R.string.more_completion_rate_badge, state.completionRate), MaterialTheme.colorScheme.primary))
            add(MoreEntry(Destination.Archive, stringResource(R.string.more_archive_desc), state.archivedCount.toString(), MaterialTheme.colorScheme.secondary))
        }
    }
    val system = listOf(
        MoreEntry(Destination.Settings, stringResource(R.string.more_settings_desc), null, MaterialTheme.colorScheme.tertiary)
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
                stringResource(R.string.more_header_eyebrow),
                stringResource(R.string.more_header_title),
                stringResource(R.string.more_header_subtitle)
            )
        }

        item { SectionHeader(stringResource(R.string.more_section_daily), stringResource(R.string.more_section_daily_supporting)) }
        daily.forEach { entry ->
            item(key = entry.destination.route) { ToolCard(entry, open) }
        }

        item { SectionHeader(stringResource(R.string.more_section_organize), stringResource(R.string.more_section_organize_supporting)) }
        organize.forEach { entry ->
            item(key = entry.destination.route) { ToolCard(entry, open) }
        }

        if (review.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.more_section_review), stringResource(R.string.more_section_review_supporting)) }
            review.forEach { entry ->
                item(key = entry.destination.route) { ToolCard(entry, open) }
            }
        }

        item { SectionHeader(stringResource(R.string.more_section_system), stringResource(R.string.more_section_system_supporting)) }
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
