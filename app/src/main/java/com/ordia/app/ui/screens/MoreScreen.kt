package com.ordia.app.ui.screens
import com.ordia.app.ui.components.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ordia.app.R
import com.ordia.app.data.preferences.InterfaceMode
import com.ordia.app.domain.GuardianEngine
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.components.OrdiaListItem
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.ui.components.SectionHeader
import com.ordia.app.ui.components.StatusBadge
import com.ordia.app.ui.navigation.Destination
import com.ordia.app.updates.OrdiaUpdateController
import com.ordia.app.updates.OrdiaUpdateController.UpdateState

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
    val daily = buildList {
        if (!state.preferences.showFloatingCapture) {
            add(MoreEntry(Destination.Capture, stringResource(R.string.more_capture_desc), null, MaterialTheme.colorScheme.primary))
        }
        add(MoreEntry(Destination.Inbox, stringResource(R.string.more_inbox_desc), state.inboxTasks.size.toString(), MaterialTheme.colorScheme.primary))
        add(MoreEntry(Destination.Focus, stringResource(R.string.more_focus_desc), stringResource(R.string.more_focus_minutes_badge, state.focusMinutesThisWeek), MaterialTheme.colorScheme.tertiary))
        add(MoreEntry(Destination.Search, stringResource(R.string.more_search_desc), null, MaterialTheme.colorScheme.secondary))
        add(MoreEntry(Destination.Assistant, stringResource(R.string.more_assistant_desc), null, MaterialTheme.colorScheme.primary))
        add(MoreEntry(Destination.Guardian, stringResource(R.string.more_guardian_desc), stringResource(R.string.more_guardian_level_badge, guardian.level), MaterialTheme.colorScheme.tertiary))
        add(MoreEntry(Destination.Conversations, stringResource(R.string.more_conversations_desc), null, MaterialTheme.colorScheme.primary))
        add(MoreEntry(Destination.Automations, stringResource(R.string.more_automations_desc), null, MaterialTheme.colorScheme.secondary))
    }
    val organize = buildList {
        add(MoreEntry(Destination.Notes, stringResource(R.string.more_notes_desc), state.notes.count { !it.archived }.toString(), MaterialTheme.colorScheme.secondary))
        if (state.preferences.interfaceMode != InterfaceMode.SIMPLE) {
            add(MoreEntry(Destination.Projects, stringResource(R.string.more_projects_desc), state.projects.size.toString(), MaterialTheme.colorScheme.primary))
            add(MoreEntry(Destination.Habits, stringResource(R.string.more_habits_desc), state.habits.size.toString(), MaterialTheme.colorScheme.tertiary))
        }
    }
    val review = buildList {
        if (state.preferences.interfaceMode == InterfaceMode.ADVANCED) {
            add(MoreEntry(Destination.Statistics, stringResource(R.string.more_statistics_desc), stringResource(R.string.more_completion_rate_badge, state.completionRate), MaterialTheme.colorScheme.primary))
            add(MoreEntry(Destination.Archive, stringResource(R.string.more_archive_desc), state.archivedCount.toString(), MaterialTheme.colorScheme.secondary))
        }
    }
    val updateState by OrdiaUpdateController.state.collectAsStateWithLifecycle()
    val updatesBadge = if (updateState is UpdateState.Available) stringResource(R.string.updates_badge_new) else null
    val system = listOf(
        MoreEntry(Destination.Updates, stringResource(R.string.more_updates_desc), updatesBadge, MaterialTheme.colorScheme.tertiary),
        MoreEntry(Destination.Intelligence, stringResource(R.string.more_intelligence_desc), null, MaterialTheme.colorScheme.primary),
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
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            ScreenHeader(
                stringResource(R.string.more_header_eyebrow),
                stringResource(R.string.more_header_title),
                stringResource(R.string.more_header_subtitle)
            )
        }

        item { SectionHeader(stringResource(R.string.more_section_daily), stringResource(R.string.more_section_daily_supporting)) }
        item(key = "group-daily") { GroupedTools(daily, open) }

        if (organize.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.more_section_organize), stringResource(R.string.more_section_organize_supporting)) }
            item(key = "group-organize") { GroupedTools(organize, open) }
        }

        if (review.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.more_section_review), stringResource(R.string.more_section_review_supporting)) }
            item(key = "group-review") { GroupedTools(review, open) }
        }

        item { SectionHeader(stringResource(R.string.more_section_system), stringResource(R.string.more_section_system_supporting)) }
        item(key = "group-system") { GroupedTools(system, open) }
    }
}

/** Apartado agrupado: una tarjeta con filas separadas por barras divisorias. */
@Composable
private fun GroupedTools(entries: List<MoreEntry>, open: (String) -> Unit) {
    OrdiaCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
    ) {
        Column(Modifier.fillMaxWidth()) {
            entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 53.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                }
                OrdiaListItem(
                    title = stringResource(entry.destination.labelRes),
                    subtitle = entry.description,
                    icon = entry.destination.icon,
                    iconTint = entry.accent,
                    onClick = { open(entry.destination.route) },
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (entry.badge != null) {
                                StatusBadge(
                                    entry.badge,
                                    background = entry.accent.copy(alpha = 0.12f),
                                    content = entry.accent
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                null,
                                Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                )
            }
        }
    }
}
