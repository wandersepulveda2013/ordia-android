package com.ordia.app.assistant

import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.CommitmentReviewStatus
import com.ordia.app.data.local.ConversationEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.domain.CommitmentRules
import com.ordia.app.domain.DateRules
import com.ordia.app.domain.DayLoad
import com.ordia.app.domain.LearningProfile
import com.ordia.app.domain.SummaryEngine
import com.ordia.app.domain.TaskRules
import com.ordia.app.domain.WhatNowEngine
import com.ordia.app.domain.WhatNowReason
import com.ordia.app.domain.foldForSearch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class AssistantAction { NONE, OPEN_PLANNER, OPEN_CONVERSATIONS, RUN_REPLAN, CREATE_NOTE, OPEN_SEARCH }

data class AssistantAnswer(
    val text: String,
    val action: AssistantAction = AssistantAction.NONE,
    val actionPayload: String = "",
    val relatedTaskIds: List<Long> = emptyList()
)

/** Asistente determinista y local; nunca necesita red ni una clave de API. */
object AssistantEngine {
    fun answer(
        request: String,
        tasks: List<TaskEntity>,
        conversations: List<ConversationEntity>,
        commitments: List<CommitmentEntity>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        profile: LearningProfile? = null
    ): AssistantAnswer {
        val clean = request.trim().take(2_000)
        val query = clean.foldForSearch()
        if (query.isBlank()) return AssistantAnswer("Escribe qué necesitas organizar.")
        // Solo tareas raíz (parentTaskId == null): las subtareas son anidadas y
        // contarlas además del padre infla los conteos que el usuario lee ("3
        // pendientes" por un proyecto descompuesto en 2 partes, "3 vencidas" por
        // una entrega con 2 subtareas vencidas). Es la misma fuente única de
        // verdad que usan SummaryEngine, GuardianEngine y WhatNowEngine, de forma
        // que el asistente no mienta sobre cuántos compromisos reales hay.
        val active = tasks.filter { TaskRules.isActive(it) && it.parentTaskId == null }
        val overdue = active.filter { TaskRules.isOverdue(it, now) }
        val pendingCommitments = commitments.filter { it.reviewStatus == CommitmentReviewStatus.PENDING }
        // Cuarto olvido de Ordía: una promesa extraída de una conversación cuyo
        // plazo (dueAt) ya pasó y sigue PENDING (sin convertir ni descartar). No es
        // una tarea vencida —no se puede reprogramar, solo convertir o descartar—,
        // así que vive aparte en [CommitmentRules]. Se calcula aquí, fuente única,
        // para que el asistente no mienta por omisión en "¿qué olvidé?"/"vencidas".
        val overdueCommitments = CommitmentRules.overduePendingSorted(commitments, now)
        return when {
            "organiza mi dia" in query || "organizar mi dia" in query || "organiza el dia" in query -> {
                val pending = if (active.size == 1) "1 tarea pendiente" else "${active.size} tareas pendientes"
                val venc = if (overdue.size == 1) "1 vencida" else "${overdue.size} vencidas"
                // Coherencia con "¿qué olvidé?"/"vencidas": el asistente conoce los
                // compromisos vencidos (4.º olvido, c.286) pero aquí —la superficie de
                // planificación, justo donde más importa saberlo antes de decidir el
                // plan— los silenciaba. Se anexan como cola informativa; la acción
                // primaria sigue siendo OPEN_PLANNER (no doble señalización: la promesa
                // no se convierte a ciegas, se recuerda para que el usuario decida).
                AssistantAnswer(
                    "Hay $pending y $venc. Puedo preparar un plan realista y reversible.${overdueCommitmentTail(overdueCommitments)}",
                    AssistantAction.OPEN_PLANNER
                )
            }
            isDayLoadQuery(query) -> dayLoadAnswer(tasks, overdue, overdueCommitments, now, zone, profile)
            "que hago ahora" in query || "siguiente accion" in query -> {
                val suggestion = WhatNowEngine.suggest(active, now)
                if (suggestion == null) {
                    // Quinto olvido de Ordía (c.357): sin tareas pendientes PERO con
                    // un compromiso vencido de una conversación. Antes decía "Puedes
                    // capturar algo nuevo o descansar." — "descansar" frente a una
                    // promesa olvidada es la mentira por omisión MÁS severa, en la
                    // superficie de mayor tráfico. Se rutea a overdueCommitmentAnswer
                    // (lo nombra + OPEN_CONVERSATIONS), igual que "¿qué olvidé?"/
                    // agenda "hoy" sin tareas (c.286 l.190 / c.356 l.416). Sin nueva
                    // pantalla: reutiliza la acción existente.
                    if (overdueCommitments.isNotEmpty()) return overdueCommitmentAnswer(overdueCommitments)
                    AssistantAnswer("No encuentro tareas pendientes. Puedes capturar algo nuevo o descansar.")
                } else {
                    val why = WhatNowEngine.reasonLabel(suggestion.reason)
                    // "Además, tienes N vencidas" se refiere a las vencidas DISTINTAS a la
                    // sugerida: si la propia tarea sugerida está vencida, ya lo dijimos en
                    // "está vencida" — repetir "además tienes 1 vencida" cuando es esa misma
                    // tarea confunde al usuario (¿otra? ¿cuál?).
                    val otherOverdue = overdue.count { it.id != suggestion.task.id }
                    val overdueTail = if (otherOverdue > 0) {
                        " Además, tienes $otherOverdue vencid${if (otherOverdue == 1) "a" else "as"}."
                    } else ""
                    // Recuperación de olvidos en la superficie de mayor tráfico: si la
                    // sugerida NO es el propio inicio olvidado, nombramos el missed-
                    // start más urgente (mismo orden que What Now) para que un
                    // compromiso cuyo hueco pasó no quede oculto detrás de otra tarea
                    // más prioritaria pero menos "olvidada". Simétrico con el tail de
                    // vencidas y con "¿qué olvidé?" (c.203): la sugerida ya lo explica
                    // vía su reason ("tenía su hueco y se pasó"), así se excluye para no
                    // repetir. Sin nueva pantalla: la cola vive en la respuesta que el
                    // usuario ya pidió. Determinista y local (sin IA fingida).
                    val missedTail = missedStartTail(active, suggestion.task, now)
                    // Quinto olvido (c.357): la cola de What Now silenciaba los
                    // compromisos vencidos de conversaciones — la misma mentira por
                    // omisión que c.356 corrigió en agenda "hoy" y c.354 en dayLoad.
                    // Se anexa como cola informativa (no doble señalización: la
                    // promesa no se convierte a ciegas, se recuerda para que el
                    // usuario decida). Paridad con "organiza mi día"/"¿voy bien?".
                    val tail = overdueTail + missedTail + overdueCommitmentTail(overdueCommitments)
                    // "Empieza por" miente si lo sugerido ya está en curso
                    // ([WhatNowReason.IN_PROGRESS_NOW]): hay que continuar, no empezar.
                    // Y el tiempo honesto es lo que FALTA, no la duración planificada
                    // completa: [TaskRules.remainingPlanMinutes] descuenta el tramo ya
                    // vivido desde `startAt` cuando la ventana está activa. Sólo lo
                    // afirmamos así cuando [isInProgressNow] confirma elapsed conocido;
                    // una tarea marcada en curso a mano (sin `startAt`) conserva "Estimo"
                    // porque no sabemos cuánto se ha trabajado —no se simulan datos.
                    val inProgress = suggestion.reason == WhatNowReason.IN_PROGRESS_NOW
                    val elapsedKnown = TaskRules.isInProgressNow(suggestion.task, now)
                    val minutes = if (elapsedKnown) TaskRules.remainingPlanMinutes(suggestion.task, now)
                        else TaskRules.plannedDuration(suggestion.task)
                    val lead = if (inProgress) "Sigue con" else "Empieza por"
                    val timePhrase = if (elapsedKnown) "Te quedan $minutes minutos" else "Estimo $minutes minutos"
                    AssistantAnswer(
                        "$lead “${suggestion.task.title}”: $why. $timePhrase.$tail",
                        relatedTaskIds = listOf(suggestion.task.id)
                    )
                }
            }
            isAgendaQuery(query) -> agendaAnswer(query, active, overdueCommitments, now, zone)
            "que olvide" in query || "olvidado" in query || "vencid" in query -> {
                // Partición honesta: "vencid" pregunta por vencidas (dueAt pasado);
                // "qué olvidé"/"olvidado" pregunta por olvidos, y un compromiso
                // agendado cuyo hueco pasó (TaskRules.isMissedStart — el "olvido
                // silencioso") ES un olvido aunque el plazo aún no vuele. Antes esto
                // decía "No tienes tareas vencidas" frente a una llamada agendada que
                // se pasó: mentía por omisión en la superficie de recuperación. Cierra
                // la simetría con What Now (c.203) y el guardián (c.201), reusando
                // WhatNowEngine.ordered para elegir el olvido más urgente.
                val forgottenIntent = "que olvide" in query || "olvidado" in query
                if (overdue.isNotEmpty()) {
                    if (forgottenIntent) {
                        // "¿Qué olvidé?" pide recuperar QUÉ se pasó, no un conteo frío.
                        // Nombramos la vencida más urgente (mismo orden que What Now:
                        // overdue primero, luego prioridad/fecha) y dejamos el resto
                        // para reprogramar. Simétrico con la rama sin-vencidas, que
                        // nombra el missed-start en lugar de decir "no hay vencidas".
                        val top = WhatNowEngine.ordered(active, now).first { TaskRules.isOverdue(it, now) }
                        val minutes = TaskRules.plannedDuration(top)
                        val tail = if (overdue.size == 1) {
                            "Puedo reprogramarla."
                        } else {
                            "y tienes ${overdue.size - 1} más. Puedo reprogramarlas."
                        }
                        AssistantAnswer(
                            "“${top.title}” está vencida (~$minutes min) $tail" +
                                missedStartTail(active, top, now) +
                                overdueCommitmentTail(overdueCommitments),
                            AssistantAction.RUN_REPLAN,
                            relatedTaskIds = overdue.take(8).map { it.id }
                        )
                    } else {
                        val venc = if (overdue.size == 1) "1 tarea vencida" else "${overdue.size} tareas vencidas"
                        AssistantAnswer(
                            "Tienes $venc. Puedo reprogramarlas sin mostrarte una pared de alertas." +
                                overdueCommitmentTail(overdueCommitments),
                            AssistantAction.RUN_REPLAN,
                            relatedTaskIds = overdue.take(8).map { it.id }
                        )
                    }
                } else {
                    val missed = WhatNowEngine.ordered(active, now)
                        .firstOrNull { TaskRules.isMissedStart(it, now) }
                    if (missed == null) {
                        // Tercer olvido de Ordía: una captura arrinconada en la
                        // bandeja SIN fecha (dueAt/startAt) que lleva
                        // [TaskRules.STALE_INBOX_DAYS_THRESHOLD] o más días
                        // esperando. El guardián ya la reencuadraba (RECUPERA EL
                        // CONTROL, c.201) pero "¿qué olvidé?" la ignoraba y decía
                        // "no hay vencidas ni olvidadas" frente a una idea olvidada
                        // — mentía por omisión en la superficie de recuperación
                        // explícita. Partición honesta: vencida (dueAt) → missed-
                        // start (startAt); aquí sólo capturas SIN ambos, así no se
                        // duplica ni se simula urgencia. La más olvidada = la más
                        // antigua. Sólo para "qué olvidé", no para "vencidas".
                        if (forgottenIntent) {
                            val stale = active
                                .filter { TaskRules.isStaleInbox(it, now, zone) }
                                .maxByOrNull { TaskRules.inboxAgeDays(it, now, zone) }
                            if (stale != null) {
                                val ageLabel = DateRules.ageLabel(TaskRules.inboxAgeDays(stale, now, zone))
                                AssistantAnswer(
                                    "«${stale.title}» lleva $ageLabel en tu bandeja sin fecha. Hazla hoy, agéndala o quítala: no la dejes pasar otra vez." +
                                        overdueCommitmentTail(overdueCommitments),
                                    relatedTaskIds = listOf(stale.id)
                                )
                            } else {
                                // Sin olvidos de tarea pero con una promesa vencida:
                                // cuarto olvido. No decimos "nada olvidado" frente a
                                // un compromiso vencido —mentiría por omisión.
                                if (overdueCommitments.isNotEmpty()) overdueCommitmentAnswer(overdueCommitments)
                                else AssistantAnswer("No tienes tareas vencidas ni compromisos olvidados.")
                            }
                        } else {
                            if (overdueCommitments.isNotEmpty()) overdueCommitmentAnswer(overdueCommitments)
                            else AssistantAnswer("No tienes tareas vencidas.")
                        }
                    } else if (forgottenIntent) {
                        val minutes = TaskRules.plannedDuration(missed)
                        // Simetría con la rama de vencidas (l.93-110): un olvido
                        // reagendable debe ofrecer la MISMA acción (RUN_REPLAN →
                        // replanDay hoy) que una vencida, no dejar al usuario
                        // reagendando a mano. replanDay construye el plan de hoy y,
                        // desde c.246, DayPlanner recupera missed-start en ese plan,
                        // así que el camino de recuperación ya existía — faltaba
                        // exponerlo aquí. "Puedo reagendarla" (yo le doy un hueco
                        // nuevo) es la forma honesta y paralela a "Puedo
                        // reprogramarla" para vencidas: el plazo no voló, lo que se
                        // pasó fue el inicio, así que "reagendar" capta mejor "darle
                        // un nuevo hueco" que "reprogramar" (mover el plazo).
                        AssistantAnswer(
                            "«${missed.title}» tenía su hueco y se pasó (~$minutes min). Puedo reagendarla." +
                                overdueCommitmentTail(overdueCommitments),
                            AssistantAction.RUN_REPLAN,
                            relatedTaskIds = listOf(missed.id)
                        )
                    } else {
                        // "vencidas" con un missed-start (no overdue) y, además,
                        // quizá una promesa vencida (que SÍ es vencida). No decimos
                        // "no tienes vencidas" si hay un compromiso vencido.
                        if (overdueCommitments.isNotEmpty()) overdueCommitmentAnswer(overdueCommitments)
                        else AssistantAnswer("No tienes tareas vencidas.")
                    }
                }
            }
            "resume" in query && ("conversacion" in query || "mensaje" in query) -> {
                val overdueSuffix = when {
                    overdueCommitments.isEmpty() -> ""
                    overdueCommitments.size == 1 -> " (1 vencido)"
                    else -> " (${overdueCommitments.size} vencidos)"
                }
                AssistantAnswer(
                    "Hay ${conversations.size} conversaciones guardadas y ${pendingCommitments.size} compromisos por revisar$overdueSuffix.",
                    AssistantAction.OPEN_CONVERSATIONS
                )
            }
            "compromiso" in query && ("sin fecha" in query || "pendiente" in query) -> {
                val undated = pendingCommitments.filter { it.dueAt == null }
                AssistantAnswer(
                    "Encontré ${undated.size} compromisos pendientes sin fecha.",
                    AssistantAction.OPEN_CONVERSATIONS
                )
            }
            "plan minimo" in query || "minimo para hoy" in query -> {
                val minimal = WhatNowEngine.ordered(active, now).take(3)
                AssistantAnswer(
                    if (minimal.isEmpty()) "Tu plan mínimo está vacío." else "Plan mínimo: " + minimal.joinToString(" · ") { it.title },
                    relatedTaskIds = minimal.map { it.id }
                )
            }
            "15 minutos" in query || "rapido" in query -> {
                val quick = WhatNowEngine.ordered(active, now).filter { it.durationMinutes <= 15 }.take(6)
                AssistantAnswer(
                    if (quick.isEmpty()) "No encuentro tareas de 15 minutos o menos." else "Puedes completar: " + quick.joinToString(" · ") { it.title },
                    relatedTaskIds = quick.map { it.id }
                )
            }
            query.startsWith("convierte esto en una nota") || query.startsWith("guardar como nota") -> {
                val content = clean.substringAfter(":", "").trim()
                if (content.isBlank()) AssistantAnswer("Añade el contenido después de dos puntos para crear la nota.")
                else AssistantAnswer("La nota está lista para guardarse: “${content.take(120)}”.", AssistantAction.CREATE_NOTE, content)
            }
            query.startsWith("busca ") || query.startsWith("muestra ") || query.startsWith("pendientes con") ->
                AssistantAnswer("Abriré la búsqueda con esa consulta.", AssistantAction.OPEN_SEARCH, clean)
            else -> AssistantAnswer(
                "Puedo organizar tu día, decirte qué hacer ahora, qué tienes mañana, mostrar lo vencido, resumir conversaciones, buscar pendientes o preparar un plan mínimo."
            )
        }
    }

    /**
     * "¿Qué tengo mañana/hoy?" — agenda a demanda. El usuario pregunta qué tiene
     * agendado para un día concreto; antes caía al mensaje genérico (debía abrir el
     * planificador y filtrar a mano). Lista las tareas raíz activas cuyo vencimiento
     * cae en ese día (reusa `TaskRules.isDueOn`/`isDueToday`, fuente única de verdad
     * compartida con la búsqueda y el planificador), ordenadas por urgencia/fecha
     * (mismo `WhatNowEngine.ordered`). Sin nueva pantalla: la superficie del
     * asistente ya existe. Determinista y local (sin IA fingida).
     */
    private fun isAgendaQuery(query: String): Boolean {
        if (!("que tengo" in query || "tengo para" in query || "que hay" in query)) return false
        // Día de la semana suelto ("¿qué tengo el viernes?"): antes no se reconocía
        // como agenda y la consulta caía al mensaje genérico — el asistente callaba
        // la agenda de un día concreto pese a preguntarla. Simétrico con
        // SearchEngine.WEEKDAY_TOKENS y el parser de captura. Resolución
        // inclusiva/estricta en agendaAnswer, no aquí.
        // Fin de semana ("finde"/"fin de semana"): simétrico con
        // SearchEngine.isWeekendQuery. Va ANTES que "semana" para que la palabra
        // "semana" de "fin de semana" NO caiga al scope de semana completa.
        // Parte del día ("esta tarde"/"esta noche"/"la madrugada"): simétrico con
        // SearchEngine (DateScope.TARDE/NOCHE/MADRUGADA). Antes "¿qué tengo esta
        // noche?" caía al mensaje genérico: el asistente callaba la agenda vespertina
        // /nocturna pese a preguntarla. La franja horaria se resuelve en agendaAnswer.
        return "manana" in query || "hoy" in query || isAgendaWeekendQuery(query) ||
            "semana" in query || "mes" in query ||
            AGENDA_WEEKDAY_TOKENS.any { it in query } ||
            agendaPartOfDay(query) != null
    }

    private fun agendaAnswer(
        query: String,
        active: List<TaskEntity>,
        overdueCommitments: List<CommitmentEntity>,
        now: Long,
        zone: ZoneId
    ): AssistantAnswer {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val thisMonth = java.time.YearMonth.from(today)
        // Detección simétrica con SearchEngine (NEXT_WEEK_TOKENS/LAST_WEEK_TOKENS/
        // NEXT_MONTH_TOKENS/LAST_MONTH_TOKENS): "próxima semana"/"semana que viene"
        // → semana siguiente; "semana pasada"/"última semana" → semana anterior;
        // "próximo mes"/"mes que viene" → mes siguiente; "mes pasado"/"último mes"
        // → mes anterior. Antes TODO lo que no era "mañana"/"hoy" caía a "esta
        // semana" (lun..dom de hoy) y "mes" ni siquiera se reconocía como agenda:
        // el usuario preguntaba "¿qué tengo el próximo mes?" y recibía los
        // compromisos de esta semana (o ayuda genérica) — mentía sobre la agenda y
        // el usuario podía olvidar lo que vino a planificar. La consulta ya viene
        // normalizada por foldForSearch (sin acentos), por eso "proxima"/"ultima".
        val isNextWeek = "proxima" in query || "proximas" in query || "viene" in query && "semana" in query
        val isLastWeek = "semana" in query && ("pasada" in query || "pasadas" in query ||
            "ultima" in query || "ultimas" in query)
        val isNextMonth = "mes" in query && ("proximo" in query || "proximos" in query ||
            "proxima" in query || "proximas" in query || "viene" in query)
        val isLastMonth = "mes" in query && ("pasada" in query || "pasadas" in query ||
            "pasado" in query || "pasados" in query ||
            "ultima" in query || "ultimas" in query || "ultimo" in query || "ultimos" in query)
        val weekdayTarget = resolveAgendaWeekday(query, today)
        val (start, end, label) = when {
            "pasado manana" in query -> Triple(today.plusDays(2), today.plusDays(2), "pasado mañana")
            "manana" in query -> Triple(today.plusDays(1), today.plusDays(1), "mañana")
            // Parte del día ("esta tarde"/"esta noche"/"la madrugada"): franja de
            // HOY. Va después de "mañana" (para que "mañana en la noche" resuelva a
            // mañana + franja nocturna, no a hoy) y ANTES de "hoy" (para que
            // "hoy en la tarde" muestre la etiqueta "esta tarde", no "hoy"). Se
            // segmenta con !hasAgendaDateScope para no robar "el viernes en la noche"
            // (→ viernes) ni "el finde en la noche" (→ finde): en esos casos la
            // franja se aplica como modificador encima de la fecha ganadora. La
            // franja horaria (band) se calcula tras el when y filtra `due`.
            agendaPartOfDay(query) != null && !hasAgendaDateScope(query) ->
                Triple(today, today, agendaPartOfDayLabel(query))
            "hoy" in query -> Triple(today, today, "hoy")
            isNextWeek -> Triple(monday.plusDays(7), monday.plusDays(13), "próxima semana")
            isLastWeek -> Triple(monday.minusDays(7), monday.minusDays(1), "semana pasada")
            isNextMonth -> {
                val nm = thisMonth.plusMonths(1)
                Triple(nm.atDay(1), nm.atEndOfMonth(), "próximo mes")
            }
            isLastMonth -> {
                val pm = thisMonth.minusMonths(1)
                Triple(pm.atDay(1), pm.atEndOfMonth(), "mes pasado")
            }
            // Fin de semana ("¿qué tengo el finde?"/"¿qué tengo el fin de
            // semana?"): sábado+domingo del PRÓXIMO finde, SIEMPRE estricto (si hoy
            // es sábado salta al siguiente), idéntico a SearchEngine.resolveWeekendTarget
            // y al parser de captura (weekendPattern → nextWeekday(SATURDAY)). Va
            // ANTES que "semana"/weekday para que "fin de semana" no caiga a
            // "esta semana" (lun..dom) ni "sábado"/"domingo" solos al weekday.
            // Así preguntar, buscar y capturar signifiquen lo mismo al decir "finde".
            isAgendaWeekendQuery(query) -> {
                val saturday = resolveAgendaWeekend(today)
                Triple(saturday, saturday.plusDays(1), "el finde")
            }
            // Día de la semana ("¿qué tengo el viernes?"/"¿qué tengo el próximo
            // lunes?"). Resolución simétrica con SearchEngine.resolveWeekdayTarget
            // y el parser de captura: inclusiva (incluye hoy si hoy es ese día) salvo
            // con modificador "próximo"/"que viene"/"siguiente" → estricta (salta al
            // siguiente). Así buscar y preguntar signifiquen lo mismo. Si el día
            // resuelto ES hoy (caso inclusivo), se reusa la etiqueta "hoy" para que
            // aplique la rama de atrasadas previas (la misma honestidad que
            // "¿qué tengo hoy?": no callar lo atrasado de días anteriores).
            weekdayTarget != null -> {
                if (weekdayTarget == today) Triple(today, today, "hoy")
                else Triple(weekdayTarget, weekdayTarget, agendaWeekdayLabel(query))
            }
            "semana" in query -> Triple(monday, monday.plusDays(6), "esta semana")
            "mes" in query -> Triple(thisMonth.atDay(1), thisMonth.atEndOfMonth(), "este mes")
            else -> Triple(monday, monday.plusDays(6), "esta semana")
        }
        val ranked = WhatNowEngine.ordered(active, now)
        // Franja horaria (parte del día) como modificador opcional encima del
        // rango de fechas: simétrico con SearchEngine.scopeBand (MADRUGADA 0..5,
        // TARDE 12..17, NOCHE 18..23). Si la consulta menciona una parte del día,
        // se filtra además por hora local. Así "¿qué tengo esta noche?" = tareas de
        // hoy con hora 18-23; "¿qué tengo el viernes en la noche?" = viernes 18-23.
        // Las tareas sin hora concreta (dueAt a medianoche, hora 0) sólo casan con
        // madrugada — igual que en SearchEngine: una tarea "solo fecha" no puede
        // afirmar honestamente pertenecer a la tarde/noche.
        val band = agendaPartOfDay(query)
        val due = ranked.filter { isDueInRange(it, start, end, zone) && (band == null || isDueInHourBand(it, band, zone)) }
        if (due.isEmpty()) {
            // "¿Qué tengo hoy?" no debe decir "no tienes nada" mientras el usuario
            // arrastra atrasadas de días anteriores: eso es exactamente lo que tiene
            // que hacer hoy. Si no hay nada vencido HOY pero sí atrasadas, las
            // nombramos (la más urgente + recuento) en vez de mentir "agenda vacía".
            // Para los demás alcances (mañana/semana/mes) sí cabe el "no tienes".
            if (label == "hoy") {
                val earlierOverdue = ranked.filter { TaskRules.isOverdue(it, now) && !TaskRules.isDueToday(it, now, zone) }
                if (earlierOverdue.isNotEmpty()) {
                    val top = earlierOverdue.first()
                    val tail = if (earlierOverdue.size == 1) "" else " y tienes ${earlierOverdue.size - 1} más atrasad${if (earlierOverdue.size - 1 == 1) "a" else "as"}."
                    return AssistantAnswer(
                        "Para hoy no tienes tareas agendadas, pero tienes ${earlierOverdue.size} atrasad${if (earlierOverdue.size == 1) "a" else "as"} de días anteriores: “${top.title}”$tail" +
                            overdueCommitmentTail(overdueCommitments),
                        relatedTaskIds = earlierOverdue.take(8).map { it.id }
                    )
                }
                // Cuarto olvido: agenda de hoy vacía y SIN atrasadas de tarea, PERO
                // con un compromiso vencido de una conversación. Antes decía "Para
                // hoy no tienes tareas agendadas." frente a una promesa vencida —
                // mentía por omisión en la superficie de agenda más común. Lo
                // nombramos (no callamos), igual que "¿qué olvidé?" sin atrasadas
                // (c.286 l.190): la promesa vencida es parte de "lo que tienes
                // pendiente hoy". La cola no basta aquí: no hay nada más que nombrar,
                // así se ruta a overdueCommitmentAnswer para identificarlo y abrir
                // Conversaciones. Sin nueva pantalla (acción existente).
                if (overdueCommitments.isNotEmpty()) return overdueCommitmentAnswer(overdueCommitments)
            }
            return AssistantAnswer("Para $label no tienes tareas agendadas.")
        }
        val titles = due.joinToString(" · ") { "“${it.title}”" }
        val ids = due.take(8).map { it.id }
        // Para "hoy", avisar además de las atrasadas de días anteriores (vencidas
        // antes de hoy): son parte de "lo que tienes" pendiente y el usuario las
        // olvidaría si la agenda sólo mirara el día de hoy. Coherente con el "además"
        // de "qué hago ahora". Para mañana/semana/mes (futuro/pasado) no aplica.
        // Lo mismo con los compromisos vencidos de conversaciones (cuarto olvido,
        // c.356): el alcance "hoy" incluye lo que se pasó y necesita acción hoy,
        // aunque la promesa aún no sea tarea. Simétrico con "¿voy bien?" (c.354) y
        // "organiza mi día" (c.294): cola informativa, no doble señalización. Para
        // alcances futuros/pasados no se anexa (no son parte de ese día).
        val tail = if (label == "hoy") {
            val earlierOverdue = active.count { TaskRules.isOverdue(it, now) && !TaskRules.isDueToday(it, now, zone) }
            val overdueTaskTail = if (earlierOverdue > 0) {
                " Además, tienes $earlierOverdue atrasad${if (earlierOverdue == 1) "a" else "as"} de días anteriores."
            } else ""
            overdueTaskTail + overdueCommitmentTail(overdueCommitments)
        } else ""
        val head = if (label == "hoy") "Hoy" else label.replaceFirstChar { it.uppercase() }
        return AssistantAnswer("$head: $titles.$tail", relatedTaskIds = ids)
    }

    private fun isDueInRange(task: TaskEntity, start: LocalDate, end: LocalDate, zone: ZoneId): Boolean {
        val due = task.dueAt ?: return false
        val d = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
        return d >= start && d <= end
    }

    // Franja horaria (parte del día) de la agenda. Simétrico con
    // SearchEngine.scopeBand: MADRUGADA 0..5, TARDE 12..17, NOCHE 18..23. Tokens sin
    // acento (foldForSearch). Devuelve null si la consulta no menciona una parte del
    // día. NOTA: no hay franja "mañana/mañana" (morning): "mañana" significa
    // "tomorrow" (igual que en SearchEngine, que no tiene scope de mañana-mañana).
    private fun agendaPartOfDay(query: String): IntRange? = when {
        "madrugada" in query -> 0..5
        "tarde" in query -> 12..17
        "noche" in query -> 18..23
        else -> null
    }

    private fun agendaPartOfDayLabel(query: String): String = when {
        "madrugada" in query -> "esta madrugada"
        "tarde" in query -> "esta tarde"
        "noche" in query -> "esta noche"
        else -> "hoy"
    }

    private fun isDueInHourBand(task: TaskEntity, band: IntRange, zone: ZoneId): Boolean {
        val due = task.dueAt ?: return false
        return Instant.ofEpochMilli(due).atZone(zone).hour in band
    }

    // ¿La consulta menciona otro alcance de fecha (día relativo, semana, mes, weekday
    // o finde)? Sirve para segmentar la rama de parte del día: si hay otro alcance,
    // la parte del día actúa como modificador (band) sobre la fecha ganadora, no
    // como la fecha misma. "hoy" no cuenta aquí (es compatible con "hoy en la tarde"
    // → "esta tarde").
    private fun hasAgendaDateScope(query: String): Boolean =
        "manana" in query || "pasado manana" in query || "semana" in query ||
            "mes" in query || isAgendaWeekendQuery(query) ||
            AGENDA_WEEKDAY_TOKENS.any { it in query }

    // Días de la semana para la agenda a demanda ("¿qué tengo el viernes?"). Tokens
    // sin acento (foldForSearch): miércoles→miercoles, sábado→sabado. Mapa a
    // DayOfWeek ISO (lun=1..dom=7). Simétrico con SearchEngine.WEEKDAY_TOKENS y el
    // parser de captura, para que preguntar, buscar y capturar signifiquen lo mismo.
    private val AGENDA_WEEKDAY_TOKENS = setOf("lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo")
    private val AGENDA_WEEKDAY_BY_TOKEN = mapOf(
        "lunes" to java.time.DayOfWeek.MONDAY,
        "martes" to java.time.DayOfWeek.TUESDAY,
        "miercoles" to java.time.DayOfWeek.WEDNESDAY,
        "jueves" to java.time.DayOfWeek.THURSDAY,
        "viernes" to java.time.DayOfWeek.FRIDAY,
        "sabado" to java.time.DayOfWeek.SATURDAY,
        "domingo" to java.time.DayOfWeek.SUNDAY
    )
    // Modificador "próximo"/"que viene"/"siguiente"/"posterior" → estricto (salta al
    // siguiente, excluye hoy). Coincide con SearchEngine.WEEKDAY_NEXT_MODIFIERS.
    private val AGENDA_WEEKDAY_NEXT_MODIFIERS = setOf("proximo", "proximos", "proxima", "proximas", "viene", "siguiente", "siguientes", "posterior", "posteriores")

    /**
     * ¿La consulta pregunta por el fin de semana? "finde" (apócope coloquial) o
     * "fin"+"semana" ("fin de semana"). Simétrico con [SearchEngine.isWeekendQuery]
     * y el parser de captura (weekendPattern). La consulta ya viene normalizada
     * por foldForSearch (sin acentos), por eso "finde" suelto también casa.
     */
    private fun isAgendaWeekendQuery(query: String): Boolean =
        "finde" in query || ("fin" in query && "semana" in query)

    /**
     * Resuelve el sábado del PRÓXIMO fin de semana: SIEMPRE estricto (si hoy es
     * sábado, salta al siguiente), idéntico a [SearchEngine.resolveWeekendTarget]
     * y al parser de captura (weekendPattern → nextWeekday(SATURDAY)). El domingo
     * objetivo es el día siguiente. Así "finde"/"fin de semana"/"este finde"/
     * "próximo finde" resuelven todos al mismo fin de semana (el parser tampoco
     * distingue "este" de "próximo" para el finde): preguntar, buscar y capturar
     * signifiquen lo mismo al decir "finde".
     */
    private fun resolveAgendaWeekend(today: LocalDate): LocalDate {
        val delta = (java.time.DayOfWeek.SATURDAY.value - today.dayOfWeek.value + 7) % 7
        val days = if (delta == 0) 7L else delta.toLong()
        return today.plusDays(days)
    }

    /**
     * Resuelve el día calendario objetivo de un weekday en la consulta de agenda,
     * con la MISMA semántica que [SearchEngine.resolveWeekdayTarget] y el parser de
     * captura: inclusivo (incluye hoy si hoy es ese día) salvo con modificador
     * "próximo"/"que viene"/"siguiente" → estricto (salta al siguiente). Devuelve
     * null si la consulta no menciona un día de la semana.
     */
    private fun resolveAgendaWeekday(query: String, today: LocalDate): LocalDate? {
        val token = AGENDA_WEEKDAY_TOKENS.firstOrNull { it in query } ?: return null
        val target = AGENDA_WEEKDAY_BY_TOKEN[token] ?: return null
        val strict = AGENDA_WEEKDAY_NEXT_MODIFIERS.any { it in query }
        val delta = (target.value - today.dayOfWeek.value + 7) % 7
        val days = if (strict) (if (delta == 0) 7 else delta).toLong() else delta.toLong()
        return today.plusDays(days)
    }

    /** Etiqueta legible del weekday de la agenda, conservando "próximo" si lo hubo. */
    private fun agendaWeekdayLabel(query: String): String {
        val token = AGENDA_WEEKDAY_TOKENS.firstOrNull { it in query } ?: return "ese día"
        val pretty = when (token) {
            "miercoles" -> "miércoles"
            "sabado" -> "sábado"
            else -> token
        }
        return if (AGENDA_WEEKDAY_NEXT_MODIFIERS.any { it in query }) "próximo $pretty" else pretty
    }

    /**
     * "¿Voy bien?" / "¿Da tiempo a todo?" / "¿Tengo mucho que hacer?" — el
     * veredicto del día a demanda. Ordía YA calcula si el trabajo restante cabe
     * en la jornada ([SummaryEngine.dayLoad]: LIGHT/ON_TRACK/FULL/OVERLOADED) y,
     * cuando no cabe, nombra la tarea de hoy más posponible
     * ([SummaryEngine.deferralSuggestion]). Pero esa inteligencia sólo vivía en
     * la tarjeta de resumen: preguntarlo al asistente caía al mensaje genérico y
     * el usuario debía abrir Hoy y leer la tarjeta. Aquí se expone en la
     * superficie a demanda, reusando el MISMO motor (fuente única de verdad) de
     * forma que asistente y tarjeta nunca discrepen sobre "¿da tiempo?". Sin
     * nueva pantalla/botón, sin IA fingida (veredicto determinista local).
     *
     * Bajo OVERLOADED el valor real no es decir "estás saturado" (el usuario lo
     * sabe) sino nombrar QUÉ mover a mañana: convierte la ansiedad de una agenda
     * que no cabe en una decisión concreta. La sugerencia es texto + id (el
     * usuario decide moverla), nunca un auto-movimiento —coherente con el
     * diseño de [SummaryEngine.deferralSuggestion] ("no mueve nada, solo nombra").
     *
     * [profile] (opcional) reproduce la ventana de jornada aprendida del usuario
     * para que el veredicto coincida con la tarjeta de Hoy (que pasa el perfil
     * cuando el aprendizaje está activo). Por defecto null → ventana 9–18;
     * coherente con [SummaryEngine.summarize].
     */
    private fun isDayLoadQuery(query: String): Boolean =
        "voy bien" in query || "voy mal" in query ||
            "da tiempo" in query || "me da tiempo" in query ||
            "tengo mucho que hacer" in query ||
            "cabe todo" in query || "cabe el dia" in query || "cabe hoy" in query ||
            "alcanzara" in query || "alcanzare" in query || "da alcance" in query ||
            "estoy saturad" in query

    private fun dayLoadAnswer(
        tasks: List<TaskEntity>,
        overdue: List<TaskEntity>,
        overdueCommitments: List<CommitmentEntity>,
        now: Long,
        zone: ZoneId,
        profile: LearningProfile?
    ): AssistantAnswer {
        val summary = SummaryEngine.summarize(tasks, now, zone, profile)
        // El veredicto de carga NUNCA calla los olvidos: "¿voy bien?" es la
        // superficie que más se pregunta justo cuando el riesgo de olvidar
        // vencidas es mayor. Antes era la outlier del asistente — las demás
        // ramas ("organiza mi día", "qué hago ahora", "qué olvidé") anexaban
        // colas de vencidas/compromisos, pero dayLoad silenciaba ambas, así un
        // usuario con 3 vencidas cuya carga "cabe" leía "Vas bien con holgura"
        // sin saber que tenía vencidas acumuladas (mentira por omisión). La cola
        // es informativa: la acción primaria sigue siendo el veredicto/deferral.
        val tail = overdueCountTail(overdue) + overdueCommitmentTail(overdueCommitments)
        return when (summary.dayLoad) {
            DayLoad.LIGHT ->
                AssistantAnswer("Tu día está despejado.$tail")
            DayLoad.ON_TRACK ->
                AssistantAnswer("Vas bien: lo que queda cabe con holgura en la jornada.$tail")
            DayLoad.FULL ->
                AssistantAnswer("El día está lleno: cabe, pero justo. Cuida los huecos.$tail")
            DayLoad.OVERLOADED -> {
                val sug = summary.deferralSuggestion
                if (sug != null) {
                    AssistantAnswer(
                        "No da tiempo a todo hoy. «${sug.title}» es la candidata a mover a mañana.$tail",
                        relatedTaskIds = listOf(sug.taskId)
                    )
                } else {
                    AssistantAnswer(
                        "No da tiempo a todo hoy. Revisa qué posponer o quitar.$tail",
                        AssistantAction.OPEN_PLANNER
                    )
                }
            }
        }
    }

    /** Cola informativa para no callar las vencidas en "¿voy bien?"/"¿da tiempo?":
     *  el veredicto de carga puede decir "cabe con holgura" sin ocultar que hay
     *  tareas vencidas que necesitan acción (no se simula urgencia, se informa).
     *  Simétrica con [overdueCommitmentTail] y con las colas de "qué hago ahora". */
    private fun overdueCountTail(overdue: List<TaskEntity>): String =
        when {
            overdue.isEmpty() -> ""
            overdue.size == 1 -> " Además, tienes 1 tarea vencida."
            else -> " Además, tienes ${overdue.size} tareas vencidas."
        }

    /**
     * Recuperación autónoma del cuarto olvido —un compromiso vencido de una
     * conversación— cuando NO hay olvidos de tarea que mostrar. Nombra el más
     * atrasado y abre Conversaciones para convertirlo/descartarlo. No inventa
     * acción sobre la promesa: convertirla o descartarla es decisión del
     * usuario, así que el `action` es [AssistantAction.OPEN_CONVERSATIONS]
     * (guiar, no ejecutar a ciegas).
     */
    private fun overdueCommitmentAnswer(overdueCommitments: List<CommitmentEntity>): AssistantAnswer {
        val top = overdueCommitments.first()
        val head = if (overdueCommitments.size == 1) {
            "«${top.action}» es un compromiso vencido que aún no convertiste en tarea."
        } else {
            "«${top.action}» es el más atrasado de ${overdueCommitments.size} compromisos vencidos de conversaciones que aún no convertiste en tarea."
        }
        return AssistantAnswer(
            "$head Convértelo en tarea o descártalo para no dejarlo pasar.",
            AssistantAction.OPEN_CONVERSATIONS
        )
    }

    /** Cola informativa para no callar un compromiso vencido cuando SÍ hay
     *  olvidos de tarea que nombrar (no lo oculta detrás de la tarea). */
    private fun overdueCommitmentTail(overdueCommitments: List<CommitmentEntity>): String =
        when {
            overdueCommitments.isEmpty() -> ""
            overdueCommitments.size == 1 ->
                " Además, tienes 1 compromiso vencido de una conversación por convertir en tarea."
            else ->
                " Además, tienes ${overdueCommitments.size} compromisos vencidos de conversaciones por convertir en tarea."
        }

    /**
     * Cola que recupera el "olvido silencioso" en "¿qué hago ahora?": nombra el
     * inicio olvidado ([TaskRules.isMissedStart]) más urgente DISTINTO a la
     * [suggested] — un compromiso al que el usuario le dio hueco y se le pasó,
     * pero cuyo plazo aún no voló (no es vencida). Antes esta señal sólo vivía en
     * "¿qué olvidé?": si la tarea más prioritaria del momento era otra, el hueco
     * incumplido quedaba oculto en la superficie de mayor tráfico y el usuario no
     * reagendaba. Elige con el MISMO orden que What Now (fuente única) para no
     * discrepar con "¿qué olvidé?" sobre cuál olvidó. Devuelve "" si no hay ninguno
     * o si el único es la propia sugerida (ya explicado por su reason). No añade
     * ids a la respuesta: el usuario ya tiene la sugerida para actuar; la cola
     * avisa, no navega.
     */
    private fun missedStartTail(
        active: List<TaskEntity>,
        suggested: TaskEntity,
        now: Long
    ): String {
        val missed = WhatNowEngine.ordered(active, now)
            .firstOrNull { TaskRules.isMissedStart(it, now) && it.id != suggested.id }
            ?: return ""
        val minutes = TaskRules.plannedDuration(missed)
        return " Además, «${missed.title}» tenía su hueco y se pasó (~$minutes min)."
    }
}
