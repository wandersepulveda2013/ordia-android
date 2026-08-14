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

    @Test fun altaPrioridad_surfacesOnlyHighPriorityRegardlessOfTitle() {
        // "alta prioridad" mapea a HIGH exacto: ni URGENT ni NORMAL se incluyen.
        val now = System.currentTimeMillis()
        val high = TaskEntity(id = 1, title = "Revisar contrato", priority = TaskPriority.HIGH)
        val urgent = TaskEntity(id = 2, title = "Vencimiento impuestos", priority = TaskPriority.URGENT)
        val normal = TaskEntity(id = 3, title = "Comprar pan", priority = TaskPriority.NORMAL)
        val ids = SearchEngine.search("alta prioridad", listOf(high, urgent, normal), emptyList(), emptyList(), emptyList(), now = now)
            .map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun prioridadAla_isEquivalentToAltaPrioridad() {
        // El orden de las palabras no debe importar: "prioridad alta" == "alta prioridad".
        val now = System.currentTimeMillis()
        val high = TaskEntity(id = 1, title = "Llamar al banco", priority = TaskPriority.HIGH)
        val normal = TaskEntity(id = 2, title = "Regar plantas", priority = TaskPriority.NORMAL)
        val ids = SearchEngine.search("prioridad alta", listOf(high, normal), emptyList(), emptyList(), emptyList(), now = now)
            .map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun bajaPrioridad_surfacesOnlyLowPriority() {
        val now = System.currentTimeMillis()
        val low = TaskEntity(id = 1, title = "Archivar correos", priority = TaskPriority.LOW)
        val normal = TaskEntity(id = 2, title = "Hacer la compra", priority = TaskPriority.NORMAL)
        val ids = SearchEngine.search("baja prioridad", listOf(low, normal), emptyList(), emptyList(), emptyList(), now = now)
            .map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun altaSola_noPriorityFilter_keepsContentSearch() {
        // "alta" sin "prioridad" NO es filtro de prioridad: debe buscar por
        // contenido (alta médica, alta en el sistema). Aquí una tarea que
        // contiene "alta" se recupera por contenido aunque sea NORMAL.
        val now = System.currentTimeMillis()
        val altaMedica = TaskEntity(id = 1, title = "Trámite de alta médica", priority = TaskPriority.NORMAL)
        val highSinAlta = TaskEntity(id = 2, title = "Revisar contrato", priority = TaskPriority.HIGH)
        val ids = SearchEngine.search("alta", listOf(altaMedica, highSinAlta), emptyList(), emptyList(), emptyList(), now = now)
            .map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun altaPrioridadWithContent_combinesPriorityAndText() {
        // "alta prioridad reunion": HIGH que trate de "reunión". La palabra
        // "reunión" se exige en el contenido; "alta"/"prioridad" no.
        val now = System.currentTimeMillis()
        val highReunion = TaskEntity(id = 1, title = "Reunión de equipo", priority = TaskPriority.HIGH)
        val highOtra = TaskEntity(id = 2, title = "Revisar contrato", priority = TaskPriority.HIGH)
        val urgentReunion = TaskEntity(id = 3, title = "Reunión de crisis", priority = TaskPriority.URGENT)
        val ids = SearchEngine.search("alta prioridad reunion", listOf(highReunion, highOtra, urgentReunion), emptyList(), emptyList(), emptyList(), now = now)
            .map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun completadas_surfacesOnlyCompletedTasksRegardlessOfTitle() {
        // Buscar "completadas" debe recuperar las tareas terminadas aunque su
        // título no contenga esa palabra, simétrico a "urgente"/"importante"/
        // "pendiente". Antes de este fix "completadas" solo hallaba tareas con
        // esa palabra literal en el título, así que una tarea "Pagar luz" ya
        // terminada jamás aparecía: el usuario no podía recuperar lo que hizo.
        val now = System.currentTimeMillis()
        val done = TaskEntity(id = 1, title = "Pagar luz", completed = true)
        val pending = TaskEntity(id = 2, title = "Comprar pan", completed = false)
        val ids = SearchEngine.search("completadas", listOf(done, pending), emptyList(), emptyList(), emptyList(), now = now)
            .map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun hechas_surfacesOnlyCompletedTasks() {
        // "hechas" es sinónimo coloquial de "completadas": misma intención.
        val now = System.currentTimeMillis()
        val done = TaskEntity(id = 1, title = "Enviar informe", completed = true)
        val pending = TaskEntity(id = 2, title = "Llamar a mamá", completed = false)
        val ids = SearchEngine.search("hechas", listOf(done, pending), emptyList(), emptyList(), emptyList(), now = now)
            .map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun completadasWithContent_combinesStatusAndText() {
        // "completadas reunion" combina el filtro de estado con texto: de las
        // terminadas, solo las que tratan de "reunión" (igual que "urgente
        // reunion").
        val now = System.currentTimeMillis()
        val doneReunion = TaskEntity(id = 1, title = "Reunión de equipo", completed = true)
        val doneOtra = TaskEntity(id = 2, title = "Pago servidor", completed = true)
        val ids = SearchEngine.search("completadas reunion", listOf(doneReunion, doneOtra), emptyList(), emptyList(), emptyList(), now = now)
            .map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun completar_doesNotTriggerCompletedFilter() {
        // "completar" es el infinitivo ("to complete"), no "completado": no debe
        // activar el filtro de estado. Una tarea titulada "Completar formulario"
        // que aún NO está terminada no debe aparecer en una búsqueda de
        // "completar" como si ya estuviera hecha.
        val now = System.currentTimeMillis()
        val toComplete = TaskEntity(id = 1, title = "Completar formulario", completed = false)
        val ids = SearchEngine.search("completar", listOf(toComplete), emptyList(), emptyList(), emptyList(), now = now)
            .map { it.id }.toSet()
        // "completar" coincide por contenido con su propio título → aparece, pero
        // NO por el filtro de estado (la tarea NO está completed).
        assertEquals(setOf(1L), ids)
    }

    @Test fun substringAltaInsideAnotherWord_isNotPriorityFilter() {
        // "exaltar" contiene la subcadena "alta", pero NO es intención de
        // prioridad: la detección es por PALABRA, no por subcadena. Una query
        // como "exaltar prioridad" (título de una nota/poema) no debe filtrar
        // tareas HIGH; cae a búsqueda por contenido. Aquí no hay tarea con
        // "exaltar" ni "prioridad" en el título → vacío (no se inventa filtro).
        val now = System.currentTimeMillis()
        val high = TaskEntity(id = 1, title = "Revisar contrato", priority = TaskPriority.HIGH)
        val normal = TaskEntity(id = 2, title = "Comprar pan", priority = TaskPriority.NORMAL)
        val ids = SearchEngine.search("exaltar prioridad", listOf(high, normal), emptyList(), emptyList(), emptyList(), now = now)
            .map { it.id }.toSet()
        assertEquals(emptySet<Long>(), ids)
    }

    // --- Recuperación por membresía de proyecto (relación tarea↔proyecto) ---

    @Test fun projectMembership_surfacesTaskViaProjectName() {
        // Buscar el nombre de un proyecto debe recuperar las tareas que pertenezcan
        // a ese proyecto aunque su título no contenga la palabra: la relación
        // tarea↔proyecto hace visible lo agrupado. "comprar cajas" vive en
        // "Mudanza" → buscar "mudanza" la encuentra (junto al propio proyecto).
        val task = TaskEntity(id = 1, title = "Comprar cajas", projectId = 10)
        val other = TaskEntity(id = 2, title = "Pasear al perro", projectId = null)
        val project = ProjectEntity(id = 10, name = "Mudanza")
        val taskIds = SearchEngine.search("mudanza", listOf(task, other), listOf(project), emptyList(), emptyList())
            .filter { it.kind == SearchKind.TASK }.map { it.id }.toSet()
        assertEquals(setOf(1L), taskIds)
    }

    @Test fun projectMembership_surfacesNoteViaProjectName() {
        // Simétrico a tareas: una nota en un proyecto se recupera al buscar el
        // nombre del proyecto. "Lista de cosas" en "Mudanza" → "mudanza".
        val note = NoteEntity(id = 5, title = "Lista de cosas", body = "", projectId = 10)
        val project = ProjectEntity(id = 10, name = "Mudanza")
        val noteIds = SearchEngine.search("mudanza", emptyList(), listOf(project), listOf(note), emptyList())
            .filter { it.kind == SearchKind.NOTE }.map { it.id }.toSet()
        assertEquals(setOf(5L), noteIds)
    }

    @Test fun projectMembership_doesNotLeakAcrossProjects() {
        // La membresía es específica: una tarea de "Mudanza" no aparece al buscar
        // "Vacaciones" solo porque exista otro proyecto con ese nombre. El proyecto
        // "Vacaciones" sí aparece (su nombre coincide), pero la tarea ajena no.
        val mudanzaTask = TaskEntity(id = 1, title = "Comprar cajas", projectId = 10)
        val vacacionesProject = ProjectEntity(id = 20, name = "Vacaciones")
        val taskIds = SearchEngine.search("vacaciones", listOf(mudanzaTask), listOf(vacacionesProject), emptyList(), emptyList())
            .filter { it.kind == SearchKind.TASK }.map { it.id }.toSet()
        assertEquals(emptySet<Long>(), taskIds)
    }

    @Test fun projectMembership_ignoresArchivedProjectName() {
        // Un proyecto archivado deja de ser señal de recuperación: su nombre no
        // debe hacer aflorar tareas (el usuario archivó el proyecto).
        val task = TaskEntity(id = 1, title = "Comprar cajas", projectId = 10)
        val archivedProject = ProjectEntity(id = 10, name = "Mudanza", archived = true)
        val ids = SearchEngine.search("mudanza", listOf(task), listOf(archivedProject), emptyList(), emptyList())
            .filter { it.kind == SearchKind.TASK }
            .map { it.id }.toSet()
        assertEquals(emptySet<Long>(), ids)
    }
}
