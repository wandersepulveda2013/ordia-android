import os
from pathlib import Path

def write_file(path_str, content):
    p = Path(path_str)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content.strip() + "\n", encoding="utf-8")

# Design System
design_system = """
package com.ordia.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Base Colors
val OrdiaWhite = Color(0xFFFFFFFF)
val OrdiaBlack = Color(0xFF000000)
val OrdiaGray = Color(0xFFF5F5F5)
val OrdiaGrayDark = Color(0xFF333333)
val OrdiaAccent = Color(0xFF6200EE)

@Composable
fun OrdiaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = OrdiaBlack, contentColor = OrdiaWhite),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdiaInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = label,
        supportingText = supportingText,
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.outlinedTextFieldColors(
            focusedBorderColor = OrdiaBlack,
            unfocusedBorderColor = OrdiaGrayDark
        )
    )
}

@Composable
fun OrdiaSheet(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
fun OrdiaDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        title = title,
        text = text,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun OrdiaCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
    modifier: Modifier = Modifier,
    isCompleted: Boolean = false,
    onToggle: (Boolean) -> Unit = {}
) {
    OrdiaCard(modifier = modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = isCompleted, onCheckedChange = onToggle)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun OrdiaNote(
    content: String,
    modifier: Modifier = Modifier
) {
    OrdiaCard(modifier = modifier.padding(vertical = 4.dp)) {
        Text(text = content, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun OrdiaAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, OrdiaGrayDark)
    ) {
        Text(label, color = OrdiaBlack)
    }
}

@Composable
fun OrdiaGuardian(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = OrdiaGray,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Text(text = "🤖", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun OrdiaTimeline(
    items: List<String>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        items.forEach { item ->
            Text(text = item, modifier = Modifier.padding(vertical = 4.dp))
            HorizontalDivider()
        }
    }
}

@Composable
fun OrdiaCommand(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OrdiaInput(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        label = { Text("Buscar...") }
    )
}

@Composable
fun OrdiaKeyboardBar(
    modifier: Modifier = Modifier,
    onActionClick: (String) -> Unit
) {
    Row(modifier = modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        OrdiaAction(label = "+ Tarea", onClick = { onActionClick("task") })
        OrdiaAction(label = "+ Nota", onClick = { onActionClick("note") })
        OrdiaAction(label = "Guardar", onClick = { onActionClick("save") })
    }
}
"""

write_file("app/src/main/java/com/ordia/app/ui/components/OrdiaDesignSystem.kt", design_system)
