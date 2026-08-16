package com.ordia.app.ui.screens
import com.ordia.app.ui.components.*

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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ordia.app.R
import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.RoutineEntity
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.EmptyState
import com.ordia.app.ui.components.HabitEditorDialog
import com.ordia.app.ui.components.RoutineEditorDialog
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.ui.components.SectionHeader

@Composable
fun HabitsScreen(state: OrdiaUiState, vm: OrdiaViewModel, contentPadding: PaddingValues) {
    var editingHabit by remember { mutableStateOf<HabitEntity?>(null) }
    var showHabitDialog by remember { mutableStateOf(false) }
    var editingRoutine by remember { mutableStateOf<RoutineEntity?>(null) }
    var showRoutineDialog by remember { mutableStateOf(false) }

    if (showHabitDialog) HabitEditorDialog(
        existing = editingHabit,
        onDismiss = { showHabitDialog = false; editingHabit = null },
        onSave = { vm.saveHabit(it); showHabitDialog = false; editingHabit = null }
    )
    if (showRoutineDialog) RoutineEditorDialog(
        existing = editingRoutine,
        existingSteps = editingRoutine?.let { state.routineSteps(it.id).map { step -> step.title } }.orEmpty(),
        onDismiss = { showRoutineDialog = false; editingRoutine = null },
        onSave = { routine, steps -> vm.saveRoutine(routine, steps); showRoutineDialog = false; editingRoutine = null }
    )

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, contentPadding.calculateTopPadding() + 20.dp, 20.dp, contentPadding.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenHeader(stringResource(R.string.habits_eyebrow), stringResource(R.string.habits_title), stringResource(R.string.habits_subtitle), stringResource(R.string.habits_new_action)) { showHabitDialog = true } }
        item { SectionHeader(stringResource(R.string.habits_section_title)) }
        if (state.habits.isEmpty()) {
            item { EmptyState(stringResource(R.string.habits_empty_title), stringResource(R.string.habits_empty_desc), stringResource(R.string.habits_create), onAction = { showHabitDialog = true }) }
        } else {
            items(state.habits, key = { it.id }) { habit ->
                val count = state.habitCount(habit.id)
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(habit.title, style = MaterialTheme.typography.titleLarge)
                                Text(habit.details.ifBlank { stringResource(R.string.habits_goal, habit.targetPerPeriod) }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { editingHabit = habit; showHabitDialog = true }) { Icon(Icons.Outlined.Edit, stringResource(R.string.habits_edit)) }
                            IconButton(onClick = { vm.deleteHabit(habit) }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.habits_delete)) }
                        }
                        LinearProgressIndicator(progress = { (count.toFloat() / habit.targetPerPeriod).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.habits_progress, count, habit.targetPerPeriod, state.habitStreak(habit)), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            OrdiaButton(onClick = { vm.toggleHabit(habit) }) { Text(if (count >= habit.targetPerPeriod) stringResource(R.string.habits_unmark) else stringResource(R.string.habits_register)) }
                        }
                    }
                }
            }
        }
        item { SectionHeader(stringResource(R.string.habits_routines_section), stringResource(R.string.habits_routines_section_desc), stringResource(R.string.habits_routines_new)) { showRoutineDialog = true } }
        if (state.routines.isEmpty()) {
            item { Text(stringResource(R.string.habits_routines_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(state.routines, key = { it.id }) { routine ->
                val steps = state.routineSteps(routine.id)
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(routine.name, style = MaterialTheme.typography.titleLarge)
                                Text(stringResource(R.string.habits_routine_steps, steps.size, steps.sumOf { it.durationMinutes }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { editingRoutine = routine; showRoutineDialog = true }) { Icon(Icons.Outlined.Edit, stringResource(R.string.habits_routine_edit)) }
                            IconButton(onClick = { vm.archiveRoutine(routine) }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.habits_routine_archive)) }
                        }
                        if (routine.description.isNotBlank()) Text(routine.description, style = MaterialTheme.typography.bodyMedium)
                        steps.take(4).forEachIndexed { index, step -> Text("${index + 1}. ${step.title}", style = MaterialTheme.typography.bodyMedium) }
                        OrdiaOutlinedButton(onClick = { vm.runRoutine(routine) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.PlayArrow, null)
                            Text(stringResource(R.string.habits_run_routine), Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }
}
