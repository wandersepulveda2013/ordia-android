package com.ordia.app.assistant

import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.CommitmentReviewStatus
import com.ordia.app.data.local.ConversationEntity
import com.ordia.app.data.local.FocusSessionEntity
import com.ordia.app.data.local.RoutineEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.domain.CommitmentRules
import com.ordia.app.domain.DateRules
import com.ordia.app.domain.DayLoad
import com.ordia.app.domain.FocusRecap
import com.ordia.app.domain.LearningProfile
import com.ordia.app.domain.SummaryEngine
import com.ordia.app.domain.TaskRules
import com.ordia.app.domain.WhatNowEngine
import com.ordia.app.domain.WhatNowReason
import com.ordia.app.domain.foldForSearch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class AssistantAction { NONE, OPEN_PLANNER, OPEN_CONVERSATIONS, RUN_REPLAN, CREATE_NOTE, CREATE_TASK, OPEN_SEARCH }

data class AssistantAnswer(
    val text: String,
    val action: AssistantAction = AssistantAction.NONE,
    val actionPayload: String = "",
    val relatedTaskIds: List<Long> = emptyList()
)

/** Asistente determinista y local; nunca necesita red ni una clave de API. */
object AssistantEngine {
    // Ventana por defecto de las formas sueltas de hueco ("tengo un rato"):
    // la misma del filtro histórico "tareas de 15 minutos o menos".
    private const val QUICK_TASK_WINDOW_MINUTES = 15

    fun answer(
        request: String,
        tasks: List<TaskEntity>,
        conversations: List<ConversationEntity>,
        commitments: List<CommitmentEntity>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        profile: LearningProfile? = null,
        focusSessions: List<FocusSessionEntity> = emptyList(),
        routines: List<RoutineEntity> = emptyList()
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
        val priorityIntent = priorityIntent(query)
        // c.807: interrogativa con contenido cualificado → payload afirmativo
        // equivalente (ver rama de contenido cualificado al final del when).
        val contentQualifiedInterrogativePayload = contentQualifiedInterrogativePayload(clean)
        // c.969: captura «tomar nota» (calculada una vez, fuera del when).
        val takeNoteCapture = takeNoteCapture(clean)
        val remindMeCapture = remindMeCapture(clean)
        val createTaskCapture = createTaskCapture(clean)
        val avisaMeCapture = avisaMeCapture(clean)
        val quieroQueRecuerdesCapture = quieroQueRecuerdesCapture(clean)
        val remindMeLoGuide = remindMeLoGuide(clean)
        // c.991: captura «ponme un recordatorio …» (lateral (e) de la sonda
        // AssistantTaskCreationProbe). Debe evaluarse ANTES de la consulta
        // c.808: «recordatorio» en la query la robaba y respondía la mentira
        // «No tienes recordatorios programados.» a una orden de CREAR.
        val setReminderCapture = setReminderCapture(clean)
        return when {
            isPlannerIntent(query) -> {
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
            // "¿resumen del día?"/"¿cuántas tareas tengo hoy?" — el PANORAMA de
            // hoy a demanda. El asistente ya respondía al veredicto ("¿voy bien?")
            // y a la agenda ("¿qué tengo hoy?"), pero la forma más natural de pedir
            // el resumen (hechas/pendientes/vencidas + cómo va el día) caía al menú
            // genérico. Ordía YA calcula esos conteos en SummaryEngine (fuente única
            // de la tarjeta de Hoy); aquí se exponen a demanda, reusando el MISMO
            // motor para que asistente y tarjeta nunca discrepen. Sin nueva
            // pantalla/botón: sólo entender más frases. Determinista y local.
            isDaySummaryQuery(query) -> daySummaryAnswer(tasks, overdue, overdueCommitments, now, zone, profile)
            // Reconocimiento de intención "¿qué hago ahora?" — la consulta de mayor
            // valor del asistente (la siguiente acción). Antes sólo "qué hago ahora" y
            // "siguiente acción" la activaban: formas cotidianas como "¿qué sigue?",
            // "¿qué me toca?" o el simple "¿qué hago?" caían al mensaje genérico, y el
            // usuario perdía la sugerencia de What Now justo cuando más la necesita.
            // Se anclan en secuencias de 2+ palabras con "qué" para evitar falsos
            // positivos del verbo suelto ("sigue", "toca"): "qué sigue" exige "qué" antes
            // de "sigue", "qué me toca" exige el pronombre. "qué hago" cubre la forma
            // desnuda (es subcadena de "qué hago ahora", así el caso original sigue
            // funcionando). No colisiona con agenda: ésta usa "tengo"/"hay"/"para".
            "que hago ahora" in query || "que hago" in query ||
                "siguiente accion" in query || "que sigue" in query ||
                "que me toca" in query ||
                // Interrogativo "cuál" (sinónimo de "qué" para la siguiente acción) +
                // "¿qué sigo?" + "¿qué viene después?": "¿cuál hago?"/"¿cuál hago
                // primero?"/"¿cuál es la siguiente?" son semánticamente idénticas a
                // "¿qué hago?"/"¿qué sigue?", y "¿qué sigo?"/"¿qué viene después?"
                // piden lo mismo (la siguiente tarea). Antes caían al menú genérico
                // por no contener "hago"/"sigue"/"toca". No colisionan con agenda
                // (ésta usa "tengo"/"hay"/"para"). "cual hago" se ancla con verbo de
                // acción para no robar "¿cuál es el problema?"/"no sé cuál" (ver test
                // whatNow_cualNoSeActivaConVerboSuelto).
                "cual hago" in query || "cual es la siguiente" in query ||
                "que sigo" in query || "que viene despues" in query ||
                // c.798: más formas cotidianas del cluster «¿qué hago ahora?» —
                // el verbo «debo» (la intención de siguiente acción) y la forma
                // orden «qué tarea tengo primero». Guarda `!hasAgendaDateScope`:
                // «qué debo hacer mañana/esta semana/el viernes» miente si se
                // responde con la sugerida de HOY (guarda hermana de «qué tengo
                // que hacer mañana» → agenda, c.554).
                ("que debo hacer" in query && !hasAgendaDateScope(query)) ||
                ("tengo primero" in query && !hasAgendaDateScope(query)) ||
                // "¿qué tengo que hacer?"/"¿qué me falta por hacer?" son las formas
                // más cotidianas de preguntar por la siguiente tarea — semánticamente
                // idénticas a "¿qué hago ahora?"/"¿qué me toca?" — pero, al no
                // contener "hago"/"toca"/"sigue", caían al menú genérico: el usuario
                // preguntaba por su siguiente acción y el asistente respondía con una
                // lista de lo que "puede" hacer en vez de sugerirle cuál. Se rutean a
                // What Now con la MISMA protección que evita robarle la agenda a las
                // variantes con timeframe: "¿qué tengo que hacer mañana?" sigue
                // resolviéndose como agenda (isAgendaQuery se evalúa antes, dentro del
                // &&, y al ser true niega la rama). "que me falta" se incluye porque
                // "¿qué me falta por hacer?" nombra exactamente lo pendiente. No se
                // añaden verbos sueltos (paridad con whatNow_verbsAloneAreNotWhatNow).
                // c.798 (sonda AssistantHonestRouteProbe): se añaden "que debo hacer"
                // (hermano cotidiano de "que hago") y "que tarea tengo primero" —
                // ambas preguntan por la siguiente acción; la agenda no las activa
                // (sin "que tengo"/marcador temporal; la guarda negada las protege
                // igual que las hermanas de c.afb).
                (("que tengo que hacer" in query || "que me falta" in query ||
                    "que debo hacer" in query || "que tarea tengo primero" in query ||
                    // c.801 (sonda extendida): imperativo «dime qué hacer» y
                    // olvido «qué no debo olvidar» piden la siguiente acción;
                    // guardas de scope idénticas a las hermanas de c.798.
                    "dime que hacer" in query || "que no debo olvid" in query ||
                    "que no debemos olvid" in query) &&
                    !isAgendaQuery(query) && !hasAgendaDateScope(query)) -> {
                val suggestion = WhatNowEngine.suggest(active, now, zone)
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
                    val missedTail = missedStartTail(active, suggestion.task, now, zone)
                    // Quinto olvido (c.357): la cola de What Now silenciaba los
                    // compromisos vencidos de conversaciones — la misma mentira por
                    // omisión que c.356 corrigió en agenda "hoy" y c.354 en dayLoad.
                    // Se anexa como cola informativa (no doble señalización: la
                    // promesa no se convierte a ciegas, se recuerda para que el
                    // usuario decida). Paridad con "organiza mi día"/"¿voy bien?".
                    // Tercer olvido (c.411): la misma superficie callaba las capturas
                    // de bandeja arrinconadas ([TaskRules.isStaleInbox], ≥7 días sin
                    // fecha ni hueco). El nudge del guardián ya las nombra como cola en
                    // TODAS sus ramas con acción ([GuardianEngine.withStaleInboxTail],
                    // c.410), pero "¿qué hago ahora?" —la superficie de mayor tráfico—
                    // sólo anexaba vencidas, missed-start y compromisos. Así un usuario
                    // con 6 ideas arrinconadas leía "empieza por X" sin señal alguna de
                    // que las está olvidando: la misma mentira por omisión que c.410
                    // cerró en el nudge. Excluye la tarea sugerida (si la propia
                    // sugerida es la captura olvidada, su reason/posición ya lo explica
                    // y no se cuenta dos veces), igual que overdueTail excluye la
                    // sugerida y missedStartTail la excluye. Determinista y local.
                    val tail = overdueTail + missedTail + staleInboxTail(active, suggestion.task, now, zone) +
                        overdueCommitmentTail(overdueCommitments)
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
                    // Contexto honesto de "¿qué hago ahora?" (c.552): si What Now
                    // detectó una cita cercana ([WhatNowSuggestion.minutesUntilNextCommitment]
                    // ≠ null), el asistente cruza la duración estimada con ese hueco y
                    // decide por el usuario en una sola frase, sin nueva pantalla/botón:
                    // avisa cuando NO cabe ("ojo: tu próxima cita es en ~M min" — empezar
                    // ahora implica que la cita interrumpirá) y confirma cuando el hueco
                    // está JUSTO (la tarea ocupa más de la mitad del hueco → "te alcanza
                    // antes de tu próxima cita"). Si sobra tiempo de sobra, calla: el
                    // silencio es honesto (no hay decisión difícil). Determinista y local
                    // (no IA fingida): compara dos números ya calculados.
                    val gapPhrase = nextCommitmentGapPhrase(
                        suggestion.minutesUntilNextCommitment,
                        minutes,
                        // Alternativa accionable (c.557): si la sugerida NO cabe
                        // antes de la próxima cita PERO existe otra tarea raíz
                        // activa que SÍ cabe (y es arrancable ahora), el asistente
                        // la nombra para convertir el aviso pasivo en una micro-
                        // decisión productiva. Reusa [WhatNowEngine.ordered] (fuente
                        // única) para elegir la mejor que cabe. Null si no hay ninguna
                        // → el aviso de c.552 queda intacto (no se inventa nada).
                        fittingAlternativeBeforeCommitment(active, suggestion.task.id, suggestion.minutesUntilNextCommitment, now, zone)
                    )
                    AssistantAnswer(
                        "$lead “${suggestion.task.title}”: $why. $timePhrase.$gapPhrase$tail",
                        relatedTaskIds = listOf(suggestion.task.id)
                    )
                }
            }
            isAgendaQuery(query) -> agendaAnswer(query, active, overdueCommitments, now, zone)
            // Cluster sonda assistant c.707: "tengo algo pronto" — el usuario
            // pregunta por lo PRÓXIMO agendado sin alcance de fecha concreto.
            // Caía al menú genérico pese a que la respuesta ya existe en los
            // datos (la próxima cita/tarea por su marca futura más cercana:
            // hueco `startAt` o `dueAt`, lo que llegue antes). La agenda va
            // ANTES en el despacho, así "tengo algo pronto hoy/el viernes"
            // (alcance explícito) sigue resolviéndose como agenda. Lo vencido
            // no es "próximo" (lo cubren las ramas de recuperación) y la
            // captura sin fecha tampoco (no se puede ordenar honestamente).
            // Vacío honesto (NUNCA menú); vacío + promesa vencida →
            // recuperación (paridad familia lie-by-omission c.357/c.416/c.680).
            // Determinista y local; cero random/IA fingida/pantalla nueva.
            isUpcomingQuery(query) -> {
                val upcoming = active
                    .mapNotNull { t -> upcomingMarker(t, now)?.let { m -> t to m } }
                    .sortedBy { it.second }
                    .take(3)
                if (upcoming.isEmpty()) {
                    if (overdueCommitments.isNotEmpty()) return overdueCommitmentAnswer(overdueCommitments)
                    AssistantAnswer("No tienes nada agendado próximamente.")
                } else {
                    val parts = upcoming.joinToString(" · ") { (t, marker) ->
                        "«${t.title}» ${upcomingWhenLabel(marker, now, zone)}"
                    }
                    AssistantAnswer(
                        "Lo más próximo: $parts." + overdueCommitmentTail(overdueCommitments),
                        relatedTaskIds = upcoming.map { it.first.id }
                    )
                }
            }
            // ¿qué olvidé? ("que olvide"), "¿qué olvidado?", sinónimos
            // ("atrasad","vencid") — y el modismo del olvido "se me/se nos
            // pasó/pasaron". Frase, NUNCA el token "paso" suelto.
            // «qué tengo pendiente(s) de ayer» (c.809, decisión de producto):
            // lo pendiente DE AYER ya está vencido — es RECUPERACIÓN
            // (hermana de «qué olvidé»), no recap (incluiría lo hecho) ni
            // conteo frío. «pendiente de mañana» NO casa (agenda futura).
            "que olvide" in query || "olvidado" in query || "atrasad" in query || "vencid" in query ||
                "pendiente de ayer" in query || "pendientes de ayer" in query || isMissedSlipQuery(query) -> {
                // Partición honesta: "vencid" pregunta por vencidas (dueAt pasado);
                // "atrasad" es el sinónimo cotidiano de "overdue" en español — la
                // palabra MÁS natural — y pregunta por lo mismo pero la rama sólo
                // reconocía "vencid", así "¿qué tengo atrasado?"/"atrasadas" caía al
                // menú genérico sin recuperar la vencida más urgente. Como
                // "olvidado", "atrasad" es intención de recuperación (nombra la
                // vencida más urgente y ofrece reprogramar), no un conteo frío.
                // "qué olvidé"/"olvidado" pregunta por olvidos, y un compromiso
                // agendado cuyo hueco pasó (TaskRules.isMissedStart — el "olvido
                // silencioso") ES un olvido aunque el plazo aún no vuele. Antes esto
                // decía "No tienes tareas vencidas" frente a una llamada agendada que
                // se pasó: mentía por omisión en la superficie de recuperación. Cierra
                // la simetría con What Now (c.203) y el guardián (c.201), reusando
                // WhatNowEngine.ordered para elegir el olvido más urgente.
                val forgottenIntent = "que olvide" in query || "olvidado" in query || "atrasad" in query ||
                    "pendiente de ayer" in query || "pendientes de ayer" in query || isMissedSlipQuery(query)
                if (overdue.isNotEmpty()) {
                    if (forgottenIntent) {
                        // "¿Qué olvidé?" pide recuperar QUÉ se pasó, no un conteo frío.
                        // Nombramos la vencida más urgente (mismo orden que What Now:
                        // overdue primero, luego prioridad/fecha) y dejamos el resto
                        // para reprogramar. Simétrico con la rama sin-vencidas, que
                        // nombra el missed-start en lugar de decir "no hay vencidas".
                        val top = WhatNowEngine.ordered(active, now, zone).first { TaskRules.isOverdue(it, now) }
                        val minutes = TaskRules.plannedDuration(top)
                        val tail = if (overdue.size == 1) {
                            "Puedo reprogramarla."
                        } else {
                            "y tienes ${overdue.size - 1} más. Puedo reprogramarlas."
                        }
                        AssistantAnswer(
                            "“${top.title}” está vencida (~$minutes min) $tail" +
                                missedStartTail(active, top, now, zone) +
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
                    val missed = WhatNowEngine.ordered(active, now, zone)
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
                val minimal = WhatNowEngine.ordered(active, now, zone).take(3)
                // Sexto olvido (c.358): "plan mínimo" es análoga a "¿qué hago ahora?"
                // — el usuario pide SU plan. Con plan vacío decía "Tu plan mínimo
                // está vacío." frente a un compromiso vencido (mentira por omisión:
                // el plan NO está vacío si hay una promesa olvidada); con tareas, no
                // anexaba la cola. Paridad con c.357/c.356/"organiza mi día". Sin
                // nueva pantalla: reutiliza overdueCommitmentAnswer/overdueCommitmentTail.
                if (minimal.isEmpty() && overdueCommitments.isNotEmpty()) {
                    return overdueCommitmentAnswer(overdueCommitments)
                }
                AssistantAnswer(
                    if (minimal.isEmpty()) "Tu plan mínimo está vacío." else "Plan mínimo: " + minimal.joinToString(" · ") { it.title } + overdueCommitmentTail(overdueCommitments),
                    relatedTaskIds = minimal.map { it.id }
                )
            }
            "15 minutos" in query || "rapido" in query || "rapida" in query -> {
                val quick = WhatNowEngine.ordered(active, now, zone).filter { it.durationMinutes <= 15 }.take(6)
                // Séptimo olvido (c.419): "tareas rápidas" es análoga a "plan mínimo"
                // — el usuario pide qué hacer ahora. Con lista vacía decía "No encuentro
                // tareas de 15 minutos o menos." frente a un compromiso vencido (mentira
                // por omisión: sí hay algo que hacer); con tareas, no anexaba la cola.
                // Paridad con c.358/c.357. "rapida" cubre la forma femenina natural
                // ("tareas rápidas"/"cosas rápidas"): antes sólo "rapido" casaba y la
                // consulta más común caía al mensaje genérico. Sin nueva pantalla:
                // reutiliza overdueCommitmentAnswer/overdueCommitmentTail.
                if (quick.isEmpty() && overdueCommitments.isNotEmpty()) {
                    return overdueCommitmentAnswer(overdueCommitments)
                }
                AssistantAnswer(
                    if (quick.isEmpty()) "No encuentro tareas de 15 minutos o menos." else "Puedes completar: " + quick.joinToString(" · ") { it.title } + overdueCommitmentTail(overdueCommitments),
                    relatedTaskIds = quick.map { it.id }
                )
            }
            query.startsWith("convierte esto en una nota") || query.startsWith("guardar como nota") -> {
                val content = clean.substringAfter(":", "").trim()
                if (content.isBlank()) AssistantAnswer("Añade el contenido después de dos puntos para crear la nota.")
                else AssistantAnswer("La nota está lista para guardarse: “${content.take(120)}”.", AssistantAction.CREATE_NOTE, content)
            }
            // c.969: «tomar nota» / «toma nota» — la forma cotidiana de pedir
            // captura de nota (hermana de «guardar como nota»; descubrimiento
            // de la sonda de paridad c.967, sonda PRE c.969: 8/8 GAP al menú
            // genérico). Con contenido («tomar nota: X» / «tomar nota de X»)
            // crea la nota con ese contenido; pelada responde la guía honesta
            // de cómo dictarlo, SIN acción: la UI crea «Nota sin título» si
            // CREATE_NOTE llega sin payload — NUNCA nota vacía.
            takeNoteCapture != null -> takeNoteCapture
            // c.987: «recuérdame <contenido>» — la hermana de TAREAS de la
            // familia de notas (c.969…c.985). Sonda PRE
            // tools/probe/AssistantTaskCreationProbe.kt: 9/10 candidatas de
            // creación caían al menú genérico (mentira por omisión: la
            // capacidad ya existe — la UI ejecuta vm.addSmartTask(payload) →
            // NaturalTaskParser, la misma captura rápida). UNA forma por
            // ciclo (anti-overreach); laterales documentadas en la sonda.
            remindMeCapture != null -> remindMeCapture
            // c.990: lateral (a) de la sonda persistente — «crea/añade/agrega
            // (una) tarea…», hermana de remindMeCapture (mismo contrato).
            createTaskCapture != null -> createTaskCapture
            // c.994: lateral (b1) de la sonda persistente — «avísame…»,
            // hermana de remindMeCapture (mismo contrato).
            avisaMeCapture != null -> avisaMeCapture
            // c.995: lateral (b2) de la sonda persistente — «quiero que
            // me recuerdes…», hermana de remindMeCapture (mismo contrato).
            quieroQueRecuerdesCapture != null -> quieroQueRecuerdesCapture
            // c.996: lateral (d) de la sonda persistente — «recuérdamelo»
            // deíctico: guía honesta (NUNCA tarea basura «lo»).
            remindMeLoGuide != null -> remindMeLoGuide
            // c.991: el imperativo de creación gana a la consulta c.808
            // (robo de rama medido: 5/5 capturas respondían «No tienes
            // recordatorios programados.» — mentira a una orden de crear).
            setReminderCapture != null -> setReminderCapture
            // (v) sonda c.779: "notas fijadas" — el asistente no recibe notas, así la
            // ÚNICA ruta honesta hacia ellas es la vista de búsqueda (SearchKind.NOTE
            // + wantsPinned). Antes caía al menú genérico: mentira por omisión
            // cruzada (el dato existe en la app; sólo falta el routing).
            isPinnedNotesQuery(query) ->
                AssistantAnswer("Abriré la búsqueda con las notas fijadas.", AssistantAction.OPEN_SEARCH, clean)
            query.startsWith("busca ") || query.startsWith("muestra ") || query.startsWith("pendientes con") ->
                AssistantAnswer("Abriré la búsqueda con esa consulta.", AssistantAction.OPEN_SEARCH, clean)
            // Residuo (f) de la sonda de entidades c.793: forma sustantiva del
            // verbo de búsqueda («búsqueda de <X>»). Sin esta rama caía al menú
            // pese a que «busca» ruteaba; y pasar la consulta íntegra envenena
            // ("busqueda" quedaría como token de contenido), así que el
            // payload es el operando despojado del prefijo.
            busquedaNounOperand(query) != null -> {
                val operand = busquedaNounOperand(query)!!
                AssistantAnswer("Abriré la búsqueda con ${operand}.", AssistantAction.OPEN_SEARCH, operand)
            }
            // Sonda de entidades c.793: "mis notas"/"todas las notas" — la forma
            // COTIDIANA de pedir ver las notas — no era "fijadas" (rama de arriba)
            // ni "guardar como nota" (CREATE_NOTE, arriba también), así que caía
            // al menú genérico: mentira por omisión cruzada (el dato existe en
            // SearchKind.NOTE; sólo faltaba el routing). Ruta honesta: OPEN_SEARCH
            // con payload canónico "notas" (SearchEngine.wantsNotes filtra limpio;
            // "mis"/"todas"/"las" se hubieran quedado como palabras de contenido).
            isNotesListingQuery(query) ->
                AssistantAnswer("Abriré la búsqueda con las notas.", AssistantAction.OPEN_SEARCH, "notas")
            // Sonda de entidades c.795: hábitos/rutinas/proyectos — las familias
            // restantes que el buscador lista. El asistente tampoco las recibe
            // (igual que notas, c.793), así que la única ruta honesta sigue
            // siendo OPEN_SEARCH; antes, «hábitos»/«mis rutinas»/«proyectos»
            // caían al menú genérico — mentira por omisión cruzada. Payload
            // canónico (plural): "mis"/"los"/"las" se hubieran quedado como
            // palabras de contenido en el buscador.
            entityListingPayload(query) != null -> {
                val payload = entityListingPayload(query)!!
                AssistantAnswer("Abriré la búsqueda con ${ENTITY_LISTING_LABELS[payload]}.", AssistantAction.OPEN_SEARCH, payload)
            }
            // Noveno olvido de la familia "lie-by-omission" / recuperación de
            // compromisos: "¿qué me comprometí?"/"¿qué prometí?" — la forma
            // COTIDIANA de pedir recordar lo prometido en una conversación — no
            // contiene "compromiso" (la rama de compromisos sin fecha exigía ese
            // sustantivo) ni casa con olvidos/vencidas (esas hablan de tareas, no
            // de promesas). Caía al menú genérico: con un compromiso vencido
            // respondía "Puedo organizar tu día… (N vencidos)" — nombra el conteo
            // pero NO cuál es la promesa olvidada; SIN vencidos, no mencionaba los
            // pendientes en absoluto. El usuario que pregunta "¿qué prometí?" para
            // recuperar una promesa olvidada perdía la recuperación. Se rutea a la
            // misma maquinaria existente: overdueCommitmentAnswer (nombra la
            // promesa vencida más urgente + OPEN_CONVERSATIONS) cuando hay vencidos;
            // conteo de pendientes + OPEN_CONVERSATIONS cuando sólo hay pendientes
            // no vencidos; mensaje honesto cuando no hay ninguno. "me comprometi"
            // cubre "¿qué me comprometí?"/"me comprometí a…"; "que prometi" cubre
            // "¿qué prometí?". No se añade "compromiso" suelto (ya existe su rama
            // específica sin fecha). Sin nueva pantalla/botón, sin IA fingida.
            "me comprometi" in query || "que prometi" in query -> {
                when {
                    overdueCommitments.isNotEmpty() -> overdueCommitmentAnswer(overdueCommitments)
                    pendingCommitments.isNotEmpty() -> AssistantAnswer(
                        "Tienes ${pendingCommitments.size} compromisos pendientes de conversaciones por revisar.",
                        AssistantAction.OPEN_CONVERSATIONS
                    )
                    else -> AssistantAnswer("No tienes compromisos pendientes de conversaciones.")
                }
            }
            // Décimo olvido de la familia "lie-by-omission": recuperar el LOGRO.
            // "¿Qué hice hoy?"/"¿qué completé?"/"¿qué terminé?"/"¿qué hice ayer?" —
            // la forma cotidiana de pedir recordar lo conseguido — caía al menú
            // genérico: con una tarea completada hace una hora respondía "Puedo
            // organizar tu día…", callando el logro que el usuario pidió recuperar.
            // Ordía YA calcula "completadas hoy" ([TaskRules.completedTodayCount],
            // fuente única de verdad para el resumen, el guardián y el insight) y lo
            // muestra en la tarjeta, pero el asistente —la superficie de diálogo—
            // no lo exponía. La recuperación del logro importa tanto como la del
            // olvido: ver lo conseguido es el retroalimentación honesta que sostiene
            // el ánimo y evita rehacer trabajo creído pendiente. Reusa el MISMO
            // predicado canónico que completedTodayCount (raíces, completed,
            // !archived, !CANCELLED, completedAt==fecha) y lo extiende a "ayer"
            // (mismo predicado, fecha = hoy-1). Lista los títulos ordenados por
            // completedAt desc (lo más reciente primero) y nombra hasta 3 + recuento
            // del resto. No infla con subtareas (raíces, igual que completedToday/
            // overdue) ni con archivadas/canceladas. Determinista y local, sin IA
            // fingida. No colisiona con la agenda ("que tengo"/"hay algo" — aquí
            // "que hice/complete/termine") ni con olvidos ("olvid"/"vencid") ni con
            // compromisos ("prometi"/"compromiso"). Sin nueva pantalla ni botón.
            isCompletedRecapIntent(query) -> completedAnswer(query, tasks, now, zone)
            // Búsqueda puntual de una entidad conocida: «a qué hora tengo la reunión»,
            // «cuándo pago la luz», «dónde es la cita». El dato EXISTE entre las tareas
            // del usuario, la consulta es directa, pero caía al MENÚ GENÉRICO («Puedo
            // organizar tu día…»): se preguntaba por algo concreto que el asistente ya
            // sabía y se respondía con una lista de capacidades. Recuperación de
            // información, no nueva pantalla: reusa DateRules y la coincidencia
            // normalizada de SearchEngine. Va tras agenda/what-now/recap (éstas se
            // evalúan antes y describen conjuntos, no una entidad concreta).
            isEntityLookupQuery(query) -> entityLookupAnswer(query, active, now, zone)
            // Filtro por prioridad EXPLÍCITA ("urgente"/"importante" y los
            // niveles exactos "prioridad alta"/"prioridad baja"). El usuario ya
            // marcó esa señal en la captura con el enum LOW/NORMAL/HIGH/URGENT:
            // "urgente" filtra URGENT; "importante" cubre HIGH+URGENT (los dos
            // niveles altos); y —paridad búsqueda↔asistente de la sonda
            // diferencial c.779— "prioridad alta" filtra EXACTAMENTE HIGH y
            // "prioridad baja" EXACTAMENTE LOW, como hace SearchEngine con
            // hasHighPriorityIntent/hasLowPriorityIntent. Antes las dos
            // últimas caían al menú genérico pese a que la búsqueda las
            // recuperaba. Se listan en el orden de What Now para que lo
            // primero nombrado sea lo primero sugerido. IA honesta: responde
            // con la señal que el propio usuario puso, no con una inferencia.
            // Va tras recap/entity-lookup para no robar consultas que combinen
            // tiempo o entidad con el marcador ("¿cuándo es lo urgente?" sigue
            // resolviéndose como entity-lookup). Con lista vacía y un
            // compromiso vencido rutea a la recuperación (paridad con c.416
            // "tareas de 15 minutos": "no tienes urgentes" frente a una
            // promesa vencida es mentira por omisión); con coincidencias anexa
            // la cola de conteo (overdueCommitmentTail).
            priorityIntent != null -> {
                val (allowed, hitLabel, emptyText) = priorityIntent
                val hits = WhatNowEngine.ordered(active, now, zone).filter { it.priority in allowed }.take(6)
                if (hits.isEmpty() && overdueCommitments.isNotEmpty()) {
                    return overdueCommitmentAnswer(overdueCommitments)
                }
                AssistantAnswer(
                    if (hits.isEmpty()) emptyText
                    else "Tienes ${hits.size} $hitLabel: " + hits.joinToString(" · ") { it.title } + overdueCommitmentTail(overdueCommitments),
                    relatedTaskIds = hits.map { it.id }
                )
            }
            // Filtro por tareas MARCADAS ("tareas marcadas"/"destacadas"). Paridad
            // con SearchEngine.FLAGGED_TOKENS (task.flagged): la búsqueda las
            // recupera, el asistente caía al menú genérico — mentía por omisión
            // sobre la señal más explícita que el usuario pone (él mismo marcó la
            // tarea, a veces TODAS las de un proyecto). Coincidencia por PALABRA
            // EXACTA (participios + pretérito "marque"/"destaque"), así el infinitivo "marcar"/"destacar"
            // (acción por hacer) no la detona (guardia palabra-exacta, igual que
            // c.779 para los participios del recap). Va después de prioridad para
            // que "marcadas como urgentes" siga filtrando por prioridad. Vacío:
            // honesto, sin menú (paridad familia lie-by-omission); con compromiso
            // vencido, recuperación (c.357/c.416).
            isFlaggedQuery(query) -> {
                val hits = WhatNowEngine.ordered(active, now, zone).filter { it.flagged }.take(6)
                if (hits.isEmpty() && overdueCommitments.isNotEmpty()) {
                    return overdueCommitmentAnswer(overdueCommitments)
                }
                AssistantAnswer(
                    if (hits.isEmpty()) "No tienes tareas marcadas."
                    else "Tienes ${hits.size} marcadas: " + hits.joinToString(" · ") { it.title } + overdueCommitmentTail(overdueCommitments),
                    relatedTaskIds = hits.map { it.id }
                )
            }
            // Tareas recurrentes ("recurrentes"/"repetitivas", por PALABRA, como
            // RECURRING_TOKENS de SearchEngine — paridad de superficies c.781-flagged).
            // Formas ADJETIVAS: ni el sustantivo "repetición" (aparece en títulos
            // de tareas únicas) ni el verbo "repetir". Vacío honesto con cola de
            // compromisos vencidos, igual que las demás ramas.
            isRecurringQuery(query) -> {
                val recurring = WhatNowEngine.ordered(active, now, zone)
                    .filter { it.recurrence != RecurrenceFrequency.NONE }.take(6)
                if (recurring.isEmpty() && overdueCommitments.isNotEmpty()) {
                    return overdueCommitmentAnswer(overdueCommitments)
                }
                AssistantAnswer(
                    if (recurring.isEmpty()) "No tienes tareas recurrentes."
                    else "Tienes ${recurring.size} recurrentes: " + recurring.joinToString(" · ") { it.title } + overdueCommitmentTail(overdueCommitments),
                    relatedTaskIds = recurring.map { it.id }
                )
            }
            // Tareas sin fecha ("sin fecha"/"sin día"/"sin plazo"/"sin vencimiento").
            // La búsqueda (SearchEngine.UNDATED) las recupera; el asistente caía al
            // menú genérico y la tarea sin vencimiento —la más olvidable de todas—
            // quedaba invisible para la superficie conversacional. Va tras el
            // compromiso-sin-fecha (ésa habla de compromisos) y tras prioridad para
            // no robar consultas mixtas. Vacío: honesto, sin menú (paridad familia
            // lie-by-omission).
            isUndatedQuery(query) -> {
                val undated = WhatNowEngine.ordered(active, now, zone).filter { it.dueAt == null }.take(6)
                if (undated.isEmpty() && overdueCommitments.isNotEmpty()) {
                    return overdueCommitmentAnswer(overdueCommitments)
                }
                AssistantAnswer(
                    if (undated.isEmpty()) "No tienes tareas sin fecha."
                    else "Tienes ${undated.size} sin fecha: " + undated.joinToString(" · ") { it.title } + overdueCommitmentTail(overdueCommitments),
                    relatedTaskIds = undated.map { it.id }
                )
            }
            // Recordatorios próximos (c.808): «qué recordatorios tengo», «mis
            // recordatorios», «qué me vas a recordar» caían al menú genérico
            // (mentira por omisión: el dato estaba disponible). Lista honesta
            // de avisos PRÓXIMOS (reminderAt >= now) de tareas activas,
            // ordenada por disparo, con etiqueta relativa («hoy/mañana/el
            // <fecha> a las <hora>», misma fuente que upcomingWhenLabel).
            // Los avisos ya pasados de tareas activas no se enumeran: esa
            // deuda la cubre la familia «vencidas». Vacío honesto sin menú.
            isRemindersQuery(query) -> {
                val all = active.filter { it.reminderAt != null && it.reminderAt >= now }
                    .sortedBy { it.reminderAt }
                val upcoming = all.take(6)
                // c.811: cuando la lista SUPERA la ventana en línea, ofrece
                // OPEN_SEARCH «recordatorios» (la búsqueda universal ya la
                // entiende, c.810) para ver el listado completo. Y el conteo se
                // calcula sobre TODOS — antes «Tienes 6 recordatorios» mentía
                // cuando había más de 6 programados.
                val truncated = all.size > upcoming.size
                AssistantAnswer(
                    if (all.isEmpty()) "No tienes recordatorios programados."
                    else "Tienes ${all.size} recordatorio" + (if (all.size == 1) "" else "s") + ": " +
                        upcoming.joinToString(" · ") { "«${it.title}» ${upcomingWhenLabel(it.reminderAt!!, now, zone)}" },
                    action = if (truncated) AssistantAction.OPEN_SEARCH else AssistantAction.NONE,
                    actionPayload = if (truncated) "recordatorios" else "",
                    relatedTaskIds = upcoming.map { it.id }
                )
            }
            isFreeTimeQuery(query) -> {
                // Décimo olvido de la familia "lie-by-omission": el usuario ya
                // declara que tiene un hueco libre ("tengo un rato/tiempo/hueco"
                // o "tengo N minutos") — caía al menú genérico, que PIDE una
                // intención conocida y le devuelve trabajo de pensar. La misma
                // frase calibrada con la ventana que el propio usuario declara
                // (explícita: dígito/palabra + minutos|horas|media hora; suelta:
                // QUICK_TASK_WINDOW_MINUTES de la rama "tareas de 15 minutos")
                // filtra el plan de What Now a lo que CABE. Y con hueco vacío,
                // la promesa vencida rutea a la recuperación (paridad
                // c.357/c.416/c.680: mentir por omisión en una superficie
                // declarativa es peor que no responder). Determinista y local:
                // reusa WhatNowEngine.ordered + overdueCommitmentAnswer; cero
                // nueva pantalla, cero random, cero IA fingida. El filtro
                // `durationMinutes <= window` es la ESTIMACIÓN REAL que el
                // usuario calibró por tarea (0 = sin estimar → cuenta como
                // corta, paridad con la rama c.416 de tareas rápidas).
                val windowMinutes = freeTimeWindowMinutes(query) ?: QUICK_TASK_WINDOW_MINUTES
                val quick = WhatNowEngine.ordered(active, now, zone).filter { it.durationMinutes <= windowMinutes }.take(6)
                if (quick.isEmpty() && overdueCommitments.isNotEmpty()) {
                    return overdueCommitmentAnswer(overdueCommitments)
                }
                return AssistantAnswer(
                    if (quick.isEmpty()) "Nada te cabe en esos $windowMinutes minutos."
                    else "En esos $windowMinutes minutos puedes completar: " + quick.joinToString(" · ") { it.title } + overdueCommitmentTail(overdueCommitments),
                    relatedTaskIds = quick.map { it.id }
                )
            }

            // Señal de sobrecarga emocional ("estoy abrumado/agobiado",
            // "no doy abasto" — cluster E c.677). Ante esa señal la respuesta debe
            // REDUCIR carga, no listar ni abrir el menú de capacidades: una única
            // cosa (la siguiente según What Now) y el resto queda esperando. Vacío
            // honesto sin tareas; vacío + promesa vencida → recuperación (paridad
            // c.357/c.416/c.680: mentir por omisión en la superficie emocional es
            // peor que no responder). Determinista y local: reusa WhatNowEngine,
            // sin random, sin IA fingida, sin nueva pantalla.
            isOverwhelmedQuery(query) -> {
                val suggestion = WhatNowEngine.suggest(active, now, zone)
                if (suggestion == null) {
                    if (overdueCommitments.isNotEmpty()) return overdueCommitmentAnswer(overdueCommitments)
                    AssistantAnswer("No encuentro tareas pendientes. Respira.")
                } else {
                    val rest = active.size - 1
                    val restTail = if (rest == 1) " Cuando la termines queda 1; una a una."
                        else if (rest > 1) " Cuando la termines quedan $rest; una a una."
                        else ""
                    AssistantAnswer(
                        "Respira. Primero solo esto: «${suggestion.task.title}».$restTail",
                        relatedTaskIds = listOf(suggestion.task.id)
                    )
                }
            }
            // Petición de recomendación/decisión (cluster sonda assistant: "qué me
            // recomiendas", "recomiéndame algo", "ayúdame a decidir", "cuál me
            // conviene hacer"). El usuario pide UNA sugerencia; responder con el
            // menú de capacidades le devuelve trabajo de pensar. La rama reusa
            // What Now y describe UNA sola cosa con el resto contado
            // honestamente (paridad familia overwhelmed). Vacío: vacío honesto
            // (NUNCA menú); vacío + promesa vencida → recuperación (paridad
            // c.357/c.416/c.680). Determinista y local: reusa WhatNowEngine, sin
            // random, sin IA fingida, sin nueva pantalla.
            isRecommendationQuery(query) -> {
                val suggestion = WhatNowEngine.suggest(active, now, zone)
                if (suggestion == null) {
                    if (overdueCommitments.isNotEmpty()) return overdueCommitmentAnswer(overdueCommitments)
                    AssistantAnswer("No encuentro tareas pendientes para recomendar.")
                } else {
                    val rest = active.size - 1
                    val restTail = if (rest == 1) " Cuando la termines queda 1."
                        else if (rest > 1) " Cuando la termines quedan $rest."
                        else ""
                    AssistantAnswer(
                        "Te sugiero empezar por «${suggestion.task.title}».$restTail",
                        relatedTaskIds = listOf(suggestion.task.id)
                    )
                }
            }
            // Petición de posponer/defer (cluster sonda assistant: "qué puedo
            // dejar para mañana", "puedo posponer algo", "qué no puedo dejar
            // para después", "qué pasa si pospongo"). El usuario pregunta QUÉ
            // se aplaza sin romper nada; responder con el menú de capacidades
            // le devuelve trabajo de decidir. Ordía YA sabe elegir la tarea de
            // hoy más posponible (fuente única: [SummaryEngine.deferralCandidate],
            // la MISMA lógica que la tarjeta de resumen nombra bajo OVERLOADED),
            // así asistente y tarjeta nunca discrepen. Solo nombra la candidata
            // + id (el usuario decide moverla; nunca un auto-movimiento,
            // coherente con el diseño de deferralSuggestion). Vacío: vacío
            // honesto (NUNCA menú); vacío + promesa vencida → recuperación
            // (paridad familia lie-by-omission c.357/c.416/c.680). Determinista
            // local; cero random/IA fingida/pantalla nueva (decisión c.361).
            // c.798 (sonda AssistantHonestRouteProbe): «¿qué tarea es más
            // larga?» nombra la más larga del día por duración planificable
            // (fuente única [TaskRules.plannedDuration], la misma del plan y
            // del resumen → no discrepa con la tarjeta). Evalúa antes del
            // deferral para que «larga» tenga su ruta. Vacío: vacío honesto
            // (nunca menú).
            isLongestTaskQuery(query) -> {
                val longest = longestTaskToday(active, now, zone)
                if (longest == null) {
                    AssistantAnswer("No tienes tareas pendientes hoy.")
                } else {
                    AssistantAnswer(
                        "La más larga de hoy es «${longest.title}» (≈${TaskRules.plannedDuration(longest)} min).",
                        relatedTaskIds = listOf(longest.id)
                    )
                }
            }
            isDeferralQuery(query) -> {
                val candidate = SummaryEngine.deferralCandidate(active, now, zone)
                if (candidate == null) {
                    if (overdueCommitments.isNotEmpty()) return overdueCommitmentAnswer(overdueCommitments)
                    AssistantAnswer("No tienes tareas de hoy que puedan posponerse.")
                } else {
                    AssistantAnswer(
                        "La más posponible de hoy es «${candidate.title}»; puedes dejarla para mañana.",
                        relatedTaskIds = listOf(candidate.taskId)
                    )
                }
            }
            // Recuento honesto de pendientes (sonda assistant c.798): «¿cuántas
            // tareas tengo?»/«¿cuántas pendientes?» caían al menú genérico aunque
            // el grupo activo ya está calculado («active» excluye subtareas y
            // completadas, fuente única). Vacío: vacío honesto celebrativo
            // (NUNCA menú; paridad familia lie-by-omission). Nombra las 5
            // primeras para que el recuento actúe (no sólo diga un número).
            isPendingCountQuery(query) -> {
                val pendingPreview = active.take(5)
                AssistantAnswer(
                    if (active.isEmpty()) "No tienes tareas pendientes."
                    else "Tienes ${active.size} pendientes: ${pendingPreview.joinToString(", ") { "«${it.title}»" }}${if (active.size > 5) "…" else "."}",
                    relatedTaskIds = active.map { it.id }
                )
            }
            // c.816 — recuento honesto de rutinas (residuo de la sonda c.815):
            // «cuantas rutinas tengo» caía al menú porque la rama de recuento
            // sólo recibía tareas; las rutinas ahora llegan como parámetro
            // (AssistantScreen pasa state.routines). Cuenta las activas (no
            // archivadas) y nombra las 5 primeras, hermana del recuento de
            // pendientes. Vacío: vacío honesto (NUNCA menú).
            isRoutineCountQuery(query) -> {
                val liveRoutines = routines.filter { !it.archived }
                val preview = liveRoutines.take(5)
                AssistantAnswer(
                    if (liveRoutines.isEmpty()) "No tienes rutinas activas."
                    else "Tienes ${liveRoutines.size} rutinas: ${preview.joinToString(", ") { "«${it.name}»" }}${if (liveRoutines.size > 5) "…" else "."}"
                )
            }
            // c.821 (sonda efímera ForgottenTasksProbe: 8/10 GAPs al menú):
            // «tareas olvidadas»/«las más antiguas» hermanas del recuento de
            // pendientes. La ruta honesta sale de `createdAt` (dato real,
            // persistido con la tarea): nombra las 3 pendientes raíz más
            // antiguas para que la recuperación actúe. Vacío: vacío honesto
            // (NUNCA menú; paridad familia lie-by-omission).
            isForgottenQuery(query) -> {
                val oldest = active.sortedWith(compareBy<TaskEntity> { it.createdAt }.thenBy { it.id })
                val preview = oldest.take(3)
                AssistantAnswer(
                    if (oldest.isEmpty()) "No tienes tareas pendientes; nada olvidado."
                    else "Las que más tiempo llevan pendientes: ${preview.joinToString(", ") { "«${it.title}»" }}${if (oldest.size > 3) "…" else "."}",
                    relatedTaskIds = oldest.map { it.id }
                )
            }
            // Petición de tiempo invertido (cluster sonda assistant: "en qué
            // gasto mi tiempo", "en qué estoy gastando tiempo", "en qué se me
            // va el tiempo"). El usuario pregunta EN QUÉ invirtió su día;
            // responder con el menú de capacidades le devuelve trabajo de
            // decidir. Ordía YA registra sesiones de enfoque con minutos
            // reales por tarea; se agregan las COMPLETADAS de hoy (fuente
            // única: [FocusRecap.today], mismas reglas defensivas que
            // GuardianEngine) y se nombran las 3 tareas con más minutos.
            // Vacío: vacío honesto (NUNCA menú; paridad familia
            // lie-by-omission). IA honesta: solo agrega minutos registrados,
            // no infiere ni estima. Cero random/pantalla nueva (decisión c.361).
            isTimeSpentQuery(query) -> {
                val recap = FocusRecap.today(tasks, focusSessions, now, zone)
                if (recap.totalMinutes <= 0) {
                    AssistantAnswer("Hoy aún no registras tiempo de enfoque; cuando completes una sesión, te diré aquí en qué lo invertiste.")
                } else {
                    val total = FocusRecap.humanMinutes(recap.totalMinutes)
                    val parts = recap.topTasks.joinToString(", ") { "«${it.title}» (${FocusRecap.humanMinutes(it.minutes)})" }
                    val tail = if (parts.isEmpty()) "." else ": $parts."
                    AssistantAnswer(
                        "Hoy invertiste $total de enfoque$tail",
                        relatedTaskIds = recap.topTasks.map { it.taskId }
                    )
                }
            }
            // Recuperación por calificador de contenido (sonda de paridad
            // búsqueda↔asistente c.792): «tareas de la casa», «tareas del
            // proyecto», «pendientes de química» — caían al menú mientras
            // SearchEngine sí filtraba por el calificador (residuo documentado
            // c.784 del conector temporal «bare», cuyo guard SÓLO cubría alcance
            // temporal). Se evalúa de ÚLTIMA: todos los vocabularios
            // (completadas/marcadas/recurrentes/prioridad/entidad/tiempo libre/
            // sobrecarga/posponer/tiempo invertido/agenda/compromisos/notas
            // fijadas) reclaman antes su rama, y el alcance temporal («tareas
            // de hoy») lo resuelve isAgendaQuery mucho antes — así aquí sólo
            // llega un calificador real de contenido. Ruta honesta: la vista
            // de búsqueda (matcher de contenido real), NUNCA una respuesta
            // inventada. Paridad con «notas fijadas» c.787. Determinista; sin
            // pantalla nueva; helper puro.
            // c.794 — la misma doctrina (c.788 DECISIONS) se extiende al
            // calificador por contenido sobre la superficie de notas con
            // [isContentQualifiedNotesQuery] («notas de/del/de la <X>»): la
            // búsqueda filtraba por el calificador y el asistente caía al
            // menú. El listado desnudo («notas»/«mis notas») lo cubre
            // [isNotesListingQuery] (c.793) mucho antes con payload canónico;
            // pinned (c.787) y creación también reclaman antes; «notas de»
            // sin calificador no rutea (guard).
            // c.807 — hermana interrogativa de las dos ramas anteriores:
            // «qué notas tengo de trabajo» / «qué tareas tengo del proyecto
            // casa» caían al menú (GAP abierto de la sonda c.803-b) mientras
            // la afirmativa equivalente ya buscaba. El payload es la frase
            // afirmativa reconstruida (sin «qué … tengo») sobre `clean`, así
            // SearchEngine extrae el calificador con su misma lógica y los
            // acentos originales se conservan. El alcance temporal («qué
            // tareas tengo de hoy») lo reclama isAgendaQuery mucho antes.
            isContentQualifiedTasksQuery(query) || isContentQualifiedNotesQuery(query) ||
                contentQualifiedInterrogativePayload != null ->
                AssistantAnswer(
                    "Abriré la búsqueda con esa consulta.",
                    AssistantAction.OPEN_SEARCH,
                    contentQualifiedInterrogativePayload ?: clean
                )
            // Octavo olvido de la familia "lie-by-omission": la consulta no casa con
            // ninguna rama conocida y el asistente cae a su menú de capacidades. Es la
            // superficie de mayor tránsito para un usuario confundido —y justo ahí
            // callaba un compromiso vencido de conversación. Antes respondía "Puedo
            // organizar tu día…" frente a una promesa olvidada: el usuario confundido
            // no sabía qué preguntar Y no se enteraba de que debía algo. No se redirige
            // a overdueCommitmentAnswer (secuestraría el menú de descubrimiento que el
            // usuario necesita); se anexa la cola de conteo, paritaria con las
            // superficies que muestran una lista (c.357/c.358/c.421). Sin nueva pantalla.
            else -> AssistantAnswer(
                "Puedo organizar tu día, decirte qué hacer ahora, qué tienes mañana, mostrar lo vencido, resumir conversaciones, buscar pendientes o preparar un plan mínimo." +
                    overdueCommitmentTail(overdueCommitments)
            )
        }
    }

    /**
     * Detecta la intención de "recap" de logros: "¿qué hice hoy?", "¿qué
     * completé/terminé hoy?", "¿qué hice ayer/anteayer?" y los períodos "¿qué
     * completé esta semana/este mes?". Tokens sin acento (ya normalizados por
     * `foldForSearch`). Excluye deliberadamente el verbo *tener* (agenda) y
     * *deber/faltar* (pendientes) para no secuestrar esas ramas. El token "hice"
     * solo casa con recap (no aparece en agenda ni en olvidos ni en
     * compromisos), por lo que es seguro.
     */
    private fun isCompletedRecapIntent(query: String): Boolean {
        val isRecapVerb =
            "que hice" in query || "que complete" in query ||
                "que termine" in query || "que acabe" in query ||
                "que complete" in query || "que completado" in query ||
                "hice hoy" in query || "complete hoy" in query ||
                "termine hoy" in query || "acabe hoy" in query ||
                "completado hoy" in query
        // c.798 (sonda AssistantHonestRouteProbe): forma invertida del recap —
        // «¿qué mes pasado hice?»/«¿qué semana pasada hice?» — el token
        // "hice" suelto basta para la intención de recap; el período lo
        // resuelve completedAnswer (LAST_WEEK/LAST_MONTH_MODIFIERS) y cae a
        // default hoy si no hay uno.
        val recapLoose = "hice" in query
        // "hice ayer"/"hice anteayer" fuerzan la fecha aunque falte el verbo
        // recap explícito ("¿qué hice ayer?" trae "hice" + "ayer"; lo mismo con
        // anteayer). "anteayer" contiene "ayer", por lo que ambos se cubren.
        return isRecapVerb || recapLoose || "hice ayer" in query || "hice anteayer" in query ||
            isCompletedAdjectiveQuery(query)
    }

    // Adjetivo de completado + sustantivo "tarea(s)": "tareas completadas",
    // "mis tareas terminadas", "tareas hechas la semana pasada". Es la MISMA
    // intención de recap ("¿qué terminé?") en su forma adjetival —la más
    // cotidiana— pero sin verbo recap caía al menú genérico callando el logro
    // (gap medido por sonda diferencial búsqueda-vs-asistente: SearchEngine
    // COMPLETED_TOKENS las recupera, el asistente no). Mismo vocabulario
    // adjetival que SearchEngine (paridad de superficies). Por PALABRA (no
    // subcadena): "determinada" contiene "terminada" y el infinitivo
    // "completar/terminar" (acción pendiente, no logro) no debe disparar el
    // recap. Se exige "tarea(s)" para no robar consultas ajenas al recap de
    // tareas ("proyecto terminado" no es recap de tareas).
    private val COMPLETED_ADJECTIVE_TOKENS = setOf(
        "completada", "completadas", "completado", "completados",
        "hecha", "hechas", "hecho", "hechos",
        "terminada", "terminadas", "terminado", "terminados",
        "finalizada", "finalizadas", "finalizado", "finalizados",
        "acabada", "acabadas", "acabado", "acabados"
    )

    private fun isCompletedAdjectiveQuery(query: String): Boolean =
        "tarea" in query &&
            query.split(Regex("\\s+")).any { it in COMPLETED_ADJECTIVE_TOKENS }

    /**
     * Intención de prioridad EXPLÍCITA de la consulta (ya plegada por
     * `foldForSearch`): (niveles permitidos, etiqueta del listado, mensaje
     * honesto de vacío). Devuelve `null` cuando no hay marcador. Cobertura en
     * paridad exacta con SearchEngine:
     *  - "prioridad alta"/"alta prioridad" → sólo HIGH (hasHighPriorityIntent);
     *  - "prioridad baja"/"baja prioridad" → sólo LOW (hasLowPriorityIntent);
     *  - "urgente" → sólo URGENT;
     *  - "importante" → HIGH+URGENT (los dos niveles altos).
     * Los niveles exigen la palabra "prioridad" como desambiguador —idéntico a
     * la búsqueda— así "alta" sola ("alta médica") ni "baja" sola ("baja del
     * auto") disparan, y la comparación es por PALABRA (no subcadena) para que
     * "saltar prioridad" no prenda por la "alta" interna de "saltar". El
     * desempate resuelve el nivel explícito primero (alta/baja), después
     * "urgente" y por último "importante" (orden de especificidad).
     */
    private fun priorityIntent(query: String): Triple<Set<TaskPriority>, String, String>? {
        val words = query.split(Regex("\\s+"))
        val hasPriorityWord = "prioridad" in words || "prioridades" in words
        return when {
            hasPriorityWord && ("alta" in words || "altas" in words) ->
                Triple(setOf(TaskPriority.HIGH), "de prioridad alta", "No tienes tareas de prioridad alta.")
            hasPriorityWord && ("baja" in words || "bajas" in words) ->
                Triple(setOf(TaskPriority.LOW), "de prioridad baja", "No tienes tareas de prioridad baja.")
            "urgente" in query -> Triple(setOf(TaskPriority.URGENT), "urgentes", "No tienes tareas marcadas como urgentes.")
            "importante" in query -> Triple(setOf(TaskPriority.HIGH, TaskPriority.URGENT), "importantes", "No tienes tareas marcadas como importantes.")
            else -> null
        }
    }

    /**
     * Vocabulario de marcado: paridad estricta con los FLAGGED_TOKENS de
     * [com.ordia.app.domain.SearchEngine] (participios + pretérito 1.ª persona
     * "marque"/"destaque", nunca infinitivos). Por PALABRA EXACTA para no
     * secuestrar "marcar"/"destacar" (acción por hacer) ni un "desmarcada". Si
     * el buscador añade o quita un token, este conjunto debe moverse con él en
     * la misma dirección (soledad buscador↔asistente, doctrina c.677/c.779).
     */
    private val FLAGGED_WORDS = setOf(
        "marcada", "marcadas", "marcado", "marcados",
        "destacada", "destacadas", "destacado", "destacados",
        "marque", "destaque"
    )

    private fun isFlaggedQuery(query: String): Boolean =
        query.split(" ").any { it in FLAGGED_WORDS }

    // Intención "tareas recurrentes": adjetivos por palabra exacta, mismo
    // mutismo que FLAGGED_WORDS; no "repetición" (título de tarea única) ni
    // "repetir" (acción ya resuelta). Token-sync con SearchEngine.RECURRING_TOKENS.
    private val RECURRING_WORDS = setOf(
        "recurrente", "recurrentes", "repetitiva", "repetitivas"
    )

    // c.812 sonda de descubrimiento (DiscoveryRound202608): la forma cotidiana
    // «tareas que se repiten» caía al menú pese a que la memoria de rutinas
    // (recurrence != NONE) ya llega. Frase (no token aislado — "repite" suelto
    // seguiría siendo acción), simétrica a [MISSED_SLIP_HEADS]; no secuestra
    // el infinitivo ("repetir una tarea" sigue sin ruteo).
    private val RECURRING_PHRASES = listOf("se repite", "se repiten")

    private fun isRecurringQuery(query: String): Boolean =
        query.split(" ").any { it in RECURRING_WORDS } ||
            RECURRING_PHRASES.any { it in query }

    // Intención "tareas sin fecha": identica al scope UNDATED de SearchEngine
    // ("sin" + hint: fecha/vencimiento/día/plazo), así búsqueda y asistente
    // coinciden en qué frases abren el conjunto. "compromiso sin fecha" NO se
    // roba: la rama de compromisos se evalúa antes que ésta en `answer`.
    // c.812 sonda de descubrimiento (DiscoveryRound202608): «sin agenda»/«sin
    // programación» apuntan al mismo conjunto (las tareas por anclar en
    // calendario) y caían al menú; el guard `sin` sigue haciendo la frase
    // inocua para "tengo agenda llena"/"programación" a secas.
    private fun isUndatedQuery(query: String): Boolean =
        "sin fecha" in query || "sin vencimiento" in query || "sin dia" in query || "sin plazo" in query ||
            "sin agenda" in query || "sin programacion" in query

    // Recordatorios consultables por voz (c.808): la preferencia vive en
    // TaskEntity.reminderAt y YA llega al asistente, así que «qué recordatorios
    // tengo» / «mis recordatorios» / «qué me vas a recordar» no necesitan
    // wiring nuevo — solo routing. El imperativo de creación («recuérdame…»)
    // no contiene el sustantivo, así que no hace falta guard explícito.
    private fun isRemindersQuery(query: String): Boolean =
        "recordatorio" in query || "que me vas a recordar" in query

    // Modismo del olvido "se me/se nos pas…" (1.ª singular y plural en pretérito):
    // frase exacta en la consulta normalizada (foldForSearch quita los acentos).
    // Se excluye "te" ("¿qué te pasó?" es "ocurrió algo", no un olvido); nunca
    // la palabra "paso" suelta. Simétrico a SearchEngine.MISSED_SLIP_HEADS.
    // c.797: cabezas imperfeccionables del olvido incluyen "se me olvid" /
    // "se nos olvid" — forma cotidiana ("¿qué se me olvidaba?", "¿qué se nos
    // olvidó?") que caía al menú genérico (mentira por omisión).
    private val MISSED_SLIP_HEADS = listOf("se me pas", "se nos pas", "se me olvid", "se nos olvid")

    private fun isMissedSlipQuery(query: String): Boolean =
        MISSED_SLIP_HEADS.any { it in query }

    // Notas fijadas: "nota(s)" + participio de "fijar" por palabra exacta
    // (vocabulario propio de notas: nunca "marcadas", que es de tareas). En
    // paridad con SearchEngine.PINNED_TOKENS. Creación ("guardar como nota:") se
    // evalúa ANTES en el when y sigue ganando; "fijada" suelta no es consulta.
    private val PINNED_NOTE_WORDS = setOf("fijada", "fijadas", "fijado", "fijados")

    private fun isPinnedNotesQuery(query: String): Boolean {
        val words = query.split(" ")
        if (words.any { it == "nota" || it == "notas" } && words.any { it in PINNED_NOTE_WORDS }) return true
        // c.802: «que tengo/hay fijado» sin el sustantivo «nota» — hermano de
        // la forma anterior; el vocabulario pinned sigue siendo honesto (la
        // búsqueda lista NOTAS fijadas; el asistente redirige a ella).
        val pinned = words.any { it in PINNED_NOTE_WORDS }
        val interrogativa = "que tengo" in query || "que hay" in query ||
            "tengo algo" in query || "hay algo" in query
        return pinned && interrogativa
    }

    // c.969: captura «tomar nota» / «toma nota» (plural incluido). El prefijo
    // exige inicio de frase para no secuestrar contenido («quiero tomar nota»
    // no es forma directa). El contenido puede venir tras «:» (hermano de
    // «guardar como nota: X») o tras «de» («tomar nota de la reunión»).
    // c.971: «apunta …» / «anota …» (imperativo e infinitivo cotidianos) se
    // suman al prefijo; su contenido puede venir tras «:», tras «esto:» o
    // directo («apunta llamar al banco»). Frontera de palabra tras el verbo:
    // «apuntarse»/«apuntarme»/«anotaciones» nunca casan (descubrimiento de la
    // auditoría pedida en la «próxima prioridad» de c.969 — sonda PRE 9/9 GAP).
    // c.972 (delta tras colisión de lateral): enclíticos «apúntame»/«anótame»
    // (± sin tilde — escritura móvil real; «apuntarme»/«anotarme» siguen FUERA
    // por bivalencia con apuntarse/anotarse) y el contenido «esto» a secas se
    // trata como pelada (antes creaba una nota BASURA titulada «esto»).
    // c.974: familia «escribe/crea una nota…» (auditoría pedida en la «próxima
    // prioridad» de c.972 — sonda PRE 8/8 GAP al menú): «escribe/escríbeme/
    // escribeme/escribir/crea/crear/haz + (una) nota(s)» se suman al prefijo;
    // el verbo EXIGE la palabra «nota» («escribe esto: X» queda FUERA —
    // lateral documentada) y el ancla ^ mantiene «quiero escribir una nota»
    // fuera. El contenido puede venir tras «:», tras «esto:», tras «de»
    // (hermano de «tomar nota de X» c.969) o directo; «esto» a secas sigue
    // siendo pelada (lección c.972).
    // c.976: dictado «escribe esto: …» / «guarda esto: …» (± «eso») — la lateral
    // que c.974 dejó documentada FUERA (descubrimiento c.972, BACKLOG). EXIGE
    // «esto/eso» como sujeto inequívoco de dictado: el verbo desnudo («escribe
    // un correo a juan», «guarda el archivo», «escríbeme un poema») NUNCA es
    // captura de nota (5/5 guards medidos en la sonda PRE 3/3 GAP).
    // c.977: enclítico «escríbeme/escribeme + esto/eso» (lateral que el propio
    // c.976 dejó documentada FUERA — sonda PRE 5/5 GAP al menú). Mismo guard:
    // «esto/eso» OBLIGATORIO, así «escríbeme un poema»/«escríbeme mañana»
    // nunca se secuestran; el ancla ^ mantiene «quiero que me escribas esto»
    // fuera.
    // c.980: (a) enclítico de «guarda» — «guárdame/guardame + esto/eso»
    // (simétrico del c.977; «esto/eso» sigue OBLIGATORIO: «guárdame el
    // archivo»/«guardame los cambios» NUNCA son captura); (b) deíctico fundido
    // «-melo» — «escríbemelo:/apúntemelo:/anótamelo: …» EXIGE «:» (el prefijo
    // usa lookahead «:» o fin de frase: «escríbemelo mañana»/«apúntemelo en la
    // lista» ni siquiera entran en la rama — quedan en el menú, no secuestran).
    // c.984: cierra la familia «-melo» (lateral BACKLOG descubierta c.981 —
    // sonda PRE 6/6 GAP al menú): (a) fusión de «guarda» — «guárdamelo/
    // guardamelo: …» (tú), simétrica de c.980(b); (b) formas de usted —
    // «guárdemelo/guardemelo: …» y «escríbamelo/escribamelo: …» («apúntemelo»/
    // «anótamelo» ya quedaron cubiertas por el [ae] de c.980). El vocalismo
    // [ae] de «guarda» cubre tú+usted en una sola alternativa; el lookahead
    // «:»/fin sigue exigiéndose, así «guárdamelo mañana»/«escríbamelo bonito»
    // nunca entran en la rama.
    // c.987: familia enclítica «-lo» (sonda efímera
    // /tmp/probe987/EncliticoLoCaptureProbe.kt — PRE 12/12 capturas + 2/2
    // peladas GAP al menú): «escríbelo/apúntalo/anótalo/guárdalo: …» (tú) y
    // «escríbalo/apúntelo/anótelo/guárdelo: …» (usted), hermana simétrica de
    // la «-melo» c.984 con el mismo vocalismo [ae] y el mismo lookahead
    // «:»/fin — «escríbelo mañana»/«guárdalo en el archivo» nunca entran.
    // c.988: enclítico «hazme una nota» (sonda efímera
    // /tmp/probe988/HazmeNotaProbe.kt — PRE 4/4 capturas + 2/2 peladas GAP al
    // menú): hermano no-enclítico «haz una nota» capturaba desde c.974 y el
    // enclítico quedaba al menú genérico. El verbo sigue exigiendo la palabra
    // «nota»: «hazme un favor»/«hazme la comida»/«hazme un café» nunca entran.
    private val TAKE_NOTE_PREFIX = Regex("(?i)^(?:toma(?:r)?\\s+notas?|apunta(?:r)?|anota(?:r)?|ap[uú]ntame|an[oó]tame|(?:escr[ií]beme|escribeme|escribe|escribir|crear?|haz|hazme)\\s+(?:una\\s+)?notas?|(?:escribe|escr[ií]beme|escribeme|guarda|gu[aá]rdame)\\s+es(?:t)?o|(?:escr[ií]b[ae]melo|ap[uú]nt[ae]melo|an[oó]t[ae]melo|gu[aá]rd[ae]melo)(?=\\s*:|\\s*$)|(?:escr[ií]b[ae]lo|ap[uú]nt[ae]lo|an[oó]t[ae]lo|gu[aá]rd[ae]lo)(?=\\s*:|\\s*$))\\b")
    private val TAKE_NOTE_WITH_CONTENT = Regex("(?i)^toma(?:r)?\\s+notas?\\s*(?::|\\bde\\b)\\s*(.+)$")
    private val JOT_NOTE_WITH_CONTENT = Regex("(?i)^(?:apunta(?:r)?|anota(?:r)?|ap[uú]ntame|an[oó]tame)\\s*(?::\\s*|\\besto\\s*:\\s*|\\s+)(.+)$")
    private val WRITE_NOTE_WITH_CONTENT = Regex("(?i)^(?:escr[ií]beme|escribeme|escribe|escribir|crear?|haz|hazme)\\s+(?:una\\s+)?notas?\\s*(?::\\s*|\\besto\\s*:\\s*|\\bde\\b\\s*|\\s+)(.+)$")
    private val DICTATE_NOTE_WITH_CONTENT = Regex("(?i)^(?:escribe|escr[ií]beme|escribeme|guarda|gu[aá]rdame)\\s+es(?:t)?o\\s*:\\s*(.+)$")
    private val MELO_NOTE_WITH_CONTENT = Regex("(?i)^(?:escr[ií]b[ae]melo|ap[uú]nt[ae]melo|an[oó]t[ae]melo|gu[aá]rd[ae]melo)\\s*:\\s*(.+)$")
    private val LO_NOTE_WITH_CONTENT = Regex("(?i)^(?:escr[ií]b[ae]lo|ap[uú]nt[ae]lo|an[oó]t[ae]lo|gu[aá]rd[ae]lo)\\s*:\\s*(.+)$")

    private fun takeNoteCapture(clean: String): AssistantAnswer? {
        val trimmed = clean.trim()
        if (!TAKE_NOTE_PREFIX.containsMatchIn(trimmed)) return null
        val content = TAKE_NOTE_WITH_CONTENT.matchEntire(trimmed)?.groupValues?.get(1)?.trim()
            ?: JOT_NOTE_WITH_CONTENT.matchEntire(trimmed)?.groupValues?.get(1)?.trim()
                ?.takeUnless { it.equals("esto", ignoreCase = true) }
            ?: WRITE_NOTE_WITH_CONTENT.matchEntire(trimmed)?.groupValues?.get(1)?.trim()
                // «esto» es la pelada de dictado; «de» es el conector pelado
                // («hazme una nota de»): ambos deben caer a la guía honesta,
                // nunca crear una nota literal «esto»/«de».
                // c.989 (delta de la COLISIÓN c.988/c.988): «mental» a secas
                // es el modismo «haz(se) una nota mental» (= recuérdalo tú),
                // NUNCA nota basura «mental» — sanea también las hermanas
                // «haz/escribe una nota mental» de c.974 (medido en la sonda
                // /tmp/probe988/HazmeNotaProbe.kt: CREATE_NOTE payload
                // «mental» preexistente).
                ?.takeUnless {
                    it.equals("esto", ignoreCase = true) ||
                        it.equals("de", ignoreCase = true) ||
                        it.equals("mental", ignoreCase = true)
                }
            ?: DICTATE_NOTE_WITH_CONTENT.matchEntire(trimmed)?.groupValues?.get(1)?.trim()
            ?: MELO_NOTE_WITH_CONTENT.matchEntire(trimmed)?.groupValues?.get(1)?.trim()
            ?: LO_NOTE_WITH_CONTENT.matchEntire(trimmed)?.groupValues?.get(1)?.trim()
        return if (content.isNullOrEmpty()) {
            AssistantAnswer("¿Qué quieres anotar? Escríbelo tras «tomar nota: » o «guardar como nota: » y la guardo.")
        } else {
            AssistantAnswer("La nota está lista para guardarse: “${content.take(120)}”.", AssistantAction.CREATE_NOTE, content)
        }
    }

    // c.987: «recuérdame <contenido>» — captura de TAREA hermana de la
    // familia de notas. El [ée] cubre la escritura móvil sin tilde; el «:»
    // opcional la simetría con notas («recuérdame: sacar al perro»). El
    // ancla ^ hace disjuntas las formas no imperativas: «no me recuerdes
    // nada», «recuerdo la tarea de ayer» y «el recuerdo llegó ayer» nunca
    // entran en la rama.
    private val REMIND_ME_PREFIX = Regex("(?i)^recu[ée]rdame(?:\\s*:)?(?:\\s+|$)")
    // c.992: ([^:].*) en lugar de (.+) — la pelada CON «:» («recuérdame:»)
    // creaba tarea BASURA «:» (el (.+) se tragaba el propio «:» por
    // backtracking al ser opcional). NUNCA tarea basura (doctrina c.969;
    // simetría con el extractor c.990 de createTaskCapture).
    private val REMIND_ME_WITH_CONTENT = Regex("(?i)^recu[ée]rdame\\s*:?\\s*([^:].*)$")

    // c.993: «que» subordinado inicial («recuérdame que tengo que…») —
    // conector, NO contenido (medido: residuo en el título). Despoje
    // ANTES de los checks: la pelada-con-«que» («recuérdame que») cae a
    // la guía honesta (NUNCA tarea basura «que», doctrina c.988) y la
    // negación tras «que» («…que no llame…») llega al check anti-overreach
    // (NUNCA capturar lo contrario de la intención). «quedarme» (sin
    // espacio) y «qué» (con tilde) NUNCA casan.
    private val LEADING_QUE = Regex("(?i)^que(?:\\s+|$)")

    private fun remindMeCapture(clean: String): AssistantAnswer? {
        val trimmed = clean.trim()
        if (!REMIND_ME_PREFIX.containsMatchIn(trimmed)) return null
        val raw = REMIND_ME_WITH_CONTENT.matchEntire(trimmed)?.groupValues?.get(1)?.trim()
        val content = raw?.let { LEADING_QUE.replace(it, "") }?.trim()
        if (content.isNullOrEmpty()) {
            // NUNCA tarea vacía (hermana de la nota pelada c.969): guía
            // honesta de cómo dictarlo, SIN acción.
            return AssistantAnswer("¿Qué quieres que te recuerde? Escríbelo tras «recuérdame …» y lo guardo como tarea.")
        }
        // Anti-overreach: «recuérdame NO llamar…» pide no hacerlo; crear la
        // tarea capturaría lo contrario de la intención (falso positivo
        // grave). Menú honesto.
        if (content.startsWith("no ", ignoreCase = true)) return null
        return AssistantAnswer("La tarea está lista para guardarse: “${content.take(120)}”.", AssistantAction.CREATE_TASK, content)
    }

    // c.990: lateral (a) de la sonda persistente de creación de tareas
    // (tools/probe/AssistantTaskCreationProbe.kt) — «crea/añade/agrega (una)
    // tarea…», el imperativo explícito de tarea. Hermana de
    // remindMeCapture: mismo contrato (guía honesta pelada, NUNCA tarea
    // vacía ni basura). La palabra «tarea» es obligatoria: «crea una
    // tabla» no es una tarea. Ancla ^: «quiero crear una tarea» queda
    // fuera (medido en la sonda efímera PRE — anti-overreach, UNA forma
    // por ciclo). Verbos: crea/crear, añade/añadir, agrega/agregar;
    // artículo «una» y «:» opcionales; «tareas» admite la «s».
    private val CREATE_TASK_PREFIX =
        Regex("(?i)^(?:crea(?:r)?|a[ñn]ad(?:e|ir)|agrega(?:r)?)\\s+(?:una\\s+)?tareas?(?:\\s*:)?(?:\\s+|$)")
    private val CREATE_TASK_WITH_CONTENT =
        Regex("(?i)^(?:crea(?:r)?|a[ñn]ad(?:e|ir)|agrega(?:r)?)\\s+(?:una\\s+)?tareas?\\s*:?\\s*([^:].*)$")

    private fun createTaskCapture(clean: String): AssistantAnswer? {
        val trimmed = clean.trim()
        if (!CREATE_TASK_PREFIX.containsMatchIn(trimmed)) return null
        val content = CREATE_TASK_WITH_CONTENT.matchEntire(trimmed)?.groupValues?.get(1)?.trim()
        // Conector pelado «de» (doctrina c.988, hermano del «esto» de
        // takeNoteCapture): «crea una tarea de» no crea basura.
        if (content.isNullOrEmpty() || content.equals("de", ignoreCase = true)) {
            // NUNCA tarea vacía (hermana de la nota pelada c.969): guía
            // honesta de cómo dictarlo, SIN acción.
            return AssistantAnswer("¿Qué tarea quieres crear? Escríbela tras «crea una tarea: …» y la guardo.")
        }
        return AssistantAnswer("La tarea está lista para guardarse: “${content.take(120)}”.", AssistantAction.CREATE_TASK, content)
    }

    // c.994: lateral (b1) de la sonda persistente — «avísame…», el
    // recordatorio declarativo. Hermana de remindMeCapture (mismo
    // contrato: guía honesta pelada, NUNCA tarea vacía; negación → menú
    // honesto). El «de» preposicional se despoja y el temporal
    // intercalado («avísame MAÑANA DE llamar…») se reordena al final
    // del payload para que NaturalTaskParser ancle la fecha con título
    // limpio (medido PRE: payload «mañana de llamar…» dejaba residuo).
    private val AVISA_ME_PREFIX = Regex("(?i)^av[íi]same(?:\\s*:)?(?:\\s+|$)")
    private val AVISA_ME_WITH_CONTENT =
        Regex("(?i)^av[íi]same\\s*:?\\s*(?:(pasado mañana|mañana|hoy)\\s+)?(?:de\\s+)?([^:].*)$")

    private fun avisaMeCapture(clean: String): AssistantAnswer? {
        val trimmed = clean.trim()
        if (!AVISA_ME_PREFIX.containsMatchIn(trimmed)) return null
        val match = AVISA_ME_WITH_CONTENT.matchEntire(trimmed)
        val temporal = match?.groupValues?.get(1)?.trim().orEmpty()
        val content = match?.groupValues?.get(2)?.trim()
        if (content.isNullOrEmpty()) {
            // NUNCA tarea vacía (doctrina c.969): guía honesta SIN acción.
            return AssistantAnswer("¿Sobre qué quieres que te avise? Escríbelo tras «avísame …» y lo guardo como tarea.")
        }
        // Anti-overreach: la negación tras «de» («avísame de NO llamar…»)
        // pide no hacerlo; crear la tarea capturaría lo contrario de la
        // intención (hermana de c.986/c.993). Menú honesto.
        if (content.startsWith("no ", ignoreCase = true)) return null
        // Anti-overreach: «avísame CUANDO llegue Ana» condiciona el aviso
        // a un evento que no podemos programar honestamente → menú.
        if (content.startsWith("cuando ", ignoreCase = true)) return null
        val payload = if (temporal.isEmpty()) content else "$content $temporal"
        return AssistantAnswer("La tarea está lista para guardarse: “${payload.take(120)}”.", AssistantAction.CREATE_TASK, payload)
    }

    // c.995: lateral (b2) de la sonda persistente — «quiero que me
    // recuerdes…», el recordatorio envuelto. Hermana de remindMeCapture
    // (mismo contrato: guía honesta pelada, NUNCA tarea vacía; negación
    // → menú honesto). El extractor ([^:].*) es simétrico a c.992:
    // NUNCA tarea basura «:». El ancla ^ hace disjuntas la negación
    // previa («no quiero que…») y el pasado («quería que me
    // recordaras…», otra persona y otro tiempo).
    private val QUIERO_QUE_PREFIX = Regex("(?i)^quiero que me recuerdes(?:\\s*:)?(?:\\s+|$)")
    private val QUIERO_QUE_WITH_CONTENT =
        Regex("(?i)^quiero que me recuerdes\\s*:?\\s*([^:].*)$")

    private fun quieroQueRecuerdesCapture(clean: String): AssistantAnswer? {
        val trimmed = clean.trim()
        if (!QUIERO_QUE_PREFIX.containsMatchIn(trimmed)) return null
        val content = QUIERO_QUE_WITH_CONTENT.matchEntire(trimmed)?.groupValues?.get(1)?.trim()
        if (content.isNullOrEmpty()) {
            // NUNCA tarea vacía (doctrina c.969): guía honesta SIN acción.
            return AssistantAnswer("¿Qué quieres que te recuerde? Escríbelo tras «quiero que me recuerdes …» y lo guardo como tarea.")
        }
        // Anti-overreach: el contenido negado («quiero que me recuerdes
        // NO llamar…») pide no hacerlo; crear la tarea capturaría lo
        // contrario de la intención (hermana de c.986/c.993/c.994).
        if (content.startsWith("no ", ignoreCase = true)) return null
        return AssistantAnswer("La tarea está lista para guardarse: “${content.take(120)}”.", AssistantAction.CREATE_TASK, content)
    }

    // c.996: lateral (d) de la sonda persistente — «recuérdamelo»
    // deíctico. El motor no tiene contexto conversacional para resolver
    // «lo», así que lo honesto es reconocer la forma y pedir el
    // contenido explícito: guía SIN acción (NUNCA tarea basura «lo»).
    // Ancla ^ con «recuérdamelo» cerrado: disjuntas la negación («no me
    // lo recuerdes») y el pasado («me lo recordó ayer»). El temporal
    // final («… mañana») se tolera en la forma pero no se puede guardar
    // sin saber QUÉ recordar — la guía es la respuesta honesta.
    private val REMIND_ME_LO =
        Regex("(?i)^recu[ée]rdamelo(?:\\s+(?:ma[ñn]ana|hoy|pasado ma[ñn]ana|esta (?:tarde|noche|ma[ñn]ana)))?\\.?$")

    private fun remindMeLoGuide(clean: String): AssistantAnswer? {
        if (!REMIND_ME_LO.matches(clean.trim())) return null
        // Guía honesta SIN acción: NUNCA tarea basura «lo» (doctrina c.969).
        return AssistantAnswer("No sé a qué se refiere «lo». Escríbeme qué quieres que te recuerde — por ejemplo «recuérdame llamar al banco» — y lo guardo como tarea.")
    }

    // c.991: «ponme un recordatorio …» — hermana de c.986, lateral (e) de la
    // sonda persistente AssistantTaskCreationProbe. El ancla ^ con «pon(me)»
    // imperativo hace disjuntas la negación («no me pongas recordatorios»),
    // el sustantivo pasado («el recordatorio sonó ayer») y la consulta
    // («qué recordatorios tengo», rama c.808 intacta). El «:» opcional da
    // simetría con notas («ponme un recordatorio: llamar al banco»).
    private val SET_REMINDER_PREFIX = Regex("(?i)^pon(?:me)?\\s+(?:un\\s+)?recordatorio(?:\\s|:|$)")
    private val SET_REMINDER_WITH_CONTENT = Regex("(?i)^pon(?:me)?\\s+(?:un\\s+)?recordatorio\\s*:?\\s*(.+)$")
    // El conector «para» («…recordatorio PARA mañana llamar al banco») se
    // despoja: medido con sonda que NaturalTaskParser extrae la fecha pero
    // dejaba el «para» de residuo en el título («para llamar al banco»).
    private val LEADING_PARA = Regex("(?i)^para\\s+")

    private fun setReminderCapture(clean: String): AssistantAnswer? {
        val trimmed = clean.trim()
        if (!SET_REMINDER_PREFIX.containsMatchIn(trimmed)) return null
        val content = SET_REMINDER_WITH_CONTENT.matchEntire(trimmed)?.groupValues?.get(1)?.trim()
            ?.replaceFirst(LEADING_PARA, "")
        return if (content.isNullOrEmpty()) {
            // NUNCA tarea vacía (doctrina c.969): guía honesta SIN acción.
            AssistantAnswer("¿Qué quieres que te recuerde? Escríbelo tras «ponme un recordatorio …» y lo guardo como tarea.")
        } else {
            AssistantAnswer("La tarea está lista para guardarse: “${content.take(120)}”.", AssistantAction.CREATE_TASK, content)
        }
    }

    private val CONTENT_TASK_SUBJECTS = setOf("tarea", "tareas", "pendiente", "pendientes")
    private val CONTENT_LEAD_ARTICLES = setOf("las", "los")
    private val CONTENT_CONNECTOR_ARTICLES = setOf("la", "las", "los", "el")

    /**
     * Calificador de contenido sobre la superficie de notas: «notas de la
     * reunión», «notas del trabajo», «mis notas de química». Hermano de
     * [isContentQualifiedTasksQuery] (c.792); el listado desnudo («notas»/
     * «mis notas»/«todas las notas») lo cubre [isNotesListingQuery] (c.793)
     * con payload canónico, así que aquí se exige conector + calificador.
     * Sin calificador tras el conector no rutea.
     */
    private val NOTE_SUBJECTS = setOf("nota", "notas")
    // c.969: «la» entra como artículo cabecera — «la nota de la reunión»
    // (nota concreta por contenido, descubrimiento de la sonda c.967) caía
    // al menú genérico mientras «las notas de física» ya ruteaba. La pelada
    // «la nota» NO se ve afectada: sin conector+calificador el guard la deja
    // pasar al listado (ENTITY_LISTING_FORMS, c.967).
    private val NOTE_LEAD_ARTICLES = setOf("las", "mis", "la")

    /**
     * Interrogativa con contenido cualificado (c.807): «qué notas tengo de
     * trabajo», «qué tareas tengo del proyecto casa», «qué pendientes tengo de
     * la uni». La afirmativa equivalente ya rutea por [isContentQualifiedNotesQuery]/
     * [isContentQualifiedTasksQuery]; la interrogativa caía al menú (GAP
     * documentado por la sonda c.803-b). Se reconstruye la frase afirmativa
     * (sujeto + conector + calificador, sin «qué … tengo») SOBRE el texto
     * original para conservar acentos en el payload. Exige calificador no
     * vacío tras el conector; la forma desnuda «qué notas tengo» la reclama
     * antes el listado simple (c.803-c) y el alcance temporal («de hoy») lo
     * reclama isAgendaQuery mucho antes en el despacho.
     */
    private val CONTENT_QUALIFIED_INTERROGATIVE_PATTERN =
        Regex("""(?i)^¿?qu[eé]\s+(notas?|tareas?|pendientes?)\s+tengo\s+((?:de|del)(?:\s+(?:la|las|los|el))?\s+\S.*)$""")

    private fun contentQualifiedInterrogativePayload(clean: String): String? {
        val match = CONTENT_QUALIFIED_INTERROGATIVE_PATTERN.matchEntire(clean.trim().trimEnd('?', '¿', ' ')) ?: return null
        val subject = match.groupValues[1]
        val qualifier = match.groupValues[2].trim()
        if (qualifier.isEmpty()) return null
        val payload = "$subject $qualifier"
        // Alcance temporal («qué tareas tengo de hoy»): la afirmativa
        // equivalente la reclama isAgendaQuery antes que la rama de contenido
        // (doctrina c.792), pero la interrogativa NO — el guard se aplica aquí
        // sobre el payload reconstruido para no secuestrarla como calificador.
        if (isAgendaQuery(payload.foldForSearch())) return null
        return payload
    }

    private fun isContentQualifiedNotesQuery(query: String): Boolean {
        val words = query.split(Regex("\\s+"))
        val subjectIdx = words.indexOfFirst { it in NOTE_SUBJECTS }
        if (subjectIdx < 0) return false
        if (subjectIdx > 1 || (subjectIdx == 1 && words[0] !in NOTE_LEAD_ARTICLES)) return false
        val rest = words.drop(subjectIdx + 1)
        if (rest.isEmpty() || (rest[0] != "de" && rest[0] != "del")) return false
        val afterConnector = rest.drop(1)
        if (afterConnector.isEmpty()) return false
        val qualifier = if (rest[0] == "de" && afterConnector[0] in CONTENT_CONNECTOR_ARTICLES) {
            afterConnector.drop(1)
        } else {
            afterConnector
        }
        return qualifier.isNotEmpty()
    }

    /**
     * Calificador de contenido de tareas: «tareas de la casa», «tareas del
     * proyecto», «pendientes de química». Sujeto (tarea(s)/pendiente(s)) con
     * artículo opcional («las/los/lo»), conector «de/del» + artículo opcional
     * («la/las/los/el»), y al menos una palabra de calificador. Se evalúa de
     * ÚLTIMA en el despacho; todos los vocabularios y el alcance temporal
     * (agenda) se resuelven antes, así sólo llega un calificador real.
     */
    private fun isContentQualifiedTasksQuery(query: String): Boolean {
        val words = query.split(Regex("\\s+"))
        val subjectIdx = words.indexOfFirst { it in CONTENT_TASK_SUBJECTS }
        if (subjectIdx < 0) return false
        if (subjectIdx > 1 || (subjectIdx == 1 && words[0] !in CONTENT_LEAD_ARTICLES)) return false
        val rest = words.drop(subjectIdx + 1)
        if (rest.isEmpty() || (rest[0] != "de" && rest[0] != "del")) return false
        val afterConnector = rest.drop(1)
        if (afterConnector.isEmpty()) return false
        val qualifier = if (rest[0] == "de" && afterConnector[0] in CONTENT_CONNECTOR_ARTICLES) {
            afterConnector.drop(1)
        } else {
            afterConnector
        }
        return qualifier.isNotEmpty()
    }
    // Listado simple de notas: "mis notas"/"todas las notas"/"las notas" — la
    // forma cotidiana de pedir ver las notas enteras. A diferencia de
    // "notas fijadas" (rama isPinnedNotesQuery) no pide un atributo, sólo la
    // lista. Se rutea por OPEN_SEARCH igual que c.788. Formas EXPLÍCITAS (no
    // tokens sueltos) para no secuestrar consultas ajenas: "notas de física"
    // (contenido) no es pedir la lista, y "guardar como nota"/"busca …" ya
    // se evalúan antes en el when.
    private val NOTES_LISTING_FORMS = setOf(
        "mis notas", "todas las notas", "todas mis notas",
        "las notas", "ver notas", "ver las notas", "lista de notas", "notas",
        // c.803-c: forma interrogativa («qué notas tengo») — hermana de «qué
        // tareas tengo». Mapa de formas exactas: «qué notas tengo de
        // trabajo» (cualificada) no entra — GAP abierto documentado.
        "que notas tengo"
    )
    private fun isNotesListingQuery(query: String): Boolean =
        query.trim() in NOTES_LISTING_FORMS

    // Listados de las familias restantes del buscador (c.795 — hermano de la
    // sonda c.793 de notas): formas EXPLÍCITAS (no tokens sueltos) para no
    // secuestrar consultas ajenas: "hábitos de lectura" (contenido) no es pedir
    // la lista — sigue al menú como antes.
    private val ENTITY_LISTING_FORMS: Map<String, String> = mapOf(
        // Hábitos
        "habitos" to "habitos", "mis habitos" to "habitos", "los habitos" to "habitos",
        "ver habitos" to "habitos", "ver los habitos" to "habitos",
        "todos los habitos" to "habitos", "todos mis habitos" to "habitos",
        "habito" to "habitos", "el habito" to "habitos", "mi habito" to "habitos",
        // Rutinas
        "rutinas" to "rutinas", "mis rutinas" to "rutinas", "las rutinas" to "rutinas",
        "ver rutinas" to "rutinas", "ver las rutinas" to "rutinas",
        "todas las rutinas" to "rutinas", "todas mis rutinas" to "rutinas",
        "rutina" to "rutinas", "la rutina" to "rutinas", "mi rutina" to "rutinas",
        // Proyectos
        "proyectos" to "proyectos", "mis proyectos" to "proyectos", "los proyectos" to "proyectos",
        "ver proyectos" to "proyectos", "ver los proyectos" to "proyectos",
        "todos los proyectos" to "proyectos", "todos mis proyectos" to "proyectos",
        "proyecto" to "proyectos", "el proyecto" to "proyectos", "mi proyecto" to "proyectos",
        // c.797: formas interrogativas (frase completa, no tokens sueltos).
        "que habitos tengo" to "habitos", "cuales son mis habitos" to "habitos",
        "cuales son los habitos" to "habitos",
        "que rutinas tengo" to "rutinas", "cuales son mis rutinas" to "rutinas",
        "cuales son las rutinas" to "rutinas",
        "que proyectos tengo" to "proyectos", "cuales son mis proyectos" to "proyectos",
        "cuales son los proyectos" to "proyectos",
        // c.798 (sonda AssistantHonestRouteProbe): la familia tareas seguía
        // sin formas de listing → «¿cuáles son mis tareas?» caía al menú
        // genérico. Payload «tareas» (paridad con wantsTasks del buscador).
        // c.798 p.3 (este run): formas «ver …» hermanas de las ya enrutadas.
        "tareas" to "tareas", "mis tareas" to "tareas", "las tareas" to "tareas",
        "ver tareas" to "tareas", "ver las tareas" to "tareas",
        "todas las tareas" to "tareas", "todas mis tareas" to "tareas",
        "tarea" to "tareas", "la tarea" to "tareas", "mi tarea" to "tareas",
        "que tareas tengo" to "tareas", "cuales son mis tareas" to "tareas",
        "cuales son las tareas" to "tareas",
        // c.966: familia automatizaciones (GAP medido por la sonda c.963 — el
        // buscador ya las lista desde c.964 con `wantsAutomations` +
        // `AUTOMATION_TERMS`; aquí faltaba el routing). «reglas» es el nombre
        // cotidiano de la misma familia (raíz de AUTOMATION_TERMS).
        "automatizaciones" to "automatizaciones", "mis automatizaciones" to "automatizaciones",
        "las automatizaciones" to "automatizaciones",
        "ver automatizaciones" to "automatizaciones", "ver las automatizaciones" to "automatizaciones",
        "todas las automatizaciones" to "automatizaciones", "todas mis automatizaciones" to "automatizaciones",
        "automatizacion" to "automatizaciones", "la automatizacion" to "automatizaciones",
        "mi automatizacion" to "automatizaciones",
        "reglas" to "automatizaciones", "mis reglas" to "automatizaciones",
        "las reglas" to "automatizaciones",
        "ver reglas" to "automatizaciones", "ver las reglas" to "automatizaciones",
        "todas las reglas" to "automatizaciones", "todas mis reglas" to "automatizaciones",
        "regla" to "automatizaciones", "la regla" to "automatizaciones",
        "mi regla" to "automatizaciones",
        "que automatizaciones tengo" to "automatizaciones",
        "cuales son mis automatizaciones" to "automatizaciones",
        "cuales son las automatizaciones" to "automatizaciones",
        "que reglas tengo" to "automatizaciones",
        "cuales son mis reglas" to "automatizaciones",
        "cuales son las reglas" to "automatizaciones",
        // c.967: paridad de notas singular/interrogativa/imperativa (P2
        // BACKLOG abierto desde c.963, sonda PRE 8/8 GAP). Las formas
        // plurales cotidianas («mis notas», «todas las notas») ya rutean por
        // la rama c.793 (NOTES_LISTING_FORMS, anterior en el despacho); aquí
        // se cubren las formas que allí faltan. Las muletillas sin artículo
        // («ensename mis notas», «quiero ver todas mis notas») rutean por la
        // vía de tokens; las que llevan artículo («la nota», «muestrame las
        // notas») necesitan forma explícita porque «el/la/las» no es ruido.
        "nota" to "notas", "la nota" to "notas", "mi nota" to "notas",
        "cuales son mis notas" to "notas", "cuales son las notas" to "notas",
        "muestrame las notas" to "notas", "ensename las notas" to "notas",
        // c.968: variantes con artículo de «quiero ver…»/«dime…» (hermanas
        // de las de arriba — «las» no es ruido). «quiero ver todas MIS
        // notas» ya rutea por tokens (c.967); «quiero ver todas LAS notas»
        // caía al menú (sonda PRE `/tmp/probe967/DeltaProbe.kt` 3/3 GAP).
        "quiero ver las notas" to "notas", "quiero ver todas las notas" to "notas",
        "dime las notas" to "notas"
    )
    private val ENTITY_LISTING_LABELS = mapOf(
        "habitos" to "los hábitos", "rutinas" to "las rutinas",
        "proyectos" to "los proyectos", "tareas" to "las tareas",
        "automatizaciones" to "las automatizaciones",
        "notas" to "las notas"
    )
    // Tokens de familia listable tolerados por el calificador «activo» y las
    // muletillas interrogativas («qué», «tengo», «hay»): «habitos activos» o
    // «que habito tengo activo» rutean AL MISMO bundle que la forma pelada en
    // vez de caer al menú genérico (c.797). Se ignora sólo cuando lo que queda
    // tras filtrar calificador+interrogativa es EXACTAMENTE un token de
    // familia; si hay contenido real («habitos activos lectura») no se rutea.
    private val ENTITY_LISTING_QUALIFIERS = setOf("activo", "activos", "activa", "activas")
    // c.801 (sonda extendida): prefijos imperativos de listado («ensename/muestrame/
    // dime mis tareas») son muletillas honestas del listado: la intención es la
    // misma que la forma interrogativa («cuáles son mis tareas», c.797).
    // c.813: deseo+verbo «quiero ver …» y cuantificador universal «todos/todas»
    // también son muletilla de listado: «quiero ver todas mis tareas» pide la
    // misma familia que «tareas» y caía al menú (GAP de la sonda c.812).
    private val ENTITY_LISTING_NOISE = setOf(
        "que", "tengo", "hay", "mis", "ensename", "muestrame", "dime",
        "quiero", "ver", "todos", "todas"
    )
    private val ENTITY_LISTING_TOKENS: Map<String, String> = mapOf(
        "habito" to "habitos", "habitos" to "habitos",
        "rutina" to "rutinas", "rutinas" to "rutinas",
        "proyecto" to "proyectos", "proyectos" to "proyectos",
        "tarea" to "tareas", "tareas" to "tareas",
        // c.966: familia automatizaciones (tokens para la ruta por
        // calificador/muletillas — «automatizaciones activas», «que reglas
        // tengo»); «regla(s)» pliega a la misma familia (AUTOMATION_TERMS).
        "automatizacion" to "automatizaciones", "automatizaciones" to "automatizaciones",
        "regla" to "automatizaciones", "reglas" to "automatizaciones",
        // c.967: notas en la vía de tokens (muletillas sin artículo —
        // «ensename mis notas», «quiero ver todas mis notas», «notas
        // activas»). Anti-colisión: el contenido («notas de física») y la
        // nota concreta («la nota de la reunión») dejan palabras fuera de
        // ruido/calificador/token → no rutean al bundle.
        "nota" to "notas", "notas" to "notas"
    )
    private fun entityListingPayload(query: String): String? {
        ENTITY_LISTING_FORMS[query.trim()]?.let { return it }
        val meaningful = query.trim().split(Regex("\\s+"))
            .filterNot { it in ENTITY_LISTING_QUALIFIERS || it in ENTITY_LISTING_NOISE }
        // Tras filtrar calificador/muletillas, deben quedar EXCLUSIVAMENTE
        // tokens de familia; si hay contenido real («habitos activos lectura»)
        // no se rutea al bundle.
        if (meaningful.isEmpty() || meaningful.any { it !in ENTITY_LISTING_TOKENS }) return null
        val distinct = meaningful.distinct()
        return if (distinct.size == 1) ENTITY_LISTING_TOKENS[distinct[0]] else null
    }

    // Forma sustantiva del verbo de búsqueda (c.796, residuo (f) de la sonda
    // c.793): «búsqueda de <operando>». Exige el conector «de» y un operando
    // no vacío; la forma «búsqueda de» sola no rutea (guarda hermana del
    // guarda «notas de» de c.794). El token se pliega sin acento por
    // `foldForSearch` en el despacho.
    private fun busquedaNounOperand(query: String): String? =
        if (query.startsWith("busqueda de ")) {
            query.removePrefix("busqueda de ").trim().takeIf { it.isNotEmpty() }
        } else null

    /**
     * Consulta de "lo próximo" sin alcance de fecha ("tengo algo pronto",
     * "¿qué tengo pronto?", "¿hay algo pronto?"). Token sin acento tras
     * `foldForSearch`. Va después de la agenda en el despacho, así las formas
     * con alcance explícito ("tengo algo pronto hoy") siguen resolviéndose
     * como agenda; no colisiona con entity-lookup ("cuándo/dónde/a qué hora")
     * ni con prioridad ("urgente"/"importante"), evaluadas por sus propios
     * marcadores.
     */
    private fun isUpcomingQuery(query: String): Boolean = "pronto" in query

    /**
     * Marca temporal futura más cercana de una tarea: su hueco (`startAt`) si
     * aún no llega; si no, su fecha límite (`dueAt`). Las marcas pasadas no son
     * "próximas" (lo vencido/olvidado lo cubren las ramas de recuperación) y
     * una tarea sin marca alguna no se puede ordenar honestamente → null.
     */
    private fun upcomingMarker(task: TaskEntity, now: Long): Long? =
        listOfNotNull(task.startAt, task.dueAt).filter { it > now }.minOrNull()

    /**
     * Etiqueta de cuándo cae una marca próxima: "hoy"/"mañana" relativas a la
     * zona del usuario, o la fecha formateada si cae más lejos. Sufijo de hora
     * sólo si la marca tiene hora real (medianoche = fecha sin hora, igual que
     * en entityLookupAnswer: no se finge una hora que no existe).
     */
    private fun upcomingWhenLabel(marker: Long, now: Long, zone: ZoneId): String {
        val date = Instant.ofEpochMilli(marker).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val day = when (date) {
            today -> "hoy"
            today.plusDays(1) -> "mañana"
            else -> "el ${DateRules.formatDate(marker)}"
        }
        return if (isMidnight(marker, zone)) day else "$day a las ${DateRules.formatTime(marker)}"
    }

    /**
     * Guarda de sobrecarga emocional: "abrumado/abrumada", "agobiado/agobiada"
     * (subcadena de género incluida) y la forma coloquial "no doy abasto".
     * Tokens sin acento tras `foldForSearch`. Familia deliberadamente pequeña
     * para no secuestrar otras ramas; colocada tras prioridad y entity-lookup
     * para no robar consultas que sí describen conjuntos/entidades.
     */
    private fun isOverwhelmedQuery(query: String): Boolean =
        "abrumad" in query || "agobiad" in query || "no doy abasto" in query

    /**
     * Petición de recomendación/decisión: "qué me recomiendas", "recomiéndame
     * algo", "ayúdame a decidir", "cuál me conviene hacer". Tokens sin acento
     * tras `foldForSearch` ("recomiend" cubre recomiendas/recomiéndame/
     * recomendación; "a decidir" no se acota más porque la superficie de
     * decisión ES esta rama — "qué cenar" no la casa por falta de "decidir...",
     * pero "decidir qué hacer" y "ayúdame a decidir" sí). Familia pequeña para
     * no robar otras ramas; va después de entity-lookup, prioridad, hueco
     * libre y sobrecarga por la misma razón de posición.
     */
    private fun isRecommendationQuery(query: String): Boolean =
        "recomiend" in query || "a decidir" in query || "me conviene" in query

    /**
     * Petición de posponer/defer: "¿qué puedo dejar para mañana/después?",
     * "¿puedo posponer algo?", "¿qué pasa si pospongo?". Tokens sin acento (ya
     * normalizados por `foldForSearch`). Conservadora y sin colisión:
     *  - NO es agenda ([isAgendaQuery]: "qué tengo"/"tengo para"/"hay algo") —
     *    aquí no aparece el verbo *tener/haber*.
     *  - NO es veredicto de carga ([isDayLoadQuery]: "voy bien"/"da tiempo") —
     *    "qué puedo posponer" no pregunta "¿cabe?" sino "¿cuál se mueve?".
     *  - NO es acción de mover una tarea concreta ("pospón la reunión") — la
     *    rama de modificación por entidad se evalúa antes y nombraría la tarea;
     *    aquí se pide el CANDIDATO entre las de hoy.
     */
    private fun isDeferralQuery(query: String): Boolean =
        "pospon" in query || "dejar para manana" in query || "dejar para despues" in query

    /**
     * Petición de la tarea más larga de hoy ("¿qué tarea es más larga?",
     * "¿qué tareas son más largas?"). Hermano de [isDeferralQuery]: la misma
     * piscina (pendiente de hoy) pero ordenada por duración planificable —
     * la «más larga» es la palanca real cuando el usuario evalúa su carga.
     * Conservador: «más larga» no casa con nada del vocabulario de agenda/
     * posponer/carga (ninguna string contiene «larga»).
     */
    private fun isLongestTaskQuery(query: String): Boolean =
        "mas larga" in query

    /**
     * Petición de recuento/listado honesto de pendientes ("¿cuántas tareas
     * tengo?", "¿cuántas pendientes tengo?"). Antes caía al menú genérico (los
     * dos GAPs restantes de la sonda AssistantHonestRouteProbe tras c.798).
     */
    // c.815 — paráfrasis natural del recuento/listado (residuo de la sonda):
    // «cosas que tengo pendientes» caía al menú aunque la intención ya tenía
    // ruta honesta (c.798). Formas EXACTAS de frase completa (no substrings)
    // para no secuestrar el calificador de contenido («que tengo pendientes
    // de química», rutas c.792/c.807) ni la agenda («que tengo pendiente hoy»).
    private val PENDING_PARAPHRASE_FORMS = setOf(
        "cosas que tengo pendientes", "cosas pendientes",
        "que tengo pendiente", "que tengo pendientes",
        "lo que tengo pendiente", "lo que tengo pendientes",
        "tengo cosas pendientes"
    )
    private fun isPendingCountQuery(query: String): Boolean =
        "cuantas tareas" in query || "cuantas pendientes" in query ||
            // c.801 (sonda extendida): forma de duración-restante honesta.
            "cuanto me falta" in query || "cuanto falta" in query ||
            query.trim() in PENDING_PARAPHRASE_FORMS

    /**
     * c.821 — recuperación de tareas olvidadas: «qué tareas tengo olvidadas»,
     * «tareas abandonadas/viejas/antiguas», «lo que siempre dejo para después»,
     * «lo más antiguo que tengo pendiente». Formas
     * EXACTAS plegadas (ya normalizadas por `foldForSearch`) para no secuestrar
     * interrogativas cualificadas por contenido («qué tareas tengo de química»
     * sigue en `contentQualifiedInterrogativePayload` → OPEN_SEARCH) ni el
     * recuento («cuántas tareas tengo» sigue en [isPendingCountQuery]).
     * Excluidas a propósito (ya tienen ruta honesta propia): «qué tengo
     * olvidado» (rama de olvido urgente [isMissedSlipQuery]: vencidas +
     * compromisos) y «qué llevo posponiendo» (rama de posponer
     * [isDeferralQuery]).
     */
    private val FORGOTTEN_PARAPHRASE_FORMS = listOf(
        "que tareas tengo olvidadas", "tareas olvidadas",
        "tareas abandonadas", "tareas viejas", "tareas antiguas",
        "lo que siempre dejo para despues", "que siempre dejo para despues",
        "que tareas llevan mas tiempo pendientes", "que tareas llevan mas tiempo pendiente",
        "lo mas antiguo que tengo pendiente", "lo mas viejo que tengo pendiente",
        "la tarea mas antigua que tengo", "la tarea mas vieja que tengo",
        "que tengo pendiente desde hace mucho", "mis tareas mas antiguas",
        "mis tareas mas viejas", "tareas mas antiguas", "tareas mas viejas"
    )

    private fun isForgottenQuery(query: String): Boolean =
        FORGOTTEN_PARAPHRASE_FORMS.any { query.trim() == it }

    // c.816 — recuento de rutinas: «cuantas rutinas tengo/hay» (las
    // rutinas llegan como parámetro de `answer`, hermano del recuento de
    // pendientes). Sólo el sustantivo cuantificado; el listado pelado
    // («rutinas», «mis rutinas») sigue en `entityListingPayload` (c.795).
    private fun isRoutineCountQuery(query: String): Boolean =
        "cuantas rutinas" in query

    /** La más larga del día por duración planificable; `null` si nada queda. */
    private fun longestTaskToday(tasks: List<TaskEntity>, now: Long, zone: ZoneId): TaskEntity? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val pending = tasks.filter { task ->
            task.parentTaskId == null && TaskRules.isActive(task) &&
                listOfNotNull(task.dueAt, task.startAt).any { epoch ->
                    Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate() == today
                }
        }
        // Duración descendente, id ascendente: empate resuelto determinista
        // por el id más bajo (paridad con los comparadores del dominio).
        return pending.sortedWith(
            compareByDescending<TaskEntity> { TaskRules.plannedDuration(it) }
                .thenBy { it.id }
        ).firstOrNull()
    }

    /**
     * Petición de tiempo invertido: "¿en qué gasto mi tiempo?", "¿en qué estoy
     * gastando tiempo?", "¿en qué se me va el tiempo?". Tokens sin acento (ya
     * normalizados por `foldForSearch`). Conservadora y sin colisión:
     *  - NO es hueco libre ([isFreeTimeQuery]: ancla "tengo") — aquí no hay
     *    "tengo tiempo" sino "gasto/gastando... tiempo".
     *  - NO es veredicto de carga ([isDayLoadQuery]: "cuánto tiempo me queda")
     *    — aquí se pregunta por el PASADO del día (invertido), no por lo que
     *    cabe. "gast" + "tiempo" no aparece en "me queda tiempo".
     *  - NO es duración estimada ("cuánto tarda X") — esa forma nombra una
     *    tarea/entidad y la rama de entidades se evalúa antes.
     */
    private fun isTimeSpentQuery(query: String): Boolean =
        ("gast" in query && "tiempo" in query) || "se me va el tiempo" in query

    /**
     * Señal de "tengo un hueco libre" (Cluster C c.677): formas sueltas
     * ("tengo tiempo/hueco/un rato (libre)") o con ventana explícita
     * ("tengo N minutos", "tengo veinte minutos", "tengo (una|media) hora").
     * Ancla "tengo" para no secuestrar otras ramas ("me queda tiempo",
     * "el tiempo que tengo", planificación "qué me queda por hacer" no la
     * casa; ver rama whatNow). "rapido"/"rapida" siguen en la rama rápida
     * (arriba en el despacho) — aquí SÓLO si NO es esa forma ("tengo un
     * rato para pensar rápido" no entra: contiene "rato" pero la rama de
     * arriba gana antes por posición; paridad positiva/negativa en tests).
     */
    private fun isFreeTimeQuery(query: String): Boolean {
        // c.798 (sonda AssistantHonestRouteProbe): formas invertidas y
        // plurales («¿qué tiempo tengo?», «tiempos libres hoy», «horario
        // libre») — antes sólo «tengo tiempo/hueco/rato» ruteaba y esas
        // formas cotidianas caían al menú genérico.
        val bareForm = "tengo tiempo" in query || "tiempo tengo" in query ||
            "tengo hueco" in query || "tengo un hueco" in query ||
            "tengo un rato" in query || "tiempos libres" in query ||
            // c.802 (hermano): «me queda poco tiempo» hermano cotidiano del hueco.
            "me queda poco tiempo" in query || "queda poco tiempo" in query ||
            // c.803 (sonda AssistantOverdueImportanceProbe): plural invertido
            // de «tengo hueco» («¿qué huecos tengo hoy?»), hermano de
            // «tiempo tengo»/«horario tengo libre». Caía al menú pese a la
            // rama existente.
            "huecos tengo" in query ||
            "horario libre" in query || "horario tengo libre" in query
        return bareForm || freeTimeWindowMinutes(query) != null
    }

    private val freeTimeWindowRegex = Regex(
        """tengo\s+(?:un\s+|una\s+)?(\d{1,3}|diez|doce|quince|veinte|treinta|cuarenta|cincuenta|sesenta)\s+(minutos?|horas?)"""
    )

    private val freeTimeWordMinutes = mapOf(
        "diez" to 10, "doce" to 12, "quince" to 15, "veinte" to 20,
        "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50, "sesenta" to 60
    )

    /**
     * Ventana explícita → minutos, o null si el usuario no declaró ninguna.
     * Dígito o palabra + "minuto(s)" → esos minutos; + "hora(s)" → ×60.
     * "media hora" → 30; "una hora" → 60. Nunca inventa una unidad donde no
     * se declaró ninguna: las formas sueltas ("un rato", "tiempo") caen al
     * QUICK_TASK_WINDOW_MINUTES de la rama rápida (paridad c.416).
     */
    private fun freeTimeWindowMinutes(query: String): Int? {
        if ("media hora" in query) return 30
        if ("una hora" in query && freeTimeWindowRegex.find(query) == null) return 60
        val m = freeTimeWindowRegex.find(query) ?: return null
        val value = m.groupValues[1].toIntOrNull() ?: freeTimeWordMinutes[m.groupValues[1]] ?: return null
        val isHour = m.groupValues[2].startsWith("hora")
        val total = if (isHour) value * 60 else value
        return total.takeIf { it > 0 }
    }

    /**
     * Respuesta de logro para "¿qué hice hoy/ayer/anteayer?" y para períodos
     * ("¿qué completé esta semana/este mes?") y los pasados ("¿qué hice la
     * semana pasada?"/"¿qué completé el mes pasado?"). Reusa el MISMO predicado
     * canónico que `TaskRules.completedTodayCount` (raíces, `status==COMPLETED`,
     * `!archived`, `!CANCELLED`) y los MISMOS límites calendario que
     * `SearchEngine.anchorMatchesScope` (semana lun→dom y mes natural, vía
     * `DateRules.calendarWeekRange`/`calendarMonthRange`/`calendarLastWeekRange`
     * /`calendarLastMonthRange` — fuente única de verdad, simétrica con los
     * scopes LAST_WEEK/LAST_MONTH de la búsqueda). No es una segunda fuente:
     * aplica el mismo filtro canónico en el rango del período pedido. Antes
     * "esta semana"/"este mes" caían a HOY y silenciaban lo terminado el lunes o
     * a principios de mes; y "la semana pasada"/"el mes pasado" caían a ESTE
     * período (mentira por omisión del logro previo, justo lo que la búsqueda SÍ
     * recuperaba: el asistente decía una cosa, la búsqueda otra). Lista los
     * títulos ordenados por `completedAt` desc (lo más reciente primero) y
     * nombra hasta 3; el resto se resume como recuento. Sin nueva pantalla ni
     * botón. Determinista y local (sin IA fingida).
     */
    private fun completedAnswer(
        query: String,
        tasks: List<TaskEntity>,
        now: Long,
        zone: ZoneId
    ): AssistantAnswer {
        // Forma adjetival SIN ancla temporal ("tareas completadas", "mis tareas
        // hechas"): filtrar por HOY mentiría por omisión ("Hoy no has
        // completado..." pese a logros de otros días) y filtrar por un período
        // arbitrario inventaría un alcance que el usuario no pidió. La lectura
        // honesta de la forma adjetival sin fecha es "mis logros recientes":
        // se listan sin filtro de fecha (mismo predicado canónico, orden desc
        // por completedAt). Con ancla ("…la semana pasada") se usa el período
        // exactamente como las formas verbales ("qué completé").
        val adjectiveNoAnchor = isCompletedAdjectiveQuery(query) &&
            "semana" !in query && "mes" !in query && "ayer" !in query && "hoy" !in query
        val today = DateRules.toLocalDate(now, zone)
        // "anteayer" contiene el substring "ayer": debe evaluarse ANTES que "ayer".
        // "semana"/"mes" se evalúan antes que los días: un período desplaza la fecha.
        // Los modificadores de pasado ("pasada"/"última"…) se evalúan ANTES que el
        // "semana"/"mes" puro: "la semana pasada" debe ir al período anterior, no al
        // en curso (simétrico a SearchEngine LAST_WEEK/LAST_MONTH).
        // Split por período, NO un superset único: la semana es femenina
        // ("última semana" = pasado) pero "último día de la semana" NO lo es
        // (habla del último DÍA de ESTA semana), así que "ultimo"/"ultimos" se
        // excluyen de la rama de semana — exactamente como
        // SearchEngine.LAST_WEEK_TOKENS, que los excluye a propósito. El mes sí
        // los admite ("el último mes"), igual que SearchEngine.LAST_MONTH_TOKENS.
        // Sin este split el asistente y la búsqueda discrepaban: "el último día
        // de la semana" caía a "La semana pasada" y silenciaba el logro de hoy.
        val pastWeek = "semana" in query && LAST_WEEK_RECAP_MODIFIERS.any { it in query }
        val pastMonth = "mes" in query && LAST_MONTH_RECAP_MODIFIERS.any { it in query }
        val (label, inRange) = when {
            pastWeek -> {
                val (s, e) = DateRules.calendarLastWeekRange(today)
                "La semana pasada" to ({ d: LocalDate -> !d.isBefore(s) && !d.isAfter(e) })
            }
            "semana" in query -> {
                val (s, e) = DateRules.calendarWeekRange(today)
                "Esta semana" to ({ d: LocalDate -> !d.isBefore(s) && !d.isAfter(e) })
            }
            pastMonth -> {
                val (s, e) = DateRules.calendarLastMonthRange(today)
                "El mes pasado" to ({ d: LocalDate -> !d.isBefore(s) && !d.isAfter(e) })
            }
            "mes" in query -> {
                val (s, e) = DateRules.calendarMonthRange(today)
                "Este mes" to ({ d: LocalDate -> !d.isBefore(s) && !d.isAfter(e) })
            }
            "anteayer" in query -> "Anteayer" to ({ d: LocalDate -> d == today.minusDays(2) })
            "ayer" in query -> "Ayer" to ({ d: LocalDate -> d == today.minusDays(1) })
            else -> "Hoy" to ({ d: LocalDate -> d == today })
        }
        // Raíces completadas (mismo predicado canónico que
        // TaskRules.completedTodayCount): sin subtareas, archivadas o canceladas.
        val completedRoots = tasks
            .asSequence()
            .filter { it.parentTaskId == null }
            .filter { it.status == TaskStatus.COMPLETED }
            .filterNot { it.archived }
            .filterNot { it.status == TaskStatus.CANCELLED }
            .filter { it.completedAt != null }
            .sortedByDescending { it.completedAt ?: 0L }
            .toList()
        // Sin ancla temporal en la forma adjetival: logros recientes, sin
        // filtro de fecha (la mentira por omisión "Hoy no has completado" quita
        // el acceso al logro de otros días; el período inventado, también).
        if (adjectiveNoAnchor) {
            return when {
                completedRoots.isEmpty() -> AssistantAnswer(
                    "Aún no tienes tareas completadas.",
                    AssistantAction.NONE
                )
                completedRoots.size <= 3 -> AssistantAnswer(
                    "Has completado ${completedRoots.size}: " +
                        completedRoots.joinToString(", ") { "«${it.title}»" } + ".",
                    AssistantAction.NONE
                )
                else -> {
                    val shown = completedRoots.take(3).joinToString(", ") { "«${it.title}»" }
                    AssistantAnswer(
                        "Has completado ${completedRoots.size}; las más recientes: $shown y ${completedRoots.size - 3} más.",
                        AssistantAction.NONE
                    )
                }
            }
        }
        val done = completedRoots
            .mapNotNull { t ->
                val at = t.completedAt ?: return@mapNotNull null
                if (inRange(DateRules.toLocalDate(at, zone))) t else null
            }
        return when {
            done.isEmpty() -> AssistantAnswer(
                "$label no has completado tareas todavía.",
                AssistantAction.NONE
            )
            done.size <= 3 -> AssistantAnswer(
                "$label completaste ${done.size}: " + done.joinToString(", ") { "«${it.title}»" } + ".",
                AssistantAction.NONE
            )
            else -> {
                val shown = done.take(3).joinToString(", ") { "«${it.title}»" }
                AssistantAnswer(
                    "$label completaste ${done.size}: $shown y ${done.size - 3} más.",
                    AssistantAction.NONE
                )
            }
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
    /**
     * Búsqueda puntual de una entidad conocida: «a qué hora tengo/pago <X>?,
     * «cuándo tengo/pago <X>?, «dónde tengo <X>?. El usuario nombra una tarea
     * concreta cuyo dato (hora/fecha) ya vive en su lista; antes caía al menú
     * genérico pese a ser una consulta directa. Recuperación de información: el
     * asistente CONOCE la respuesta, no la inventa. Tokens sin acento (ya
     * normalizados por foldForSearch). Excluye el verbo «hacer» (what-now) y los
     * tiempo-scopes de agenda («hoy»/«mañana»/«semana»/«mes»/«finde») para no
     * secuestrar esas ramas, que se evalúan antes y describen conjuntos, no una
     * entidad puntual con verbo de consulta (a qué hora / cuándo / dónde).
     */
    private fun isEntityLookupQuery(query: String): Boolean {
        val isWhenTime = "a que hora" in query || "cuando" in query || "que dia" in query || "que fecha" in query
        val isWhere = "donde" in query
        if (!(isWhenTime || isWhere)) return false
        // Evita capturar agenda («¿qué tengo mañana?»/«…esta semana?») y what-now
        // («¿qué hago ahora?»): esos scopes describen conjuntos y ya se rutean arriba.
        if (isAgendaQuery(query)) return false
        if ("que hago" in query || "que hago ahora" in query) return false
        return true
    }

    /**
     * Responde la hora/fecha de la entidad preguntada, buscando coincidencia por
     * título (normalizada con foldForSearch, misma fuente que SearchEngine).
     * Honestidad: si hay varias coincidencias las nombra para desambiguar (no
     * elige una a ciegas); si no encuentra ninguna lo dice (no inventa). La marca
     * de reloj sale de startAt si existe, si no de dueAt; un vencimiento a
     * medianoche significa «solo fecha, sin hora» —no se muestra «00:00» como si
     * fuese una hora real. Reusa DateRules.formatTime/formatDate (fuente única
     * compartida con la UI y las notificaciones). Determinista y local.
     */
    private fun entityLookupAnswer(
        query: String,
        tasks: List<TaskEntity>,
        now: Long,
        zone: ZoneId
    ): AssistantAnswer {
        val needle = extractEntityNeedle(query) ?: return AssistantAnswer(
            "No encuentro a qué te refieres. Prueba «a qué hora tengo la reunión» o «cuándo pago la luz»."
        )
        val matches = tasks.filter { it.title.foldForSearch().contains(needle) }
        if (matches.isEmpty()) {
            return AssistantAnswer("No encuentro nada que sea «$needle» entre tus tareas.")
        }
        if (matches.size > 1) {
            val titles = matches.take(3).joinToString(", ") { "«${it.title}»" }
            val extra = if (matches.size > 3) " y ${matches.size - 3} más" else ""
            return AssistantAnswer(
                "Tienes varias que pueden serlo: $titles$extra. ¿Cuál de ellas?",
                relatedTaskIds = matches.take(3).map { it.id }
            )
        }
        val task = matches.single()
        val isTimeQuery = "a que hora" in query
        // Marca de reloj: startAt prioritario (slot agendado); si no, dueAt. Si
        // ninguno tiene hora útil, se dice «sin hora fija» en vez de fingir.
        val clock = task.startAt ?: task.dueAt
        val hasClockTime = clock != null && !isMidnight(clock, zone)
        return when {
            isTimeQuery && hasClockTime -> AssistantAnswer(
                "«${task.title}» está a las ${DateRules.formatTime(clock)}.",
                relatedTaskIds = listOf(task.id)
            )
            isTimeQuery -> AssistantAnswer(
                "«${task.title}» no tiene una hora fija; está para el ${DateRules.formatDate(task.dueAt ?: task.startAt)}.",
                relatedTaskIds = listOf(task.id)
            )
            else -> {
                val dateRef = task.dueAt ?: task.startAt
                if (dateRef != null) {
                    val whenText = if (task.startAt != null && hasClockTime) {
                        "el ${DateRules.formatDate(task.startAt)} a las ${DateRules.formatTime(task.startAt)}"
                    } else {
                        "el ${DateRules.formatDate(dateRef)}"
                    }
                    AssistantAnswer("«${task.title}» está $whenText.", relatedTaskIds = listOf(task.id))
                } else {
                    AssistantAnswer(
                        "«${task.title}» no tiene fecha asignada todavía.",
                        relatedTaskIds = listOf(task.id)
                    )
                }
            }
        }
    }

    /**
     * Extrae el término de la entidad del enunciado de consulta (lo que sigue al
     * verbo). foldForSearch NO quita signos («¿», «?», «¡»), solo acentos/case,
     * así que el enunciado puede venir como «¿a que hora tengo la reunion?».
     * Se localiza el marcador (no exige startsWith), se descartan signos y se
     * recorta puntuación final. Mantiene el orden de markers (más específicos
     * primero) para que «a que hora tengo la» se case antes que «a que hora ».
     */
    private fun extractEntityNeedle(query: String): String? {
        val markers = listOf(
            "a que hora tengo el ", "a que hora tengo la ", "a que hora tengo ",
            "a que hora es el ", "a que hora es la ", "a que hora es ",
            "a que hora ",
            "cuando tengo el ", "cuando tengo la ", "cuando tengo ",
            "cuando pago el ", "cuando pago la ", "cuando pago ",
            "cuando ",
            "que dia tengo ", "que dia es ", "que dia ",
            "que fecha tengo ", "que fecha es ", "que fecha ",
            "donde tengo el ", "donde tengo la ", "donde tengo ",
            "donde es el ", "donde es la ", "donde es ", "donde "
        )
        for (m in markers) {
            val idx = query.indexOf(m)
            if (idx >= 0) {
                val rest = query.substring(idx + m.length)
                    .trim(' ', '¡', '¿', '?', '.', ',', '!', ':')
                    .trim()
                return rest.takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun isMidnight(epochMillis: Long, zone: ZoneId): Boolean {
        val t = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
        return t == LocalTime.MIDNIGHT
    }

    private fun isAgendaQuery(query: String): Boolean {
        // Marcadores verbales ("qué tengo"/"qué hay"/...) o la forma desnuda
        // "tareas de/del ..." (c.783: la búsqueda ya la entendía vía DateRules y el
        // asistente la mandaba al menú — gap (iv) sonda c.779). El alcance temporal
        // se exige abajo igualmente: "tareas de matemáticas" NO se secuestra (guardia).
        if (!("que tengo" in query || "tengo para" in query || "que hay" in query ||
                "tengo algo" in query || "hay algo" in query ||
                // c.801: imperativo «dime que ...» + scope temporal → agenda
                // (hermana de «dime que hacer» cuya rama What Now emplea la guarda
                // de scope negada; con scope, cae aquí y no al menú).
                "dime que" in query ||
                // c.802: marcadores de expectativa «que viene»/«que me espera» —
                // piden la agenda de un scope («que viene esta semana», «que me
                // espera hoy»). Sin scope, no rutean (la guarda temporal de abajo
                // lo exige; «que viene despues» — sin scope — sigue a What Now).
                "que viene" in query || "que me espera" in query ||
                // c.803-b (sonda DiscoveryRound): panorama semanal «¿cómo va mi
                // semana?» / «resumen de la semana» — hermano de «que viene esta
                // semana». La guarda exige «semana»: «¿cómo va el proyecto?» o
                // «resumen del libro» siguen al menú (sin scope honesto).
                // c.814: variante «de que va» (sonda efímera c.813 residual) —
                // la guarda «semana» también la protege («de que va el
                // proyecto» sigue al menú).
                ("como va" in query && "semana" in query) ||
                ("de que va" in query && "semana" in query) ||
                ("resumen" in query && "semana" in query) ||
                // c.817 (sonda efímera c.812 residual): forma cotidiana «tengo que
                // hacer algo <scope>» («…en la mañana», «…mañana») — los demás
                // marcadores exigen «que tengo»/«tengo algo» y esta paráfrasis
                // caía al menú pese a que la agenda por franja ya existe. La
                // guarda de alcance temporal de abajo la protege: «tengo que
                // hacer algo» sin scope sigue al menú (¿agenda de qué día?).
                "hacer algo" in query ||
                // c.820: hermana residual de c.817 — el usuario también suelta el
                // pronombre solo: «algo en la mañana», «¿algo esta noche?»,
                // «algo para hoy». Por PALABRA y con la misma guarda de alcance
                // de abajo: «algo de matemáticas» sigue al menú honesto.
                BARE_ALGO_TOKEN.containsMatchIn(query) ||
                BARE_TEMPORAL_TASK_CONNECTORS.any { it in query })) return false
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
        // Modificadores delegados a DateRules (fuente única compartida con
        // SearchEngine y el recap, anti-drift). "semana" se exige fuera del set:
        // "pasado" masculino se acepta aquí por paridad con la búsqueda (frase
        // gramaticalmente irregular "semana pasado" no debe caer a esta semana).
        val isNextWeek = "proxima" in query || "proximas" in query || "viene" in query && "semana" in query
        val isLastWeek = "semana" in query && DateRules.LAST_WEEK_MODIFIERS.any { it in query }
        val isNextMonth = "mes" in query && ("proximo" in query || "proximos" in query ||
            "proxima" in query || "proximas" in query || "viene" in query)
        val isLastMonth = "mes" in query && DateRules.LAST_MONTH_MODIFIERS.any { it in query }
        val weekdayTarget = resolveAgendaWeekday(query, today)
        // Plural ("¿qué tengo los viernes?"): las N fechas EXACTAS del weekday
        // objetivo en vez de sólo la siguiente. Se calcula antes del `when` para
        // poder ramificar antes que el weekday singular (la consulta plural también
        // casaría el token suelto y devolvería un único día). La membresía se
        // evalúa por las fechas discretas (no un intervalo continuo): evita mezclar
        // otro día de la semana que caiga dentro del horizonte. Ver
        // [pluralWeekdayDates] y [isScheduledInDates].
        val pluralDates = pluralWeekdayDates(query, today)
        val (start, end, label) = when {
            // "esta mañana" (demostrativo): la mañana (franja 6-11) de HOY, jamás
            // tomorrow. El "esta" desambigua el token "mañana", igual que en el
            // parser de captura, el motor de contexto (hoy 09:00) y SearchEngine
            // (DateScope.MORNING). Va ANTES que "pasado mañana"/"mañana": sin esta
            // rama, "¿qué tengo esta mañana?" mostraba la agenda de MAÑANA (mentira
            // cruzada con la captura). La franja horaria la aplica la etapa `band`.
            "esta manana" in query -> Triple(today, today, "esta mañana")
            // "en la mañana"/"por la mañana": la mañana de HOY (la franja 6-11 la
            // aplica la etapa `band` vía agendaPartOfDay), jamás tomorrow — ver
            // [enLaMananaHoy]. Va ANTES de "manana": sin esta rama el token suelto
            // robaba "¿qué tengo en la mañana?" a la agenda de MAÑANA. Se segmenta
            // del weekday y del finde para que "el viernes en la mañana" resuelva
            // al viernes (franja como modificador) y no a hoy.
            enLaMananaHoy(query) && AGENDA_WEEKDAY_TOKENS.none { it in query } &&
                !isAgendaWeekendQuery(query) -> Triple(today, today, "esta mañana")
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
            // Día de la semana en plural ("¿qué tengo los viernes?"/"¿qué tengo
            // los lunes?"): los weekday españoles son invariables en plural, así
            // que antes "los viernes" casaba el token suelto y devolvía SOLO el
            // próximo viernes — el usuario que pregunta por el patrón recurrente
            // no veía sus compromisos de los viernes siguientes (p. ej. uno
            // quincenal caía siempre invisible) y podía olvidar lo que vino a
            // planificar. Ahora resuelve un rango de los próximos N viernes
            // (mismo horizonte que "este mes" ≈ 4 semanas), reusando la maquinaria
            // de rango existente (isScheduledInRange) — sin nueva pantalla ni
            // botón. Va ANTES que el weekday singular (que también casaría el
            // token). Inclusivo salvo modificador "próximo"/"que viene" (salta hoy
            // si hoy es ese día), simétrico con el singular. Etiqueta honesta:
            // "los próximos viernes" (no afirma ver TODOS los viernes para siempre).
            pluralDates != null -> {
                // start..end = primera..última fecha objetivo (para la franja
                // horaria, que sólo aplica a tareas ya confirmadas en estas fechas).
                Triple(pluralDates.first(), pluralDates.last(), pluralWeekdayLabel(query))
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
        val ranked = WhatNowEngine.ordered(active, now, zone)
        // Franja horaria (parte del día) como modificador opcional encima del
        // rango de fechas: simétrico con SearchEngine.scopeBand (MADRUGADA 0..5,
        // MORNING 6..11 ["esta mañana"], TARDE 12..17, NOCHE 18..23). Si la consulta
        // menciona una parte del día,
        // se filtra además por hora local. Así "¿qué tengo esta noche?" = tareas de
        // hoy con hora 18-23; "¿qué tengo el viernes en la noche?" = viernes 18-23.
        // Las tareas sin hora concreta (dueAt a medianoche, hora 0) sólo casan con
        // madrugada — igual que en SearchEngine: una tarea "solo fecha" no puede
        // afirmar honestamente pertenecer a la tarde/noche.
        val band = agendaPartOfDay(query)
        // c.385: la membresía de fecha espeja PlannerCalendar.datesFor y
        // SummaryEngine.remainingToday — una tarea es "del rango [start,end]" si
        // su hora prevista (`startAt`) o su fecha límite (`dueAt`) cae en él. Antes
        // este filtro miraba SÓLO `dueAt`, así "¿qué tengo hoy?" omitía un slot
        // agendado para hoy cuyo vencimiento era posterior (startAt hoy, dueAt el
        // viernes): el planificador lo mostraba hoy y "¿voy bien?" lo contaba en la
        // carga de hoy, pero la agenda lo callaba — mentía por omisión en la
        // consulta más común. Ahora las tres superficies acuerdan. La franja
        // horaria (band) se resuelve con la marca que cae en el rango, prefiriendo
        // `startAt` (simétrico con PlannerCalendar.timestampOnDate), así "¿qué tengo
        // esta tarde?" muestra un slot de hoy 15:00 aunque venza más tarde.
        val due = ranked.filter {
            val dateOk = if (pluralDates != null) isScheduledInDates(it, pluralDates, zone)
                else isScheduledInRange(it, start, end, zone)
            dateOk && (band == null || isInHourBand(it, band, start, end, zone))
        }
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
                // c.412: inicio olvidado (missed-start) cuyo hueco se pasó en un día
                // ANTERIOR. startAt en un día previo y dueAt futuro (o sin dueAt) →
                // no cae en el rango de hoy (isScheduledInRange) ni es vencida por
                // dueAt (earlierOverdue), así que antes la agenda "hoy" decía "no
                // tienes nada" frente a trabajo olvidado que debe hacer hoy. Todas
                // las demás superficies (What Now, "¿qué olvidé?", "¿voy bien?") ya
                // lo recuperaban; la agenda callaba el mismo olvido en la consulta más
                // común. Lo nombramos (no routing: la consulta es de agenda).
                val missedEmpty = mostUrgentMissedStart(active, now, zone)
                if (missedEmpty != null) {
                    val minutes = TaskRules.plannedDuration(missedEmpty)
                    return AssistantAnswer(
                        "Para hoy no tienes tareas agendadas, pero «${missedEmpty.title}» tenía su hueco y se pasó (~$minutes min)." +
                            overdueCommitmentTail(overdueCommitments),
                        relatedTaskIds = listOf(missedEmpty.id)
                    )
                }
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
        // c.412: y lo mismo con los inicios olvidados (missed-start) cuyo hueco se
        // pasó en un día anterior (startAt previo, dueAt futuro/sin dueAt): no son
        // ni atrasadas por dueAt ni agenda de hoy, pero son trabajo olvidado que
        // debe hacer hoy. Aquí, al haber agenda de hoy, va como cola informativa
        // (no se lista entre las de hoy: es "además", igual que earlierOverdue).
        val tail = if (label == "hoy") {
            val earlierOverdue = active.count { TaskRules.isOverdue(it, now) && !TaskRules.isDueToday(it, now, zone) }
            val overdueTaskTail = if (earlierOverdue > 0) {
                " Además, tienes $earlierOverdue atrasad${if (earlierOverdue == 1) "a" else "as"} de días anteriores."
            } else ""
            val missed = mostUrgentMissedStart(active, now, zone)
            val missedTail = if (missed != null) {
                val minutes = TaskRules.plannedDuration(missed)
                " Además, «${missed.title}» tenía su hueco y se pasó (~$minutes min)."
            } else ""
            // c.417: 3.er olvido (capturas de bandeja arrinconadas, isStaleInbox).
            // Paralelo a earlierOverdue/missed/compromisos: la agenda "hoy" no puede
            // callar seis ideas arrinconadas mientras lista las de hoy. Como
            // isStaleInbox exige dueAt==null && startAt==null, ninguna stale aparece
            // en la agenda listada → cola de conteo sin doble señalización. Misma
            // mentir por omisión que c.410/c.411 cerraron en el nudge y What Now.
            overdueTaskTail + missedTail + staleInboxTail(active, now, zone) +
                overdueCommitmentTail(overdueCommitments)
        } else ""
        val head = if (label == "hoy") "Hoy" else label.replaceFirstChar { it.uppercase() }
        return AssistantAnswer("$head: $titles.$tail", relatedTaskIds = ids)
    }

    private fun isScheduledInRange(task: TaskEntity, start: LocalDate, end: LocalDate, zone: ZoneId): Boolean {
        // Una tarea pertenece al rango si su `startAt` o su `dueAt` cae en él.
        // Simétrico con PlannerCalendar.datesFor (que suma ambas fechas) y con el
        // conteo de carga del día (SummaryEngine.remainingToday, que cuenta por
        // startAt). Mirar sólo `dueAt` haría que la agenda omitiera un slot agendado
        // para hoy cuyo vencimiento es posterior.
        val inRange = { d: LocalDate -> d >= start && d <= end }
        val due = task.dueAt?.let { DateRules.toLocalDate(it, zone) }
        val stt = task.startAt?.let { DateRules.toLocalDate(it, zone) }
        return (due != null && inRange(due)) || (stt != null && inRange(stt))
    }

    // Franja horaria (parte del día) de la agenda. Simétrico con
    // SearchEngine.scopeBand: MADRUGADA 0..5, MORNING 6..11, TARDE 12..17,
    // NOCHE 18..23. Tokens sin acento (foldForSearch). Devuelve null si la consulta
    // no menciona una parte del día. NOTA: "mañana" SOLA nunca es franja (significa
    // "tomorrow", igual que en SearchEngine y el parser); únicamente la forma
    // demostrativa "esta mañana" es inequívoca como la mañana de HOY (6..11).
    private fun agendaPartOfDay(query: String): IntRange? = when {
        "esta manana" in query -> 6..11
        enLaMananaHoy(query) -> 6..11
        "madrugada" in query -> 0..5
        "tarde" in query -> 12..17
        "noche" in query -> 18..23
        else -> null
    }

    private fun agendaPartOfDayLabel(query: String): String = when {
        "esta manana" in query -> "esta mañana"
        enLaMananaHoy(query) -> "esta mañana"
        "madrugada" in query -> "esta madrugada"
        "tarde" in query -> "esta tarde"
        "noche" in query -> "esta noche"
        else -> "hoy"
    }

    // "en la mañana"/"por la mañana" (preposición + artículo): la mañana (6..11)
    // de HOY, jamás tomorrow — la misma lectura que "esta mañana", el parser de
    // captura (hoy 09:00) y SearchEngine. Sin esta señal el token suelto
    // "manana" de "¿qué tengo en la mañana?" caía a la agenda de MAÑANA (mentira
    // cruzada con la captura). El lookbehind fijo excluye "mañana en la mañana"
    // y "pasado mañana en la mañana": ahí el primer "mañana" es tomorrow y la
    // consulta resuelve a ese día completo (sin franja, como SearchEngine).
    private val AGENDA_MANANA_PREP = Regex("(?<!manana )\\b(?:en|por) la manana\\b")
    private fun enLaMananaHoy(query: String): Boolean = AGENDA_MANANA_PREP.containsMatchIn(query)

    private fun isInHourBand(task: TaskEntity, band: IntRange, start: LocalDate, end: LocalDate, zone: ZoneId): Boolean {
        // La franja se resuelve con la marca temporal que cae dentro del rango de
        // fechas de la consulta, prefiriendo `startAt` (simétrico con
        // PlannerCalendar.timestampOnDate). Así "¿qué tengo esta tarde?" muestra un
        // slot de hoy 15:00 aunque venza más tarde, y no mezcla por un `dueAt`
        // nocturno cuando el usuario agendó el slot para la mañana.
        val stt = task.startAt?.takeIf { DateRules.toLocalDate(it, zone) in start..end }
        val ts = stt ?: task.dueAt?.takeIf { DateRules.toLocalDate(it, zone) in start..end } ?: return false
        return Instant.ofEpochMilli(ts).atZone(zone).hour in band
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
    // Forma desnuda "tareas de/del <fecha>": SUJETO explícito ("tarea"/"tareas") +
    // conector "de"/"del". Así "tareas del viernes" se resuelve como agenda sin el
    // marcador verbal; el alcance temporal se exige en isAgendaQuery, por lo que
    // "tareas de matemáticas" NO se secuestra por el conector suelto. Normalizado
    // (sin tilde) antes, por isAssistantQuery → normalize.
    // Sonda c.793: "tareas por la tarde/noche" — la forma MÁS natural en
    // español de pedir agenda por franja ("por la tarde"). Sólo existían los
    // conectores "de/del", así que la consulta caía al menú: mentira por
    // omisión, el asistente NO leía la agenda vespertina que ya tenía la
    // maquinaria. La guardia de alcance temporal abajo (hoy/mañana/semana/
    // mes/finde/weekday/partOfDay) impide secuestrar el contenido: "tareas
    // por hacer" sigue sin activar agenda (no hay token temporal).
    private val BARE_TEMPORAL_TASK_CONNECTORS = listOf(
        "tareas de ", "tareas del ", "tarea de ", "tarea del ",
        "tareas por ", "tarea por "
    )
    // c.820: paráfrasis desnuda «algo <alcance>» — hermana residual de «tengo
    // que hacer algo <scope>» (c.817). Coincidencia por PALABRA para no
    // secuestrar «algoritmo para mañana»/«algodón...»; el alcance temporal
    // se exige igualmente abajo («algo de matemáticas» sigue al menú).
    private val BARE_ALGO_TOKEN = Regex("""\balgo\b""")
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

    // Modificadores de pasado para el recap de logros: "la semana pasada"/"el
    // mes pasado"/"la última semana"/"el último mes". Espejo EXACTO por período
    // de SearchEngine.LAST_WEEK_TOKENS/LAST_MONTH_TOKENS, de modo que el recap
    // del asistente y la búsqueda interpreten "semana pasada"/"mes pasado" igual
    // y nunca discrepen. La semana EXCLUYE "ultimo"/"ultimos" (la semana es
    // femenina: "última semana" sí es pasado, pero "último día de la semana" no
    // — habla del último día de ESTA semana); el mes los ADMITE (es masculino:
    // "el último mes"). Sin este split por período, "el último día de la semana"
    // caía a "La semana pasada" y silenciaba el logro de hoy (mentira por
    // omisión introducida en c.611 por usar un superset único).
    private val LAST_WEEK_RECAP_MODIFIERS = DateRules.LAST_WEEK_MODIFIERS
    private val LAST_MONTH_RECAP_MODIFIERS = DateRules.LAST_MONTH_MODIFIERS

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
     * Horizonte (en semanas) del weekday en plural ("los viernes"): cubre los
     * próximos [PLURAL_WEEKDAY_HORIZON_WEEKS] weekday consecutivos. ≈1 mes, mismo
     * orden de magnitud que el alcance "este mes": suficiente para ver el patrón
     * recurrente (un compromiso quincenal aparece) sin pretender agotar el
     * futuro infinito.
     */
    private const val PLURAL_WEEKDAY_HORIZON_WEEKS = 4

    /**
     * Fechas concretas de los próximos N weekday para una consulta en PLURAL
     * ("los viernes", "los próximos lunes"). Los weekday españoles son
     * invariables en plural, así el plural se detecta por el determinante "los "
     * antecediendo al token (foldForSearch ya minificó y quitó acentos). Se
     * permite texto entre ambos ("los próximos viernes", "los días lunes")
     * porque el modificador de strictness puede intercalarse.
     *
     * Devuelve la LISTA EXACTA de los N días de la semana objetivo (no un
     * intervalo calendario continuo). Antes se devolvía un rango continuo
     * `start..end` de ~4 semanas y la pertenencia se evaluaba con
     * [isScheduledInRange] sobre ese intervalo, lo que incluía por error
     * tareas de OTRO día de la semana que caían dentro del intervalo
     * (p. ej. un miércoles aparecía bajo "los viernes"). Devolver sólo los
     * weekday objetivo y filtar por membresía discreta ([isScheduledInDates])
     * corrige el exceso: "los viernes" muestra sólo lo agendado esos viernes.
     *
     * La primera fecha es la próxima ocurrencia (inclusiva: incluye hoy si hoy
     * es ese día) salvo con modificador "próximo"/"que viene" (estricto: salta
     * hoy); las restantes suman 1 semana cada una — N weekday consecutivos.
     * Simétrico con [resolveAgendaWeekday] (misma semántica inclusivo/estricto).
     * Devuelve null si la consulta no es un weekday en plural.
     */
    private fun pluralWeekdayDates(query: String, today: LocalDate): List<LocalDate>? {
        val token = AGENDA_WEEKDAY_TOKENS.firstOrNull { Regex("\\blos\\b.*\\b$it\\b").containsMatchIn(query) } ?: return null
        val target = AGENDA_WEEKDAY_BY_TOKEN[token] ?: return null
        val strict = AGENDA_WEEKDAY_NEXT_MODIFIERS.any { it in query }
        val delta = (target.value - today.dayOfWeek.value + 7) % 7
        val days = if (strict) (if (delta == 0) 7 else delta).toLong() else delta.toLong()
        val start = today.plusDays(days)
        return (0 until PLURAL_WEEKDAY_HORIZON_WEEKS).map { start.plusWeeks(it.toLong()) }
    }

    /**
     * Pertenencia por MEMBRESÍA DISCRETA: una tarea pertenece al plural de
     * weekday ("los viernes") si su `startAt` o `dueAt` cae en uno de los días
     * objetivo exactos, NO en cualquier día del intervalo calendario. Así un
     * compromiso quincenal (su `dueAt` es uno de esos viernes) aparece, pero una
     * tarea de otro día de la semana dentro del horizonte se excluye.
     * Simétrico con [isScheduledInRange] (rango continuo) usado por los demás
     * alcances (hoy/semana/mes/finde/weekday singular).
     */
    private fun isScheduledInDates(task: TaskEntity, dates: List<LocalDate>, zone: ZoneId): Boolean {
        val set = dates.toHashSet()
        val due = task.dueAt?.let { DateRules.toLocalDate(it, zone) }
        val stt = task.startAt?.let { DateRules.toLocalDate(it, zone) }
        return (due != null && due in set) || (stt != null && stt in set)
    }

    /** Etiqueta honesta del weekday en plural: "los próximos viernes". */
    private fun pluralWeekdayLabel(query: String): String {
        val token = AGENDA_WEEKDAY_TOKENS.firstOrNull { Regex("\\blos\\b.*\\b$it\\b").containsMatchIn(query) } ?: return "esos días"
        val pretty = when (token) {
            "miercoles" -> "miércoles"
            "sabado" -> "sábado"
            else -> token
        }
        return "los próximos $pretty"
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
            "cuanto tiempo me queda" in query || "cuanto tiempo me falta" in query ||
            "cuanto tiempo libre" in query ||
            "cuanto me queda" in query || "tengo tiempo libre" in query ||
            "tengo mucho que hacer" in query ||
            // c.798 (carga): cobertura remota. Las formas interrogativas de
            // tiempo libre («qué tiempo tengo» / «tiempos libres» / «horario
            // tengo libre») NO van aquí: viven en isFreeTimeQuery. Como la rama
            // de carga se evalúa antes en answer(), tenerlas en ambas listas
            // haría que el hueco libre respondiera como carga del día (su test
            // freeTime_recognizes* lo detecta).
            "cuanta carga" in query || "tengo muchas tareas" in query ||
            "cuanto tiempo me falta" in query ||
            "cabe todo" in query || "cabe el dia" in query || "cabe hoy" in query ||
            "alcanzara" in query || "alcanzare" in query || "da alcance" in query ||
            "estoy saturad" in query ||
            COMO_VOY.containsMatchIn(query) ||
            // c.802: «que tan llena/lleno» — carga del día/semana honesta.
            "que tan llena" in query || "que tan lleno" in query

    /**
     * Detecta la intención de PANORAMA del día: el recuento (hechas/pendientes/
     * vencidas) + cómo va el día. Frases cotidianas: "¿resumen del día?",
     * "¿cómo va el día/mi día?", "¿cuántas tareas tengo hoy?", "¿cuántas tengo
     * hoy?", "¿cuántos pendientes tengo hoy?". Tokens sin acento (ya
     * normalizados por `foldForSearch`). No colisiona:
     *  - NO es veredicto de carga ([isDayLoadQuery]: "voy bien"/"da tiempo"/
     *    "cuánto me queda"/"tengo tiempo libre"...) — aquí se pide un recuento,
     *    no "¿cabe?".
     *  - NO es agenda ([isAgendaQuery]: "qué tengo"/"tengo para"/"hay algo"...) —
     *    "cuántas tengo hoy" no contiene "qué tengo"/"tengo para"/"tengo algo".
     *  - NO es what-now ("qué hago"/"qué sigue"...) ni recap ("qué hice"/"completé")
     *    ni búsqueda de entidad ("a qué hora"/"cuándo"/"dónde").
     * El recuento con tiempo (futuro: "cuántas tengo mañana/el viernes") se deja
     * fuera: exigir "hoy" evita robar la agenda de un día concreto. Mismo enfoque
     * conservador que [isDayLoadQuery] (guarda anti-colisión explícita).
     */
    private fun isDaySummaryQuery(query: String): Boolean {
        if ("resumen del dia" in query || "resumen de hoy" in query) return true
        if ("como va el dia" in query || "como va mi dia" in query) return true
        // c.803 (sonda AssistantOverdueImportanceProbe): versión de verbo
        // cotidiano («resume mi día») y sinónimo laboral («mi jornada») de
        // las formas de arriba — caían al menú pese a que el panorama ya
        // existe. «jornada» no compite con el veredicto de carga: COMO_VOY
        // exige «cómo voy» (1.ª persona), así que la guarda es estructural.
        // «resume mi día» no compite con la rama de conversaciones, que
        // exige «conversación»/«mensaje».
        if ("resume mi dia" in query || "como va mi jornada" in query) return true
        // Recuento de hoy: exige "hoy" para no robar la agenda de otros días.
        if ("hoy" in query) {
            if ("cuantas tareas" in query || "cuantas tengo" in query ||
                "cuantos pendientes" in query || "cuantos pendiente" in query
            ) return true
            // c.803: «¿cuánto falta por hacer hoy?» pide el mismo recuento
            // de pendientes. Guarda «hoy» hermana de las de arriba:
            // «…mañana» sigue fuera (no roba la agenda de otro día).
            if ("cuanto falta por hacer" in query) return true
        }
        return false
    }

    /**
     * Intención de planificación: abre el planificador. "organiza mi día" y sus
     * sinónimos cotidianos. La query ya viene normalizada (sin acentos, minúsculas).
     * Excluye "plan mínimo" (lista de 3) porque se resuelve en su propia rama más
     * abajo; aquí no colisiona porque se exige un verbo + "día"/"plan", y las
     * formas con sustantivo "plan" llevan guarda `"plan minimo" !in query` para
     * no robar la rama de la lista de 3 (paridad con [planificaDoesNotStealPlanMinimo]).
     */
    private fun isPlannerIntent(query: String): Boolean {
        if ("plan minimo" in query) return false
        return "organiza mi dia" in query || "organizar mi dia" in query || "organiza el dia" in query ||
            "planifica mi dia" in query || "planificar mi dia" in query ||
            "planifica el dia" in query || "planificar el dia" in query ||
            "planificame mi dia" in query || "planificame el dia" in query ||
            "planea mi dia" in query || "planea el dia" in query ||
            "ordena mi dia" in query || "ordena el dia" in query ||
            "arma mi dia" in query || "armar mi dia" in query ||
            "arma el plan" in query || "armar el plan" in query ||
            "armame un plan" in query || "armame el plan" in query ||
            "preparame un plan" in query || "preparame el plan" in query ||
            "prepara un plan" in query || "preparar un plan" in query ||
            "hazme un plan" in query || "hazme el plan" in query ||
            "dame un plan" in query || "dame el plan" in query ||
            DECLARATIVE_PLAN_REQUEST.containsMatchIn(query)
    }

    // Formas declarativas ("quiero/necesito un plan"): a diferencia de las
    // imperativas, "un plan" puede ser sujeto de un plan-documento ("quiero un
    // plan estratégico para mañana"). Se exige "un/el plan" al FINAL del
    // enunciado (sin calificador posterior) para no robar esas frases
    // legítimas. La guarda superior `"plan minimo" !in query` ya excluye la
    // lista de 3, así esta regex no la roba.
    private val DECLARATIVE_PLAN_REQUEST =
        Regex("""\b(quiero|necesito)\s+(un|el)\s+plan\s*[.!?]*$""")

    // "¿cómo voy?" / "¿cómo voy hoy?" — la forma cotidiana por excelencia de pedir
    // el panorama del día. Casar "como voy" como subcadena robaría "¿cómo voy a
    // llegar/pagar/hacer?" (pide el MODO de lograr algo, no el panorama), así se
    // exige límite de palabra y se descarta lo seguido de " a" (infinitivo). La
    // query ya viene normalizada (sin acentos). Paridad con el guard de "tengo
    // tiempo" suelto (capacidad para una tarea concreta, no veredicto del día).
    private val COMO_VOY = Regex("""\bcomo\s+voy\b(?!\s+a\b)""")

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
        val tail = overdueCountTail(overdue) +
            missedStartTail(summary.missedStart) +
            staleInboxTail(tasks, now, zone) +
            overdueCommitmentTail(overdueCommitments)
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

    /**
     * "¿resumen del día?"/"¿cuántas tareas tengo hoy?"/"¿cómo va el día?" — el
     * PANORAMA de hoy a demanda. El asistente ya respondía al veredicto de carga
     * ("¿voy bien?") y a la lista de agenda ("¿qué tengo hoy?"), pero la forma
     * más natural de pedir el PANORAMA — cuántas hechas, cuántas pendientes,
     * cuántas vencidas y cómo va el día — caía al menú genérico. Ordía YA calcula
     * esos conteos en `SummaryEngine` (fuente única de la tarjeta de Hoy); aquí se
     * exponen a demanda, reusando el MISMO motor para que el asistente y la tarjeta
     * nunca discrepen (no es una segunda fuente de verdad). Como [dayLoadAnswer],
     * no calla los olvidos: anexa missed-start, stale-inbox y compromisos vencidos
     * (las vencidas se cuentan inline como métrica primaria, así que NO se repiten
     * como cola — evita la doble señalización de c.409/c.410). Bajo OVERLOADED nombra
     * la candidata a posponer (mismo `deferralSuggestion`). Sin nueva pantalla ni
     * botón: sólo entender más frases sobre la superficie que ya existe.
     * Determinista y local (sin IA fingida).
     */
    private fun daySummaryAnswer(
        tasks: List<TaskEntity>,
        overdue: List<TaskEntity>,
        overdueCommitments: List<CommitmentEntity>,
        now: Long,
        zone: ZoneId,
        profile: LearningProfile?
    ): AssistantAnswer {
        val summary = SummaryEngine.summarize(tasks, now, zone, profile)
        val completed = summary.completedToday
        val remaining = summary.remainingToday
        val over = overdue.size
        val mins = summary.remainingMinutesToday
        // Las vencidas se cuentan inline → NO se repiten como cola (anti-doble-
        // señalización). El resto de olvidos SÍ van como cola informativa.
        val tail = missedStartTail(summary.missedStart) +
            staleInboxTail(tasks, now, zone) +
            overdueCommitmentTail(overdueCommitments)
        // Recuento: hechas / pendientes (~min) / vencidas, en frases plurales
        // correctas. Si todo es cero, "no tienes tareas pendientes" (honesto).
        val parts = mutableListOf<String>()
        if (completed > 0) parts += "$completed ${if (completed == 1) "hecha" else "hechas"}"
        if (remaining > 0) {
            val dur = if (mins >= 60) {
                val h = mins / 60
                val m = mins % 60
                if (m == 0) "~${h}h" else "~${h}h ${m}min"
            } else {
                "~${mins} min"
            }
            val noun = if (remaining == 1) "pendiente" else "pendientes"
            parts += "$remaining $noun ($dur)"
        }
        if (over > 0) parts += "$over ${if (over == 1) "vencida" else "vencidas"}"
        val head = if (parts.isEmpty()) "Hoy no tienes tareas pendientes." else "Hoy: ${parts.joinToString(", ")}."
        val hasPendingWork = remaining > 0 || over > 0
        if (!hasPendingWork) {
            // Todo hecho (o nada) → el recuento basta; el veredicto sería "despejado"
            // y resultaría redundante. Se evita "cabe con holgura" cuando no queda nada.
            return AssistantAnswer("$head$tail")
        }
        // Con trabajo pendiente/vencido → veredicto honesto del día (mismo motor).
        val (verdict, ids, action) = when (summary.dayLoad) {
            DayLoad.LIGHT -> Triple("El día está despejado.", emptyList<Long>(), AssistantAction.NONE)
            DayLoad.ON_TRACK -> Triple("Va a tiempo.", emptyList<Long>(), AssistantAction.NONE)
            DayLoad.FULL -> Triple("Cabe, pero justo.", emptyList<Long>(), AssistantAction.NONE)
            DayLoad.OVERLOADED -> {
                val sug = summary.deferralSuggestion
                if (sug != null) {
                    Triple("No da tiempo a todo: «${sug.title}» es la candidata a mover a mañana.", listOf(sug.taskId), AssistantAction.NONE)
                } else {
                    Triple("No da tiempo a todo: revisa qué posponer o quitar.", emptyList<Long>(), AssistantAction.OPEN_PLANNER)
                }
            }
        }
        return AssistantAnswer("$head $verdict$tail", action, relatedTaskIds = ids)
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
     * Cola informativa para no callar el "olvido silencioso"
     * ([TaskRules.isMissedStart] — un compromiso al que el usuario le dio hueco
     * `startAt` y se le pasó, sin vencer aún) en "¿voy bien?"/"¿da tiempo?".
     * Simétrica con [overdueCountTail] y [overdueCommitmentTail]: el veredicto de
     * carga cuenta el trabajo olvidado en `loadMinutes` (c.247) y, bajo OVERLOADED
     * sin candidata a posponer, puede caer al genérico "Revisa qué posponer o
     * quitar" — consejo dañino para un olvido (posponerlo lo agrava;
     * [SummaryEngine.mostDeferrableTask] ya lo excluye de las candidatas a mover).
     * Nombrarlo evita la mentira por omisión que c.407 corrigió en la tarjeta de
     * resumen de TodayScreen: allí la misma carga se inflaba por olvidos pero la
     * tarjeta callaba la causa. Aquí ocurrió lo mismo — el asistente lee el mismo
     * `SummaryEngine` veredicto, pero su cola sólo nombraba vencidas y compromisos,
     * no el olvido que inflaba la carga. El conteo viene de
     * [SummaryEngine.DaySummary.missedStart] (raíces, fuente única). No añade ids:
     * avisa, no navega (el usuario ya tiene el veredicto/deferral para actuar).
     */
    private fun missedStartTail(missedStart: Int): String =
        when {
            missedStart <= 0 -> ""
            missedStart == 1 ->
                " Además, tienes 1 tarea cuyo hueco ya pasó: recupérala hoy o reagéndala con intención, no la pospongas."
            else ->
                " Además, tienes $missedStart tareas cuyo hueco ya pasó: recupéralas hoy o reagéndalas con intención, no las pospongas."
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
        now: Long,
        zone: ZoneId
    ): String {
        val missed = mostUrgentMissedStartExcluding(active, suggested.id, now, zone) ?: return ""
        val minutes = TaskRules.plannedDuration(missed)
        return " Además, «${missed.title}» tenía su hueco y se pasó (~$minutes min)."
    }

    private fun mostUrgentMissedStart(active: List<TaskEntity>, now: Long, zone: ZoneId): TaskEntity? =
        WhatNowEngine.ordered(active, now, zone).firstOrNull { TaskRules.isMissedStart(it, now) }

    private fun mostUrgentMissedStartExcluding(
        active: List<TaskEntity>,
        excludeId: Long,
        now: Long,
        zone: ZoneId
    ): TaskEntity? =
        WhatNowEngine.ordered(active, now, zone).firstOrNull { TaskRules.isMissedStart(it, now) && it.id != excludeId }

    /**
     * Cola que recupera el 3.er olvido en "¿qué hago ahora?": cuenta las
     * capturas de bandeja arrinconadas ([TaskRules.isStaleInbox], ≥7 días sin
     * fecha ni hueco) DISTINTAS a la [suggested]. Antes esta señal sólo vivía en
     * "¿qué olvidé?" y en el nudge del guardián ([GuardianEngine.withStaleInboxTail],
     * c.410): si la tarea más prioritaria del momento era otra, seis ideas
     * arrinconadas quedaban ocultas en la superficie de MAYOR tráfico y el
     * usuario no las agendaba ni hacía — la misma mentira por omisión que
     * c.357/c.410 cerraron para vencidas, missed-start y compromisos. Es una
     * cola de CONTEO (no nombra títulos ni pide acción concreta: la acción
     * primaria es la tarea ya señalada), simétrica con
     * [overdueCommitmentTail] (4.º olvido) y coherente con el nudge, que
     * también sólo cuenta. Excluye la sugerida: si la propia candidata es la
     * captura olvidada, su razón/posición ya la explica y no se cuenta dos
     * veces — igual que [overdueTail] excluye la sugerida y [missedStartTail]
     * la excluye. No añade ids: avisa, no navega (el usuario ya tiene la
     * sugerida para actuar; la cola empuja a revisar la bandeja).
     */
    private fun staleInboxTail(
        active: List<TaskEntity>,
        suggested: TaskEntity,
        now: Long,
        zone: ZoneId
    ): String {
        val count = active.count { TaskRules.isStaleInbox(it, now, zone) && it.id != suggested.id }
        if (count == 0) return ""
        val capturas = if (count == 1) "1 captura" else "$count capturas"
        val llevan = if (count == 1) "lleva" else "llevan"
        return " Además, $capturas en la bandeja $llevan una semana o más sin agendar."
    }

    /**
     * Variante sin exclusión para superficies sin tarea sugerida: la agenda "hoy"
     * lista tareas con `dueAt`/`startAt` de hoy (ninguna es stale-inbox, que exige
     * `dueAt==null && startAt==null`), y el veredicto de carga no nombra ninguna
     * tarea — así no hay candidata que excluir ni doble señalización. Cuenta TODAS
     * las capturas arrinconadas como cola informativa del 3.er olvido, paralela a
     * [overdueCountTail]/[missedStartTail]/[overdueCommitmentTail]. Misma clase de
     * "mentir por omisión" que c.410/c.411 cerraron: un día con agenda rellena o un
     * veredicto "despejado" no puede callar seis ideas arrinconadas. Determinista y
     * local (predicado único [TaskRules.isStaleInbox]).
     */
    private fun staleInboxTail(active: List<TaskEntity>, now: Long, zone: ZoneId): String {
        val count = active.count { TaskRules.isStaleInbox(it, now, zone) }
        if (count == 0) return ""
        val capturas = if (count == 1) "1 captura" else "$count capturas"
        val llevan = if (count == 1) "lleva" else "llevan"
        return " Además, $capturas en la bandeja $llevan una semana o más sin agendar."
    }

    /**
     * Frase honesta de "¿qué hago ahora?" sobre el hueco hasta la próxima cita
     * (c.552). [gapMinutes] viene de [WhatNowSuggestion.minutesUntilNextCommitment]
     * (null = sin cita cercana en el horizonte útil) y [taskMinutes] es la
     * duración/pendiente ya estimada de la tarea sugerida.
     *
     * - No cabe (taskMinutes > gap): avisa — empezar ahora implica que la cita
     *   interrumpirá la tarea. Siempre útil: cambia la decisión del usuario.
     * - Cabe y está JUSTO (taskMinutes ocupa más de la mitad del hueco): confirma
     *   "te alcanza antes de tu próxima cita" para que el usuario se decida a
     *   arrancar sabiendo que el margen es corto.
     * - Cabe de sobra: calla. El silencio es honesto: no hay decisión difícil que
     *   señalar y un "te alcanza" trivial sólo añadiría ruido.
     *
     * Determinista y local (no IA fingida): dos comparaciones sobre enteros.
     * El prefijo " " integra la frase al mismo nivel que [timePhrase] (antes del
     * tail de olvidos, que lleva su propio " Además,").
     */
    private fun nextCommitmentGapPhrase(gapMinutes: Int?, taskMinutes: Int, alternative: TaskEntity?): String {
        val gap = gapMinutes ?: return ""
        return when {
            taskMinutes > gap -> {
                // Alternativa accionable (c.557): la sugerida no cabe antes de la
                // cita, pero hay otra tarea que SÍ cabe y es arrancable ahora →
                // se nombra para que el usuario aproveche el hueco en vez de
                // arrancar algo que la cita interrumpirá. Sin alternativa, el
                // aviso simple de c.552 (no se inventa una opción que no existe).
                if (alternative != null) {
                    val altMin = TaskRules.plannedDuration(alternative)
                    " Ojo: tu próxima cita es en ~$gap min; antes cabe “${alternative.title}” (~$altMin min)."
                } else {
                    " Ojo: tu próxima cita es en ~$gap min."
                }
            }
            taskMinutes * 2 > gap -> " Te alcanza antes de tu próxima cita."
            else -> ""
        }
    }

    /**
     * Mejor tarea raíz activa que cabe en el hueco hasta la próxima cita y es
     * arrancable AHORA, distinta de [suggestedId]. Inteligencia contextual
     * honesta (c.557): convierte el aviso "no cabe" de c.552 en una
     * micro-decisión productiva ("antes cabe X") cuando existe algo
     * genuinamente realizable, sin nueva pantalla/botón.
     *
     * Reusa [WhatNowEngine.ordered] (fuente única de verdad del ranking) y
     * toma la PRIMERA que cumple:
     * - distinta de la sugerida (no repetir lo que ya encabeza la respuesta);
     * - que el usuario NO está ejecutando ahora ([TaskRules.isBeingWorkedOn]
     *   —no sugerir lo que ya hace, ni pisar el trabajo activo—);
     * - arrancable ahora: sin `startAt` futuro (una tarea con hueco propio no
     *   es "haz esto ahora", respeta la planificación del usuario; simétrico
     *   con [TaskRules.isScheduledLater] que hunde esas tareas en What Now);
     * - que cabe: [TaskRules.plannedDuration] ≤ [gapMinutes] (acotado a
     *   [MIN_PLAN_MINUTES, MAX_PLAN_MINUTES], así una tarea de duración
     *   desconocida no se promete "cabe en 10 min" con datos inventados).
     *
     * Determinista y local (no IA fingida, sin random): filtro + primera del
     * orden canónico. Null cuando [gapMinutes] no es útil (≤0) o nada cumple
     * → el llamador mantiene el aviso simple de c.552.
     */
    private fun fittingAlternativeBeforeCommitment(
        active: List<TaskEntity>,
        suggestedId: Long,
        gapMinutes: Int?,
        now: Long,
        zone: ZoneId
    ): TaskEntity? {
        val gap = gapMinutes ?: return null
        if (gap <= 0) return null
        return WhatNowEngine.ordered(active, now, zone).firstOrNull { t ->
            t.id != suggestedId &&
                !TaskRules.isBeingWorkedOn(t, now) &&
                (t.startAt == null || t.startAt <= now) &&
                TaskRules.plannedDuration(t) <= gap
        }
    }
}
