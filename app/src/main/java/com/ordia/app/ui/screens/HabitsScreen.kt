package com.ordia.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
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
import androidx.compose.ui.unit.dp
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
        item { ScreenHeader("CONSTANCIA SIN CULPA", "Hábitos y rutinas", "Registra el avance; no conviertas un día difícil en una deuda.", "Nuevo hábito") { showHabitDialog = true } }
        item { SectionHeader("Hábitos") }
        if (state.habits.isEmpty()) {
            item { EmptyState("Sin hábitos todavía", "Empieza con algo pequeño que puedas repetir.", "Crear hábito") { showHabitDialog = true } }
        } else {
            items(state.habits, key = { it.id }) { habit ->
                val count = state.habitCount(habit.id)
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(habit.title, style = MaterialTheme.typography.titleLarge)
                                Text(habit.details.ifBlank { "Meta: ${habit.targetPerPeriod}" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { editingHabit = habit; showHabitDialog = true }) { Icon(Icons.Outlined.Edit, "Editar hábito") }
                            IconButton(onClick = { vm.deleteHabit(habit) }) { Icon(Icons.Outlined.DeleteOutline, "Eliminar hábito") }
                        }
                        LinearProgressIndicator(progress = { (count.toFloat() / habit.targetPerPeriod).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("$count/${habit.targetPerPeriod} · Racha ${state.habitStreak(habit)} días", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            Button(onClick = { vm.toggleHabit(habit) }) { Text(if (count >= habit.targetPerPeriod) "Desmarcar" else "Registrar") }
                        }
                    }
                }
            }
        }
        item { SectionHeader("Rutinas", "Convierte una secuencia en tareas de la bandeja", "Nueva") { showRoutineDialog = true } }
        if (state.routines.isEmpty()) {
            item { Text("Una rutina puede ser “Preparar el día”, “Cerrar trabajo” o cualquier secuencia repetible.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(state.routines, key = { it.id }) { routine ->
                val steps = state.routineSteps(routine.id)
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(routine.name, style = MaterialTheme.typography.titleLarge)
                                Text("${steps.size} pasos · ${steps.sumOf { it.durationMinutes }} min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { editingRoutine = routine; showRoutineDialog = true }) { Icon(Icons.Outlined.Edit, "Editar rutina") }
                            IconButton(onClick = { vm.archiveRoutine(routine) }) { Icon(Icons.Outlined.DeleteOutline, "Archivar rutina") }
                        }
                        if (routine.description.isNotBlank()) Text(routine.description, style = MaterialTheme.typography.bodyMedium)
                        steps.take(4).forEachIndexed { index, step -> Text("${index + 1}. ${step.title}", style = MaterialTheme.typography.bodyMedium) }
                        OutlinedButton(onClick = { vm.runRoutine(routine) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.PlayArrow, null)
                            Text("Añadir rutina a la bandeja", Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }
}
