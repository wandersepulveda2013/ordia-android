package com.ordia.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import com.ordia.app.data.local.ProjectEntity
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
    val priorityColor = priorityAccent(task.priority)
    Card(
        modifier = modifier.fillMaxWidth().combinedClickable(onClick = onEdit, onLongClick = { menuOpen = true }),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colored accent rail — encodes priority for quick scanning.
            Box(
                Modifier
                    .width(5.dp)
                    .height(46.dp)
                    .background(priorityColor, RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
            )
            Row(Modifier.fillMaxWidth().padding(end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = task.completed, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f).padding(start = 2.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        task.title,
                        modifier = Modifier.weight(1f, fill = false).animateContentSize(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (task.completed) FontWeight.Normal else FontWeight.Medium,
                        textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                    )
                    if (task.flagged) Icon(Icons.Outlined.Flag, "Marcada", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (task.dueAt != null) {
                        Icon(Icons.Outlined.Schedule, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${DateRules.formatDate(task.dueAt)} ${DateRules.formatTime(task.dueAt)}".trim(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (TaskRules.isOverdue(task)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text("Bandeja", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    project?.let { Text("· ${it.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (task.priority >= TaskPriority.HIGH) PriorityPill(if (task.priority == TaskPriority.URGENT) "Urgente" else "Alta")
                    subtaskProgress?.let { (done, total) -> if (total > 0) Text("$done/$total pasos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, "Más opciones") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Editar") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menuOpen = false; onEdit() })
                if (onDuplicate != null) DropdownMenuItem(text = { Text("Duplicar") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = { menuOpen = false; onDuplicate() })
                if (onDelete != null) {
                    androidx.compose.material3.HorizontalDivider()
                    DropdownMenuItem(text = { Text("Archivar") }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) }, onClick = { menuOpen = false; onDelete() })
                }
            }
            }
        }
    }
}

@Composable
fun PriorityPill(text: String) {
    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(text, Modifier.padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun priorityAccent(priority: TaskPriority): androidx.compose.ui.graphics.Color = when (priority) {
    TaskPriority.URGENT -> MaterialTheme.colorScheme.error
    TaskPriority.HIGH -> Color(0xFFC98A2B)
    TaskPriority.NORMAL -> MaterialTheme.colorScheme.outlineVariant
    TaskPriority.LOW -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
}
