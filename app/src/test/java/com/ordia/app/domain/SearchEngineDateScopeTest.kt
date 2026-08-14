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

    // --- Búsqueda por parte del día (tarde/noche/madrugada), solo HOY ---

    private val zone = java.time.ZoneId.systemDefault()

    private fun atHour(localDate: java.time.LocalDate, hour: Int): Long =
        java.time.ZonedDateTime.of(localDate.atTime(hour, 0), zone).toInstant().toEpochMilli()

    @Test fun tarde_returnsOnlyTasksDueTodayAfternoon() {
        val today = java.time.LocalDate.of(2026, 8, 13)
        val pod = listOf(
            TaskEntity(id = 1, title = "Cita médica", dueAt = atHour(today, 15)),   // tarde de hoy
            TaskEntity(id = 2, title = "Desayuno", dueAt = atHour(today, 8)),       // mañana de hoy
            TaskEntity(id = 3, title = "Cena", dueAt = atHour(today, 20)),           // noche de hoy
            TaskEntity(id = 4, title = "Tarde de ayer", dueAt = atHour(today.minusDays(1), 15)), // ayer tarde
            TaskEntity(id = 5, title = "Tarde de mañana", dueAt = atHour(today.plusDays(1), 15)) // mañana tarde
        )
        val ids = SearchEngine.search("tarde", pod, emptyList(), emptyList(), emptyList(), now = atHour(today, 12)).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun estaTarde_matchesAfternoonOnly() {
        val today = java.time.LocalDate.of(2026, 8, 13)
        val pod = listOf(
            TaskEntity(id = 1, title = "Reunión", dueAt = atHour(today, 17)),
            TaskEntity(id = 2, title = "Compra", dueAt = atHour(today, 7)),
            TaskEntity(id = 3, title = "Trámite", dueAt = atHour(today, 12)) // 12:00 entra en tarde (12-17)
        )
        val ids = SearchEngine.search("esta tarde", pod, emptyList(), emptyList(), emptyList(), now = atHour(today, 6)).map { it.id }.toSet()
        assertEquals(setOf(1L, 3L), ids)
    }

    @Test fun noche_returnsOnlyTasksDueTonight() {
        val today = java.time.LocalDate.of(2026, 8, 13)
        val pod = listOf(
            TaskEntity(id = 1, title = "Llamada", dueAt = atHour(today, 21)),        // noche
            TaskEntity(id = 2, title = "Café", dueAt = atHour(today, 16)),          // tarde
            TaskEntity(id = 3, title = "Última hora", dueAt = atHour(today, 23))     // noche (límite)
        )
        val ids = SearchEngine.search("noche", pod, emptyList(), emptyList(), emptyList(), now = atHour(today, 10)).map { it.id }.toSet()
        assertEquals(setOf(1L, 3L), ids)
    }

    @Test fun madrugada_returnsOnlyEarlyMorningToday() {
        val today = java.time.LocalDate.of(2026, 8, 13)
        val pod = listOf(
            TaskEntity(id = 1, title = "Vuelo", dueAt = atHour(today, 4)),           // madrugada
            TaskEntity(id = 2, title = "Almuerzo", dueAt = atHour(today, 13)),       // tarde
            TaskEntity(id = 3, title = "Madrugón anterior", dueAt = atHour(today.minusDays(1), 3)) // ayer
        )
        val ids = SearchEngine.search("madrugada", pod, emptyList(), emptyList(), emptyList(), now = atHour(today, 1)).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun tardeConTexto_filtraDentroDeLaFranja() {
        val today = java.time.LocalDate.of(2026, 8, 13)
        val pod = listOf(
            TaskEntity(id = 1, title = "Cita médica", dueAt = atHour(today, 15)),
            TaskEntity(id = 2, title = "Cita peluquería", dueAt = atHour(today, 16)),
            TaskEntity(id = 3, title = "Cita médica temprano", dueAt = atHour(today, 8))
        )
        val ids = SearchEngine.search("tarde cita medica", pod, emptyList(), emptyList(), emptyList(), now = atHour(today, 11)).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun parteDelDia_excluyeCompletadas() {
        val today = java.time.LocalDate.of(2026, 8, 13)
        val pod = listOf(
            TaskEntity(id = 1, title = "Hecha", dueAt = atHour(today, 15), completed = true),
            TaskEntity(id = 2, title = "Pendiente", dueAt = atHour(today, 16))
        )
        val ids = SearchEngine.search("tarde", pod, emptyList(), emptyList(), emptyList(), now = atHour(today, 10)).map { it.id }.toSet()
        assertEquals(setOf(2L), ids)
    }

    // --- Búsqueda por mes ("este mes"/"próximo mes"/"mes que viene"/"mes pasado") ---
    // Simétrico al scope de semana: permite recuperar lo que vence en un mes
    // sin recorrer la lista, aunque el título no contenga "mes".

    private fun monthTask(id: Long, title: String, localDate: java.time.LocalDate): TaskEntity =
        TaskEntity(id = id, title = title, dueAt = java.time.ZonedDateTime.of(localDate.atTime(9, 0), zone).toInstant().toEpochMilli())

    @Test fun esteMes_returnsOnlyTasksDueThisMonth() {
        // "este mes" recupera las tareas que vencen en el mes en curso, aunque
        // su título no contenga la palabra "mes". Simétrico a "esta semana".
        val today = java.time.LocalDate.of(2026, 8, 13) // agosto
        val t0 = java.time.ZonedDateTime.of(today.atTime(9, 0), zone).toInstant().toEpochMilli()
        val pod = listOf(
            monthTask(1, "Renta", today.plusDays(5)),            // 08-18 este mes
            monthTask(2, "Auditoría", today.plusDays(40)),        // ~09-22 mes que viene
            monthTask(3, "Julio", today.minusDays(20))            // ~07-24 mes pasado
        )
        val ids = SearchEngine.search("este mes", pod, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun esteMes_coversFullCalendarMonth() {
        // El scope "este mes" abarca todo el mes natural (del 1 al último día),
        // no solo desde hoy: una tarea del día 1 del mismo mes entra aunque ya
        // sea pasado (es recuperación, no planificación).
        val today = java.time.LocalDate.of(2026, 8, 13)
        val t0 = java.time.ZonedDateTime.of(today.atTime(9, 0), zone).toInstant().toEpochMilli()
        val pod = listOf(
            monthTask(1, "Principios de mes", java.time.LocalDate.of(2026, 8, 2)),  // este mes, ya pasado
            monthTask(2, "Fin de mes", java.time.LocalDate.of(2026, 8, 31)),        // este mes, futuro
            monthTask(3, "Otro mes", java.time.LocalDate.of(2026, 9, 1))            // mes que viene
        )
        val ids = SearchEngine.search("este mes", pod, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L, 2L), ids)
    }

    @Test fun proximoMes_returnsOnlyNextMonthTasks() {
        val today = java.time.LocalDate.of(2026, 8, 13)
        val t0 = java.time.ZonedDateTime.of(today.atTime(9, 0), zone).toInstant().toEpochMilli()
        val pod = listOf(
            monthTask(1, "Renta", today.plusDays(5)),            // este mes
            monthTask(2, "Auditoría", today.plusDays(40)),        // septiembre
            monthTask(3, "Julio", today.minusDays(20))            // julio
        )
        val ids = SearchEngine.search("proximo mes", pod, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(2L), ids)
    }

    @Test fun mesQueViene_isEquivalentToProximoMes() {
        val today = java.time.LocalDate.of(2026, 8, 13)
        val t0 = java.time.ZonedDateTime.of(today.atTime(9, 0), zone).toInstant().toEpochMilli()
        val pod = listOf(
            monthTask(1, "Renta", today.plusDays(5)),
            monthTask(2, "Auditoría", today.plusDays(40))
        )
        val ids = SearchEngine.search("mes que viene", pod, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(2L), ids)
    }

    @Test fun mesPasado_returnsOnlyLastMonthTasks() {
        // "mes pasado" recupera tareas del mes anterior (recuperación, incluye
        // completadas como los scopes pasados de semana).
        val today = java.time.LocalDate.of(2026, 8, 13)
        val t0 = java.time.ZonedDateTime.of(today.atTime(9, 0), zone).toInstant().toEpochMilli()
        val pod = listOf(
            monthTask(1, "Renta", today.plusDays(5)),            // agosto
            monthTask(2, "Auditoría", today.plusDays(40)),        // septiembre
            monthTask(3, "Julio", java.time.LocalDate.of(2026, 7, 25)),  // julio
            monthTask(4, "Junio", java.time.LocalDate.of(2026, 6, 30))   // junio (más allá)
        )
        val ids = SearchEngine.search("mes pasado", pod, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(3L), ids)
    }

    @Test fun mesPasada_conTilde_activaLastMonth() {
        // "mes pasada" (variante con tilde informal) también activa LAST_MONTH.
        val today = java.time.LocalDate.of(2026, 8, 13)
        val t0 = java.time.ZonedDateTime.of(today.atTime(9, 0), zone).toInstant().toEpochMilli()
        val pod = listOf(
            monthTask(1, "Julio", java.time.LocalDate.of(2026, 7, 25)),
            monthTask(2, "Agosto", today.plusDays(5))
        )
        val ids = SearchEngine.search("el mes pasado", pod, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun mesSola_noActivaScopeSiNoHayCalificador() {
        // "mes" sola NO debe activar un scope de fecha (ambigua: ¿este mes? ¿el
        // concepto "mes"?); cae a búsqueda de contenido. Evita activar month scope
        // por accidente en frases como "resumen del mes".
        val today = java.time.LocalDate.of(2026, 8, 13)
        val t0 = java.time.ZonedDateTime.of(today.atTime(9, 0), zone).toInstant().toEpochMilli()
        val pod = listOf(
            monthTask(1, "Resumen del mes", today.plusDays(5)),
            monthTask(2, "Otra tarea", today.plusDays(40))
        )
        val ids = SearchEngine.search("mes", pod, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun esteMesConTexto_filtraDentroDelMes() {
        // "este mes renta" combina el scope de mes con texto de contenido.
        val today = java.time.LocalDate.of(2026, 8, 13)
        val t0 = java.time.ZonedDateTime.of(today.atTime(9, 0), zone).toInstant().toEpochMilli()
        val pod = listOf(
            monthTask(1, "Pagar renta", today.plusDays(5)),
            monthTask(2, "Luz", java.time.LocalDate.of(2026, 8, 20))
        )
        val ids = SearchEngine.search("este mes renta", pod, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    // --- "completadas hoy"/"completadas esta semana": recuperar lo terminado
    // en un período presente. Antes del fix el filtro wantsCompleted chocaba con
    // los scopes NO pasados (que excluyen completadas) → resultado vacío. Ahora,
    // cuando el usuario busca "completadas [scope presente]", se ancla la fecha en
    // completedAt (cuándo la terminó) y se permite mostrar completed en ese rango.

    @Test fun completadasHoy_returnsOnlyTasksCompletedToday() {
        // "completadas hoy" recupera las tareas terminadas HOY (por completedAt),
        // aunque su título no lo diga. Una terminada ayer NO entra.
        val today = java.time.LocalDate.of(2026, 8, 13)
        val t0 = atHour(today, 12)
        val tasks = listOf(
            TaskEntity(id = 1, title = "Pagar luz", completed = true, completedAt = atHour(today, 10), dueAt = atHour(today, 9)),
            TaskEntity(id = 2, title = "Llamar cliente", completed = true, completedAt = atHour(today.minusDays(1), 10), dueAt = atHour(today.minusDays(1), 9)),
            TaskEntity(id = 3, title = "Comprar pan", completed = false, dueAt = atHour(today, 15))
        )
        val ids = SearchEngine.search("completadas hoy", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun hechasHoy_isEquivalentToCompletadasHoy() {
        // "hechas hoy" es sinónimo coloquial: misma intención que "completadas hoy".
        val today = java.time.LocalDate.of(2026, 8, 13)
        val t0 = atHour(today, 12)
        val tasks = listOf(
            TaskEntity(id = 1, title = "Enviar informe", completed = true, completedAt = atHour(today, 8)),
            TaskEntity(id = 2, title = "Ayer", completed = true, completedAt = atHour(today.minusDays(1), 8))
        )
        val ids = SearchEngine.search("hechas hoy", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun completadasEstaSemana_returnsTasksCompletedThisWeek() {
        // Jueves 2026-08-13: esta semana = lun 08-10..dom 08-16. "completadas esta
        // semana" recupera las terminadas en ese rango (por completedAt), incluso
        // las de lunes (ya pasado). Una terminada el domingo anterior NO entra.
        val today = java.time.LocalDate.of(2026, 8, 13) // jueves
        val t0 = atHour(today, 12)
        val tasks = listOf(
            TaskEntity(id = 1, title = "Lunes", completed = true, completedAt = atHour(today.minusDays(3), 9)), // 08-10 esta semana
            TaskEntity(id = 2, title = "Domingo pasado", completed = true, completedAt = atHour(today.minusDays(4), 9)), // 08-09 semana pasada
            TaskEntity(id = 3, title = "Pendiente", completed = false, dueAt = atHour(today, 15))
        )
        val ids = SearchEngine.search("completadas esta semana", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun completadasHoy_excludesPendingEvenIfDueToday() {
        // Una tarea pendiente vencida hoy NO debe aparecer en "completadas hoy":
        // el filtro de estado exige completed real, y el scope se ancla en
        // completedAt (la pendiente no lo tiene) → se excluye.
        val today = java.time.LocalDate.of(2026, 8, 13)
        val t0 = atHour(today, 12)
        val tasks = listOf(
            TaskEntity(id = 1, title = "Hecha", completed = true, completedAt = atHour(today, 9)),
            TaskEntity(id = 2, title = "Pendiente vence hoy", completed = false, dueAt = atHour(today, 18))
        )
        val ids = SearchEngine.search("completadas hoy", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }
}
