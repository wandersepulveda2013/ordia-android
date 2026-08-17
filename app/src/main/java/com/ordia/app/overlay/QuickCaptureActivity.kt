package com.ordia.app.overlay

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.ordia.app.OrdiaApplication
import com.ordia.app.R
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.CaptureEntity
import com.ordia.app.data.local.CaptureSource
import com.ordia.app.data.local.CaptureStatus
import com.ordia.app.data.local.CaptureTarget
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import com.ordia.app.domain.NoteBlock
import com.ordia.app.domain.NoteBlockCodec
import com.ordia.app.domain.NaturalTaskParser
import com.ordia.app.domain.ReminderRules
import com.ordia.app.domain.UniversalCaptureEngine
import com.ordia.app.ui.theme.OrdiaTheme
import kotlinx.coroutines.launch
import java.util.Locale

class QuickCaptureActivity : ComponentActivity() {
    private val dictatedText = mutableStateOf("")
    private val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            dictatedText.value = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(true)
        val container = (application as OrdiaApplication).container
        val initialMode = intent.getStringExtra(EXTRA_MODE) ?: MODE_TASK
        val initialText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val startVoice = intent.getBooleanExtra(EXTRA_START_VOICE, false)
        setContent {
            OrdiaTheme {
                var mode by remember { mutableStateOf(initialMode) }
                var text by remember { mutableStateOf(initialText) }
                val voice = dictatedText.value
                LaunchedEffect(voice) {
                    if (voice.isNotBlank() && voice != text) text = voice
                }
                LaunchedEffect(startVoice) {
                    if (startVoice) launchVoiceRecognition()
                }
                val title = stringResource(R.string.quick_capture_title)
                val subtitle = stringResource(R.string.quick_capture_subtitle)
                val taskLabel = stringResource(R.string.suggestion_type_task)
                val noteLabel = stringResource(R.string.quick_capture_note)
                val taskHint = stringResource(R.string.quick_capture_task_hint)
                val noteHint = stringResource(R.string.quick_capture_note_hint)
                val dictateLabel = stringResource(R.string.quick_capture_dictate)
                val quickNoteFallback = stringResource(R.string.quick_capture_fallback_title)
                val saveLabel = stringResource(R.string.external_suggestion_save)
                Surface(
                    modifier = Modifier.padding(16.dp),
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 10.dp
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(title, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
                        Text(
                            subtitle,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = mode == MODE_TASK, onClick = { mode = MODE_TASK }, label = { Text(taskLabel) })
                            FilterChip(selected = mode == MODE_NOTE, onClick = { mode = MODE_NOTE }, label = { Text(noteLabel) })
                        }
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            label = { Text(if (mode == MODE_TASK) taskHint else noteHint) }
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { launchVoiceRecognition() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Mic, null)
                                Text(dictateLabel, Modifier.padding(start = 6.dp))
                            }
                            Button(
                                onClick = {
                                    val clean = text.trim()
                                    lifecycleScope.launch {
                                        val requestedTarget = if (mode == MODE_NOTE) CaptureTarget.NOTE else CaptureTarget.TASK
                                        val source = if (dictatedText.value.isNotBlank()) CaptureSource.VOICE else CaptureSource.COMPOSER
                                        val interpretation = UniversalCaptureEngine.interpret(clean, requestedTarget)
                                        val now = System.currentTimeMillis()
                                        var capture = CaptureEntity(
                                            content = clean,
                                            source = source,
                                            requestedTarget = requestedTarget,
                                            resolvedTarget = interpretation.target,
                                            status = CaptureStatus.PENDING,
                                            fingerprint = UniversalCaptureEngine.fingerprint(clean),
                                            createdAt = now,
                                            updatedAt = now
                                        )
                                        runCatching {
                                            val captureId = container.captureRepository.insert(capture)
                                            capture = capture.copy(id = captureId)
                                            val result = if (mode == MODE_NOTE) {
                                                "NOTE" to container.noteRepository.add(
                                                    NoteEntity(
                                                        title = interpretation.title.take(60).ifBlank { quickNoteFallback },
                                                        body = clean,
                                                        blocksData = NoteBlockCodec.encode(listOf(NoteBlock(text = clean))),
                                                        createdAt = now,
                                                        updatedAt = now
                                                    )
                                                )
                                            } else {
                                                val parsed = interpretation.parsedTask ?: NaturalTaskParser.parse(clean)
                                                // Past-safe: offset literal en pasado (plazo corto) → default
                                                // adaptativo; evita que ReminderSync silencie el aviso (olvido).
                                                val reminderAt = parsed.reminderOffsetMinutes
                                                    ?.takeIf { parsed.dueAt != null }
                                                    ?.let { offset ->
                                                        ReminderRules.reminderAtFromOffset(parsed.dueAt!!, offset, now)
                                                    }
                                                val task = TaskEntity(
                                                    title = parsed.title,
                                                    details = clean,
                                                    dueAt = parsed.dueAt,
                                                    reminderAt = reminderAt,
                                                    durationMinutes = parsed.durationMinutes ?: 25,
                                                    priority = parsed.priority,
                                                    recurrence = parsed.recurrence,
                                                    recurrenceInterval = parsed.recurrenceInterval,
                                                    recurrenceDays = parsed.recurrenceDays,
                                                    status = if (parsed.dueAt == null) TaskStatus.INBOX else TaskStatus.PLANNED,
                                                    createdAt = now,
                                                    updatedAt = now
                                                )
                                                val taskId = container.taskRepository.add(task)
                                                if (task.dueAt != null || task.reminderAt != null) {
                                                    container.reminderScheduler.schedule(task.copy(id = taskId))
                                                }
                                                "TASK" to taskId
                                            }
                                            container.captureRepository.update(
                                                capture.copy(
                                                    status = CaptureStatus.PROCESSED,
                                                    resultType = result.first,
                                                    resultId = result.second,
                                                    updatedAt = System.currentTimeMillis()
                                                )
                                            )
                                            com.ordia.app.widget.OrdiaWidgetUpdater.updateAll(this@QuickCaptureActivity)
                                            finish()
                                        }.onFailure { error ->
                                            if (capture.id > 0L) {
                                                container.captureRepository.update(
                                                    capture.copy(
                                                        status = CaptureStatus.FAILED,
                                                        errorCode = error.javaClass.simpleName.take(80),
                                                        updatedAt = System.currentTimeMillis()
                                                    )
                                                )
                                            }
                                            Toast.makeText(
                                                this@QuickCaptureActivity,
                                                R.string.quick_capture_save_failed,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                enabled = text.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) { Text(saveLabel) }
                        }
                    }
                }
            }
        }
    }

    private fun launchVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.quick_capture_voice_prompt))
        }
        runCatching { voiceLauncher.launch(intent) }
    }

    companion object {
        const val EXTRA_MODE = "capture_mode"
        const val EXTRA_START_VOICE = "start_voice"
        const val MODE_TASK = "task"
        const val MODE_NOTE = "note"
    }
}
