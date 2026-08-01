package com.ordia.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ordia.app.R
import com.ordia.app.data.preferences.InterfaceMode
import com.ordia.app.ui.components.GuardianAvatar
import com.ordia.app.ui.components.GuardianMood
import com.ordia.app.ui.components.ordiaWorkSurface

@Composable
fun OnboardingScreen(
    selectedMode: InterfaceMode,
    onModeSelected: (InterfaceMode) -> Unit,
    onFinish: () -> Unit
) {
    var page by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().ordiaWorkSurface().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GuardianAvatar(72.dp, if (page == 2) GuardianMood.HAPPY else GuardianMood.CALM)
            Text(
                when (page) {
                    0 -> stringResource(R.string.app_tagline)
                    1 -> stringResource(R.string.onboarding_page1_title)
                    else -> stringResource(R.string.onboarding_page2_title)
                },
                style = MaterialTheme.typography.displaySmall,
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InterfaceMode.entries.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { onModeSelected(mode) },
                            label = { Text(mode.label()) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { index ->
                    Text(
                        if (index == page) "●" else "○",
                        modifier = Modifier.padding(4.dp),
                        color = if (index == page) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                if (page > 0) OutlinedButton(onClick = { page-- }) { Text(stringResource(R.string.onboarding_back)) }
                Spacer(Modifier.width(10.dp))
                Button(onClick = { if (page < 2) page++ else onFinish() }) { Text(if (page < 2) stringResource(R.string.onboarding_continue) else stringResource(R.string.onboarding_enter)) }
            }
        }
    }
}

@Composable
private fun InterfaceMode.label(): String = when (this) {
    InterfaceMode.SIMPLE -> stringResource(R.string.onboarding_mode_simple)
    InterfaceMode.ORGANIZED -> stringResource(R.string.onboarding_mode_organized)
    InterfaceMode.ADVANCED -> stringResource(R.string.onboarding_mode_advanced)
}
