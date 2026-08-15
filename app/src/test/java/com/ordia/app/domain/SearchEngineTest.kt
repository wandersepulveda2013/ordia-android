package com.ordia.app.domain

import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.ProjectEntity
import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.RoutineEntity
import com.ordia.app.data.local.RoutineStepEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
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

    @Test fun cancelledTasks_areExcluded() {
        // Una tarea CANCELLED (descartada por el usuario) nunca debe aflorar en la
        // búsqueda, igual que una archivada. Aunque el estado CANCELLED hoy sólo es
        // alcanzable importando un respaldo que lo contenga (ninguna acción de la
        // app lo asigna), todas las superficies activas (What Now, plan del día,
        // widget, asistente, guardián, recordatorios) lo excluyen; la búsqueda debe
        // ser coherente. La propia SearchEngine.taskMatchesDateScope ya excluye
        // CANCELLED en sus scopes de fecha (con comentarios), pero el predicado
        // de entrada de tareas (búsqueda genérica y por atributo) no lo hacía.
        // Sin este guard, una cancelada con completed=false pasa el filtro
        // "pendiente" y aparece al buscar su título o al buscar "pendiente".
        val cancelled = TaskEntity(id = 1, title = "Cancelar suscripción", status = TaskStatus.CANCELLED, completed = false)
        val results = SearchEngine.search("cancelar", listOf(cancelled), emptyList(), emptyList(), emptyList())
        assertTrue(results.isEmpty())
    }

    @Test fun cancelledTasks_excludedFromPendienteSearch() {
        // Variante por atributo: buscar "pendiente" recupera las tareas no
        // completadas, pero una CANCELLED (completed=false) NO es "pendiente"
        // activa — el usuario la descartó. Sin el guard de estado, el filtro
        // (!pendiente || !completed) la dejaba pasar porque completed=false.
        val cancelled = TaskEntity(id = 1, title = "Idea descartada", status = TaskStatus.CANCELLED, completed = false)
        val active = TaskEntity(id = 2, title = "Tarea real", completed = false)
        val ids = SearchEngine.search("pendiente", listOf(cancelled, active), emptyList(), emptyList(), emptyList())
            .filter { it.kind == SearchKind.TASK }.map { it.id }.toSet()
        assertEquals(setOf(2L), ids)
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

    // --- Recuperación de tareas marcadas ("marcadas"/"destacadas") ---

    @Test fun marcadas_recoversFlaggedTasksWithoutTheWordInTitle() {
        // Buscar "marcadas" recupera las tareas que el usuario marcó (flagged)
        // aunque su título no contenga esa palabra, igual que "completadas" o
        // "urgente". Es recuperar lo que el usuario señaló como importante.
        val flagged = TaskEntity(id = 1, title = "Presupuesto Q3", flagged = true)
        val plain = TaskEntity(id = 2, title = "Ordenar escritorio", flagged = false)
        val ids = SearchEngine.search("marcadas", listOf(flagged, plain), emptyList(), emptyList(), emptyList())
            .filter { it.kind == SearchKind.TASK }.map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun destacadas_recoversFlaggedTasks() {
        // Sinónimo "destacadas" también recupera tareas marcadas.
        val flagged = TaskEntity(id = 1, title = "Llamar al banco", flagged = true)
        val ids = SearchEngine.search("destacadas", listOf(flagged), emptyList(), emptyList(), emptyList())
            .filter { it.kind == SearchKind.TASK }.map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun marcadas_withContent_recoversFlaggedMatchingContent() {
        // "marcadas presupuesto" recupera la tarea marcada cuyo título contiene
        // "presupuesto", pero no una marcada ajena ni una no marcada que sí
        // contiene la palabra.
        val match = TaskEntity(id = 1, title = "Revisar presupuesto", flagged = true)
        val otherFlagged = TaskEntity(id = 2, title = "Otra cosa", flagged = true)
        val unflaggedMatch = TaskEntity(id = 3, title = "Presupuesto viejo", flagged = false)
        val ids = SearchEngine.search("marcadas presupuesto", listOf(match, otherFlagged, unflaggedMatch), emptyList(), emptyList(), emptyList())
            .filter { it.kind == SearchKind.TASK }.map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    // --- Recuperación de notas fijadas ("fijadas") ---

    @Test fun fijadas_recoversPinnedNotesWithoutTheWordInContent() {
        // Buscar "fijadas" recupera las notas que el usuario fijó (pinned) aunque su
        // contenido no contenga esa palabra, simétrico a "marcadas" para tareas. La
        // fijación es la señal que el usuario dejó (UI: "Fijar"/"Desfijar") para
        // encontrar algo después; sin este filtro una nota fijada cuyo contenido no
        // dice "fijada" era irrecuperable por búsqueda universal.
        val pinned = NoteEntity(id = 1, title = "Lista de compra", body = "pan leche", pinned = true)
        val plain = NoteEntity(id = 2, title = "Otras ideas", body = "viaje", pinned = false)
        val ids = SearchEngine.search("fijadas", emptyList(), emptyList(), listOf(pinned, plain), emptyList())
            .filter { it.kind == SearchKind.NOTE }.map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun fijadas_withContent_recoversPinnedMatchingContent() {
        // "fijadas presupuesto" recupera la nota fijada cuyo contenido contiene
        // "presupuesto", pero no otra fijada ajena ni una no fijada que sí lo
        // contiene. Igual que "marcadas presupuesto" para tareas.
        val match = NoteEntity(id = 1, title = "Cuentas", body = "presupuesto mensual", pinned = true)
        val otherPinned = NoteEntity(id = 2, title = "Rutina", body = "gimnasio", pinned = true)
        val unpinnedMatch = NoteEntity(id = 3, title = "Viejo", body = "presupuesto anual", pinned = false)
        val ids = SearchEngine.search("fijadas presupuesto", emptyList(), emptyList(), listOf(match, otherPinned, unpinnedMatch), emptyList())
            .filter { it.kind == SearchKind.NOTE }.map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun marcadas_recoversFlaggedTasks_notPinnedNotes() {
        // Guard de separación de vocabulario: "marcadas" es el término de las
        // TAREAS (flagged); una nota fijada NO debe aflorar al buscar "marcadas"
        // (su término es "fijadas"). Evita recuperar ruido cruzado entre
        // superficies con vocabulario distinto.
        val flagged = TaskEntity(id = 1, title = "Llamar al banco", flagged = true)
        val pinnedNote = NoteEntity(id = 2, title = "Lista de compra", body = "pan", pinned = true)
        val ids = SearchEngine.search("marcadas", listOf(flagged), emptyList(), listOf(pinnedNote), emptyList())
            .map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    // --- Recuperación de tareas recurrentes ("repetitivas"/"recurrentes") ---

    @Test fun recurrentes_recoversRecurringTasksWithoutTheWordInContent() {
        // Buscar "recurrentes" recupera las tareas que se repiten (recurrence !=
        // NONE) aunque su título no contenga esa palabra, simétrico a "marcadas"
        // para flagged. La recurrencia es la señal que el usuario dejó (UI:
        // "Cambiar repetición"/"Cada mes") para auditar sus compromisos
        // periódicos; sin este filtro una tarea "Pagar renta" mensual era
        // irrecuperable al buscar "recurrentes".
        val renta = TaskEntity(id = 1, title = "Pagar renta", recurrence = RecurrenceFrequency.MONTHLY)
        val pan = TaskEntity(id = 2, title = "Comprar pan", recurrence = RecurrenceFrequency.NONE)
        val ids = SearchEngine.search("recurrentes", listOf(renta, pan), emptyList(), emptyList(), emptyList())
            .filter { it.kind == SearchKind.TASK }.map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun repetitivas_recoversRecurringTasksSynonym() {
        // "repetitivas" es sinónimo de "recurrentes" (mismo atributo). Verifica
        // que ambas entradas léxicas recuperan las tareas recurrentes.
        val gimnasio = TaskEntity(id = 1, title = "Gimnasio", recurrence = RecurrenceFrequency.WEEKLY)
        val ids = SearchEngine.search("repetitivas", listOf(gimnasio), emptyList(), emptyList(), emptyList())
            .filter { it.kind == SearchKind.TASK }.map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun recurrentes_withContent_recoversRecurringMatchingContent() {
        // "recurrentes luz" recupera la tarea recurrente cuyo contenido contiene
        // "luz", pero no otra recurrente ajena ni una no recurrente que sí lo
        // contiene. Igual que "marcadas presupuesto" para flagged.
        val match = TaskEntity(id = 1, title = "Pago", details = "factura luz", recurrence = RecurrenceFrequency.MONTHLY)
        val otherRecurring = TaskEntity(id = 2, title = "Gimnasio", details = "rutina", recurrence = RecurrenceFrequency.WEEKLY)
        val nonRecurringMatch = TaskEntity(id = 3, title = "Revisar", details = "medidor luz", recurrence = RecurrenceFrequency.NONE)
        val ids = SearchEngine.search("recurrentes luz", listOf(match, otherRecurring, nonRecurringMatch), emptyList(), emptyList(), emptyList())
            .filter { it.kind == SearchKind.TASK }.map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun recurrentes_doesNotRecoverNonRecurringTaskTitledRepetitivo() {
        // Guard de filtro léxico: "repetitivas" recupera SOLO tareas con
        // recurrence; una tarea ÚNICA (recurrence=NONE) cuyo título contenga
        // "repetitivo" no debe aflorar. Es el contrato de los filtros léxicos
        // (paralelo a "marcadas" no recuperando una no-marcada titulada
        // "marca"): la palabra es un filtro de atributo, no de contenido.
        val recurring = TaskEntity(id = 1, title = "Pagar renta", recurrence = RecurrenceFrequency.MONTHLY)
        val titledButUnique = TaskEntity(id = 2, title = "Entrenamiento repetitivo", recurrence = RecurrenceFrequency.NONE)
        val ids = SearchEngine.search("repetitivas", listOf(recurring, titledButUnique), emptyList(), emptyList(), emptyList())
            .filter { it.kind == SearchKind.TASK }.map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    // --- Recuperación por jerarquía (relación subtarea↔padre) ---

    @Test fun parentTitle_surfacesSubtaskViaParentName() {
        // Buscar el título de la tarea padre debe recuperar la subtarea aunque su
        // propio título no contenga esa palabra: la relación subtarea↔padre (que
        // la UI explota anidándolas) hace visible lo agrupado, igual que la
        // membresía de proyecto. La subtarea "Comprar cajas" cuyo padre es
        // "Mudanza" → buscar "mudanza" la encuentra.
        val parent = TaskEntity(id = 1, title = "Mudanza")
        val subtask = TaskEntity(id = 2, title = "Comprar cajas", parentTaskId = 1)
        val ids = SearchEngine.search("mudanza", listOf(parent, subtask), emptyList(), emptyList(), emptyList())
            .filter { it.kind == SearchKind.TASK }.map { it.id }.toSet()
        assertEquals(setOf(1L, 2L), ids)
    }

    @Test fun parentTitle_surfacesSubtaskViaParentDetails() {
        // Los detalles del padre también son contexto válido: una subtarea
        // "Confirmar horario" cuyo padre detalla "coordinar la mudanza" se
        // recupera al buscar "mudanza".
        val parent = TaskEntity(id = 1, title = "Trámite", details = "Coordinar la mudanza del 15")
        val subtask = TaskEntity(id = 2, title = "Confirmar horario", parentTaskId = 1)
        val ids = SearchEngine.search("mudanza", listOf(parent, subtask), emptyList(), emptyList(), emptyList())
            .filter { it.kind == SearchKind.TASK }.map { it.id }.toSet()
        assertEquals(setOf(1L, 2L), ids)
    }

    @Test fun parentTitle_doesNotLeakAcrossUnrelatedTasks() {
        // La jerarquía es específica: una subtarea cuyo padre NO trata del
        // término no aparece. "Comprar cajas" bajo "Mudanza" no surge al buscar
        // "vacaciones" solo porque exista otra tarea "Vacaciones".
        val parent = TaskEntity(id = 1, title = "Mudanza")
        val subtask = TaskEntity(id = 2, title = "Comprar cajas", parentTaskId = 1)
        val other = TaskEntity(id = 3, title = "Vacaciones")
        val ids = SearchEngine.search("vacaciones", listOf(parent, subtask, other), emptyList(), emptyList(), emptyList())
            .filter { it.kind == SearchKind.TASK }.map { it.id }.toSet()
        assertEquals(setOf(3L), ids)
    }

    // --- Rutinas en la búsqueda universal (relación rutina↔pasos) ---

    @Test fun routine_foundByName() {
        // La rutina era invisible en la búsqueda universal: existía en la app y
        // en el estado de UI, pero SearchEngine no la indexaba. Ahora buscar el
        // nombre de una rutina la recupera, simétrico a hábitos/proyectos/notas.
        val routine = RoutineEntity(id = 41, name = "Rutina matinal")
        val results = SearchEngine.search("matinal", emptyList(), emptyList(), emptyList(), emptyList(), routines = listOf(routine))
        assertEquals(1, results.size)
        assertEquals(SearchKind.ROUTINE, results.first().kind)
        assertEquals(41L, results.first().id)
    }

    @Test fun routine_foundByDescription() {
        val routine = RoutineEntity(id = 42, name = "Mañana", description = "Desayuno y ejercicio ligero")
        val results = SearchEngine.search("ejercicio", emptyList(), emptyList(), emptyList(), emptyList(), routines = listOf(routine))
        assertEquals(listOf(42L), results.map { it.id })
    }

    @Test fun routine_foundByStepTitle() {
        // La capacidad clave: una rutina se recupera buscando el título de
        // cualquiera de sus pasos, aunque el nombre/descripción de la rutina no
        // contenga esa palabra. Buscar "dientes" encuentra la rutina "Noche" si
        // tiene el paso "lavarme los dientes". Recupera información que el
        // usuario organizó dentro de la rutina, sin nueva pantalla ni botón.
        val steps = listOf(RoutineStepEntity(id = 1, routineId = 43, title = "lavarme los dientes", position = 0))
        val routine = RoutineEntity(id = 43, name = "Noche")
        val results = SearchEngine.search("dientes", emptyList(), emptyList(), emptyList(), emptyList(), routines = listOf(routine), routineSteps = steps)
        assertEquals(listOf(43L), results.map { it.id })
        assertEquals(SearchKind.ROUTINE, results.first().kind)
    }

    @Test fun routine_archived_isExcluded() {
        // Simétrico a tareas/proyectos/notas/hábitos archivados: una rutina
        // archivada no debe aflorar en la búsqueda activa.
        val routine = RoutineEntity(id = 44, name = "Rutina archivada", archived = true)
        val results = SearchEngine.search("archivada", emptyList(), emptyList(), emptyList(), emptyList(), routines = listOf(routine))
        assertTrue(results.isEmpty())
    }

    @Test fun routine_pureDateScope_doesNotFlood() {
        // Un scope de fecha puro ("hoy") solo aplica a entidades con fecha
        // (tareas). Las rutinas no tienen fecha: buscar "hoy" no debe devolver
        // cada rutina como ruido. Simétrico a proyectos/notas/hábitos.
        val routine = RoutineEntity(id = 45, name = "Rutina de hoy")
        val results = SearchEngine.search("hoy", emptyList(), emptyList(), emptyList(), emptyList(), routines = listOf(routine))
        assertTrue(results.none { it.kind == SearchKind.ROUTINE })
    }

    @Test fun routine_subtitleFallsBackToStepTitles() {
        // Sin descripción, el subtítulo del resultado muestra los pasos unidos,
        // reutilizando datos existentes (sin nueva cadena). Así el usuario ve
        // de qué trata la rutina antes de abrirla.
        val steps = listOf(
            RoutineStepEntity(id = 1, routineId = 46, title = "Cepillarme", position = 0),
            RoutineStepEntity(id = 2, routineId = 46, title = "Hidratarme", position = 1)
        )
        val routine = RoutineEntity(id = 46, name = "Cuidado")
        val result = SearchEngine.search("cuidado", emptyList(), emptyList(), emptyList(), emptyList(), routines = listOf(routine), routineSteps = steps).first()
        assertEquals("Cepillarme · Hidratarme", result.subtitle)
    }
}
