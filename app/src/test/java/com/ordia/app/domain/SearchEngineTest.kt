package com.ordia.app.domain

import com.ordia.app.data.local.AutomationAction
import com.ordia.app.data.local.AutomationCondition
import com.ordia.app.data.local.AutomationRuleEntity
import com.ordia.app.data.local.AutomationTrigger
import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.CommitmentKind
import com.ordia.app.data.local.CommitmentOwner
import com.ordia.app.data.local.CommitmentReviewStatus
import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.ProjectEntity
import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.RoutineEntity
import com.ordia.app.data.local.RoutineStepEntity
import com.ordia.app.data.local.TagEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
import com.ordia.app.data.local.TaskTagCrossRef
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // c.795 — listados de familias de entidad (hábitos/rutinas/proyectos), al igual
    // que «notas»/«tareas»: el buscador sólo los encontraba por coincidencia de
    // contenido, así que «hábitos»/«mis hábitos» («mis» es stop word) devolvían
    // vacío aunque el usuario tuviera decenas — mentira por omisión de la
    // búsqueda universal. Los términos semánticos los listan todos (igual que el
    // listing de conversaciones), y el intent tipado los libera del filtro
    // «typed» igual que «tarea(s)»/«nota(s)»/«compromiso(s)» arriba.
    @Test fun habitosBaresListanTodosLosHabitos() {
        val results = SearchEngine.search(
            "habitos",
            emptyList(), emptyList(), emptyList(),
            listOf(
                HabitEntity(id = 1, title = "Leer 20 minutos"),
                HabitEntity(id = 2, title = "Correr 5k"),
                HabitEntity(id = 3, title = "Meditar", archived = true)
            )
        )
        assertEquals(setOf(1L, 2L), results.map { it.id }.toSet())
        assertEquals(setOf(SearchKind.HABIT), results.map { it.kind }.toSet())
    }

    @Test fun misHabitosListaLosHabitos() {
        val results = SearchEngine.search(
            "mis hábitos",
            emptyList(), emptyList(), emptyList(),
            listOf(HabitEntity(id = 1, title = "Leer 20 minutos"))
        )
        assertEquals(1, results.size)
        assertEquals(1L, results.first().id)
    }

    @Test fun habitosCalificadosFiltranPorContenido() {
        // «hábitos de lectura» conserva sólo el que coincide con «lectura»; el
        // familar token «hábito(s)» no exige presencia en el título detalle.
        val results = SearchEngine.search(
            "habitos de lectura",
            emptyList(), emptyList(), emptyList(),
            listOf(
                HabitEntity(id = 1, title = "Lectura diaria"),
                HabitEntity(id = 2, title = "Correr 5k")
            )
        )
        assertEquals(listOf(1L), results.map { it.id })
    }

    @Test fun rutinasBaresListanTodasLasRutinas() {
        val results = SearchEngine.search(
            "rutinas",
            emptyList(), emptyList(), emptyList(), emptyList(),
            routines = listOf(
                RoutineEntity(id = 1, name = "Mañana"),
                RoutineEntity(id = 2, name = "Noche"),
                RoutineEntity(id = 3, name = "Deporte", archived = true)
            )
        )
        assertEquals(setOf(1L, 2L), results.map { it.id }.toSet())
        assertEquals(setOf(SearchKind.ROUTINE), results.map { it.kind }.toSet())
    }

    @Test fun misRutinasListaLasRutinas() {
        val results = SearchEngine.search(
            "mis rutinas",
            emptyList(), emptyList(), emptyList(), emptyList(),
            routines = listOf(RoutineEntity(id = 1, name = "Mañana"))
        )
        assertEquals(listOf(1L), results.map { it.id })
    }

    @Test fun proyectosBaresListanLosProyectos() {
        val results = SearchEngine.search(
            "proyectos",
            emptyList(),
            listOf(
                ProjectEntity(id = 1, name = "Mudanza"),
                ProjectEntity(id = 2, name = "Toolisto", archived = true)
            ),
            emptyList(), emptyList()
        )
        assertEquals(listOf(1L), results.map { it.id })
        assertEquals(setOf(SearchKind.PROJECT), results.map { it.kind }.toSet())
    }

    // c.963 — la familia de automatizaciones era la ÚNICA listable del buscador
    // sin términos semánticos: el filtro usaba sólo matches() (contención
    // literal), así que «automatizaciones»/«reglas» («mis» es stop word)
    // devolvían vacío aunque el usuario tuviera varias — la misma mentira por
    // omisión que c.795 corrigió para hábitos/rutinas/proyectos. wantsAutomations
    // ya existía (typed quedaba liberado); faltaba el semanticMatches hermano.
    private fun automation(id: Long, name: String, instruction: String = "", explanation: String = "") =
        AutomationRuleEntity(
            id = id, name = name, instruction = instruction,
            trigger = AutomationTrigger.DAILY_MORNING, condition = AutomationCondition.ALWAYS,
            action = AutomationAction.PLAN_DAY, explanation = explanation, definitionHash = "h$id"
        )

    @Test fun automatizacionesBaresListanTodasLasAutomatizaciones() {
        val results = SearchEngine.search(
            "automatizaciones",
            emptyList(), emptyList(), emptyList(), emptyList(),
            automations = listOf(
                automation(1, "Aviso de gym"),
                automation(2, "Resumen nocturno")
            )
        )
        assertEquals(setOf(1L, 2L), results.map { it.id }.toSet())
        assertEquals(setOf(SearchKind.AUTOMATION), results.map { it.kind }.toSet())
    }

    @Test fun misAutomatizacionesListaLasAutomatizaciones() {
        val results = SearchEngine.search(
            "mis automatizaciones",
            emptyList(), emptyList(), emptyList(), emptyList(),
            automations = listOf(automation(1, "Aviso de gym"))
        )
        assertEquals(listOf(1L), results.map { it.id })
    }

    @Test fun reglasBaresListanLasAutomatizaciones() {
        // «reglas» es la forma cotidiana hermana (wantsAutomations la detecta por
        // subcadena «regla»); sin término semántico exigía «reglas» en el nombre.
        val results = SearchEngine.search(
            "reglas",
            emptyList(), emptyList(), emptyList(), emptyList(),
            automations = listOf(automation(1, "Aviso de gym"))
        )
        assertEquals(listOf(1L), results.map { it.id })
    }

    @Test fun automatizacionesCalificadasFiltranPorContenido() {
        // «automatizaciones gym» conserva sólo la que coincide con «gym»; el
        // token de familia no exige presencia en nombre/instrucción/explicación.
        val results = SearchEngine.search(
            "automatizaciones gym",
            emptyList(), emptyList(), emptyList(), emptyList(),
            automations = listOf(
                automation(1, "Aviso de gym"),
                automation(2, "Resumen nocturno")
            )
        )
        assertEquals(listOf(1L), results.map { it.id })
    }

    @Test fun entityListing_guard_substringNoDisparaElIntent() {
        // El intent se dispara por PALABRA (como wantsTasks/wantsCommitments):
        // «rutinario»/«proyectare» no son «rutina(s)»/«proyecto(s)», así que la
        // tarea titulada así sigue apareciendo (no queda excluida por typed).
        val task = TaskEntity(id = 1, title = "Revisar el rutinario semanal")
        val results = SearchEngine.search("rutinario", listOf(task), emptyList(), emptyList(), emptyList())
        assertEquals(listOf(1L), results.map { it.id })
    }

    @Test fun entityListing_guard_notasSiguenSinVerHabitos() {
        // «hábitos» no dispara `wantsNotes`, y las notas siguen su propio filtro.
        val notes = SearchEngine.search(
            "habitos",
            emptyList(), emptyList(),
            listOf(NoteEntity(id = 1, title = "Ideas")),
            emptyList()
        )
        assertTrue(notes.isEmpty())
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

    // --- "completé"/"hice" (1ª persona del pretérito) activan la recuperación
    // COMPLETED ---
    // El participio "completadas"/"hecho" ya activaba el scope COMPLETED, pero las
    // formas MÁS naturales de preguntar por lo hecho — "completé" (→ "complete" vía
    // foldForSearch) y "hice" — no estaban en COMPLETED_TOKENS. Buscar "¿qué
    // completé?"/"¿qué hice?" devolvía VACÍO pese a haber tareas terminadas: la
    // recuperación del trabajo completado quedaba ciega con la phrasing más común,
    // espejo exacto del hueco de "olvidé". Aquí se prueba que ahora recuperan una
    // tarea terminada que una pendiente NO es.
    @Test fun completé_y_hice_activateCompletedRecovery() {
        val now = System.currentTimeMillis()
        val done = TaskEntity(id = 10, title = "Reunión equipo", priority = TaskPriority.NORMAL, completed = true, completedAt = now)
        val pending = TaskEntity(id = 11, title = "Reunión equipo", priority = TaskPriority.NORMAL)
        // "completé" (con tilde, entrada natural) y su forma plegada "complete".
        val withAccent = SearchEngine.search("completé", listOf(done, pending), emptyList(), emptyList(), emptyList(), now = now)
        assertEquals(setOf(10L), withAccent.map { it.id }.toSet())
        val folded = SearchEngine.search("complete", listOf(done, pending), emptyList(), emptyList(), emptyList(), now = now)
        assertEquals(setOf(10L), folded.map { it.id }.toSet())
        // "hice" (1ª persona de hacer) recupera lo hecho.
        val hice = SearchEngine.search("hice", listOf(done, pending), emptyList(), emptyList(), emptyList(), now = now)
        assertEquals(setOf(10L), hice.map { it.id }.toSet())
    }

    // Guard simétrico al de "olvidar"/"completar": el infinitivo "hacer" NO activa
    // el scope COMPLETED, para que una tarea titulada "Hacer la compra" (pendiente)
    // no se devuelva como si ya estuviera hecha. Añadir "hice" (1ª persona, no
    // infinitivo) preserva este guard.
    @Test fun hacer_doesNotActivateCompletedRecovery_guard() {
        val now = System.currentTimeMillis()
        val done = TaskEntity(id = 10, title = "Reunión equipo", priority = TaskPriority.NORMAL, completed = true, completedAt = now)
        val results = SearchEngine.search("hacer", listOf(done), emptyList(), emptyList(), emptyList(), now = now)
        assertTrue(results.isEmpty())
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

    // --- Pretérito 1.ª persona del marcado ("las que marqué" / "lo que
    // destaqué"): paridad búsqueda↔asistente (mismo ciclo, misma lista) ---
    // La forma más natural de pedir lo que uno MISMO marcó es el pretérito
    // ("¿qué marqué?"), no el participio ("marcadas"). Tras foldForSearch,
    // "marqué"→"marque" y "destaqué"→"destaque"; son tokens de intención por
    // palabra exacta, igual que los participios, y se excluyen del contenido
    // exigido (el título no tiene por qué decir "marqué").

    @Test fun lasQueMarque_pastTense_recoversFlaggedTasks() {
        val flagged = TaskEntity(id = 1, title = "Presupuesto Q3", flagged = true)
        val plain = TaskEntity(id = 2, title = "Ordenar escritorio", flagged = false)
        // "lo que marqué" además exige que "lo" (artículo neutro) sea stop-word
        // como la/las/el/los: sin ello la consulta exigía un "lo" libre en el
        // título y devolvía vacío.
        for (q in listOf("las que marqué", "lo que marqué")) {
            val ids = SearchEngine.search(q, listOf(flagged, plain), emptyList(), emptyList(), emptyList())
                .filter { it.kind == SearchKind.TASK }.map { it.id }.toSet()
            assertEquals("«$q» recupera la marcada", setOf(1L), ids)
        }
    }

    @Test fun loQueDestaque_pastTense_recoversFlaggedTasks() {
        val flagged = TaskEntity(id = 1, title = "Llamar al banco", flagged = true)
        val plain = TaskEntity(id = 2, title = "Comprar café", flagged = false)
        val ids = SearchEngine.search("lo que destaqué", listOf(flagged, plain), emptyList(), emptyList(), emptyList())
            .filter { it.kind == SearchKind.TASK }.map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun marquePastTense_withContent_filtersWithinFlagged() {
        // "marqué presupuesto": la marca es filtro, el contenido sigue exigiéndose
        // dentro de las marcadas (simétrico a "marcadas presupuesto").
        val match = TaskEntity(id = 1, title = "Revisar presupuesto", flagged = true)
        val otherFlagged = TaskEntity(id = 2, title = "Otra cosa", flagged = true)
        val unflaggedMatch = TaskEntity(id = 3, title = "Presupuesto viejo", flagged = false)
        val ids = SearchEngine.search("marqué presupuesto", listOf(match, otherFlagged, unflaggedMatch), emptyList(), emptyList(), emptyList())
            .filter { it.kind == SearchKind.TASK }.map { it.id }.toSet()
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

    @Test fun recurrentes_interrogativeFiller_recoversRecurringTasks() {
        // "¿cuáles son recurrentes?": muletilla interrogativa ("cuales"/"son")
        // es ruido, igual que "tengo"/"hay". Sin excluirla, la consulta natural
        // quedaba vacía aunque el filtro de atributo sí aplicara (GAP sondeado
        // en c.782). Paridad buscador↔asistente para preguntas naturales.
        val renta = TaskEntity(id = 1, title = "Pagar renta", recurrence = RecurrenceFrequency.MONTHLY)
        val pan = TaskEntity(id = 2, title = "Comprar pan", recurrence = RecurrenceFrequency.NONE)
        val ids = SearchEngine.search("cuales son recurrentes", listOf(renta, pan), emptyList(), emptyList(), emptyList())
            .filter { it.kind == SearchKind.TASK }.map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    // --- Recuperación de recordatorios programados («recordatorios», c.808) ---
    // El recordatorio (TaskEntity.reminderAt) es la promesa de que la app
    // avisará; la pregunta «¿qué me vas a recordar?» debe poder responderse
    // desde la búsqueda universal, no sólo desde el asistente. Sin este
    // filtro, una tarea «Cita médica» con aviso programado era irrecuperable
    // salvo escribiendo su título — el dato existe y sólo faltaba la ruta.
    @Test fun recordatorios_recoversTasksWithReminderAtWithoutTheWordInContent() {
        val cita = TaskEntity(id = 1, title = "Cita médica", dueAt = 2_000_000L, reminderAt = 1_700_000L)
        val pan = TaskEntity(id = 2, title = "Comprar pan")
        val ids = SearchEngine.search("recordatorios", listOf(cita, pan), emptyList(), emptyList(), emptyList())
            .filter { it.kind == SearchKind.TASK }.map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun recordatorio_singularAlsoRecovers() {
        val cita = TaskEntity(id = 1, title = "Cita médica", reminderAt = 1_700_000L)
        val ids = SearchEngine.search("recordatorio", listOf(cita), emptyList(), emptyList(), emptyList())
            .filter { it.kind == SearchKind.TASK }.map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun recordatorios_interrogativeFiller_recoversReminders() {
        // «cuáles son mis recordatorios»: la muletilla interrogativa es stop
        // word (c.783); el filtro de atributo debe seguir aplicando.
        val cita = TaskEntity(id = 1, title = "Cita médica", reminderAt = 1_700_000L)
        val pan = TaskEntity(id = 2, title = "Comprar pan")
        val ids = SearchEngine.search("cuales son mis recordatorios", listOf(cita, pan), emptyList(), emptyList(), emptyList())
            .filter { it.kind == SearchKind.TASK }.map { it.id }.toSet()
        assertEquals(setOf(1L), ids)
    }

    @Test fun recordatorios_excludesCompletedTasksWhoseReminderWillNotFire() {
        // Una tarea completada no volverá a avisar (al completar se cancela la
        // notificación): listarla como «recordatorio» sería mentira.
        val hecha = TaskEntity(id = 1, title = "Pagar agua", reminderAt = 1_700_000L, completed = true)
        val pendiente = TaskEntity(id = 2, title = "Pagar luz", reminderAt = 1_800_000L)
        val ids = SearchEngine.search("recordatorios", listOf(hecha, pendiente), emptyList(), emptyList(), emptyList())
            .filter { it.kind == SearchKind.TASK }.map { it.id }.toSet()
        assertEquals(setOf(2L), ids)
    }

    @Test fun recordatorios_withContent_recoversRemindersMatchingContent() {
        // «recordatorios luz»: la palabra de contenido recorta el listado,
        // igual que «recurrentes luz» (c.600s) y «marcadas presupuesto».
        val match = TaskEntity(id = 1, title = "Pago", details = "factura luz", reminderAt = 1_700_000L)
        val otherReminder = TaskEntity(id = 2, title = "Gimnasio", reminderAt = 1_800_000L)
        val noReminderMatch = TaskEntity(id = 3, title = "Revisar", details = "medidor luz")
        val ids = SearchEngine.search("recordatorios luz", listOf(match, otherReminder, noReminderMatch), emptyList(), emptyList(), emptyList())
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

    @Test fun tag_surfacesTaskViaTagName() {
        // La capacidad clave: una tarea se recupera al buscar el nombre de
        // cualquiera de sus etiquetas, aunque su título/detalle no lo contengan.
        // Simétrico a la membresía de proyecto (tarea↔proyecto) y a la jerarquía
        // subtarea↔padre. "Llamar al cliente" con etiqueta "trabajo" → "trabajo".
        val task = TaskEntity(id = 1, title = "Llamar al cliente", completed = false)
        val other = TaskEntity(id = 2, title = "Pasear al perro", completed = false)
        val tag = TagEntity(id = 7, name = "trabajo")
        val links = listOf(TaskTagCrossRef(taskId = 1, tagId = 7))
        val taskIds = SearchEngine.search("trabajo", listOf(task, other), emptyList(), emptyList(), emptyList(), tags = listOf(tag), taskTags = links)
            .filter { it.kind == SearchKind.TASK }.map { it.id }.toSet()
        assertEquals(setOf(1L), taskIds)
    }

    @Test fun tag_doesNotLeakAcrossTasks() {
        // La etiqueta es específica: una tarea SIN la etiqueta "trabajo" no
        // aparece al buscar "trabajo" solo porque exista la etiqueta en otra
        // tarea. (Otra tarea sí puede salir; aquí sólo hay una sin la etiqueta.)
        val untagged = TaskEntity(id = 3, title = "Regar plantas", completed = false)
        val tag = TagEntity(id = 7, name = "trabajo")
        val ids = SearchEngine.search("trabajo", listOf(untagged), emptyList(), emptyList(), emptyList(), tags = listOf(tag), taskTags = emptyList())
            .filter { it.kind == SearchKind.TASK }.map { it.id }.toSet()
        assertEquals(emptySet<Long>(), ids)
    }

    @Test fun tag_matchesMultipleTasksSharingTag() {
        // Varias tareas comparten una etiqueta → buscarla recupera todas.
        val t1 = TaskEntity(id = 1, title = "Informe", completed = false)
        val t2 = TaskEntity(id = 2, title = "Reunión", completed = false)
        val tag = TagEntity(id = 7, name = "oficina")
        val links = listOf(TaskTagCrossRef(1, 7), TaskTagCrossRef(2, 7))
        val ids = SearchEngine.search("oficina", listOf(t1, t2), emptyList(), emptyList(), emptyList(), tags = listOf(tag), taskTags = links)
            .filter { it.kind == SearchKind.TASK }.map { it.id }.toSet()
        assertEquals(setOf(1L, 2L), ids)
    }

    @Test fun tag_unlinkedTagIdIsIgnored() {
        // Si un link apunta a un tagId que no está en `tags` (p. ej. etiqueta
        // borrada pero link no limpiado — FK CASCADE lo previene, pero el motor
        // debe ser robusto), no se rompe ni recupera por un nombre inexistente.
        val task = TaskEntity(id = 1, title = "Llamar", completed = false)
        val links = listOf(TaskTagCrossRef(taskId = 1, tagId = 99))
        val ids = SearchEngine.search("fantasma", listOf(task), emptyList(), emptyList(), emptyList(), tags = emptyList(), taskTags = links)
            .filter { it.kind == SearchKind.TASK }.map { it.id }.toSet()
        assertEquals(emptySet<Long>(), ids)
    }

    // --- En curso = lo más accionable ahora (paridad con Qué Hacer Ahora) ---

    @Test fun inProgressTaskRanksAboveOverdueNotStarted() {
        // La búsqueda debe ordenar igual que "Qué hacer ahora": lo que se está
        // ejecutando AHORA (ventana startAt..startAt+duración activa) va primero,
        // por encima de una tarea vencida y urgente que aún no se ha empezado.
        // Antes, urgencyRank no tenía tier de "en curso", así que la tarea en curso
        // NORMAL caía al fondo (urgency 6) y la vencida urgente la superaba: la
        // búsqueda ofrecía primero algo que el usuario NO está haciendo en vez de
        // lo que ya tiene entre manos, contradiciendo su propia doc de paridad con
        // Qué Hacer Ahora y el orden que toda la app (WhatNowEngine, DayPlanner,
        // widget, guardián) ya centralizó en TaskRules.timeRank.
        val now = System.currentTimeMillis()
        val inProgress = TaskEntity(
            id = 50,
            title = "Reunión equipo",
            priority = TaskPriority.NORMAL,
            startAt = now - 30 * 60_000L,
            durationMinutes = 120
        )
        val overdue = TaskEntity(
            id = 51,
            title = "Reunión equipo",
            priority = TaskPriority.URGENT,
            dueAt = now - 3_600_000L
        )
        val results = SearchEngine.search("reunion", listOf(inProgress, overdue), emptyList(), emptyList(), emptyList(), now = now)
        assertEquals(2, results.size)
        assertEquals(50L, results.first().id)
    }

    @Test fun manuallyInProgressTaskRanksAboveOverdue() {
        // Una tarea con estado IN_PROGRESS (marcada a mano por el usuario, sin
        // startAt activo) también encabeza los resultados: el usuario la declaró
        // "en curso", así que es lo más accionable ahora. Misma regla que
        // TaskRules.timeRank, donde status==IN_PROGRESS es el rango máximo (6).
        val now = System.currentTimeMillis()
        val active = TaskEntity(
            id = 60,
            title = "Diseño",
            priority = TaskPriority.NORMAL,
            status = TaskStatus.IN_PROGRESS
        )
        val overdue = TaskEntity(
            id = 61,
            title = "Diseño",
            priority = TaskPriority.URGENT,
            dueAt = now - 3_600_000L
        )
        val results = SearchEngine.search("diseño", listOf(active, overdue), emptyList(), emptyList(), emptyList(), now = now)
        assertEquals(60L, results.first().id)
    }

    @Test fun overdueStillBeatsNonOverdueNonInProgress() {
        // La jerarquía histórica se mantiene intacta salvo el nuevo tier superior:
        // una vencida urgente sigue ganando a una normal pendiente (sin startAt ni
        // ventana activa). Garantiza que el fix no degrada el orden existente.
        val now = System.currentTimeMillis()
        val overdue = TaskEntity(
            id = 70,
            title = "Reunión equipo",
            priority = TaskPriority.URGENT,
            dueAt = now - 3_600_000L
        )
        val fresh = TaskEntity(
            id = 71,
            title = "Reunión equipo",
            priority = TaskPriority.NORMAL
        )
        val results = SearchEngine.search("reunion", listOf(overdue, fresh), emptyList(), emptyList(), emptyList(), now = now)
        assertEquals(70L, results.first().id)
    }

    // --- Inicio inminente / hueco olvidado: paridad con TaskRules.timeRank ---

    @Test fun imminentStartRanksAboveHighInboxTask() {
        // Paridad con "Qué hacer ahora": una cita que empieza en pocos minutos
        // ([TaskRules.isImminentStart], rango 4 en timeRank, misma banda que una
        // vencida) debe encabezar la búsqueda por encima de una tarea HIGH de la
        // bandeja que NO vence ni empieza ahora. Antes urgencyRank no tenía tier
        // para isImminentStart, así la reunión inminente NORMAL caía a urgency 7
        // (else) y la HIGH (urgency 5) la superaba: buscar "reunión" 5 min antes
        // de empezar mostraba primero algo de la bandeja en vez de la cita que se
        // avecina — justo el olvido que "Qué hacer ahora" evita elevándola al
        // rango 4. Sin nueva pantalla: solo reordena lo que ya aparece.
        val now = System.currentTimeMillis()
        val imminent = TaskEntity(
            id = 80,
            title = "Reunión equipo",
            priority = TaskPriority.NORMAL,
            startAt = now + 5 * 60_000L,
            durationMinutes = 60
        )
        val highInbox = TaskEntity(
            id = 81,
            title = "Reunión equipo",
            priority = TaskPriority.HIGH
        )
        val results = SearchEngine.search("reunion", listOf(imminent, highInbox), emptyList(), emptyList(), emptyList(), now = now)
        assertEquals(2, results.size)
        assertEquals(80L, results.first().id)
    }

    @Test fun imminentStartUrgentSharesOverdueBand() {
        // Una cita inminente URGENT comparte banda con una vencida NORMAL en
        // timeRank (ambas rango 4; la URGENT gana por priorityScore dentro de la
        // banda, igual que nextBestTask desempata por prioridad). La búsqueda debe
        // reflejarlo: la inminente URGENT va antes que la vencida NORMAL. Antes la
        // vencida NORMAL (urgency 2) superaba a la inminente URGENT (urgency 7),
        // invirtiendo el orden que toda la app usa en "Qué hacer ahora".
        val now = System.currentTimeMillis()
        val imminentUrgent = TaskEntity(
            id = 90,
            title = "Reunión equipo",
            priority = TaskPriority.URGENT,
            startAt = now + 5 * 60_000L,
            durationMinutes = 60
        )
        val overdueNormal = TaskEntity(
            id = 91,
            title = "Reunión equipo",
            priority = TaskPriority.NORMAL,
            dueAt = now - 3_600_000L
        )
        val results = SearchEngine.search("reunion", listOf(imminentUrgent, overdueNormal), emptyList(), emptyList(), emptyList(), now = now)
        assertEquals(90L, results.first().id)
    }

    // --- Vence hoy: paridad de bandas con TaskRules.timeRank (c.383) ---

    @Test fun dueTodayBeatsUrgentWithoutDateInSearch() {
        // Paridad con "Qué hacer ahora": en [TaskRules.timeRank] isDueToday
        // (rango 3) se evalúa ANTES que la prioridad URGENT (rango 2), así una
        // NORMAL que vence hoy manda sobre una URGENT sin fecha. Antes la
        // búsqueda ponía la URGENT sin fecha (urgency 4) por encima de la
        // NORMAL que vence hoy (urgency 6): buscar una cita que vence hoy la
        // enterraba bajo una urgente sin plazo, contradiciendo lo que sugería
        // "Qué hacer ahora". Mismo principio que isImminentStart: la banda
        // temporal manda sobre la prioridad pura.
        val now = System.currentTimeMillis()
        val dueToday = TaskEntity(
            id = 110,
            title = "Reunión equipo",
            priority = TaskPriority.NORMAL,
            dueAt = now
        )
        val urgentNoDate = TaskEntity(
            id = 111,
            title = "Reunión equipo",
            priority = TaskPriority.URGENT
        )
        val results = SearchEngine.search("reunion", listOf(dueToday, urgentNoDate), emptyList(), emptyList(), emptyList(), now = now)
        assertEquals(2, results.size)
        assertEquals(110L, results.first().id)
    }

    @Test fun dueTodayBeatsHighWithoutDateInSearch() {
        // Misma lógica contra HIGH: una NORMAL que vence hoy debe encabezar la
        // búsqueda sobre una HIGH sin fecha, igual que en timeRank (rango 3 > 1)
        // y simétrico a isImminentStart NORMAL sobre HIGH (c.664).
        val now = System.currentTimeMillis()
        val dueToday = TaskEntity(
            id = 120,
            title = "Reunión equipo",
            priority = TaskPriority.NORMAL,
            dueAt = now
        )
        val highNoDate = TaskEntity(
            id = 121,
            title = "Reunión equipo",
            priority = TaskPriority.HIGH
        )
        val results = SearchEngine.search("reunion", listOf(dueToday, highNoDate), emptyList(), emptyList(), emptyList(), now = now)
        assertEquals(120L, results.first().id)
    }

    @Test fun dueTodayUrgentBeatsDueTodayNormalInSearch() {
        // Dentro de la banda dueToday, URGENT desempata antes que NORMAL
        // (urgency 3 < 4), igual que priorityScore desempata dentro de cada
        // banda de timeRank. Garantiza que la prioridad sigue decidiendo cuando
        // la banda temporal es la misma.
        val now = System.currentTimeMillis()
        val dueTodayUrgent = TaskEntity(
            id = 130,
            title = "Reunión equipo",
            priority = TaskPriority.URGENT,
            dueAt = now
        )
        val dueTodayNormal = TaskEntity(
            id = 131,
            title = "Reunión equipo",
            priority = TaskPriority.NORMAL,
            dueAt = now
        )
        val results = SearchEngine.search("reunion", listOf(dueTodayUrgent, dueTodayNormal), emptyList(), emptyList(), emptyList(), now = now)
        assertEquals(130L, results.first().id)
    }

    @Test fun overdueStillBeatsDueTodayInSearch() {
        // La banda vencida (urgency 1/2) sigue por encima de vence-hoy
        // (urgency 3/4), igual que timeRank (rango 4 > 3): el plazo ya volado
        // manda sobre el de hoy. Evita que el reordenamiento de dueToday
        // promueva una tarea de hoy por encima de una ya vencida.
        val now = System.currentTimeMillis()
        val overdue = TaskEntity(
            id = 140,
            title = "Reunión equipo",
            priority = TaskPriority.URGENT,
            dueAt = now - 3_600_000L
        )
        val dueTodayNormal = TaskEntity(
            id = 141,
            title = "Reunión equipo",
            priority = TaskPriority.NORMAL,
            dueAt = now
        )
        val results = SearchEngine.search("reunion", listOf(overdue, dueTodayNormal), emptyList(), emptyList(), emptyList(), now = now)
        assertEquals(140L, results.first().id)
    }

    @Test fun missedStartElevatedAboveFreshInboxTask() {
        // Recuperación de tareas olvidadas: una tarea cuyo hueco planificado ya
        // pasó sin completarse ([TaskRules.isMissedStart] — el "olvido silencioso")
        // debe aflorar por encima de una captura fresca de la bandeja con la misma
        // prioridad, incluso cuando el título de la fresca ordenaría antes
        // alfabéticamente. nextBestTask desempata por isMissedStart dentro de la
        // banda de urgencia; la búsqueda no lo hacía, así la olvidada empatataba
        // con la fresca (urgency 7, dueAt MAX) y caía tras ella por título — el
        // compromiso agendado que se le pasó al usuario quedaba enterrado en la
        // búsqueda, contradiciendo el tema #1 de recuperación del producto.
        val now = System.currentTimeMillis()
        val missed = TaskEntity(
            id = 100,
            title = "Reunión equipo",
            priority = TaskPriority.NORMAL,
            startAt = now - 2 * 3_600_000L,
            durationMinutes = 30
        )
        val fresh = TaskEntity(
            id = 101,
            title = "Reunión aaa",
            priority = TaskPriority.NORMAL
        )
        val results = SearchEngine.search("reunion", listOf(missed, fresh), emptyList(), emptyList(), emptyList(), now = now)
        assertEquals(2, results.size)
        assertEquals(100L, results.first().id)
    }

    // --- "olvidé" (1ª persona del pretérito) activa la recuperación MISSED ---
    // Las formas de participio ("olvidadas"/"olvidados") ya activaban el scope
    // MISSED, pero la forma MÁS natural de buscar lo olvidado — "olvidé", que
    // foldForSearch pliega a "olvide" — no estaba en MISSED_TOKENS. El resultado
    // era que buscar "olvidé" devolvía VACÍO pese a haber tareas olvidadas
    // (hueco pasado, vencida o bandeja arrinconada): el tema #1 de recuperación
    // del producto quedaba ciego con la phrasing más común. Aquí se prueba que
    // "olvidé" ahora recupera una missed-start que una captura fresca NO es.
    @Test fun olvidé_activatesMissedRecovery() {
        val now = System.currentTimeMillis()
        val missed = TaskEntity(
            id = 100,
            title = "Reunión equipo",
            priority = TaskPriority.NORMAL,
            startAt = now - 2 * 3_600_000L,
            durationMinutes = 30
        )
        val fresh = TaskEntity(
            id = 101,
            title = "Reunión aaa",
            priority = TaskPriority.NORMAL
        )
        // Forma con tilde (entrada natural del usuario).
        val withAccent = SearchEngine.search("olvidé", listOf(missed, fresh), emptyList(), emptyList(), emptyList(), now = now)
        assertEquals(1, withAccent.size)
        assertEquals(100L, withAccent.first().id)
        // Forma sin tilde (plegada por foldForSearch): mismo resultado.
        val folded = SearchEngine.search("olvide", listOf(missed, fresh), emptyList(), emptyList(), emptyList(), now = now)
        assertEquals(1, folded.size)
        assertEquals(100L, folded.first().id)
    }

    // Guard documentado: el infinitivo "olvidar" y el sustantivo "olvido" NO
    // activan el scope MISSED, para no disparar la recuperación con un título de
    // tarea tipo "olvidar hacer X". Añadir "olvide" (1ª persona, no infinitivo)
    // preserva este guard: "olvidar" sigue sin recuperar tareas olvidadas.
    @Test fun olvidar_doesNotActivateMissedRecovery_guard() {
        val now = System.currentTimeMillis()
        val missed = TaskEntity(
            id = 100,
            title = "Reunión equipo",
            priority = TaskPriority.NORMAL,
            startAt = now - 2 * 3_600_000L,
            durationMinutes = 30
        )
        val results = SearchEngine.search("olvidar", listOf(missed), emptyList(), emptyList(), emptyList(), now = now)
        assertTrue(results.isEmpty())
    }

    // --- "se me pasó": la forma frasa coloquial del olvido ("¿qué se me pasó?") ---
    // El asistente responde a "¿qué olvidé?" porque detecta la intención
    // forgotten; la búsqueda universal recuperaba con participios
    // ("olvidadas") y con la 1ª persona del pretérito ("olvidé"). Pero la forma
    // MÁS natural de preguntar por el olvido silencioso ([TaskRules.isMissedStart])
    // es la frase "se me pasó" — singular — o "se me pasaron" — plural. Sin
    // detección de frase, esas palabras son stop-words ("se","me") + "pasó" (no
    // está en MISSED_TOKENS), así que buscar así devolvía vacío pese a haber
    // olvidos. La detección se hace como segmento precomputable del query
    // normalizado (patrón `agendaHolidayMorning`, c.778), NO por token: "paso"
    // está en STOP_WORDS precisamente para no secuestrar recuperación con
    // títulos como "primer paso", así que prohibido añadirlo a MISSED_TOKENS.
    @Test fun seMePaso_activatesMissedRecovery() {
        val now = System.currentTimeMillis()
        val missed = TaskEntity(
            id = 110,
            title = "Llamada agendada",
            priority = TaskPriority.NORMAL,
            startAt = now - 90 * 60_000L,
            durationMinutes = 30
        )
        val results = SearchEngine.search("se me pasó", listOf(missed), emptyList(), emptyList(), emptyList(), now = now)
        assertEquals(1, results.size)
        assertEquals(110L, results.first().id)
    }

    @Test fun seMePasaron_plural_activatesMissedRecovery() {
        val now = System.currentTimeMillis()
        val missed = TaskEntity(
            id = 111,
            title = "Revisión de contrato",
            priority = TaskPriority.NORMAL,
            startAt = now - 60 * 60_000L,
            durationMinutes = 25
        )
        val results = SearchEngine.search("se me pasaron", listOf(missed), emptyList(), emptyList(), emptyList(), now = now)
        assertEquals(1, results.size)
        assertEquals(111L, results.first().id)
    }

    // Guard: "paso" solo no dispara el scope MISSED (detección por frase, no
    // palabra suelta). Vocabulario "se me pasó": lander c.785.
    @Test fun pasoBare_doesNotActivateMissedRecovery_guard() {
        val now = System.currentTimeMillis()
        val missed = TaskEntity(
            id = 112,
            title = "Reunión equipo",
            priority = TaskPriority.NORMAL,
            startAt = now - 2 * 3_600_000L,
            durationMinutes = 30
        )
        val results = SearchEngine.search("paso", listOf(missed), emptyList(), emptyList(), emptyList(), now = now)
        assertTrue(results.isEmpty())
    }

    // c.786: extensión simétrica de la frase de olvido a primera persona
    // plural — "se nos pasó"/"se nos pasaron". c.785 sólo cubrió 1.ª
    // singular ("se me"), así que la forma plural caía a vacío (mentira por
    // omisión en la recuperación de olvidos).
    @Test fun seNosPaso_firstPersonPlural_activatesMissedRecovery() {
        val now = System.currentTimeMillis()
        val missed = TaskEntity(
            id = 113,
            title = "Recogida de vacío",
            priority = TaskPriority.NORMAL,
            startAt = now - 90 * 60_000L,
            durationMinutes = 30
        )
        val results = SearchEngine.search("se nos pasó", listOf(missed), emptyList(), emptyList(), emptyList(), now = now)
        assertEquals(1, results.size)
        assertEquals(113L, results.first().id)
    }

    @Test fun seNosPasaron_firstPersonPlural_activatesMissedRecovery() {
        val now = System.currentTimeMillis()
        val missed = TaskEntity(
            id = 114,
            title = "Visita al taller",
            priority = TaskPriority.NORMAL,
            startAt = now - 60 * 60_000L,
            durationMinutes = 20
        )
        val results = SearchEngine.search("se nos pasaron las cosas", listOf(missed), emptyList(), emptyList(), emptyList(), now = now)
        assertEquals(1, results.size)
        assertEquals(114L, results.first().id)
    }

    // --- "anteayer"/"antier": paridad de búsqueda con la captura ---
    // El parser de captura resuelve "anteayer"/"antier" a base.minusDays(2)
    // (NaturalTaskParserTest "anteayer/antier"). La búsqueda ya honraba la
    // simetría futura con DAY_AFTER_TOMORROW ("pasado mañana"), pero NO la pasada:
    // una tarea capturada como "reunión antier" (dueAt = hace 2 días) era
    // IRRECUPERABLE al buscar "antier"/"anteayer" salvo que su título contuviera
    // literalmente la palabra. Ahora buscar y capturar significan lo mismo en
    // ambos sentidos del calendario. Detección por palabra exacta (no subcadena):
    // "anteayer" no casa con "ayer" como palabra, así que "anteayer" no cae a
    // YESTERDAY; el scope DAY_BEFORE_YESTERDAY es el único que se activa.
    @Test fun anteayer_recoversTaskDueTwoDaysAgoByDateScope() {
        val now = System.currentTimeMillis()
        val twoDaysAgo = now - 2 * 24 * 3_600_000L
        // El título NO contiene "anteayer": la recuperación es por fecha, no por texto.
        val task = TaskEntity(
            id = 200,
            title = "Reunión equipo",
            priority = TaskPriority.NORMAL,
            dueAt = twoDaysAgo
        )
        val results = SearchEngine.search("anteayer", listOf(task), emptyList(), emptyList(), emptyList(), now = now)
        assertEquals(1, results.size)
        assertEquals(200L, results.first().id)
    }

    // "antier" = variante coloquial hispanoamericana (MX/CA/parts SA) de "anteayer":
    // el parser la admite con la misma resolución; la búsqueda debe ser simétrica.
    @Test fun antier_recoversTaskDueTwoDaysAgoByDateScope() {
        val now = System.currentTimeMillis()
        val twoDaysAgo = now - 2 * 24 * 3_600_000L
        val task = TaskEntity(
            id = 201,
            title = "Cita médica",
            priority = TaskPriority.NORMAL,
            dueAt = twoDaysAgo
        )
        val results = SearchEngine.search("antier", listOf(task), emptyList(), emptyList(), emptyList(), now = now)
        assertEquals(1, results.size)
        assertEquals(201L, results.first().id)
    }

    // "anteayer" no arrastra tareas de hoy ni de ayer: el scope se ancla al día
    // exacto hace 2 días, igual que "ayer" se ancla a hace 1 y "hoy" a hoy.
    @Test fun anteayer_excludesTodayAndYesterdayTasks() {
        val now = System.currentTimeMillis()
        val yesterday = now - 24 * 3_600_000L
        val twoDaysAgo = now - 2 * 24 * 3_600_000L
        val today = TaskEntity(id = 210, title = "Cita hoy", priority = TaskPriority.NORMAL, dueAt = now)
        val yesterdayTask = TaskEntity(id = 211, title = "Cita ayer", priority = TaskPriority.NORMAL, dueAt = yesterday)
        val twoDaysAgoTask = TaskEntity(id = 212, title = "Cita", priority = TaskPriority.NORMAL, dueAt = twoDaysAgo)
        val results = SearchEngine.search("anteayer", listOf(today, yesterdayTask, twoDaysAgoTask), emptyList(), emptyList(), emptyList(), now = now)
        assertEquals(1, results.size)
        assertEquals(212L, results.first().id)
    }

    // "anteayer" recupera también tareas completadas (es un scope PASADO, como
    // "ayer"/"semana pasada"/"mes pasado"): su propósito es revisar qué había en
    // ese día, incluido lo ya terminado. Es la lectura natural de "qué hice
    // anteayer". Las canceladas se excluyen siempre (no son información útil).
    @Test fun anteayer_recoversCompletedTaskAnchoredTwoDaysAgo() {
        val now = System.currentTimeMillis()
        val twoDaysAgo = now - 2 * 24 * 3_600_000L
        val completedTask = TaskEntity(
            id = 220,
            title = "Enviar correo",
            priority = TaskPriority.NORMAL,
            dueAt = twoDaysAgo,
            status = TaskStatus.COMPLETED,
            completedAt = twoDaysAgo
        )
        val results = SearchEngine.search("anteayer", listOf(completedTask), emptyList(), emptyList(), emptyList(), now = now)
        assertEquals(1, results.size)
        assertEquals(220L, results.first().id)
    }

    // --- Recuperacion del 4. olvido en la busqueda universal ---
    // Un compromiso (promesa de conversacion) vencido y PENDING es informacion
    // critica: algo que alguien (o yo) prometio y cuyo plazo ya expiro. El
    // producto lo nombra en TODAS las superficies de recuperacion (asistente
    // "que olvide?", guardian, resumen, planificador) PERO la busqueda universal
    // lo EXCLUIA: "vencidas"/"atrasadas"/"olvidadas" son `pureDateScope` (sin
    // palabras de contenido), y el filtro `!pureDateScope` suprimia los compromisos;
    // con texto, "vencidas" no casa con `action`/`actor`/`location`. Asi un
    // compromiso vencido era IRRECUPERABLE salvo tecleando literalmente la accion
    // o el actor - justo el olvido que la busqueda deberia rescatar. Ahora, al
    // scope OVERDUE/MISSED, un compromiso vencido entra por
    // [CommitmentRules.isOverduePending], honrando la fuente unica de verdad, sin
    // exigir coincidencia lexica (igual que una tarea vencida entra por scope sin
    // que su titulo diga "vencida"). Sinonimos deben ser consistentes entre si.

    private fun overduePendingCommitment(now: Long): CommitmentEntity = CommitmentEntity(
        id = 300,
        conversationId = 100,
        kind = CommitmentKind.OTHER_COMMITMENT,
        owner = CommitmentOwner.OTHER,
        actor = "Maria",
        action = "llamar",
        dueAt = now - 24 * 3_600_000L, // vencido hace 1 dia
        confidence = 0.9f,
        reviewStatus = CommitmentReviewStatus.PENDING,
        fingerprint = "fp-vencida"
    )

    @Test fun vencidas_recoversOverduePendingCommitment() {
        val now = System.currentTimeMillis()
        val results = SearchEngine.search(
            "vencidas", emptyList(), emptyList(), emptyList(), emptyList(),
            commitments = listOf(overduePendingCommitment(now)), now = now
        )
        assertEquals(1, results.size)
        assertEquals(SearchKind.COMMITMENT, results.first().kind)
        assertEquals(300L, results.first().id)
    }

    @Test fun vencidos_recoversOverduePendingCommitment() {
        val now = System.currentTimeMillis()
        val results = SearchEngine.search(
            "vencidos", emptyList(), emptyList(), emptyList(), emptyList(),
            commitments = listOf(overduePendingCommitment(now)), now = now
        )
        assertEquals(1, results.size)
        assertEquals(SearchKind.COMMITMENT, results.first().kind)
    }

    @Test fun olvidadas_recoversOverduePendingCommitment() {
        val now = System.currentTimeMillis()
        val results = SearchEngine.search(
            "olvidadas", emptyList(), emptyList(), emptyList(), emptyList(),
            commitments = listOf(overduePendingCommitment(now)), now = now
        )
        assertEquals(1, results.size)
        assertEquals(SearchKind.COMMITMENT, results.first().kind)
    }

    @Test fun atrasadas_recoversOverduePendingCommitment() {
        val now = System.currentTimeMillis()
        val results = SearchEngine.search(
            "atrasadas", emptyList(), emptyList(), emptyList(), emptyList(),
            commitments = listOf(overduePendingCommitment(now)), now = now
        )
        assertEquals(1, results.size)
        assertEquals(SearchKind.COMMITMENT, results.first().kind)
    }

    // "vencidas" no arrastra compromisos NO vencidos (futuros o sin fecha): el scope
    // OVERDUE solo abre el guard para los que `isOverduePending` confirma. Un
    // compromiso futuro (dueAt > now) o sin dueAt PENDING no es "lo vencido".
    @Test fun vencidas_doesNotRecoverNonOverdueCommitment() {
        val now = System.currentTimeMillis()
        val futureCommitment = CommitmentEntity(
            id = 301, conversationId = 100, kind = CommitmentKind.OTHER_COMMITMENT,
            owner = CommitmentOwner.OTHER, actor = "Maria", action = "llamar",
            dueAt = now + 24 * 3_600_000L, confidence = 0.9f,
            reviewStatus = CommitmentReviewStatus.PENDING, fingerprint = "fp-futura"
        )
        val noDueCommitment = CommitmentEntity(
            id = 302, conversationId = 100, kind = CommitmentKind.OTHER_COMMITMENT,
            owner = CommitmentOwner.OTHER, actor = "Luis", action = "enviar",
            dueAt = null, confidence = 0.9f,
            reviewStatus = CommitmentReviewStatus.PENDING, fingerprint = "fp-sinfecha"
        )
        val results = SearchEngine.search(
            "vencidas", emptyList(), emptyList(), emptyList(), emptyList(),
            commitments = listOf(futureCommitment, noDueCommitment), now = now
        )
        assertTrue(results.none { it.kind == SearchKind.COMMITMENT })
    }

    // "vencidas" no recupera compromisos ya CONVERTED o DISMISSED: la fuente unica
    // de verdad [CommitmentRules.isOverduePending] exige PENDING. Un compromiso
    // convertido en tarea o descartado ya no es un "olvido" pendiente.
    @Test fun vencidas_doesNotRecoverConvertedOrDismissedOverdueCommitments() {
        val now = System.currentTimeMillis()
        val converted = overduePendingCommitment(now).copy(
            id = 310, reviewStatus = CommitmentReviewStatus.CONVERTED, fingerprint = "fp-conv"
        )
        val dismissed = overduePendingCommitment(now).copy(
            id = 311, reviewStatus = CommitmentReviewStatus.DISMISSED, fingerprint = "fp-dism"
        )
        val results = SearchEngine.search(
            "vencidas", emptyList(), emptyList(), emptyList(), emptyList(),
            commitments = listOf(converted, dismissed), now = now
        )
        assertTrue(results.none { it.kind == SearchKind.COMMITMENT })
    }

    // "pendientes" recupera los compromisos PENDING sin importar su fecha: un
    // compromiso PENDIENTE es, por definicion, "pendiente" (de revisar/convertir).
    // Antes de este fix "pendientes" EXCLUIA toda promesa: "pendient" no activaba
    // `wantsCommitments` (solo "compromiso" lo hacia), asi que el guard `!typed`
    // (activado por "pendiente"->wantsTasks) suprimia los compromisos por completo
    // - justo el olvido que la busqueda deberia rescatar. Simetrico a como una
    // tarea no completada entra al buscar "pendiente" sin que su titulo lo diga.
    // Recupera futuros, sin fecha y vencidos por igual: todos son "pendientes de
    // revisar". Honra el 4. olvido de Ordia en la superficie por excelencia.
    @Test fun pendientes_recoversAllPendingCommitments() {
        val now = System.currentTimeMillis()
        val overdue = CommitmentEntity(
            id = 320, conversationId = 100, kind = CommitmentKind.OTHER_COMMITMENT,
            owner = CommitmentOwner.OTHER, actor = "Maria", action = "llamar",
            dueAt = now - 24 * 3_600_000L, confidence = 0.9f,
            reviewStatus = CommitmentReviewStatus.PENDING, fingerprint = "fp-ven"
        )
        val future = CommitmentEntity(
            id = 321, conversationId = 100, kind = CommitmentKind.OTHER_COMMITMENT,
            owner = CommitmentOwner.OTHER, actor = "Luis", action = "enviar",
            dueAt = now + 24 * 3_600_000L, confidence = 0.9f,
            reviewStatus = CommitmentReviewStatus.PENDING, fingerprint = "fp-fut"
        )
        val noDate = CommitmentEntity(
            id = 322, conversationId = 100, kind = CommitmentKind.OTHER_COMMITMENT,
            owner = CommitmentOwner.OTHER, actor = "Socio", action = "pensar oferta",
            dueAt = null, confidence = 0.9f,
            reviewStatus = CommitmentReviewStatus.PENDING, fingerprint = "fp-nodate"
        )
        val results = SearchEngine.search(
            "pendientes", emptyList(), emptyList(), emptyList(), emptyList(),
            commitments = listOf(overdue, future, noDate), now = now
        )
        assertEquals(3, results.size)
        assertEquals(setOf(320L, 321L, 322L), results.map { it.id }.toSet())
        results.forEach { assertEquals(SearchKind.COMMITMENT, it.kind) }
    }

    // "pendientes" NO recupera compromisos CONVERTED o DISMISSED: ya no son
    // "pendientes". Un compromiso convertido en tarea o descartado dejo de ser un
    // pendiente de revisar - igual que una tarea completada no aparece al buscar
    // "pendiente". Coherencia con [CommitmentRules.isOverduePending] (que exige
    // PENDING) y con el filtro de tareas `!task.completed`.
    @Test fun pendientes_doesNotRecoverConvertedOrDismissedCommitments() {
        val now = System.currentTimeMillis()
        val converted = CommitmentEntity(
            id = 330, conversationId = 100, kind = CommitmentKind.OTHER_COMMITMENT,
            owner = CommitmentOwner.OTHER, actor = "Maria", action = "llamar",
            dueAt = now - 24 * 3_600_000L, confidence = 0.9f,
            reviewStatus = CommitmentReviewStatus.CONVERTED, resultTaskId = 999L,
            fingerprint = "fp-conv"
        )
        val dismissed = CommitmentEntity(
            id = 331, conversationId = 100, kind = CommitmentKind.OTHER_COMMITMENT,
            owner = CommitmentOwner.OTHER, actor = "Luis", action = "enviar",
            dueAt = now + 24 * 3_600_000L, confidence = 0.9f,
            reviewStatus = CommitmentReviewStatus.DISMISSED, fingerprint = "fp-dism"
        )
        val results = SearchEngine.search(
            "pendientes", emptyList(), emptyList(), emptyList(), emptyList(),
            commitments = listOf(converted, dismissed), now = now
        )
        assertTrue(results.none { it.kind == SearchKind.COMMITMENT })
    }

    // "pendientes" sigue recuperando las tareas no completadas (su comportamiento
    // original) ADEMAS de los compromisos PENDING: una sola query recupera TODO lo
    // pendiente en todas las clases de olvido. Menos friccion, mas potencia: el
    // usuario no tiene que saber si "eso que falta" es una tarea o una promesa.
    @Test fun pendientes_recoversPendingTasksAndPendingCommitments() {
        val now = System.currentTimeMillis()
        val pendingTask = TaskEntity(id = 1, title = "Llamar al dentista", status = TaskStatus.PLANNED, completed = false)
        val completedTask = TaskEntity(id = 2, title = "Comprar pan", status = TaskStatus.COMPLETED, completed = true)
        val commitment = CommitmentEntity(
            id = 340, conversationId = 100, kind = CommitmentKind.OTHER_COMMITMENT,
            owner = CommitmentOwner.OTHER, actor = "Maria", action = "enviar proposal",
            dueAt = null, confidence = 0.9f,
            reviewStatus = CommitmentReviewStatus.PENDING, fingerprint = "fp-pend"
        )
        val results = SearchEngine.search(
            "pendientes", listOf(pendingTask, completedTask), emptyList(), emptyList(),
            emptyList(), commitments = listOf(commitment), now = now
        )
        val taskIds = results.filter { it.kind == SearchKind.TASK }.map { it.id }
        val commitmentIds = results.filter { it.kind == SearchKind.COMMITMENT }.map { it.id }
        assertTrue(pendingTask.id in taskIds)
        assertFalse(completedTask.id in taskIds)
        assertEquals(listOf(340L), commitmentIds)
    }

    // --- Completadas hunden al fondo en búsqueda normal (lo más accionable primero) ---
    // Una tarea completada con `dueAt` pasado NO debe aparecer por encima de una
    // pendiente de la misma query. Antes del fix ambas empataban en urgencia (7) y
    // el desempate por `dueAt` ascendente dejaba la completada (fecha pasada, pequeña)
    // POR ENCIMA de la pendiente actual (sin dueAt, MAX): buscar "reunión" ofrecía
    // primero una reunión ya hecha en vez de la que falta por hacer. La completada
    // sigue recuperable (sigue en los resultados), pero ahora ocupa el último tier.
    @Test fun completedTask_doesNotRankAbovePendingInNormalSearch() {
        val now = System.currentTimeMillis()
        val pastDue = now - 10L * 24 * 60 * 60 * 1000 // hace ~10 días
        val done = TaskEntity(
            id = 1, title = "Reunión equipo", priority = TaskPriority.NORMAL,
            completed = true, dueAt = pastDue, completedAt = pastDue
        )
        val pending = TaskEntity(id = 2, title = "Reunión equipo", priority = TaskPriority.NORMAL, dueAt = null)
        val ids = SearchEngine.search("reunion", listOf(done, pending), emptyList(), emptyList(), emptyList(), now = now)
            .map { it.id }
        assertEquals(listOf(2L, 1L), ids)
    }

    // Variante con varias completadas: entre sí conservan un orden estable y predecible
    // (por `dueAt` ascendente) y todas quedan por debajo de la pendiente.
    @Test fun completedTasks_sinkBelowPending_andKeepRelativeOrder() {
        val now = System.currentTimeMillis()
        val older = TaskEntity(id = 1, title = "Reunión vieja", priority = TaskPriority.URGENT, completed = true, dueAt = now - 20L * 24 * 60 * 60 * 1000, completedAt = now)
        val newer = TaskEntity(id = 2, title = "Reunión reciente", priority = TaskPriority.NORMAL, completed = true, dueAt = now - 2L * 24 * 60 * 60 * 1000, completedAt = now)
        val pending = TaskEntity(id = 3, title = "Reunión pendiente", priority = TaskPriority.LOW, dueAt = null)
        val ids = SearchEngine.search("reunion", listOf(pending, older, newer), emptyList(), emptyList(), emptyList(), now = now)
            .map { it.id }
        // La pendiente va primero; luego las completadas por dueAt ascendente (older antes).
        assertEquals(listOf(3L, 1L, 2L), ids)
    }

    // "completadas" sigue devolviendo SOLO completadas y ordenadas entre sí (no se
    // pierde la recuperación al hundir el tier).
    @Test fun completadasQuery_keepsCompletedResultsOrdered() {
        val now = System.currentTimeMillis()
        val a = TaskEntity(id = 1, title = "Reunión A", completed = true, dueAt = now - 5L * 24 * 60 * 60 * 1000, completedAt = now)
        val b = TaskEntity(id = 2, title = "Reunión B", completed = true, dueAt = now - 1L * 24 * 60 * 60 * 1000, completedAt = now)
        val pending = TaskEntity(id = 3, title = "Reunión C", completed = false)
        val ids = SearchEngine.search("completadas reunion", listOf(a, b, pending), emptyList(), emptyList(), emptyList(), now = now)
            .map { it.id }
        assertEquals(listOf(1L, 2L), ids)
    }

    // --- Filtros de intención por palabra (no subcadena) ---
    // Los filtros léxicos de atributo ("vencidas"→sólo vencidas, "pendiente"→sólo
    // no completadas, "urgente"/"importante"→prioridad) se detectaban como
    // SUBCADENA del query normalizado. Eso secuestraba búsquedas de contenido
    // legítimas: "convencido" casa la subcadena "vencid" y la búsqueda pasaba a
    // devolver SOLO tareas vencidas (una tarea "Estar convencido" sin vencer era
    // IRRECUPERABLE); "dependiente" casa "pendiente" y se excluía a una tarea
    // COMPLETADA ("Empleado dependiente") que el usuario buscaba por su título.
    // La detección por palabra exacta (plural incluido) elimina los falsos
    // positivos sin perder las formas legítimas ("vencidas"/"pendientes").

    @Test fun convencido_doesNotHijackIntoOverdueOnlyFilter() {
        // "convencido" contiene la subcadena "vencid": antes el filtro
        // `!normalized.contains("vencid") || isOverdue` excluía una tarea NO
        // vencida cuyo título la contiene. Recuperación de información rota.
        val task = TaskEntity(id = 1, title = "Estar convencido", dueAt = null)
        val results = SearchEngine.search("convencido", listOf(task), emptyList(), emptyList(), emptyList())
        assertEquals(listOf(1L), results.map { it.id })
    }

    @Test fun convencidas_pluralAlsoNotHijacked() {
        val task = TaskEntity(id = 1, title = "Personas convencidas", dueAt = null)
        val results = SearchEngine.search("convencidas", listOf(task), emptyList(), emptyList(), emptyList())
        assertEquals(listOf(1L), results.map { it.id })
    }

    @Test fun dependiente_recoversCompletedTaskByTitle() {
        // "dependiente" contiene la subcadena "pendiente": antes el filtro
        // `!normalized.contains("pendiente") || !task.completed` excluía una tarea
        // COMPLETADA cuyo título la contiene. El usuario pierde lo ya hecho.
        val task = TaskEntity(id = 1, title = "Empleado dependiente", completed = true)
        val results = SearchEngine.search("dependiente", listOf(task), emptyList(), emptyList(), emptyList())
        assertEquals(listOf(1L), results.map { it.id })
    }

    @Test fun independiente_recoversTaskByTitle() {
        // "independiente" también contiene "pendiente": mismo secuestro.
        val task = TaskEntity(id = 1, title = "Proyecto independiente", dueAt = null)
        val results = SearchEngine.search("independiente", listOf(task), emptyList(), emptyList(), emptyList())
        assertEquals(listOf(1L), results.map { it.id })
    }

    @Test fun vencidas_keepsOverdueOnlyFilterAfterWordLevelFix() {
        // Regresión: la forma legítima "vencidas" sigue filtrando a vencidas sólo.
        val now = System.currentTimeMillis()
        val overdue = TaskEntity(id = 1, title = "Factura", dueAt = now - 2L * 24 * 60 * 60 * 1000)
        val pending = TaskEntity(id = 2, title = "Factura", dueAt = now + 2L * 24 * 60 * 60 * 1000)
        val ids = SearchEngine.search("vencidas", listOf(overdue, pending), emptyList(), emptyList(), emptyList(), now = now).map { it.id }
        assertEquals(listOf(1L), ids)
    }

    @Test fun pendiente_keepsIncompleteOnlyFilterAfterWordLevelFix() {
        // Regresión: la forma legítima "pendiente" sigue excluyendo completadas.
        val now = System.currentTimeMillis()
        val pending = TaskEntity(id = 1, title = "Pago", completed = false, dueAt = now + 2L * 24 * 60 * 60 * 1000)
        val done = TaskEntity(id = 2, title = "Pago", completed = true)
        val ids = SearchEngine.search("pendiente", listOf(pending, done), emptyList(), emptyList(), emptyList(), now = now).map { it.id }
        assertEquals(listOf(1L), ids)
    }

    // --- Calificador "activo(s)/activa(s)" sobre las familias listables (c.797) ---
    // Un hábito lo es si no está archivado (el buscador ya filtra `archived`); una
    // rutina/proyecto activa = no archivada. Sin esta regla, «habitos activos»/
    // «rutinas activas» exigían «activo» en el título y el listado volvía vacío —
    // mentira por omisión. Se ignora SOLO cuando hay familia (wantsHabits/
    // wantsRoutines/wantsProjects), igual que se ignoran «habito»/«rutina»/
    // «proyecto» en `semanticMatches`.

    @Test fun habitosActivos_listsHabitsWithoutTitleQualifier() {
        val habit = HabitEntity(id = 7, title = "Leer")
        val results = SearchEngine.search(
            "habitos activos",
            emptyList(), emptyList(), emptyList(), listOf(habit)
        )
        assertEquals(listOf(7L), results.map { it.id })
    }

    @Test fun rutinasActivas_listsRoutinesWithoutTitleQualifier() {
        val routine = RoutineEntity(id = 8, name = "Gym")
        val results = SearchEngine.search(
            "rutinas activas",
            emptyList(), emptyList(), emptyList(), emptyList(),
            routines = listOf(routine)
        )
        assertEquals(listOf(8L), results.map { it.id })
    }

    @Test fun proyectosActivos_listsProjectsWithoutTitleQualifier() {
        val project = ProjectEntity(id = 9, name = "Mudanza")
        val results = SearchEngine.search(
            "proyectos activos",
            emptyList(), listOf(project), emptyList(), emptyList()
        )
        assertEquals(listOf(9L), results.map { it.id })
    }

    @Test fun habitosActivos_stillExcludesArchivedHabits() {
        // El calificador se ignora, pero el filtro de archivado ES la semántica
        // honesta de «activo» para la familia: la archivada no ha de pasar.
        val activa = HabitEntity(id = 7, title = "Leer")
        val archivada = HabitEntity(id = 8, title = "Correr", archived = true)
        val results = SearchEngine.search(
            "habitos activos",
            emptyList(), emptyList(), emptyList(), listOf(activa, archivada)
        )
        assertEquals(listOf(7L), results.map { it.id })
    }

    @Test fun activoQualifier_aloneOnTitleStillMatches() {
        // Guardia: «activos» SIN familia sigue siendo contenido legítimo — una
        // tarea cuyo título es «Activos» se recupera por texto, no por filtro.
        val task = TaskEntity(id = 12, title = "Cierre activos 2025")
        val results = SearchEngine.search("activos", listOf(task), emptyList(), emptyList(), emptyList())
        assertEquals(listOf(12L), results.map { it.id })
    }

    // ── c.963: alcance de fecha sobre notas («notas de ayer») ────────────────
    // El buscador detectaba el alcance («ayer», «esta semana»…) pero para las
    // notas lo IGNORABA: «notas de ayer» devolvía TODAS las notas y la escrita
    // ayer quedaba invisible entre el resto (sonda JVM c.963: 6/6 consultas
    // con alcance devolvían las 5 notas del fixture). Una nota no tiene
    // startAt/dueAt; su fecha natural es createdAt (cuándo la escribió el
    // usuario). El alcance ahora filtra por createdAt con el mismo anclaje
    // calendario que las tareas. Baseline preservado: un alcance puro SIN la
    // palabra «nota» («ayer» a secas) sigue sin listar notas (anti-ruido), y
    // «notas»/«notas de compras» no cambian.

    private val notesScopeZone: ZoneId = ZoneId.systemDefault()
    // Lunes 2026-08-24 12:00 local: hoy=lunes, ayer=domingo (semana pasada).
    private val notesScopeToday: LocalDate = LocalDate.of(2026, 8, 24)
    private val notesScopeNow: Long =
        notesScopeToday.atTime(LocalTime.NOON).atZone(notesScopeZone).toInstant().toEpochMilli()

    private fun noteOn(id: Long, title: String, date: LocalDate, hour: Int = 9): NoteEntity {
        val epoch = date.atTime(LocalTime.of(hour, 0)).atZone(notesScopeZone).toInstant().toEpochMilli()
        return NoteEntity(id = id, title = title, createdAt = epoch, updatedAt = epoch)
    }

    private fun notesScopeFixture(): List<NoteEntity> = listOf(
        noteOn(1, "Nota de hoy", notesScopeToday),
        noteOn(2, "Nota de ayer", notesScopeToday.minusDays(1), 15),
        noteOn(3, "Nota semana pasada", notesScopeToday.minusDays(5), 10),
        noteOn(4, "Nota mes pasado", LocalDate.of(2026, 7, 15), 10),
        noteOn(5, "Lista de compras", notesScopeToday, 10)
    )

    private fun noteIds(query: String): Set<Long> =
        SearchEngine.search(query, emptyList(), emptyList(), notesScopeFixture(), emptyList(), now = notesScopeNow)
            .filter { it.kind == SearchKind.NOTE }.map { it.id }.toSet()

    @Test fun notesDateScope_filtersByCreatedAt() {
        assertEquals(setOf(2L), noteIds("notas de ayer"))
        assertEquals(setOf(1L, 5L), noteIds("notas de hoy"))
        // Semana calendario lunes-domingo: hoy (lunes 24) es el único de ESTA
        // semana; ayer (domingo 23) ya pertenece a la pasada.
        assertEquals(setOf(1L, 5L), noteIds("notas de esta semana"))
        assertEquals(setOf(2L, 3L), noteIds("notas de la semana pasada"))
        assertEquals(setOf(1L, 2L, 3L, 5L), noteIds("notas de este mes"))
        assertEquals(setOf(4L), noteIds("notas del mes pasado"))
    }

    @Test fun notesDateScope_weekdayScopeFiltersByCreatedAt() {
        // Hoy ES lunes: «notas del lunes» recupera las escritas hoy, no las de
        // otros días (paridad con «tareas del lunes» vía weekdayTarget).
        assertEquals(setOf(1L, 5L), noteIds("notas del lunes"))
    }

    @Test fun notesDateScope_futureScopeReturnsNoNotes() {
        // Honestidad: no existen notas escritas en el futuro — el alcance
        // futuro devuelve vacío, nunca el listado completo.
        assertEquals(emptySet<Long>(), noteIds("notas de mañana"))
    }

    @Test fun notesDateScope_untypedPureScopeStillExcludesNotes() {
        // Baseline anti-ruido intacto: «ayer» a secas (alcance puro sin la
        // palabra «nota») NO debe inundar los resultados con notas.
        assertEquals(emptySet<Long>(), noteIds("ayer"))
    }

    @Test fun notesDateScope_listingAndContentQualifierUnaffected() {
        // Baseline intacto: el listado desnudo sigue devolviendo todas las
        // notas y el calificador de contenido sigue filtrando por texto.
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), noteIds("notas"))
        assertEquals(setOf(5L), noteIds("notas de compras"))
    }
}
