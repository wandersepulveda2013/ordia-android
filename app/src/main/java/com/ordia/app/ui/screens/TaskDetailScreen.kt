package com.ordia.app.ui.screens

import android.content.Intent
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ordia.app.R
import com.ordia.app.data.local.AttachmentEntity
import com.ordia.app.data.local.AttachmentOwnerType
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.domain.DateRules
import com.ordia.app.domain.SubtaskRules
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.EmptyState
import com.ordia.app.ui.components.PriorityPill
import com.ordia.app.ui.components.TaskEditorDialog
import com.ordia.app.ui.components.TaskRow

@Composable
fun TaskDetailScreen(
    state: OrdiaUiState,
    vm: OrdiaViewModel,
    taskId: Long,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onTask: (Long) -> Unit
) {
    val context = LocalContext.current
    val fallbackAttachmentName = stringResource(R.string.capture_attachment_name)
    val task = state.task(taskId)
    var editing by remember(taskId) { mutableStateOf(false) }
    var subtaskText by rememberSaveable(taskId) { mutableStateOf("") }
    if (task == null) {
        Column(Modifier.fillMaxSize().padding(contentPadding).padding(20.dp)) {
            EmptyState(stringResource(R.string.task_detail_unavailable), stringResource(R.string.task_detail_unavailable_desc), stringResource(R.string.task_detail_volver), onBack)
        }
        return
    }
    if (editing) {
        TaskEditorDialog(
            existing = task,
            projects = state.projects,
            tags = state.tags,
            onAddTag = vm::addTag,
            selectedTagIds = state.tagsForTask(task.id).map { it.id }.toSet(),
            onDismiss = { editing = false },
            onSave = { updated, tags -> vm.saveTask(updated, tags); editing = false }
        )
    }
    val subtasks = state.subtasks(task.id)
    val attachments = state.attachmentsFor(AttachmentOwnerType.TASK, task.id)
    val pickAttachment = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val accessKept = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            if (!accessKept) {
                Toast.makeText(context, R.string.task_attachment_access_failed, Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            var displayName = uri.lastPathSegment ?: fallbackAttachmentName
            var sizeBytes = 0L
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) displayName = cursor.getString(nameIndex) ?: displayName
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) sizeBytes = cursor.getLong(sizeIndex)
                }
            }
            vm.addAttachment(
                AttachmentEntity(
                    ownerType = AttachmentOwnerType.TASK,
                    ownerId = task.id,
                    uri = uri.toString(),
                    displayName = displayName,
                    mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream",
                    sizeBytes = sizeBytes
                )
            )
        }
    }
    val canAddSubtask = remember(state.tasks, task.id) {
        SubtaskRules.canAddSubtask(task, state.tasks.associateBy { it.id })
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, contentPadding.calculateTopPadding() + 12.dp, 20.dp, contentPadding.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, stringResource(R.string.task_detail_volver)) }
                Text(stringResource(R.string.task_detail_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit)) }
                IconButton(onClick = { vm.deleteTask(task); onBack() }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.task_detail_archive)) }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(task.title, style = MaterialTheme.typography.headlineLarge)
                if (task.details.isNotBlank()) Text(task.details, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PriorityPill(taskPriorityLabel(task.priority))
                    Text(state.project(task.projectId)?.name ?: stringResource(R.string.task_detail_no_project), style = MaterialTheme.typography.bodyMedium)
                    Text(DateRules.formatDate(task.dueAt), style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = { vm.toggleTask(task) }) { Text(if (task.completed) stringResource(R.string.task_detail_mark_pending) else stringResource(R.string.task_detail_complete)) }
            }
        }
        item { Text(stringResource(R.string.task_detail_steps), style = MaterialTheme.typography.titleLarge) }
        if (canAddSubtask) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(subtaskText, { subtaskText = it }, modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.task_detail_add_subtask)) }, singleLine = true)
                    IconButton(onClick = { vm.addSubtask(task, subtaskText); subtaskText = "" }, enabled = subtaskText.isNotBlank()) { Icon(Icons.Outlined.Add, stringResource(R.string.task_detail_add_subtask)) }
                }
            }
        } else {
            item {
                Text(stringResource(R.string.subtask_max_depth), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (subtasks.isEmpty()) item { Text(stringResource(R.string.task_detail_no_subtasks), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(subtasks, key = { it.id }) { subtask ->
            TaskRow(
                task = subtask,
                onToggle = { vm.toggleTask(subtask) },
                onEdit = { onTask(subtask.id) },
                onDelete = { vm.deleteTask(subtask) }
            )
        }
        if (state.tagsForTask(task.id).isNotEmpty()) {
            item { Text(stringResource(R.string.task_detail_tags, state.tagsForTask(task.id).joinToString { it.name }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.task_attachments_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { pickAttachment.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Outlined.AttachFile, null)
                    Text(stringResource(R.string.task_attachment_add), Modifier.padding(start = 6.dp))
                }
            }
        }
        if (attachments.isEmpty()) {
            item { Text(stringResource(R.string.task_attachments_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(attachments, key = { "attachment-${it.id}" }) { attachment ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.InsertDriveFile, null, modifier = Modifier.size(22.dp))
                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW)
                            .setDataAndType(android.net.Uri.parse(attachment.uri), attachment.mimeType)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        runCatching { context.startActivity(intent) }.onFailure {
                            Toast.makeText(context, R.string.task_attachment_open_failed, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(attachment.displayName, maxLines = 1)
                        Text(
                            android.text.format.Formatter.formatShortFileSize(context, attachment.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { vm.deleteAttachment(attachment) }) {
                    Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.task_attachment_remove, attachment.displayName))
                }
            }
        }
    }
}

@Composable
private fun taskPriorityLabel(priority: TaskPriority): String = stringResource(
    when (priority) {
        TaskPriority.LOW -> R.string.dialog_priority_low
        TaskPriority.NORMAL -> R.string.dialog_priority_normal
        TaskPriority.HIGH -> R.string.dialog_priority_high
        TaskPriority.URGENT -> R.string.dialog_priority_urgent
    }
)
