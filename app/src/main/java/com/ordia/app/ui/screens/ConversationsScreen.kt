package com.ordia.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.core.app.NotificationManagerCompat
import com.ordia.app.BuildConfig
import com.ordia.app.R
import com.ordia.app.conversations.ChatImportParser
import com.ordia.app.conversations.CommitmentEngine
import com.ordia.app.conversations.ConversationPreview
import com.ordia.app.conversations.ConversationPrivacyPolicy
import com.ordia.app.conversations.ConversationSummaryEngine
import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.CommitmentOwner
import com.ordia.app.data.local.ConversationEntity
import com.ordia.app.data.local.ConversationSourceType
import com.ordia.app.data.local.ConsentEventEntity
import com.ordia.app.data.local.ConsentEventType
import com.ordia.app.data.local.ObservedSourceEntity
import com.ordia.app.domain.DateRules
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.ObservationRuntimeState
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.ui.components.SectionHeader
import java.io.Reader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ConversationsScreen(
    vm: OrdiaViewModel,
    padding: PaddingValues,
    onTask: (Long) -> Unit
) {
    val context = LocalContext.current
    val importDefaultName = stringResource(R.string.conversation_import_default_name)
    val importReadError = stringResource(R.string.conversation_import_read_error)
    val parseFailed = stringResource(R.string.conversation_parse_failed)
    val pastedName = stringResource(R.string.conversation_pasted_name)
    val scope = rememberCoroutineScope()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val commitments by vm.commitments.collectAsStateWithLifecycle()
    val pending by vm.pendingCommitments.collectAsStateWithLifecycle()
    val sharedPreview by vm.sharedConversationPreview.collectAsStateWithLifecycle()
    val observedSources by vm.observedSources.collectAsStateWithLifecycle()
    val consentHistory by vm.consentHistory.collectAsStateWithLifecycle()
    val observationRuntime by vm.observationRuntime.collectAsStateWithLifecycle()
    var preview by remember { mutableStateOf<ConversationPreview?>(null) }
    var previewSource by remember { mutableStateOf(ConversationSourceType.IMPORTED) }
    var selfParticipant by remember { mutableStateOf<String?>(null) }
    var retainOriginal by remember { mutableStateOf(false) }
    var showPaste by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }
    var parseError by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var showClearObservedData by remember { mutableStateOf(false) }
    var listenerGranted by remember {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val granted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        if (granted != listenerGranted) {
            listenerGranted = granted
            vm.recordNotificationPermissionState(granted)
        }
    }

    LaunchedEffect(sharedPreview?.contentHash) {
        sharedPreview?.let {
            preview = it
            previewSource = ConversationSourceType.SHARED
            selfParticipant = null
            retainOriginal = false
        }
    }

    fun setPreview(value: ConversationPreview, source: ConversationSourceType) {
        preview = value
        previewSource = source
        selfParticipant = null
        retainOriginal = false
        parseError = null
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val name = context.contentResolver.query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: importDefaultName
                    val raw = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                            readBounded(it, ChatImportParser.MAX_IMPORT_CHARS)
                        } ?: error(importReadError)
                    }
                    withContext(Dispatchers.Default) { ChatImportParser.parse(raw, name) }
                }.onSuccess { setPreview(it, ConversationSourceType.IMPORTED) }
                    .onFailure { parseError = it.message ?: parseFailed }
            }
        }
    }

    if (showPaste) {
        AlertDialog(
            onDismissRequest = { showPaste = false },
            title = { Text(stringResource(R.string.conversation_paste_title)) },
            text = {
                OutlinedTextField(
                    value = pasteText,
                    onValueChange = { pasteText = it.take(ChatImportParser.MAX_IMPORT_CHARS) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 8,
                    label = { Text(stringResource(R.string.conversation_paste_label)) }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = pasteText.isNotBlank(),
                    onClick = {
                        runCatching { ChatImportParser.parse(pasteText, pastedName) }
                            .onSuccess {
                                setPreview(it, ConversationSourceType.SHARED)
                                showPaste = false
                            }
                            .onFailure { parseError = it.message }
                    }
                ) { Text(stringResource(R.string.conversation_analyze)) }
            },
            dismissButton = {
                TextButton(onClick = { showPaste = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    deleteTarget?.let { conversation ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.conversation_delete_title)) },
            text = { Text(stringResource(R.string.conversation_delete_body, conversation.title)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteConversation(conversation.id)
                    deleteTarget = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showClearObservedData) {
        AlertDialog(
            onDismissRequest = { showClearObservedData = false },
            title = { Text(stringResource(R.string.observation_clear_dialog_title)) },
            text = { Text(stringResource(R.string.observation_clear_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearObservedConversationData()
                    showClearObservedData = false
                }) { Text(stringResource(R.string.observation_clear_data)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearObservedData = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = padding.calculateTopPadding() + 20.dp,
            end = 20.dp,
            bottom = padding.calculateBottomPadding() + 40.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ScreenHeader(
                stringResource(R.string.conversation_eyebrow),
                stringResource(R.string.conversation_title),
                stringResource(R.string.conversation_subtitle)
            )
        }
        item {
            ObservationControlCard(
                available = BuildConfig.CONTEXT_NOTIFICATION_ACCESS_ENABLED,
                runtime = observationRuntime,
                listenerGranted = listenerGranted,
                onEnabled = vm::setObservationEnabled,
                onPermission = {
                    vm.recordNotificationPermissionReviewed()
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                onPause = vm::pauseObservationOneHour,
                onResume = vm::resumeObservation,
                onStop = {
                    vm.revokeObservationInternally()
                    if (listenerGranted) {
                        vm.recordNotificationPermissionReviewed()
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                },
                onClear = { showClearObservedData = true }
            )
        }
        if (BuildConfig.CONTEXT_NOTIFICATION_ACCESS_ENABLED) {
            item {
                SectionHeader(
                    stringResource(R.string.observation_sources_title),
                    stringResource(R.string.observation_sources_subtitle)
                )
            }
            items(OBSERVATION_APPS, key = { "source-${it.packageName}" }) { appOption ->
                val source = observedSources.firstOrNull { it.packageName == appOption.packageName }
                ObservationSourceCard(
                    app = appOption,
                    source = source,
                    onEnabled = { enabled ->
                        vm.configureObservedSource(appOption.packageName, appOption.label, enabled)
                    }
                )
            }
            item {
                ConsentHistoryCard(consentHistory.take(8))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { picker.launch(arrayOf("text/*", "application/json")) }) {
                    Icon(Icons.Outlined.FileOpen, null)
                    Text(stringResource(R.string.conversation_import), Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    pasteText = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    showPaste = true
                }) {
                    Icon(Icons.Outlined.ContentPaste, null)
                    Text(stringResource(R.string.conversation_paste), Modifier.padding(start = 8.dp))
                }
            }
        }

        parseError?.let { error ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(error, Modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        preview?.let { value ->
            item {
                ConversationPreviewCard(
                    preview = value,
                    selfParticipant = selfParticipant,
                    retainOriginal = retainOriginal,
                    onSelfParticipant = { selfParticipant = it },
                    onRetainOriginal = { retainOriginal = it },
                    onSave = {
                        vm.saveConversationPreview(
                            preview = value,
                            retainOriginal = retainOriginal,
                            selfParticipant = selfParticipant,
                            sourceType = previewSource
                        ) { saved ->
                            if (saved) {
                                preview = null
                                pasteText = ""
                            }
                        }
                    },
                    onCancel = {
                        preview = null
                        pasteText = ""
                        vm.clearSharedConversationPreview()
                    }
                )
            }
        }

        item {
            SectionHeader(
                stringResource(R.string.commitment_pending_title),
                pluralStringResource(R.plurals.commitment_pending_subtitle, pending.size, pending.size)
            )
        }
        if (pending.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ChatBubbleOutline, null)
                        Text(stringResource(R.string.commitment_empty), Modifier.padding(start = 12.dp))
                    }
                }
            }
        } else {
            items(pending, key = { "pending-${it.id}" }) { commitment ->
                CommitmentCard(
                    commitment = commitment,
                    onConvert = { vm.convertCommitmentToTask(commitment.id) },
                    onDismiss = { vm.dismissCommitment(commitment.id) }
                )
            }
        }

        item {
            SectionHeader(
                stringResource(R.string.conversation_history_title),
                stringResource(R.string.conversation_history_subtitle)
            )
        }
        if (conversations.isEmpty()) {
            item { Text(stringResource(R.string.conversation_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(conversations, key = { "conversation-${it.id}" }) { conversation ->
                val related = commitments.filter { it.conversationId == conversation.id }
                ConversationHistoryCard(
                    conversation = conversation,
                    commitmentCount = related.size,
                    convertedTaskIds = related.mapNotNull { it.resultTaskId },
                    onTask = onTask,
                    onDelete = { deleteTarget = conversation }
                )
            }
        }
    }
}

@Composable
private fun ObservationControlCard(
    available: Boolean,
    runtime: ObservationRuntimeState,
    listenerGranted: Boolean,
    onEnabled: (Boolean) -> Unit,
    onPermission: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Security, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(stringResource(R.string.observation_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            if (available) R.string.observation_subtitle else R.string.observation_unavailable
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (available) {
                    Switch(checked = runtime.enabled, onCheckedChange = onEnabled)
                }
            }
            if (!available) return@Column

            val status = when {
                !runtime.enabled -> stringResource(R.string.observation_status_off)
                runtime.paused -> stringResource(R.string.observation_status_paused)
                !listenerGranted -> stringResource(R.string.observation_status_permission_required)
                else -> stringResource(R.string.observation_status_active)
            }
            Text(status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.observation_only_commitments),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedButton(onClick = onPermission) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null)
                        Text(stringResource(R.string.observation_permission), Modifier.padding(start = 6.dp))
                    }
                }
                item {
                    OutlinedButton(onClick = if (runtime.paused) onResume else onPause, enabled = runtime.enabled) {
                        Icon(if (runtime.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, null)
                        Text(
                            stringResource(
                                if (runtime.paused) R.string.observation_resume else R.string.observation_pause
                            ),
                            Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
            OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.observation_clear_data))
            }
            TextButton(onClick = onStop, modifier = Modifier.fillMaxWidth(), enabled = runtime.enabled) {
                Text(stringResource(R.string.observation_stop))
            }
            if (listenerGranted) {
                Text(
                    stringResource(R.string.observation_android_revoke_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ObservationSourceCard(
    app: ObservationApp,
    source: ObservedSourceEntity?,
    onEnabled: (Boolean) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Outlined.NotificationsNone, null)
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleSmall)
                Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.observation_source_commitments_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Switch(checked = source?.enabled == true, onCheckedChange = onEnabled)
        }
    }
}

@Composable
private fun ConsentHistoryCard(events: List<ConsentEventEntity>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            stringResource(R.string.observation_history_title),
            stringResource(R.string.observation_history_subtitle)
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (events.isEmpty()) {
                    Text(stringResource(R.string.observation_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    events.forEach { event ->
                        Column {
                            Text(consentEventLabel(event.eventType), style = MaterialTheme.typography.titleSmall)
                            Text(
                                buildString {
                                    if (event.sourcePackage.isNotBlank()) append(event.sourcePackage).append(" · ")
                                    append(DateRules.formatDate(event.occurredAt)).append(" · ")
                                    append(DateRules.formatTime(event.occurredAt))
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun consentEventLabel(type: ConsentEventType): String = stringResource(
    when (type) {
        ConsentEventType.OBSERVATION_ENABLED -> R.string.consent_event_observation_enabled
        ConsentEventType.OBSERVATION_DISABLED -> R.string.consent_event_observation_disabled
        ConsentEventType.SOURCE_ENABLED -> R.string.consent_event_source_enabled
        ConsentEventType.SOURCE_DISABLED -> R.string.consent_event_source_disabled
        ConsentEventType.PAUSED -> R.string.consent_event_paused
        ConsentEventType.RESUMED -> R.string.consent_event_resumed
        ConsentEventType.DATA_CLEARED -> R.string.consent_event_data_cleared
        ConsentEventType.PERMISSION_REVIEWED -> R.string.consent_event_permission_reviewed
        ConsentEventType.SYSTEM_PERMISSION_GRANTED -> R.string.consent_event_permission_granted
        ConsentEventType.SYSTEM_PERMISSION_REVOKED -> R.string.consent_event_permission_revoked
        ConsentEventType.INTERNAL_ACCESS_REVOKED -> R.string.consent_event_access_revoked
    }
)

@Composable
private fun ConversationPreviewCard(
    preview: ConversationPreview,
    selfParticipant: String?,
    retainOriginal: Boolean,
    onSelfParticipant: (String?) -> Unit,
    onRetainOriginal: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val commitments = remember(preview.contentHash, selfParticipant) {
        CommitmentEngine.extract(preview.messages, selfParticipant, preview.contentHash)
    }
    val summary = remember(preview.contentHash, selfParticipant) {
        ConversationSummaryEngine.summarize(preview, commitments)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.conversation_preview_title), style = MaterialTheme.typography.titleLarge)
            val messageCount = pluralStringResource(R.plurals.conversation_message_count, preview.messages.size, preview.messages.size)
            val participantCount = pluralStringResource(R.plurals.conversation_participant_count, preview.participants.size, preview.participants.size)
            val commitmentCount = pluralStringResource(R.plurals.conversation_commitment_count, commitments.size, commitments.size)
            Text(
                stringResource(R.string.conversation_preview_counts, messageCount, participantCount, commitmentCount),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge
            )
            if (preview.participants.isNotEmpty()) {
                Text(stringResource(R.string.conversation_identity_title), style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selfParticipant == null,
                            onClick = { onSelfParticipant(null) },
                            label = { Text(stringResource(R.string.conversation_identity_unknown)) }
                        )
                    }
                    items(preview.participants, key = { it }) { participant ->
                        FilterChip(
                            selected = participant == selfParticipant,
                            onClick = { onSelfParticipant(participant) },
                            label = { Text(participant) }
                        )
                    }
                }
            }
            Text(summary, style = MaterialTheme.typography.bodyMedium)
            preview.messages.take(4).forEach { message ->
                val safeText = if (ConversationPrivacyPolicy.containsSensitiveContent(message.text)) {
                    stringResource(R.string.conversation_sensitive_omitted)
                } else message.text
                Text(
                    buildString {
                        message.sender?.let { append(it).append(": ") }
                        append(safeText)
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.conversation_retain_original), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.conversation_retain_original_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = retainOriginal, onCheckedChange = onRetainOriginal)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave) { Text(stringResource(R.string.conversation_save_review)) }
                OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    }
}

@Composable
private fun CommitmentCard(
    commitment: CommitmentEntity,
    onConvert: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(commitment.action, style = MaterialTheme.typography.titleMedium)
            val owner = when (commitment.owner) {
                CommitmentOwner.SELF -> stringResource(R.string.commitment_owner_self)
                CommitmentOwner.OTHER -> stringResource(R.string.commitment_owner_other)
                CommitmentOwner.UNKNOWN -> stringResource(R.string.commitment_owner_unknown)
            }
            Text(
                stringResource(R.string.commitment_meta, owner, (commitment.confidence * 100).toInt()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            commitment.dueAt?.let {
                Text(
                    "${DateRules.formatDate(it)} · ${DateRules.formatTime(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (commitment.location.isNotBlank()) {
                Text(stringResource(R.string.commitment_location, commitment.location), style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConvert) {
                    Icon(Icons.Outlined.AddTask, null)
                    Text(stringResource(R.string.commitment_create_task), Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.commitment_discard)) }
            }
        }
    }
}

@Composable
private fun ConversationHistoryCard(
    conversation: ConversationEntity,
    commitmentCount: Int,
    convertedTaskIds: List<Long>,
    onTask: (Long) -> Unit,
    onDelete: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(conversation.title, style = MaterialTheme.typography.titleMedium)
                    val messageCountText = pluralStringResource(
                        R.plurals.conversation_message_count,
                        conversation.messageCount,
                        conversation.messageCount
                    )
                    val commitmentCountText = pluralStringResource(
                        R.plurals.conversation_commitment_count,
                        commitmentCount,
                        commitmentCount
                    )
                    Text(
                        stringResource(R.string.conversation_history_meta, messageCountText, commitmentCountText),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.conversation_delete_action))
                }
            }
            Text(conversation.summary, style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(
                    if (conversation.retainsOriginal) R.string.conversation_original_retained else R.string.conversation_summary_only
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            convertedTaskIds.distinct().take(3).forEach { taskId ->
                AssistChip(onClick = { onTask(taskId) }, label = { Text(stringResource(R.string.conversation_open_created_task)) })
            }
        }
    }
}

private fun readBounded(reader: Reader, maxChars: Int): String {
    val output = StringBuilder(minOf(maxChars, 64 * 1024))
    val buffer = CharArray(8 * 1024)
    while (output.length < maxChars) {
        val read = reader.read(buffer, 0, minOf(buffer.size, maxChars - output.length))
        if (read < 0) break
        output.append(buffer, 0, read)
    }
    return output.toString()
}

private data class ObservationApp(val label: String, val packageName: String)

private val OBSERVATION_APPS = listOf(
    ObservationApp("WhatsApp", "com.whatsapp"),
    ObservationApp("WhatsApp Business", "com.whatsapp.w4b"),
    ObservationApp("Telegram", "org.telegram.messenger"),
    ObservationApp("Signal", "org.thoughtcrime.securesms"),
    ObservationApp("Google Messages", "com.google.android.apps.messaging")
)
