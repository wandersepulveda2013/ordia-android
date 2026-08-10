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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.ordia.app.OrdiaApplication
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
                val modeState = remember { mutableStateOf(initialMode) }
                val textState = remember { mutableStateOf(initialText) }
                val voice = dictatedText.value
                LaunchedEffect(voice) {
                    if (voice.isNotBlank() && voice != textState.value) textState.value = voice
                }
                Surface(
                    modifier = Modifier.padding(16.dp),
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 10.dp
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Captura rápida", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
                        Text(
                            "Guárdalo ahora. Ordia te ayuda a organizarlo después.",
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = modeState.value == MODE_TASK, onClick = { modeState.value = MODE_TASK }, label = { Text("Tarea") })
                            FilterChip(selected = modeState.value == MODE_NOTE, onClick = { modeState.value = MODE_NOTE }, label = { Text("Nota") })
                        }
                        OutlinedTextField(
                            value = textState.value,
                            onValueChange = { textState.value = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            label = { Text(if (modeState.value == MODE_TASK) "¿Qué necesitas hacer?" else "¿Qué quieres guardar?") }
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { launchVoiceRecognition() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Mic, null)
                                Text("Dictar", Modifier.padding(start = 6.dp))
                            }
                            Button(
                                onClick = {
                                    val clean = textState.value.trim()
                                    lifecycleScope.launch {
                                        if (modeState.value == MODE_NOTE) {
                                            container.noteRepository.add(
                                                NoteEntity(
                                                    title = clean.lineSequence().firstOrNull()?.take(60).orEmpty().ifBlank { "Nota rápida" },
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
                                enabled = textState.value.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) { Text("Guardar") }
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
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla para guardar en Ordia")
        }
        runCatching { voiceLauncher.launch(intent) }
    }

    companion object {
        const val EXTRA_MODE = "capture_mode"
        const val MODE_TASK = "task"
        const val MODE_NOTE = "note"
    }
}
