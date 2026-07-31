package com.ordia.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.ordia.app.R
import com.ordia.app.context.ContextualSuggestion

@Composable
fun ContextualSuggestionDialog(
    suggestion: ContextualSuggestion,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(suggestion.id) { mutableStateOf(suggestion.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.suggestion_confirm_title)) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(stringResource(R.string.suggestion_privacy_note))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(100) },
                    label = { Text(stringResource(R.string.external_suggestion_title_hint)) },
                    singleLine = false
                )
                Text(stringResource(R.string.suggestion_confidence, (suggestion.confidence * 100).toInt()))
            }
        },
        confirmButton = { Button(onClick = { onConfirm(title.trim()) }, enabled = title.isNotBlank()) { Text(stringResource(R.string.action_add)) } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.suggestion_discard)) } }
    )
}
