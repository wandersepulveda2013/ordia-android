package com.ordia.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ordia.app.ui.theme.OrdiaShapes

@Composable
fun OrdiaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    text: String? = null,
    content: @Composable (RowScope.() -> Unit)? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = OrdiaShapes.small,
        contentPadding = contentPadding
    ) {
        if (text != null) Text(text) else if (content != null) content()
    }
}

@Composable
fun OrdiaSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    text: String? = null,
    content: @Composable (RowScope.() -> Unit)? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = OrdiaShapes.small,
        contentPadding = contentPadding
    ) {
        if (text != null) Text(text) else if (content != null) content()
    }
}

@Composable
fun OrdiaTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    text: String? = null,
    content: @Composable (RowScope.() -> Unit)? = null
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = OrdiaShapes.small,
        contentPadding = contentPadding
    ) {
        if (text != null) Text(text) else if (content != null) content()
    }
}

@Composable
fun OrdiaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = OrdiaShapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant),
            content = { content() }
        )
    } else {
        Card(
            modifier = modifier,
            shape = OrdiaShapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant),
            content = { content() }
        )
    }
}

@Composable
fun OrdiaInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        maxLines = maxLines,
        shape = OrdiaShapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
            focusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
        )
    )
}
