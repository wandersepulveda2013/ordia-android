package com.ordia.app.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ordia.app.R
import com.ordia.app.domain.OffloadItem
import com.ordia.app.domain.OffloadItemKind
import com.ordia.app.domain.MentalOffloadEngine
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.OrdiaCard
import com.ordia.app.ui.components.OrdiaStatusChip
import com.ordia.app.ui.components.OrdiaStatusTone
import com.ordia.app.ui.components.ScreenHeader
import java.util.Locale

/**
 * Descarga mental 2026: captura libre, Ordía separa en elementos accionables,
 * el usuario confirma. Reduce esfuerzo mental de vaciar la cabeza.
 */
@Composable
fun MentalOffloadScreen(
    vm: OrdiaViewModel,
    padding: PaddingValues,
    onDone: () -> Unit,
    onTask: (Long) -> Unit
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<com.ordia.app.domain.OffloadResult?>(null) }
    var applied by remember { mutableStateOf(false) }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let { spoken ->
                    text = listOf(text.trim(), spoken).filter(String::isNotBlank).joinToString("\n")
                }
        }
    }

    fun process() {
        if (text.isBlank()) return
        result = MentalOffloadEngine.parse(text)
        applied = false
    }

    fun applyAll() {
        val r = result ?: return
        r.items.forEach { item ->
            when (item.kind) {
                OffloadItemKind.TASK, OffloadItemKind.PURCHASE, OffloadItemKind.FOLLOWUP, OffloadItemKind.EVENT ->
                    vm.addTask(title = item.title, dueAt = item.dueAt, priority = item.priority)
                OffloadItemKind.NOTE -> vm.addNote(title = item.title)
            }
        }
        applied = true
        Toast.makeText(context, context.getString(R.string.offload_confirm), Toast.LENGTH_SHORT).show()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
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
                eyebrow = stringResource(R.string.offload_title),
                title = stringResource(R.string.offload_title),
                subtitle = stringResource(R.string.offload_hint)
            )
        }

        item {
            OrdiaCard {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(10_000); result = null; applied = false },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    placeholder = { Text(stringResource(R.string.offload_hint)) },
                    leadingIcon = { Icon(Icons.Outlined.Edit, null) }
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = {
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
                    }) {
                        Icon(Icons.Outlined.Mic, null)
                        Text(stringResource(R.string.offload_voice), Modifier.padding(start = 6.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = ::process, enabled = text.isNotBlank()) {
                        Text(stringResource(R.string.offload_process))
                    }
                }
            }
        }

        result?.let { r ->
            if (r.isEmpty) {
                item {
                    OrdiaCard {
                        Text(
                            stringResource(R.string.offload_no_items),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            if (text.isNotBlank()) vm.addNote(text)
                            onDone()
                        }) {
                            Icon(Icons.Outlined.Check, null)
                            Text(stringResource(R.string.offload_save_as_note), Modifier.padding(start = 6.dp))
                        }
                    }
                }
            } else {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Outlined.Bolt, null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            stringResource(R.string.offload_understood, r.count),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        if (!applied) {
                            Button(onClick = ::applyAll) {
                                Icon(Icons.Outlined.Check, null)
                                Text(stringResource(R.string.offload_confirm), Modifier.padding(start = 6.dp))
                            }
                        } else {
                            Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
                items(r.items.withIndex().toList(), key = { it.index }) { (index, item) ->
                    OffloadItemRow(item, index)
                }
                if (applied) {
                    item {
                        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.action_understood))
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OffloadItemRow(item: OffloadItem, index: Int) {
    val (icon, tone, label) = when (item.kind) {
        OffloadItemKind.TASK -> Triple(Icons.Outlined.CheckCircle, OrdiaStatusTone.ACCENT, stringResource(R.string.offload_task))
        OffloadItemKind.PURCHASE -> Triple(Icons.Outlined.ShoppingCart, OrdiaStatusTone.HUMAN, stringResource(R.string.offload_purchase))
        OffloadItemKind.FOLLOWUP -> Triple(Icons.Outlined.Edit, OrdiaStatusTone.ACTIVITY, stringResource(R.string.offload_followup))
        OffloadItemKind.EVENT -> Triple(Icons.Outlined.Bolt, OrdiaStatusTone.WARNING, stringResource(R.string.offload_event))
        OffloadItemKind.NOTE -> Triple(Icons.Outlined.Edit, OrdiaStatusTone.NEUTRAL, stringResource(R.string.offload_save_as_note))
    }
    OrdiaCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (item.dueAt != null || item.context.isNotBlank()) {
                    Text(
                        buildString {
                            item.context.takeIf { it.isNotBlank() }?.let { append(it.replaceFirstChar { c -> c.uppercase() }) }
                            item.dueAt?.let { append(" · ${com.ordia.app.domain.DateRules.formatDate(it)}") }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OrdiaStatusChip(label = label, tone = tone)
        }
    }
}
