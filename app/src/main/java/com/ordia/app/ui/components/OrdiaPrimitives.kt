package com.ordia.app.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ordia.app.ui.theme.ordiaElevation
import com.ordia.app.ui.theme.ordiaTokens

/**
 * Tarjeta 2026: superficie suave con profundidad mínima, sin bordes duros.
 * Contenido dominante; el contenedor apenas se percibe.
 */
@Composable
fun OrdiaCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    elevation: Dp = ordiaElevation.raised,
    border: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = elevation,
        border = if (border) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) else null
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

/**
 * Encabezado de bloque con eyebrow discreto y título cálido. Mantiene jerarquía
 * fuerte sin gritar.
 */
@Composable
fun OrdiaBlockHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * Eyebrow (etiqueta de sección): pequeño, en color de acento, sin gritar.
 */
@Composable
fun OrdiaEyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}

/**
 * Chip de estado semántico: punto + etiqueta, tono sobrio.
 */
@Composable
fun OrdiaStatusChip(
    label: String,
    icon: ImageVector? = null,
    tone: OrdiaStatusTone = OrdiaStatusTone.NEUTRAL,
    modifier: Modifier = Modifier
) {
    val (container, content) = tone.colors()
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(14.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

enum class OrdiaStatusTone { NEUTRAL, ACCENT, ACTIVITY, HUMAN, SUCCESS, WARNING, DANGER }

@Composable
private fun OrdiaStatusTone.colors(): Pair<Color, Color> = with(ordiaTokens) {
    when (this@colors) {
        OrdiaStatusTone.NEUTRAL -> surfaceContainer to textPrimary
        OrdiaStatusTone.ACCENT -> accentSoft to accentStrong
        OrdiaStatusTone.ACTIVITY -> activitySoft to activity
        OrdiaStatusTone.HUMAN -> humanSoft to human
        OrdiaStatusTone.SUCCESS -> Color(0xFFE2F3EA) to success
        OrdiaStatusTone.WARNING -> Color(0xFFFBEFD6) to warning
        OrdiaStatusTone.DANGER -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
}

/**
 * Estado vacío cálido 2026: mensaje humano, no frío. Opcionalmente con acción.
 */
@Composable
fun OrdiaEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (icon != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        null,
                        Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

/**
 * Línea de briefing: cuenta grande + etiqueta, densa y escaneable.
 */
@Composable
fun OrdiaBriefingStat(
    count: String,
    label: String,
    modifier: Modifier = Modifier,
    tone: OrdiaStatusTone = OrdiaStatusTone.NEUTRAL
) {
    val accentColor = when (tone) {
        OrdiaStatusTone.ACCENT -> MaterialTheme.colorScheme.primary
        OrdiaStatusTone.ACTIVITY -> MaterialTheme.colorScheme.secondary
        OrdiaStatusTone.HUMAN -> ordiaTokens.human
        OrdiaStatusTone.SUCCESS -> ordiaTokens.success
        OrdiaStatusTone.WARNING -> ordiaTokens.warning
        OrdiaStatusTone.DANGER -> ordiaTokens.danger
        OrdiaStatusTone.NEUTRAL -> MaterialTheme.colorScheme.onSurface
    }
    Column(modifier, horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            count,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Separador sutil: divisores que casi no se ven, por diseño.
 */
@Composable
fun OrdiaSoftDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)) {
            Spacer(Modifier.width(1.dp).height(1.dp))
        }
    }
}
