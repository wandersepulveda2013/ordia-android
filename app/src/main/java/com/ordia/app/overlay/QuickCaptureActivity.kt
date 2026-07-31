package com.ordia.app.overlay

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
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
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import com.ordia.app.domain.NoteBlock
import com.ordia.app.domain.NoteBlockCodec
import com.ordia.app.domain.NaturalTaskParser
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
        setContent {
            OrdiaTheme {
                var mode by remember { mutableStateOf(initialMode) }
                var text by remember { mutableStateOf(initialText) }
                val voice = dictatedText.value
                LaunchedEffect(voice) {
                    if (voice.isNotBlank() && voice != text) text = voice
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
                                        if (mode == MODE_NOTE) {
                                            container.noteRepository.add(
                                                NoteEntity(
                                                    title = clean.lineSequence().firstOrNull()?.take(60).orEmpty().ifBlank { quickNoteFallback },
                                                    body = clean,
                                                    blocksData = NoteBlockCodec.encode(listOf(NoteBlock(text = clean)))
                                                )
                                            )
                                        } else {
                                            val parsed = NaturalTaskParser.parse(clean)
                                            val task = TaskEntity(
                                                title = parsed.title,
                                                dueAt = parsed.dueAt,
                                                priority = parsed.priority,
                                                status = if (parsed.dueAt == null) TaskStatus.INBOX else TaskStatus.PLANNED
                                            )
                                            val taskId = container.taskRepository.add(task)
                                            if (task.dueAt != null) {
                                                container.reminderScheduler.schedule(task.copy(id = taskId))
                                            }
                                        }
                                        com.ordia.app.widget.OrdiaWidgetUpdater.updateAll(this@QuickCaptureActivity)
                                        finish()
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
        const val MODE_TASK = "task"
        const val MODE_NOTE = "note"
    }
}
