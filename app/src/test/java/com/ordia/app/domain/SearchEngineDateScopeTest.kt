package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.ProjectEntity
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

    @Test fun semanaQueViene_returnsOnlyNextWeekTasks() {
        // Jueves 2026-08-13: esta semana va a dom 08-16; la próxima semana es
        // lun 08-17 .. dom 08-23.
        val zone = java.time.ZoneId.systemDefault()
        val todayLocal = java.time.LocalDate.of(2026, 8, 13) // jueves
        val t0 = java.time.ZonedDateTime.of(todayLocal.atTime(9, 0), zone).toInstant().toEpochMilli()
        val tasks = listOf(
            TaskEntity(id = 1, title = "Hoy", dueAt = t0),
            TaskEntity(id = 2, title = "Lunes próximo", dueAt = java.time.ZonedDateTime.of(todayLocal.plusDays(4).atTime(9, 0), zone).toInstant().toEpochMilli()), // 08-17
            TaskEntity(id = 3, title = "Domingo próximo", dueAt = java.time.ZonedDateTime.of(todayLocal.plusDays(10).atTime(9, 0), zone).toInstant().toEpochMilli()), // 08-23
            TaskEntity(id = 4, title = "Fuera de rango", dueAt = java.time.ZonedDateTime.of(todayLocal.plusDays(11).atTime(9, 0), zone).toInstant().toEpochMilli()) // 08-24 (siguiente)
        )
        val ids = SearchEngine.search("semana que viene", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(2L, 3L), ids)
    }

    @Test fun proximaSemana_returnsOnlyNextWeekTasks() {
        // Sinónimo "próxima semana" debe activar el mismo scope NEXT_WEEK.
        val zone = java.time.ZoneId.systemDefault()
        val todayLocal = java.time.LocalDate.of(2026, 8, 13) // jueves
        val t0 = java.time.ZonedDateTime.of(todayLocal.atTime(9, 0), zone).toInstant().toEpochMilli()
        val tasks = listOf(
            TaskEntity(id = 1, title = "Hoy", dueAt = t0),
            TaskEntity(id = 2, title = "Lunes próximo", dueAt = java.time.ZonedDateTime.of(todayLocal.plusDays(4).atTime(9, 0), zone).toInstant().toEpochMilli()),
            TaskEntity(id = 3, title = "Domingo próximo", dueAt = java.time.ZonedDateTime.of(todayLocal.plusDays(10).atTime(9, 0), zone).toInstant().toEpochMilli())
        )
        val ids = SearchEngine.search("próxima semana", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(2L, 3L), ids)
    }

    @Test fun semanaQueViene_excludesThisWeekTasks() {
        // "semana que viene" NO debe incluir tareas de esta semana (la distinción
        // clave frente a "esta semana").
        val zone = java.time.ZoneId.systemDefault()
        val todayLocal = java.time.LocalDate.of(2026, 8, 13) // jueves
        val t0 = java.time.ZonedDateTime.of(todayLocal.atTime(9, 0), zone).toInstant().toEpochMilli()
        val tasks = listOf(
            TaskEntity(id = 1, title = "Hoy", dueAt = t0), // esta semana
            TaskEntity(id = 2, title = "Domingo de esta semana", dueAt = java.time.ZonedDateTime.of(todayLocal.plusDays(3).atTime(9, 0), zone).toInstant().toEpochMilli()), // 08-16 (esta semana)
            TaskEntity(id = 3, title = "Lunes próximo", dueAt = java.time.ZonedDateTime.of(todayLocal.plusDays(4).atTime(9, 0), zone).toInstant().toEpochMilli()) // 08-17 (próxima)
        )
        val ids = SearchEngine.search("la semana que viene", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(3L), ids)
    }

    @Test fun semanaSolaSigueSiendoEstaSemana() {
        // No-regresión: "semana" sin modificador de próxima sigue siendo THIS_WEEK.
        val zone = java.time.ZoneId.systemDefault()
        val todayLocal = java.time.LocalDate.of(2026, 8, 13) // jueves
        val t0 = java.time.ZonedDateTime.of(todayLocal.atTime(9, 0), zone).toInstant().toEpochMilli()
        val tasks = listOf(
            TaskEntity(id = 1, title = "Hoy", dueAt = t0), // esta semana
            TaskEntity(id = 2, title = "Lunes próximo", dueAt = java.time.ZonedDateTime.of(todayLocal.plusDays(4).atTime(9, 0), zone).toInstant().toEpochMilli()) // próxima semana
        )
        val ids = SearchEngine.search("semana", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun semanaQueViene_onSundayStartsNextMonday() {
        // Domingo 2026-08-16: "semana que viene" = lun 08-17 .. dom 08-23.
        val zone = java.time.ZoneId.systemDefault()
        val sundayLocal = java.time.LocalDate.of(2026, 8, 16) // domingo
        val t0 = java.time.ZonedDateTime.of(sundayLocal.atTime(9, 0), zone).toInstant().toEpochMilli()
        val tasks = listOf(
            TaskEntity(id = 1, title = "Hoy domingo", dueAt = t0), // esta semana (hoy)
            TaskEntity(id = 2, title = "Lunes que viene", dueAt = java.time.ZonedDateTime.of(sundayLocal.plusDays(1).atTime(9, 0), zone).toInstant().toEpochMilli()), // 08-17
            TaskEntity(id = 3, title = "Domingo que viene", dueAt = java.time.ZonedDateTime.of(sundayLocal.plusDays(7).atTime(9, 0), zone).toInstant().toEpochMilli()) // 08-23
        )
        val ids = SearchEngine.search("próxima semana", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(2L, 3L), ids)
    }

    // --- Recuperación de tareas pasadas ("ayer", "semana pasada") ---

    @Test fun ayer_recoversTaskDueYesterday() {
        // Jueves 2026-08-13: ayer = mié 08-12.
        val zone = java.time.ZoneId.systemDefault()
        val todayLocal = java.time.LocalDate.of(2026, 8, 13) // jueves
        val t0 = java.time.ZonedDateTime.of(todayLocal.atTime(9, 0), zone).toInstant().toEpochMilli()
        val tasks = listOf(
            TaskEntity(id = 1, title = "Hoy", dueAt = t0),
            TaskEntity(id = 2, title = "Ayer", dueAt = java.time.ZonedDateTime.of(todayLocal.minusDays(1).atTime(9, 0), zone).toInstant().toEpochMilli()), // 08-12
            TaskEntity(id = 3, title = "Anteayer", dueAt = java.time.ZonedDateTime.of(todayLocal.minusDays(2).atTime(9, 0), zone).toInstant().toEpochMilli()) // 08-11
        )
        val ids = SearchEngine.search("ayer", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }
        assertEquals(listOf(2L), ids)
    }

    @Test fun ayer_recoversEvenCompletedTask() {
        // "ayer" recupera información: incluye tareas ya completadas.
        val zone = java.time.ZoneId.systemDefault()
        val todayLocal = java.time.LocalDate.of(2026, 8, 13) // jueves
        val t0 = java.time.ZonedDateTime.of(todayLocal.atTime(9, 0), zone).toInstant().toEpochMilli()
        val tasks = listOf(
            TaskEntity(id = 1, title = "Hecha ayer", dueAt = java.time.ZonedDateTime.of(todayLocal.minusDays(1).atTime(9, 0), zone).toInstant().toEpochMilli(), completed = true)
        )
        val ids = SearchEngine.search("ayer", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }
        assertEquals(listOf(1L), ids)
    }

    @Test fun semanaPasada_recoversTasksFromPreviousWeek() {
        // Jueves 2026-08-13: esta semana = lun 08-10..dom 08-16; pasada = lun 08-03..dom 08-09.
        val zone = java.time.ZoneId.systemDefault()
        val todayLocal = java.time.LocalDate.of(2026, 8, 13) // jueves
        val t0 = java.time.ZonedDateTime.of(todayLocal.atTime(9, 0), zone).toInstant().toEpochMilli()
        val tasks = listOf(
            TaskEntity(id = 1, title = "Hoy", dueAt = t0), // esta semana
            TaskEntity(id = 2, title = "Lunes pasado", dueAt = java.time.ZonedDateTime.of(todayLocal.minusDays(10).atTime(9, 0), zone).toInstant().toEpochMilli()), // 08-03
            TaskEntity(id = 3, title = "Domingo pasado", dueAt = java.time.ZonedDateTime.of(todayLocal.minusDays(4).atTime(9, 0), zone).toInstant().toEpochMilli()), // 08-09
            TaskEntity(id = 4, title = "Lunes esta semana", dueAt = java.time.ZonedDateTime.of(todayLocal.minusDays(3).atTime(9, 0), zone).toInstant().toEpochMilli()) // 08-10 (esta semana)
        )
        val ids = SearchEngine.search("semana pasada", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(2L, 3L), ids)
    }

    @Test fun ultimaSemana_recoversTasksWithAccent() {
        // "última semana" (con tilde) también activa LAST_WEEK.
        val zone = java.time.ZoneId.systemDefault()
        val todayLocal = java.time.LocalDate.of(2026, 8, 13) // jueves
        val t0 = java.time.ZonedDateTime.of(todayLocal.atTime(9, 0), zone).toInstant().toEpochMilli()
        val tasks = listOf(
            TaskEntity(id = 1, title = "Mié pasado", dueAt = java.time.ZonedDateTime.of(todayLocal.minusDays(8).atTime(9, 0), zone).toInstant().toEpochMilli()) // 08-05
        )
        val ids = SearchEngine.search("última semana", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }
        assertEquals(listOf(1L), ids)
    }

    @Test fun semanaPasada_excludesThisWeekTasks() {
        // Regresión: "semana pasada" NO trae tareas de esta semana.
        val zone = java.time.ZoneId.systemDefault()
        val todayLocal = java.time.LocalDate.of(2026, 8, 13) // jueves
        val t0 = java.time.ZonedDateTime.of(todayLocal.atTime(9, 0), zone).toInstant().toEpochMilli()
        val tasks = listOf(
            TaskEntity(id = 1, title = "Hoy", dueAt = t0), // 08-13 esta semana
            TaskEntity(id = 2, title = "Lunes esta semana", dueAt = java.time.ZonedDateTime.of(todayLocal.minusDays(3).atTime(9, 0), zone).toInstant().toEpochMilli()) // 08-10 esta semana
        )
        val ids = SearchEngine.search("la semana pasada", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertTrue("Esta semana no debe aparecer en 'la semana pasada'", ids.isEmpty())
    }

    // --- Recuperación de tareas sin fecha ("sin fecha"/"sin vencimiento") ---

    @Test fun sinFecha_returnsOnlyUndatedTasks() {
        // "sin fecha" recupera las tareas capturadas pero nunca agendadas.
        val ids = SearchEngine.search("sin fecha", tasks, emptyList(), emptyList(), emptyList(), now = now).map { it.id }
        assertEquals(listOf(5L), ids)
    }

    @Test fun sinVencimiento_returnsOnlyUndatedTasks() {
        // Sinónimo "sin vencimiento" activa el mismo scope UNDATED.
        val ids = SearchEngine.search("sin vencimiento", tasks, emptyList(), emptyList(), emptyList(), now = now).map { it.id }
        assertEquals(listOf(5L), ids)
    }

    @Test fun sinFecha_excludesCompletedUndatedTasks() {
        // Una tarea sin fecha pero ya completada no es "olvidada"; se excluye.
        val completedUndated = TaskEntity(id = 20, title = "Ya hecha sin fecha", completed = true)
        val all = tasks + completedUndated
        val ids = SearchEngine.search("sin fecha", all, emptyList(), emptyList(), emptyList(), now = now).map { it.id }
        assertTrue("La tarea completada sin fecha no debe aparecer", 20L !in ids)
        assertTrue(5L in ids)
    }

    @Test fun sinFechaSinAcento_tambiénFunciona() {
        // "sin dia" (sin tilde) también debe activar el scope UNDATED.
        val withDia = tasks + TaskEntity(id = 21, title = "Pendiente sin día")
        val ids = SearchEngine.search("sin dia", withDia, emptyList(), emptyList(), emptyList(), now = now).map { it.id }.toSet()
        assertEquals(setOf(5L, 21L), ids)
    }

    @Test fun negacionAjena_noActivaScopeUndated() {
        // "sin" sin sustantivo de fecha no debe activar UNDATED: "sin azúcar"
        // es una búsqueda de contenido normal, no un scope de fecha.
        val sweet = tasks + TaskEntity(id = 22, title = "Café sin azúcar")
        val ids = SearchEngine.search("sin azucar", sweet, emptyList(), emptyList(), emptyList(), now = now).map { it.id }
        assertEquals(listOf(22L), ids)
    }

    // --- Scope de fecha puro: el ruido no fecha-able se suprime ---

    @Test fun pureDateScope_excludesDatelessEntities() {
        // Buscar solo "hoy" (scope de fecha sin palabras de contenido) NO debe
        // devolver notas, proyectos, hábitos, conversaciones, compromisos ni
        // automatizaciones: nada de eso tiene fecha que filtrar, así que
        // incluirlos inunda la búsqueda de ruido sin señal. Antes del fix
        // "hoy" devolvía cada nota y cada proyecto aunque no tuvieran relación.
        val ids = SearchEngine.search(
            "hoy",
            tasks = listOf(TaskEntity(id = 1, title = "Reunión de equipo", dueAt = now)),
            projects = listOf(ProjectEntity(id = 2, name = "Proyecto sin fecha")),
            notes = listOf(NoteEntity(id = 3, title = "Idea suelta", body = "Nota sin fecha")),
            habits = listOf(HabitEntity(id = 4, title = "Hábito sin fecha", details = "")),
            now = now
        ).map { it.id }
        assertEquals(listOf(1L), ids)
    }

    @Test fun pureDateScope_overdueExcludesDatelessEntities() {
        // Igual para "vencidas": solo tareas atrasadas, nada de notas/proyectos.
        val overdue = TaskEntity(id = 41, title = "Factura atrasada", dueAt = now - 3 * day)
        val ids = SearchEngine.search(
            "vencidas",
            tasks = listOf(overdue),
            projects = listOf(ProjectEntity(id = 2, name = "Proyecto")),
            notes = listOf(NoteEntity(id = 3, title = "Nota", body = "")),
            habits = emptyList(),
            now = now
        ).map { it.id }
        assertEquals(listOf(41L), ids)
    }

    @Test fun dateScopeWithContent_returnsMatchingDatelessEntities() {
        // "hoy reunion" SÍ puede traer una nota sobre "reunión": hay palabra de
        // contenido real además del scope de fecha, así que el contenido fecha-
        // menos sigue siendo relevante (no se suprime, solo se filtra por texto).
        val taskToday = TaskEntity(id = 1, title = "Reunión de equipo", dueAt = now)
        val noteReunion = NoteEntity(id = 2, title = "Reunión", body = "Temas de la reunión")
        val noteAjena = NoteEntity(id = 3, title = "Compras", body = "Lista del super")
        val ids = SearchEngine.search(
            "hoy reunion",
            tasks = listOf(taskToday),
            notes = listOf(noteReunion, noteAjena),
            habits = emptyList(),
            projects = emptyList(),
            now = now
        ).map { it.id }.toSet()
        assertTrue("La tarea de hoy debe aparecer", 1L in ids)
        assertTrue("La nota sobre reunión es relevante", 2L in ids)
        assertTrue("La nota ajena no debe aparecer", 3L !in ids)
    }
}
