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
        title = { Text("¿Quieres que Ordia lo recuerde?") },
        text = {
            androidx.compose.foundation.layout.Column {
                Text("El análisis ocurrió en tu teléfono. Ordia no guardó la conversación original.")
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(100) },
                    label = { Text("Título") },
                    singleLine = false
                )
                Text("Confianza: ${(suggestion.confidence * 100).toInt()} %")
            }
        },
        confirmButton = { Button(onClick = { onConfirm(title.trim()) }, enabled = title.isNotBlank()) { Text("Añadir") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Descartar") } }
    )
}
