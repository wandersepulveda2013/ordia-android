package com.ordia.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ordia.app.R
import com.ordia.app.ui.theme.OrdiaAccent

/**
 * Barra superior compacta (52–56 dp). Rediseño limpio: encabezado denso con
 * eyebrow pequeño, título de 18 sp y subtítulo de una línea, acción opcional.
 */
@Composable
fun ScreenHeader(
    eyebrow: String? = null,
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (eyebrow != null) {
                Text(
                    eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Text(actionLabel, Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** Encabezado de sección compacto (título 16 sp + soporte de una línea). */
@Composable
fun SectionHeader(
    title: String,
    supporting: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (action != null && onAction != null) {
            Text(
                action,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onAction)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    supporting: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f))
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(shape = CircleShape, color = accent.copy(alpha = 0.14f)) {
                    if (icon != null) {
                        Icon(icon, null, Modifier.padding(6.dp).size(15.dp), tint = accent)
                    } else {
                        Box(Modifier.padding(10.dp).size(6.dp).background(accent, CircleShape))
                    }
                }
            }
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GuardianAvatar(48.dp)
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(2.dp))
                Button(
                    onClick = onAction,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(actionLabel)
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.padding(start = 6.dp).size(16.dp))
                }
            }
        }
    }
}

/** Fila de lista reutilizable (rediseño limpio): título, soporte, icono y acción opcionales. */
@Composable
fun OrdiaListItem(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    trailing: (@Composable () -> Unit)? = null
) {
    val base = modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    Row(
        base.padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Surface(shape = MaterialTheme.shapes.small, color = iconTint.copy(alpha = 0.13f)) {
                Icon(icon, null, Modifier.padding(9.dp).size(19.dp), tint = iconTint)
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

/** Etiqueta tipo píldora para estados (rediseño limpio). */
@Composable
fun StatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(999.dp), color = background) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1
        )
    }
}

/** Estado de carga (rediseño limpio). */
@Composable
fun OrdiaLoading(modifier: Modifier = Modifier, label: String? = null) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
        Text(
            label ?: stringResource(R.string.component_loading),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Estado de error con reintento opcional (rediseño limpio). */
@Composable
fun OrdiaError(
    modifier: Modifier = Modifier,
    message: String? = null,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.component_error_title), style = MaterialTheme.typography.titleMedium)
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (onRetry != null) {
            OutlinedButton(
                onClick = onRetry,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(stringResource(R.string.component_error_retry))
            }
        }
    }
}

@Composable
fun GuardianAvatar(size: Dp, mood: GuardianMood = GuardianMood.CALM, modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.primary
    val foreground = MaterialTheme.colorScheme.onPrimary
    val accent = OrdiaAccent
    val description = stringResource(R.string.component_guardian_description, stringResource(mood.labelRes))
    Canvas(
        modifier = modifier.size(size).semantics {
            contentDescription = description
        }
    ) {
        drawCircle(background)
        drawCircle(
            accent,
            radius = this.size.minDimension * 0.43f,
            style = Stroke(width = this.size.minDimension * 0.035f)
        )
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
            GuardianMood.FOCUSED -> drawLine(
                foreground,
                Offset(this.size.width * 0.39f, mouthY),
                Offset(this.size.width * 0.61f, mouthY),
                strokeWidth = this.size.minDimension * 0.035f
            )
        }
        drawCircle(
            accent,
            radius = this.size.minDimension * 0.055f,
            center = Offset(this.size.width * 0.5f, this.size.height * 0.18f)
        )
    }
}

enum class GuardianMood(@StringRes val labelRes: Int) {
    CALM(R.string.component_guardian_mood_calm),
    HAPPY(R.string.component_guardian_mood_happy),
    FOCUSED(R.string.component_guardian_mood_focused)
}

@Composable
fun ProgressRing(
    progress: Float,
    centerText: String,
    label: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 86.dp,
    strokeWidth: Dp = 9.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
) {
    val animatedProgress by animateFloatAsState(progress.coerceIn(0f, 1f), label = "ordia-progress")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth.toPx())
                )
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    style = Stroke(width = strokeWidth.toPx())
                )
            }
            Text(centerText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = accent.copy(alpha = 0.14f)) {
                    Icon(icon, null, Modifier.padding(10.dp).size(21.dp), tint = accent)
                }
                Spacer(Modifier.weight(1f))
                if (!badge.isNullOrBlank()) {
                    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            badge,
                            Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun Modifier.ordiaWorkSurface(): Modifier {
    val dot = MaterialTheme.colorScheme.outline.copy(alpha = 0.13f)
    return this.drawBehind {
        val gap = 18.dp.toPx()
        val radius = 0.85.dp.toPx()
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
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Text(label)
    }
}

@Composable
fun InfoBanner(title: String, text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
