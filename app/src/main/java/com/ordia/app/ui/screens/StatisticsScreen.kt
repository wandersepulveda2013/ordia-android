package com.ordia.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ordia.app.R
import com.ordia.app.domain.DateRules
import com.ordia.app.domain.HabitRules
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.components.EmptyState
import com.ordia.app.ui.components.ProgressRing
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.ui.components.SectionHeader
import com.ordia.app.ui.components.SectionSurface
import com.ordia.app.ui.components.StatCard
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Suppress("NonObservableLocale")
@Composable
private fun composeLocale(): Locale {
    val locales = LocalConfiguration.current.locales
    return if (locales.isEmpty()) Locale.getDefault() else locales.get(0)
}

@Composable
fun StatisticsScreen(state: OrdiaUiState, contentPadding: PaddingValues) {
    val currentLocale = composeLocale()
    val completedByDay = (6 downTo 0).map { offset ->
        val date = LocalDate.now().minusDays(offset.toLong())
        date to state.rootTasks.count { task ->
            task.completedAt?.let { DateRules.toLocalDate(it) == date } == true
        }
    }
    val completedThisWeek = completedByDay.sumOf { it.second }
    val dailyAverage = completedThisWeek / 7f
    val habitsDoneToday = state.habits.count { HabitRules.isCompleted(state.habitCount(it.id), it.targetPerPeriod) }
    val habitCompletion = if (state.habits.isEmpty()) 0f else habitsDoneToday.toFloat() / state.habits.size
    val bestStreak = state.habits.maxOfOrNull { state.habitStreak(it) } ?: 0
    val completedFocusSessions = state.focusSessions.count { it.completed }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            20.dp,
            contentPadding.calculateTopPadding() + 20.dp,
            20.dp,
            contentPadding.calculateBottomPadding() + 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            ScreenHeader(
                stringResource(R.string.statistics_eyebrow),
                stringResource(R.string.statistics_title),
                stringResource(R.string.statistics_subtitle)
            )
        }

        item {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(22.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(stringResource(R.string.statistics_pace), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(stringResource(R.string.statistics_completed, state.completionRate), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            when {
                                state.rootTasks.isEmpty() -> stringResource(R.string.statistics_msg_empty)
                                state.completionRate >= 80 -> stringResource(R.string.statistics_msg_high)
                                state.completionRate >= 50 -> stringResource(R.string.statistics_msg_mid)
                                else -> stringResource(R.string.statistics_msg_low)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                        )
                    }
                    ProgressRing(
                        progress = state.completionRate / 100f,
                        centerText = "${state.completionRate}%",
                        size = 96.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                    )
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    StatCard(
                        stringResource(R.string.statistics_week),
                        completedThisWeek.toString(),
                        stringResource(R.string.statistics_tasks_completed),
                        Modifier.width(180.dp),
                        icon = Icons.Outlined.CheckCircle
                    )
                }
                item {
                    StatCard(
                        stringResource(R.string.statistics_average),
                        String.format(currentLocale, "%.1f", dailyAverage),
                        stringResource(R.string.statistics_closures_per_day),
                        Modifier.width(180.dp),
                        icon = Icons.Outlined.CheckCircle,
                        accent = MaterialTheme.colorScheme.secondary
                    )
                }
                item {
                    StatCard(
                        stringResource(R.string.statistics_focus),
                        "${state.focusMinutesThisWeek}m",
                        stringResource(R.string.statistics_sessions, completedFocusSessions),
                        Modifier.width(180.dp),
                        icon = Icons.Outlined.Timer,
                        accent = MaterialTheme.colorScheme.tertiary
                    )
                }
                item {
                    StatCard(
                        stringResource(R.string.statistics_best_streak),
                        stringResource(R.string.statistics_days, bestStreak),
                        stringResource(R.string.statistics_active_habits),
                        Modifier.width(180.dp),
                        icon = Icons.Outlined.Spa,
                        accent = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        item { SectionHeader(stringResource(R.string.statistics_activity_7d), stringResource(R.string.statistics_activity_7d_desc)) }
        item {
            SectionSurface {
                WeeklyBars(
                    values = completedByDay.map { it.second },
                    labels = completedByDay.map {
                        it.first.dayOfWeek.getDisplayName(TextStyle.NARROW, currentLocale).uppercase()
                    }
                )
                Text(
                    if (completedThisWeek == 0) stringResource(R.string.statistics_no_completions_week) else stringResource(R.string.statistics_week_summary, completedThisWeek, String.format(currentLocale, "%.1f", dailyAverage)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { SectionHeader(stringResource(R.string.statistics_habits), stringResource(R.string.statistics_habits_desc)) }
        if (state.habits.isEmpty()) {
            item { EmptyState(stringResource(R.string.statistics_no_habits), stringResource(R.string.statistics_no_habits_desc)) }
        } else {
            item {
                SectionSurface {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        ProgressRing(
                            progress = habitCompletion,
                            centerText = "$habitsDoneToday/${state.habits.size}",
                            label = stringResource(R.string.statistics_done_today),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            state.habits.sortedByDescending { state.habitStreak(it) }.take(5).forEach { habit ->
                                val streak = state.habitStreak(habit)
                                val progress = (state.habitCount(habit.id).toFloat() / habit.targetPerPeriod.coerceAtLeast(1)).coerceIn(0f, 1f)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth()) {
                                        Text(habit.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        Text(stringResource(R.string.statistics_days, streak), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(5.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.statistics_projects), stringResource(R.string.statistics_projects_desc)) }
        if (state.projects.isEmpty()) {
            item { EmptyState(stringResource(R.string.statistics_no_projects), stringResource(R.string.statistics_no_projects_desc)) }
        } else {
            item {
                SectionSurface {
                    state.projects.sortedByDescending { state.projectProgress(it.id) }.take(6).forEach { project ->
                        val progress = state.projectProgress(project.id)
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(project.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyBars(values: List<Int>, labels: List<String>) {
    val barColor = MaterialTheme.colorScheme.primary
    val todayColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(190.dp)) {
            val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1
            val slot = size.width / values.size.coerceAtLeast(1)
            drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
            drawLine(gridColor.copy(alpha = 0.5f), Offset(0f, size.height * 0.5f), Offset(size.width, size.height * 0.5f), strokeWidth = 1f)
            values.forEachIndexed { index, value ->
                val height = size.height * (value.toFloat() / max)
                drawRoundRect(
                    color = if (value == 0) gridColor.copy(alpha = 0.55f) else if (index == values.lastIndex) todayColor else barColor,
                    topLeft = Offset(index * slot + slot * 0.2f, size.height - height.coerceAtLeast(5f)),
                    size = Size(slot * 0.6f, height.coerceAtLeast(5f)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            labels.forEachIndexed { index, label ->
                Column(Modifier.weight(1f), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    Text(values.getOrElse(index) { 0 }.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
