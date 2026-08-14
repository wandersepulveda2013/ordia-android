package com.ordia.app.ui.components.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * OrdiaButton is the primary action button for the application.
 * It strictly adheres to the minimalist design system.
 */
@Composable
fun OrdiaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
        content = content
    )
}

/**
 * OrdiaOutlinedButton for secondary actions.
 */
@Composable
fun OrdiaOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onBackground
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
        content = content
    )
}

/**
 * OrdiaTextButton for tertiary, less intrusive actions.
 */
@Composable
fun OrdiaTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onBackground
        ),
        content = content
    )
}
