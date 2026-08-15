package com.ordia.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ordia.app.R
import com.ordia.app.data.local.ProjectEntity
import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.domain.DateRules
import com.ordia.app.domain.TaskRules

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskRow(
    task: TaskEntity,
    project: ProjectEntity? = null,
    subtaskProgress: Pair<Int, Int>? = null,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    val overdue = TaskRules.isOverdue(task)
    val accent = when {
        overdue -> MaterialTheme.colorScheme.error
        task.priority == TaskPriority.URGENT -> MaterialTheme.colorScheme.error
        task.priority >= TaskPriority.HIGH -> MaterialTheme.colorScheme.secondary
        task.completed -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onEdit, onLongClick = { menuOpen = true }),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (task.completed) {
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (task.completed) 0.dp else 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(4.dp).height(46.dp), contentAlignment = Alignment.Center) {
                Surface(Modifier.width(4.dp).height(38.dp), shape = CircleShape, color = accent) {}
            }
            Checkbox(checked = task.completed, onCheckedChange = { onToggle() })
            Column(
                Modifier.weight(1f).padding(start = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        task.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (task.completed) FontWeight.Normal else FontWeight.SemiBold,
                        textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    if (task.flagged) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Icon(
                                Icons.Outlined.Flag,
                                stringResource(R.string.task_flagged),
                                Modifier.padding(6.dp).size(14.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (task.dueAt != null) {
                        MetadataPill(
                            text = "${DateRules.formatDate(task.dueAt)} ${DateRules.formatTime(task.dueAt)}".trim(),
                            icon = Icons.Outlined.Schedule,
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            container = if (overdue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    } else {
                        MetadataPill(
                            text = stringResource(R.string.task_inbox),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            container = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    }
                    project?.let {
                        MetadataPill(
                            text = it.name,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            container = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                    if (task.priority >= TaskPriority.HIGH) {
                        PriorityPill(
                            stringResource(if (task.priority == TaskPriority.URGENT) R.string.dialog_priority_urgent else R.string.dialog_priority_high),
                            isUrgent = task.priority == TaskPriority.URGENT
                        )
                    }
                }

                subtaskProgress?.let { (done, total) ->
                    if (total > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            LinearProgressIndicator(
                                progress = { (done.toFloat() / total).coerceIn(0f, 1f) },
                                modifier = Modifier.weight(1f).height(5.dp),
                                color = accent,
                                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            Text(stringResource(R.string.task_subtask_progress, done, total), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Outlined.MoreVert, stringResource(R.string.task_more_options))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit)) },
                    leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                    onClick = { menuOpen = false; onEdit() }
                )
                if (onDuplicate != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.task_duplicate)) },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                        onClick = { menuOpen = false; onDuplicate() }
                    )
                }
                if (onDelete != null) {
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.task_archive)) },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataPill(
    text: String,
    color: Color,
    container: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Surface(shape = RoundedCornerShape(999.dp), color = container) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) Icon(icon, null, Modifier.size(13.dp), tint = color)
            Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun PriorityPill(text: String, isUrgent: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (isUrgent) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (isUrgent) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

/**
 * Chips de revisión de una captura interpretada (captura universal).
 *
 * Muestra los datos que el analizador local entendió (fecha, recordatorio,
 * duración, repetición) y, opcionalmente, un aviso de revisión cuando la
 * captura aterrizó en la Bandeja.
 */
@Composable
fun CaptureChips(
    chips: List<String>,
    hint: String? = null,
    modifier: Modifier = Modifier
) {
    if (chips.isEmpty() && hint == null) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (chips.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.take(4).forEach { text ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text,
                            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        hint?.let { text ->
            Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    text,
                    Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

/** Etiqueta legible de una repetición para chips de captura (o null si no repite). */
@Composable
fun recurrenceChipLabel(frequency: RecurrenceFrequency, interval: Int, days: String): String? = when (frequency) {
    RecurrenceFrequency.NONE -> null
    RecurrenceFrequency.DAILY ->
        if (interval <= 1) stringResource(R.string.capture_chip_recurrence_daily_1)
        else stringResource(R.string.capture_chip_recurrence_daily_n, interval)
    RecurrenceFrequency.WEEKLY -> {
        val dayNumbers = days.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .distinct()
            .sorted()
        when {
            dayNumbers.isEmpty() ->
                if (interval <= 1) stringResource(R.string.capture_chip_recurrence_weekly)
                else stringResource(R.string.capture_chip_recurrence_weekly_n, interval)
            else -> {
                val dayLabels = mutableListOf<String>()
                for (day in dayNumbers) dayLabels += stringResource(shortDayRes(day))
                stringResource(R.string.capture_chip_recurrence_weekly_days, dayLabels.joinToString(", "))
            }
        }
    }
    RecurrenceFrequency.MONTHLY ->
        if (interval <= 1) stringResource(R.string.capture_chip_recurrence_monthly_1)
        else stringResource(R.string.capture_chip_recurrence_monthly_n, interval)
    RecurrenceFrequency.YEARLY ->
        if (interval <= 1) stringResource(R.string.capture_chip_recurrence_yearly_1)
        else stringResource(R.string.capture_chip_recurrence_yearly_n, interval)
    RecurrenceFrequency.HOURLY ->
        if (interval <= 1) stringResource(R.string.capture_chip_recurrence_hourly_1)
        else stringResource(R.string.capture_chip_recurrence_hourly_n, interval)
}

private fun shortDayRes(iso: Int): Int = when (iso) {
    1 -> R.string.day_short_mon
    2 -> R.string.day_short_tue
    3 -> R.string.day_short_wed
    4 -> R.string.day_short_thu
    5 -> R.string.day_short_fri
    6 -> R.string.day_short_sat
    else -> R.string.day_short_sun
}
