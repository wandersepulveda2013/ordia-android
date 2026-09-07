package com.ordia.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

@Composable
fun OrdiaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        color = Color.Transparent,
        content = content
    )
}

@Composable
fun OrdiaInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholderText: String,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholderText, style = textStyle) },
        textStyle = textStyle,
        modifier = modifier,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        )
    )
}

@Composable
fun OrdiaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
