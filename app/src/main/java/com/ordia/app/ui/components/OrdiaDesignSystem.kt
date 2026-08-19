package com.ordia.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

@Composable
fun OrdiaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdiaSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                content = content
            )
        }
    )
}

@Composable
fun OrdiaDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Box(modifier = Modifier.padding(24.dp)) {
                content()
            }
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
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
) {
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
        supportingText = supportingText,
        isError = isError,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        )
    )
}

@Composable
fun OrdiaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = modifier.fillMaxWidth()
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            content = content
        )
    } else {
        Card(
            modifier = cardModifier,
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            content = content
        )
    }
}

@Composable
fun OrdiaSurface(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        color = color,
        contentColor = contentColor,
        content = content
    )
}

@Composable
fun OrdiaTask() {
    // Placeholder for OrdiaTask component
}

@Composable
fun OrdiaNote() {
    // Placeholder for OrdiaNote component
}

@Composable
fun OrdiaAction() {
    // Placeholder for OrdiaAction component
}

@Composable
fun OrdiaGuardian() {
    // Placeholder for OrdiaGuardian component
}

@Composable
fun OrdiaTimeline() {
    // Placeholder for OrdiaTimeline component
}

@Composable
fun OrdiaCommand() {
    // Placeholder for OrdiaCommand component
}

@Composable
fun OrdiaKeyboardBar() {
    // Placeholder for OrdiaKeyboardBar component
}
