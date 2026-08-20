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

    // "pasado mañana" contiene el token "mañana": antes detectDateScope caía a
    // TOMORROW y la búsqueda devolvía las de MAÑANA (id=2) en vez de las de pasado
    // mañana (id=3). Misma mentira de agenda que AssistantEngine; aquí se verifica
    // que la búsqueda universal responde honestamente sobre pasado mañana.
    @Test fun pasadoManana_returnsOnlyTasksDueDayAfterTomorrow() {
        val ids = SearchEngine.search("pasado mañana", tasks, emptyList(), emptyList(), emptyList(), now = now).map { it.id }
        assertEquals(listOf(3L), ids)
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

    // --- "esta mañana": la mañana (franja 6-11) de HOY ---
    //
    // El demostrativo "esta" desambigua "mañana": "esta mañana" es la mañana de
    // HOY, jamás tomorrow. El parser de captura y el motor de contexto ya lo
    // resolvían a hoy 09:00 (c.592), pero la búsqueda lo mandaba a MAÑANA por el
    // token suelto "manana": capturar "cita esta mañana" nacía hoy 09:00 y buscar
    // "esta mañana" mostraba lo de mañana — la mentira cruzada entre superficies.
    // La franja 6-11 cierra el hueco horario entre MADRUGADA (0-5) y TARDE (12-17).

    @Test fun estaManana_returnsOnlyThisMorningToday() {
        val today = java.time.LocalDate.of(2026, 8, 13)
        val pod = listOf(
            TaskEntity(id = 1, title = "Café con Ana", dueAt = atHour(today, 9)),              // esta mañana
            TaskEntity(id = 2, title = "Despertar", dueAt = atHour(today, 6)),                 // esta mañana (borde inferior)
            TaskEntity(id = 3, title = "Cierre de mañana", dueAt = atHour(today, 11)),         // esta mañana (borde superior)
            TaskEntity(id = 4, title = "Almuerzo", dueAt = atHour(today, 13)),                 // tarde de hoy
            TaskEntity(id = 5, title = "Mediodía", dueAt = atHour(today, 12)),                 // ya es tarde (12-17)
            TaskEntity(id = 6, title = "Vuelo", dueAt = atHour(today, 3)),                     // madrugada de hoy
            TaskEntity(id = 7, title = "Mañana de mañana", dueAt = atHour(today.plusDays(1), 9)) // tomorrow
        )
        val ids = SearchEngine.search("esta mañana", pod, emptyList(), emptyList(), emptyList(), now = atHour(today, 8)).map { it.id }.toSet()
        assertEquals(setOf(1L, 2L, 3L), ids)
    }

    @Test fun mananaSola_sigueSiendoTomorrow() {
        // Control anti-regresión: "mañana" SIN demostrativo sigue siendo TOMORROW
        // (la lectura dominante del token suelto); sólo "esta mañana" se reancla a hoy.
        val today = java.time.LocalDate.of(2026, 8, 13)
        val pod = listOf(
            TaskEntity(id = 1, title = "Hoy 9h", dueAt = atHour(today, 9)),
            TaskEntity(id = 2, title = "Mañana 9h", dueAt = atHour(today.plusDays(1), 9))
        )
        val ids = SearchEngine.search("mañana", pod, emptyList(), emptyList(), emptyList(), now = atHour(today, 8)).map { it.id }.toSet()
        assertEquals(setOf(2L), ids)
    }

    @Test fun estaMananaConTexto_filtraDentroDeLaFranja() {
        val today = java.time.LocalDate.of(2026, 8, 13)
        val pod = listOf(
            TaskEntity(id = 1, title = "Cita médica", dueAt = atHour(today, 8)),
            TaskEntity(id = 2, title = "Cita peluquería", dueAt = atHour(today, 16))
        )
        val ids = SearchEngine.search("esta mañana cita", pod, emptyList(), emptyList(), emptyList(), now = atHour(today, 7)).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun estaManana_encuentraPorStartAtAunqueVenzaMasTarde() {
        // Un slot de HOY 09:30 cuyo plazo vence al mediodía: es "de esta mañana"
        // (misma lógica startAt ∨ dueAt que el resto de partes del día).
        val today = java.time.LocalDate.of(2026, 8, 13)
        val pod = listOf(
            TaskEntity(id = 1, title = "Trabajo enfocado", startAt = atHour(today, 9), dueAt = atHour(today, 12))
        )
        val ids = SearchEngine.search("esta mañana", pod, emptyList(), emptyList(), emptyList(), now = atHour(today, 8)).map { it.id }.toSet()
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

    @Test fun parteDelDia_encuentraTareaCuyoStartAtHoyOtraFranjaYDueAtHoyEnLaFranja() {
        // Un compromiso agendado a las 10:00 (mañana) cuyo PLAZO vence esta tarde
        // (15:00). El usuario que pregunta "esta tarde" necesita verlo: vence esta
        // tarde. PRE-fix, la rama de parte del día tomaba startAt (10:00, hoy) como
        // ancla EXCLUSIVA y comprobaba sólo su hora (10 ∉ 12..17) → false; jamás
        // miraba dueAt (15:00, sí en la franja), mintiendo por omisión. Inconsistente
        // con el resto de scopes (hoy/semana/mes), que ya hacen OR de startAt ∨ dueAt.
        val today = java.time.LocalDate.of(2026, 8, 13)
        val pod = listOf(
            TaskEntity(id = 1, title = "Entrega", startAt = slotAt(today, 10), dueAt = atHour(today, 15)),
            TaskEntity(id = 2, title = "Slot mañana sin plazo tarde", startAt = slotAt(today, 9), dueAt = atHour(today, 20)),
            TaskEntity(id = 3, title = "Slot tarde puro", startAt = slotAt(today, 16))
        )
        val ids = SearchEngine.search("esta tarde", pod, emptyList(), emptyList(), emptyList(), now = atHour(today, 8)).map { it.id }.toSet()
        assertEquals(setOf(1L, 3L), ids)
    }

    @Test fun parteDelDia_noCaeFalsamenteCuandoAmbasMarcasHoyEstanFueraDeLaFranja() {
        // startAt hoy 10:00 y dueAt hoy 11:00, ambos en la mañana: NUNCA es "esta
        // tarde". Garantiza que el OR de ambas marcas no infla falsos positivos.
        val today = java.time.LocalDate.of(2026, 8, 13)
        val pod = listOf(
            TaskEntity(id = 1, title = "Mañana pura", startAt = slotAt(today, 10), dueAt = atHour(today, 11))
        )
        val ids = SearchEngine.search("esta tarde", pod, emptyList(), emptyList(), emptyList(), now = atHour(today, 8)).map { it.id }.toSet()
        assertEquals(emptySet<Long>(), ids)
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

    // --- Recuperación de tareas olvidadas ("olvidadas") ---
    // "olvidadas" recupera lo que el usuario olvidó: una tarea vencida (plazo
    // incumplido) O una cuyo hueco planificado ya pasó sin completarse
    // ([TaskRules.isMissedStart] — el "olvido silencioso"). Antes estas últimas
    // eran irrecuperables por búsqueda: no son "vencidas" (sin dueAt o con
    // dueAt futuro) y su título no contiene "olvidada". Es la unión honesta del
    // tema #1 de recuperación del producto, ahora visible en la superficie de
    // búsqueda universal (sin nueva pantalla). isMissedStart excluye por
    // definición a las vencidas, así que la unión con isOverdue no duplica.

    private fun missedStartTask(id: Long, title: String, hoursAgo: Long, durationMinutes: Int = 25): TaskEntity {
        // Hueco que empezó hace N horas: ya pasó la ventana (start + duración)
        // sin completarse, pero NO es vencida (sin dueAt).
        return TaskEntity(
            id = id,
            title = title,
            startAt = now - hoursAgo * 3600_000L,
            durationMinutes = durationMinutes,
            status = TaskStatus.PLANNED
        )
    }

    @Test fun olvidadas_returnsMissedStartAndOverdueTasks() {
        val missed = missedStartTask(10, "Llamada que se me pasó", hoursAgo = 3)
        val overdue = TaskEntity(id = 11, title = "Factura vencida", dueAt = now - 2 * day)
        val scheduled = TaskEntity(id = 12, title = "Cita futura", startAt = now + 3 * 3600_000L, durationMinutes = 25, status = TaskStatus.PLANNED)
        val inbox = TaskEntity(id = 13, title = "Idea suelta", createdAt = now)
        val tasks = listOf(missed, overdue, scheduled, inbox)
        val ids = SearchEngine.search("olvidadas", tasks, emptyList(), emptyList(), emptyList(), now = now).map { it.id }.toSet()
        assertEquals(setOf(10L, 11L), ids)
    }

    @Test fun olvidadas_includesOverdueWithoutStart() {
        // Una vencida sin startAt también es "olvidada" (plazo incumplido).
        val overdue = TaskEntity(id = 21, title = "Renta atrasada", dueAt = now - 5 * day)
        val ids = SearchEngine.search("olvidadas", listOf(overdue), emptyList(), emptyList(), emptyList(), now = now).map { it.id }.toSet()
        assertEquals(setOf(21L), ids)
    }

    @Test fun olvidadas_excludesCompletedMissedStart() {
        // Una tarea cuyo hueco pasó pero ya se completó no es un olvido.
        val done = missedStartTask(30, "Ya hecha", hoursAgo = 4).copy(completed = true, completedAt = now)
        val pending = missedStartTask(31, "Aún pendiente", hoursAgo = 4)
        val ids = SearchEngine.search("olvidadas", listOf(done, pending), emptyList(), emptyList(), emptyList(), now = now).map { it.id }.toSet()
        assertEquals(setOf(31L), ids)
    }

    @Test fun olvidadas_excludesScheduledFutureStart() {
        // Un hueco que aún no empezó no es un olvido.
        val future = TaskEntity(id = 40, title = "Reunión próxima", startAt = now + 2 * 3600_000L, durationMinutes = 25, status = TaskStatus.PLANNED)
        val ids = SearchEngine.search("olvidadas", listOf(future), emptyList(), emptyList(), emptyList(), now = now).map { it.id }
        assertTrue(ids.isEmpty())
    }

    @Test fun olvidadas_excludesTaskStillInProgress() {
        // El hueco empezó pero la ventana aún no cerró (start + duración >= now):
        // está en curso, no es un olvido.
        val inProgress = TaskEntity(
            id = 50,
            title = "Trabajando ahora",
            startAt = now - 5 * 60_000L, // hace 5 min
            durationMinutes = 25, // ventana cierra en ~20 min
            status = TaskStatus.PLANNED
        )
        val ids = SearchEngine.search("olvidadas", listOf(inProgress), emptyList(), emptyList(), emptyList(), now = now).map { it.id }
        assertTrue(ids.isEmpty())
    }

    @Test fun olvidadasConTexto_filtraDentroDelConjunto() {
        // "olvidadas mudanza" recupera solo la olvidada sobre mudanza.
        val mudanza = missedStartTask(60, "Mudanza: llamar camión", hoursAgo = 6)
        val otra = missedStartTask(61, "Comprar regalo", hoursAgo = 6)
        val ids = SearchEngine.search("olvidadas mudanza", listOf(mudanza, otra), emptyList(), emptyList(), emptyList(), now = now).map { it.id }.toSet()
        assertEquals(setOf(60L), ids)
    }

    @Test fun olvidadas_pureDateScopeExcludesDatelessEntities() {
        // "olvidadas" pura devuelve SOLO tareas olvidadas: notas y proyectos
        // (sin fecha que olvidar) no inundan los resultados, igual que "vencidas".
        val missed = missedStartTask(70, "Olvidada", hoursAgo = 2)
        val results = SearchEngine.search(
            "olvidadas",
            tasks = listOf(missed),
            projects = listOf(ProjectEntity(id = 2, name = "Proyecto")),
            notes = listOf(NoteEntity(id = 3, title = "Idea", body = "")),
            habits = emptyList()
        )
        val kinds = results.map { it.kind }.toSet()
        assertEquals(setOf(SearchKind.TASK), kinds)
        assertEquals(listOf(70L), results.map { it.id })
    }

    @Test fun olvidados_masculinoPlural_tambienFunciona() {
        // Formas masculinas/plurales también activan el scope.
        val missed = missedStartTask(80, "Recordatorio perdido", hoursAgo = 2)
        val ids = SearchEngine.search("olvidados", listOf(missed), emptyList(), emptyList(), emptyList(), now = now).map { it.id }
        assertEquals(listOf(80L), ids)
    }

    @Test fun olvidadas_includesStaleInboxCapture() {
        // Una captura de la bandeja SIN fecha ni hueco que lleva >=7 días
        // esperando ([TaskRules.isStaleInbox] — el "tercer olvido" de Ordía,
        // junto a isOverdue e isMissedStart) TAMBIÉN es "olvidada": el propio
        // guardián la rescata como "RECUPERA EL CONTROL ... lleva N en tu
        // bandeja sin fecha". La búsqueda "olvidadas" debe honrar esa misma
        // definición para que el usuario que vio el nudge pueda actuar sobre
        // ella desde la búsqueda universal. No es vencida (sin dueAt) ni
        // missed-start (sin startAt): es la unión del tercer tipo de olvido.
        val staleInbox = TaskEntity(
            id = 90,
            title = "Idea que dejé botada",
            createdAt = now - 8 * day // 8 días en la bandeja, sin fecha ni hueco
        )
        val ids = SearchEngine.search("olvidadas", listOf(staleInbox), emptyList(), emptyList(), emptyList(), now = now).map { it.id }.toSet()
        assertEquals(setOf(90L), ids)
    }

    @Test fun olvidadas_excludesFreshInboxCapture() {
        // Una captura reciente (menos del umbral de 7 días) NO es un olvido:
        // el usuario todavía no la ha arrinconado. Anti-regresión: la frontera
        // de "olvidada" para una captura sin fecha es de calendario, no instantánea.
        val fresh = TaskEntity(
            id = 91,
            title = "Captura de hoy",
            createdAt = now - 2 * day // 2 días: aún no es olvido
        )
        val ids = SearchEngine.search("olvidadas", listOf(fresh), emptyList(), emptyList(), emptyList(), now = now).map { it.id }
        assertTrue(ids.isEmpty())
    }

    // --- Búsqueda por día de la semana ("lunes", "viernes"...) ---
    // Recupera las tareas que vencen ese día de la semana sin exigirlo en el
    // título, simétrico a "hoy"/"mañana"/"esta semana". Resolución idéntica al
    // parser de captura: "lunes" dicho un lunes incluye hoy; con "próximo"/
    // "que viene" salta al estricto siguiente; dicho a mitad de semana resuelve
    // a la próxima ocurrencia hacia adelante.

    private fun weekdayAt(localDate: java.time.LocalDate, hour: Int = 9): Long =
        java.time.ZonedDateTime.of(localDate.atTime(hour, 0), zone).toInstant().toEpochMilli()

    @Test fun lunes_onMonday_includesToday() {
        // Lunes 2026-08-10: "lunes" incluye hoy (misma semántica que el parser).
        val todayLocal = java.time.LocalDate.of(2026, 8, 10) // lunes
        val t0 = weekdayAt(todayLocal, 12)
        val tasks = listOf(
            TaskEntity(id = 1, title = "Hoy lunes", dueAt = weekdayAt(todayLocal)),
            TaskEntity(id = 2, title = "Martes", dueAt = weekdayAt(todayLocal.plusDays(1))),
            TaskEntity(id = 3, title = "Sin fecha")
        )
        val ids = SearchEngine.search("lunes", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun viernes_midWeek_resolvesToNextFriday() {
        // Miércoles 2026-08-12: "viernes" → viernes 08-14 (próxima ocurrencia).
        val todayLocal = java.time.LocalDate.of(2026, 8, 12) // miércoles
        val t0 = weekdayAt(todayLocal, 12)
        val tasks = listOf(
            TaskEntity(id = 1, title = "Este viernes", dueAt = weekdayAt(todayLocal.plusDays(2))),  // 08-14
            TaskEntity(id = 2, title = "Viernes pasado", dueAt = weekdayAt(todayLocal.minusDays(5))), // 08-07
            TaskEntity(id = 3, title = "Próximo viernes", dueAt = weekdayAt(todayLocal.plusDays(9)))   // 08-21
        )
        val ids = SearchEngine.search("viernes", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun proximoLunes_onMonday_jumpsToNextWeek() {
        // Lunes 2026-08-10: "próximo lunes" → lunes 08-17 (estricto, excluye hoy).
        val todayLocal = java.time.LocalDate.of(2026, 8, 10) // lunes
        val t0 = weekdayAt(todayLocal, 12)
        val tasks = listOf(
            TaskEntity(id = 1, title = "Hoy", dueAt = weekdayAt(todayLocal)),
            TaskEntity(id = 2, title = "Próximo", dueAt = weekdayAt(todayLocal.plusDays(7))) // 08-17
        )
        val ids = SearchEngine.search("próximo lunes", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(2L), ids)
    }

    @Test fun lunesQueViene_strictNextWeek() {
        // Lunes 2026-08-10: "lunes que viene" → 08-17 ("que" es stop word).
        val todayLocal = java.time.LocalDate.of(2026, 8, 10) // lunes
        val t0 = weekdayAt(todayLocal, 12)
        val tasks = listOf(
            TaskEntity(id = 1, title = "Hoy", dueAt = weekdayAt(todayLocal)),
            TaskEntity(id = 2, title = "Que viene", dueAt = weekdayAt(todayLocal.plusDays(7)))
        )
        val ids = SearchEngine.search("lunes que viene", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(2L), ids)
    }

    @Test fun viernesReunion_filtersByBothDateAndContent() {
        // "viernes reunion": solo la tarea del viernes con "reunión" en el título.
        val todayLocal = java.time.LocalDate.of(2026, 8, 12) // miércoles
        val t0 = weekdayAt(todayLocal, 12)
        val tasks = listOf(
            TaskEntity(id = 1, title = "Reunión de equipo", dueAt = weekdayAt(todayLocal.plusDays(2))),  // 08-14 viernes
            TaskEntity(id = 2, title = "Informe viernes", dueAt = weekdayAt(todayLocal.plusDays(2))),    // 08-14 viernes
            TaskEntity(id = 3, title = "Otra reunión", dueAt = weekdayAt(todayLocal.plusDays(1)))        // 08-13 jueves
        )
        val ids = SearchEngine.search("viernes reunión", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun lunes_pureDateScopeExcludesDatelessEntities() {
        // "lunes" puro (sin contenido) solo devuelve tareas con dueAt ese día:
        // notas/proyectos sin fecha no inundan los resultados, igual que "hoy".
        val todayLocal = java.time.LocalDate.of(2026, 8, 12) // miércoles
        val t0 = weekdayAt(todayLocal, 12)
        val task = TaskEntity(id = 1, title = "Cita", dueAt = weekdayAt(todayLocal.plusDays(5))) // 08-17 lunes
        val results = SearchEngine.search(
            "lunes",
            tasks = listOf(task),
            projects = listOf(ProjectEntity(id = 2, name = "Proyecto lunes")),
            notes = listOf(NoteEntity(id = 3, title = "Nota lunes", body = "")),
            habits = emptyList(),
            now = t0
        )
        val kinds = results.map { it.kind }.toSet()
        assertEquals(setOf(SearchKind.TASK), kinds)
        assertEquals(listOf(1L), results.map { it.id })
    }

    @Test fun lunes_excludesCompletedTaskDueThatDay() {
        // Una tarea completada que vencía el lunes NO aparece en "lunes"
        // (lectura hacia adelante: ya no es "lo que tengo"). Como en "hoy".
        val todayLocal = java.time.LocalDate.of(2026, 8, 12) // miércoles
        val t0 = weekdayAt(todayLocal, 12)
        val nextMonday = todayLocal.plusDays(5) // 08-17 lunes
        val tasks = listOf(
            TaskEntity(id = 1, title = "Pendiente", dueAt = weekdayAt(nextMonday)),
            TaskEntity(id = 2, title = "Ya hecha", dueAt = weekdayAt(nextMonday), completed = true, completedAt = t0)
        )
        val ids = SearchEngine.search("lunes", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun completadasLunes_anchorsOnCompletedAt() {
        // "completadas lunes" recupera lo terminado ese lunes (por completedAt),
        // no lo que vencía ese lunes. Simétrico a "completadas hoy".
        val todayLocal = java.time.LocalDate.of(2026, 8, 12) // miércoles
        val t0 = weekdayAt(todayLocal, 12)
        val nextMonday = todayLocal.plusDays(5) // 08-17 lunes
        val tasks = listOf(
            // Terminada el próximo lunes aunque vencía antes.
            TaskEntity(id = 1, title = "Hecha el lunes", completed = true, completedAt = weekdayAt(nextMonday, 10), dueAt = weekdayAt(todayLocal.minusDays(1))),
            // Vencía el lunes pero aún pendiente: NO entra en "completadas lunes".
            TaskEntity(id = 2, title = "Pendiente", completed = false, dueAt = weekdayAt(nextMonday))
        )
        val ids = SearchEngine.search("completadas lunes", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun sabado_resolvesCorrectlyWithAccent() {
        // "sábado" (con tilde) se normaliza a "sabado" y resuelve al sábado.
        val todayLocal = java.time.LocalDate.of(2026, 8, 12) // miércoles
        val t0 = weekdayAt(todayLocal, 12)
        val saturday = todayLocal.plusDays(3) // 08-15 sábado
        val tasks = listOf(
            TaskEntity(id = 1, title = "Sábado", dueAt = weekdayAt(saturday)),
            TaskEntity(id = 2, title = "Domingo", dueAt = weekdayAt(saturday.plusDays(1)))
        )
        val ids = SearchEngine.search("sábado", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    // --- Búsqueda por fin de semana ("finde"/"fin de semana") ---
    // Recupera las tareas que vencen el fin de semana (sábado+domingo) sin
    // exigirlo en el título, simétrico a "hoy"/"lunes"/"esta semana". Resolución
    // idéntica al parser de captura (c.33/c.2039): el fin de semana SIEMPRE
    // resuelve al PRÓXIMO sábado estricto (nextWeekday(SATURDAY): si hoy es
    // sábado, salta al siguiente), y abarca ese sábado y su domingo. Así buscar
    // y capturar significan lo mismo. Antes "fin de semana" caía por error a
    // THIS_WEEK (la palabra "semana" disparaba WEEK_TOKENS) e "finde" no tenía
    // scope: ambos devolvían solo contenido literal — una tarea que vence el
    // sábado sin la palabra "finde" en el título era irrecuperable.

    @Test fun finde_midWeek_findsSaturdayAndSunday() {
        // Miércoles 2026-08-12: "finde" → fin de semana del 08-15 (sábado) y
        // 08-16 (domingo). No incluye viernes (08-14) ni lunes (08-17).
        val todayLocal = java.time.LocalDate.of(2026, 8, 12) // miércoles
        val t0 = weekdayAt(todayLocal, 12)
        val sat = todayLocal.plusDays(3) // 08-15 sábado
        val sun = todayLocal.plusDays(4) // 08-16 domingo
        val tasks = listOf(
            TaskEntity(id = 1, title = "Sábado", dueAt = weekdayAt(sat)),
            TaskEntity(id = 2, title = "Domingo", dueAt = weekdayAt(sun)),
            TaskEntity(id = 3, title = "Viernes", dueAt = weekdayAt(todayLocal.plusDays(2))), // 08-14
            TaskEntity(id = 4, title = "Lunes", dueAt = weekdayAt(todayLocal.plusDays(5)))     // 08-17
        )
        val ids = SearchEngine.search("finde", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L, 2L), ids)
    }

    @Test fun finDeSemana_doesNotFallToThisWeek() {
        // Regresión clave: "fin de semana" contiene "semana" y antes disparaba
        // THIS_WEEK (WEEK_TOKENS), inundando con tareas de toda la semana
        // (viernes 08-14, lunes 08-17). Ahora debe resolver al fin de semana
        // (sábado 08-15 + domingo 08-16) únicamente. "de" es stop word.
        val todayLocal = java.time.LocalDate.of(2026, 8, 12) // miércoles
        val t0 = weekdayAt(todayLocal, 12)
        val sat = todayLocal.plusDays(3) // 08-15 sábado
        val sun = todayLocal.plusDays(4) // 08-16 domingo
        val tasks = listOf(
            TaskEntity(id = 1, title = "Sábado", dueAt = weekdayAt(sat)),
            TaskEntity(id = 2, title = "Domingo", dueAt = weekdayAt(sun)),
            TaskEntity(id = 3, title = "Viernes", dueAt = weekdayAt(todayLocal.plusDays(2))), // 08-14 (esta semana)
            TaskEntity(id = 4, title = "Lunes", dueAt = weekdayAt(todayLocal.plusDays(5)))    // 08-17 (esta semana)
        )
        val ids = SearchEngine.search("fin de semana", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L, 2L), ids)
    }

    @Test fun esteFinDeSemana_sameAsFinde() {
        // "este fin de semana" → mismo fin de semana próximo que "finde"
        // (semántica del parser: siempre próximo sábado estricto).
        val todayLocal = java.time.LocalDate.of(2026, 8, 12) // miércoles
        val t0 = weekdayAt(todayLocal, 12)
        val sat = todayLocal.plusDays(3) // 08-15 sábado
        val tasks = listOf(
            TaskEntity(id = 1, title = "Sábado", dueAt = weekdayAt(sat)),
            TaskEntity(id = 2, title = "Próximo sábado", dueAt = weekdayAt(sat.plusDays(7))) // 08-22
        )
        val ids = SearchEngine.search("este fin de semana", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun finde_onSaturday_jumpsToNextWeekend() {
        // Sábado 2026-08-15: "finde" resuelve al PRÓXIMO fin de semana (08-22/
        // 08-23), no al de hoy — simétrico al parser (nextWeekday estricto:
        // si hoy es sábado, +7). El usuario que busca "finde" un sábado por la
        // tarde quiere saber qué le espera el PRÓXIMO, no lo de hoy (que ya
        // está corriendo); coherente con capturar "finde" ese sábado.
        val todayLocal = java.time.LocalDate.of(2026, 8, 15) // sábado
        val t0 = weekdayAt(todayLocal, 12)
        val nextSat = todayLocal.plusDays(7) // 08-22
        val nextSun = todayLocal.plusDays(8) // 08-23
        val tasks = listOf(
            TaskEntity(id = 1, title = "Este sábado", dueAt = weekdayAt(todayLocal)),      // 08-15 hoy
            TaskEntity(id = 2, title = "Próximo sábado", dueAt = weekdayAt(nextSat)),       // 08-22
            TaskEntity(id = 3, title = "Próximo domingo", dueAt = weekdayAt(nextSun))       // 08-23
        )
        val ids = SearchEngine.search("finde", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(2L, 3L), ids)
    }

    // "final de semana" (variante regional latinoamericana de "fin de semana") debe ser
    // un scope WEEKEND en la búsqueda, idéntico a "fin de semana"/"finde". Simetría con el
    // parser: una tarea capturada como "final de semana" (dueAt=sábado) se recupera
    // buscando "final de semana"; y viceversa, buscar "fin de semana" también la encuentra.
    // Antes "final de semana" caía a THIS_WEEK (WEEK_TOKENS vía "semana") y devolvía toda
    // la semana, no el finde. P1 de recuperación en el dialecto regional.
    @Test fun finalDeSemana_resolvesAsWeekendScope() {
        val todayLocal = java.time.LocalDate.of(2026, 8, 12) // miércoles
        val t0 = weekdayAt(todayLocal, 12)
        val sat = todayLocal.plusDays(3) // 08-15 sábado
        val sun = todayLocal.plusDays(4) // 08-16 domingo
        val tasks = listOf(
            TaskEntity(id = 1, title = "Sábado", dueAt = weekdayAt(sat)),
            TaskEntity(id = 2, title = "Domingo", dueAt = weekdayAt(sun)),
            TaskEntity(id = 3, title = "Viernes", dueAt = weekdayAt(todayLocal.plusDays(2))), // 08-14 (esta semana, NO finde)
            TaskEntity(id = 4, title = "Lunes", dueAt = weekdayAt(todayLocal.plusDays(5)))    // 08-17 (esta semana, NO finde)
        )
        val ids = SearchEngine.search("final de semana", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L, 2L), ids)
    }

    @Test fun finalDeSemana_crossSymmetricWithFinDeSemanaCapture() {
        // Simetría cruzada: una tarea capturada con "fin de semana" se recupera
        // buscando "final de semana", y viceversa. Ambas son el mismo concepto.
        val todayLocal = java.time.LocalDate.of(2026, 8, 12) // miércoles
        val t0 = weekdayAt(todayLocal, 12)
        val sat = todayLocal.plusDays(3) // 08-15 sábado
        val tasks = listOf(
            TaskEntity(id = 1, title = "Capturada con fin", dueAt = weekdayAt(sat)),
            TaskEntity(id = 2, title = "Otra", dueAt = weekdayAt(sat.plusDays(7)))
        )
        val ids = SearchEngine.search("final de semana", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun findeReunion_filtersByBothDateAndContent() {
        // "finde reunion": solo la tarea del fin de semana con "reunión" en el
        // título. "finde" no se exige como contenido (es scope de fecha).
        val todayLocal = java.time.LocalDate.of(2026, 8, 12) // miércoles
        val t0 = weekdayAt(todayLocal, 12)
        val sat = todayLocal.plusDays(3) // 08-15 sábado
        val sun = todayLocal.plusDays(4) // 08-16 domingo
        val tasks = listOf(
            TaskEntity(id = 1, title = "Reunión de equipo", dueAt = weekdayAt(sat)),   // finde + reunión
            TaskEntity(id = 2, title = "Brindis", dueAt = weekdayAt(sun)),             // finde sin reunión
            TaskEntity(id = 3, title = "Otra reunión", dueAt = weekdayAt(todayLocal))  // reunión sin finde (hoy)
        )
        val ids = SearchEngine.search("finde reunión", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun finde_pureDateScopeExcludesDatelessEntities() {
        // "finde" puro (sin contenido) solo devuelve tareas con dueAt ese fin
        // de semana: notas/proyectos sin fecha no inundan los resultados.
        val todayLocal = java.time.LocalDate.of(2026, 8, 12) // miércoles
        val t0 = weekdayAt(todayLocal, 12)
        val sat = todayLocal.plusDays(3) // 08-15 sábado
        val task = TaskEntity(id = 1, title = "Cita", dueAt = weekdayAt(sat))
        val results = SearchEngine.search(
            "finde",
            tasks = listOf(task),
            projects = listOf(ProjectEntity(id = 2, name = "Proyecto finde")),
            notes = listOf(NoteEntity(id = 3, title = "Nota finde", body = "")),
            habits = emptyList(),
            now = t0
        )
        val kinds = results.map { it.kind }.toSet()
        assertEquals(setOf(SearchKind.TASK), kinds)
        assertEquals(listOf(1L), results.map { it.id })
    }

    @Test fun finde_excludesCompletedTaskDueWeekend() {
        // Una tarea completada que vencía el sábado NO aparece en "finde"
        // (lectura hacia adelante: ya no es "lo que tengo"). Como en "hoy"/"lunes".
        val todayLocal = java.time.LocalDate.of(2026, 8, 12) // miércoles
        val t0 = weekdayAt(todayLocal, 12)
        val sat = todayLocal.plusDays(3) // 08-15 sábado
        val tasks = listOf(
            TaskEntity(id = 1, title = "Pendiente", dueAt = weekdayAt(sat)),
            TaskEntity(id = 2, title = "Ya hecha", dueAt = weekdayAt(sat), completed = true, completedAt = t0)
        )
        val ids = SearchEngine.search("finde", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun completadasFinde_anchorsOnCompletedAt() {
        // "completadas finde" recupera lo terminado ese fin de semana (por
        // completedAt), no lo que vencía entonces. Simétrico a "completadas hoy"/
        // "completadas lunes".
        val todayLocal = java.time.LocalDate.of(2026, 8, 12) // miércoles
        val t0 = weekdayAt(todayLocal, 12)
        val sat = todayLocal.plusDays(3) // 08-15 sábado
        val tasks = listOf(
            // Terminada el sábado aunque vencía antes.
            TaskEntity(id = 1, title = "Hecha el sábado", completed = true, completedAt = weekdayAt(sat, 10), dueAt = weekdayAt(todayLocal.minusDays(1))),
            // Vencía el sábado pero aún pendiente: NO entra en "completadas finde".
            TaskEntity(id = 2, title = "Pendiente", completed = false, dueAt = weekdayAt(sat))
        )
        val ids = SearchEngine.search("completadas finde", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun finDeSemana_withAccent_normalizesAndResolves() {
        // "fin de semana" con tilde en ninguna palabra (no hay tildes aquí),
        // pero verifica que el apócope "finde" escrito como "Finde" (mayúscula)
        // se normaliza y resuelve. foldForSearch baja a minúsculas.
        val todayLocal = java.time.LocalDate.of(2026, 8, 12) // miércoles
        val t0 = weekdayAt(todayLocal, 12)
        val sat = todayLocal.plusDays(3) // 08-15 sábado
        val tasks = listOf(
            TaskEntity(id = 1, title = "Sábado", dueAt = weekdayAt(sat))
        )
        val ids = SearchEngine.search("Finde", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    // --- startAt como ancla de membresía de fecha (consistencia con
    // PlannerCalendar.datesFor y AssistantEngine.isScheduledInRange) ---
    // Antes taskMatchesDateScope miraba SÓLO `dueAt` para decidir si una tarea
    // pertenecía a un alcance de fecha ("hoy"/"esta semana"/"este mes"/parte del
    // día). Una tarea agendada para HOY por su hueco `startAt` (con `dueAt`
    // posterior) NO aparecía al buscar "hoy", aunque sí la mostrara el calendario
    // del planificador, la carga del día (SummaryEngine) y la agenda del
    // asistente (c.385). La búsqueda universal mentía por omisión en la consulta
    // más cotidiana. Ahora una tarea es "del día X" si su `startAt` o su `dueAt`
    // cae en X — espejo exacto de las demás superficies.

    private fun slotAt(d: java.time.LocalDate, h: Int): Long =
        java.time.ZonedDateTime.of(d.atTime(h, 0), zone).toInstant().toEpochMilli()

    @Test fun hoy_incluyeTareaAgendadaHoyPorStartAtConDueAtPosterior() {
        // Slot agendado para hoy 15:00 (startAt) que vence mañana. Antes "hoy" la
        // omitía (miraba sólo dueAt=mañana); ahora la encuentra por startAt.
        val today = java.time.LocalDate.of(2026, 8, 13) // jueves
        val t0 = slotAt(today, 9)
        val tasks = listOf(
            TaskEntity(id = 1, title = "Reunión slot", startAt = slotAt(today, 15), dueAt = slotAt(today.plusDays(1), 12)),
            TaskEntity(id = 2, title = "Vence hoy", dueAt = slotAt(today, 18)),
            TaskEntity(id = 3, title = "Vence mañana", dueAt = slotAt(today.plusDays(1), 10))
        )
        val ids = SearchEngine.search("hoy", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L, 2L), ids)
    }

    @Test fun estaSemana_incluyeTareaAgendadaPorStartAtConDueAtProximaSemana() {
        // Slot de esta semana (startAt) que vence la próxima semana. Antes "esta
        // semana" la omitía; ahora la encuentra por startAt. (THIS_WEEK para
        // pendientes abarca hoy→domingo: el slot debe caer ese rango hacia adelante.)
        val today = java.time.LocalDate.of(2026, 8, 13) // jueves; esta semana hoy(13)→dom(16)
        val t0 = slotAt(today, 9)
        val tasks = listOf(
            TaskEntity(id = 1, title = "Slot vie", startAt = slotAt(today.plusDays(1), 11), dueAt = slotAt(today.plusDays(5), 12)), // startAt 08-14 (esta semana), dueAt 08-18 (próxima)
            TaskEntity(id = 2, title = "Vence próxima", dueAt = slotAt(today.plusDays(5), 12))
        )
        val ids = SearchEngine.search("esta semana", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun esteMes_incluyeTareaAgendadaPorStartAtConDueAtProximoMes() {
        // Slot de este mes (startAt) que vence el próximo mes. Antes "este mes" la
        // omitía; ahora la encuentra por startAt.
        val today = java.time.LocalDate.of(2026, 8, 13) // agosto
        val t0 = slotAt(today, 9)
        val tasks = listOf(
            TaskEntity(id = 1, title = "Slot agosto", startAt = slotAt(java.time.LocalDate.of(2026, 8, 20), 10), dueAt = slotAt(java.time.LocalDate.of(2026, 9, 5), 12)),
            TaskEntity(id = 2, title = "Vence septiembre", dueAt = slotAt(java.time.LocalDate.of(2026, 9, 5), 12))
        )
        val ids = SearchEngine.search("este mes", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun tarde_incluyeSlotDeHoyPorStartAtAunqueVenzaDespues() {
        // "esta tarde" encuentra un slot agendado para hoy 15:00 (startAt) aunque
        // venza mañana: la franja se resuelve con la marca que cae hoy,
        // prefiriendo startAt (simétrico con AssistantEngine.isInHourBand).
        val today = java.time.LocalDate.of(2026, 8, 13)
        val t0 = slotAt(today, 9)
        val tasks = listOf(
            TaskEntity(id = 1, title = "Reunión tarde", startAt = slotAt(today, 15), dueAt = slotAt(today.plusDays(1), 12)),
            TaskEntity(id = 2, title = "Desayuno", startAt = slotAt(today, 8), dueAt = slotAt(today.plusDays(1), 12))
        )
        val ids = SearchEngine.search("esta tarde", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun hoy_noIncluyeCompletadaAunqueSuStartAtSeaHoy() {
        // Guard de no-regresión: una tarea completada NO aparece en "hoy"
        // (los scopes presentes excluyen completadas), aun cuando su startAt sea
        // hoy. La exclusión por `completed` se mantiene tras el cambio de ancla.
        val today = java.time.LocalDate.of(2026, 8, 13)
        val t0 = slotAt(today, 9)
        val tasks = listOf(
            TaskEntity(id = 1, title = "Hecha hoy", startAt = slotAt(today, 8), dueAt = slotAt(today.plusDays(1), 12), completed = true),
            TaskEntity(id = 2, title = "Pendiente hoy", dueAt = slotAt(today, 18))
        )
        val ids = SearchEngine.search("hoy", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(2L), ids)
    }

    @Test fun ayer_recuperaTareaAgendadaAyerPorStartAt() {
        // Recuperación: una tarea cuyo hueco (startAt) fue ayer aparece al buscar
        // "ayer" aunque su dueAt sea posterior. Simétrico con PlannerCalendar, que
        // ubica la tarea en el día de su startAt.
        val today = java.time.LocalDate.of(2026, 8, 13) // jueves
        val t0 = slotAt(today, 9)
        val tasks = listOf(
            TaskEntity(id = 1, title = "Slot de ayer", startAt = slotAt(today.minusDays(1), 14), dueAt = slotAt(today, 12)),
            TaskEntity(id = 2, title = "Vence hoy", dueAt = slotAt(today, 12))
        )
        val ids = SearchEngine.search("ayer", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun startAtFuturo_noContaminaScopePasado() {
        // Guard de no-regresión: una tarea con startAt futuro y dueAt futuro NO
        // aparece en "ayer" (ninguna marca cae ayer).
        val today = java.time.LocalDate.of(2026, 8, 13)
        val t0 = slotAt(today, 9)
        val tasks = listOf(
            TaskEntity(id = 1, title = "Futura", startAt = slotAt(today.plusDays(2), 10), dueAt = slotAt(today.plusDays(3), 12))
        )
        val ids = SearchEngine.search("ayer", tasks, emptyList(), emptyList(), emptyList(), now = t0).map { it.id }.toSet()
        assertTrue("Una tarea futura no debe aparecer en 'ayer'", ids.isEmpty())
    }

    // --- "en la mañana"/"por la mañana" (preposición + artículo): mañana de HOY ---

    @Test fun enLaManana_returnsOnlyTodayMorningTasks() {
        // Con preposición + artículo, "mañana" es la franja 6-11 de HOY — la misma
        // lectura que el parser de captura (hoy 09:00) —, nunca tomorrow. Antes el
        // token suelto "manana" caía a TOMORROW y, encima, "en"/"por" quedaban
        // como palabras de contenido que ningún título contiene → devolvía VACÍO.
        val today = java.time.LocalDate.of(2026, 8, 13)
        val pod = listOf(
            TaskEntity(id = 1, title = "Correr en el parque", dueAt = atHour(today, 8)),          // hoy, mañana
            TaskEntity(id = 2, title = "Comprar aguacates", dueAt = atHour(today, 15)),           // hoy, tarde
            TaskEntity(id = 3, title = "Reunión de equipo", dueAt = atHour(today.plusDays(1), 9)) // mañana, mañana
        )
        val nowT = atHour(today, 12)
        for (q in listOf("en la mañana", "por la mañana", "¿qué tengo en la mañana?")) {
            val ids = SearchEngine.search(q, pod, emptyList(), emptyList(), emptyList(), now = nowT).map { it.id }.toSet()
            assertEquals("'$q' solo debe traer la mañana de hoy", setOf(1L), ids)
        }
    }

    @Test fun mananaEnLaManana_stillMeansTomorrow() {
        // Control: "mañana en la mañana" = tomorrow (el primer "mañana" gana; el
        // lookbehind de la regex bloquea la lectura preposicional). SearchEngine
        // resuelve al día completo (sin franja, igual que "mañana" sola).
        val today = java.time.LocalDate.of(2026, 8, 13)
        val pod = listOf(
            TaskEntity(id = 1, title = "Correr en el parque", dueAt = atHour(today, 8)),          // hoy, mañana
            TaskEntity(id = 3, title = "Reunión de equipo", dueAt = atHour(today.plusDays(1), 9)), // mañana
            TaskEntity(id = 4, title = "Cena con Ana", dueAt = atHour(today.plusDays(1), 20))      // mañana noche
        )
        val nowT = atHour(today, 12)
        val ids = SearchEngine.search("mañana en la mañana", pod, emptyList(), emptyList(), emptyList(), now = nowT).map { it.id }.toSet()
        assertEquals(setOf(3L, 4L), ids)
    }

    @Test fun porLaTarde_resolvesAfternoonBand() {
        // "por"/"en" ya no envenenan la búsqueda: con stop-words eliminadas,
        // "por la tarde" resuelve la franja de hoy como "tarde" sola (antes
        // devolvía vacío porque exigía "por" en el título).
        val today = java.time.LocalDate.of(2026, 8, 13)
        val pod = listOf(
            TaskEntity(id = 1, title = "Cita médica", dueAt = atHour(today, 15)),
            TaskEntity(id = 2, title = "Desayuno", dueAt = atHour(today, 8))
        )
        val nowT = atHour(today, 12)
        val ids = SearchEngine.search("por la tarde", pod, emptyList(), emptyList(), emptyList(), now = nowT).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun contentSearchWithEnPor_noLongerPoisoned() {
        // Búsqueda de contenido con preposición: "cita en madrid" antes exigía
        // "en" en el título (→ vacío casi siempre). Con "en" como stop-word
        // basta con que el contenido real case.
        val tasks = listOf(
            TaskEntity(id = 1, title = "Cita médica", details = "Madrid"),
            TaskEntity(id = 2, title = "Comprar pan")
        )
        val ids = SearchEngine.search("cita en madrid", tasks, emptyList(), emptyList(), emptyList(), now = now).map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }
}
