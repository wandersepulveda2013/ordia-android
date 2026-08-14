package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Búsqueda por intención de fecha: escribir "hoy", "mañana", "esta semana" o
 * "atrasadas"/"vencidas" devuelve las tareas de ese rango aunque su título no
 * contenga esa palabra. Antes solo funcionaba "vencidas" (parcialmente) y el
 * resto devolvía vacío, rompiendo una búsqueda universal esperable.
 */
class SearchEngineDateScopeTest {

    private val now = java.time.LocalDateTime.of(2026, 8, 13, 12, 0) // jueves
        .toInstant(ZoneOffset.UTC).toEpochMilli()
    private val day = 24L * 3600_000

    private val tasks = listOf(
        TaskEntity(id = 1, title = "Reunión de equipo", dueAt = now),                 // hoy
        TaskEntity(id = 2, title = "Enviar informe", dueAt = now + day),              // mañana
        TaskEntity(id = 3, title = "Pagar factura", dueAt = now + 2 * day),           // pasado mañana
        TaskEntity(id = 4, title = "Llamada cliente", dueAt = now - 3 * day),         // atrasada
        TaskEntity(id = 5, title = "Sin fecha")                                       // sin vencimiento
    )

    @Test fun hoy_returnsOnlyTasksDueToday() {
        val ids = SearchEngine.search("hoy", tasks, emptyList(), emptyList(), emptyList(), now = now).map { it.id }
        assertEquals(listOf(1L), ids)
    }

    @Test fun manana_returnsOnlyTasksDueTomorrow() {
        val ids = SearchEngine.search("mañana", tasks, emptyList(), emptyList(), emptyList(), now = now).map { it.id }
        assertEquals(listOf(2L), ids)
    }

    @Test fun estaSemana_returnsTasksFromTodayToEndOfWeek() {
        // Usar instantes a las 09:00 en la zona por defecto para evitar desbordamiento de día
        // cuando el sistema opera en otra zona horaria.
        val zone = java.time.ZoneId.systemDefault()
        val todayLocal = java.time.LocalDate.of(2026, 8, 13) // jueves
        val t0 = java.time.ZonedDateTime.of(todayLocal.atTime(9, 0), zone).toInstant().toEpochMilli()
        val tasks = listOf(
            TaskEntity(id = 1, title = "Hoy", dueAt = t0),
            TaskEntity(id = 2, title = "Mañana", dueAt = java.time.ZonedDateTime.of(todayLocal.plusDays(1).atTime(9, 0), zone).toInstant().toEpochMilli()),
            TaskEntity(id = 3, title = "Pasado", dueAt = java.time.ZonedDateTime.of(todayLocal.plusDays(2).atTime(9, 0), zone).toInstant().toEpochMilli()),
            TaskEntity(id = 4, title = "Atrasada", dueAt = java.time.ZonedDateTime.of(todayLocal.minusDays(3).atTime(9, 0), zone).toInstant().toEpochMilli()),
            TaskEntity(id = 5, title = "Sin fecha")
        )
        val ids = SearchEngine.search("esta semana", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        // Jueves 2026-08-13: la semana abarca hasta el domingo 2026-08-16.
        assertEquals(setOf(1L, 2L, 3L), ids)
    }

    @Test fun atrasadas_returnsOnlyOverdueTasks() {
        val ids = SearchEngine.search("atrasadas", tasks, emptyList(), emptyList(), emptyList(), now = now).map { it.id }
        assertEquals(listOf(4L), ids)
    }

    @Test fun vencidas_returnsOnlyOverdueTasks() {
        val ids = SearchEngine.search("vencidas", tasks, emptyList(), emptyList(), emptyList(), now = now).map { it.id }
        assertEquals(listOf(4L), ids)
    }

    @Test fun estaSemana_onSundayEndsTodayNotNextWeek() {
        // Domingo 2026-08-16: la semana termina HOY (domingo), no abarca hasta el
        // domingo siguiente. Antes del fix, `daysToSunday = 7 - (7 % 7) = 7`
        // arrastraba tareas de la semana siguiente (id 2, lunes próximo) a
        // "esta semana". Tras el fix, solo queda lo de hoy (id 1); la atrasada
        // (id 3) se excluye del rango "esta semana" (tiene su propio filtro
        // "atrasadas"), igual que en el caso del jueves ya cubierto.
        val zone = java.time.ZoneId.systemDefault()
        val sundayLocal = java.time.LocalDate.of(2026, 8, 16) // domingo
        val t0 = java.time.ZonedDateTime.of(sundayLocal.atTime(9, 0), zone).toInstant().toEpochMilli()
        val tasks = listOf(
            TaskEntity(id = 1, title = "Hoy domingo", dueAt = t0),
            TaskEntity(id = 2, title = "Lunes que viene", dueAt = java.time.ZonedDateTime.of(sundayLocal.plusDays(1).atTime(9, 0), zone).toInstant().toEpochMilli()),
            TaskEntity(id = 3, title = "Atrasada", dueAt = java.time.ZonedDateTime.of(sundayLocal.minusDays(3).atTime(9, 0), zone).toInstant().toEpochMilli())
        )
        val ids = SearchEngine.search("esta semana", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun completedTasksAreExcludedFromDateScopes() {
        val completedToday = TaskEntity(id = 10, title = "Ya hecha", dueAt = now, completed = true)
        val all = tasks + completedToday
        val ids = SearchEngine.search("hoy", all, emptyList(), emptyList(), emptyList(), now = now).map { it.id }
        assertTrue("La tarea completada no debe aparecer en 'hoy'", 10L !in ids)
    }

    @Test fun dateScopeCombinedWithTextFiltersByBoth() {
        // "hoy reunion": solo la tarea de hoy cuyo título contiene "reunión".
        val ids = SearchEngine.search("hoy reunion", tasks, emptyList(), emptyList(), emptyList(), now = now).map { it.id }
        assertEquals(listOf(1L), ids)
    }

    @Test fun archivedTasksExcludedFromDateScopes() {
        val archivedToday = TaskEntity(id = 11, title = "Archivada", dueAt = now, archived = true)
        val all = tasks + archivedToday
        val ids = SearchEngine.search("hoy", all, emptyList(), emptyList(), emptyList(), now = now).map { it.id }
        assertTrue(11L !in ids)
    }

    @Test fun overdueScopeRanksUrgentOverdueFirst() {
        val urgent = TaskEntity(id = 41, title = "Factura luz", priority = TaskPriority.URGENT, dueAt = now - 5 * day)
        val normal = TaskEntity(id = 42, title = "Otra atrasada", dueAt = now - day)
        val ids = SearchEngine.search("atrasadas", listOf(urgent, normal), emptyList(), emptyList(), emptyList(), now = now).map { it.id }
        assertEquals(listOf(41L, 42L), ids)
    }
}
