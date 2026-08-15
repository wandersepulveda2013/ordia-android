package com.ordia.app.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.HabitFrequency
import com.ordia.app.data.local.ProjectEntity
import com.ordia.app.data.local.ProjectStatus
import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.RoutineEntity
import com.ordia.app.data.local.TagEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
import com.ordia.app.domain.DateRules
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun TaskEditorDialog(
    existing: TaskEntity? = null,
    projects: List<ProjectEntity>,
    tags: List<TagEntity>,
    selectedTagIds: Set<Long> = emptySet(),
    defaultDueDate: LocalDate? = null,
    onAddTag: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (TaskEntity, Set<Long>) -> Unit
) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var details by remember(existing?.id) { mutableStateOf(existing?.details.orEmpty()) }
    var dueAt by remember(existing?.id) {
        mutableStateOf(existing?.dueAt ?: defaultDueDate?.let { DateRules.toEpochMillis(it, LocalTime.of(9, 0)) })
    }
    var duration by remember(existing?.id) { mutableStateOf((existing?.durationMinutes ?: 25).toString()) }
    var priority by remember(existing?.id) { mutableStateOf(existing?.priority ?: TaskPriority.NORMAL) }
    var projectId by remember(existing?.id) { mutableStateOf(existing?.projectId) }
    var recurrence by remember(existing?.id) { mutableStateOf(existing?.recurrence ?: RecurrenceFrequency.NONE) }
    var reminderEnabled by remember(existing?.id) { mutableStateOf(existing?.reminderAt != null) }
    var flagged by remember(existing?.id) { mutableStateOf(existing?.flagged ?: false) }
    var priorityMenu by remember { mutableStateOf(false) }
    var projectMenu by remember { mutableStateOf(false) }
    var recurrenceMenu by remember { mutableStateOf(false) }
    var chosenTags by remember(existing?.id) { mutableStateOf(selectedTagIds) }
    var newTag by remember(existing?.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Nueva tarea" else "Editar tarea") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OrdiaInput(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = "Título", minLines = 1, maxLines = 1)
                OutlinedTextField(details, { details = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Detalles") }, minLines = 2, maxLines = 4)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val current = dueAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() } ?: LocalDate.now()
                            DatePickerDialog(context, { _, year, month, day ->
                                val chosenDate = LocalDate.of(year, month + 1, day)
                                val time = dueAt?.let { DateRules.toLocalTime(it) } ?: LocalTime.of(9, 0)
                                dueAt = DateRules.toEpochMillis(chosenDate, time)
                            }, current.year, current.monthValue - 1, current.dayOfMonth).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.CalendarMonth, null)
                        Text(dueAt?.let { DateRules.formatDate(it) } ?: "Fecha", Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(
                        onClick = {
                            val current = dueAt?.let { DateRules.toLocalTime(it) } ?: LocalTime.of(9, 0)
                            TimePickerDialog(context, { _, hour, minute ->
                                val date = dueAt?.let { DateRules.toLocalDate(it) } ?: LocalDate.now()
                                dueAt = DateRules.toEpochMillis(date, LocalTime.of(hour, minute))
                            }, current.hour, current.minute, false).show()
                        },
                        enabled = dueAt != null,
                        modifier = Modifier.weight(1f)
                    ) { Text(dueAt?.let { DateRules.formatTime(it) } ?: "Hora") }
                }
                if (dueAt != null) TextButton(onClick = { dueAt = null; reminderEnabled = false }) { Text("Quitar fecha") }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { priorityMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Flag, null)
                            Text(priority.label(), Modifier.padding(start = 6.dp))
                        }
                        DropdownMenu(priorityMenu, { priorityMenu = false }) {
                            TaskPriority.entries.forEach { value -> DropdownMenuItem(text = { Text(value.label()) }, onClick = { priority = value; priorityMenu = false }) }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { projectMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(projects.firstOrNull { it.id == projectId }?.name ?: "Sin proyecto")
                        }
                        DropdownMenu(projectMenu, { projectMenu = false }) {
                            DropdownMenuItem(text = { Text("Sin proyecto") }, onClick = { projectId = null; projectMenu = false })
                            projects.forEach { project -> DropdownMenuItem(text = { Text(project.name) }, onClick = { projectId = project.id; projectMenu = false }) }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { recurrenceMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Repeat, null)
                            Text(recurrence.label(), Modifier.padding(start = 6.dp))
                        }
                        DropdownMenu(recurrenceMenu, { recurrenceMenu = false }) {
                            RecurrenceFrequency.entries.forEach { value -> DropdownMenuItem(text = { Text(value.label()) }, onClick = { recurrence = value; recurrenceMenu = false }) }
                        }
                    }
                    OrdiaInput(duration, { duration = it.filter(Char::isDigit).take(3) }, modifier = Modifier.weight(0.7f), label = "Minutos", minLines = 1, maxLines = 1)
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recordar 30 minutos antes", Modifier.weight(1f))
                    Switch(reminderEnabled, { reminderEnabled = it }, enabled = dueAt != null)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Marcar como importante", Modifier.weight(1f))
                    Switch(flagged, { flagged = it })
                }

                Text("Etiquetas")
                if (tags.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(tags, key = { it.id }) { tag ->
                            FilterChip(
                                selected = tag.id in chosenTags,
                                onClick = { chosenTags = if (tag.id in chosenTags) chosenTags - tag.id else chosenTags + tag.id },
                                label = { Text(tag.name) }
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OrdiaInput(newTag, { newTag = it.take(30) }, modifier = Modifier.weight(1f), label = "Nueva etiqueta", minLines = 1, maxLines = 1)
                    OrdiaButton("Añadir", { onAddTag(newTag); newTag = "" }, enabled = newTag.isNotBlank() && tags.none { it.name.equals(newTag.trim(), true) })
                }
            }
        },
        confirmButton = {
            OrdiaButton("Guardar", { val normalizedDue = dueAt
                    val task = (existing ?: TaskEntity(title = title)).copy(
                        title = title,
                        details = details,
                        dueAt = normalizedDue,
                        reminderAt = if (reminderEnabled && normalizedDue != null) normalizedDue - 30 * 60_000L else null,
                        durationMinutes = duration.toIntOrNull()?.coerceIn(1, 480) ?: 25,
                        priority = priority,
                        projectId = projectId,
                        recurrence = recurrence,
                        flagged = flagged,
                        status = when {
                            existing?.completed == true -> TaskStatus.COMPLETED
                            normalizedDue == null -> TaskStatus.INBOX
                            else -> TaskStatus.PLANNED
                        },
                        updatedAt = now
                    )
                    onSave(task, chosenTags) }, enabled = title.isNotBlank())
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun ProjectEditorDialog(existing: ProjectEntity? = null, onDismiss: () -> Unit, onSave: (ProjectEntity) -> Unit) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var description by remember(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    var status by remember(existing?.id) { mutableStateOf(existing?.status ?: ProjectStatus.ACTIVE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Nuevo proyecto" else "Editar proyecto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OrdiaInput(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = "Nombre", minLines = 1, maxLines = 1)
                OrdiaInput(description, { description = it }, modifier = Modifier.fillMaxWidth(), label = "Descripción", minLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProjectStatus.entries.forEach { value -> FilterChip(selected = status == value, onClick = { status = value }, label = { Text(value.label()) }) }
                }
            }
        },
        confirmButton = { OrdiaButton("Guardar", { onSave((existing ?: ProjectEntity(name = name)).copy(name = name, description = description, status = status)) }, enabled = name.isNotBlank()) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun HabitEditorDialog(existing: HabitEntity? = null, onDismiss: () -> Unit, onSave: (HabitEntity) -> Unit) {
    val context = LocalContext.current
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var details by remember(existing?.id) { mutableStateOf(existing?.details.orEmpty()) }
    var frequency by remember(existing?.id) { mutableStateOf(existing?.frequency ?: HabitFrequency.DAILY) }
    var target by remember(existing?.id) { mutableStateOf((existing?.targetPerPeriod ?: 1).toString()) }
    var reminderEnabled by remember(existing?.id) { mutableStateOf(existing?.reminderMinutes != null) }
    var reminderMinutes by remember(existing?.id) { mutableStateOf(existing?.reminderMinutes ?: 9 * 60) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Nuevo hábito" else "Editar hábito") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OrdiaInput(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = "Hábito", minLines = 1, maxLines = 1)
                OrdiaInput(details, { details = it }, modifier = Modifier.fillMaxWidth(), label = "Por qué es importante", minLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HabitFrequency.entries.forEach { value -> FilterChip(selected = frequency == value, onClick = { frequency = value }, label = { Text(value.label()) }) }
                }
                OrdiaInput(target, { target = it.filter(Char::isDigit).take(2) }, modifier = Modifier.fillMaxWidth(), label = "Meta por período", minLines = 1, maxLines = 1)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recordatorio diario", Modifier.weight(1f))
                    Switch(reminderEnabled, { reminderEnabled = it })
                }
                if (reminderEnabled) {
                    OutlinedButton(
                        onClick = {
                            val current = LocalTime.of((reminderMinutes / 60).coerceIn(0, 23), (reminderMinutes % 60).coerceIn(0, 59))
                            android.app.TimePickerDialog(context, { _, hour, minute -> reminderMinutes = hour * 60 + minute }, current.hour, current.minute, false).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Notifications, null)
                        Text(
                            "%02d:%02d".format(reminderMinutes / 60, reminderMinutes % 60),
                            Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
        },
        confirmButton = { OrdiaButton("Guardar", { onSave((existing ?: HabitEntity(title = title)).copy(title = title, details = details, frequency = frequency, targetPerPeriod = target.toIntOrNull()?.coerceIn(1, 20) ?: 1, reminderMinutes = if (reminderEnabled) reminderMinutes else null)) }, enabled = title.isNotBlank()) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun RoutineEditorDialog(existing: RoutineEntity? = null, existingSteps: List<String> = emptyList(), onDismiss: () -> Unit, onSave: (RoutineEntity, List<String>) -> Unit) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var description by remember(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    var steps by remember(existing?.id) { mutableStateOf(existingSteps.joinToString("\n")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Nueva rutina" else "Editar rutina") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OrdiaInput(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = "Nombre", minLines = 1, maxLines = 1)
                OrdiaInput(description, { description = it }, modifier = Modifier.fillMaxWidth(), label = "Descripción", minLines = 2)
                OrdiaInput(steps, { steps = it }, modifier = Modifier.fillMaxWidth(), label = "Pasos, uno por línea", minLines = 5)
            }
        },
        confirmButton = { OrdiaButton("Guardar", { onSave((existing ?: RoutineEntity(name = name)).copy(name = name, description = description), steps.lines()) }, enabled = name.isNotBlank()) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun TaskPriority.label() = when (this) {
    TaskPriority.LOW -> "Baja"
    TaskPriority.NORMAL -> "Normal"
    TaskPriority.HIGH -> "Alta"
    TaskPriority.URGENT -> "Urgente"
}
private fun RecurrenceFrequency.label() = when (this) {
    RecurrenceFrequency.NONE -> "No repetir"
    RecurrenceFrequency.DAILY -> "Cada día"
    RecurrenceFrequency.WEEKLY -> "Cada semana"
    RecurrenceFrequency.MONTHLY -> "Cada mes"
    RecurrenceFrequency.YEARLY -> "Cada año"
}
private fun ProjectStatus.label() = when (this) {
    ProjectStatus.ACTIVE -> "Activo"
    ProjectStatus.PAUSED -> "En pausa"
    ProjectStatus.COMPLETED -> "Completado"
}
private fun HabitFrequency.label() = when (this) {
    HabitFrequency.DAILY -> "Diario"
    HabitFrequency.WEEKLY -> "Semanal"
    HabitFrequency.MONTHLY -> "Mensual"
}
