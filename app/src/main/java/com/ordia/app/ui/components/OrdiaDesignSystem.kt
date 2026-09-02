package com.ordia.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun OrdiaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Button(onClick = onClick, modifier = modifier) {
        content()
    }
}

@Composable
fun OrdiaInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
        )
    )
}

@Composable
fun OrdiaSheet(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(modifier = modifier) {
        content()
    }
}

@Composable
fun OrdiaDialog(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(modifier = modifier) {
        content()
    }
}

@Composable
fun OrdiaCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(modifier = modifier) {
        content()
    }
}

@Composable
fun OrdiaTask(
    title: String,
    modifier: Modifier = Modifier
) {
    OrdiaCard(modifier = modifier) {
        Text(text = title)
    }
}

@Composable
fun OrdiaNote(
    title: String,
    content: String,
    modifier: Modifier = Modifier
) {
    OrdiaCard(modifier = modifier) {
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun OrdiaAction(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OrdiaButton(onClick = onClick, modifier = modifier) {
        Text(text = title)
    }
}

@Composable
fun OrdiaGuardian(
    name: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Text(text = name)
    }
}

@Composable
fun OrdiaTimeline(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier) {
        content()
    }
}

@Composable
fun OrdiaCommand(
    command: String,
    modifier: Modifier = Modifier
) {
    Text(text = command, modifier = modifier)
}

@Composable
fun OrdiaKeyboardBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
    }
}
