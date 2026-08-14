package com.ordia.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ordia.app.ui.theme.OrdiaGoldSoft

@Composable
fun OrdiaButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    color: Color = MaterialTheme.colorScheme.primary
) {
    if (outlined) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.small,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(containerColor = color)
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun OrdiaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable () -> Unit
) {
    val cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val cardBorder = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = cardColors,
            border = cardBorder
        ) {
            content()
        }
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = cardColors,
            border = cardBorder
        ) {
            content()
        }
    }
}

@Composable
fun ScreenHeader(
    eyebrow: String? = null,
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (eyebrow != null) {
            Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineLarge)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (actionLabel != null && onAction != null) {
                OutlinedButton(onClick = onAction) {
                    Icon(Icons.Outlined.Add, null)
                    Text(actionLabel, Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, supporting: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null && onAction != null) {
            Text(
                action,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onAction).padding(horizontal = 10.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
fun StatCard(label: String, value: String, supporting: String? = null, modifier: Modifier = Modifier) {
    OrdiaCard(modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium)
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().ordiaWorkSurface(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            Modifier.padding(horizontal = 26.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GuardianAvatar(72.dp)
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(2.dp))
                OrdiaButton(label = actionLabel, onClick = onAction)
            }
        }
    }
}

@Composable
fun GuardianAvatar(size: androidx.compose.ui.unit.Dp, mood: GuardianMood = GuardianMood.CALM, modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.primary
    val foreground = MaterialTheme.colorScheme.onPrimary
    val accent = OrdiaGoldSoft
    Canvas(
        modifier = modifier.size(size).semantics { contentDescription = "Guardián de Ordia, estado ${mood.label}" }
    ) {
        drawCircle(background)
        drawCircle(accent, radius = this.size.minDimension * 0.43f, style = Stroke(width = this.size.minDimension * 0.035f))
        val eyeY = this.size.height * 0.43f
        val eyeRadius = this.size.minDimension * 0.035f
        drawCircle(foreground, eyeRadius, Offset(this.size.width * 0.38f, eyeY))
        drawCircle(foreground, eyeRadius, Offset(this.size.width * 0.62f, eyeY))
        val mouthY = this.size.height * 0.62f
        when (mood) {
            GuardianMood.CALM, GuardianMood.HAPPY -> {
                drawArc(
                    color = foreground,
                    startAngle = if (mood == GuardianMood.HAPPY) 10f else 20f,
                    sweepAngle = if (mood == GuardianMood.HAPPY) 160f else 140f,
                    useCenter = false,
                    topLeft = Offset(this.size.width * 0.36f, mouthY - this.size.height * 0.09f),
                    size = androidx.compose.ui.geometry.Size(this.size.width * 0.28f, this.size.height * 0.15f),
                    style = Stroke(width = this.size.minDimension * 0.035f)
                )
            }
            GuardianMood.FOCUSED -> drawLine(foreground, Offset(this.size.width * 0.39f, mouthY), Offset(this.size.width * 0.61f, mouthY), strokeWidth = this.size.minDimension * 0.035f)
        }
        drawCircle(accent, radius = this.size.minDimension * 0.055f, center = Offset(this.size.width * 0.5f, this.size.height * 0.18f))
    }
}

enum class GuardianMood(val label: String) { CALM("tranquilo"), HAPPY("feliz"), FOCUSED("concentrado") }

@Composable
fun Modifier.ordiaWorkSurface(): Modifier {
    val dot = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
    return this.drawBehind {
        val gap = 18.dp.toPx()
        val radius = 0.9.dp.toPx()
        var x = gap / 2
        while (x < size.width) {
            var y = gap / 2
            while (y < size.height) {
                drawCircle(dot, radius, Offset(x, y))
                y += gap
            }
            x += gap
        }
    }
}

@Composable
fun PrimaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OrdiaButton(label = label, onClick = onClick, modifier = modifier)
}

@Composable
fun OrdiaInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = MaterialTheme.shapes.small,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
fun OrdiaDialog(
    onDismissRequest: () -> Unit,
    title: String,
    content: @Composable () -> Unit,
    confirmAction: @Composable (() -> Unit)? = null,
    dismissAction: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                content()
                if (confirmAction != null || dismissAction != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        dismissAction?.invoke()
                        if (dismissAction != null && confirmAction != null) Spacer(Modifier.size(8.dp))
                        confirmAction?.invoke()
                    }
                }
            }
        }
    }
}

@Composable
fun InfoBanner(title: String, text: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
