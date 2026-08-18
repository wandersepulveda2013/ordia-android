package com.ordia.app.assistant

import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.CommitmentReviewStatus
import com.ordia.app.data.local.ConversationEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
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
import java.time.LocalTime
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
                (("que tengo que hacer" in query || "que me falta" in query) && !isAgendaQuery(query)) -> {
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
                    val gapPhrase = nextCommitmentGapPhrase(suggestion.minutesUntilNextCommitment, minutes)
                    AssistantAnswer(
                        "$lead “${suggestion.task.title}”: $why. $timePhrase.$gapPhrase$tail",
                        relatedTaskIds = listOf(suggestion.task.id)
                    )
                }
            }
            isAgendaQuery(query) -> agendaAnswer(query, active, overdueCommitments, now, zone)
            "que olvide" in query || "olvidado" in query || "atrasad" in query || "vencid" in query -> {
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
                val forgottenIntent = "que olvide" in query || "olvidado" in query || "atrasad" in query
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
                val quick = WhatNowEngine.ordered(active, now).filter { it.durationMinutes <= 15 }.take(6)
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
            query.startsWith("busca ") || query.startsWith("muestra ") || query.startsWith("pendientes con") ->
                AssistantAnswer("Abriré la búsqueda con esa consulta.", AssistantAction.OPEN_SEARCH, clean)
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
     * Detecta la intención de "recap" de logros: "¿qué hice hoy?",
     * "¿qué completé/terminé hoy?", "¿qué hice ayer?". Tokens sin acento (ya
     * normalizados por `foldForSearch`). Excluye deliberadamente el verbo
     * *tener* (agenda) y *deber/faltar* (pendientes) para no secuestrar esas
     * ramas. El token "hice" solo casa con recap (no aparece en agenda ni en
     * olvidos ni en compromisos), por lo que es seguro.
     */
    private fun isCompletedRecapIntent(query: String): Boolean {
        val isRecapVerb =
            "que hice" in query || "que complete" in query ||
                "que termine" in query || "que acabe" in query ||
                "que complete" in query || "que completado" in query ||
                "hice hoy" in query || "complete hoy" in query ||
                "termine hoy" in query || "acabe hoy" in query ||
                "completado hoy" in query
        // "hice ayer" se trata aparte para forzar la fecha de ayer aunque falte
        // el verbo recap explícito ("¿qué hice ayer?" trae "hice" + "ayer").
        return isRecapVerb || "hice ayer" in query
    }

    /**
     * Respuesta de logro para "¿qué hice hoy?"/"¿qué hice ayer?". Reusa el
     * MISMO predicado canónico que `TaskRules.completedTodayCount` (raíces,
     * `status==COMPLETED`, `!archived`, `!CANCELLED`, `completedAt` cae en la
     * fecha), extendido a "ayer" (fecha = hoy-1). No es una segunda fuente de
     * verdad: aplica el mismo filtro canónico en otra fecha. Lista los títulos
     * ordenados por `completedAt` desc (lo más reciente primero) y nombra hasta
     * 3; el resto se resume como recuento. Sin nueva pantalla ni botón: la
     * superficie del asistente ya existe. Determinista y local (sin IA fingida).
     */
    private fun completedAnswer(
        query: String,
        tasks: List<TaskEntity>,
        now: Long,
        zone: ZoneId
    ): AssistantAnswer {
        val today = DateRules.toLocalDate(now, zone)
        val target = if ("ayer" in query) today.minusDays(1) else today
        val day = if ("ayer" in query) "Ayer" else "Hoy"
        val done = tasks
            .asSequence()
            .filter { it.parentTaskId == null }
            .filter { it.status == TaskStatus.COMPLETED }
            .filterNot { it.archived }
            .filterNot { it.status == TaskStatus.CANCELLED }
            .mapNotNull { t ->
                val at = t.completedAt ?: return@mapNotNull null
                if (DateRules.toLocalDate(at, zone) == target) t else null
            }
            .sortedByDescending { it.completedAt ?: 0L }
            .toList()
        return when {
            done.isEmpty() -> AssistantAnswer(
                "$day no has completado tareas todavía.",
                AssistantAction.NONE
            )
            done.size <= 3 -> AssistantAnswer(
                "$day completaste ${done.size}: " + done.joinToString(", ") { "«${it.title}»" } + ".",
                AssistantAction.NONE
            )
            else -> {
                val shown = done.take(3).joinToString(", ") { "«${it.title}»" }
                AssistantAnswer(
                    "$day completaste ${done.size}: $shown y ${done.size - 3} más.",
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
        if (!("que tengo" in query || "tengo para" in query || "que hay" in query ||
                "tengo algo" in query || "hay algo" in query)) return false
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
        // Plural ("¿qué tengo los viernes?"): las N fechas EXACTAS del weekday
        // objetivo en vez de sólo la siguiente. Se calcula antes del `when` para
        // poder ramificar antes que el weekday singular (la consulta plural también
        // casaría el token suelto y devolvería un único día). La membresía se
        // evalúa por las fechas discretas (no un intervalo continuo): evita mezclar
        // otro día de la semana que caiga dentro del horizonte. Ver
        // [pluralWeekdayDates] y [isScheduledInDates].
        val pluralDates = pluralWeekdayDates(query, today)
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
                val missedEmpty = mostUrgentMissedStart(active, now)
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
            val missed = mostUrgentMissedStart(active, now)
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
            "cuanto tiempo me queda" in query || "cuanto tiempo libre" in query ||
            "cuanto me queda" in query || "tengo tiempo libre" in query ||
            "tengo mucho que hacer" in query ||
            "cabe todo" in query || "cabe el dia" in query || "cabe hoy" in query ||
            "alcanzara" in query || "alcanzare" in query || "da alcance" in query ||
            "estoy saturad" in query

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
        // Recuento de hoy: exige "hoy" para no robar la agenda de otros días.
        if ("hoy" in query) {
            if ("cuantas tareas" in query || "cuantas tengo" in query ||
                "cuantos pendientes" in query || "cuantos pendiente" in query
            ) return true
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
        now: Long
    ): String {
        val missed = mostUrgentMissedStartExcluding(active, suggested.id, now) ?: return ""
        val minutes = TaskRules.plannedDuration(missed)
        return " Además, «${missed.title}» tenía su hueco y se pasó (~$minutes min)."
    }

    private fun mostUrgentMissedStart(active: List<TaskEntity>, now: Long): TaskEntity? =
        WhatNowEngine.ordered(active, now).firstOrNull { TaskRules.isMissedStart(it, now) }

    private fun mostUrgentMissedStartExcluding(
        active: List<TaskEntity>,
        excludeId: Long,
        now: Long
    ): TaskEntity? =
        WhatNowEngine.ordered(active, now).firstOrNull { TaskRules.isMissedStart(it, now) && it.id != excludeId }

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
        return " Además, $capturas en la bandeja $llevan una semana sin agendar."
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
        return " Además, $capturas en la bandeja $llevan una semana sin agendar."
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
    private fun nextCommitmentGapPhrase(gapMinutes: Int?, taskMinutes: Int): String {
        val gap = gapMinutes ?: return ""
        return when {
            taskMinutes > gap -> " Ojo: tu próxima cita es en ~$gap min."
            taskMinutes * 2 > gap -> " Te alcanza antes de tu próxima cita."
            else -> ""
        }
    }
}
