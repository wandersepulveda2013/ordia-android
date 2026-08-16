package com.ordia.app.ui.screens
import com.ordia.app.ui.components.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ordia.app.R
import com.ordia.app.data.preferences.InterfaceMode
import com.ordia.app.ui.theme.OrdiaAccent

/**
 * Bienvenida compacta inspirada en las referencias suministradas: ilustración
 * lineal, mucho espacio útil, una sola decisión por página y CTA inequívoco.
 * No depende de recursos remotos y conserva el estado al rotar.
 */
@Composable
fun OnboardingScreen(
    selectedMode: InterfaceMode,
    onModeSelected: (InterfaceMode) -> Unit,
    onFinish: () -> Unit,
    finishing: Boolean = false
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            Modifier.fillMaxWidth().widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                stringResource(R.string.app_short_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            OnboardingArtwork(page)
            Text(
                when (page) {
                    0 -> stringResource(R.string.app_tagline)
                    1 -> stringResource(R.string.onboarding_page1_title)
                    else -> stringResource(R.string.onboarding_page2_title)
                },
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Text(
                when (page) {
                    0 -> stringResource(R.string.onboarding_page0_body)
                    1 -> stringResource(R.string.onboarding_page1_body)
                    else -> stringResource(R.string.onboarding_page2_body)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (page == 1) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InterfaceMode.entries.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { onModeSelected(mode) },
                            label = { Text(mode.label()) },
                            leadingIcon = if (selectedMode == mode) {
                                { Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp)) }
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(3) { index ->
                    Box(
                        Modifier
                            .size(if (index == page) 9.dp else 7.dp)
                            .background(
                                if (index == page) OrdiaAccent else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape
                            )
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                if (page > 0) {
                    OrdiaOutlinedButton(
                        onClick = { page-- },
                        enabled = !finishing,
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.onboarding_back)) }
                    Spacer(Modifier.width(10.dp))
                }
                OrdiaButton(
                    onClick = {
                        if (page < 2) page++ else if (!finishing) onFinish()
                    },
                    enabled = !finishing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (page < 2) stringResource(R.string.onboarding_continue)
                        else stringResource(R.string.onboarding_enter)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun OnboardingArtwork(page: Int) {
    val icon = when (page) {
        0 -> Icons.Outlined.Inbox
        1 -> Icons.Outlined.AutoAwesome
        else -> Icons.Outlined.Lock
    }
    Surface(
        modifier = Modifier.fillMaxWidth().height(210.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
            Surface(shape = CircleShape, color = OrdiaAccent.copy(alpha = 0.09f)) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(28.dp).size(62.dp),
                    tint = OrdiaAccent
                )
            }
            MiniIllustrationCard(
                icon = Icons.AutoMirrored.Outlined.Notes,
                modifier = Modifier.align(Alignment.TopStart)
            )
            MiniIllustrationCard(
                icon = Icons.Outlined.CheckCircle,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
            Box(
                Modifier.align(Alignment.TopEnd).size(8.dp)
                    .background(OrdiaAccent, CircleShape)
            )
            Box(
                Modifier.align(Alignment.BottomStart).size(6.dp)
                    .background(MaterialTheme.colorScheme.onSurface, CircleShape)
            )
        }
    }
}

@Composable
private fun MiniIllustrationCard(icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.padding(14.dp).size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun InterfaceMode.label(): String = when (this) {
    InterfaceMode.SIMPLE -> stringResource(R.string.onboarding_mode_simple)
    InterfaceMode.ORGANIZED -> stringResource(R.string.onboarding_mode_organized)
    InterfaceMode.ADVANCED -> stringResource(R.string.onboarding_mode_advanced)
}
