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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ordia.app.OrdiaApplication
import com.ordia.app.R
import com.ordia.app.data.preferences.GuardianSpecies
import com.ordia.app.domain.GuardianEngine
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.ui.components.SectionHeader
import com.ordia.app.ui.components.StatCard
import com.ordia.app.ui.components.VirtualGuardian
import com.ordia.app.ui.descriptionRes
import com.ordia.app.ui.labelRes
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
        preferences = state.preferences,
        commitments = state.commitments
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
                eyebrow = stringResource(R.string.guardian_screen_header_eyebrow),
                title = stringResource(R.string.guardian_screen_refuge_title, snapshot.name),
                subtitle = stringResource(R.string.guardian_screen_header_subtitle)
            )
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VirtualGuardian(
                        snapshot = snapshot,
                        size = 160.dp,
                        animationsEnabled = state.preferences.guardianAnimations && !state.preferences.reduceMotion
                    )
                    Text(
                        stringResource(R.string.guardian_screen_identity_line, stringResource(snapshot.species.labelRes()), stringResource(snapshot.stage.labelRes()), snapshot.level),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(
                            stringResource(R.string.guardian_screen_personality, stringResource(snapshot.archetype.labelRes())),
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
                            stringResource(R.string.guardian_screen_next_stage_progress, snapshot.experienceToNext, stringResource(it.labelRes()), (snapshot.progressToNext * 100).toInt())
                        } ?: stringResource(R.string.guardian_screen_max_evolution),
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
                        stringResource(R.string.guardian_screen_bond_label),
                        snapshot.bond.toString(),
                        stringResource(R.string.guardian_screen_bond_unit),
                        Modifier.width(172.dp),
                        Icons.Outlined.Favorite,
                        MaterialTheme.colorScheme.secondary
                    )
                }
                item {
                    StatCard(
                        stringResource(R.string.guardian_screen_energy_label),
                        "${snapshot.energy}%",
                        stringResource(R.string.guardian_screen_energy_state),
                        Modifier.width(172.dp),
                        Icons.Outlined.BatteryChargingFull,
                        MaterialTheme.colorScheme.tertiary
                    )
                }
                item {
                    StatCard(
                        stringResource(R.string.guardian_screen_experience_label),
                        snapshot.experience.toString(),
                        stringResource(R.string.guardian_screen_experience_detail, snapshot.activityExperience, snapshot.bondExperience),
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
                            Text(stringResource(R.string.guardian_screen_daily_care), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.guardian_screen_daily_care_progress, snapshot.dailyGoalsCompleted, snapshot.dailyGoalsTotal),
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
                stringResource(R.string.guardian_screen_dynamics_title),
                if (snapshot.interactionsRemaining > 0) stringResource(R.string.guardian_screen_dynamics_remaining, snapshot.interactionsRemaining)
                else stringResource(R.string.guardian_screen_dynamics_complete)
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(GuardianEngine.Interaction.entries) { interaction ->
                    Button(onClick = { scope.launch { repository.interactGuardian(interaction) } }) {
                        Text(stringResource(interaction.labelRes()))
                    }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.guardian_screen_evolution_path), stringResource(snapshot.archetype.descriptionRes())) }
        item { SectionHeader(stringResource(R.string.guardian_screen_growth_activity), stringResource(R.string.guardian_screen_growth_activity_sub)) }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProgressLine(stringResource(R.string.guardian_screen_tasks_today_title), snapshot.completedToday, stringResource(R.string.guardian_screen_tasks_today_desc))
                    ProgressLine(stringResource(R.string.guardian_screen_focus_minutes_title), snapshot.focusMinutesToday, stringResource(R.string.guardian_screen_focus_minutes_desc))
                    ProgressLine(stringResource(R.string.guardian_screen_habits_today_title), snapshot.habitsDoneToday, stringResource(R.string.guardian_screen_habits_today_desc))
                }
            }
        }

        item { SectionHeader(stringResource(R.string.guardian_screen_identity_title), stringResource(R.string.guardian_screen_identity_subtitle)) }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(24) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.guardian_screen_name_label)) },
                        singleLine = true
                    )
                    OutlinedButton(
                        onClick = { scope.launch { repository.setGuardianName(name) } },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Psychology, null)
                        Text(stringResource(R.string.guardian_screen_save_name), Modifier.padding(start = 8.dp))
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(GuardianSpecies.entries) { species ->
                            FilterChip(
                                selected = state.preferences.guardianSpecies == species,
                                onClick = { scope.launch { repository.setGuardianSpecies(species) } },
                                label = { Text(stringResource(species.labelRes())) }
                            )
                        }
                    }
                    Text(
                        stringResource(state.preferences.guardianSpecies.descriptionRes()),
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
                    stringResource(R.string.guardian_screen_kindness_note),
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
