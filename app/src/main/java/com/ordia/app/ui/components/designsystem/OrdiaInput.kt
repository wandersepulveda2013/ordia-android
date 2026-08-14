package com.ordia.app.ui.components.designsystem

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * OrdiaInput provides a clean, border-focused input field without heavy backgrounds.
 */
@Composable
fun OrdiaInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    isError: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        isError = isError,
        placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodyLarge) } },
        label = label?.let { { Text(it) } },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            errorBorderColor = MaterialTheme.colorScheme.error,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}
