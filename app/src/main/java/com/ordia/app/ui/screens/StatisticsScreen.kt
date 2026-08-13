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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.ui.components.StatCard
import java.time.LocalDate

@Composable
fun StatisticsScreen(state: OrdiaUiState, contentPadding: PaddingValues) {
    val completedByDay = (6 downTo 0).map { offset ->
        val date = LocalDate.now().minusDays(offset.toLong())
        date to state.rootTasks.count { task -> task.completedAt?.let { com.ordia.app.domain.DateRules.toLocalDate(it) == date } == true }
    }
    val completedByDay30 = (29 downTo 0).map { offset ->
        val date = LocalDate.now().minusDays(offset.toLong())
        state.rootTasks.count { task -> task.completedAt?.let { com.ordia.app.domain.DateRules.toLocalDate(it) == date } == true }
    }
    val priorityCounts = com.ordia.app.data.local.TaskPriority.entries.associateWith { p ->
        state.rootTasks.count { !it.archived && !it.completed && it.priority == p }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, contentPadding.calculateTopPadding() + 20.dp, 20.dp, contentPadding.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { ScreenHeader("PROGRESO SIN PRESIÓN", "Tu semana", "Mira patrones útiles, no una puntuación de tu valor.") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Completado", "${state.completionRate}%", "tareas registradas", Modifier.weight(1f))
                StatCard("Enfoque", "${state.focusMinutesThisWeek}m", "últimos 7 días", Modifier.weight(1f))
                StatCard("Hábitos", state.habits.count { state.habitCount(it.id) >= it.targetPerPeriod }.toString(), "cumplidos hoy", Modifier.weight(1f))
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Tareas completadas por día", style = MaterialTheme.typography.titleLarge)
                    WeeklyBars(completedByDay.map { it.second })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        completedByDay.forEach { (date, _) -> Text(date.dayOfWeek.name.take(1), style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Últimos 30 días", style = MaterialTheme.typography.titleLarge)
                    MonthlyBars(completedByDay30)
                    Text(
                        "Total: ${completedByDay30.sum()} tareas completadas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Pendientes por prioridad", style = MaterialTheme.typography.titleLarge)
                    priorityCounts.forEach { (priority, count) ->
                        val maxCount = (priorityCounts.values.maxOrNull() ?: 1).coerceAtLeast(1)
                        val fraction = count.toFloat() / maxCount
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(priorityLabel(priority), style = MaterialTheme.typography.bodyMedium)
                                Text("$count", style = MaterialTheme.typography.labelLarge)
                            }
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth(),
                                color = priorityBarColor(priority)
                            )
                        }
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Rachas actuales", style = MaterialTheme.typography.titleLarge)
                    if (state.habits.isEmpty()) Text("Crea un hábito para empezar a ver rachas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.habits.sortedByDescending { state.habitStreak(it) }.take(6).forEach { habit ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(habit.title, modifier = Modifier.weight(1f))
                            Text("${state.habitStreak(habit)} días", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyBars(values: List<Int>) {
    val barColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1
        drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
        val slot = size.width / values.size
        values.forEachIndexed { index, value ->
            val height = size.height * (value.toFloat() / max)
            drawRoundRect(
                color = if (value == 0) gridColor else barColor,
                topLeft = Offset(index * slot + slot * 0.22f, size.height - height),
                size = Size(slot * 0.56f, height.coerceAtLeast(4f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
            )
        }
    }
}

@Composable
private fun MonthlyBars(values: List<Int>) {
    val barColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1
        val slot = size.width / values.size
        values.forEachIndexed { index, value ->
            val height = size.height * (value.toFloat() / max)
            drawRect(
                color = if (value == 0) gridColor else barColor,
                topLeft = Offset(index * slot + slot * 0.15f, size.height - height),
                size = Size(slot * 0.7f, height.coerceAtLeast(2f))
            )
        }
    }
}

@Composable
private fun priorityBarColor(priority: com.ordia.app.data.local.TaskPriority): Color = when (priority) {
    com.ordia.app.data.local.TaskPriority.URGENT -> MaterialTheme.colorScheme.error
    com.ordia.app.data.local.TaskPriority.HIGH -> Color(0xFFC98A2B)
    com.ordia.app.data.local.TaskPriority.NORMAL -> MaterialTheme.colorScheme.secondary
    com.ordia.app.data.local.TaskPriority.LOW -> MaterialTheme.colorScheme.outline
}

private fun priorityLabel(priority: com.ordia.app.data.local.TaskPriority): String = when (priority) {
    com.ordia.app.data.local.TaskPriority.URGENT -> "Urgente"
    com.ordia.app.data.local.TaskPriority.HIGH -> "Alta"
    com.ordia.app.data.local.TaskPriority.NORMAL -> "Normal"
    com.ordia.app.data.local.TaskPriority.LOW -> "Baja"
}
