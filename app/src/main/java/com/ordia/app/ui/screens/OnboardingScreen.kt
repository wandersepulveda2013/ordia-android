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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ordia.app.data.preferences.InterfaceMode
import com.ordia.app.ui.components.GuardianAvatar
import com.ordia.app.ui.components.GuardianMood
import com.ordia.app.ui.components.ordiaWorkSurface
import com.ordia.app.ui.components.OrdiaButton

@Composable
fun OnboardingScreen(
    selectedMode: InterfaceMode,
    onModeSelected: (InterfaceMode) -> Unit,
    onFinish: () -> Unit
) {
    var page by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().ordiaWorkSurface().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            GuardianAvatar(92.dp, if (page == 2) GuardianMood.HAPPY else GuardianMood.CALM)
            Text(
                when (page) {
                    0 -> "Tu mundo, en orden"
                    1 -> "Ordia se adapta a ti"
                    else -> "Un compañero, no una molestia"
                },
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center
            )
            Text(
                when (page) {
                    0 -> "Ordia es tu sistema personal para organizar tareas, notas y rutinas sin distracciones. Todo funciona en tu dispositivo."
                    1 -> "Elige la interfaz que mejor encaje con tu momento actual. Puedes cambiarla cuando quieras."
                    else -> "El Guardián te avisará si estás aplazando demasiado o si estás a punto de perder el control. ¿Empezamos?"
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (page == 1) {
                Spacer(Modifier.size(12.dp))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InterfaceMode.entries.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { onModeSelected(mode) },
                            label = { Text(mode.name) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(Modifier.size(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (page > 0) {
                    OrdiaButton(onClick = { page-- }, label = "Atrás", primary = false)
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                OrdiaButton(
                    onClick = { if (page < 2) page++ else onFinish() },
                    label = if (page < 2) "Siguiente" else "Comenzar"
                )
            }
        }
    }
}
