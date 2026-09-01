package com.ordia.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun OrdiaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
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
    placeholder: @Composable (() -> Unit)? = null,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    colors: androidx.compose.material3.TextFieldColors = TextFieldDefaults.colors()
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        supportingText = supportingText,
        placeholder = placeholder,
        textStyle = textStyle,
        shape = RoundedCornerShape(8.dp),
        colors = colors
    )
}

@Composable
fun OrdiaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val clickModifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    Card(
        modifier = modifier.then(clickModifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        content = {
            Box(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    )
}

@Composable
fun OrdiaSheet(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) { content() }
}

@Composable
fun OrdiaDialog(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp)
    ) { content() }
}

@Composable
fun OrdiaTask(title: String, modifier: Modifier = Modifier) {
    OrdiaCard(modifier = modifier) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun OrdiaNote(title: String, preview: String, modifier: Modifier = Modifier) {
    OrdiaCard(modifier = modifier) {
        androidx.compose.foundation.layout.Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = preview, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun OrdiaAction(title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OrdiaButton(onClick = onClick, modifier = modifier) {
        Text(title)
    }
}

@Composable
fun OrdiaGuardian(name: String, modifier: Modifier = Modifier) {
    OrdiaCard(modifier = modifier) {
        Text(text = name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun OrdiaTimeline(items: List<String>, modifier: Modifier = Modifier) {
    // Placeholder
}

@Composable
fun OrdiaCommand(command: String, modifier: Modifier = Modifier) {
    // Placeholder
}

@Composable
fun OrdiaKeyboardBar(modifier: Modifier = Modifier) {
    // Placeholder
}
