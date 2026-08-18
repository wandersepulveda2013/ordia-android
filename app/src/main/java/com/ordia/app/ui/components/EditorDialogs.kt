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
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.AlertDialog
import com.ordia.app.ui.components.OrdiaButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import com.ordia.app.ui.components.OrdiaOutlinedButton
import com.ordia.app.ui.components.OrdiaInput
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ordia.app.R
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
    val reminderLabel = stringResource(R.string.dialog_task_reminder)
    val importantLabel = stringResource(R.string.dialog_task_important)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (existing == null) stringResource(R.string.dialog_task_new) else stringResource(R.string.dialog_task_edit))
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OrdiaInput(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.external_suggestion_title_hint)) }, singleLine = true)
                OrdiaInput(details, { details = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.dialog_task_details)) }, minLines = 2, maxLines = 4)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OrdiaOutlinedButton(
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
                        Icon(Icons.Outlined.CalendarMonth, stringResource(R.string.dialog_change_date))
                        Text(dueAt?.let { DateRules.formatDate(it) } ?: stringResource(R.string.dialog_task_date), Modifier.padding(start = 6.dp))
                    }
                    OrdiaOutlinedButton(
                        onClick = {
                            val current = dueAt?.let { DateRules.toLocalTime(it) } ?: LocalTime.of(9, 0)
                            TimePickerDialog(context, { _, hour, minute ->
                                val date = dueAt?.let { DateRules.toLocalDate(it) } ?: LocalDate.now()
                                dueAt = DateRules.toEpochMillis(date, LocalTime.of(hour, minute))
                            }, current.hour, current.minute, false).show()
                        },
                        enabled = dueAt != null,
                        modifier = Modifier.weight(1f)
                    ) { Text(dueAt?.let { DateRules.formatTime(it) } ?: stringResource(R.string.dialog_task_time)) }
                }
                if (dueAt != null) TextButton(onClick = { dueAt = null; reminderEnabled = false }) { Text(stringResource(R.string.dialog_task_clear_date)) }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        OrdiaOutlinedButton(onClick = { priorityMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Flag, stringResource(R.string.dialog_change_priority))
                            Text(priority.label(), Modifier.padding(start = 6.dp))
                        }
                        DropdownMenu(priorityMenu, { priorityMenu = false }) {
                            TaskPriority.entries.forEach { value -> DropdownMenuItem(text = { Text(value.label()) }, onClick = { priority = value; priorityMenu = false }) }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        OrdiaOutlinedButton(onClick = { projectMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(projects.firstOrNull { it.id == projectId }?.name ?: stringResource(R.string.dialog_project_none))
                        }
                        DropdownMenu(projectMenu, { projectMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.dialog_project_none)) }, onClick = { projectId = null; projectMenu = false })
                            projects.forEach { project -> DropdownMenuItem(text = { Text(project.name) }, onClick = { projectId = project.id; projectMenu = false }) }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        OrdiaOutlinedButton(onClick = { recurrenceMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Repeat, stringResource(R.string.dialog_change_recurrence))
                            Text(recurrence.label(), Modifier.padding(start = 6.dp))
                        }
                        DropdownMenu(recurrenceMenu, { recurrenceMenu = false }) {
                            RecurrenceFrequency.entries.forEach { value -> DropdownMenuItem(text = { Text(value.label()) }, onClick = { recurrence = value; recurrenceMenu = false }) }
                        }
                    }
                    OrdiaInput(
                        value = duration,
                        onValueChange = { duration = it.filter(Char::isDigit).take(3) },
                        modifier = Modifier.weight(0.7f),
                        label = { Text(stringResource(R.string.dialog_task_minutes)) },
                        singleLine = true
                    )
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(reminderLabel, Modifier.weight(1f))
                    Switch(
                        reminderEnabled,
                        { reminderEnabled = it },
                        enabled = dueAt != null,
                        modifier = Modifier.semantics { contentDescription = reminderLabel }
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(importantLabel, Modifier.weight(1f))
                    Switch(
                        flagged,
                        { flagged = it },
                        modifier = Modifier.semantics { contentDescription = importantLabel }
                    )
                }

                Text(stringResource(R.string.dialog_task_tags))
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
                    OrdiaInput(
                        value = newTag,
                        onValueChange = { newTag = it.take(30) },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.dialog_task_new_tag)) },
                        singleLine = true
                    )
                    TextButton(
                        onClick = { onAddTag(newTag); newTag = "" },
                        enabled = newTag.isNotBlank() && tags.none { it.name.equals(newTag.trim(), true) }
                    ) { Text(stringResource(R.string.action_add)) }
                }
            }
        },
        confirmButton = {
            OrdiaButton(
                onClick = {
                    val normalizedDue = dueAt
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
                    onSave(task, chosenTags)
                },
                enabled = title.isNotBlank()
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
fun ProjectEditorDialog(existing: ProjectEntity? = null, onDismiss: () -> Unit, onSave: (ProjectEntity) -> Unit) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var description by remember(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    var status by remember(existing?.id) { mutableStateOf(existing?.status ?: ProjectStatus.ACTIVE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) stringResource(R.string.dialog_project_new) else stringResource(R.string.dialog_project_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OrdiaInput(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.dialog_field_name)) }, singleLine = true)
                OrdiaInput(description, { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.dialog_field_description)) }, minLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProjectStatus.entries.forEach { value -> FilterChip(selected = status == value, onClick = { status = value }, label = { Text(value.label()) }) }
                }
            }
        },
        confirmButton = { OrdiaButton(onClick = { onSave((existing ?: ProjectEntity(name = name)).copy(name = name, description = description, status = status)) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
fun HabitEditorDialog(existing: HabitEntity? = null, onDismiss: () -> Unit, onSave: (HabitEntity) -> Unit) {
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var details by remember(existing?.id) { mutableStateOf(existing?.details.orEmpty()) }
    var frequency by remember(existing?.id) { mutableStateOf(existing?.frequency ?: HabitFrequency.DAILY) }
    var target by remember(existing?.id) { mutableStateOf((existing?.targetPerPeriod ?: 1).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) stringResource(R.string.dialog_habit_new) else stringResource(R.string.dialog_habit_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OrdiaInput(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.dialog_habit_title)) }, singleLine = true)
                OrdiaInput(details, { details = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.dialog_habit_why)) }, minLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HabitFrequency.entries.forEach { value -> FilterChip(selected = frequency == value, onClick = { frequency = value }, label = { Text(value.label()) }) }
                }
                OrdiaInput(target, { target = it.filter(Char::isDigit).take(2) }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.dialog_habit_target)) }, singleLine = true)
            }
        },
        confirmButton = { OrdiaButton(onClick = { onSave((existing ?: HabitEntity(title = title)).copy(title = title, details = details, frequency = frequency, targetPerPeriod = target.toIntOrNull()?.coerceIn(1, 20) ?: 1)) }, enabled = title.isNotBlank()) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
fun RoutineEditorDialog(existing: RoutineEntity? = null, existingSteps: List<String> = emptyList(), onDismiss: () -> Unit, onSave: (RoutineEntity, List<String>) -> Unit) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var description by remember(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    var steps by remember(existing?.id) { mutableStateOf(existingSteps.joinToString("\n")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) stringResource(R.string.dialog_routine_new) else stringResource(R.string.dialog_routine_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OrdiaInput(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.dialog_field_name)) }, singleLine = true)
                OrdiaInput(description, { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.dialog_field_description)) }, minLines = 2)
                OrdiaInput(steps, { steps = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.dialog_routine_steps)) }, minLines = 5)
            }
        },
        confirmButton = { OrdiaButton(onClick = { onSave((existing ?: RoutineEntity(name = name)).copy(name = name, description = description), steps.lines()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun TaskPriority.label(): String = stringResource(
    when (this) {
        TaskPriority.LOW -> R.string.dialog_priority_low
        TaskPriority.NORMAL -> R.string.dialog_priority_normal
        TaskPriority.HIGH -> R.string.dialog_priority_high
        TaskPriority.URGENT -> R.string.dialog_priority_urgent
    }
)

@Composable
private fun RecurrenceFrequency.label(): String = stringResource(
    when (this) {
        RecurrenceFrequency.NONE -> R.string.dialog_recurrence_none
        RecurrenceFrequency.DAILY -> R.string.dialog_recurrence_daily
        RecurrenceFrequency.WEEKLY -> R.string.dialog_recurrence_weekly
        RecurrenceFrequency.MONTHLY -> R.string.dialog_recurrence_monthly
        RecurrenceFrequency.YEARLY -> R.string.dialog_recurrence_yearly
    }
)

@Composable
private fun ProjectStatus.label(): String = stringResource(
    when (this) {
        ProjectStatus.ACTIVE -> R.string.dialog_project_status_active
        ProjectStatus.PAUSED -> R.string.dialog_project_status_paused
        ProjectStatus.COMPLETED -> R.string.dialog_project_status_completed
    }
)

@Composable
private fun HabitFrequency.label(): String = stringResource(
    when (this) {
        HabitFrequency.DAILY -> R.string.dialog_habit_frequency_daily
        HabitFrequency.WEEKLY -> R.string.dialog_habit_frequency_weekly
        HabitFrequency.MONTHLY -> R.string.dialog_habit_frequency_monthly
    }
)
