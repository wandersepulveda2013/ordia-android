package com.ordia.app.domain

import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.ProjectEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {
    @Test fun search_coversAllCoreContent() {
        val results = SearchEngine.search(
            "toolisto",
            tasks = listOf(TaskEntity(id = 1, title = "Revisar Toolisto")),
            projects = listOf(ProjectEntity(id = 2, name = "Toolisto")),
            notes = listOf(NoteEntity(id = 3, title = "Ideas", body = "Cambios para Toolisto")),
            habits = listOf(HabitEntity(id = 4, title = "Revisión diaria", details = "Abrir Toolisto"))
        )
        assertEquals(setOf(SearchKind.TASK, SearchKind.PROJECT, SearchKind.NOTE, SearchKind.HABIT), results.map { it.kind }.toSet())
        assertEquals(SearchKind.PROJECT, results.first().kind)
    }

    @Test fun archivedContent_isExcluded() {
        val results = SearchEngine.search("oculto", listOf(TaskEntity(title = "Oculto", archived = true)), emptyList(), emptyList(), emptyList())
        assertTrue(results.isEmpty())
    }

    @Test fun matchesWithoutAccents() {
        val results = SearchEngine.search(
            "habito",
            emptyList(),
            emptyList(),
            emptyList(),
            listOf(HabitEntity(id = 7, title = "Hábito de lectura"))
        )
        assertEquals(1, results.size)
        assertEquals(7L, results.first().id)
    }

    @Test fun noteTypeFilterDoesNotRequireTheWordNotaInContent() {
        // "nota proyecto" debe encontrar una nota sobre "proyecto" aunque la
        // nota no contenga la palabra "nota" (igual que "tarea X" filtra tareas
        // sin exigir la palabra "tarea" en su contenido).
        val results = SearchEngine.search(
            "nota proyecto",
            emptyList(),
            emptyList(),
            listOf(NoteEntity(id = 11, title = "Proyecto Q3", body = "")),
            emptyList()
        )
        assertEquals(1, results.size)
        assertEquals(SearchKind.NOTE, results.first().kind)
        assertEquals(11L, results.first().id)
    }

    @Test fun urgencyRanksOverdueAheadOfAlphabeticalMatches() {
        // Dos tareas con el mismo título; la atrasada y urgente debe aparecer antes.
        val now = System.currentTimeMillis()
        val overdue = TaskEntity(id = 21, title = "Reunión equipo", priority = TaskPriority.URGENT, dueAt = now - 3_600_000L)
        val fresh = TaskEntity(id = 22, title = "Reunión equipo", priority = TaskPriority.NORMAL)
        val results = SearchEngine.search("reunion", listOf(overdue, fresh), emptyList(), emptyList(), emptyList(), now = now)
        assertEquals(2, results.size)
        assertEquals(21L, results.first().id)
    }

    @Test fun textPrefixStillBeatsUrgencyForDifferentTitles() {
        // "Toolisto" como prefijo del proyecto sigue ganando sobre una tarea que
        // solo contiene la palabra (no la prefija), incluso si la tarea es urgente.
        val now = System.currentTimeMillis()
        val urgent = TaskEntity(id = 31, title = "Revisar Toolisto", priority = TaskPriority.URGENT, dueAt = now - 60_000L)
        val results = SearchEngine.search(
            "toolisto",
            listOf(urgent),
            listOf(ProjectEntity(id = 32, name = "Toolisto")),
            emptyList(),
            emptyList(),
            now = now
        )
        assertEquals(SearchKind.PROJECT, results.first().kind)
    }

    // --- Filtro por prioridad: "urgente" simétrico a "importante" ---

    @Test fun urgente_surfacesOnlyUrgentPriorityRegardlessOfTitle() {
        // Buscar "urgente" debe recuperar la tarea marcada como URGENT aunque su
        // título no contenga la palabra "urgente", igual que "importante" recupera
        // las de prioridad alta. Antes de este fix "urgente" solo hallaba tareas
        // que tuvieran "urgente" literal en el título (asimetría con "importante").
        val now = System.currentTimeMillis()
        val urgentTask = TaskEntity(id = 1, title = "Pagar factura de luz", priority = TaskPriority.URGENT)
        val normalTask = TaskEntity(id = 2, title = "Comprar pan", priority = TaskPriority.NORMAL)
        val ids = SearchEngine.search("urgente", listOf(urgentTask, normalTask), emptyList(), emptyList(), emptyList(), now = now)
            .map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun urgente_excludesHighPriorityTasks() {
        // "urgente" mapea al nivel URGENT (el más alto), no al rango amplio
        // HIGH+URGENT que cubre "importante": una tarea HIGH no es urgente.
        val now = System.currentTimeMillis()
        val high = TaskEntity(id = 1, title = "Revisar contrato", priority = TaskPriority.HIGH)
        val urgent = TaskEntity(id = 2, title = "Vencimiento impuestos", priority = TaskPriority.URGENT)
        val ids = SearchEngine.search("urgente", listOf(high, urgent), emptyList(), emptyList(), emptyList(), now = now)
            .map { it.id }.toSet()
        assertEquals(setOf(2L), ids)
    }

    @Test fun urgenteWithContent_findsUrgentTasksMatchingText() {
        // "urgente reunion" combina el filtro de prioridad con texto: de las
        // urgentes, solo las que tratan de "reunión".
        val now = System.currentTimeMillis()
        val urgentReunion = TaskEntity(id = 1, title = "Reunión de crisis", priority = TaskPriority.URGENT)
        val urgentOtra = TaskEntity(id = 2, title = "Pago servidor", priority = TaskPriority.URGENT)
        val ids = SearchEngine.search("urgente reunion", listOf(urgentReunion, urgentOtra), emptyList(), emptyList(), emptyList(), now = now)
            .map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }
}
