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
import com.ordia.app.ui.components.core.GuardianAvatar
import com.ordia.app.ui.components.core.GuardianMood
import com.ordia.app.ui.components.core.ordiaWorkSurface

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
                    0 -> "Tareas, notas, planes, hábitos y enfoque en un lugar tranquilo. Empieza sin construir un sistema complicado."
                    1 -> "Elige cuánta estructura quieres ver. Puedes cambiarlo después en Ajustes."
                    else -> "El guardián flotante te permitirá capturar una tarea o una idea desde cualquier aplicación. Solo aparece cuando tú lo activas."
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
                if (page > 0) OutlinedButton(onClick = { page-- }) { Text("Atrás") }
                Spacer(Modifier.width(10.dp))
                Button(onClick = { if (page < 2) page++ else onFinish() }) { Text(if (page < 2) "Continuar" else "Entrar a Ordia") }
            }
        }
    }
}

private fun InterfaceMode.label() = when (this) {
    InterfaceMode.SIMPLE -> "Simple · Tareas, notas y calendario"
    InterfaceMode.ORGANIZED -> "Organizado · Proyectos, hábitos y planificación"
    InterfaceMode.ADVANCED -> "Avanzado · Etiquetas, vistas y controles adicionales"
}
