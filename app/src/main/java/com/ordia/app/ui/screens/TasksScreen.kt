package com.ordia.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ordia.app.R
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.domain.TaskRules
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.EmptyState
import com.ordia.app.ui.components.InfoBanner
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.ui.components.SectionHeader
import com.ordia.app.ui.components.StatCard
import com.ordia.app.ui.components.TaskEditorDialog
import com.ordia.app.ui.components.TaskRow

enum class TaskFilter {
    PENDING,
    TODAY,
    OVERDUE,
    UPCOMING,
    FLAGGED,
    COMPLETED,
    ALL
}

enum class TaskSort {
    SMART,
    DATE,
    PRIORITY
}

@Composable
private fun TaskFilter.label(): String = when (this) {
    TaskFilter.PENDING -> stringResource(R.string.tasks_filter_pending)
    TaskFilter.TODAY -> stringResource(R.string.tasks_filter_today)
    TaskFilter.OVERDUE -> stringResource(R.string.tasks_filter_overdue)
    TaskFilter.UPCOMING -> stringResource(R.string.tasks_filter_upcoming)
    TaskFilter.FLAGGED -> stringResource(R.string.tasks_filter_flagged)
    TaskFilter.COMPLETED -> stringResource(R.string.tasks_filter_completed)
    TaskFilter.ALL -> stringResource(R.string.tasks_filter_all)
}

@Composable
private fun TaskSort.label(): String = when (this) {
    TaskSort.SMART -> stringResource(R.string.tasks_sort_smart)
    TaskSort.DATE -> stringResource(R.string.tasks_sort_date)
    TaskSort.PRIORITY -> stringResource(R.string.tasks_sort_priority)
}

@Composable
fun TasksScreen(
    state: OrdiaUiState,
    vm: OrdiaViewModel,
    contentPadding: PaddingValues,
    onTask: (Long) -> Unit
) {
    var filter by remember { mutableStateOf(TaskFilter.PENDING) }
    var sort by remember { mutableStateOf(TaskSort.SMART) }
    var query by rememberSaveable { mutableStateOf("") }
    var adding by rememberSaveable { mutableStateOf(false) }

    if (adding) {
        TaskEditorDialog(
            projects = state.projects,
            tags = state.tags,
            onAddTag = vm::addTag,
            onDismiss = { adding = false },
            onSave = { task, tags ->
                vm.saveTask(task, tags)
                adding = false
            }
        )
    }

    val now = System.currentTimeMillis()
    val filtered = state.rootTasks.filter { task ->
        !task.archived &&
            (query.isBlank() || task.title.contains(query, ignoreCase = true) || task.details.contains(query, ignoreCase = true)) &&
            when (filter) {
                TaskFilter.PENDING -> !task.completed
                TaskFilter.TODAY -> !task.completed && TaskRules.isDueToday(task)
                TaskFilter.OVERDUE -> !task.completed && TaskRules.isOverdue(task)
                TaskFilter.UPCOMING -> !task.completed && task.dueAt?.let { it > now && !TaskRules.isDueToday(task) } == true
                TaskFilter.FLAGGED -> !task.completed && task.flagged
                TaskFilter.COMPLETED -> task.completed
                TaskFilter.ALL -> true
            }
    }
    val shown = when (sort) {
        TaskSort.SMART -> filtered.sortedWith(
            compareBy<TaskEntity> { it.completed }
                .thenBy { it.dueAt ?: Long.MAX_VALUE }
                .thenByDescending { it.priority }
                .thenBy { it.title.lowercase() }
        )
        TaskSort.DATE -> filtered.sortedBy { it.dueAt ?: Long.MAX_VALUE }
        TaskSort.PRIORITY -> filtered.sortedWith(compareByDescending<TaskEntity> { it.priority }.thenBy { it.dueAt ?: Long.MAX_VALUE })
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            20.dp,
            contentPadding.calculateTopPadding() + 20.dp,
            20.dp,
            contentPadding.calculateBottomPadding() + 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                stringResource(R.string.tasks_eyebrow),
                stringResource(R.string.tasks_title),
                stringResource(R.string.tasks_subtitle),
                stringResource(R.string.tasks_action_new),
                onAction = { adding = true }
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    StatCard(
                        stringResource(R.string.tasks_filter_pending),
                        state.pendingCount.toString(),
                        stringResource(R.string.tasks_active_stat),
                        Modifier.width(170.dp),
                        icon = Icons.Outlined.Schedule
                    )
                }
                item {
                    StatCard(
                        stringResource(R.string.tasks_filter_overdue),
                        state.overdueTasks.size.toString(),
                        stringResource(R.string.tasks_undecided_stat),
                        Modifier.width(170.dp),
                        icon = Icons.Outlined.WarningAmber,
                        accent = androidx.compose.material3.MaterialTheme.colorScheme.error
                    )
                }
                item {
                    StatCard(
                        stringResource(R.string.tasks_filter_completed),
                        state.completedCount.toString(),
                        stringResource(R.string.tasks_historic_stat),
                        Modifier.width(170.dp),
                        icon = Icons.Outlined.CheckCircle,
                        accent = androidx.compose.material3.MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        if (state.overdueTasks.isNotEmpty()) {
            item {
                InfoBanner(
                    stringResource(R.string.tasks_overdue_banner, state.overdueTasks.size),
                    stringResource(R.string.tasks_overdue_banner_desc)
                )
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.tasks_search)) },
                placeholder = { Text(stringResource(R.string.tasks_search_placeholder)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.tasks_clear_search))
                        }
                    }
                },
                singleLine = true
            )
        }

        item {
            Text(stringResource(R.string.tasks_view), style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            LazyRow(
                modifier = Modifier.padding(top = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(TaskFilter.entries) { value ->
                    FilterChip(
                        selected = filter == value,
                        onClick = { filter = value },
                        label = { Text(value.label()) }
                    )
                }
            }
        }

        item {
            Text(stringResource(R.string.tasks_sort), style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            LazyRow(
                modifier = Modifier.padding(top = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(TaskSort.entries) { value ->
                    FilterChip(
                        selected = sort == value,
                        onClick = { sort = value },
                        label = { Text(value.label()) }
                    )
                }
            }
        }

        item {
            SectionHeader(
                title = filter.label(),
                supporting = if (query.isBlank()) stringResource(R.string.tasks_results, shown.size) else stringResource(R.string.tasks_results_query, shown.size, query)
            )
        }

        if (shown.isEmpty()) {
            item {
                EmptyState(
                    title = when {
                        query.isNotBlank() -> stringResource(R.string.tasks_empty_title_query)
                        filter == TaskFilter.COMPLETED -> stringResource(R.string.tasks_empty_title_completed)
                        filter == TaskFilter.OVERDUE -> stringResource(R.string.tasks_empty_title_overdue)
                        else -> stringResource(R.string.tasks_empty_title_other)
                    },
                    description = when {
                        query.isNotBlank() -> stringResource(R.string.tasks_empty_desc_query)
                        filter == TaskFilter.OVERDUE -> stringResource(R.string.tasks_empty_desc_overdue)
                        else -> stringResource(R.string.tasks_empty_desc_other)
                    },
                    actionLabel = if (query.isBlank()) stringResource(R.string.tasks_create_task) else null,
                    onAction = if (query.isBlank()) ({ adding = true }) else null
                )
            }
        } else {
            items(shown, key = { it.id }) { task ->
                TaskListItem(state, vm, task, onTask)
            }
        }
    }
}

@Composable
private fun TaskListItem(state: OrdiaUiState, vm: OrdiaViewModel, task: TaskEntity, onTask: (Long) -> Unit) {
    val subtasks = state.subtasks(task.id)
    TaskRow(
        task = task,
        project = state.project(task.projectId),
        subtaskProgress = subtasks.count { it.completed } to subtasks.size,
        onToggle = { vm.toggleTask(task) },
        onEdit = { onTask(task.id) },
        onDuplicate = { vm.duplicateTask(task) },
        onDelete = { vm.deleteTask(task) }
    )
}
