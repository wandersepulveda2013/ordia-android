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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.ordia.app.ui.components.OrdiaOutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ordia.app.R
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.ProjectEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import com.ordia.app.domain.OrganizeActionsEngine
import com.ordia.app.domain.TaskRules
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.OrdiaCard
import com.ordia.app.ui.components.OrdiaEmptyState
import com.ordia.app.ui.components.OrdiaEyebrow
import com.ordia.app.ui.components.ScreenHeader

/**
 * Hub de Organización 2026: reúne tareas, notas, listas y proyectos en una
 * vista escaneable, con atajos de IA (organización reversible).
 */
@Composable
fun OrganizeScreen(
    state: OrdiaUiState,
    vm: OrdiaViewModel,
    padding: PaddingValues,
    onTask: (Long) -> Unit,
    onNote: (Long) -> Unit,
    onProject: (Long) -> Unit,
    onInbox: () -> Unit,
    onPlanner: () -> Unit,
    onAutomations: () -> Unit
) {
    val tasks = state.pendingTasks.take(5)
    val inboxCount = state.inboxTasks.size
    val overdueCount = state.overdueTasks.size
    val notes = state.notes.filter { !it.archived }.sortedByDescending { it.updatedAt }.take(4)
    val projects = state.projects.filter { !it.archived }.take(4)
    val lists = remember(state.tasks) {
        state.tasks.filter { it.status == TaskStatus.INBOX && !it.archived }
            .groupBy { it.projectId }
            .filter { it.value.size >= 3 }
            .entries.take(3)
    }
    var showProposal by remember { mutableStateOf(false) }
    val proposal = remember(state.tasks, showProposal) {
        if (showProposal) OrganizeActionsEngine.proposeWeek(state.tasks) else null
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = padding.calculateTopPadding() + 20.dp,
            end = 20.dp,
            bottom = padding.calculateBottomPadding() + 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                eyebrow = stringResource(R.string.organize_subtitle),
                title = stringResource(R.string.organize_title),
                subtitle = when {
                    overdueCount > 0 -> stringResource(R.string.today_subtitle_overdue, overdueCount)
                    inboxCount > 0 -> stringResource(R.string.organize_subtitle)
                    else -> stringResource(R.string.empty_pending)
                }
            )
        }

        item {
            OrdiaCard {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                if (proposal != null) stringResource(R.string.organize_ai_propose, proposal.count)
                                else stringResource(R.string.organize_ai_working),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                stringResource(R.string.organize_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (proposal == null) {
                            OrdiaOutlinedButton(onClick = { showProposal = true }) {
                                Text(stringResource(R.string.organize_ai_review))
                            }
                        }
                    }
                    if (proposal != null && !proposal.isEmpty) {
                        proposal.changes.take(6).forEach { change ->
                            Text(
                                "• ${change.summary}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OrdiaOutlinedButton(onClick = { showProposal = false }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.organize_ai_undo))
                            }
                        }
                    } else if (proposal != null && proposal.isEmpty) {
                        Text(
                            stringResource(R.string.empty_pending),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            OrganizeSectionRow(
                icon = Icons.Outlined.CheckCircle,
                title = stringResource(R.string.organize_section_tasks),
                count = state.pendingCount,
                action = if (inboxCount > 0) stringResource(R.string.today_inbox_badge, inboxCount) else null,
                onAction = if (inboxCount > 0) onInbox else null
            )
        }
        if (tasks.isEmpty()) {
            item {
                OrdiaEmptyState(
                    title = stringResource(R.string.empty_pending),
                    message = stringResource(R.string.empty_inbox),
                    icon = Icons.Outlined.CheckCircle
                )
            }
        } else {
            items(tasks, key = { "org-task-${it.id}" }) { task ->
                OrganizeTaskRow(task, state.project(task.projectId)?.name, onTask)
            }
        }

        item {
            OrganizeSectionRow(
                icon = Icons.Outlined.Description,
                title = stringResource(R.string.organize_section_notes),
                count = notes.size,
                action = null,
                onAction = null
            )
        }
        if (notes.isEmpty()) {
            item {
                OrdiaEmptyState(
                    title = stringResource(R.string.notes_empty_title),
                    message = stringResource(R.string.notes_empty_desc),
                    icon = Icons.Outlined.Description
                )
            }
        } else {
            items(notes, key = { "org-note-${it.id}" }) { note ->
                OrganizeNoteRow(note, onNote)
            }
        }

        if (lists.isNotEmpty()) {
            item {
                OrganizeSectionRow(
                    icon = Icons.Outlined.Inbox,
                    title = stringResource(R.string.organize_section_lists),
                    count = lists.size,
                    action = null,
                    onAction = null
                )
            }
            items(lists, key = { "org-list-${it.key ?: 0L}" }) { (projectId, listTasks) ->
                val projectName = state.projects.firstOrNull { it.id == projectId }?.name
                    ?: stringResource(R.string.organize_section_lists)
                OrdiaCard {
                    OrdiaEyebrow(projectName)
                    Text(
                        stringResource(R.string.briefing_important, listTasks.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    listTasks.take(3).forEach { task ->
                        Text(
                            "• ${task.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (listTasks.size > 3) {
                        Text(
                            stringResource(R.string.briefing_appointments, listTasks.size - 3),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            OrganizeSectionRow(
                icon = Icons.Outlined.Folder,
                title = stringResource(R.string.organize_section_projects),
                count = projects.size,
                action = stringResource(R.string.nav_planner),
                onAction = onPlanner
            )
        }
        if (projects.isEmpty()) {
            item {
                OrdiaEmptyState(
                    title = stringResource(R.string.notes_empty_title),
                    message = stringResource(R.string.organize_subtitle),
                    icon = Icons.Outlined.Folder
                )
            }
        } else {
            items(projects, key = { "org-proj-${it.id}" }) { project ->
                OrganizeProjectRow(project, state, onProject)
            }
        }
    }
}

@Composable
private fun OrganizeSectionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    count: Int,
    action: String?,
    onAction: (() -> Unit)?
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (action != null && onAction != null) {
            OrdiaOutlinedButton(onClick = onAction, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Text(action, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun OrganizeTaskRow(task: TaskEntity, projectName: String?, onTask: (Long) -> Unit) {
    OrdiaCard(modifier = Modifier.padding()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                projectName?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OrdiaOutlinedButton(onClick = { onTask(task.id) }) {
                Icon(Icons.Outlined.MoreHoriz, null)
            }
        }
    }
}

@Composable
private fun OrganizeNoteRow(note: NoteEntity, onNote: (Long) -> Unit) {
    OrdiaCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    note.title.ifBlank { stringResource(R.string.notes_empty_body) },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    note.body.take(100),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OrdiaOutlinedButton(onClick = { onNote(note.id) }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
            }
        }
    }
}

@Composable
private fun OrganizeProjectRow(
    project: ProjectEntity,
    state: OrdiaUiState,
    onProject: (Long) -> Unit
) {
    val taskCount = state.tasks.count { it.projectId == project.id && !it.archived && it.status != TaskStatus.CANCELLED }
    OrdiaCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    project.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    stringResource(R.string.briefing_important, taskCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OrdiaOutlinedButton(onClick = { onProject(project.id) }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
            }
        }
    }
}
