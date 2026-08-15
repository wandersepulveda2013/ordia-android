package com.ordia.app.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ordia.app.R
import com.ordia.app.data.local.CaptureEntity
import com.ordia.app.data.local.CaptureSource
import com.ordia.app.data.local.CaptureStatus
import com.ordia.app.data.local.CaptureTarget
import com.ordia.app.domain.UniversalCaptureEngine
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.ui.components.SectionHeader
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CaptureScreen(
    vm: OrdiaViewModel,
    padding: PaddingValues,
    onTask: (Long) -> Unit,
    onNote: (Long) -> Unit
) {
    val draftState by vm.captureDraftState.collectAsStateWithLifecycle()
    val draft = draftState.draft
    val history by vm.recentCaptures.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var text by rememberSaveable { mutableStateOf("") }
    var targetName by rememberSaveable { mutableStateOf(CaptureTarget.AUTO.name) }
    var attachmentUri by rememberSaveable { mutableStateOf("") }
    var attachmentMime by rememberSaveable { mutableStateOf("") }
    var sourceName by rememberSaveable { mutableStateOf(CaptureSource.COMPOSER.name) }
    var restored by rememberSaveable { mutableStateOf(false) }
    val target = remember(targetName) { CaptureTarget.valueOf(targetName) }
    val source = remember(sourceName) { CaptureSource.valueOf(sourceName) }

    LaunchedEffect(draftState.loaded, draft?.updatedAt) {
        if (!draftState.loaded || restored) return@LaunchedEffect
        val saved = draft
        if (saved != null && text.isBlank() && attachmentUri.isBlank()) {
            text = saved.content
            targetName = saved.target.name
            attachmentUri = saved.attachmentUri
            attachmentMime = saved.mimeType
        }
        restored = true
    }

    LaunchedEffect(text, targetName, attachmentUri, attachmentMime, draftState.loaded) {
        if (!draftState.loaded) return@LaunchedEffect
        delay(450)
        vm.saveCaptureDraft(text, target, attachmentUri, attachmentMime)
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let { spoken ->
                    text = listOf(text.trim(), spoken).filter(String::isNotBlank).joinToString("\n")
                    sourceName = CaptureSource.VOICE.name
                }
        }
    }
    val attachmentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            attachmentUri = uri.toString()
            attachmentMime = context.contentResolver.getType(uri).orEmpty()
            sourceName = CaptureSource.ATTACHMENT.name
        }
    }

    val interpretation = remember(text, targetName, attachmentUri) {
        if (text.isBlank() && attachmentUri.isBlank()) null
        else UniversalCaptureEngine.interpret(text, target, attachmentUri.isNotBlank())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = padding.calculateTopPadding() + 20.dp,
            end = 20.dp,
            bottom = padding.calculateBottomPadding() + 40.dp
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            ScreenHeader(
                stringResource(R.string.capture_header_eyebrow),
                stringResource(R.string.capture_header_title),
                stringResource(R.string.capture_header_subtitle)
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it.take(UniversalCaptureEngine.MAX_CONTENT_CHARS)
                            sourceName = CaptureSource.COMPOSER.name
                        },
                        modifier = Modifier.fillMaxWidth().height(168.dp),
                        label = { Text(stringResource(R.string.capture_input_label)) },
                        placeholder = { Text(stringResource(R.string.capture_input_hint)) }
                    )

                    CaptureTargetSelector(target) { targetName = it.name }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {
                                try {
                                    voiceLauncher.launch(
                                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                                        }
                                    )
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(context, R.string.capture_voice_unavailable, Toast.LENGTH_SHORT).show()
                                }
                            },
                            label = { Text(stringResource(R.string.capture_action_voice)) },
                            leadingIcon = { Icon(Icons.Outlined.Mic, null) }
                        )
                        AssistChip(
                            onClick = { attachmentLauncher.launch(arrayOf("*/*")) },
                            label = { Text(stringResource(R.string.capture_action_attach)) },
                            leadingIcon = { Icon(Icons.Outlined.AttachFile, null) }
                        )
                        AssistChip(
                            onClick = {
                                val pasted = clipboard.getText()?.text.orEmpty().trim()
                                if (pasted.isBlank()) {
                                    Toast.makeText(context, R.string.capture_clipboard_empty, Toast.LENGTH_SHORT).show()
                                } else {
                                    text = listOf(text.trim(), pasted).filter(String::isNotBlank).joinToString("\n")
                                    sourceName = CaptureSource.CLIPBOARD.name
                                }
                            },
                            label = { Text(stringResource(R.string.capture_action_paste)) },
                            leadingIcon = { Icon(Icons.Outlined.ContentPaste, null) }
                        )
                    }

                    if (attachmentUri.isNotBlank()) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(
                                    R.string.capture_attachment_selected,
                                    attachmentUri.toUri().lastPathSegment.orEmpty()
                                ),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = {
                                    attachmentUri = ""
                                    attachmentMime = ""
                                }
                            ) {
                                Icon(Icons.Outlined.Close, stringResource(R.string.capture_action_remove_attachment))
                            }
                        }
                    }

                    interpretation?.let { preview ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(stringResource(R.string.capture_preview_title), style = MaterialTheme.typography.labelMedium)
                                Text(captureTargetLabel(preview.target), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(
                                        R.string.capture_preview_confidence,
                                        (preview.confidence * 100).toInt(),
                                        preview.explanation
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            vm.submitCapture(
                                content = text,
                                requestedTarget = target,
                                source = source,
                                attachmentUri = attachmentUri,
                                mimeType = attachmentMime
                            ) { _, _ ->
                                text = ""
                                targetName = CaptureTarget.AUTO.name
                                attachmentUri = ""
                                attachmentMime = ""
                                sourceName = CaptureSource.COMPOSER.name
                                restored = true
                            }
                        },
                        enabled = text.isNotBlank() || attachmentUri.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.capture_save))
                    }

                    if (text.isNotBlank() || attachmentUri.isNotBlank() || target != CaptureTarget.AUTO) {
                        AssistChip(
                            onClick = {
                                text = ""
                                targetName = CaptureTarget.AUTO.name
                                attachmentUri = ""
                                attachmentMime = ""
                                sourceName = CaptureSource.COMPOSER.name
                                restored = true
                                vm.clearCaptureDraft()
                            },
                            label = { Text(stringResource(R.string.capture_discard_draft)) },
                            leadingIcon = { Icon(Icons.Outlined.Close, null) }
                        )
                    }
                }
            }
        }

        item {
            SectionHeader(
                stringResource(R.string.capture_history_title),
                stringResource(R.string.capture_history_subtitle)
            )
        }
        if (history.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp)) {
                        Icon(Icons.Outlined.History, null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.capture_history_empty))
                    }
                }
            }
        } else {
            items(history, key = { it.id }) { capture ->
                CaptureHistoryRow(
                    capture = capture,
                    onOpen = {
                        when (capture.resultType) {
                            "TASK" -> capture.resultId?.let(onTask)
                            "NOTE" -> capture.resultId?.let(onNote)
                        }
                    },
                    onRetry = { vm.retryCapture(capture) },
                    onDiscard = { vm.discardFailedCapture(capture) }
                )
            }
        }
    }
}

@Composable
private fun CaptureTargetSelector(selected: CaptureTarget, onSelect: (CaptureTarget) -> Unit) {
    val targets = listOf(
        CaptureTarget.AUTO to R.string.capture_target_auto,
        CaptureTarget.INBOX to R.string.capture_target_inbox,
        CaptureTarget.TASK to R.string.capture_target_task,
        CaptureTarget.NOTE to R.string.capture_target_note,
        CaptureTarget.REMINDER to R.string.capture_target_reminder,
        CaptureTarget.EVENT to R.string.capture_target_event
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(targets, key = { it.first.name }) { (target, label) ->
            FilterChip(
                selected = selected == target,
                onClick = { onSelect(target) },
                label = { Text(stringResource(label)) }
            )
        }
    }
}

@Composable
private fun CaptureHistoryRow(
    capture: CaptureEntity,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit
) {
    val canOpen = capture.status == CaptureStatus.PROCESSED && capture.resultId != null
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = canOpen, onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                capture.content.ifBlank { stringResource(R.string.capture_attachment_name) },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "${captureSourceLabel(capture.source)} · ${captureStatusLabel(capture.status)}",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(capture.createdAt)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            if (capture.status == CaptureStatus.FAILED) {
                Text(
                    stringResource(R.string.capture_retry_explanation),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRetry, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Refresh, null)
                        Text(stringResource(R.string.capture_retry), Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(onClick = onDiscard) {
                        Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.capture_discard_failed))
                    }
                }
            }
        }
    }
}

@Composable
private fun captureTargetLabel(target: CaptureTarget): String = stringResource(
    when (target) {
        CaptureTarget.AUTO -> R.string.capture_target_auto
        CaptureTarget.INBOX -> R.string.capture_target_inbox
        CaptureTarget.TASK -> R.string.capture_target_task
        CaptureTarget.NOTE -> R.string.capture_target_note
        CaptureTarget.REMINDER -> R.string.capture_target_reminder
        CaptureTarget.EVENT -> R.string.capture_target_event
    }
)

@Composable
private fun captureStatusLabel(status: CaptureStatus): String = stringResource(
    when (status) {
        CaptureStatus.PENDING -> R.string.capture_status_pending
        CaptureStatus.PROCESSED -> R.string.capture_status_processed
        CaptureStatus.FAILED -> R.string.capture_status_failed
    }
)

@Composable
private fun captureSourceLabel(source: CaptureSource): String = stringResource(
    when (source) {
        CaptureSource.SHARE, CaptureSource.PROCESS_TEXT -> R.string.capture_source_share
        CaptureSource.VOICE -> R.string.capture_source_voice
        CaptureSource.CLIPBOARD -> R.string.capture_source_clipboard
        CaptureSource.ATTACHMENT -> R.string.capture_source_attachment
        else -> R.string.capture_source_composer
    }
)
