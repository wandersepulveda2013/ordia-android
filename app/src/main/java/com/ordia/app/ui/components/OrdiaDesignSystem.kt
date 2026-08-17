package com.ordia.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

enum class OrdiaButtonVariant {
    PRIMARY, OUTLINE, TEXT
}

@Composable
fun OrdiaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: OrdiaButtonVariant = OrdiaButtonVariant.PRIMARY,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.medium,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val actualInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    when (variant) {
        OrdiaButtonVariant.PRIMARY -> {
            Button(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = contentPadding,
                interactionSource = actualInteractionSource,
                content = content
            )
        }
        OrdiaButtonVariant.OUTLINE -> {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                shape = shape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = contentPadding,
                interactionSource = actualInteractionSource,
                content = content
            )
        }
        OrdiaButtonVariant.TEXT -> {
            TextButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = contentPadding,
                interactionSource = actualInteractionSource,
                content = content
            )
        }
    }
}

@Composable
fun OrdiaInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = MaterialTheme.shapes.medium
) {
    val safeSingleLine = singleLine && minLines == 1 && maxLines == 1

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = safeSingleLine,
        maxLines = maxLines,
        minLines = minLines,
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
fun OrdiaTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    OrdiaButton(onClick = onClick, modifier = modifier, variant = OrdiaButtonVariant.TEXT, enabled = enabled, content = content)
}

@Composable
fun OrdiaOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    OrdiaButton(onClick = onClick, modifier = modifier, variant = OrdiaButtonVariant.OUTLINE, enabled = enabled, content = content)
}

@Composable
fun OrdiaSurface(
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable () -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        color = color,
        contentColor = contentColor,
        shape = shape,
        content = content
    )
}

@Composable
fun OrdiaDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: String? = null,
    confirmButtonText: String = "Aceptar",
    onConfirm: () -> Unit,
    dismissButtonText: String? = null,
    onDismiss: (() -> Unit)? = null
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { androidx.compose.material3.Text(title, style = MaterialTheme.typography.titleMedium) },
        text = text?.let { { androidx.compose.material3.Text(it, style = MaterialTheme.typography.bodyMedium) } },
        confirmButton = {
            OrdiaTextButton(onClick = { onConfirm(); onDismissRequest() }) {
                androidx.compose.material3.Text(confirmButtonText)
            }
        },
        dismissButton = dismissButtonText?.let { btnText ->
            {
                OrdiaTextButton(onClick = { onDismiss?.invoke(); onDismissRequest() }) {
                    androidx.compose.material3.Text(btnText)
                }
            }
        }
    )
}

@Composable
fun OrdiaTimeline(modifier: Modifier = Modifier) {
    // Placeholder for timeline component
    androidx.compose.foundation.layout.Box(modifier = modifier)
}
