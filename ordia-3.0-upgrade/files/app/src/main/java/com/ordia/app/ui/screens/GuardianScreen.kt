package com.ordia.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ordia.app.OrdiaApplication
import com.ordia.app.data.preferences.GuardianSpecies
import com.ordia.app.domain.GuardianEngine
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.ui.components.SectionHeader
import com.ordia.app.ui.components.StatCard
import com.ordia.app.ui.components.VirtualGuardian
import kotlinx.coroutines.launch

@Composable
fun GuardianScreen(state: OrdiaUiState, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val repository = (context.applicationContext as OrdiaApplication).container.preferencesRepository
    val scope = rememberCoroutineScope()
    val snapshot = GuardianEngine.snapshot(
        tasks = state.tasks,
        habits = state.habits,
        habitLogs = state.habitLogs,
        focusSessions = state.focusSessions,
        notes = state.notes,
        preferences = state.preferences
    )
    var name by remember(state.preferences.guardianName) { mutableStateOf(state.preferences.guardianName) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = contentPadding.calculateTopPadding() + 20.dp,
            end = 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 36.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ScreenHeader(
                eyebrow = "COMPAÑERO VIRTUAL",
                title = "El refugio de ${snapshot.name}",
                subtitle = "Tu guardián evoluciona con acciones reales: tareas, hábitos, notas y sesiones de enfoque."
            )
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    VirtualGuardian(
                        snapshot = snapshot,
                        size = 220.dp,
                        animationsEnabled = state.preferences.guardianAnimations && !state.preferences.reduceMotion
                    )
                    Text(
                        "${snapshot.species.label} · ${snapshot.stage.label} · Nivel ${snapshot.level}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(
                            "Personalidad: ${snapshot.archetype.label}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Text(
                        snapshot.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(
                        progress = { snapshot.progressToNext },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        snapshot.nextStage?.let {
                            "Faltan ${snapshot.experienceToNext} XP para ${it.label} · ${(snapshot.progressToNext * 100).toInt()}%"
                        } ?: "Evolución máxima alcanzada",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    StatCard(
                        "Vínculo",
                        snapshot.bond.toString(),
                        "puntos",
                        Modifier.width(172.dp),
                        Icons.Outlined.Favorite,
                        MaterialTheme.colorScheme.secondary
                    )
                }
                item {
                    StatCard(
                        "Energía",
                        "${snapshot.energy}%",
                        "estado actual",
                        Modifier.width(172.dp),
                        Icons.Outlined.BatteryChargingFull,
                        MaterialTheme.colorScheme.tertiary
                    )
                }
                item {
                    StatCard(
                        "Experiencia",
                        snapshot.experience.toString(),
                        "${snapshot.activityExperience} actividad + ${snapshot.bondExperience} vínculo",
                        Modifier.width(205.dp),
                        Icons.Outlined.AutoAwesome,
                        MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Cuidado diario", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${snapshot.dailyGoalsCompleted} de ${snapshot.dailyGoalsTotal} señales saludables completadas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("${snapshot.dailyGoalsCompleted}/${snapshot.dailyGoalsTotal}", style = MaterialTheme.typography.titleLarge)
                    }
                    LinearProgressIndicator(
                        progress = { snapshot.dailyGoalsCompleted.toFloat() / snapshot.dailyGoalsTotal.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(snapshot.suggestedAction, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            SectionHeader(
                "Dinámicas",
                if (snapshot.interactionsRemaining > 0) "${snapshot.interactionsRemaining} interacciones con vínculo disponibles hoy."
                else "Puedes seguir interactuando; el vínculo diario ya está completo."
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(GuardianEngine.Interaction.entries) { interaction ->
                    Button(onClick = { scope.launch { repository.interactGuardian(interaction) } }) {
                        Text(interaction.label)
                    }
                }
            }
        }

        item { SectionHeader("Camino de evolución", snapshot.archetype.description) }
        item { SectionHeader("Actividad que lo hace crecer", "El progreso del guardián representa tu actividad real.") }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProgressLine("Tareas completadas hoy", snapshot.completedToday, "Cada tarea aporta experiencia.")
                    ProgressLine("Minutos de enfoque hoy", snapshot.focusMinutesToday, "El tiempo real de concentración alimenta su energía.")
                    ProgressLine("Hábitos cumplidos hoy", snapshot.habitsDoneToday, "Las rachas desbloquean rasgos y efectos visuales.")
                }
            }
        }

        item { SectionHeader("Identidad", "Puedes cambiar de especie sin perder tu progreso.") }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(24) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nombre del guardián") },
                        singleLine = true
                    )
                    OutlinedButton(
                        onClick = { scope.launch { repository.setGuardianName(name) } },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Psychology, null)
                        Text("Guardar nombre", Modifier.padding(start = 8.dp))
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(GuardianSpecies.entries) { species ->
                            FilterChip(
                                selected = state.preferences.guardianSpecies == species,
                                onClick = { scope.launch { repository.setGuardianSpecies(species) } },
                                label = { Text(species.label) }
                            )
                        }
                    }
                    Text(
                        state.preferences.guardianSpecies.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    "Ordia no enferma ni castiga al guardián cuando descansas. La mascota acompaña tu vida; no intenta convertirla en una obligación.",
                    modifier = Modifier.padding(18.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun ProgressLine(title: String, value: Int, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                value.toString(),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
