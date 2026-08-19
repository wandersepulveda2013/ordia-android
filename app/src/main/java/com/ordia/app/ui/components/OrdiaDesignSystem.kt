package com.ordia.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun OrdiaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ),
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = colors,
        content = content
    )
}

@Composable
fun OrdiaInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        isError = isError,
        singleLine = singleLine,
        maxLines = maxLines,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdiaSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        content = content
    )
}

@Composable
fun OrdiaDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = title,
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun OrdiaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            content = content
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            content = content
        )
    }
}

// Placeholders for advanced domain components.
// These will be fully implemented in later waves (e.g., Wave 3 for Universal Capture).

@Composable
fun OrdiaTask(modifier: Modifier = Modifier, title: String = "", onClick: () -> Unit = {}) {
    OrdiaCard(modifier = modifier, onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun OrdiaNote(modifier: Modifier = Modifier, title: String = "", contentPreview: String = "", onClick: () -> Unit = {}) {
    OrdiaCard(modifier = modifier, onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (contentPreview.isNotEmpty()) {
                Text(contentPreview, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun OrdiaAction(modifier: Modifier = Modifier, text: String = "Action", onClick: () -> Unit = {}) {
    OrdiaButton(onClick = onClick, modifier = modifier) { Text(text) }
}

@Composable
fun OrdiaGuardian(modifier: Modifier = Modifier, message: String = "") {
    OrdiaCard(modifier = modifier) {
        Text(message, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun OrdiaTimeline(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp)) {
        Text("Timeline Empty State", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun OrdiaCommand(modifier: Modifier = Modifier, commandName: String = "", onClick: () -> Unit = {}) {
    Row(modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp)) {
        Text(commandName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun OrdiaKeyboardBar(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceAround) {
        Text("Capture", style = MaterialTheme.typography.labelMedium)
        Text("Task", style = MaterialTheme.typography.labelMedium)
    }
}
