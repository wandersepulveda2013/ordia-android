package com.ordia.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Foundational visual components for the custom minimalist design system.

@Composable
fun OrdiaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        content = content
    )
}

@Composable
fun OrdiaInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        supportingText = supportingText
    )
}

@Composable
fun OrdiaSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    // Basic placeholder for sheet functionality
    Surface(modifier = modifier, shadowElevation = 4.dp) {
        Column { content() }
    }
}

@Composable
fun OrdiaDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        confirmButton = { },
        text = { content() }
    )
}

@Composable
fun OrdiaCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
fun OrdiaTask(
    title: String,
    modifier: Modifier = Modifier
) {
    OrdiaCard(modifier = modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun OrdiaNote(
    title: String,
    content: String,
    modifier: Modifier = Modifier
) {
    OrdiaCard(modifier = modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = content, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun OrdiaAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text)
    }
}

@Composable
fun OrdiaGuardian(
    name: String,
    modifier: Modifier = Modifier
) {
    OrdiaCard(modifier = modifier) {
        Text(text = "Guardian: $name", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun OrdiaTimeline(
    events: List<String>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        events.forEach { event ->
            Text(text = event, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
fun OrdiaCommand(
    command: String,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.padding(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(text = "> $command", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun OrdiaKeyboardBar(
    actions: List<String>,
    onActionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        actions.forEach { action ->
            OrdiaAction(text = action, onClick = { onActionClick(action) })
        }
    }
}
