package com.ordia.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

@Composable
fun OrdiaCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        content()
    }
}

@Composable
fun OrdiaSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        content()
    }
}

@Composable
fun OrdiaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (isPrimary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun OrdiaInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    minLines: Int = 1,
    maxLines: Int = 1,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        minLines = minLines,
        maxLines = maxLines,
        singleLine = minLines == 1 && maxLines == 1,
        textStyle = textStyle,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedBorderColor = MaterialTheme.colorScheme.secondary,
        )
    )
}
