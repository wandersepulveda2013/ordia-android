# CURRENT_STATE — Ordía

> Fotografía ACTUAL del estado de Ordía. No es un historial; el historial está en `RUN_LOG.md`.
> Actualizar AL FINAL de cada sesión autónoma (reescribir, no acumular).

## Modo continuo (supervisor persistente)

- **Arquitectura de continuidad real**: `tools/ordia_supervisor.py` (+ `ordia_supervisor.sh`,
  `SUPERVISOR.md`). Un proceso persistente en una máquina siempre encendida del usuario orquesta
  la Automation `Ordía Continuous Evolution` (id `b3bd3870-…`), garantiza `MAX_CONCURRENT_RUNS=1`
  y encadena runs en ~15–40 s (no horas). Deshabilita el cron al arrancar y lo rehabilita al parar.
- **Sin supervisor**: el cron cada 15 min es modo degradado. **Con supervisor**: continuidad de
  segundos, 1 agente. Ver `tools/SUPERVISOR.md`.

## Estado

- **Fecha (UTC)**: 2026-08-13 (ciclo 54)
- **Branch de trabajo**: `openhands/autonomous-ordia` (HEAD tras ciclo 54; rebase no destructivo sobre ciclo 53 What Now + ciclo 52 snooze del run paralelo)
- **main**: contiene SOLO infraestructura de orquestación (workflows); no el rebuild de la app.
- **Workflows autónomos (en `main`)**: `ordia-autonomous-jules.yml` (cron `17 */2 * * *` + dispatch)
  y `ordia-autonomous-merge.yml` (pull_request_target + cron `*/15 * * * *` + dispatch).
- **Release workflow**: publica APK firmada en cada push a `openhands/autonomous-ordia` (incluso
  docs-only) → los commits de código generan releases automáticamente.

| P1/P2 | Parser — fechas relativas/pasadas/imposibles + rango horario + recurrencias laborables/quincenal/bare + día de mes suelto | FIXED → VERIFIED: "esta semana" c.34; "un par de" c.35; "mediados de semana" c.36; "a las N horas" c.37 cont. (316 tests); "a finales de semana" c.37 (319 tests); fechas pasadas "hace N"/"la semana/el mes pasado" + recuperación fechas imposibles (29 feb, 31 abr) c.38 (329 tests); fix "de/por/a la mañana" (hora) vs fecha "mañana" c.39 (336 tests); recordatorios con números escritos y fracciones c.40 (344 tests); listas de días sin coma + plurales sábados/domingos c.41 (350 tests); rango horario sin "horas" ambas < 13 c.42 base (353 tests) + ampliación followers c.42 cont. (358 tests); recurrencia quincenal "cada quincena"/"quincenalmente" c.42 (365 tests); día de semana suelto hoy con hora futura → hoy c.42 cont.2 (362 tests); listas de días sin prefijo ("gym sábados y domingos") c.42 (369 tests); "entre semana"/"días laborables/hábiles"/"de lunes a viernes" = WEEKLY [1-5] c.43 (376 tests); fecha/hito "la quincena" (1ra/2da/sin cualificar) c.44 (388 tests); `nextBestTask` time-aware (widget/asistente) c.45 (394 tests); **"el 15" día de mes suelto con artículo** c.47 (394+4 tests); **"de aquí a N"/"de acá a N" prefijo relativo coloquial** c.50 (413 tests); **DayPlanner conflicto startAt otro día** c.51 (415 tests); **intervalo+días "cada 2 semanas los lunes"/"cada quincena los lunes y viernes"/"cada 3 semanas de lunes a viernes"** c.54 (428 tests) |

## Último trabajo — Ciclo 54: Parser combina intervalo de cadencia + lista de días

Fix P1 de captura/recurrencia (`NaturalTaskParser.parseRecurrence`). Cuando una frase unía un
intervalo de cadencia con una lista de días (**"cada 2 semanas los lunes"**, **"cada quincena los
lunes y viernes"**, **"cada 3 semanas los martes y jueves"**, **"cada 2 semanas de lunes a viernes"**,
**"cada 2 semanas los findes"**), la rama WEEKLY+days del parser se quedaba solo con la lista de días
y devolvía `interval=1`: la rutina quedaba programada como **todas las semanas** aunque el usuario
pidió quincenal/cada-N-semanas (cadencia errónea), y la frase de intervalo ("cada 2 semanas") **quedaba
como residuo en el título**. Una rutina quincenal se convertía en semanal: tareas duplicadas, ruido y
recordatorios mal cadenciados. Verificado con probe: "Gym cada 2 semanas los lunes" → antes
`WEEKLY interval=1 days=1` + título `"Gym cada 2 semanas"`.

**Causa raíz**: las ramas de días (dayListPattern, weekdayRangePattern, weekdaySetPattern,
weekendRecurrencePattern) devolvían `interval=1` hardcoded e ignoraban cualquier intervalo explícito
presente en la frase; además solo añadían su propio rango a `phraseRanges`, dejando la frase de
intervalo sin consumir → residuo en el título.

**Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**: helper `detectWeekInterval()`
que detecta "cada N semanas" o "cada quincena/quincenalmente/todas las quincenas" en la frase y
devuelve `(interval, rango)`. Las ramas de días ahora consumen ese intervalo cuando existe (lo aplican
al `RecurrenceResult.interval` y añaden su rango a `phraseRanges` para limpiarlo del título). Sin
intervalo explícito → `interval=1` (cadencia semanal normal, sin regresión). Lógica local honesta, sin
random ni modelo simulado.

**Tests**: +6 en `NaturalTaskParserTest.kt` (`biweeklyIntervalWithDayListCombinesIntervalAndDays`,
`quincenaIntervalWithDayListCombinesIntervalAndDays`, `triweeklyIntervalWithMultipleDaysCombinesIntervalAndDays`,
`biweeklyIntervalWithWeekdayRangeCombinesIntervalAndDays`, `biweeklyIntervalWithWeekendCombinesIntervalAndDays`,
`dayListWithoutIntervalKeepsWeeklyInterval`). **428 domain tests PASS** (`bash tools/run_domain_tests.sh`,
26 clases — 421 base c.52 snooze + 6 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`). **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room
con DAOs reales, render real del parser en la app.

## Último trabajo — Ciclo 53: What Now desempata por prioridad (consistencia con el widget)

Fix P2 de consistencia de inteligencia entre `WhatNowEngine` (tarjeta "¿Qué hago ahora?" de
`TodayScreen`) y `TaskRules.nextBestTask` (widget de inicio + asistente + fallback del ViewModel).
Ambos ordenaban tareas por rango temporal, pero **solo `nextBestTask` desempataba por prioridad**
(`thenByDescending { priorityScore }`). `WhatNowEngine.suggest` saltaba directamente al `dueAt` más
próximo. Consecuencia real: con dos tareas atrasadas donde la NORMAL vencía ANTES que la URGENTE
(p.ej. normal ayer 9:00, urgente ayer 10:00), What Now sugería la normal mientras el widget
sugería la urgente — **dos respuestas distintas para el mismo conjunto de tareas**. Además
`priorityScore` existía duplicado como `private` en `TaskRules` y en `DayPlanner`, violando la
fuente única de verdad declarada para otras helpers (`isImminentStart`, `isDueToday`).

**Solución (mínima, sin nueva pantalla/botón)**:
- `TaskRules.priorityScore` ahora es **público** (fuente única de verdad del puntaje de prioridad).
- `WhatNowEngine` añade `thenByDescending { TaskRules.priorityScore(it.priority) }` ANTES del
  `dueAt`, replicando exactamente el orden de `nextBestTask` → ambas superficies sugieren la misma
  tarea.
- `DayPlanner` reutiliza `TaskRules.priorityScore` y se elimina su copia `private` duplicada (DRY).

Lógica local honesta, sin IA falsa ni random. Retrocompatible (sin cambios de firma pública).

**Colisión de remoto (no destructiva)**: al iniciar, el remoto ya estaba en `8275185` (ciclos 52
snooze + docs). Mi base local `d18fc32` era obsoleta (STALE_BASE). NO se forzó push: stash del
trabajo → fast-forward a `8275185` → re-aplicar SOLO mis 4 archivos que el remoto NO tocó
(verificado `git diff` vacío en `TaskRules/WhatNowEngine/DayPlanner/WhatNowEngineTest`). Sin
conflicto de código. Trabajo del ciclo 52 (ReminderRules) preservado íntegro.

**Tests**: +1 `picksUrgentOverNormalAmongOverdue` (reproduce el bug: dos atrasadas normal 9:00 vs
urgente 10:00 → antes del fix What Now elegía la normal; tras fix elige la urgente Y coincide con
`nextBestTask`). **422 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 421 base
c.52 + 1 nuevo), smoke 25 OK (`tools/run_domain_checks.sh`). **NO VERIFICADO**: gradle/lint/assemble/
Android/UI/Room con DAOs reales, render real de What Now en `TodayScreen`.

## Último trabajo — Ciclo 52: snooze ya no corrompe `reminderAt` en tareas recurrentes

Fix P1 de **integridad de datos** en recordatorios. `ReminderActionReceiver`
manejaba `ACTION_SNOOZE` sobrescribiendo `task.reminderAt = now + 10min`.
Para tareas **recurrentes** eso destruye el offset
`reminderOffset = dueAt - reminderAt` que `RecurrenceEngine.nextOccurrence`
reutiliza en **todas** las ocurrencias futuras: un recordatorio configurado a
"15 min antes" pasaba a ser "5 min antes" para siempre tras un solo snooze.
Mutación silenciosa e irreversible: una acción transitoria (aplazar la
notificación 10 min) corrompía permanentemente la preferencia de cuándo avisar
en cada futura repetición. Solo afectaba a recurrentes, lo que lo hacía sutil:
se manifiesta días/semanas después del snooze, cuando la siguiente ocurrencia
recuerda "demasiado tarde".

**Solución (mínima, sin nueva pantalla/botón)**: extraída la lógica de snooze
a `ReminderRules.snooze` (dominio puro, testable). El aplazamiento es
**transitorio**: `SnoozeResult.triggerAt` (now+10min) se pasa a
`ReminderScheduler.scheduleAt` y persiste en WorkManager (sobrevive a
reinicios sin tocar `reminderAt`). La preferencia original `reminderAt`
(pero también `dueAt`/`startAt`) se preserva intacta; solo se actualiza
`updatedAt = now`. `ReminderActionReceiver` delega a `ReminderRules.snooze`.
Único path de snooze en la app (verificado: `grep ACTION_SNOOZE` → solo
`TaskReminderWorker` construye el intent y `ReminderActionReceiver` lo
consume).

**Tests**: +6 en `ReminderRulesTest.kt`, incluida la invariante de integridad
`snoozeThenComplete_preservesReminderOffsetAcrossRecurrence` que reproduce el
bug: una tarea con `reminderAt = dueAt - 15min`, tras snooze (10 min) y
completar, la siguiente ocurrencia mantiene `reminderOffset = 15min`
(antes del fix el offset colapsaba a 5min). **421 domain tests PASS**
(`bash tools/run_domain_tests.sh`), smoke 25 OK. **NO VERIFICADO**:
gradle/lint/assemble/Android/UI/Room con DAOs reales, `ReminderScheduler`
real/WorkManager, receptor de broadcast en dispositivo.

## Último trabajo — Ciclo 51: DayPlanner no marca conflicto de hora si `startAt` es otro día

Fix P2 de fiabilidad del planificador diario (`DayPlanner.build`). El conflicto
`MOVED_FROM_SCHEDULED_TIME` (tarea con hora prevista que el plan reubica en otra hora) se computaba
comparando solo `hour*60+minute` del `startAt` original contra el `startMinute` del bloque planificado,
**sin verificar que el `startAt` cayera en el día del plan**. El comentario ya decía "tareas que ya
tenían hora prevista **ese día**", pero la implementación lo aplicaba a cualquier tarea con `startAt`.
Patrón real afectado: una tarea **iniciada ayer** (`startAt` ayer 15:00) que **vence hoy** era
reubicada por el plan de hoy y marcada falsamente como "hora movida", aunque nunca tuvo hora
asignada *hoy*. Ruido de conflictos espurios en el planificador.

**Solución (mínima, `DayPlanner.kt`, sin nueva pantalla/botón)**: antes de comparar la hora se verifica
`original.toLocalDate() == date`. Si el `startAt` es de otro día, no hay hora prevista "ese día" y no se
añade el conflicto. Si cae en el mismo día, el comportamiento previo se conserva (hora distinta →
conflicto real). Lógica local honesta, alineada con la intención documentada en el comentario.

**Colisión de remoto (no destructiva)**: al hacer `git fetch` el remoto ya estaba en `497010f`
(ciclo 50 = parser "de aquí a N" de otro run). Mi base local `4f7e701` era obsoleta (STALE_BASE).
NO se forzó push: stash del trabajo → fast-forward a `497010f` → re-aplicar SOLO los cambios de
código (`DayPlanner.kt`/`DayPlannerTest.kt`) que el remoto NO tocó (sin conflicto de código) → docs
reescritos sobre la base remota y reetiquetados ciclo 51. Trabajo del ciclo 50 del otro run preservado.
Sin force push, sin reset --hard, sin tocar `main`.

**Colisión 2 de remoto (rebase no destructivo)**: al hacer `git fetch` previo al push, el remoto había
avanzado `497010f`→`b3fc5f7` (otro run: `fix(manifest): resolve FileProvider merger conflict blocking
previewAdvanced CI`). Mi commit divergía (1 ahead / 1 behind). `git rebase origin/openhands/autonomous-ordia`
limpio (áreas ortogonales: planner vs manifest/previewAdvanced); 415 tests PASS re-confirmados tras rebase.
Push fast-forward OK: `b3fc5f7..bf11f31`. HEAD final: `bf11f31`.

**Tests**: +2 (`noConflictWhenStartAtIsOnADifferentDay`, `conflictStillReportedWhenStartAtIsOnSameDay`).
**415 domain tests PASS** (`bash tools/run_domain_tests.sh`), smoke 25 OK. **NO VERIFICADO**:
gradle/lint/assemble/Android/UI/Room con DAOs reales, render real del planner.

## Último trabajo — Ciclo 50: "de aquí a N"/"de acá a N" — prefijo relativo coloquial

Fix P1 de captura de plazos futuros (parser natural). Frases cotidianas como
**"llamar al dentista de aquí a tres días"**, **"reunión de aquí a una semana"**,
**"viaje de aquí a un mes"** NO se parseaban: `relativePattern` solo admitía los prefijos
`en`/`dentro de`, así que `dueAt` quedaba `null` y la frase **"de aquí a tres días"** permanecía
como residuo en el título. Tarea sin fecha → sin recordatorio, invisible en planificador/What Now,
**olvidada** (la forma coloquial más común de "en N" en español). La brecha era ortogonal a
`dayOfMonthPattern` (c.47) y a las fechas pasadas "hace N" (c.38); faltaba el prefijo coloquial
futuro. "para dentro de N" ya funcionaba ("para" se limpia, "dentro de" casaba).

**Solución (mínima, `NaturalTaskParser.kt`)**: extendido el grupo de prefijos de `relativePattern`
de `(?:en|dentro\s+de)` a
`(?:en|dentro\s+de|de\s+aqu[íi]\s+a|de\s+ac[aá]\s+a)`, añadiendo las variantes coloquiales
**"de aquí a"** / **"de acá a"** (con/sin tilde, `í`/`i` y `á`/`a`). El resto del patrón
(cantidad + unidad) y la lógica de cálculo (`relativeDueAt`, `now + millis`) se reutilizan sin
cambios; la limpieza del título (`working.replace(it.value, " ")`) ya elimina todo el match,
incluido el prefijo. Riesgo de falso positivo bajo: requiere `de aquí a`/`de acá a` + cantidad +
unidad temporal. Heurística honesta (no IA, no random).

**Colisiones de remoto (no destructivas, tres veces)**: el remoto avanzó tres veces durante el run,
todas ortogonales (ninguna tocó `NaturalTaskParser.kt` ni su test):
(1) `bf3579d`→`8950d07` (guardián doble conteo, WhatNow IMMINENT_START, nextBestTask compromisos).
(2) `8950d07`→`4cb5f0f` (DayPlanner "Vence hoy" c.48 de otro run + docs follow-up).
(3) `4cb5f0f`→`4f7e701` (búsqueda ranking por urgencia c.49 de otro run + docs follow-up).
Rutina segura repetida (`git stash push` solo del código → descartar docs → `git pull --ff-only`
→ `git stash pop` limpio; en la 3ra colisión además `git reset --soft` de mi commit previo y
rebase sobre la nueva base). Etiquetado ciclo 50 para no colisionar con el ciclo 49 del run
paralelo (búsqueda). Sin force push, sin reset --hard, sin tocar `main`.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**413 tests PASS** (408 base remota c.49 incl. SearchEngine + 5 nuevos: `deAquiATresDiasParsesDueAt`,
`deAquiAUnaSemanaParsesDueAt`, `deAquiAUnMesParsesDueAt`, `deAquiANDiasRespetaHoraExplicita`,
`deAcaAUnaSemanaParsesDueAt`), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`). Probe ad-hoc
confirmó los 5 casos reales antes de los tests. NO VERIFICADO: gradle/lint/assemble/Android/UI/Room
con DAOs reales (sin Android SDK).


## Último trabajo — Ciclo 49: búsqueda ordena por urgencia (no solo alfabético)

Unidad atómica de producto/inteligencia de la **búsqueda universal** (P2 — potencia sin más interfaz).
**Problema**: `SearchEngine.search` ordenaba resultados por prefijo de título y luego alfabético, sin
considerar urgencia. Al buscar "reunión" con dos tareas "Reunión equipo" —una atrasada+urgente, otra
normal sin fecha— la crítica podía quedar debajo por azar alfabético. La búsqueda devolvía matches sin
priorizar lo accionable, contrario al principio de Ordía de elevar lo atrasado/urgente en todas las
superficies (What Now, widget, guardián ya lo hacen; la búsqueda no).

**Solución (mínima, `SearchEngine.kt`, sin nueva pantalla/botón)**: wrapper interno `Ranked(result,
urgency, dueAt)` calculado al construir cada resultado. `urgencyRank(task, now)` reutiliza
`TaskRules.isOverdue`/`isDueToday` (fuente única de verdad) en orden honesto idéntico a `nextBestTask`:
atrasada+urgente > atrasada > urgente+vence-hoy > urgente > alta > vence-hoy > resto. Orden final:
**prefijo de título** (relevancia textual primero) → **urgencia** → **dueAt** → **alfabeto**. No-tareas
tienen urgencia neutral, así que un proyecto/nota que **prefija** el query sigue ganando (la relevancia
textual domina); una tarea atrasada que también prefija sube por encima. Heurística local honesta.

**VERIFICADO localmente (JVM puro)**: `bash tools/run_domain_tests.sh` = **408 tests PASS** (406 base
c.48 + 2 nuevos), 25 clases. Smoke 25 OK. NO VERIFICADO: gradle/lint/assemble/Android/UI/Room con DAOs
reales, render real de `SearchScreen`.

## Último trabajo — Ciclo 47 (run paralelo): `nextBestTask` alineado con IMMINENT_START (widget/asistente)

Unidad atómica de fiabilidad de inteligencia (P1 — consistencia entre superficies). El ciclo 46 añadió
la detección de **compromisos a punto de empezar** (`IMMINENT_START`, `startAt` futuro dentro de 15 min)
al **What Now** de `TodayScreen` (`WhatNowEngine`), pero **NO** a `TaskRules.nextBestTask`, que es la
heurística compartida por el **widget de inicio**, el **asistente** y el `nextTask` del ViewModel. Allí
un compromiso inminente seguía cayendo en `isScheduledLater` (rank -1, último recurso): el widget
sugería una tarea cualquiera de la Bandeja **mientras una reunión empezaba en 5 min** — la superficie más
vista daba una respuesta menos oportuna que la pantalla principal, justo el olvido que Ordía debe evitar.

**Solución (mínima, en `TaskRules.kt` + DRY)**:
- `isImminentStart(task, now)` + `IMMINENT_WINDOW_MINUTES = 15` ahora viven en `TaskRules`
  (públicos, fuente única de verdad). `WhatNowEngine.isImminentStart` se redujo a un delegado
  (`TaskRules.isImminentStart`), eliminando la constante duplicada. Sin cambio de comportamiento
  en What Now (mismas 4 pruebas).
- `TaskRules.timeRank` añade la rama inminente (rank 4, entre OVERDUE y SCHEDULED_LATER) en
  el mismo orden honesto que `WhatNowEngine`: EN_CURSO > EN_PROGRESO > ATRASADA > **INMINENTE** >
  VENCE_HOY > URGENTE > ALTA > BANDEJA. Una tarea atrasada sigue ganando a un compromiso que aún
  no empieza.
- Retrocompatible: la firma `nextBestTask(tasks)` existente sigue delegando con `now`/zona por
  defecto → `OrdiaWidgetProvider`, `AssistantEngine`, `OrdiaViewModel` compilan sin cambio. Sin
  nueva pantalla, sin nuevo botón: misma heurística, consistente en todas las superficies.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**404 tests PASS** (392 base c.46 + 1 guardian run paralelo + 4 parser "el 15" + 2 guardian doble conteo + 5 nuevos de este ciclo: 3 de `nextBestTask` inminente), 25 clases. (404 verificado tras rebase)
Smoke 25 OK (`tools/run_domain_checks.sh`). NO VERIFICADO: gradle/lint/assemble/Android/UI/Room con DAOs
reales, render real del widget (sin Android SDK).

## Último trabajo — Ciclo 46 (run paralelo): What Now detecta compromisos a punto de empezar (IMMINENT_START)

Unidad atómica de inteligencia del "¿Qué hago ahora?" (P1 — evitar olvidos de compromisos inminentes).
**Problema**: un compromiso programado con `startAt` futuro (reunión/llamada/cita) que empieza en
pocos minutos **no aparecía** en "¿Qué hago ahora?" aunque sea lo más urgente del momento: el ranking
lo trataba como `SCHEDULED_LATER` (rank -1, último recurso) siempre que `startAt > now`, sin importar
si faltaban 5 min o 5 horas. Así una reunión a las 10:05 escrita a las 10:00 quedaba enterrada bajo
una tarea cualquiera de la Bandeja — justo el tipo de olvido que Ordía debe evitar.

**Solución (mínima, en `WhatNowEngine.kt`)**:
- Nuevo `WhatNowReason.IMMINENT_START` (entre OVERDUE y DUE_TODAY en el ranking, rank 4).
- `isImminentStart(task, now)`: `startAt` futuro y dentro de `IMMINENT_WINDOW_MINUTES = 15`.
  Las que empiezan más allá de 15 min siguen como `SCHEDULED_LATER` (sin cambio).
- Orden de prioridad honesto: EN_CURSO > EN_PROGRESO > ATRASADA > INMINENTE > VENCE_HOY >
  URGENTE > ALTA > BANDEJA. Una tarea atrasada sigue ganando a un compromiso que aún no empieza
  (no regresas lo pasado por lo inminente).
- UI: nueva etiqueta `what_now_reason_imminent` = "Empieza enseguida" en `TodayScreen.kt`
  (rama `when` exhaustiva añadida). Sin nueva pantalla, sin nuevo botón: misma sugerencia,
  mejor priorización. Menos interfaz, más potencia.

**Colisión de remoto resuelta (no destructiva)**: el remoto había avanzado a `966b799` (ciclos 44–45
del otro run: "la quincena" como hito + `nextBestTask` time-aware). `git fetch` + `git rebase
origin/openhands/autonomous-ordia` (no destructivo, sin force): auto-merge limpio en
`WhatNowEngine.kt`/`TodayScreen.kt`/strings/test (cambios ortogonales); conflicto solo en
`CURRENT_STATE.md` resuelto conservando el trabajo del otro run y renumerando el mío a **ciclo 46**.
Sin force push, sin reset --hard.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**392 tests PASS** (388 base remota c.45 + 4 nuevos), 25 clases. Smoke 25 OK
(`tools/run_domain_checks.sh`). NO VERIFICADO: gradle/lint/assemble/Android/UI/Room con DAOs
reales (sin Android SDK).

| P2 | Inteligencia — `nextBestTask` time-aware (widget/asistente alineado con What Now) | FIXED (c.45, run paralelo `2ef4bfa`): ordena en-curso-ahora > vencida > vence-hoy > urgente > alta > resto; `isDueToday` zona-aware. 388 tests. |
| P1 | Inteligencia/coach — `GuardianEngine.overdue` infla subtareas vs `SummaryEngine` | FIXED (c.46): filtra `parentTaskId == null` (alineado con resumen) + expone `overdue` en `Snapshot`. 391 tests. |
| P1 | Parser — "el 15" día del mes suelto con artículo | FIXED (c.47): `dayOfMonthPattern` ("el N"/"el N del mes") con lookahead anti-colisión; reutiliza `nextMonthlyDate`. 394 tests. |
| P2 | Planificador — etiqueta "Vence hoy" incorrecta en planes de otra fecha | FIXED (c.48): `DayPlanner.planReason` distingue "Vence hoy" (vence el día real de hoy) de "Vence este día" (vence el día del plan ≠ hoy). 399 tests. |

## Último trabajo — Ciclo 48: planificador — "Vence hoy" era falso en planes de otra fecha

Fix P2 de corrección de UX/urgencia en el planificador. `DayPlanner.planReason` etiquetaba como
`DUE_TODAY` ("Vence hoy") a **toda** tarea con `dueAt`, sin mirar la `date` del plan. Pero el
planificador se construye para la `selectedDate` que el usuario elige (y `AutomationEngine.PLAN_DAY`
siempre usa hoy). Consecuencia: al abrir el plan de **mañana** (o cualquier fecha futura), una tarea
que vence ese día mostraba **"Vence hoy"** — urgencia falsa que confunde al usuario sobre qué vence
realmente hoy. La etiqueta decía "hoy" cuando no lo era.

**Causa raíz**: `planReason(task, now)` ignoraba la `date` del plan; el `when` mapeaba cualquier
`dueAt != null` a `DUE_TODAY` de forma incondicional.

**Solución (mínima, `DayPlanner.kt`)**: `planReason` ahora recibe `date`+`zone` y compara la fecha
de vencimiento con el día **real de hoy** (`DateRules.toLocalDate(now, zone)`): si coincide →
`DUE_TODAY` ("Vence hoy"); si no → nuevo `DUE_ON_DATE` ("Vence este día"). La urgencia real de "hoy"
ya no se degrada con la vista: una tarea que vence hoy sigue siendo `DUE_TODAY` aunque se muestre en
un plan de otra fecha, y una que vence otro día no finge ser "hoy". Añadida rama UI en
`PlannerScreen.plannerReasonLabel` + string `planner_reason_due_on_date` = "Vence este día".
Heurística honesta (no IA, no random). Sin nueva pantalla, sin nuevo botón — solo precisión.

**Colisión de remoto (no destructiva)**: al iniciar, `git pull --ff-only` trajo los ciclos 45–47
(`2ef4bfa`/`d98862b`/`a934b65`/`fc1279b`/`6d0c6a4`/`cb042c7`→`52a4736`); base actualizada sin
divergencia. Sin force push, sin reset --hard.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**399 tests PASS** (393 base remota tras integrar ciclos 45–47 + 2 nuevos `dueOnFuturePlanDateIsNotLabeledAsToday`
y `dueTodayIsLabeledAsTodayEvenOnFuturePlanDate`, 25 clases). Smoke 25 OK no ejecutado por falta de
`kotlinc` en PATH en este run (libs presentes); el smoke es subconjunto del suite de dominio ya
pasado. NO VERIFICADO: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).

## Último trabajo — Ciclo 47: "el 15" — día del mes suelto con artículo

Fix P1 de captura de citas (parser natural). Frases cotidianas como **"reunión el 15 a las 10"**,
**"cita el 20"**, **"entregar el 5 a las 18"** se fechaban en HOY por error: "el 15" no casaba con
`numericDatePattern` (que exige `DD/MM` con mes) y quedaba como residuo en el título; la hora suelta
("a las 10") se aplicaba entonces a HOY → la cita se programaba hoy en vez del día 15. Día erróneo
→ reunión perdida (no aparece como atrasada ese día, recordatorio dispara hoy). La brecha era
ortogonal a `monthNameDate` ("el 15 de marzo" sí funcionaba) y a la recurrencia mensual
("el 15 de cada mes"); faltaba el "el N" aislado.

**Solución (mínima, `NaturalTaskParser.kt`)**: nuevo `dayOfMonthPattern` = `el N` / `el N del mes`
con *negative lookahead* `(?!\s*de\s+[a-záéíóúüñ])` para no colisionar con "el 15 de marzo" (lo
resuelve `monthNameDate`, evaluado antes) ni "el 15 de cada mes" (recurrencia mensual). Se ancla al
día N reutilizando el helper existente `nextMonthlyDate(from, day)` (día N de este mes, o del
siguiente si ya pasó). Rama nueva en el `when` de `date`, entre `monthNameDate` y `numericDateMatch`,
para que "el 15 de marzo" gana y "el 15" aislado no caiga al fallback de hoy. Limpieza del residuo
"el 15" en el título. Heurística honesta (no IA, no random).

**Colisión de remoto (no destructiva)**: durante el run el remoto avanzó varias veces (ciclos 43–46:
"la quincena" `d98862b`, "entre semana"/"de lunes a viernes" `a934b65`, recurrencia de listas de
días bare `fc1279b`, `nextBestTask` time-aware `2ef4bfa`, guardián overdue raíz `6d0c6a4`).
`git stash` → `git pull --ff-only` → `git stash pop`; auto-merge limpio en `NaturalTaskParser.kt`
+ tests (cambios ortogonales); conflicto solo en `CURRENT_STATE.md`/`RUN_LOG.md` (docs) resuelto
conservando el trabajo del otro run y renumerando el mío a **ciclo 47**. Sin force push, sin reset
--hard.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**394 tests PASS** (390 base remota + 4 nuevos, 25 clases). Smoke 25 OK
(`tools/run_domain_checks.sh`). NO VERIFICADO: gradle/lint/assemble/Android/UI/Room con DAOs
reales (sin Android SDK).

## Último trabajo — Ciclo 46: guardián cuenta atrasados solo como tareas raíz (consistencia con resumen)

Fix P1 de consistencia/inteligencia (no parser, no pérdida de datos). `GuardianEngine.snapshot`
contaba los atrasados (`overdue`) **incluyendo subtareas**: un padre atrasado con 2 subtareas
también atrasadas → el guardián veía **3** atrasados, mientras la tarjeta de resumen
(`SummaryEngine`, que filtra `parentTaskId == null`) mostraba **1**. Dos superficies daban
números contradictorios al usuario. El invariant "las subtareas son anidadas, no se cuentan
además del padre" ya estaba fijado en `SummaryEngine` (test `overdueCountsRootTaskNotNestedSubtasks`)
pero **no** en el guardián. El `overdue` del guardián alimenta el ánimo (`CONCERNED` si `>= 5`),
el mensaje ("Hay N pendientes atrasados") y la acción sugerida → la inflación era visible y
afectaba al "coach".

**Solución (mínima, `GuardianEngine.kt`)**: filtrar `it.parentTaskId == null` en el conteo de
`overdue` (alineado con `SummaryEngine`). Además, exponer `overdue: Int` en `Snapshot` (campo
añadido al final de la data class, retrocompatible: nadie construye `Snapshot` posicionalmente;
la UI lee por nombre) para que el invariant sea verificable y la superficie pueda mostrar el mismo
número que el resumen. `completedToday`/XP siguen contando subtareas a propósito (progreso granular
deliberado) — solo el conteo de atrasados se alinea con la definición global.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**391 tests PASS** (388 base remota c.45 + 3 del run paralelo `nextBestTask` + 1 nuevo), 25 clases.
Smoke 25 OK (`tools/run_domain_checks.sh`). NO VERIFICADO: gradle/lint/assemble/Android/UI/Room con
DAOs reales (sin Android SDK). Render real del guardián/overlay.

## Último trabajo — Ciclo 45: `nextBestTask` time-aware (inteligencia widget/asistente)

Mejora funcional de inteligencia local (P2 — "menos interfaz, más potencia"). `TaskRules.nextBestTask`
(heurística "¿qué hago ahora?" usada por el **widget de inicio**, el **asistente** y el fallback
`nextTask` del ViewModel) ordenaba solo por `overdue > prioridad > dueAt > createdAt` e **ignoraba
el tiempo**: una tarea ocurriendo **ahora mismo** (`startAt<=now<=startAt+dur`, p.ej. una reunión
a las 15:00 siendo las 15:00) no se priorizaba, y una **URGENTE para mañana** se sugería antes
que una **NORMAL que vence hoy**. `WhatNowEngine` (tarjeta What Now de `TodayScreen`) sí era
time-aware, dejando al widget/asistente dando una respuesta menos oportuna que la pantalla
principal — y el widget es la superficie más vista.

**Solución (mínima, en `TaskRules.kt`)**: `nextBestTask(tasks, now, zone)` con orden honesto (no IA,
no random): (1) en curso ahora (`startAt<=now<=startAt+dur`), (2) vencida, (3) vence hoy,
(4) urgente, (5) alta, (6) resto por inbox/orden/creación; las programadas para más tarde quedan
últimas. Nuevos helpers `isInProgressNow` (privado) e `isDueToday` (público, zona-aware, ya usado
por `GuardianCoach`). Retrocompatible: la firma existente `nextBestTask(tasks)` delega con
`now`/zona por defecto → `OrdiaWidgetProvider`, `AssistantEngine`, `OrdiaViewModel` siguen
compilando sin cambio.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**386 tests PASS** (384 base remota + 2 nuevos de este ciclo, 25 clases). Smoke 25 OK
(`tools/run_domain_checks.sh`). NO VERIFICADO: gradle/lint/assemble/Android/UI/Room con DAOs
reales, render real del widget (sin Android SDK).

## Último trabajo — Ciclo 43: parser "entre semana"/"días laborables"/"de lunes a viernes" = recurrencia semanal Lun–Vie

Unidad atómica del ciclo de parser natural (P1 — evitar olvidos + fricción de captura). Frases
cotidianas para hábitos laborables ("Gimnasio entre semana", "Trabajo de lunes a viernes",
"Estudiar días laborables", "Reunión los días hábiles") **no generaban recurrencia**: el parser
las trataba como texto suelto → tarea única (freq=NONE) que aparece una sola vez y se olvida
el resto de la semana. Peor, "de lunes a viernes" dejaba "lunes" como residuo en el título
(`dayListPattern` capturaba solo "lunes", days=[1]) y el viernes se perdía. Brecha simétrica
frente a `weekendRecurrencePattern` (fines de semana → WEEKLY sáb+dom, c.33).

**Solución (mínima, en `NaturalTaskParser.kt`)**:
- `weekdayRangePattern`: nuevo patrón `(los |de )?(lunes|martes|miércoles|jueves|viernes)\s+a\s+(martes|…|domingo)` (rango Lun–Vie, admite prefijo `los `/`de `). Si el rango termina en viernes (o incluye viernes), → `RecurrenceFrequency.WEEKLY`, `days=[1,2,3,4,5]` (hábito laboral). Resuelve a la **próxima ocurrencia** del primer día (jue 30-07 dado now=mié 29-07 12:00, slot ya pasado hoy).
- `weekdaySetPattern`: variantes léxicas equivalentes → mismo WEEKLY [1-5]: `entre semana`, `días laborables`, `días hábiles`, `días de semana`, `de semana` (con prefijo opcional `los `/`de `). Consumen la frase completa (título limpio).
- **Orden de patrones crítico**: ambos se evalúan **ANTES** que `dayListPattern` para que "los lunes a viernes" sea rango (days=[1..5]) y no la lista ["lunes"] (days=[1]). El singular "fin de semana" sigue siendo fecha única (próximo sábado), sin colisión.

**Colisión de remoto resuelta (no destructiva)**: durante el run el remoto avanzó varios commits
(ciclos 38–42: "de/por/a la mañana", recordatorios con números escritos/fracciones, listas de
días sin coma + plurales sábados/domingos, rango horario sin "horas", recurrencia quincenal).
Procedimiento no destructivo: `git rebase` de mi commit sobre el HEAD remoto; auto-merge limpio en
`NaturalTaskParser.kt` + tests (cambios ortogonales); conflictos solo en docs
(`CURRENT_STATE.md`, `RUN_LOG.md`) resueltos conservando el trabajo del otro run y
renumerando el mío a **ciclo 43** (la otra ejecución ya había reclamado los números 41 y 42). Sin
force push, sin reset --hard.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**372 tests PASS** (365 base remota + 7 nuevos de este ciclo, 25 clases). Smoke 25 OK
(`tools/run_domain_checks.sh`). NO VERIFICADO: gradle/lint/assemble/Android/UI/Room con DAOs
reales (sin Android SDK).

Bug de captura P2 (ciclo 42): el rango horario **sin la palabra "horas"** y con ambas horas < 13
("clase de 9 a 11") no se reconocía: `durationMinutes=null` y "de 9 a 11" quedaba como residuo.
Un run paralelo (`0a77387`) envió el fix base (aceptar el rango si no le sigue sustantivo de
cantidad, con set de followers básicos). **Este run** detectó que ese set dejaba residuo cuando
el rango iba seguido de un día de la semana ("el viernes"), un día relativo ("mañana") o un
marcador de parte del día ("a la tarde", "por la noche"), y lo amplió. Sigue rechazando
"comprar de 2 a 5 entradas". Heurística honesta, conservadora.

VERIFICADO localmente (JVM puro, sin Android SDK): `bash tools/run_domain_tests.sh` =
**369 tests PASS** (365 base remota c.42 + 4 nuevos de listas bare), 25 clases. Smoke 25 OK.
NO VERIFICADO: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).

## Último trabajo — Ciclo 42 (cont. 2): día de semana suelto hoy con hora futura vence hoy

Fix P1 de captura de citas de hoy (parser natural).

**Problema**: **"el viernes a las 18"** escrito el propio viernes **antes** de las 18:00 se
programaba para el **viernes de la semana siguiente**: la cita de hoy se perdía una semana
entera (reunión/cita olvidada hoy, recordatorio 7 días tarde). Causa raíz: la rama de fecha
suelta usaba `nextWeekday`, que **siempre** salta +7 cuando el día objetivo es hoy (correcto para
recurrencias —necesitan "próximo" estricto— pero incorrecto para una fecha suelta puntual). No
existía path para "hoy si aún no llegó la hora".

**Solución (mínima, `NaturalTaskParser.kt`)**: nueva `nextWeekdayOrSame(from, target)` — devuelve
hoy si el día objetivo coincide con el de `from`, si no delega a `nextWeekday`. La rama de fecha
suelta (weekday blando) usa `nextWeekdayOrSame`; el descarte de "hoy si la hora ya pasó" se difiere
al combinar fecha+hora: si fecha+hora resulta pasada respecto a `now`, se rueda +7 días (sin agenda
en pasado, sin regresión). Las recurrencias siguen usando `nextWeekday` (próximo estricto, sin
cambio). Heurística honesta (no IA).

**Colisión con run paralelo (no destructiva)**: el push inicial se rechazó por divergencia (remoto
avanzó de `0a77387` a `727e7b8` por un run paralelo). `git fetch` + `git rebase
origin/openhands/autonomous-ordia` (no destructivo, sin force) integró limpio sobre el nuevo HEAD.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**369 tests PASS** (365 base remota + 4 nuevos de listas bare), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`).
NO VERIFICADO: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).

## Último trabajo — Ciclo 42 (cont.): rango horario sin "horas" + ampliación de followers seguros

Mejora aditiva sobre el fix base del rango horario (P2 — captura de bloque horario cotidiano).

**Colisión con run paralelo (no destructiva)**: mi base local (`91c8b9f`) estaba por detrás del
remoto: `0a77387` (run paralelo) ya había resuelto el mismo backlog item con un enfoque
equivalente (rango sin unidad y horas < 13 aceptado si no le sigue sustantivo de cantidad).
Descarté mi implementación competidora vía `git stash` + `git pull --ff-only` (sin force push,
sin reset --hard) y reconstruí sobre `0a77387`. Sin STALE_RUN destructivo. Aporté una mejora
aditiva no duplicativa sobre el fix base.

**Mejora aditiva**: el set de followers seguros del fix base dejaba residuo en tres clases de
frases cotidianas — rango + día de la semana ("clase de 9 a 11 el viernes"), rango + día
relativo ("taller de 10 a 12 mañana") y rango + parte del día con conector no listado ("curso
de 4 a 6 a la tarde", "turno de 9 a 11 por la noche"). Causa raíz: el regex `followedByCount`
sólo incluía conectores básicos (con/y/o/para/hasta/luego/después/pero/porque + puntuación).
Solución: ampliar el regex con artículos (el/la/los/las/un/una), a/al, por, sin, sobre, desde,
del, días de la semana y días relativos (mañana/hoy/ayer). El rechazo de "comprar de 2 a 5
entradas"/"de 2 a 5 personas" se preserva (sustantivo contable sigue fuera del set).

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**369 tests PASS** (365 base remota + 4 nuevos de listas bare), 25 clases. Smoke 25 OK.
NO VERIFICADO: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).

## Último trabajo — Ciclo 42: parser listas de días sin prefijo ("gym sábados y domingos")

Unidad atómica del ciclo de parser natural (P1 — pérdida de datos silenciosa en rutinas). La forma
**bare** de lista de días (sin "los"/"cada"/"todos los") es tan común como la prefijada: **"gym
sábados y domingos"**, **"fútbol domingos a las 18"**, **"lavar auto sábados domingos"**. El
parser solo casaba listas con prefijo, así estas caían sin recurrencia (`recurrence=NONE`) y los
días quedaban como residuo en el título → la rutina semanal se **olvidaba en silencio** y los
recordatorios no disparaban nunca. Complementario al ciclo 41 (separador opcional), pero distinto:
aquí el problema era la ausencia de artículo prefijo.

**Solución (mínima, `NaturalTaskParser.kt` — `parseRecurrence()`)**: reconocer listas bare de 2+
días como recurrencia WEEKLY sin exigir "los"/"cada". Un día plural marcado ("domingos",
"sábados") en solitario también es recurrencia (hábito semanal explícito). Un día suelto **no
plural** ("reunión martes") sigue siendo **fecha única** (es ambiguo: ¿fecha o recurrencia?),
para no programar una rutina equivocada. `dayNameRegex` garantiza que el match solo consume
nombres de día reales, sin robar texto ajeno.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**357 tests PASS** (350 base + 4 nuevos: `parsesBareDayListRecurrence`,
`parsesBareDayListWithExplicitTime`, `parsesBarePluralSingleDayRecurrence`,
`bareSingleNonPluralDayIsNotRecurrence`), 25 clases. Smoke 25 OK. NO VERIFICADO:
gradle/lint/assemble/Android/UI/Room (sin Android SDK).

## Último trabajo — Ciclo 38: fechas pasadas + recuperación de fechas imposibles

Dos unidades atómicas del ciclo de parser natural (P1 — evitar olvidos + datos erróneos).


## Último trabajo — Ciclo 42: parser rango horario sin "horas" (ambas < 13)

Unidad atómica del parser natural (P2 — forma cotidiana no reconocida). **"clase de 9 a 11"**,
**"taller de 10 a 12"** (formato 12h sin la palabra "horas") caían a `dueAt=null`,
`durationMinutes=null` con el rango crudo ("9 a 11") como residuo en el título. El `timeRangePattern`
casa, pero el guard lo rechazaba: exigía unidad final ("horas"/"hs"/"h") o alguna hora ≥ 13
(24h inequívoco) para evitar falsos positivos como **"comprar de 2 a 5 entradas"** (cantidad, no horario).

**Solución (mínima, `NaturalTaskParser.kt`)**: heurística honesta (no IA): un rango sin unidad y
ambas horas < 13 se acepta como ventana horaria **solo si NO va seguido de un sustantivo de
cantidad**. Si tras el rango hay fin de cadena o un conector/preposición/puntuación
("con Juan", "y luego", ", después") se entiende como horario; si hay un sustantivo después
("entradas", "personas") se respeta como cantidad. Así "clase de 9 a 11" → dur 120,
título "Clase"; "comprar de 2 a 5 entradas" → sin duración, título intacto. Restricción
`end - start in 1..11` evita rangos absurdos.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**353 tests PASS** (350 base c.41 + 3 nuevos), 25 clases. Smoke 25 OK. NO VERIFICADO:
gradle/lint/assemble/Android/UI/Room (sin Android SDK).

## Último trabajo — Ciclo 41: parser listas de días sin coma + plurales sábados/domingos

Unidad atómica del ciclo de parser natural (P1 — pérdida de datos silenciosa en rutinas). **"los lunes miércoles y viernes"** (forma informal en español, sin coma entre los dos primeros días) era tan común como la forma con coma, pero el parser exigía conector ","/"y" entre cada par: capturaba solo "lunes" y dejaba "miércoles y viernes" como residuo en el título → la rutina se repetía **un solo día** en silencio y los recordatorios no disparaban en los días perdidos. Adicionalmente, los plurales **"sábados"/"domingos"** no casaban (patrón singular con `\b`) y se perdían también. Complementario al ciclo 20 (que añadió el conector ","/"y").

**Solución (mínima, `NaturalTaskParser.kt`)**: separador **opcional** en `dayListPattern` (`(?:,|y)?`): como los nombres de día son palabras cerradas y específicas, admitir separador vacío solo casa cuando la palabra siguiente es otro día, sin riesgo de robar texto ajeno ("los lunes con el equipo" para en "lunes" porque "con" no es un día). Plural `s[aá]bados?|domingos?`.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` = **350 tests PASS** (incluye 3 nuevos de este ciclo + 3 del run concurrente `60007d1` sobre la misma feature, casos distintos; coexisten con tests de los ciclos 36-40), 25 clases. Smoke 25 OK. NO VERIFICADO: gradle/lint/assemble/Android/UI/Room (sin Android SDK).

**Nota de integración**: rebase no destructivo sobre `origin/openhands/autonomous-ordia` (otras runs avanzaron a ciclos 36–40: "a las N horas", fechas pasadas, recuperación de fechas imposibles, "a finales de semana", "de/por/a la mañana" vs fecha "mañana", recordatorios con números escritos y fracciones); este trabajo se renumera a ciclo 41 para evitar colisión. Conflictos de docs resueltos tomando base remota y reinsertando esta sección. Auto-merge limpio en `NaturalTaskParser.kt` + test (cambios ortogonales).

## Último trabajo — Ciclo 37: parser "a las N horas" (hora, no duración falsa)

Unidad atómica del ciclo de parser natural (P1 — corrección de bug que generaba datos
erróneos). **"a las N horas"** es la forma más natural de dar una hora en reloj de 24h
con sufijo "horas" ("reunión a las 9 horas", "clase a las 10 horas"). El parser **NO la
reconocía como hora**: el `timePattern` no consumía el sufijo "horas", así que "9 horas"
era **robado por `durationMatch`** como una duración falsa de **540 minutos** (9x60), y "a las"
quedaba como residuo en el título. Consecuencia: la tarea recibía una duración absurda y
**ninguna hora real** → recordatorio y planificación incorrectos. Bug doble porque, al añadir
la guardia para descartar "N horas" como duración, el filtro se aplicaba al ganador global tras
`minByOrNull`, descartando **TODOS** los matches de duración (incluido "durante 1h" válido)
cuando había algún "N horas" inválido presente. Además los conectores "durante"/"por" no
se limpiaban del título tras extraer la duración.

**1. Fechas pasadas "hace N"/"la semana/el mes pasado"** (commit `ff3a1f4`).
El usuario registra una tarea ya vencida ("pagué hace 2 días", "revisé el informe la
semana pasada", "reunión el mes pasado"). Antes estas formas quedaban **SIN fecha**
(`dueAt=null` → sin recordatorio, invisible en What Now/planificador) **Y** con la frase
temporal intacta como basura en el título. Causa raíz: no existían `agoPattern` ("hace N")
ni `lastPeriodPattern` ("la semana/el mes/el año pasado"); además `previousWeekdayPattern`
capturaba "el mes pasado" (grupo1="mes", no es día → sin fecha) y **borraba** la frase.
Solución: nuevos `agoPattern` (resta N días/semanas/meses/años; "hace poco"/"hace un
rato" = -3h, heurística honesta de "recién") y `lastPeriodPattern` (resta 7d/30d/365d),
detectados **antes** de `previousWeekdayPattern` e integrados al **inicio** de la cadena
`effectiveRelativeDueAt` (las fechas pasadas son explícitas y tienen prioridad sobre fechas
futuras ambiguas). La hora explícita se aplica sobre la fecha pasada (tarea vencida con hora).

**2. Recuperación de fechas imposibles** (commit `265fc93`).
`parseMonthNameDate` usaba `LocalDate.of(year, month, day)` que lanza `DateTimeException`
para fechas imposibles ("el 29 de febrero" en año no bisiesto, "el 31 de abril"). El
`runCatching` devolvía `null` → caía al fallback que **deja la frase temporal en el título**
y `dueAt=null` (tarea sin fecha y con basura). El usuario que escribe "el 29 de febrero"
claramente quiere una fecha real, no perderla. Solución: en vez de descartar, **recuperar**
con `java.time.Year`/`YearMonth`: Feb 29 no bisiesto → siguiente año bisiesto (2028);
día > máx del mes (31 abr) → clamp al último día válido del **siguiente año** (30 abr
2027); Feb 30 → Feb 28. Así la frase se reconoce, se borra del título y la tarea obtiene
una fecha útil (no se pierde).

**Colisión de remoto resuelta (no destructiva)**: durante el run el remoto avanzó dos veces
(runs paralelos: "mediados de semana"/"un par de" y luego "a las N horas" ciclo 37).
Rebase de mis 2 commits sobre el remoto; conflicto en `NaturalTaskParser.kt` (remote añadió
`startOfWeekDueAt`/`midOfWeekDueAt`; local añadió `agoDueAt`/`lastPeriodDueAt`) resuelto
combinando ambos conjuntos en la cadena `effectiveRelativeDueAt`. Conflicto en el test file
(ambos añadieron tests al final) resuelto conservando ambos conjuntos. Sin STALE_RUN, sin
force push, sin reset --hard.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**329 tests PASS** (25 clases). Smoke 25 OK (`tools/run_domain_checks.sh`). NO VERIFICADO:
gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).

### Ciclos parser recientes (resumen)
- Ciclo 43: "entre semana"/"días laborables/hábiles"/"de lunes a viernes" = WEEKLY Lun–Vie (7 tests, 372 total)).
- Ciclo 41: listas de días sin coma + plurales sábados/domingos (3 tests, 350 total).
- Ciclo 40: recordatorios con números escritos y fracciones (8 tests, 344 total).
- Ciclo 39: "de/por/a la mañana" (hora) vs fecha "mañana" (336 tests).
- Ciclo 38: fechas pasadas "hace N"/"la semana/el mes pasado" + recuperación fechas imposibles (329 tests).
- Ciclo 37: "a las N horas" como hora, no duración falsa (3 tests, incluidos en 336).
- Ciclo 36: "mediados de semana" = miércoles (4 tests).
- Ciclo 35: "un par de" coloquial = 2 (4 tests).
- Ciclo 34: "esta semana" (próximo domingo) + "principios de semana" (lunes).
- Ciclo 33: "principios de mes" (día 1), "fines de semana" recurrencia WEEKLY sáb+dom, días pasados ("el jueves pasado").
- Ciclo 32 (cont.4): adjuntos copiados a almacenamiento interno (`AttachmentStorage` + FileProvider) — P1 persistencia.
- Ciclo 31: fechas relativas semanas/meses + ayer/anteayer.

## Riesgos / bloqueos

- **P1 OPEN — adjuntos de captura guardan URI externo**: `OrdiaViewModel.attachCaptureIfPresent`
  guarda `attachmentUri` (URI externo) en `AttachmentEntity.uri` sin copiar el contenido a
  almacenamiento interno. Si `takePersistableUriPermission` falla o el permiso se revoca, el
  adjunto queda inaccesible tras reinicio. Mitigación parcial ciclo 28 (Log.w). **Solución
  robusta pendiente**: copiar bytes a `filesDir` + migración de adjuntos existentes. Requiere
  sesión dedicada (BACKLOG).
- **BLOQUEO EXTERNO — keystore**: los 4 secrets `ORDIA_UPDATE_KEYSTORE_*` deben cargarse por el
  usuario una sola vez (`tools/keystore/README.md`). El agente no puede gestionar Actions secrets
  (HTTP 403). Hasta entonces CI compila+testea+ensambla pero el workflow de firma falla en el
  guard. (NOTA: las releases v3.0.12–v3.0.23 SÍ están firmadas — el keystore ya está cargado en
  este entorno; el bloqueo aplica a entornos nuevos.)
- **Sin emulador Android** en el agente: la prueba N→N+1 end-to-end de self-update real y la
  verificación de variantes 6x (Safe/Full/Advanced x debug/release) NO se ejecutan; cubiertas
  solo por tests unitarios contract + verificación estática de APK firmada.

## Pendientes principales (ver BACKLOG.md)

| Pri | Área | Estado |
|-----|------|--------|
| P1 | Persistencia — adjuntos URI externo | FIXED (NO VERIFICADO Android) ciclo 32 cont.4 |
| P1 | Parser — fechas relativas/pasadas/imposibles | FIXED → VERIFIED: "esta semana" c.34; "un par de" c.35; "mediados de semana" c.36; "a las N horas" c.37 cont. (316 tests); "a finales de semana" c.37 (319 tests); fechas pasadas "hace N"/"la semana/el mes pasado" + recuperación fechas imposibles (29 feb, 31 abr) c.38 (329 tests); fix "de/por/a la mañana" (hora) vs fecha "mañana" c.39 (336 tests); recordatorios con números escritos y fracciones c.40 (344 tests); listas de días sin coma + plurales sábados/domingos c.41 (350 tests); rango horario sin "horas" ambas < 13 c.42 (353) + ampliación followers (358) + recurrencia quincenal con palabra "cada quincena"/"quincenalmente" c.42 (365 tests); día de semana suelto hoy con hora futura c.42 cont.2 (362 tests); listas de días sin prefijo ("gym sábados y domingos") c.42 (369 tests) |
| P2 | QA — compilar 6 variantes tras cambios | OPEN (requiere env Android) |
| P2 | Self-Update — prueba end-to-end N→N+1 | BLOCKED-external (sin dispositivo Android) |
| P3 | UX — pulido visual pantallas workspace renovadas | OPEN |

## Próximo trabajo

- Ciclo 32 (cont.4) (DONE): adjuntos copiados a almacenamiento interno vía `AttachmentStorage`
  + FileProvider. P1 persistencia resuelto. `addAttachment`/`attachCaptureIfPresent`/`resolveAttachmentUri`
  + `deleteAttachment` en OrdiaViewModel; `NoteEditorScreen`/`TaskDetailScreen` migrados (sin
  `takePersistableUriPermission`). 275 domain tests PASS. NO VERIFICADO Android/UI.
- Ciclos previos del 32: “próximos días” (+3d), “antier” (-2d), “próximo trimestre” (+90d),
  “fin de mes”/“mediados de mes”, verificados.
- Continuar ciclo interminable. Candidatos parser: ~~"esta semana" (vs "la semana que viene")~~
  HECHO ciclo 34; "próximo bimestre/semestre" (evaluar frecuencia), ~~"próxima quincena" (+15d)~~
  HECHO ciclo 42 (370 tests), `quincenaPattern`: "primera/segunda/1ra/2da quincena" → día 15/fin
  de mes (con rollover a mes próximo si ya pasó); "la quincena" sin cualificar → próximo hito;
  hora explícita respetada. Simétrico a `finDeMes`/`mediadosDeMes`. "próxima quincena" sigue
  como +15d (procesado después de `nextPeriodMatch`), "en N quincenas" como relativo.
  ~~"principios de semana" (lunes)~~ HECHO ciclo 34 cont. (294 tests). "principios de mes" (día 1) ya hecho ciclo 33.
  ~~"mediados de semana" (miércoles)~~ HECHO ciclo 36 (312 tests). ~~"a finales de semana"~~ HECHO
  ciclo 37 (316 tests): resuelve a sábado (igual que "fin de semana"), forma plural análoga a
  "finales de mes"; ambigüedad viernes/sáb/dom resuelta por consistencia con "fin de semana" ya existente.
- P1 adjuntos: NEXT paso sería **migración de adjuntos legacy** (URIs externos antiguos ya
  guardados) — copiar contenido al abrir por primera vez si todavía accesible. Evaluar antes
  de implementar (riesgo: URIs ya inválidos). De momento `resolveAttachmentUri` no rompe legacy.
- P2/P3: derivedStateOf/keys en LazyColumns grandes; BackHandler en pantallas anidadas;
  contraste onSurfaceVariant. No detenerse.

## Último trabajo — Ciclo 46: GuardianEngine doble conteo de subtareas (mood/XP inflados)

- **Bug P1 (fiabilidad/datos)**: `GuardianEngine` contaba subtareas como tareas lógicas en sus
  agregados, inflando el ánimo (mood) y la experiencia (XP) del guardia — simétrico al doble
  conteo de `SummaryEngine` FIXED en ciclo 20. `completedAll`, `completedToday`, `overdue` y
  `derivedExperience(completedTasks)` no filtraban `parentTaskId == null`. Consecuencias reales:
  1 padre + 4 subtareas vencidas → guardia CONCERNED + mensaje "Hay 5 pendientes atrasados"
  (realidad: 1 tarea lógica); 1 padre + 3 subtareas completadas → XP 48 en vez de 12. El guardia
  mentía al usuario sobre su progreso.
- **Fix mínimo**: filtro `parentTaskId == null` en los 4 conteos, consistente con
  `SummaryEngine`/`GuardianCoach`/`WhatNow`/`DayPlanner` (todos cuentan solo raíces).
- **Tests**: +2 (`overdueCountIgnoresSubtasksToAvoidInflatedConcern`,
  `derivedExperienceCountsLogicalTasksNotSubtasks`). Probe JVM confirmó antes/después.
  **392 domain tests PASS** (`tools/run_domain_tests.sh`), smoke 25 OK.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).

