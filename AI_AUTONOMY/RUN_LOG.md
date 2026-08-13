# RUN_LOG — Ordía

> Registro cronológico de sesiones autónomas (append-only, no borrar entradas).

---
## Ciclo 51 - 2026-08-13 (UTC) - fix: DayPlanner no marca conflicto de hora si startAt es otro día
- **Run/ciclo**: 51.
- **HEAD inicial**: `4f7e701` (base local al iniciar; el remoto ya había avanzado a `497010f` con el ciclo 50 del parser "de aquí a N" — STALE_BASE detectado al fetch).
- **Colisión evitada**: al hacer `git fetch` el remoto estaba en `497010f` (ciclo 50 = parser, otro run). Mi base local `4f7e701` era obsoleta. NO se forzó push. Se hizo stash del trabajo, fast-forward a `497010f`, y se re-aplicaron SOLO los cambios de código (`DayPlanner.kt`/`DayPlannerTest.kt`) que el remoto NO tocó (sin conflicto de código). Los docs se reescribieron sobre la base remota. Trabajo del ciclo 50 del otro run preservado íntegro.
- **Problema seleccionado**: `DayPlanner.build` marcaba conflicto `MOVED_FROM_SCHEDULED_TIME` comparando solo `hour*60+minute` del `startAt` original contra el `startMinute` del bloque planificado, **sin verificar que el `startAt` cayera en el día del plan**. El comentario decía "tareas que ya tenían hora prevista **ese día**" pero la implementación lo aplicaba a cualquier tarea con `startAt`. Una tarea **empezada ayer** (startAt ayer 15:00) que **vence hoy** era reubicada por el plan de hoy y marcada falsamente como "hora movida", aunque nunca tuvo hora asignada *hoy*. Patrón real: una tarea iniciada el día previo arrastra su vencimiento al día siguiente.
- **Prioridad**: P2 (fiabilidad de detección de conflictos; evitar conflicto espurio en planificador).
- **Causa raíz**: la comparación omitía la componente de fecha; `original.toLocalDate() != date` no se comprobaba.
- **Solución (mínima, `DayPlanner.kt`, sin nueva pantalla/botón)**: antes de comparar la hora se verifica `original.toLocalDate() == date`. Si el `startAt` es de otro día, no hay hora prevista "ese día" y no se añade el conflicto. Si cae en el mismo día, el comportamiento previo se conserva (hora distinta → conflicto real). Lógica local honesta, alineada con la intención documentada en el comentario.
- **Tests**: +2 en `DayPlannerTest.kt`: `noConflictWhenStartAtIsOnADifferentDay` (startAt ayer → 0 conflictos) + `conflictStillReportedWhenStartAtIsOnSameDay` (startAt hoy 15:00, plan lo ubica 9:00 → conflicto real). **415 domain tests PASS** (`bash tools/run_domain_tests.sh`, 25 clases — 413 base remota c.50 + 2 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales, render real del planner en pantalla (sin Android SDK).
- **Archivos modificados**: `DayPlanner.kt`, `DayPlannerTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: (tras commit/push de este ciclo).

### Siguiente
- Descubrimiento continuo: auditar captura, recordatorios, detección de vencidas importantes, contexto, onboarding, navegación, accesibilidad, rendimiento, privacidad.
- Parser: manejo robusto de múltiples marcadores temporales en una frase.
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).


## Ciclo 50 - 2026-08-13 (UTC) - parser: "de aquí a N"/"de acá a N" prefijo relativo coloquial

- **HEAD inicial**: `8950d07` (synced tras primer pull; remoto `bf3579d`→`8950d07`).
- **Remoto avanzó 3x durante el run** (rutina segura stash/pop, sin colisión, ver abajo):
  (1) `bf3579d`→`8950d07`: guardián doble conteo, WhatNow IMMINENT_START, nextBestTask compromisos.
  (2) `8950d07`→`4cb5f0f`: DayPlanner "Vence hoy" c.48 (otro run) + docs follow-up.
  (3) `4cb5f0f`→`4f7e701`: SearchEngine ranking urgencia c.49 (otro run) + docs follow-up.
  En todos los casos el remoto **no tocó** `NaturalTaskParser.kt` ni su test → stash pop limpio.
  En la 3ra colisión mi commit previo (`ccf7f36`, etiquetado c.49) ya no fast-forwardeaba: hice
  `git reset --soft HEAD~1`, descarté docs, re-sync, stash pop, rehice docs como c.50.
- **Problema seleccionado**: P1 parser/captura. `relativePattern` solo admitía `en`/`dentro de`;
  las formas coloquiales **"de aquí a N ..."** / **"de acá a N ..."** no casaban → `dueAt=null`,
  residuo en el título, tarea sin recordatorio, invisible en planificador/What Now → olvidada.
- **Causa raíz**: grupo de prefijos del regex demasiado estrecho; faltaba el prefijo coloquial
  futuro más común del español.
- **Solución (mínima)**: extendido el grupo de prefijos de `relativePattern`
  `(?:en|dentro\s+de)` → `(?:en|dentro\s+de|de\s+aqu[íi]\s+a|de\s+ac[aá]\s+a)`
  (variantes con/sin tilde). Resto del patrón y lógica de cálculo reutilizados sin cambios.
- **Heurística honesta**: regex determinista, no IA/random, no llama "IA" a una regla.
- **Tests**: `bash tools/run_domain_tests.sh` = **413 PASS** (408 base remota c.49 incl. SearchEngine
  + 5 nuevos), 25 clases. `bash tools/run_domain_checks.sh` smoke 25 OK. Nuevos:
  `deAquiATresDiasParsesDueAt`, `deAquiAUnaSemanaParsesDueAt`, `deAquiAUnMesParsesDueAt`,
  `deAquiANDiasRespetaHoraExplicita`, `deAcaAUnaSemanaParsesDueAt`. Probe ad-hoc pre-test OK.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `NaturalTaskParser.kt`, `NaturalTaskParserTest.kt`,
  `CURRENT_STATE.md`, `BACKLOG.md`, `RUN_LOG.md`.
- **Etiquetado ciclo 50**: dos runs paralelos ya usaron c.48 (DayPlanner) y c.49 (búsqueda);
  ciclo 50 evita colisión de numeración. Sin force push, sin reset --hard, sin tocar `main`.
- **Commits**: `feat(parser): "de aquí a N"/"de acá a N" prefijo relativo coloquial` → `877765c` (push OK a `openhands/autonomous-ordia`; `4f7e701..877765c`).
- **HEAD final**: `877765c`.
- **Próxima prioridad**: seguir auditando parser (prefijos pasados coloquiales, "al rato"/
  "en un rato", "esta noche"/"anoche" relativas; recurrencias "cada otros N ..."; nominativos
  "el próximo lunes" vs "lunes que viene") y otras áreas (rutinas, recordatorios, What Now,
  backup/restore, concurrencia workers).


---
## Ciclo 49 - 2026-08-13 (UTC) - search ranking accionable (urgencia sobre orden alfabético)

- **Run/ciclo**: 49 (búsqueda/inteligencia — fuera del parser). Continúa la línea "potencia sin más interfaz".
- **HEAD inicial**: `8950d07` (base de captura). Durante el run el remoto avanzó a `4cb5f0f` (ciclo 48: fix "Vence hoy" falso en planificador). Colisión no destructiva: `git stash` → `git pull --ff-only` → `git stash pop`, auto-merge limpio (cambios ortogonales: remoto tocó `DayPlanner.kt`/`PlannerScreen.kt`/strings/test, este run toca `SearchEngine.kt`). Sin force push, sin reset --hard.
- **Problema seleccionado (P2, producto/UX — búsqueda)**: `SearchEngine.search` ordenaba los resultados por **prefijo del título y luego alfabético**, sin considerar urgencia. Al buscar "reunión" con dos tareas "Reunión equipo" —una **atrasada y urgente**, otra normal sin fecha— el orden dependía solo del alfabeto, así que la crítica podía quedar debajo. La búsqueda devolvía "matches" sin priorizar lo accionable, contrario al principio de Ordía ("qué hacer ahora" eleva lo atrasado/urgente en todas las superficies).
- **Causa raíz**: el `sortWith(compareBy { prefix }.thenBy { title })` final operaba solo sobre `SearchResult` (kind/id/title/subtitle), que **no transporta** prioridad/fecha/estado; el filtrado ya descartaba atrasadas, pero el ordenado ignoraba la urgencia disponible en el `TaskEntity` origen.
- **Prioridad**: P2 (mejora funcional potente de una superficie existente; no pérdida de datos ni crash).
- **Solución (mínima, `SearchEngine.kt`, sin nueva pantalla/botón)**: wrapper interno `Ranked(result, urgency, dueAt)` calculado al construir cada resultado. `urgencyRank(task, now)` reutiliza `TaskRules.isOverdue`/`isDueToday` (fuente única de verdad) en orden honesto idéntico a `nextBestTask`: atrasada+urgente > atrasada > urgente+vence-hoy > urgente > alta > vence-hoy > resto. Orden final: **prefijo de título** (relevancia textual primero) → **urgencia** → **dueAt** → **alfabeto**. No-tareas tienen urgencia neutral (6), así que un proyecto/nota que **prefija** el query sigue ganando (la relevancia textual domina); una tarea atrasada que también prefija sube por encima. Heurística local honesta (no IA simulada).
- **Tests**: +2 (`urgencyRanksOverdueAheadOfAlphabeticalMatches`: dos tareas mismo título, la atrasada+urgente primera; `textPrefixStillBeatsUrgencyForDifferentTitles`: proyecto "Toolisto" prefija y sigue ganando sobre tarea "Revisar Toolisto" urgente — preserva el invariant del test `search_coversAllCoreContent`). **408 domain tests PASS** (`bash tools/run_domain_tests.sh`; 406 base remota c.48 + 2 nuevos), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). Render real de `SearchScreen`.
- **Archivos modificados**: `SearchEngine.kt`, `SearchEngineTest.kt`, `CURRENT_STATE.md`, `RUN_LOG.md`.
- **Commits**: `feat(search): ranking por urgencia ante orden alfabético` → `bc4f45d` (push OK a `openhands/autonomous-ordia`; `4cb5f0f..bc4f45d`).
- **HEAD final**: `bc4f45d`.
- **Próxima prioridad**: fuera del parser. Candidatos: `SearchEngine` could surface a "vencidas" quick-filter chip; auditar `SummaryEngine`/`DayPlanner` para oportunidades de simplificación; detección de vencidas importantes; acciones rápidas de captura.

---
## Ciclo 46 - 2026-08-13 (UTC) - guardián: atrasados cuenta solo tareas raíz (consistencia con resumen)

- **Run/ciclo**: 46 (inteligencia/consistencia, no parser).
- **HEAD inicial**: `e0850e6` (base `openhands/autonomous-ordia` sincronizada al iniciar; clean). Durante el run el remoto avanzó a `966b799` (ciclo 45 del otro run: `nextBestTask` time-aware + parser listas bare). Colisión no destructiva: `git stash` → `git pull --ff-only` → `git stash pop`, auto-merge limpio (cambios ortogonales: remoto tocó `NaturalTaskParser.kt`/`TaskRules.kt`, este run toca `GuardianEngine.kt`). Sin force push, sin reset --hard.
- **Problema seleccionado (P1, consistencia/inteligencia)**: `GuardianEngine.snapshot` contaba los atrasados (`overdue`) incluyendo subtareas: un padre atrasado con 2 subtareas también atrasadas → el guardián veía **3** atrasados, mientras la tarjeta de resumen (`SummaryEngine`, que filtra `parentTaskId == null`) mostraba **1**. Dos superficies daban números contradictorios al usuario. El invariant "las subtareas son anidadas, no se cuentan además del padre" ya estaba fijado en `SummaryEngine` (test `overdueCountsRootTaskNotNestedSubtasks`, ciclo previo) pero **no** en el guardián.
- **Causa raíz**: `val overdue = tasks.count { !it.completed && !it.archived && TaskRules.isOverdue(it, nowMillis) }` no filtraba `parentTaskId == null`, a diferencia del conteo equivalente en `SummaryEngine.summarize`. El `overdue` del guardián alimenta el ánimo (`CONCERNED` si `>= 5`), el mensaje ("Hay N pendientes atrasados") y la acción sugerida → la inflación era visible y afectaba el "coach".
- **Prioridad**: P1 (inteligencia/coach con datos inconsistentes respecto al resumen; no pérdida de datos, pero información errónea al usuario en una superficie de acompañamiento).
- **Solución (mínima, `GuardianEngine.kt`)**: filtrar `it.parentTaskId == null` en el conteo de `overdue` (alineado con `SummaryEngine`). Además, exponer `overdue` en `Snapshot` (campo `overdue: Int` añadido al final de la data class, retrocompatible: nadie construye `Snapshot` posicionalmente; la UI lee por nombre) para que el invariant sea verificable y la superficie pueda mostrar el mismo número que el resumen. `completedToday`/XP siguen contando subtareas a propósito (progreso granular deliberado) — solo el conteo de atrasados se alinea con la definición global.
- **Tests**: +1 (`overdueCountsOnlyRootTasksNotNestedSubtasks`: padre + 2 subtareas atrasadas → `overdue==1`, ánimo `CURIOUS` no `CONCERNED` (umbral 5), `suggestedAction` reacciona al atrasado). **391 domain tests PASS** (`bash tools/run_domain_tests.sh`; 388 base remota c.45 + 3 del run paralelo `nextBestTask` + 1 nuevo), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). Render real del guardián/overlay.
- **Commits**: `fix(guardian): overdue cuenta solo tareas raíz (consistencia con resumen)` → `6d0c6a4` (push OK a `openhands/autonomous-ordia`).
- **HEAD final**: `6d0c6a4`.
- **Próxima prioridad**: seguir fuera del parser. Candidatos: auditar `SubtaskRules`/`RoutineRules`/`HabitRules` para invariantes de conteo análogos; revisar `WhatNowEngine` vs `nextBestTask` time-aware para coherencia total; oportunidades de captura ultrarrápida.

## Ciclo 46 (run paralelo) - 2026-08-13 (UTC) - What Now detecta compromisos a punto de empezar (IMMINENT_START)

- **Run/ciclo**: 46 (inteligencia del "¿Qué hago ahora?" — evitar olvidos de compromisos inminentes). Renumerado desde 44 tras colisión de remoto (los ciclos 44–45 fueron reclamados por un run paralelo: "la quincena" + `nextBestTask` time-aware).
- **HEAD inicial**: `e0850e6` (mi base de captura); al hacer push el remoto había avanzado a `966b799`. `git fetch` + `git rebase origin/openhands/autonomous-ordia` (no destructivo, sin force): auto-merge limpio en `WhatNowEngine.kt`/`TodayScreen.kt`/strings/test; conflicto solo en `CURRENT_STATE.md` resuelto conservando el trabajo del otro run.
- **Problema seleccionado (P1, What Now)**: un compromiso programado con `startAt` futuro (reunión/llamada/cita) que empieza en pocos minutos **no aparecía** en "¿Qué hago ahora?": el ranking lo trataba como `SCHEDULED_LATER` (rank -1, último recurso) siempre que `startAt > now`, sin distinguir 5 min de 5 h. Una reunión a las 10:05 escrita a las 10:00 quedaba enterrada bajo una tarea cualquiera de la Bandeja — olvido de compromiso inminente.
- **Prioridad**: P1 (recuperación de compromiso importante a punto de empezar; evita olvidos sin nueva interfaz).
- **Causa raíz**: `WhatNowEngine.rank()`/`reason()` solo distinguían `startAt > now` → SCHEDULED_LATER; no existía gradación por proximidad temporal.
- **Solución (mínima, `WhatNowEngine.kt`)**: nuevo `WhatNowReason.IMMINENT_START` (rank 4, entre OVERDUE y DUE_TODAY) + `isImminentStart(task, now)` (`startAt` futuro y dentro de `IMMINENT_WINDOW_MINUTES = 15`). Las que empiezan más allá siguen como SCHEDULED_LATER. Orden honesto: atrasada > inminente > vence hoy. UI: etiqueta `what_now_reason_imminent`="Empieza enseguida" en `TodayScreen.kt` (rama `when` exhaustiva). Sin nueva pantalla/botón.
- **Tests**: +4 (`imminentStartSurfacesAboveInbox`, `startOutsideImminentWindowStillDeprioritized`, `overdueStillBeatsImminentStart`, `imminentStartBeatsDueTodayWithoutStart`). **392 domain tests PASS** (388 base remota c.45 + 4 nuevos), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `WhatNowEngine.kt`, `TodayScreen.kt`, `strings_screens1.xml`, `WhatNowEngineTest.kt`, `CURRENT_STATE.md`, `RUN_LOG.md`.
- **Commits**: (pendiente de push).
- **HEAD final**: (pendiente).


---
## Ciclo 42 (cont. 2) - 2026-08-13 (UTC) - día de semana suelto hoy con hora futura vence hoy

- **Run/ciclo**: 42 (continuación 2 — fix P1 de captura de citas de hoy).
- **HEAD inicial**: `727e7b8` (remoto actualizado por run paralelo). Workspace arrancó en `0a77387`; al hacer `git fetch` + `git pull --ff-only` el remoto había avanzado a `727e7b8`. Tras commit local, el push se rechazó por divergencia (más trabajo remoto); `git fetch` + `git rebase origin/openhands/autonomous-ordia` (no destructivo, sin force) integró limpio.
- **Problema seleccionado (P1, parser)**: **"el viernes a las 18"** escrito el propio viernes **antes** de las 18:00 se programaba para el **viernes de la semana siguiente**: la cita de hoy se perdía una semana entera (reunión/cita olvidada hoy, recordatorio tardío 7 días). Causa raíz: la rama de fecha suelta usaba `nextWeekday`, que **siempre** salta +7 cuando el día objetivo es hoy (comportamiento correcto para recurrencias, que necesitan el "próximo" estricto, pero incorrecto para una fecha suelta puntual). No existía un path para "hoy si aún no llegó la hora".
- **Prioridad**: P1 (pérdida/desplazamiento de cita del día → recordatorio 1 semana tarde; dato visible incorrecto en planificador/What Now).
- **Solución (mínima, `NaturalTaskParser.kt`)**: nueva `nextWeekdayOrSame(from, target)` — devuelve hoy si el día objetivo coincide con el de `from`, si no delega a `nextWeekday`. La rama de fecha suelta (weekday blando) pasa a usar `nextWeekdayOrSame`; el descarte de "hoy si la hora ya pasó" se difiere al momento de combinar fecha+hora: si fecha+hora resulta pasada respecto a `now`, se rueda +7 días. Así no se agenda en pasado y no hay regresión. Las recurrencias siguen usando `nextWeekday` (próximo estricto, sin cambio). Heurística honesta (no IA).
- **Tests**: +4 (`weekdayHoyConHoraFuturaQuedaHoy`, `weekdayHoyConHoraPasadaRuedaProximaSemana`, `weekdayHoySinHoraYMediodiaPasadoRuedaProximaSemana`, `weekdayHoyTardeConHoraFuturaQuedaHoy`). Confirmado PASS. **362 domain tests PASS** (358 base remota + 4 nuevos; tras rebase sobre `727e7b8` que añadió 5 tests del run paralelo), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Commits**: `fix(parser): día de semana suelto hoy con hora futura vence hoy` (`b4ef5e4`).
- **HEAD final**: `b4ef5e4` (push a openhands/autonomous-ordia OK; `727e7b8..b4ef5e4`).

### Siguiente
- Continuar ciclo interminable. Candidatos parser (P2): "la quincena" como hito financiero (día 15/fin de mes); "próximo bimestre/semestre".
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).
- Explorar captura/What Now/rutinas en busca de oportunidades de producto.

---
## Ciclo 42 (cont. 3) - 2026-08-13 (UTC) - parser listas de días sin prefijo ("gym sábados y domingos")

- **Run/ciclo**: 42
- **HEAD inicial**: `e4157c1` (base `openhands/autonomous-ordia` sincronizada; clean al iniciar). Al finalizar, colisión con runs paralelos: el remoto avanzó varios commits (rango horario sin "horas", recurrencia quincenal "cada quincena", día de semana suelto hoy con hora futura — ciclo 42). Se resolvió con `git fetch` + `git rebase` (no destructivo: mi commit aún no pusheado) y resolviendo conflictos de docs (BACKLOG/CURRENT_STATE/RUN_LOG) combinando ambos conjuntos. Sin force push, sin reset --hard, sin STALE_RUN destructivo.
- **Problema seleccionado (P1)**: `NaturalTaskParser` perdía la recurrencia de listas de días **sin prefijo** ("gym sábados y domingos", "fútbol domingos a las 18", "lavar auto sábados domingos"). `parseRecurrence()` solo casaba listas con "los"/"cada"/"todos los", así la forma bare caía `recurrence=NONE` y los días quedaban como residuo en el título → la rutina semanal se olvidaba en silencio (pérdida de datos: recordatorios nunca disparaban). La forma bare es tan común como la prefijada en español hablado/escrito.
- **Causa raíz**: el patrón de recurrencia exigía un artículo prefijo para activar la rama de lista de días; sin él no se probaba la rama de weekday-list.
- **Prioridad**: P1 (pérdida de datos silenciosa en rutinas).
- **Solución (mínima, `NaturalTaskParser.kt` — `parseRecurrence()`)**: reconocer listas bare de 2+ días como WEEKLY sin artículo; día plural marcado ("domingos"/"sábados") en solitario también es recurrencia (hábito semanal explícito). Día suelto **no plural** ("reunión martes") sigue siendo **fecha única** (ambiguo: no programar rutina equivocada). `dayNameRegex` evita falsos positivos (solo consume nombres de día reales, no roba texto ajeno).
- **Tests**: +4 (`parsesBareDayListRecurrence`, `parsesBareDayListWithExplicitTime`, `parsesBarePluralSingleDayRecurrence`, `bareSingleNonPluralDayIsNotRecurrence`). Tras integrar la base remota de los runs paralelos (incl. c.43 "entre semana" + c.44 "la quincena"): **388 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`). Verificado con probe JVM antes de integrar a la suite oficial.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Commits**: `feat(parser): reconocer recurrencia de listas de días sin prefijo ("gym sábados y domingos")`.
- **Colisión de remoto (2.ª y 3.ª rebase, no destructiva)**: el remoto avanzó repetidamente durante el run (c.43 "entre semana"/"de lunes a viernes" = WEEKLY [1-5]; c.44 fecha/hito "la quincena"). En cada caso `git fetch` + `git rebase`: auto-merge limpio en `NaturalTaskParser.kt` + tests (cambios ortogonales); conflictos solo en docs (`CURRENT_STATE.md`, `RUN_LOG.md`, `BACKLOG.md`) resueltos combinando ambos conjuntos y sumando los tests de cada ciclo (369→376→388). Sin force push, sin reset --hard.
- **HEAD final**: (ver commit tras push).
- **Próxima prioridad**: "la quincena" ya resuelto por c.44 (run paralelo). Re-auditar parser en busca de gaps reales (ej. "a finales de este mes" date regression reportada en probe); revisar áreas no-parser (captura, What Now, rutinas, backup) para mayor impacto de producto.

---
## Ciclo 42 (cont. - run paralelo) - 2026-08-13 (UTC) - rango horario sin "horas": ampliación de followers seguros

- **Run/ciclo**: 42 (continuación â colisión con run paralelo resuelta, mejora aditiva sobre el fix base del ciclo 42).
- **HEAD inicial**: `91c8b9f` (base del workspace al iniciar). Al hacer `git fetch` el remoto había avanzado a `0a77387` ("feat(parser): rango horario sin unidad 'horas' cuando ambas horas < 13"): un run paralelo resolvió el **mismo** backlog item con un enfoque equivalente (3 tests, set de followers básicos). Descarté mi implementación competidora vía `git stash` (luego `git stash drop`) + `git pull --ff-only` (sin force push, sin reset --hard, sin STALE_RUN destructivo) y reconstruí sobre `0a77387` aportando una **mejora aditiva no duplicativa**.
- **Problema seleccionado (P2, parser)**: el fix base del rango horario sin "horas" dejaba residuo ("de 9 a 11" en el título, `durationMinutes=null`) en tres clases de frases cotidianas: rango + día de la semana ("clase de 9 a 11 el viernes"), rango + día relativo ("taller de 10 a 12 mañana") y rango + parte del día con conector no listado ("curso de 4 a 6 a la tarde", "turno de 9 a 11 por la noche"). Causa raíz: el regex `followedByCount` del fix base sólo incluía conectores básicos (con/y/o/para/hasta/luego/después/pero/porque + puntuación), así que esos tokens iniciaban "sustantivo de cantidad" falso â el rango se rechazaba.
- **Prioridad**: P2 (captura incorrecta + título sucio; no pérdida de datos, pero fricción).
- **Solución (mínima, en `NaturalTaskParser.kt`)**: ampliar el regex de followers seguros con artículos (el/la/los/las/un/una), a/al, por, sin, sobre, desde, del, días de la semana (lunesâ¦domingo, con y sin acento) y días relativos (mañana/manana, hoy, ayer, anteayer). El rechazo de "comprar de 2 a 5 entradas"/"reunión de 2 a 5 personas" se preserva (sustantivo contable sigue fuera del set). Heurística honesta, conservadora.
- **Tests**: +5 TDD (`bareRangeSmallHoursFollowedByWeekdayParsesDuration`, `â¦FollowedByRelativeDayParsesDuration`, `â¦FollowedByATardeParsesDuration`, `â¦FollowedByPorLaNocheParsesDuration`, `â¦FollowedByCountableNounStillRejected`). Confirmado FAIL previo (4 fallos) contra la base remota, luego PASS. **358 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases (353 base remota + 5 nuevos). Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Commits**: `feat(parser): rango horario sin "horas" â ampliar followers seguros (día semana/parte del día)`.
- **HEAD final**: `b4ef5e4`.

### Siguiente
- Continuar ciclo interminable. Candidatos parser (P2): "la quincena" como hito financiero (día 15/fin de mes); "próximo bimestre/semestre"; "mediados de semana" ya hecho.
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).
- P2/P3: derivedStateOf/keys en LazyColumns grandes; BackHandler; contraste onSurfaceVariant.

---
## Ciclo 42 - 2026-08-13 (UTC) - recurrencia quincenal con palabra ("cada quincena")

- **Run/ciclo**: 42
- **HEAD inicial**: 4446852 (docs ciclo 41). Colisión de remoto: durante el stash+ff el remoto avanzó a `edea354` (run concurrente que resolvió el rango horario sin "horas", ambos < 13, en el mismo ciclo 42). Resolución no destructiva: se hizo `git stash` + `pull --ff-only` + `git stash pop`; conflictos en BACKLOG.md y CURRENT_STATE.md resueltos combinando ambos logros (rango horario remoto + recurrencia quincenal mía). Sin STALE_RUN, sin force push, sin reset --hard.
- **Problema seleccionado (P1)**: `NaturalTaskParser` no reconocía la **recurrencia quincenal con palabra** ("nómina cada quincena", "reporte quincenalmente", "todas las quincenas"). Causa raíz: `intervalPattern` (`cada\s+(\d{1,3})\s*(días|semanas|meses|años)`) **solo admite dígitos**; la forma con la palabra "quincena" no casaba con ningún patrón → `recurrence=NONE` y, por el anclaje del ciclo 19 (que solo aplica si `frequency != NONE`), `dueAt=null`. Resultado: la tarea recurrente nacía **invisible** — sin fecha, sin recordatorio (`reminderAt ?: dueAt` ambos null), ausente de What Now/planificador → nóminas/pagos quincenales se olvidaban. La quincena (cada ~15 días / día 15 y fin de mes) es una cadencia financiera/laboreal muy común en español.
- **Prioridad**: P1 (evitar olvidos de tareas recurrentes cotidianas; datos invisibles).
- **Solución (mínima, en `NaturalTaskParser.kt`)**: nuevo patrón dedicado `cada quincena|quincenalmente|todas las quincenas` → `RecurrenceResult(WEEKLY, interval=2, …)` (cada 2 semanas ≈ quincena), colocado **antes** de `fixedPatterns`. Se procesa en `parseRecurrence` (que ya corre antes que las fechas), así que la frase se borra del título y reutiliza el anclaje a fecha de captura del ciclo 19. **Sin añadir enum ni migración**: WEEKLY+interval=2 es representación honesta y reutiliza el avance semanal existente. Sin riesgo de falsos positivos (palabra específica, no ambigua).
- **Tests**: +3 (`cadaQuincenaParsesBiweeklyRecurrence`, `quincenalmenteParsesBiweeklyRecurrence` con hora, `cadaQuincenaRespetaFechaExplicita` con `el 15/8`). **365 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases (362 remoto tras segundo rebase sobre `b4ef5e4` + 3 quincenal míos). Smoke 25 OK (`tools/run_domain_checks.sh`). No se redujeron ni eliminaron tests.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Hallazgos adicionales**: "la quincena"/"próxima quincena" como **fecha absoluta** (día 15 / fin de mes) sigue OPEN (P2): `nextPeriodPattern` cubre "próxima quincena" como +15d relativo, pero no como hito anclado al 15. Rango horario sin "horas" ("clase de 9 a 11") fue **resuelto por el run concurrente** en este mismo ciclo 42 (ambas horas < 13, heurística honesta de fin de cadena/conector vs sustantivo de cantidad) — consolidado en CURRENT_STATE/BACKLOG.
- **AI_AUTONOMY actualizado**: CURRENT_STATE (ciclo 42, 365 tests, ambos logros combinados), BACKLOG (entrada P1 FIXED + marcado parcial en item P2 quincena), RUN_LOG (esta entrada).
- **Commits**: `feat(parser): recurrencia quincenal "cada quincena"/"quincenalmente" (WEEKLY x2)`.
- **HEAD final**: `97ec260` (push a openhands/autonomous-ordia exitoso; rebase sobre `b4ef5e4` del run concurrente, 365 tests PASS, smoke 25 OK).

### Siguiente
- Parser P2: "la quincena"/"próxima quincena" como fecha anclada al 15 (evaluar si aporta vs +15d actual).
- Continuar descubrimiento: auditar What Now, rutinas, recordatorios en busca de P1 reales.
- Rango horario sin "horas": solo si se encuentra señal honesta fuerte (no lista de sustantivos frágil).

---
## Ciclo 38 - 2026-08-13 (UTC) - fechas pasadas + recuperación de fechas imposibles

- **Run/ciclo**: 38
- **HEAD inicial**: bdd3dc0 (base inicial del workspace). El remoto ya estaba en `9ac1a8b` (5 commits por delante de la base local: "mediados de semana", "un par de", docs, y más). Se hizo `git fetch` + rebase de mis 2 commits locales (`5a67a47`, `b0a33a7`) sobre el remoto. Durante el run el remoto avanzó una vez más (`f2d26ba`, ciclo 37 "a las N horas"): segundo rebase, sin conflictos. Procedimiento no destructivo en ambos casos; sin STALE_RUN, sin force push, sin reset --hard.
- **Problema seleccionado (2 unidades atómicas, P1)**:
  1. **Fechas pasadas "hace N"/"la semana/el mes pasado"** (commit `5a67a47`→`ff3a1f4`): `NaturalTaskParser` no reconocía fechas pasadas cotidianas ("pagué hace 2 días", "revisé el informe la semana pasada", "reunión el mes pasado"). Causa raíz: `agoPattern` ("hace N") no existía; `lastPeriodPattern` ("la semana/el mes/el año pasado") no existía y además `previousWeekdayPattern` ("el mes pasado") capturaba la frase, dejando grupo1="mes" (no es día → sin fecha) y **borraba** la frase del título. Resultado: `dueAt=null` (tarea sin recordatorio, invisible en What Now/planificador) **Y** frase temporal como basura en el título. También "hace poco"/"hace un rato" (coloquial = "recién") no se resolvían. Solución: nuevos `agoPattern` (resta N días/semanas/meses/años, o 3h para "poco"/"un rato") y `lastPeriodPattern` (resta 7d/30d/365d), detectados **antes** de `previousWeekdayPattern`, integrados al inicio de la cadena `effectiveRelativeDueAt` (las fechas pasadas son explícitas y tienen prioridad sobre fechas futuras ambiguas); la hora explícita se aplica sobre la fecha pasada (tarea vencida con hora).
  2. **Recuperación de fechas imposibles** (commit `b0a33a7`→`265fc93`): `parseMonthNameDate` usaba `LocalDate.of(year, month, day)` que lanza `DateTimeException` para fechas imposibles ("el 29 de febrero" en año no bisiesto, "el 31 de abril"). El `runCatching` devolvía `null` → caía al fallback que **deja la frase temporal en el título** y `dueAt=null` (tarea sin fecha y con basura). El usuario que escribe "el 29 de febrero" claramente quiere una fecha real, no perderla. Solución: en vez de descartar, **recuperar** con `Year`/`YearMonth`: Feb 29 no bisiesto → siguiente año bisiesto (2028); día > máx del mes (31 abr) → clamp al último día válido del **siguiente año** (30 abr 2027); Feb 30 → Feb 28. Así la frase se reconoce, se borra del título y la tarea obtiene una fecha útil (no se pierde).
- **Prioridad**: P1 (evitar olvidos: tareas sin recordatorio + títulos sucios; datos erróneos por fechas perdidas).
- **Solución (mínima, en `NaturalTaskParser.kt`)**: nuevos patrones `agoPattern`/`lastPeriodPattern` + variables `agoDueAt`/`lastPeriodDueAt` al inicio de `effectiveRelativeDueAt`; refactor de `parseMonthNameDate` con `java.time.Year`/`YearMonth` para clamp/recuperación. Imports añadidos: `java.time.Year`, `java.time.YearMonth`.
- **Tests**: +9 fechas pasadas (`haceNdiasResuelveFechaPasada`, `haceUnaSemanaResuelveFechaPasada`, `haceNmesesResuelveFechaPasada`, `haceNdiasConHoraAplicaHoraSobreFechaPasada`, `laSemanaPasadaResuelveFechaPasada`, `elMesPasadoResuelveFechaPasada`, `elMesPasadoConHoraAplicaHora`, `hacePocoResuelveHaceTresHoras`, `haceUnRatoLimpiaTituloSinResiduo`) + 5 fechas imposibles. **329 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`).
- **Colisión de remoto resuelta**: conflicto en `NaturalTaskParser.kt` (remote añadió `startOfWeekDueAt`/`midOfWeekDueAt`; local añadió `agoDueAt`/`lastPeriodDueAt`) resuelto combinando ambos conjuntos en la cadena `effectiveRelativeDueAt`. Conflicto en `NaturalTaskParserTest.kt` (ambos añadieron tests al final) resuelto conservando ambos conjuntos. Rebase posterior sobre `f2d26ba` sin conflictos.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Commits**: `feat(parser): fechas pasadas "hace N"/"la semana/el mes pasado" + limpieza titulo` (`ff3a1f4`); `fix(parser): recuperar fechas imposibles (29 feb, 31 abr) en vez de perderlas` (`265fc93`).
- **HEAD final**: `265fc93` (push exitoso a openhands/autonomous-ordia tras 2 colisiones de remoto resueltas no destructivamente).

### Siguiente
- Continuar ciclo interminable. Candidatos parser descubiertos (P2): rango horario sin palabra "horas" ("clase de 9 a 11"); números escritos en recordatorios relativos ("recuérdame dos horas antes" → offset null); "la quincena" como hito financiero.
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).
- P2/P3: derivedStateOf/keys en LazyColumns grandes; BackHandler; contraste onSurfaceVariant.

## Ciclo 37 - 2026-08-13 (UTC) - "a las N horas" (hora, no duración falsa)

- **Run/ciclo**: 37
- **HEAD inicial**: 46efb3e (base inicial). Durante el run el remoto avanzó TRES veces por runs paralelos: 8146acf (quincena/bimestre/semestre), e4157c1 ("un par de"), y 9ac1a8b ("mediados de semana", ciclo 36). Procedimiento no destructivo en cada caso: stash+ff+pop sobre 8146acf; rebase sobre e4157c1 (conflicto solo en CURRENT_STATE.md, resuelto conservando ambas secciones, renum. 35→36); rebase sobre 9ac1a8b sin conflictos (renum. 36→37, ya que el remoto usó ciclo 36 para "mediados de semana"). Sin STALE_RUN destructivo, sin force push, sin reset --hard.
- **Problema seleccionado**: `NaturalTaskParser` interpretaba **"a las N horas"** como duración falsa. El `timePattern` no consumía el sufijo "horas", así que "9 horas" era robado por `durationMatch` (540 min falsos) y "a las" quedaba como residuo en el título. La tarea recibía una duración absurda y **ninguna hora real** → recordatorio/planificación incorrectos. Bug doble: la guardia añadida para descartar "N horas" como duración filtraba el ganador global tras `minByOrNull`, descartando TODOS los matches de duración (incluido "durante 1h" válido) cuando había un "N horas" inválido presente; además los conectores "durante"/"por" no se limpiaban del título.
- **Prioridad**: P1 (datos erróneos, recordatorio/planificación incorrectos, fricción de captura).
- **Causa raíz**: `timePatterns[0]` carecía de grupo opcional para el sufijo "horas"/"hs"; la guardia anti-"N horas" operaba sobre el resultado de `minByOrNull` (global) en vez de filtrar por-match antes; la limpieza de conector post-duración solo cubría "de".
- **Solución (mínima, en `NaturalTaskParser.kt`)**:
  - `timePatterns[0]` añade grupo opcional `(?:\s*(horas?|hs))?` tras la hora (con o sin meridiem): consume el sufijo "horas"/"hs" sin alterar la lógica AM/PM. Así "a las 9 horas" se reconoce y borra completo como frase temporal.
  - Guardia de duración refactorizada: filtro **por-match** ANTES de `minByOrNull`, descartando solo los matches "N horas" precedidos por frase temporal (`timePhrasePreceding`) y conservando válidos como "1h"/"2h".
  - Limpieza de conector extendida: tras extraer la duración se borra también "durante"/"por" (además de "de").
- **Tests**: +3 formales (`aLasNHorasEsHoraNoDuracion`, `aLasNHorasConFechaNoEsDuracion`, `duranteConnectorBeforeCompactDurationIsRemoved`) en `NaturalTaskParserTest.kt`. **315 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Commits**: fix(parser): "a las N horas" como hora, no duración falsa (b959764 → 3c773b2 tras rebase sobre e4157c1 → b842e00 tras rebase sobre 9ac1a8b). Renumerado 35→36→37 al detectar colisión de numeración con runs paralelos ("un par de"=35, "mediados de semana"=36).
- **HEAD final**: 7fa4056 (push exitoso a openhands/autonomous-ordia tras 3 colisiones de remoto resueltas no destructivamente).

### Siguiente
- Continuar ciclo interminable. Candidatos parser descubiertos en el probe (P2): rango horario sin palabra "horas" ("clase de 9 a 11" → sin duración); números escritos en recordatorios relativos ("recuérdame dos horas antes" → offset null).
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).
- P2/P3: derivedStateOf/keys en LazyColumns grandes; BackHandler; contraste onSurfaceVariant.

---


## SESIÓN 000 — Bootstrap del sistema autónomo

- **Fecha (UTC)**: 2026-08-10
- **Trigger**: consolidación del rebuild de Codex y creación del sistema autónomo
- **Resultado**: ÉXITO

### Qué se hizo

1. Inventario completo del repo (ramas, status, remotes, pendientes de Codex).
2. Backup local del árbol de trabajo fuera del repo (`C:\Users\wsepulveda\AppData\Local\Temp\opencode`).
3. Auditoría de cambios de Codex (sin secretos; 0 hits en `JULES_API_KEY` / `TOKEN` / `KEY` en `app/src/main`, `app/src/test`).
4. Validación del rebuild:
   - `./gradlew test` → 6 variantes verdes.
   - `./gradlew lintPreviewSafeDebug` → 2 errores corregidos.
   - `./gradlew assembleDebug assembleRelease` → verdes.
5. Consolidación del rebuild en 9 commits coherentes + publicación de `feature/ordia-total-rebuild-2026-08-10`.
6. Creación y publicación de `jules/autonomous-ordia` (HEAD `d34ffd8`, branch autónoma permanente).
7. Creación de memoria persistente `AI_AUTONOMY/` (MISSION, CURRENT_STATE, BACKLOG, DECISIONS, RUN_LOG, AGENTS).

### Problemas encontrados

- `ContextPrivacyFilter` no filtraba fragmentos de paquete sin punto (banca genérica) → corregido + test.
- 2 errores de lint (`StartActivityAndCollapseDeprecated`, `stringResource` fuera de composable) → corregidos.

### Commits creados

- `143e8e2..d34ffd8` (9 commits de consolidación sobre la rama de publicación)

### Evidencia

- `git log --oneline -11` tras la sesión: 9 commits de consolidación + 1 de docs previo.
- Ramas remotas: `feature/ordia-total-rebuild-2026-08-10` y `jules/autonomous-ordia` (ambas en `d34ffd8`).

---

## SESIÓN 001 — Actualización del workflow autónomo Jules

- **Fecha (UTC)**: 2026-08-10 (segunda parte del bootstrap)
- **Trigger**: continuar plan de sistema autónomo (fases 8-31)
- **Resultado**: ÉXITO (parcial — sin `gh` local ni clave de API no se puede ejecutar un ciclo de prueba)

### Qué se hizo

1. Descubierto que `origin/main` ya contenía `.github/workflows/ordia-autonomous-jules.yml`
   (creado en `182f80d`, reparado en `9d05dd2`) — la rama del rebuild divergió antes y NO lo incluía.
2. Leído el workflow existente: apuntaba a `main` como rama inicial (INCORRECTO para el nuevo modelo).
3. Creado `.github/workflows/ordia-autonomous-jules.yml` actualizado en `jules/autonomous-ordia`:
   - **Rama de trabajo**: `jules/autonomous-ordia` (nunca `main`).
   - **Cron**: cada 2 horas en el minuto 17 (`17 */2 * * *`) + `workflow_dispatch`.
   - **Failsafe variable**: `vars.ORDIA_AUTONOMY_ENABLED` (false/0/no/off → no lanza).
   - **Failsafe archivo**: `AI_AUTONOMY/AUTONOMY_BYPASS` → no lanza.
   - **Session lock**: comprueba PRs abiertas hacia `jules/autonomous-ordia`; si hay, no lanza.
   - **Verificación de rama en la API de Jules** antes de lanzar (la rama debe existir en el conector).
   - **Prompt maestro ampliado**: auditoría periódica del trabajo de Codex, anti-fake-IA explícito,
     testing por variante, UI/UX en español blanco/negro, NO CICLOS NI ACTIVIDAD FALSA,
     RAMA Y PRs explícitos.
4. Validado el YAML con `js-yaml` (Node, instalado en temp fuera del repo): sintaxis válida,
   heredocs Python balanceados (2 abren/2 cierran), r-string equilibrado, sin `${{ }}` dentro
   de los heredocs Python, llaves `{}` del dict equilibradas, cero tabuladores.
5. Actualizada la memoria: `SUPERVISION.md` (comportamiento del workflow y parada de emergencia),
   `DECISIONS.md` (failsafe activo por defecto, session lock por PR, verificación de rama en API, cron 2h).

### Problemas encontrados

- `gh` NO está instalado localmente → no se pudo verificar la existencia de `secrets.JULES_API_KEY`
  ni lanzar un ciclo de prueba desde la terminal. Documentado; el workflow fallará con mensaje claro
  si la clave no está configurada en el repo.
- Python local NO disponible → validación YAML hecha con Node/js-yaml en temp (fuera del repo).

### Prerrequisitos para el primer ciclo real (humano)

1. Confirmar que `secrets.JULES_API_KEY` existe en el repo (Settings → Secrets → Actions).
2. Confirmar que el conector de Jules ve la rama `jules/autonomous-ordia`.
3. (Opcional) Crear variable `ORDIA_AUTONOMY_ENABLED` = `false` solo si NO se quiere autonomía por defecto.

### Commits creados

- `969059d` docs(autonomy): memoria persistente AI_AUTONOMY y guía AGENTS (sesión 000).
- (pendiente) docs(autonomy): workflow Jules actualizado + memoria (este commit).

---

## SESIÓN 002 — Corrección de infraestructura: auto-merge y publicación en main

- **Fecha (UTC)**: 2026-08-10
- **Trigger**: corrección final del sistema autónomo (dos problemas críticos detectados)
- **Resultado**: ÉXITO (infraestructura publicada; falta solo el primer ciclo real manual)

### Problemas críticos detectados

1. **El workflow nuevo no está en `main`**: GitHub Actions ejecuta los schedulers desde la
   default branch. `origin/main` seguía en `9d05dd2` con la versión vieja (cron `0 7 * * *`,
   `preferred = "main"`), así que el cron de 2h NUNCA correría.
2. **`AUTO_CREATE_PR` no es auto-merge**: el session lock antiguo (cualquier PR abierta → no
   lanzar) habría bloqueado la autonomía indefinidamente tras la primera PR de Jules.

### Qué se hizo

1. `git fetch --all --prune` + consulta a la API de GitHub (25 PRs, todas `base:main`, autor
   propietario `wandersepulveda2013`): la señal fiable de PR de Jules es el patrón de rama head
   (`fix/desc-<timestamp>`, regex), no el autor.
2. Reescrito `.github/workflows/ordia-autonomous-jules.yml` (versión definitiva):
   - cron `17 */2 * * *` + `workflow_dispatch`; permisos mínimos (solo lectura); timeout 20 min.
   - Failsafes: variable `ORDIA_AUTONOMY_ENABLED` (false/0/no/off → deshabilitada; si no existe
     → habilitada) y archivo `AI_AUTONOMY/AUTONOMY_BYPASS`.
   - Paso `Find Ordia repository in Jules` con `preferred = "jules/autonomous-ordia"`.
   - Paso `Session lock` (id `lock`): consulta Jules Sessions API (fail-open; estados activos
     `QUEUED/PLANNING/IN_PROGRESS/PAUSED/AWAITING_*` → `skip_active_session`); evalúa PRs abiertas
     hacia la rama autónoma y sus checks (fallida → `proceed_with_failure_context`; CI corriendo →
     `skip_ci_running`; lista → `skip_ready_for_merge`; ≥4 PRs → `skip_too_many_prs`; draft/stale →
     contexto); anti-loop por área fallada ≥2 veces; contexto en `/tmp/autonomy-context.json`.
   - Paso `Launch autonomous Ordia session` condicionado a `decision == proceed |
     proceed_with_failure_context`; inyecta contexto de PRs fallidas/atascadas al prompt;
     `requirePlanApproval: False`; `automationMode: AUTO_CREATE_PR`.
3. Creado `.github/workflows/ordia-autonomous-merge.yml` (NUEVO): auto-merge squash hacia
   `jules/autonomous-ordia` con 12 guardas:
   (1) base ref exacto `jules/autonomous-ordia`; (2) guard clause `base == "main"` → MERGE
   PROHIBIDO; (3) no fork + patrón de rama Jules; (4) no draft; (5-6) `mergeable` y
   `mergeable_state` clean (behind → update-branch vía API); (7-10) todos los check-runs
   success/neutral/skipped, sin queued/in_progress/pending/waiting ni failure/cancelled/
   timed_out/action_required/stale/startup_failure (+ combined status); (11) security/secret/
   codeql/scan/dependency checks deben ser success; (12) merge squash sin force
   (`POST /pulls/{n}/merge` con `merge_method=squash`).
   Trigger: `pull_request_target` (opened/synchronize/reopened/ready_for_review) + cron
   `*/15 * * * *` + `workflow_dispatch`; concurrency group, `cancel-in-progress: false`.
   Permisos: `contents: write`, `pull-requests: write`, `checks: read`, `statuses: read`.
   Logging a `GITHUB_STEP_SUMMARY` (PR number, head SHA, checks, resultado, nuevo HEAD de la rama)
   + comentario post-merge en la PR.
4. Ampliado `.github/workflows/android-ci.yml`: `branches: [main, jules/autonomous-ordia]` para
   push y pull_request (verify corre en PRs hacia la rama autónoma; sign/publish solo en main).
5. Validado YAML con js-yaml (Node en temp, fuera del repo): 3/3 válidos, heredocs balanceados,
   llaves `{}` equilibradas, sin `${{ }}` dentro de los heredocs Python. Permisos job-level
   verificados (jules: read-only; merge: write mínimo; android-ci verify: read+checks write).
6. Publicado en `main` SOLO infraestructura (rama `infra/autonomous-main` desde `origin/main`,
   sin rebuild): `9d05dd2..d5b3b60` → main.
   - `origin/main` ahora: `android-ci.yml`, `build-apk.yml`, `ordia-autonomous-jules.yml`,
     `ordia-autonomous-merge.yml`.
   - Verificado con `git show origin/main:...`: cron `17 */2 * * *`, `preferred =
     "jules/autonomous-ordia"`, `requirePlanApproval: False`, `automationMode: AUTO_CREATE_PR`,
     guard clause main en el merge, `merge_method: squash`.
   - Sin camino automático `* → main`: scheduler crea PRs hacia la rama autónoma; auto-merge solo
     hacia la rama autónoma con guard clause; build-apk/android-ci son CI puro.
7. Publicada la rama autónoma con la infraestructura definitiva: `d84aeab..cc1a1e3`.

### Problemas encontrados

- Ninguno nuevo. `gh` y Python local siguen NO disponibles (validación YAML con Node/js-yaml en
  temp). No se pudo lanzar un ciclo real de prueba (falta `secrets.JULES_API_KEY`).

### Prerrequisitos para el primer ciclo real (humano)

1. Confirmar que `secrets.JULES_API_KEY` existe en el repo (Settings → Secrets → Actions).
2. Confirmar que el conector de Jules ve la rama `jules/autonomous-ordia`.
3. (Opcional) Ejecutar manualmente `Ordia Autonomous Jules` (workflow_dispatch) y
   `Ordia Autonomous Merge` (workflow_dispatch) para observar el primer ciclo.

### Commits creados

- `d5b3b60` (main, infraestructural) ci(autonomy): infraestructura definitiva del sistema autónomo en main
- `cc1a1e3` (jules/autonomous-ordia) ci(autonomy): session lock robusto, auto-merge seguro y CI sobre la rama autónoma
- (pendiente) docs(autonomy): memoria de la sesión 002 (este commit)

---

## Sesión 003 — 2026-08-11 (OpenHands: auditoría + fix de verificación de dominio)

**Objetivo**: primera ejecución de OpenHands. Inspeccionar el estado real del repo, leer la
memoria `AI_AUTONOMY`, auditar el trabajo previo de Jules/Codex, ejecutar una baseline razonable,
reconstruir prioridades y resolver el problema ejecutable de mayor prioridad verificable.

**Entorno**: rama `jules/autonomous-ordia` (HEAD inicial `ecd6151`). Sin Android SDK en el entorno;
se instaló OpenJDK 21 + kotlinc 2.1.20 + JUnit4/hamcrest/org.json/kotlinx-coroutines (-jvm) en /tmp.

### Cambios

- `tools/domain-smoke/DomainSmoke.kt`: fix del smoke obsoleto. El assertion
  `search.map { it.kind }.toSet() == SearchKind.entries.toSet()` fallaba siempre porque
  `SearchKind` se amplió a 7 valores (TASK, PROJECT, NOTE, HABIT, CONVERSATION, COMMITMENT,
  AUTOMATION) pero el smoke solo alimenta 4 listas. Alineado con `SearchEngineTest` (set explícito
  de los 4 kinds core).
- `tools/domain-smoke/PreferenceStubs.kt` (NUEVO): stubs JVM de `ThemeMode`, `InterfaceMode`,
  `GuardianMode`, `GuardianSpecies`, `UserPreferences`, `PreferencesRepository` (con
  `DAILY_INTERACTION_LIMIT`) para compilar/ejecutar los tests del dominio que dependen de
  `data.preferences` sin Android DataStore. Solo se usa desde tools/, no forma parte de la app.
- `AI_AUTONOMY/BACKLOG.md`, `CURRENT_STATE.md`, `RUN_LOG.md`, `DECISIONS.md`: memoria actualizada.

### Tests

- `bash tools/run_domain_checks.sh` → **PASS** (25 assertions) [antes FAIL: "Universal search failed"].
- Tests unitarios del dominio (JUnit4, JVM): **125 tests PASS, 0 FAIL, 0 skip**.
- `./gradlew test/lint/assemble`: **NO VERIFICADO** (sin Android SDK en el entorno).

### Hallazgos de auditoría

- La rama `jules/autonomous-ordia` es el rebuild Ordía 3.0 (275+ archivos); `main` solo tiene
  infra de orquestación. NO trabajar sobre main.
- Trabajo previo de Jules/Codex auditado: recordatorios (ReminderScheduler con WorkManager
  unique work + cancelAllAndAwait, ReminderActionReceiver con mutex+snooze real), persistencia
  (BackupManager con checksum SHA-256 ORD-031, RestoreData atómico via withTransaction),
  IA (TFLite simulado eliminado → IntelligenceProvider real), privacidad (IME guard, context
  filter). Trabajo REAL y robusto, no simulado.
- `SearchEngine` correctamente ampliado a 7 kinds; `SearchEngineTest` correcto; solo el smoke
  estaba desactualizado.
- `NoteBlocks.kt`/`TaskSnapshotCodec.kt` (dominio) acoplados a `org.json` (API Android) — deuda
  técnica menor, funcional.

### Commit

- (pendiente de crear en este paso) fix(test): alinear domain smoke con SearchKind ampliado

### Siguiente prioridad

- Auditoría de persistencia (Room cascadas/índices/transacciones/N+1) y recordatorios/WorkManager
  con Android SDK (gradle). Ítems P0/P1 del BACKLOG: restauración con manifiesto corrupto, backup
  adverso. La verificación de dominio ya está verde.

---

## Sesión 004 — 2026-08-11 (OpenHands: autonomía nocturna, Ciclo 1: NaturalTaskParser)

**Objetivo**: continuar autonomía. Auditar área funcional crítica P1 (parser) en profundidad.
**Entorno**: rama `jules/autonomous-ordia` (HEAD inicial `35fb204`). Sin Android SDK; kotlinc/JUnit4 en /tmp.

### Método
- Probe JVM reproducible (`/tmp/parser-probe/Probe.kt`) que invoca `NaturalTaskParser.parse` con casos
  reales en español (fechas numéricas, esta noche/tarde/mañana, números escritos, 12/24h, urgente).
- Comparación con comportamiento esperado y con `parseMonthNameDate` (consistencia).

### Bugs encontrados y corregidos (commit `fb53e8c`)
- BUG1 (P1): fecha numérica sin año en el pasado no rodaba al año siguiente. "5/3" el 29-jul → 2026-03-05
  (pasada). Inconsistente con "5 de julio"→2027. Una fecha pasada hace que el recordatorio nunca dispare
  (ReminderSync filtra trigger<=now). Fix: rodar a +1 año si rawYear==null y date<today.
- BUG2 (P1): "esta mañana/tarde/noche" no reconocidas; además "esta mañana" se leía como "mañana" (tomorrow)
  porque contiene "mañana". Fix: rama partOfDay antes que la de "mañana"; horas canónicas (9/15/21), tolerant
  a acentos; tiempo explícito tiene prioridad.
- BUG4 (P1): "urgente" como palabra inicial no se detectaba sin prefijo !/#. Fix: `^urgente\b`; no se
  detecta a mitad de frase (evita "no es urgente").
- BUG3 (P2, OPEN): números escritos en "en dos horas"/"dentro de tres días" no parseados. Queda en backlog.

### Tests
- 136 domain tests PASS (125 previos + 11 nuevos de regresión), 0 FAIL.
- `bash tools/run_domain_checks.sh` → 25 assertions OK.
- `./gradlew test/lint/assemble`: NO VERIFICADO (sin Android SDK).

### Siguiente prioridad
- Ciclo 2: auditoría estática de persistencia (Room cascadas/índices/transacciones/N+1, restore atómico).
  Después recordatorios end-to-end, notas, rutinas.

---

## Sesión 004 — 2026-08-11 (OpenHands: autonomía nocturna, Ciclos 2-3: persistencia, recordatorios, notas)

**Objetivo**: auditar persistencia/Room, backup/restore, recordatorios end-to-end, seguridad del
manifiesto y notas. Buscar bugs P0/P1 reales.
**Entorno**: rama `jules/autonomous-ordia`. Sin Android SDK; kotlinc/JUnit4 en /tmp.

### Auditoría estática (sin hallazgos P0/P1)
- **Persistencia/Room**: Entities con FK + índices correctos; `TaskDao.deleteSubtreeAndSelf`
  transaccional (resuelve huérfanos de `parentTaskId`, ORD-025); OrdiaDatabase con migraciones
  1→7 y SIN `fallbackToDestructiveMigration` (no hay pérdida silenciosa por schema mismatch).
- **Backup/Restore**: `RoomBackupStore.replaceAll` dentro de `database.withTransaction` atómica,
  orden de borrado/inserción coherente con FK; pre-restore backup verificado; verify-after-commit
  con rollback; checksum SHA-256 (ORD-031); mutex de operación. Sólido.
- **Recordatorios**: `ReminderScheduler` usa `enqueueUniqueWork`+REPLACE (sin duplicados);
  `TaskReminderWorker` re-lee la tarea y filtra completed/archived/cancelled, maneja quiet hours
  (reschedule a nextEndMillis), reintenta si falta POST_NOTIFICATIONS; `ReminderActionReceiver`
  exported=false (no spoofable) y bajo `TaskMutationGate.mutex`; `ReminderResyncReceiver` solo
  re-encola futuros (ReminderSync filtra trigger<=now). Sólido.
- **Manifiesto**: allowBackup=false, usesCleartextTraffic=false; MainActivity SEND/PROCESS_TEXT
  (texto capado a MAX_SHARED_TEXT_CHARS, URI permiso en runCatching). Sólido.
- **toggleTask + RecurrenceEngine**: mutex, reminder/start offset preservado en recurrencia,
  guard de avance (<=completedAt), subtask auto-complete con undo log. Sólido.

### Bug P1 encontrado y corregido (commit `2ae258a`)
- `NoteBlockCodec.decode`: el `runCatching` envolvía TODO el bucle de parseo. Si un único elemento
  del array JSON estaba malformado (p.ej. un string donde se esperaba un objeto),
  `array.getJSONObject(i)` lanzaba y el catch devolvía `listOf(NoteBlock(text = fallbackBody))`,
  perdiendo TODOS los bloques (data loss silencioso). Probe: `[HEADING válido, "badstring",
  PARAGRAPH válido]` → antes 1 bloque vacío.
- Fix: validar el array raíz por separado (si no es JSON → fallbackBody, degradación conocida);
  parsear cada elemento de forma aislada, descartar malformados (continue), conservar válidos;
  caer al fallback solo si TODOS fallan. Ahora → 2 bloques válidos.
- 11 tests nuevos en `NoteBlockCodecTest.kt` (la clase NO tenía cobertura previa): round-trip
  de todos los tipos, fallback a body, elemento malformado, todos malformados, tipo desconocido
  (forward-compat), id faltante, array vacío, truncated JSON, toPlainText.

### Infraestructura
- `tools/run_domain_tests.sh`: runner JUnit4 reutilizable que compila stubs + dominio + tests y
  los ejecuta con `JUnitCore`. Para futuras sesiones autónomas sin Android SDK.

### Tests
- `bash tools/run_domain_tests.sh` → 147 tests PASS (25 clases), 0 FAIL.
- `bash tools/run_domain_checks.sh` → 25 assertions OK.
- `./gradlew test/lint/assemble`: NO VERIFICADO (sin Android SDK).

### Siguiente prioridad
- Ciclo 4: auditoría de rutinas (queries, batch, concurrencia, N+1). Después BUG3 (parser P2).

---

## Sesión 004 (cont.) — 2026-08-11 (OpenHands: Ciclos 3-4: recordatorios, rutinas, BUG3)

**Objetivo**: auditar recordatorios end-to-end, rutinas y resolver BUG3 (P2 parser).
**Entorno**: rama `jules/autonomous-ordia`. Sin Android SDK; kotlinc/JUnit4 en /tmp.

### Ciclo 3 — Recordatorios (sin hallazgos P0/P1)
- `ReminderScheduler`: `enqueueUniqueWork` + `REPLACE` → sin duplicados; cancelación reutiliza
  mismo unique name.
- `TaskReminderWorker`: re-lee la tarea (no confía en el snapshot de entrada), filtra
  completed/archived/cancelled, respeta quiet hours (reschedule a `nextEndMillis`), reintenta
  si falta `POST_NOTIFICATIONS`.
- `ReminderActionReceiver`: `exported=false` (no spoofable), bajo `TaskMutationGate.mutex`.
- `ReminderResyncReceiver`: solo re-encola futuros (`ReminderSync` filtra `trigger<=now`);
  responde a TIME/TIMEZONE/DATE.
- Conclusión: la capa de recordatorios es sólida. No se encontraron P0/P1.

### Ciclo 4 — Rutinas (sin hallazgos P0/P1; P3 menor)
- `RoutineRules.wasRunToday`: dedup correcta (filtra completed/archived/cancelled y compara
  `createdAt` con `today`).
- `runRoutine`: crea tareas con `sortOrder=index` → el orden de los pasos se preserva en la
  bandeja (observeAll ordena por `sortOrder ASC`). `createdAt=now+index` solo evita empates.
- `undoLastAutomation` + `AutomationUndoRules.createdTaskIds`: el undo de rutina es REAL —
  elimina las tareas creadas si siguen intactas en inbox (no borra las que el usuario ya
  completó/modificó). Testeado (`routine_withoutSnapshots_treatsEveryAffectedTaskAsCreated`).
- P3 menor: `saveRoutine` hace `deleteStep` por cada paso existente + `addStep` por cada paso
  nuevo SIN transacción atómica; si el proceso muere a mitad, la rutina queda con pasos
  parciales. Registrado en BACKLOG (P3, OPEN).

### BUG3 (P2) resuelto — commit `a48c5d7`
- `relativePattern` exigía dígitos (`\d{1,3}`) → "en dos horas", "dentro de tres días",
  "en una hora" no se parseaban (`dueAt=null`).
- Fix: patrón acepta dígitos O palabras (un/una, dos..doce) e introductor "dentro de";
  `parseWrittenNumber()` convierte el grupo. Cobertura 1-12 (casos comunes en español).
- 8 tests nuevos (incl. regresión de dígitos).

### Tests
- `bash tools/run_domain_tests.sh` → 155 tests PASS (25 clases), 0 FAIL.
- `bash tools/run_domain_checks.sh` → 25 assertions OK.
- `./gradlew test/lint/assemble`: NO VERIFICADO (sin Android SDK).

### Commits
- `2ae258a` fix: NoteBlockCodec.decode resiliente por elemento.
- `78b4ef4` docs(autonomy): memoria ciclos 2-3.
- `a48c5d7` fix: parser reconoce números escritos en tiempo relativo.

### Siguiente prioridad
- Ciclo 5: auditoría de Onboarding (caber en pantallas pequeñas, botones accesibles, sin scroll
  imposible) y responsive. Después: NoteEditor `rememberSaveable`, atomicidad de `saveRoutine`,
  ítems P2 pendientes.

---

## Ciclo 5 — 2026-08-11

- HEAD inicial: `fa44990` (docs: memoria ciclos 3-4).
- Entorno: OpenJDK 21, kotlinc 2.1.20, libs en /tmp/libs. Baseline pre-fix: 155 tests + 25 smoke OK.

### BUG4 (P1) resuelto — parser: meridiem "de la tarde/noche" + limpieza de título
- `timePatterns` no reconocía "de la mañana/tarde/noche" como meridiem: "a las 4 de la tarde" →
  hora 04:00 (madrugada) en vez de 16:00; "a las 9 de la tarde" → 09:00 en vez de 21:00.
  Bug serio de P0/P1: tarea programada en horario totalmente equivocado.
- `mediodía`/`medianoche` solo capturaban la palabra, no "al mediodía"/"a la medianoche":
  dejaban "al"/"a la" sueltos en el título.
- Orden de limpieza destruía "esta mañana": el borrado genérico de "mañana" corría ANTES que
  `partOfDayPattern.replace`, dejando "esta" huérfano ("correo al jefe esta").
- Fix (mínimo):
  1. `timePatterns[0]` ("a las…") acepta opcional `de la mañana|tarde|noche` como grupo meridiem.
  2. `mediodía`/`medianoche` aceptan prefijo opcional `al ` / `a la ` para limpiar el título.
  3. `explicitTime` normaliza el meridiem extendido: "de la tarde"/"de la noche" → pm (+12),
     "de la mañana" → am (12→0).
  4. Limpieza del título reordenada: `partOfDayPattern` y `timePatterns` se aplican ANTES del
     borrado genérico de "mañana"/"hoy" (ambos contienen "mañana").
  5. `monthNamePattern.replace` ahora es condicional: solo elimina si el mes es válido, evitando
     que "9 de la" (en "a las 9 de la tarde") se destruya y deje "a las" + "tarde" sueltos.

### Tests
- 8 tests nuevos de regresión: `deLaTardeAppliesPmOffset`, `deLaNocheAppliesPmOffset`,
  `deLaMananaKeepsAmHour`, `deLaTardeWithMinutesAppliesPmOffset`, `deLaTardeDoesNotBreakTitle`,
  `alMediodiaParsesNoonAndCleanTitle`, `aLaMedianocheParsesMidnightAndCleanTitle`,
  `estaMananaCleanedFullyFromTitle`.
- `bash tools/run_domain_tests.sh` → 163 tests PASS (25 clases), 0 FAIL.
- `bash tools/run_domain_checks.sh` → 25 assertions OK.
- `./gradlew test/lint/assemble`: NO VERIFICADO (sin Android SDK).

### Commits
- `4a20688` fix: parser reconoce "de la tarde/noche" y limpia título. (pushed a origin/openhands/autonomous-ordia)

### Siguiente prioridad
- Ciclo 6: auditoría de Onboarding (responsive, pantallas pequeñas) y NoteEditor
  `rememberSaveable`; seguir auditando el parser (casos límite: "a las 3pm de la tarde",
  horas con "de la madrugada", meses con tildes en mayúsculas).

---

## Ciclo 6 (2026-08-11) — parser: contexto PM de parte del día + "12 de la noche" = medianoche

- HEAD inicial: `fa44990` (docs: memoria ciclos 3-4). Rama: `openhands/autonomous-ordia` (synced).
- Entorno: OpenJDK 21, kotlinc 2.1.20, libs en /tmp/libs. Baseline pre-fix: 163 tests + 25 smoke OK.

### Problema seleccionado (P1 — tareas programadas 12h equivocadas)
Auditoría con `Probe2.kt` (/tmp/parser-probe/) reveló 4 bugs P1 de alto impacto, frases
ESPAÑOLAS EXTREMADAMENTE COMUNES que el parser interpretaba en horario totalmente errado:

1. **"esta tarde a las 4"** → 04:00 (4 AM!) en vez de 16:00.
2. **"esta noche a las 9"** → 09:00 (9 AM!) en vez de 21:00.
3. **"mañana a la tarde"** → 09:00 + "a la tarde" pegado en el título (sin PM aplicado).
4. **"a las 12 de la noche"** → 12:00 (mediodía) en vez de 00:00 (medianoche).

### Causa raíz
- `explicitTime` (hora "a las 4", sin meridiem) tenía prioridad sobre `partOfDayTime`
  ("esta tarde"), pero al no propagar el contexto PM de la parte del día, la hora quedaba AM.
- No existía reconocimiento de parte del día "suelta" ("a la tarde"/"de la tarde" sin "esta"):
  no aportaba hora canónica ni contexto PM, y dejaba restos en el título.
- "12 de la noche": el fix del ciclo 5 hacía `+12` para horas 1-11, pero para hora 12 dejaba 12
  (mediodía); el caso "12 de la noche"=medianoche quedaba mal.
- "de la madrugada" no estaba como meridiem en `timePatterns[0]` (dejaba restos en título).

### Solución (mínima, quirúrgica en `NaturalTaskParser.kt`)
1. Nuevo `standalonePartOfDayPattern` = `(a la|de la) (tarde|noche|madrugada)` con horas canónicas
   (tarde 15, noche 21, madrugada 4). NO fuerza fecha (solo hora del día sobre la fecha parseada).
2. `hasPartOfDayPmContext`: true si parte del día (esta o suelta) es tarde/noche.
3. `explicitTime` ahora emite `Pair<LocalTime, Boolean>` (hora + tuvo meridiem explícito).
4. Contexto PM: si hora explícita sin meridiem + contexto PM + hora en 1..11 → +12.
5. Fallback de hora: `partOfDayTime ?: standalonePartOfDayTime` cuando no hay hora explícita.
6. "12 de la noche" → 00:00 (caso especial: noche + 12 = medianoche, no mediodía).
7. "de la madrugada" añadido a `timePatterns[0]` y a `isAm` (12→0).
8. Limpieza de título: `standalonePartOfDayPattern.replace` después de `timePatterns`,
   antes del borrado genérico (evita restos "a la"/"de la").

### Tests
- 6 tests nuevos de regresión: `estaTardeConHoraSinMeridiemAplicaPm`,
  `estaNocheConHoraSinMeridiemAplicaPm`, `aLaTardeSueltaDefineHoraYNoFuerzaFecha`,
  `deLaTardeSueltaDaHoraCanonicaHoy`, `doceDeLaNocheEsMedianoche`,
  `deLaMadrugadaEsAmYLimpiaTitulo`.
- `bash tools/run_domain_tests.sh` → 169 tests PASS (25 clases), 0 FAIL.
- `bash tools/run_domain_checks.sh` → 25 assertions OK.
- `./gradlew test/lint/assemble`: NO VERIFICADO (sin Android SDK).

### Archivos modificados
- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt` (+27/-6)
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt` (+43)

### Hallazgos adicionales (menores, no abordados esta run)
- "salir de madrugada" (sin "a las"/"a la") no es reconocido (deja "de madrugada" en título,
  dueAt null). Caso raro; el patrón standalone exige "a la"/"de la". Para backlog.
- "a las 24" → null (24:00 es válido como medianoche pero raro). Menor.
- "a las 3.5" → comportamiento extraño (".5" suelto en título). Edge, no feature real.

### Siguiente prioridad
- Continuar auditoría del parser (casos límite), luego UX: Onboarding responsive,
  NoteEditor `rememberSaveable`, atomicidad de `saveRoutine`. Ver BACKLOG.

---

---

## CICLO 7 — Parser: "a las 24" / "24:00" = medianoche

- **Fecha (UTC)**: 2026-08-11
- **HEAD inicial**: 4f43c0b (ciclo 6, en origin)
- **Trigger**: continuación autónoma; auditoría de casos límite del parser.

### Problema seleccionado (P3 → resulta info-loss real)

"a las 24" (forma común de medianoche en horarios de transporte, farmacias 24h, cierre de pubs)
NO se reconocía como hora válida: el regex de hora `([01]?\d|2[0-3])` solo aceptaba 0-23, así que
"a las 24" producía `dueAt=null` (tarea SIN recordatorio) y dejaba "a las 24" como basura en el
título. Casos verificados antes del fix:
- "comprar pan a las 24" → title='comprar pan a las 24', dueAt=null
- "reunion a las 24:00" → title='reunion a las 24:00', dueAt=null
- "cena a las 24 de la noche" → title='cena a las 24' (basura), dueAt=21:00 (inconsistente)

Pérdida de información: el usuario cree que dejó una tarea para medianoche y en realidad queda
sin hora/recordatorio. Por eso se sube de P3 a impacto real de info-loss.

### Causa raíz
- `timePatterns[0]` y `[1]` usaban `2[0-3]` para la hora → 24 no casaba.
- Sin casamiento de hora, "24" sobrevivía al limpiado de título y "de la noche" sí se parseaba
  por separado, generando el resultado inconsistente.

### Solución (mínima, en `NaturalTaskParser.kt`)
1. Regex de hora `2[0-3]` → `2[0-4]` en `timePatterns[0]` y `[1]` (acepta 20-24).
2. En `explicitTime`: si `hour == 24` → `LocalTime.MIDNIGHT to true` (medianoche absoluta).
   Marcar `meridiem=true` bloquea que el contexto PM de parte del día aplique +12 sobre un 24
   que ya es absoluto (24 no es 12, no entra en `hour<12`). "24" se comporta igual que
   "medianoche"/"a las 0".
3. Al casar la hora, el token "24" (con sus variantes "24:00", "24 de la noche") se elimina del
   título en el limpiado genérico, dejando el título limpio.

### Tests
- 3 tests nuevos de regresión: `aLas24EsMedianocheYLimpiaTitulo`,
  `aLas24ConMinutosEsMedianoche`, `aLas24DeLaNocheLimpiaTituloYEsMedianoche`.
- `bash tools/run_domain_tests.sh` → 172 tests PASS (25 clases), 0 FAIL.
- `bash tools/run_domain_checks.sh` → 25 assertions OK.
- `./gradlew test/lint/assemble`: NO VERIFICADO (sin Android SDK).

### Archivos modificados
- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt` (+18/-11)
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt` (+20)

### Hallazgos adicionales (probe de descubrimiento ciclo 7)
Casos de título que dejan basura/residuos NO limpiados (candidatos a ciclo 8):
- "ir al dentista mañana a primera hora" → title='ir al dentista a primera hora' ("a primera
  hora" NO se interpreta ni se limpia; debería ser ~09:00 inicio de jornada).
- "llamar a mamá el viernes que viene" → title='llamar a mamá que viene' ("que viene" se queda).
- "reunion a las 3pm del jueves" → title='reunion del' ("del" huérfano por limpiado de fecha).
- "reunion de 18 a 20" → rango horario no parseado (title y dueAt nulos). Rango válido de horas.
- "pasado mañana a las 4" → 04:00 AM (no aplica contexto; hora sin meridiem sin parte del día
  queda AM — consistente con diseño, no bug).

### Siguiente prioridad
- Ciclo 8: limpiar residuos de título ("que viene", "del" huérfano, "a primera hora") y/o
  parsear rango horario "de 18 a 20". Evaluar impacto real antes de implementar.

---

## SESIÓN 004 — Ciclo 8 (NaturalTaskParser: residuos de día de la semana)

- **Fecha (UTC)**: 2026-08-11
- **HEAD inicial**: `accbdff` (ciclo 7)
- **Trigger**: continuación autonomía — limpiar residuos de título en casos MUY comunes.
- **Resultado**: ÉXITO — VERIFIED

### Problema seleccionado
- **Prioridad**: P3 (calidad de título en captura ultrarrápida).
- **Causa raíz**: `weekdayPattern` solo capturaba el prefijo `el`/`próximo` pero NO:
  - prefijo `del`/`de` ("reunión del jueves", "a las 3pm del jueves", "factura del martes");
  - sufijo `que viene`/`próximo(s|a)` ("el viernes que viene", "el miércoles próximo").
  Al limpiar el día, los tokens adyacentes quedaban huérfanos en el título: "reunión del",
  "llamar a mamá que viene", "ir al dentista próximo". Casos de uso extremadamente comunes.

### Solución
- Extendido `weekdayPattern` para capturar prefijo `del`/`de` y sufijo `que viene`/`próximo(s|a)`.
- Group 1 sigue siendo el día (no rompe `toDayOfWeek()` ni `parseRecurrence`).
- El `.replace(value, " ")` del cleanup ahora consume prefijo+día+sufijo completos.

### Tests
- 6 tests nuevos de regresión (del jueves, del jueves con hora, el viernes que viene,
  el miércoles próximo, del viernes que viene con hora, preservación de "el viernes a las 15:00").
- `bash tools/run_domain_tests.sh` → 178 tests OK (25 clases). (+6 respecto a ciclo 7)
- `bash tools/run_domain_checks.sh` → 25 assertions OK.
- `./gradlew test/lint/assemble`: NO VERIFICADO (sin Android SDK).

### Archivos modificados
- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt` (regex extendido)
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt` (+48, 6 tests)

### Hallazgos adicionales
- Probe confirmó 10/10 casos ahora limpios (antes 7/10 con residuos).
- Pendientes ciclo 9 (P3 menores): "a primera hora" (sin interpretar); rango horario
  "de 18 a 20" (no parseado, dueAt=null). Ver BACKLOG.

### Siguiente prioridad
- Ciclo 9: "a primera hora" → ~09:00 + limpiar del título; o rango horario "de 18 a 20".
  Evaluar impacto real antes de implementar. Luego UX/ítems P2.

---

## SESIÓN 004 — Ciclo 9 (NaturalTaskParser: "a primera hora")

- **Fecha (UTC)**: 2026-08-11
- **HEAD inicial**: `c06e1da` (ciclo 8)
- **Trigger**: continuación autonomía — frase natural muy común sin interpretar.
- **Resultado**: ÉXITO — VERIFIED

### Problema seleccionado
- **Prioridad**: P2 (captura ultrarrápida + persistencia: la frase no generaba recordatorio).
- **Causa raíz**: "a primera hora" (y "a primera hora de la mañana/madrugada") no tenía
  patrón dedicado. El parser: (1) NO asignaba hora → `dueAt=null` (sin recordatorio) salvo
  que hubiera otra fecha; (2) dejaba residuo "a primera hora" en el título. Frase de uso
  cotidiano ("ir al dentista mañana a primera hora") quedaba incompleta.

### Solución
- Nuevo `primeraHoraPattern`: `(?i)\b(?:a\s+)?primera\s+horas?(?:\s+de\s+la\s+(?:mañana|madrugada))?\b`
  (acepta ñ y "manana" sin acento por robustez de teclado).
- `primeraHoraTime = LocalTime.of(9, 0)` como hora canónica de inicio de jornada.
- Se aplica como **fallback** en `parsedTime` (después de hora explícita y partes del día),
  así una hora de reloj siempre gana y "de la tarde" sigue usando `standalonePartOfDayPattern`.
- Limpieza del título: `.let { primeraHoraPattern.replace(it, " ") }` tras
  `standalonePartOfDayPattern` (orden correcto: si "primera hora de la tarde", el patrón
  standalone consume "de la tarde" primero y el de primera hora consume "a primera hora").

### Tests
- 4 tests nuevos de regresión:
  - "mañana a primera hora" → 09:00 + título limpio;
  - "a primera hora" sin fecha → hoy 09:00;
  - "del jueves a primera hora de la mañana" → jueves 09:00 + título "Reunión";
  - no deja residuo "primera"/"hora" en el título.
- `bash tools/run_domain_tests.sh` → 182 tests OK (25 clases). (+4 respecto a ciclo 8)
- `bash tools/run_domain_checks.sh` → 25 assertions OK.
- Probe adicional sobre 6 casos reales: todos limpios y con hora correcta (09:00; "de la
  tarde"=15:00 vía standalone). NO VERIFICADO: gradle/lint/assemble (sin Android SDK).

### Archivos modificados
- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt` (+patrón, +fallback, +cleanup)
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt` (+4 tests, +import assertFalse)

### Hallazgos adicionales
- "a primera hora de la tarde" (caso aislado sin contenido) dejaba el título completo;
  con contenido real ("Reunión a primera hora de la tarde") se limpia correctamente y
  asigna 15:00 vía standalone. No es un bug real; se documentó en el patrón.
- Pendiente ciclo 10: rango horario "de 18 a 20" (dueAt=null, no parseado). Evaluar
  impacto real y si aporta utilidad (rango vs. hora única).

### Siguiente prioridad
- Ciclo 10: rango "de 18 a 20" o nueva auditoría funcional (captura/What Now/inteligencia).

---

## SESIÓN 010 — Bug: "N min antes" clasificado como duración, no recordatorio

- **Fecha (UTC)**: 2026-08-11
- **Rama**: `openhands/autonomous-ordia`
- **HEAD inicial**: 41b3abc
- **Prioridad**: P1 (recordatorio perdido: el usuario esperaba un recordatorio pero obtenía una duración).
- **Causa raíz**: El patrón de recordatorio #2 `(\d{1,3})\s*(minutos?|horas?|días?)\s+antes`
  NO aceptaba la abreviatura `min` (ni `hora`), mientras que el patrón de duración #3
  `(\d{1,3})\s*(minutos?|min)\b` SÍ la aceptaba. Como los recordatorios se extraen ANTES
  que la duración, "30 min antes" no casaba como recordatorio y caía como duración:
  - `Avisar 30 min antes` -> dur=30, rem=null, título="Avisar antes" (recordatorio perdido).
  - `Reunión 15 min antes` -> dur=15, rem=null (recordatorio perdido).

### Solución
- Patrón de recordatorio #2 ampliado a `(\d{1,3})\s*(minutos?|min|horas?|hora|días?|día)\s+antes`
  para aceptar las mismas abreviaturas que el patrón #1 y que el de duración. Ahora
  "30 min antes" se reconoce como recordatorio ANTES de llegar al de duración.
- Mínimo cambio: 1 línea de regex + comentario explicativo.

### Tests
- 2 tests nuevos de regresión:
  - "Avisar 30 min antes" -> rem=30, dur=null, título="Avisar".
  - "Reunión 15 min antes" (sin verbo de recordatorio) -> rem=15, dur=null, título="Reunión".
- `bash tools/run_domain_tests.sh` -> 184 tests OK (25 clases). (+2 respecto a ciclo 9)
- `bash tools/run_domain_checks.sh` -> 25 assertions OK.
- Probe sobre 8 casos de recordatorio/duración: todos correctos. NO VERIFICADO: gradle/lint/assemble.

### Archivos modificados
- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt` (patrón reminder #2 ampliado)
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt` (+2 tests)

### Hallazgos adicionales (auditoría ciclo 10)
- Probe amplio reveló más oportunidades reales (a evaluar en próximos ciclos):
  - `#tag`/`@tag` no se limpia del título ni asigna categoría explícita (P2).
  - `Reunión de 30 minutos` deja residuo "de" en título (P3).
  - `Trabajar 2h` no reconoce "2h" compacto como duración (P2).
  - `prioridad alta:` y `urgente`/`importante` a mitad de frase no fijan prioridad (P2).

### Siguiente prioridad
- Limpiar `#tag`/`@tag` del título y honrar categoría explícita, o bien "2h" compacto.

---

## SESIÓN 011 — Categoría explícita por etiqueta "#cat"/"@cat" + limpieza del título

- **Fecha (UTC)**: 2026-08-11
- **Rama**: `openhands/autonomous-ordia`
- **HEAD inicial**: 0c02eaf
- **Prioridad**: P2 (UX/inteligencia: el usuario etiquetaba con `#cat`/`@cat` pero la etiqueta
  quedaba como residuo feo en el título y, peor, `@trabajo` se ignoraba y la categoría se
  infería por keywords —a veces mal, p. ej. "Llamar a Ana @trabajo" → cat=personal—).
- **Causa raíz**: El parser solo detectaba categoría por keywords ("comprar", "reunión"...).
  No existía manejo de etiquetas explícitas `#cat`/`@cat`. El usuario al escribir `#trabajo`
  o `@compras` expresaba su intención de categoría, pero:
  1. La etiqueta quedaba en el título ("Comprar leche #compras").
  2. `@cat` no se reconocía y la categoría se infería (mal) por keywords.
  3. `#cat` válida (p. ej. `#personal`) era invalidada por keywords contradictorias.

### Solución
- Nuevo patrón `explicitCategoryPattern` = `[#@](trabajo|compras|salud|casa|personal)\b`,
  construido a partir de los nombres de categoría conocidos (no hardcodeado, evita
  divergencia con `categories`). Solo reconoce categorías conocidas, así un hashtag de
  contenido como `#proyecto` o `#vacaciones` NO se roba como categoría ni se elimina.
- En `parse()`, la categoría explícita se extrae ANTES de la inferencia por keywords y
  tiene prioridad: si el usuario etiquetó `#personal`, gana sobre "comprar leche"→compras.
- La etiqueta reconocida se elimina del título ("Comprar leche #compras" → "Comprar leche").
- Etiquetas desconocidas (p. ej. `#proyecto`) permanecen intactas en el título (contenido
  del usuario) y la categoría se infiere normalmente.

### Tests
- 5 tests nuevos de regresión:
  - `#compras` limpia título y asigna categoría.
  - `@trabajo` limpia título y asigna categoría (antes se ignoraba → personal).
  - `#personal` sobreescribe la inferencia por keywords.
  - `#proyecto` (desconocido) queda en el título y la categoría se infiere (trabajo).
  - `#trabajo` combinado con fecha y hora: título limpio + categoría + hora correcta.
- `bash tools/run_domain_tests.sh` -> 189 tests OK (25 clases). (+5 respecto a ciclo 10)
- `bash tools/run_domain_checks.sh` -> 25 assertions OK.
- Probe sobre 10 casos: todos correctos. NO VERIFICADO: gradle/lint/assemble.

### Archivos modificados
- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`
  (+`explicitCategoryPattern`, +extracción con prioridad sobre keywords, +comentario)
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt` (+5 tests)

### Hallazgos adicionales (auditoría ciclo 11)
- `Trabajar 2h` / `Estudiar 1h`: "Nh" compacto no se reconoce como duración (P2, pendiente).
- `Reunión de 30 minutos` deja residuo "de" en título (P3, pendiente).
- Rango horario "de 18 a 20" → dueAt=null (P3, pendiente de evaluación).
- Title residue "que viene" tras día de semana (P3, pendiente).

### Siguiente prioridad
- "Nh" compacto como duración ("Trabajar 2h" → dur=120) — P2, alto valor (captura rápida).

---

## SESIÓN 012 — Duración compacta "Nh" ("Trabajar 2h" → 120 min)

- **Fecha (UTC)**: 2026-08-11
- **Rama**: `openhands/autonomous-ordia`
- **HEAD inicial**: 7cdc803
- **Prioridad**: P2 (captura rápida: "2h"/"1h" es la forma natural y compacta de expresar
  duración; antes no se reconocía y quedaba "2h" como residuo feo en el título sin
  asignar duración).
- **Causa raíz**: `durationPatterns` solo reconocía unidades completas (`minutos`, `min`,
  `horas`, `hora`). La abreviatura compacta "h" pegada al número ("2h") no casaba con
  ningún patrón, así que `durationMinutes=null` y "2h" se quedaba en el título.

### Solución
- Nuevo patrón `\b(\d{1,3})\s*(h)\b` añadido al final de `durationPatterns`.
- El `\b` final es clave: en "2horas" la 'h' va seguida de 'o' (ambos word chars), por lo
  que NO hay límite de palabra entre ellas → el patrón compacto NO casa "2horas". Así no
  roba el prefijo ni deja residuo "oras"; el patrón completo `horas?` sigue manejándolo.
- Detección de unidad ampliada: `unit.startsWith("hora") || unit == "h"` → horas (×60).

### Tests
- 4 tests nuevos de regresión:
  - "Trabajar 2h" → dur=120, título="Trabajar".
  - "Estudiar 1h" → dur=60, título="Estudiar".
  - "Reunión 2horas" → dur=120, título limpio (el compacto no roba la palabra completa).
  - "Estudiar 2h recuérdame 15 min antes" → dur=120 + rem=15 (sin interferencia).
- `bash tools/run_domain_tests.sh` -> 193 tests OK (25 clases). (+4 respecto a ciclo 11)
- `bash tools/run_domain_checks.sh` -> 25 assertions OK.
- NO VERIFICADO: gradle/lint/assemble.

### Archivos modificados
- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`
  (+patrón compacto `\b(\d)\s*h\b`, +comentario, +unit check `h`→horas)
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt` (+4 tests)

### Hallazgos adicionales (auditoría ciclo 12)
- `prioridad alta:`/`urgente`/`importante` a mitad de frase no fijan prioridad (P2, pendiente —
  alto valor: "Llamar mamá urgente" debería ser HIGH).
- Residuo "de" en "Reunión de 30 minutos" (P3, pendiente).
- Rango horario "de 18 a 20" → dueAt=null (P3, pendiente de evaluación).

### Siguiente prioridad
- Prioridad a mitad de frase ("urgente"/"importante" como palabra suelta en cualquier
  posición → HIGH) — P2, alto valor (evita olvidos de tareas urgentes).

---

## SESIÓN 013 — Prioridad por sufijo final "urgente"/"importante"

- **Fecha (UTC)**: 2026-08-11
- **Rama**: `openhands/autonomous-ordia`
- **HEAD inicial**: c265777
- **Prioridad**: P2 (evita olvidos: el usuario expresa prioridad en texto libre como palabra
  final "Llamar mamá urgente"; antes caía a NORMAL).
- **Causa raíz**: la detección de "urgente" sin prefijo solo cubría la palabra INICIAL
  (`leadingUrgentPattern`). "urgente"/"importante" como palabra final (sufijo de prioridad)
  no se reconocían, por diseño para evitar falsos positivos de mitad de frase ("no es
  urgente el documento"). Resultado: prioridad genuina del usuario ignorada en el patrón de
  captura más natural ("Llamar mamá urgente").

### Solución
- `trailingPriorityPattern`: `\b(urgente|importante)\b\s*[.!?]?$` → detecta la palabra final.
  - "urgente" → URGENT, "importante" → HIGH.
  - Se limpia del título (igual que el prefijo `!urgente` y el `leadingUrgentPattern`).
- `negatedPriorityPattern`: `\bno\s+(?:es|era|fue|parece|ser[áa])\s+(?:lo\s+)?(?:urgente|importante)\b\s*[.!?]?$`
  → guard de negación. "no es urgente" como palabra final NO activa prioridad (NORMAL) y se
  conserva como contenido del título.
- Orden de prioridad respetado: prefijos `!`/`#` > inicial > sufijo final (sin negación).

### Tests
- 4 tests nuevos de regresión:
  - "Llamar mamá urgente" → URGENT, título="Llamar mamá".
  - "Enviar factura importante" → HIGH, título="Enviar factura".
  - "Comprar leche urgente!" → URGENT, título="Comprar leche".
  - "Revisar el documento, no es urgente" → NORMAL, título conservado.
- `bash tools/run_domain_tests.sh` -> 197 tests OK (25 clases). (+4 respecto a ciclo 12)
- `bash tools/run_domain_checks.sh` -> 25 assertions OK.
- NO VERIFICADO: gradle/lint/assemble.

### Archivos modificados
- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`
  (+trailingPriorityPattern, +negatedPriorityPattern, +rama sufijo final en `when` de prioridad,
  +limpieza del sufijo del título)
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt` (+4 tests)

### Hallazgos adicionales (auditoría ciclo 13)
- Residuo "de" en "Reunión de 30 minutos" (P3, pendiente).
- Rango horario "de 18 a 20" → dueAt=null (P3, pendiente de evaluación).

### Siguiente prioridad
- Nueva auditoría funcional del parser con casos reales adicionales, o resolver el residuo
  "de" (P3) si aporta claridad. Continuar evitando feature bloat.

## SESIÓN 014 — Duraciones fraccionarias "media hora"/"cuarto de hora"

- **Fecha**: 2026-08-11
- **HEAD inicial**: 6486ce3
- **Rama**: `openhands/autonomous-ordia`

### Problema seleccionado
- **Prioridad**: P2 (captura: el usuario expresa duración en fracciones comunes del español sin dígitos; antes se perdía la duración y quedaba residuo en el título).
- **Causa raíz**: `durationPatterns` requieren dígitos (`\d{1,3}`). Frases muy comunes como "media hora" (30 min) y "(un) cuarto de hora" (15 min) no casaban, así que `durationMinutes` quedaba null y la frase se conservaba como residuo en el título ("Estudiar media hora" → título="Estudiar media hora", dur=null).

### Solución
- `fractionalDurationPattern`: `\b(media\s+hora|(?:un\s+)?cuarto\s+(?:de\s+)?hora)\b`.
  - "media hora" → 30 min, "(un) cuarto de hora"/"cuarto de hora" → 15 min.
  - Guard de "hora": "cuarto" solo ("Limpiar el cuarto") NO casa (cuarto=habitación).
- Integrado en el bloque de duración: ocurrencia más a la izquierda entre duración numérica y fraccionaria; se limpia del título en cualquier caso.
- `coerceIn(5, 24*60)` preserva los límites existentes.

### Tests
- 5 tests nuevos: media hora=30, un cuarto de hora=15, cuarto de hora=15, media hora+fecha/hora no interfiere, cuarto=habitación no es duración.
- `bash tools/run_domain_tests.sh` -> 202 tests OK (25 clases). (+5 respecto a ciclo 13)
- `bash tools/run_domain_checks.sh` -> 25 assertions OK.
- NO VERIFICADO: gradle/lint/assemble.

### Archivos modificados
- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt` (+fractionalDurationPattern, +rama fraccionaria en durationMinutes, +limpieza)
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt` (+5 tests)

### Hallazgos adicionales (auditoría ciclo 14)
- Residuo "de" en "Reunión de 30 minutos" (P3, sigue ABIERTO).
- Rango horario "de 18 a 20" → dueAt=null (P3, sigue pendiente).
- "finde"/"fin de semana" no se parsea como fecha (ambigüedad de design: ¿sábado? se omite).

### Siguiente prioridad
- Resolver el residuo "de" (P3), o evaluar rango horario "de 18 a 20". De lo contrario nueva auditoría funcional fuera del parser (tareas/rutinas/búsqueda/What Now).

### Commit / push
- (commit al final del ciclo)

---

## Ciclo 15 — 2026-08-11 (OpenHands, autonomía)

### Contexto
- HEAD inicial: `9c5222f` (feat(parser): duraciones fraccionarias).
- Branch: `openhands/autonomous-ordia`.

### Problema seleccionado
- P3 (datos) — `saveRoutine` no atómico: delete-then-reinsert de pasos de rutina sin transacción.

### Causa raíz
- `OrdiaViewModel.saveRoutine` borraba cada paso existente y luego insertaba los nuevos
  en llamadas DAO separadas. Si el proceso moría entre el borrado y las inserciones, la
  rutina quedaba con pasos parciales o sin pasos (pérdida de trabajo del usuario). Además
  leía los pasos existentes desde `uiState` (memoria) en vez de la fuente de verdad.

### Solución
- `RoutineStepDao.replaceSteps(routineId, steps)` con `@Transaction` (deleteByRoutine +
  insert por paso), siguiendo el patrón de `deleteSubtreeAndSelf`.
- `RoutineRepository.replaceSteps(...)` expuesto.
- `saveRoutine` construye la lista limpia de pasos y los reemplaza atómicamente.
- Reasigna `position` por índice (orden de visualización).

### Archivos modificados
- `app/src/main/java/com/ordia/app/data/local/Daos.kt` (+deleteByRoutine, +replaceSteps @Transaction)
- `app/src/main/java/com/ordia/app/data/repository/Repositories.kt` (+replaceSteps)
- `app/src/main/java/com/ordia/app/ui/OrdiaViewModel.kt` (saveRoutine usa replaceSteps)

### Tests
- `bash tools/run_domain_tests.sh` -> 202 tests OK (25 clases). (sin regresión)
- `bash tools/run_domain_checks.sh` -> 25 assertions OK.
- Sintaxis DAO/Repository verificada con kotlinc + stubs (sin errores en líneas cambiadas).
- NO VERIFICADO: integración DAO/Room/gradle requiere Android SDK.

### Hallazgos adicionales
- Recurrencia mensual verificada correcta (`plusMonths(1)`); Probe3 discrepancia era por `due` distinto, no bug.
- `addStep`/`deleteStep` del repositorio ya no usados por `saveRoutine` (se conservan para API).

### Commit / push
- fix(data): `saveRoutine` atómico con replaceSteps @Transaction (previene pérdida de pasos en crash)
- commit `0f14ded`; push OK a `openhands/autonomous-ordia` (9c5222f..0f14ded).

### Siguiente prioridad
- Continuar auditoría funcional no-parser (tareas/búsqueda/What Now/automatizaciones) o resolver P3 pendientes del parser.

---

## Run 2026-08-11 — Ciclo 16 (OpenHands, autonomía)

### Contexto inicial
- HEAD inicial: `0f14ded`.
- Branch: `openhands/autonomous-ordia`.

### Problema seleccionado
- P3 (parser/captura) — Rango horario "de H1 a H2 [horas]" no parseado + residuo "de" en
  duraciones numéricas.

### Causa raíz
- `NaturalTaskParser` no tenía patrón para rangos horarios. "Cita de 18 a 20" dejaba el
  rango completo en el título y `durationMinutes=null`. Peor, "Clase de 18 a 20 horas"
  casaba "20 horas" con el patrón de duración numérica → 20h (1200 min) falso.
- Además, el conector "de" antes de una duración numérica ("Reunión de 30 minutos") no se
  eliminaba junto con la duración → título "Reunión de".

### Solución
- `timeRangePattern` = `\b(?:de\s+)?(\d{1,2})\s*(?:a|-)\s*(\d{1,2})(\s*(?:horas?|hs|h))?\b`.
- Se procesa ANTES que `durationPatterns` para que el segundo número no sea robado como
  duración. Duración = (end-start)*60 min, con sanitización (end>start, end<=24, ≤24h).
- Guard anti-falso-positivo: solo se acepta como rango horario si hay unidad final o alguna
  hora>=13 (formato 24h inequívoco). "Comprar de 2 a 5 entradas" no se toca.
- No fija hora de inicio (ambigua sin meridiem); solo la duración, de forma honesta.
- La duración numérica posterior arrastra el conector "de " cuando lo precede (regex
  `\bde\s+<dur>`), limpiando "Reunión de 30 minutos" → "Reunión".

### Archivos modificados
- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`
  (+`timeRangePattern`; lógica de rango + conector "de" en bloque de duración)
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`
  (+7 tests: rango con unidad, 24h sin unidad, horas pequeñas con unidad, anti-falso-positivo
  en conteo, rango con texto final, conector "de" en min/horas)

### Tests
- `bash tools/run_domain_tests.sh` -> 209 tests OK (25 clases). (+7 desde 202)
- `bash tools/run_domain_checks.sh` -> 25 assertions OK.
- Verificación con probe JVM cubriendo 15 casos (rango/anti-falso/duraciones/horas).
- NO VERIFICADO: gradle/lint/Android (sin Android SDK).

### Hallazgos adicionales
- BACKLOG: 2 entradas P3 parser (rango "de 18 a 20", residuo "de") marcadas FIXED → VERIFIED.
- "Trabajo de 8 a 12 horas" → 240 min correcto (guard por unidad).
- "Reunión de 18 a 20 con juan" → "Reunión con juan" + 120 min (preserva texto final).

### Commit / push
- (pendiente; se commitea a continuación)

### Siguiente prioridad
- Nueva auditoría funcional no-parser (tareas/búsqueda/What Now/automatizaciones/contexto)
  o siguientes P3 del parser ("salir de madrugada", "a las 3.5").

---

## Sesión OpenHands 004 — Ciclo 17 (NaturalTaskParser — recurrencia mensual anclada)

- **Fecha (UTC)**: 2026-08-11
- **HEAD inicial**: `63400f3` (origin/openhands/autonomous-ordia, sincronizado vía pull)
- **Ciclo**: 17 (continúa ciclo 16)

### Problema seleccionado
P1 — Recurrencia mensual anclada a día del mes ("el 15 de cada mes") NO se parseaba.
`dueAt` quedaba `null`, el día se quedaba como residuo en el título ("Pagar la cuenta el 15 de"),
la tarea mensual nunca tenía fecha de vencimiento y los recordatorios no disparaban.

### Causa raíz
`parseRecurrence` reconocía "cada mes" (frecuencia simple) y "cada N meses" (intervalo),
pero NO el patrón anclado `N de/del (cada) mes` ("el 15 de cada mes"). Sin anclaje, el día
caía al título y no se generaba fecha. Hallazgo lateral: la rama `monthNameMatch != null`
del `when` de fecha seleccionaba la fecha nominal de mes aunque `parseMonthNameDate`
devolviera `null` (mes inválido), de modo que un sufijo de hora "8 de la manana" generaba
la falsa coincidencia "8 de la" (mes="la" inexistente → null) y anulaba la resolución de
fecha de repeticiones mensuales/semanales con hora explícita.

### Solución
- `RecurrenceResult` +`monthlyDayOfMonth: Int?` (día del mes anclado).
- `parseRecurrence`: patrón `monthlyDayPattern = \b(?:el|los)?\s*(\d{1,2})\s+(?:de|del)\s+(?:cada\s+)?mes(es)?\b`;
  captura el día (1..31), marca la frase para limpieza y devuelve `MONTHLY` anclado.
- `nextMonthlyDate(from, day)`: próxima fecha con ese día, inclusive si es hoy; avanza de
  mes si el día no existe en el mes actual (31 en feb) o ya pasó; recorre ≤24 meses.
- Rama en el `when` de fecha: `recurrence.frequency==MONTHLY && monthlyDayOfMonth!=null -> nextMonthlyDate`.
- Fix lateral: `monthNameDate` = `monthNameMatch?.let { parseMonthNameDate(...) }`;
  la rama de fecha usa `monthNameDate != null` (fecha resuelta, no mera coincidencia regex),
  evitando que "8 de la manana" sombre repeticiones con hora.

### Archivos modificados
- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`
  (+`RecurrenceResult.monthlyDayOfMonth`; `monthlyDayPattern` en `parseRecurrence`;
  `nextMonthlyDate`; rama MONTHLY en `when`; `monthNameDate` resolución)
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`
  (+3 tests: `parsesMonthlyDayOfMonthRecurrence`, `parsesMonthlyDayOfMonthTodayInclusive`,
  `monthlyDayOfMonthKeepsExplicitTime`)

### Tests
- `bash tools/run_domain_tests.sh` -> 212 tests OK (25 clases). (+3 desde 209)
- `bash tools/run_domain_checks.sh` -> 25 assertions OK.
- Probe JVM (12 casos): anclaje a próximo mes, día hoy inclusive, 31 (jul existe), 30
  (jul existe), 10 con hora "de la manana" (antes `due=hoy` ahora `2026-08-10 08:00`),
  "15 de agosto"/"1 de enero" (fecha única, NO recurrente — patrón requiere "mes").
- NO VERIFICADO: gradle/lint/Android/Room (sin Android SDK).

### Hallazgos adicionales
- BACKLOG: entrada P1 mensual anclado añadida a Completados → VERIFIED.
- Caso menor no resuelto (preexistente, word-order raro): "pagar cada mes el 15" (día
  DESPUÉS de "cada mes") queda como MONTHLY sin anclaje + "el 15" en título. No es
  regresión; no se sobre-ingeniería (orden poco común).
- "cada mes" a secas sigue sin fecha (ambiguo, sin día): comportamiento preexistente correcto.

### Commit / push
- `ae43af3` (feat(parser): recurrencia mensual anclada a día del mes) — push a openhands/autonomous-ordia (pendiente push en este run)
- HEAD final: `ae43af3`

### Siguiente prioridad
- Auditoría funcional no-parser (tareas/búsqueda/What Now/automatizaciones/contexto) o
  siguientes P2/P3 del backlog (lint `InsertDriveFile` AutoMirrored, i18n strings).

---

## SESIÓN 017 — Ciclo 18 (RecurrenceEngine: anclaje mensual consistente con parser)

- **Fecha (UTC)**: 2026-08-11
- **HEAD inicial**: `324d1e6` (origin/openhands/autonomous-ordia; push del ciclo 17
  completado al inicio de este run con `github_token`)
- **Ciclo**: 18 (continúa ciclo 17 — cierra consistencia parser↔engine de la nueva
  funcionalidad de recurrencia mensual)

### Problema seleccionado
P1 — `RecurrenceEngine` mensual NO anclaba al día del mes: usaba `base.plusMonths(interval)`,
que **clampa** los días 29-31 al último día válido del mes destino (p. ej. ene 31 + 1 mes →
feb 28). Esto hacía **derivar el ancla** de una recurrencia "el 31 de cada mes" (31 → 30 → 30…)
y era **inconsistente** con el anclaje de `NaturalTaskParser.nextMonthlyDate` (que salta los
meses sin el día). Tras completar la primera ocurrencia generada por el parser, el engine
rompía la promesa de "cada día 31". La nueva funcionalidad del ciclo 17 quedaba a medias en
el ciclo de vida real (completion → próxima ocurrencia).

### Causa raíz
`advance` para `MONTHLY` = `base.plusMonths(interval)`. `java.time` clampa el día del mes
al límite del mes destino en vez de saltar el mes. No había helper de anclaje mensual.

### Solución
- `RecurrenceEngine.nextMonthly(base, interval)`: ancla al `base.dayOfMonth`, busca el
  primer mes a partir de `base + interval` que contenga ese día (`YearMonth.lengthOfMonth`),
  conservando hora y zona de `base`. Recorre ≤24 iteraciones (día ≤31 siempre halla mes
  válido: 31 existe en jul/ago/oct/dic).
- `advance` MONTHLY ahora llama a `nextMonthly`.
- Para días 1-28 (caso más común: "el 15 de cada mes") el comportamiento es **idéntico**
  (todo mes los contiene). Solo cambia días 29-31, ahora correctos y coherentes con el parser.

### Archivos modificados
- `app/src/main/java/com/ordia/app/domain/RecurrenceEngine.kt`
  (+import `YearMonth`; `nextMonthly`; `advance` MONTHLY → `nextMonthly`)
- `app/src/test/java/com/ordia/app/domain/RecurrenceEngineTest.kt`
  (+3 tests: `monthly_anchorsToDayOfMonthAndSkipsMonthsLackingIt`,
  `monthly_preservesDayForCommonDays`, `monthly_advancesPastCompletedAt`)

### Tests
- Red primero: 2 failures (`feb 28` en vez de `mar 31`; `mar 28` en completión tardía).
- `bash tools/run_domain_tests.sh` → **215 tests OK** (25 clases) (212 + 3).
- `bash tools/run_domain_checks.sh` → **25 assertions OK** (kotlinc en PATH vía
  /tmp/kotlinc-home/kotlinc/bin).
- NO VERIFICADO: gradle/lint/Android/Room (sin Android SDK).

### Commit / push
- `c709e26` (fix(parser): RecurrenceEngine mensual anclaje) — push al cierre del run.
- HEAD final: `c709e26`

### Hallazgos adicionales
- Funcionalidad de recurrencia mensual del ciclo 17 ahora cierra su ciclo completo
  (parser → primera fecha → completion → próxima fecha coherente).
- No se requiere cambio de esquema Room: el ancla se infiere del `dueAt` de la tarea
  completada (día del mes), evitando una migración no verificable sin Android SDK.

### Siguiente prioridad
- Auditoría funcional no-parser: What Now (tareas programadas vs. inbox), búsqueda,
  automatizaciones, contexto, GuardianEngine, LearningEngine.

## SESIÓN 018 — Ciclo 19 (NaturalTaskParser: anclaje de recurrencias de intervalo)

- **Fecha (UTC)**: 2026-08-11
- **HEAD inicial**: `cf9841e`
- **Trigger**: continuidad de autonomía; revisión del ciclo 17/18 reveló un bug
  **simétrico** que dejó recurrencias de intervalo (no mensuales) sin `dueAt`.

### Problema seleccionado

P1 — Recurrencias de **intervalo** sin fecha explícita (diaria "cada día", quincenal
"cada 2 semanas", mensual/anual sin día "cada mes"/"cada año") se creaban con
`dueAt=null`. El bloque `when` de resolución de fecha solo anclaba recurrencias con día
explícito (semanal con días, mensual con día del mes). Resultado: la primera ocurrencia
era **invisible** — no aparecía en What Now/planificador; su recordatorio **nunca
disparaba** (`ReminderSync` usa `reminderAt ?: dueAt`, ambos null); se olvidaba hasta su
primer completado (recién entonces `RecurrenceEngine` infería el siguiente desde
`completedAt`). Es la continuación lógica del bug mensual de los ciclos 17 (parser) y
18 (engine).

### Causa raíz

En `NaturalTaskParser.parseRecurrence()`, el `when` que deriva la fecha de la primera
ocurrencia tenía casos para "fecha explícita" (hoy/mañana/día de semana/día de mes) y para
"recurrencia semanal con días" y "mensual con día del mes", pero **ningún caso para
recurrencias de intervalo puro** (DAILY/WEEKLY-interval/YEARLY-interval sin día), que
caían en el `else` dejando `date=null` → `dueAt=null`.

### Solución

- Nuevo caso al final del `when`: `recurrence.frequency != NONE -> base.toLocalDate()`
  ancla la primera ocurrencia a la **fecha de captura** cuando ninguna fecha explícita se
  resolvió antes. Las fechas explícitas conservan prioridad porque se evalúan primero en
  el `when`. Para "cada día a las 8" el ancla es hoy + la hora explícita.
- `RecurrenceEngine.nextOccurrence` ya maneja `dueAt` no nulo correctamente: la próxima
  ocurrencia = `dueAt + intervalo`, coherente. Sin cambio de esquema Room.

### Archivos modificados

- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`
  (caso `recurrence.frequency != NONE -> base.toLocalDate()` en el `when` de resolución
  de fecha)
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`
  (2 tests actualizados de `assertNull(dueAt)` a fecha de captura; +3 tests nuevos:
  `parsesDailyRecurrenceWithTime`, `parsesYearlyRecurrence`,
  `parsesIntervalRecurrencePreservesExplicitDate`)

### Tests

- Red primero: tests nuevos/existentes fallaban con `dueAt=null`.
- `bash tools/run_domain_tests.sh` → **218 tests OK** (25 clases) (215 + 3 netos tras
  actualizar 2 existentes).
- `bash tools/run_domain_checks.sh` → **25 assertions OK**.
- NO VERIFICADO: gradle/lint/Android/Room (sin Android SDK).

### Hallazgos adicionales

- Las recurrencias con fecha explícita ("reunión cada lunes el 30 de julio") preservan la
  fecha explícita porque se evalúa antes que el nuevo caso de anclaje por defecto.
- Cierra el conjunto de bugs de anclaje de primera ocurrencia: mensual (ciclo 17), avance
  mensual (ciclo 18) y ahora intervalo (ciclo 19). Todas las recurrencias tienen primera
  ocurrencia visible y recordable.

### Commit / push
- fix(parser): recurrencias de intervalo ancladas a fecha de captura — push al cierre.
- HEAD final: `c3958ee`.

### Siguiente prioridad
- Auditoría funcional no-parser: What Now (tareas programadas vs. inbox), búsqueda,
  automatizaciones, contexto, GuardianEngine, LearningEngine.

---

## 2026-08-11 — OpenHands — Ciclo 20a (auditoría funcional no-parser)

- **HEAD inicial**: `cd80eb0` (origin/openhands/autonomous-ordia); tras `git pull --ff-only`.
- **Branch**: `openhands/autonomous-ordia`.

### Objetivo
- Auditoría funcional no-parser iniciada en la prioridad del ciclo 19: What Now, búsqueda,
  automatizaciones, GuardianEngine, LearningEngine, inteligencia, contexto.

### Cambios (2 bugs P1 encontrados y corregidos)

1. **SummaryEngine — subtareas inflaban los conteos del resumen diario** (commit `7127b7e`):
   `dailySummary` contaba `completedToday`/`completedWeek`/`dueToday` sobre TODAS las tareas,
   incluyendo subtareas (sin filtrar `parentTaskId == null`). Al completar un padre con N
   subtareas (auto-completadas en cascada), el resumen mostraba `completedToday = N+1` en vez
   de 1 (doble conteo). El resumen del día es una superficie de decisión del usuario; el doble
   conteo infla la percepción de progreso y distorsiona el "¿cuánto hice hoy?". Fix:
   `parentTaskId == null` en los conteos del snapshot; las subtareas siguen existiendo y
   aportando al progreso visual de su padre (vía `SubtaskRules.progress`), pero el resumen
   cuenta tareas lógicas (raíces), consistente con `WhatNowEngine.isCandidate` y
   `AutomationActionPlanner` (que ya filtraban raíces). Test nuevo `summaryCountsOnlyRootTasks`.

2. **SearchEngine — "nota X" no encontraba notas sin la palabra "nota" en el contenido**
   (commit `c1bab04`): la búsqueda por tipo `nota` filtraba resultados donde el tipo de la
   entidad era `NOTE`, pero para notas la coincidencia semántica requiere reconocer el
   **término de consulta** "nota"/"notas"/"apunte"/"apuntes" como el tipo buscado, no que el
   contenido de la nota contenga esa palabra. Las notas rara vez incluyen la palabra "nota"
   en su cuerpo, así que una consulta "nota ideas" no devolvía notas aunque existieran.
   Asimétrico con tareas/conversaciones/compromisos, que ya tenían `semanticMatches` con su
   set de términos. Fix: `NOTE_TERMS` set + `semanticMatches` fallback para notes, análogo al
   resto. Test nuevo `noteTypeFilterDoesNotRequireTheWordNotaInContent`.

### Auditoría sin hallazgos (motores sólidos)
- `RecurrenceEngine.kt`, `TaskRules.kt`, `DayPlanner.kt`, `PlannerCalendar.kt`,
  `QuietHours.kt`, `RoutineRules.kt`, `HabitRules.kt`, `GuardianEngine.kt`,
  `LearningEngine.kt`, `SubtaskRules.kt`, `WhatNowEngine.kt`, `DateRules.kt`,
  `TaskSnapshotCodec.kt`, `UniversalCaptureEngine.kt`, `FocusTimerRules.kt`,
  `ReminderSync.kt`, `TaskMutationGate.kt`, `AutomationRules.kt` (catalog + guard),
  `AutomationEngine.kt`, `AutomationUndoRules.kt`.

### Tests
- `bash tools/run_domain_tests.sh` → **222 tests OK** (25 clases).
- `bash tools/run_domain_checks.sh` → **25 assertions OK**.
- NO VERIFICADO: gradle/lint/assemble/Android/Room con DAOs reales (sin Android SDK).

### Hallazgos adicionales
- `GuardianEngine.completedToday`/`completedAll`/`activityExperience` incluyen subtareas.
  Decidido NO cambiar: el guardián mide **actividad** (cada registro completado es actividad
  real) y su XP es explícitamente "derivada de registros reales"; no es un conteo de
  "tareas lógicas" como el resumen diario. Distinto al bug de SummaryEngine. Dejado como
  hallazgo documentado, no como fix.
- `AutomationEngine` es robusto: loop guard (chainDepth>1), límites frecuencia/diario,
  snapshots de deshacer capturados ANTES de aplicar updates, `runCatching` para fallos,
  no duplica revisión de compromisos (marker check).

### Commits / push
- `7127b7e` fix(summary): subtareas inflaban los conteos del resumen diario
- `c1bab04` fix(search): "nota X" no encontraba notas sin la palabra "nota"
- Push a `openhands/autonomous-ordia`.
- **HEAD final**: `c1bab04`.

### Siguiente prioridad
- Continuar descubrimiento: context/external (ContextPrivacyFilter, app usage), conversations
  (compromisos), accesibilidad, rendimiento. O buscar oportunidades de producto (no solo
  auditoría): ¿el What Now podría agrupar/replanificar mejor? ¿la captura podría sugerir
  categoría/proyecto automáticamente con más cobertura de keywords?

---

## Sesión OpenHands — 2026-08-11 (modo continuo: supervisor persistente)

**Objetivo**: implementar continuidad real 24/7 (run termina → siguiente run en segundos, no horas).
**Entorno**: rama `openhands/autonomous-ordia`. HEAD inicial `cd80eb0` (sincronizado desde remoto,
que había avanzado 19 commits por los runs cron previos: recurrencia mensual anclada, saveRoutine
atómico @Transaction, duraciones compactas/fraccionarias, prioridad por sufijo, categoría por
etiqueta, "a primera hora", "24:00"=medianoche, etc.).

### Hallazgos críticos
- **Todos los runs cron+manual aparecían FAILED por timeout de 600 s**, pero el agente SÍ
  commiteaba+pusheaba antes de morir (el remoto avanzó 19 commits). El corte era prematuro.
- **El cron del automation service NO previene concurrencia**: dispatcha ciegamente sin comprobar
  runs activos. Se detectaron **2 runs concurrentes** (violaba MAX_CONCURRENT=1).

### Arquitectura implementada
- `tools/ordia_supervisor.py`: supervisor persistente (solo stdlib: urllib/json/fcntl). Loop:
  comprueba runs activos vía API → si ninguno, dispatcha → espera (poll ~25 s) → repite con
  cooldown ~15 s. Lock de proceso (flock), backoff exponencial tras fallos, STOP/PAUSE/RESUME
  via archivos sentinel, logs. MAX_CONCURRENT_RUNS=1 garantizado.
- `tools/ordia_supervisor.sh` + `tools/SUPERVISOR.md`: lanzador y documentación (comando único
  para el usuario en una máquina siempre encendida).
- El supervisor deshabilita el cron al arrancar (evita concurrencia) y lo rehabilita al detenerse
  (watchdog de seguridad).
- Automation `Ordía Continuous Evolution`: timeout 600→**1800 s**, cron → `*/15 * * * *`
  (watchdog degradado). **Cron deshabilitado ahora** mientras no corra el supervisor.

### Intervalos reales
- Con supervisor: **~15–40 s** entre runs.
- Sin supervisor (solo cron cada 15 min): hasta 15 min + concurrencia ocasional.

### Tests
- Smoke del supervisor: módulo carga, habla con la API (list_runs/active_runs OK), detecta
  correctamente el run RUNNING existente. NO se ejecutó el loop infinito aquí (no es entorno
  persistente). `bash tools/run_domain_tests.sh` NO ejecutado en esta sesión (sin cambios de
  dominio); último estado conocido: 218 tests OK (ciclo 19).
- Gradle/Android: NO VERIFICADO (sin Android SDK).

### Siguiente prioridad
- El usuario arranca el supervisor en su máquina (comando único en `tools/SUPERVISOR.md`).
  Tras eso, cada run continúa el desarrollo desde el HEAD de `openhands/autonomous-ordia`.

## SESIÓN 019 — Ciclo 20b (NaturalTaskParser: recurrencia semanal de varios días)

- **Fecha (UTC)**: 2026-08-11
- **HEAD inicial**: `73e4fef` (tras sincronizar con remoto: otro run commiteó "supervisor
  persistente" — no tocaba mis archivos; rebase no destructivo vía stash+pull+pop limpio)
- **Trigger**: auditoría funcional del parser con probe JVM de 20 casos reales de captura.

### Problema seleccionado

P1 — **Pérdida de datos silenciosa en rutinas semanales de varios días.** La forma natural
más común en español para una rutina semanal con varios días ("reunión los lunes y jueves")
NO se parseaba correctamente: el parser solo admitía dos días con el patrón `cada X y Z`,
pero `todos los X` y `los X` capturaban **un solo día**. El resultado era doblemente malo:

- "reunión los lunes y jueves a las 10" → `title='reunión y'` (residuo "y jueves" en el título)
- `recurrenceDays='1'` → la rutina repetía **solo los lunes**, perdiendo el jueves.

Una tarea recurrente que pierde días es una promesa rota al usuario: una cita o reunión que
no aparece en los días correctos. Es un bug de datos (persistencia/rutinas), P1.

### Causa raíz

En `NaturalTaskParser.parseRecurrence()`, `weeklyDayPatterns` eran **tres** regex separadas
con grupos de captura por día. Solo la variante `cada` tenía un grupo opcional `y Z` para un
segundo día. Las variantes `todos los X` y `los X` solo capturaban un día y, al aparecer "y
jueves" a continuación, este no casaba con ningún patrón y quedaba como residuo en el título.
Peor: la rutina resultante solo contenía el primer día.

### Solución

- Unificación de los 3 patrones en **uno solo** `dayListPattern` que captura un **conector**
  (`todos los|cada|los`) seguido de una **lista de días** separados por `,` o `y`. Los días
  se extraen con `dayNameRegex.findAll(groupValues[1])` → `distinct().sorted().toList()`.
- Menos código, más capacidad: además de arreglar "los X y Z" y "todos los X y Z", ahora
  soporta **listas con comas** ("lunes, miércoles y viernes" → `1,3,5`).
- No rompe casos existentes: "todos los viernes" → `5`; "cada lunes y jueves" → `1,4`;
  "los viernes" → `5`. Casos no-día ("cada 2 semanas", "cada día", "los lapices") no casan
  y caen a su lógica correspondiente (intervalo / DAILY / sin recurrencia).

### Archivos modificados

- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`
  (`parseRecurrence`: 3 patrones → 1 `dayListPattern` + `dayNameRegex`)
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`
  (+3 tests: `parsesLosWeekdaysWithY`, `parsesTodosLosWeekdaysWithY`, `parsesCommaDayList`)

### Tests

- Red primero: la probe mostró el bug (`title='reunión y'`, `days='1'`); tras el fix
  `title='reunión'`, `days='1,4'`.
- `bash tools/run_domain_tests.sh` → **221 tests OK** (25 clases) (218 + 3 netos).
- `bash tools/run_domain_checks.sh` → **25 assertions OK**.
- NO VERIFICADO: gradle/lint/Android/Room (sin Android SDK).

### Hallazgos adicionales

- La probe también confirmó que el resto del parser (recurrencias mensuales/anuales, duraciones,
  prioridades, recordatorios relativos, categorías) funciona correctamente en los 20 casos
  probados — incluyendo los fixes de los ciclos 17-19.
- Continúa la línea de corrección de bugs de datos en recurrencias del parser, cerrando otra
  forma de pérdida silenciosa.

### Commit / push
- fix(parser): recurrencia semanal de varios días ("los lunes y jueves") — push al cierre.

### Siguiente prioridad
- Continuar auditoría funcional: UniversalCaptureEngine, FocusClock/FocusTimerRules,
  GuardianCoach, OnboardingCompleter, PlannerCalendar, CommandPaletteCatalog,
  TaskMutationGate, QuietHoursRules. Revisar el minor issue de SummaryEngine
  (`remainingMinutesToday` sin coerce como DayPlanner).

---

## Ciclo 20 — Unidad 2 (SummaryEngine / DayPlanner — coherencia de minutos)

- Fecha: 2026-08-11
- HEAD inicial: 9302e5a (tras push de la unidad 1)
- Rama: `openhands/autonomous-ordia`

### Problema seleccionado
P2 (consistencia/correctitud de métrica de headline): la badge "Xm" de la
pantalla Today (`SummaryEngine.remainingMinutesToday`) sumaba
`task.durationMinutes` en bruto, mientras `DayPlanner` (el plan del día que el
usuario ve justo debajo) coerciona cada tarea a `coerceIn(10, 180)`. El
headline y el plan discrepaban: una tarea de 600m mostraba "600m" pendientes
pero el plan solo agenda 180m; una tarea con duración por defecto 5m contaba
5m cuando el plan la trataba como 10m.

### Causa raíz
Tres motores trataban la duración de forma distinta: `DayPlanner`
`coerceIn(10,180)`, `WhatNowEngine.isInProgressNow` `coerceAtLeast(10)`, y
`SummaryEngine` suma cruda. Números mágicos `10`/`180` duplicados.

### Solución
Fuente única de verdad `TaskRules.plannedDuration(task): Int` =
`task.durationMinutes.coerceIn(MIN_PLAN_MINUTES, MAX_PLAN_MINUTES)` con
constantes `MIN_PLAN_MINUTES=10`, `MAX_PLAN_MINUTES=180`. Usada por
`DayPlanner.build` (sustituye `coerceIn(10,180)`) y `SummaryEngine.summarize`
(sustituye `sumOf { it.durationMinutes }`). `WhatNowEngine` se deja intacto
(su `coerceAtLeast(10)` detecta "¿sigue en curso?", interés distinto; capar a
180 cambiaría comportamiento). Menos duplicación, más coherencia.

### Archivos
- `app/src/main/java/com/ordia/app/domain/TaskRules.kt` (+`plannedDuration`, constantes)
- `app/src/main/java/com/ordia/app/domain/DayPlanner.kt` (usa `TaskRules.plannedDuration`)
- `app/src/main/java/com/ordia/app/domain/SummaryEngine.kt` (usa `TaskRules.plannedDuration`)
- `app/src/test/java/com/ordia/app/domain/SummaryEngineTest.kt`
  (+2 tests: `remainingMinutesCoercesPerTaskToPlanBounds`, `remainingMinutesMatchesDayPlannerScheduledMinutes`)

### Tests
- `bash tools/run_domain_tests.sh` â **223 tests OK** (25 clases) (221 + 2 netos).
- `bash tools/run_domain_checks.sh` â **25 assertions OK**.
- NO VERIFICADO: gradle/lint/Android/Room (sin Android SDK).

### Hallazgos adicionales
- Auditoría rápida de `WhatNowEngine`: lógica de ranking correcta
  (IN_PROGRESS > en-curso-ahora > atrasada > vence-hoy > urgente > alta > inbox;
  scheduled-later se respeta con rank -1). Sin bug P1 encontrado.
- `TaskRules.nextBestTask` usa `thenByDescending { priorityScore }` mientras
  `DayPlanner`/`WhatNow` usan `compareByDescending` con `priorityScore`; coherente.

### Commit / push
- perf(ux): coherencia de minutos plan vs resumen (`TaskRules.plannedDuration`) â push al cierre.

### Siguiente prioridad
- Continuar auditoría funcional no-parser: UniversalCaptureEngine, FocusClock/FocusTimerRules,
  GuardianCoach, OnboardingCompleter, PlannerCalendar, CommandPaletteCatalog,
  TaskMutationGate, QuietHours. Buscar oportunidades de producto reales (P1 datos > P2).


---

## Ciclo 20 — Unidad 3 (Rutinas — duplicados al re-disparar tras completar)

- **Fecha (UTC)**: 2026-08-11
- **HEAD inicial**: 6f2ae9f (cierre Unidad 2)
- **Prioridad**: P1 (persistencia/duplicación de tareas)
- **Área**: Rutinas (`RoutineRules.wasRunToday`, `OrdiaViewModel.runRoutine`)

### Problema seleccionado
`RoutineRules.wasRunToday` solo contaba tareas **pendientes** creadas hoy
(`!completed && !archived && status != CANCELLED`). Al completar todas las
tareas de la rutina de hoy, `wasRunToday` devolvía `false` → un nuevo disparo de
`runRoutine` (reabrir app, worker, o tap manual) volvía a añadir los pasos →
**tareas duplicadas** en la bandeja justo cuando el usuario había sido productivo.
El guardia anti-duplicados se derrotaba a sí mismo.

### Causa raíz
El filtro exigía estado pendiente, confundiendo "rutina ejecutada hoy" con
"rutina ejecutada hoy y aún pendiente". Completar la tanda = la rutina YA se
ejecutó; no debe re-añadirse. El test `wasRunTodayFalseWhenTaskCompleted`
codificaba el bug como deseado.

### Solución
`wasRunToday` ahora devuelve true si existe al menos una tarea de la rutina
creada hoy y **no archivada ni cancelada** (la compleción ya no la excluye).
Archivado/cancelado se mantienen fuera (semántica de descarte explícito del
usuario; fuera del alcance del bug claro y ambigua). Cambio mínimo de 1 línea
en la condición + docstring que explica el porqué.

### Archivos
- `app/src/main/java/com/ordia/app/domain/RoutineRules.kt` (`wasRunToday`: quita `!task.completed`)
- `app/src/test/java/com/ordia/app/domain/RoutineRulesTest.kt`
  (corrige `wasRunTodayFalseWhenTaskCompleted` → `wasRunTodayTrueWhenCreatedTodayAndCompleted`;
  +`wasRunTodayTrueWhenTodayBatchPartiallyCompleted`)

### Tests
- `bash tools/run_domain_tests.sh` → **224 tests OK** (25 clases) (222 + 2 netos).
- `bash tools/run_domain_checks.sh` → **25 assertions OK**.
- NO VERIFICADO: gradle/Android/ViewModel real (sin Android SDK; `runRoutine` en
  `OrdiaViewModel` requiere repositorios/Room).

### Hallazgos adicionales
- `ReminderSync.triggers`: lógica correcta (futuro-only, mismo disparo que
  `ReminderScheduler.schedule`; re-sincronización no duplica notificaciones).
- `TaskSnapshotCodec`: robusto para su propósito (decode defensivo con
  `runCatching`, enums con fallback; snapshots los produce el propio codec así
  las keys siempre existen). El caso "key ausente → 0 (1970)" solo afecta JSON
  externo/malformado, no snapshots internos → no es bug activo.
- `GuardianEngine`: XP/mood/archetype derivados de registros reales (no random,
  no reglas disfrazadas de IA) → honesto, conforme a "IA honesta".
- `DayPlanner`: recupera tareas vencidas (`overdueByDate` las incluye y ordena
  primero); `scheduledMinutes`/`remainingMinutes` coherentes con `SummaryEngine`.
- `SearchEngine`: sólido; sort solo prioriza "empieza con" → oportunidad P2
  menor (vencidas/incompletas antes) registrada mentalmente, no implementada
  (evitar feature bloat; impacto bajo).

### AI_AUTONOMY actualizado
- `BACKLOG.md`: +entrada P1 Rutinas (FIXED→VERIFIED ciclo 20); nota de auditoría
  de Rutinas actualizada.
- `CURRENT_STATE.md`: +Unidad 3 en "Último trabajo realizado".

### Commit / push
- fix(rutinas): evitar duplicados al re-disparar rutina tras completar la tanda del día.
- HEAD: e5773c0.

### Siguiente prioridad
- Seguir auditoría funcional no-parser (OnboardingCompleter, PlannerCalendar,
  CommandPaletteCatalog, QuietHoursRules, SubtaskRules, HabitRules). Buscar P1
  datos/recordatorios antes que P2 cosmetic.

## Ciclo 20 — Unidad 4 (Merge de integración con remoto)

- **Fecha (UTC)**: 2026-08-11
- **HEAD inicial**: e5773c0 (Unidad 3, no pusheado por divergencia)
- **Prioridad**: Integridad de rama (anti-colisión)

### Problema seleccionado
El push de Unidad 3 (e5773c0) fue rechazado: el remoto
`openhands/autonomous-ordia` avanzó con trabajo de otra ejecución (commits
`7127b7e` summary subtasks, `c1bab04` search "nota X", `db62f6d` docs, merges
`5c21ee6`/`f8fa87d`). Mi base (6f2ae9f) estaba obsoleta.

### Causa raíz
Ejecuciones concurrentes sobre la misma rama. Mi fix tocaba
`RoutineRules.kt`/`RoutineRulesTest.kt`; el remoto tocaba
`SearchEngine.kt`/`SummaryEngine.kt` + tests. Sin conflicto de código. Solo
los docs `AI_AUTONOMY/BACKLOG.md` y `CURRENT_STATE.md` chocaron (ambos
append-only).

### Solución
`git merge origin/openhands/autonomous-ordia` (no force, no reset destructivo;
patrón de merge ya usado por el remoto en 5c21ee6/f8fa87d). Resueltos los
conflictos de docs combinando ambas entradas (sin pérdida de trabajo): la fila
de Rutinas con la nota del fix ciclo 20 + la nueva fila de auditoría de motores
no-parser; en CURRENT_STATE se preservaron las 3 unidades + la auditoría 20a.
RUN_LOG auto-mergeó limpio.

### Tests
- `bash tools/run_domain_tests.sh` → **228 tests OK** (25 clases) (224 locales
  + 4 del remoto: `summaryCountsOnlyRootTasks`,
  `noteTypeFilterDoesNotRequireTheWordNotaInContent` y asociados).
- `bash tools/run_domain_checks.sh` → **25 assertions OK**.
- NO VERIFICADO: gradle/Android/ViewModel real.

### Archivos
- `AI_AUTONOMY/BACKLOG.md`, `AI_AUTONOMY/CURRENT_STATE.md` (resolución de
  conflicto).
- Trae del remoto: `SearchEngine.kt`, `SummaryEngine.kt`, sus tests, RUN_LOG.

### Commit / push
- Merge commit 6e99822: "Merge remote-tracking branch 'origin/openhands/autonomous-ordia'
  into openhands/autonomous-ordia".
- Push OK a `openhands/autonomous-ordia` con `github_token` (f8fa87d..6e99822).
  (Nota: `GITHUB_TOKEN` rechazado; `github_token` válido.)

### Siguiente prioridad
- Auditoría no-parser restante: `OnboardingCompleter`, `PlannerCalendar`,
  `CommandPaletteCatalog`, `SubtaskRules`, `HabitRules`. Buscar P1 datos antes
  que P2.

## Ciclo 21 — Unidad 1 (NaturalTaskParser — "mañana por la tarde/noche/mañana")

- **Fecha (UTC)**: 2026-08-11
- **HEAD inicial**: 6e99822 (origin/openhands/autonomous-ordia, sincronizado tras merge ciclo 20)
- **Prioridad**: P1 — captura/persistencia (título mangulado + hora incorrecta en frase cotidiana)

### Problema seleccionado
Investigación TDD del parser (continuación del ciclo 20) reveló que la frase
natural "mañana por la tarde" (y variantes noche/mañana) se procesaba mal:
- el título quedaba como "Reunión por la tarde" (residuo "por la tarde" huérfano);
- la hora se fijaba en 09:00 (default) en vez de la hora canónica de la tarde (15:00).

"mañana por la mañana" dejaba "por la" huérfano. Frase cotidianísima en español.

### Causa raíz
`standalonePartOfDayPattern` solo reconocía los conectores "a la" y "de la"
(p.ej. "a la tarde", "de la tarde"), NO "por la". Y el mapa de horas canónicas
(`standalonePartOfDayTimes`) no incluía "mañana"/"manana". Al no casar, la
frase no se consumía como señal horaria ni se limpiaba del título; como
`parsedTime` quedaba nulo, `dueAt` usaba `LocalTime.of(9,0)` por defecto.

### Solución
Fix mínimo (un patrón, sin nueva pantalla): extender
`standalonePartOfDayPattern` a `(?:a\s+la|de\s+la|por\s+la)\s+(tarde|noche|madrugada|ma[nñ]ana)`
y añadir `mañana`/`manana` → 09:00 (AM) al mapa. Esto corrige título **y** hora
a la vez para tarde(15:00)/noche(21:00)/mañana(09:00), y como "mañana" es AM
(no está en `partOfDayPmKeys`), no introduce falsos offsets PM. Verificado que
no rompe "a las 9 de la mañana" (el meridiem explícito del `timePatterns` tiene
prioridad) ni "a primera hora de la mañana" (orden de limpieza del título
preserva el patrón `primeraHoraPattern`).

### Tests
- `bash tools/run_domain_tests.sh` → **232 tests OK** (25 clases) (228 + 4 nuevos).
- `bash tools/run_domain_checks.sh` → **25 assertions OK**.
- TDD: los 4 tests se escribieron primero y fallaron exactamente con el bug
  documentado (`expected:<Reunión[]> but was:<Reunión[ por la tarde]>`, etc.),
  luego el fix los puso en verde.
- NO VERIFICADO: gradle/lint/assemble/Android/UI/Room con DAOs reales.

### Archivos
- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt` (patrón + mapa + doc).
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt` (+4 tests).

### Commit / push
- fix(parser): reconocer "mañana por la tarde/noche/mañana" (título + hora canónica).
- HEAD final: por commit.

### Siguiente prioridad
- Continuar auditoría del parser: "pasado mañana por la tarde", "el fin de semana"
  (dueAt=null, sin soporte de fin de semana), "mañana a primera hora". Validar
  combinaciones con recurrencia. Buscar P1 datos/recordatorios en workers/backup.


---

---

## 2026-08-11 — Continuous Delivery + Self-Update + Supervisor v2 (OpenHands)

### Objetivo
Integrar Continuous Evolution → Continuous Delivery → Self-Update para que las
builds de `openhands/autonomous-ordia` lleguen al teléfono del usuario y se
instalen automáticamente.

### Cambios
- Delivery workflow `.github/workflows/openhands-delivery.yml` (nuevo): push a
  `openhands/autonomous-ordia` → tests+lint+assemble `previewAdvanced` release →
  firma con `ORDIA_UPDATE_KEYSTORE_*` → SHA-256 → GitHub Release con naming
  EXACTO que `UpdateSecurityRules` espera (`v3.0.N-code-C`,
  `Ordia-3.0-code-C.apk` + `.sha256`). Gates; concurrency cancela builds obsoletas;
  no sobrescribe releases.
- Watchdog `.github/workflows/ordia-openhands-watchdog.yml` (nuevo): cada 15 min,
  comprueba runs activos + lease gist; si supervisor caído y sin runs, rehabilita
  cron + dispatch recovery. Concurrencia 1.
- Supervisor v2 `tools/ordia_supervisor.py`: lock cross-platform (fcntl/msvcrt/
  pidfile), lease distribuido vía GitHub Gist (heartbeat ~90s, TTL 300s, expira si
  muere sin finally), STOP/PAUSE/RESUME, backoff.
- Service mode: `tools/docker-compose.yml`, `tools/ordia-supervisor.service`,
  `tools/install-supervisor.sh`.
- Observabilidad: `tools/ordia-status.py`. Keystore guía: `tools/keystore/README.md`.
- Tests supervisor: `tools/test_supervisor.py`.

### Bug crítico encontrado y corregido
`android-ci.yml` existente publicaba releases con tag `v3.0.0-build.N` y asset
`Ordia-3.0-signed.apk`, pero `UpdateSecurityRules.parseVersionCodeFromTag` espera
`v3.0.N-code-C` y `expectedApkName`=`Ordia-3.0-code-C.apk`. El auto-updater NUNCA
detectaba actualización (parseTag→null→fail). El nuevo workflow corrige el mismatch.
La rama autónoma además NO disparaba CI (solo main/jules).

### Tests
- `python3 tools/test_supervisor.py` → PASS (lock cross-platform, lease TTL, guard
  concurrencia, backoff acotado).
- Naming verificado con regex: tag/apk/sha coinciden con `UpdateSecurityRules`.
- `UpdateSecurityRulesTest` (Kotlin, ya existente) cubre el naming del workflow.
- Gradle/Android: NO VERIFICADO (sin SDK). Watchdog/supervisor loop infinito: NO
  VERIFICADO (no es entorno persistente).

### Commit / push
- `feat(infra): continuous delivery + supervisor v2 + watchdog + self-update infra`

### Siguiente prioridad
- Tras configurar los GitHub Secrets de firma, la primera push a la rama debe
  producir una Release instalable. Verificar end-to-end cuando existan secretos.
- Volver al ciclo normal de Evolution (auditar/implementar features de Ordía).


---

## Ciclo 22 — 2026-08-11 (Sesión OpenHands — auditoría parser: días relativos + hora)

- **HEAD inicial**: `379886e` (sync OK con `origin/openhands/autonomous-ordia`; base local
  estaba obsoleta tras 3 commits del run de infra → `git stash` + `pull --ff-only` + `stash pop`,
  sin force ni reset destructivo).
- **Anti-colisión**: al iniciar, la rama remota había avanzado 3 commits; mi trabajo del
  parser (no commiteado) se preservó vía stash y se re-aplicó limpio sobre el HEAD actualizado.

### Problema seleccionado (P1 — captura)
`NaturalTaskParser` perdía la hora cuando se combinaba **fecha relativa en días** con **hora
explícita**. Ej: `"Entregar informe dentro de 3 días a primera hora"` → el parser calculaba
la fecha relativa (`now + 3d`) y, al existir `parsedTime` (`09:00` de "a primera hora"), la
rama de `dueAt` tomaba el camino de `parsedTime != null` usando `today + parsedTime` en vez de
combinar la fecha relativa con la hora. Resultado: la tarea quedaba para **hoy a las 09:00**
(perdiendo los 3 días) o, según el orden de ramas, se descartaba la hora y se usaba sólo la
fecha relativa a medianoche. Ambos caminos eran incorrectos para la intención del usuario.

### Causa raíz
El conector de tiempo relativo distingue horas (`"dentro de 3 horas"`) de días
(`"dentro de 3 días"`), pero la rama final de `dueAt` no usaba esa distinción: cuando había
`parsedTime`, ignoraba `relativeDueAt` (fecha relativa) y caía a `today + parsedTime`.

### Solución (cambio mínimo, sin nueva pantalla)
- Añadido flag `relativeIsDays` (true cuando el match relativo es en días, false para horas).
- Nueva rama en `dueAt`: si `relativeDueAt != null && relativeIsDays && parsedTime != null`
  → combinar la **fecha** de `relativeDueAt` con la **hora** de `parsedTime`. Así
  `"dentro de 3 días a primera hora"` → `now+3d 09:00` (antes `09:00` hoy / hora perdida).
- Las horas relativas (`"dentro de 3 horas"`) siguen sin combinar con `parsedTime`
  (no tiene sentido: la hora relativa ya define el instante).

### Tests
- `bash tools/run_domain_tests.sh` → **235 tests PASS** (232 previos + 3 nuevos de regresión:
  `dentroDe3DiasAPrimeraHoraCombinaFechaRelativaConHora`,
  `dentroDeNDiasConHoraExplícitaCombinaFechaYHora`,
  `en3DiasALasCincoDeLaTardeCombinaFechaYHora`).
- `bash tools/run_domain_checks.sh` → smoke 25 assertions OK.
- NO VERIFICADO: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).

### Commit / push
- `fix(parser): combinar días relativos con hora explícita (dentro de 3 días a primera hora)`

### Siguiente prioridad
- Continuar auditoría del parser: "este fin de semana" / "el fin de semana" (dueAt=null,
  sin soporte de fin de semana — gap P3). Validar "mañana a primera hora" vs "a primera hora".
- Auditar lógica de sincronización de recordatorios (workers) en busca de P1 datos.

---

### Verificación CI (run 31491777388, post-fix)
- ✓ Verificar (tests + lint + assemble previewAdvanced release) — PASSED en CI
  real (Gradle clean+test+lint+assembleRelease verde en runner GitHub).
- ✓ Localizar APK sin firmar — PASSED.
- ✓ Rechazar APK debuggable antes de firmar — PASSED (gate de seguridad funciona).
- ✗ Restaurar keystore y firmar APK — falló EXACTAMENTE en
  `test -n "${KEYSTORE_B64:-}"` → "Falta el secret ORDIA_UPDATE_KEYSTORE_BASE64".
  Comportamiento esperado y seguro: sin secrets de firma, no publica nada.
- Bug gradlew exit 126 (gradlew tracked 100644) corregido: chmod +x en workflow +
  modo 100755 en git.

---

## Ciclo 23 — 2026-08-11 (Sesión OpenHands — objetivo: APK instalable + self-update)

- **HEAD inicial**: `e2e1314` (sync OK con remote).
- **HEAD final**: `23289c3` (pushed, working tree limpio).

### Problema seleccionado (P0/P1 — objetivo usuario)
El usuario quiere una APK instalable que pueda auto-actualizarse en el futuro.
Auditoría reveló DOS bugs reales que hacían que el self-update estuviera **silenciosamente
muerto** aunque toda la infraestructura existía y los tests pasaban:

1. **P0 — `SELF_UPDATE_ENABLED` overridden to false on release builds** (root cause):
   `app/build.gradle.kts` declaraba `buildConfigField("boolean","SELF_UPDATE_ENABLED","false")`
   en el buildType `release`. En AGP, un campo de buildType **pisa** al del product flavor
   (los buildTypes se aplican al final). Por tanto `previewAdvancedRelease` y
   `previewFullRelease` compilaban con `SELF_UPDATE_ENABLED=false` aunque el flavor dijera
   `true`. Resultado: una APK perfectamente construida y firmada **jamás** podría buscar ni
   instalar actualizaciones — `OrdiaUpdateManager.schedule()` y `checkDetailed()` retornan
   inmediatamente. Funcionalidad falsamente implementada (exactamente el tipo que la misión
   prohíbe). FIX: eliminar el override del buildType; cada flavor declara su valor correcto.

2. **P1 — contrato CI↔updater sin test de regresión**: ya existía el mismatch resuelto
   (tag `v3.0.N-code-C` / asset `Ordia-3.0-code-C.apk`), pero no había test que impidiera
   que el bug reaparezca silenciosamente. FIX: añadidos 2 tests contract en
   `UpdateSecurityRulesTest`:
   - `ciWorkflowNaming_isAcceptedByUpdater`: replica las fórmulas EXACTAS de
     `openhands-delivery.yml` y afirma que `UpdateSecurityRules` las acepta (parseTag,
     expectedApkName, selectExpectedApk, parseChecksum, URL confiable).
   - `ciWorkflowNaming_rejectsOldBrokenFormats`: asegura que los formatos viejos rotos
     (`v3.0.0-build.5`, `Ordia-3.0-signed.apk`, `Ordia-3.0.apk`) sigan rechazados.

### Solución
- Mínima: 1 línea eliminada en `app/build.gradle.kts` + 49 líneas de tests contract.
- No se añadió infraestructura nueva (el usuario pidió no sobre-ingeniería).

### Auditoría del updater (confirmado correcto, NO modificado)
- `OrdiaUpdateManager.checkDetailed`: consulta `releases/latest`, rechaza draft/prerelease,
  parsea versionCode del tag, rechaza `<= VERSION_CODE`, valida URL host/github.com,
  selecciona asset exacto por nombre, descarga con checksum SHA-256, valida tamaño.
- `validateDownloadedPackageLocked`: copia+hash del bytes privados, verifica SHA-256
  doble (fuente + privado), `verifyArchive` valida packageName==installed, versionCode==tag,
  `signaturesAreCompatible` (soporta rotación de claves vía signingCertificateHistory).
- `UpdateInstallActivity`: valida ANTES de instalar, `canRequestPackageInstalls()`,
  lleva a `ACTION_MANAGE_UNKNOWN_APP_SOURCES` si falta, lanza `ACTION_INSTALL_PACKAGE`
  (instalador oficial de Android). NO es instalación silenciosa (respeta Android).
- Post-actualización: datos sobreviven (misma applicationId + firma compatible → Android
  trata como upgrade, Room migra, DataStore persiste).

### Tests
- CI run **31500689793** (post-fix): `Verificar` ✓ PASSED en runner real — incluye los 2
  tests contract nuevos + clean + lint + assemblePreviewAdvancedRelease.
- Local: NO VERIFICADO gradle (sin Android SDK en agente).
- `Restaurar keystore y firmar APK` ✗ en guard `ORDIA_UPDATE_KEYSTORE_BASE64`
  (comportamiento seguro esperado — sin secrets de firma, no publica).

### Bloqueo externo real (NO resolvible por el agente)
El `GITHUB_TOKEN` del agente **no puede gestionar Actions secrets** (HTTP 403 en
`/actions/secrets/public-key` y `/gists`). El scope es vacío. Por tanto, los 4 secrets
de firma (`ORDIA_UPDATE_KEYSTORE_*`) **deben ser cargados por el usuario una vez**.
Se generó un keystore con keytool y se documentó el flujo de 1 comando en
`tools/keystore/README.md`. El keystore no se subió al repo (riesgo de seguridad).

### Commit / push
- `fix(updater): SELF_UPDATE_ENABLED was silently overridden false on release builds`

### Siguiente prioridad
- **Usuario**: cargar los 4 GitHub Secrets de firma (ver `tools/keystore/README.md`).
  Tras ello, un push vacío produce la primera APK instalable + auto-actualizable.
- Tras verificar end-to-end (instalar N, publicar N+1, comprobar que Ordía lo detecta),
  cerrar ORD-UPD como VERIFIED.


---

## Ciclo 24 — 2026-08-11 — Signed APK VERIFIED end-to-end (T4 COMPLETE)

### Objetivo
Producir la primera APK instalable firmada vía CI y verificar la cadena completa (artefacto, release, SHA-256, firma, packageName, versionCode, no-debuggable, updater compiled).

### Contexto
- Usuario cargó manualmente los 4 GitHub Secrets de firma (`ORDIA_UPDATE_KEYSTORE_*`).
- Ciclo 23 dejó la rama `openhands/autonomous-ordia` en `cd96ee5` con la fix P0 del override `SELF_UPDATE_ENABLED=false` y un commit vacío de trigger.
- CI run `31505311240` (trigger push) encolado al inicio de este ciclo.

### Cambios de código
- NINGUNO. El trabajo previo (ciclo 23) era correcto; este ciclo fue de verificación.

### Resultado CI run 31505311240 (job 93825270234) — TODOS VERDES
- ✓ Set up job / Checkout / Java 17 / Gradle 8.13 / versionCode
- ✓ Verificar (tests + lint + assemble previewAdvanced release)
- ✓ Localizar APK sin firmar
- ✓ Rechazar APK debuggable antes de firmar
- ✓ **Restaurar keystore y firmar APK** (antes fallaba por secrets ausentes → ahora pasa)
- ✓ Verificar versionCode interno de la APK
- ✓ Calcular SHA-256
- ✓ Subir APK firmado como artefacto
- ✓ Publicar GitHub Release inmutable
- ✓ Complete job

### Release publicada
- Tag: `v3.0.8-code-1300000801` (Latest, no draft, no prerelease)
- Assets:
  - `Ordia-3.0-code-1300000801.apk` (2 738 083 bytes)
  - `Ordia-3.0-code-1300000801.apk.sha256` (96 bytes)
- URL: https://github.com/wandersepulveda2013/ordia-android/releases/tag/v3.0.8-code-1300000801
- Commit: cd96ee5bed9bab37f3f0fbb39bafe3f4d24fd8b2

### Artefacto CI
- `ordia-previewadvanced-signed` (2 038 290 bytes, id 9107169334) adjunto a la run.

### Verificación independiente local (descargué la APK de la release)
- **SHA-256** = `74953d2999c9a2c29860cddf38373b5685275e5594a9601676c49151f5b05a83` — coincide EXACTO con el `.sha256` publicado y con el reportado por CI.
- **versionCode** (parseado del AndroidManifest.xml binario) = `1300000801` ✓
- **versionName** = `3.0.8-preview-advanced.1` ✓
- **applicationId / packageName** = `com.ordia.app.preview.advanced` ✓ (string idx 111)
- **debuggable** = AUSENTE en el manifest → NO debuggable ✓
- **Firma** = APK Signature Scheme v2 (`0x7109871a`) presente en el APK Signing Block ✓ + v1 JAR sig (`META-INF/ORDIA-UP.SF` + `ORDIA-UP.RSA`, alias `ordia-update`/ORDIA-UP).
- **Updater compilado en** = `UpdateInstallActivity` presente en `classes.dex` ✓
- **SELF_UPDATE_ENABLED** = true confirmado en código HEAD (líneas 64/77 true; release{} sin override — la fix P0 del ciclo 23 está activa en esta build).

### Tests
- Contract tests `UpdateSecurityRulesTest` ejecutados dentro del step `Verificar` (verde).
- Sin tests nuevos este ciclo (no hubo cambios de código).

### Estado
- **T4 VERIFIED**: CI produce APK firmada instalable + auto-actualizable reproduciblemente.
- Queda T5 (verificar auto-update N→N+1 en dispositivo real) — requiere hardware Android; el agente no puede ejecutarlo. Marcado BLOCKED-external en BACKLOG.

### Siguiente prioridad
- T5 end-to-end en teléfono: instalar `Ordia-3.0-code-1300000801.apk`, disparar una nueva release (versionCode superior), y confirmar que Ordía la detecta/descarga/instala. Fuera del alcance del agente sin dispositivo.

---

## Ciclo 25 — 2026-08-11 — Primera mejora P2 visible (simpleza UI) publicada en release

### Objetivo
Reanudar la evolución autónoma bajo la nueva MISIÓN (administrar producto integralmente,
priorizar P2 visible cuando no hay P0/P1). Reducir ruido visual en la pantalla principal.

### Contexto
- MISIÓN actualizada (commit b004771): rol de administrador autónomo permanente del producto,
  P2 = evolución real visible, BLOCKED-external no detiene evolución.
- Sin P0/P1 abiertos (T4 delivery VERIFIED; T5 BLOCKED-external dispositivo).

### Problema P2 encontrado
TodayScreen mostraba What Now DOS veces: (1) como CompactAction en la fila de 3 botones y
(2) como Card dedicada con eyebrow+razón justo debajo. Mismo destino
(onTask(whatNow.id) ?: onOpenInbox), dos entradas = ruido visual y jerarquía confusa.
El action duplicado no aportaba nada que la card rica no cumpliera mejor.

### Causa raíz
Decisión de diseño previa: la fila de 3 CompactActions incluía un atajo a What Now
redundante con la card principal. No es un bug; es ruido de jerarquía.

### Solución
- Quité el CompactAction "What Now" de la fila (1 línea). La card dedicada sigue presente
  con eyebrow "SIGUIENTE PASO" + título + razón + flecha.
- La fila pasa de 3 a 2 actions equilibrados (Revisar mensajes, Nota rápida) con más
  respiración (spacing 8dp → 10dp).
- NO toqué el reloj de recomposición cada 60s: tras análisis tiene propósito funcional
  (detectar tareas que se vuelven overdue sin interacción del usuario). Quitarlo sería P1.
- Cadena today_what_now_action quedó sin referencia; la dejé en strings (no rompe).

### Archivos
- Modificado: app/src/main/java/com/ordia/app/ui/screens/TodayScreen.kt (1 inserción, 7 eliminaciones).

### Tests
- CI run 31520002066 (job 93874503437) — **success** ✓
  - Verificar (testReleaseUnitTest + lint + assemblePreviewAdvancedRelease) ✓
  - Rechazar APK debuggable ✓ / Firmar APK ✓ / Verificar versionCode ✓ / SHA-256 ✓
  - Publicar GitHub Release ✓
- SmokeTest.firstLaunchOrTodayScreen_isVisible busca "SIGUIENTE PASO" (eyebrow de la card
  What Now, NO tocada) → sigue pasando.
- Sin SDK local: compilación validada por CI.

### Release
- Tag: v3.0.11-code-1300001101 (Latest)
- Commit: 2dfce3a
- Esta es la TERCERA release firmada consecutiva reproducible (1300000801→0901→1101).
  Confirma la cadena de entrega de la nueva MISIÓN: OBSERVAR→…→RELEASE→USUARIO.

### Hallazgos
- android-ci.yml solo corre en main/jules/autonomous-ordia (no en openhands/autonomous-ordia),
  pero openhands-delivery.yml incluye step Verificar equivalente → cobertura OK.
- Reproducibilidad del delivery confirmada: cada push produce release firmada.

### Estado
- Mejora P2 VERIFIED vía CI. Usuario verá Today más limpio en v3.0.11.

### Siguiente prioridad
- Otra mejora P2 visible: revisar CaptureScreen (captura rápida es núcleo del producto)
  o simplificar más pantallas principales. Continúa autónomamente.

---

## Ciclo 26 — 2026-08-11 — Dos mejoras P2 visibles (captura + icono) en una release

### Objetivo
Continuar evolución autónoma P2 visible. La MISIÓN exige CONTINUAR, no detenerse tras cada mejora.

### Mejoras

#### 1. CaptureScreen: aplanar Card anidada (P2 visual)
- Problema: el preview de interpretación era una `Card` DENTRO del `Card` principal de captura
  — patrón "card-in-card" que la MISIÓN prohíbe (ruido visual, doble contención).
- Solución: reemplazada por `Surface`(surfaceContainer, shape medium, sin border/elevation extra).
  Lee como sección del mismo contenedor. Sin cambio de comportamiento.
- Archivo: CaptureScreen.kt (commit 3f896db).

#### 2. TaskDetailScreen: icono deprecado (P2 backlog OPEN)
- Problema: `Icons.Outlined.InsertDriveFile` deprecado → lint warning.
- Solución: `Icons.AutoMirrored.Outlined.InsertDriveFile` (versión correcta para iconos espejables).
  Compose BOM 2026.06.00 la soporta.
- Archivo: TaskDetailScreen.kt (commit 837ca63). Backlog P2 "deprecated InsertDriveFile icon" → FIXED.

### Tests
- CI run 31522884362 (job 93884233254) — **success** ✓
  - Verificar (tests+lint+assemble) ✓ — ahora con 1 lint warning menos (icono corregido).
  - Firma ✓ / versionCode ✓ / SHA-256 ✓ / Release ✓.
- Run 31522754097 (3f896db solo) fue cancelada por concurrency (cancel-in-progress) → la release
  final 837ca63 contiene ambos cambios. Eficiente: no se publican releases intermedias obsoletas.

### Release
- Tag: v3.0.14-code-1300001401 (Latest). 4ta release firmada consecutiva.

### Auditoría (sin cambios de estado)
- NoteBlockCodec.decode ya robusto (fallback graceful, descarta solo elementos corruptos) — OK.
- NaturalTaskParser soporta sábado/domingo, "esta tarde/noche", "dentro de", "próximo" — backlog P3
  "weekend parser support" ya cubierto → marcar NOT_APPLICABLE.
- OnboardingScreen ya tiene verticalScroll + widthIn(max=520) + systemBarsPadding — caber OK.
- PlannerScreen / TasksScreen: sin card-in-card — limpios.

### Siguiente
- Continuar: próxima mejora P2 visible. No detenerse.


---

## Ciclo 27 — 2026-08-11 — Limpieza deuda técnica + accesibilidad (3 commits, 3 releases)

### Objetivo
Continuar evolución P2/P3. La MISIÓN exige ciclo interminable: no detenerse.

### Cambios (3 commits → 3 releases firmadas consecutivas)

#### 1. Eliminar string muerta today_what_now_action (commit 3d4c780 → release v3.0.15)
- Quedó sin referenciar tras el ciclo 25 (removí el action What Now duplicado).
- Sin dependencias (no hay values-es ni getIdentifier dinámico).

#### 2. Eliminar 46 strings huérfanas intel_* (commit e714f57 → release v3.0.16)
- Feature de modelo local/Gemma (intel_mode_local_*, intel_download_*, intel_state_*) se simplificó
  fuera de IntelligenceScreen pero dejó 46 cadenas definidas y sin referenciar.
- Las 12 intel_* todavía usadas por IntelligenceScreen se conservaron.
- Script Python removió líneas; XML validado; verificado que las 12 usadas siguen presentes.

#### 3. Accesibilidad TodayScreen planner IconButton (commit bd82841 → release v3.0.17)
- IconButton(onOpenPlanner) con Icon(ArrowForward, null) → null hacía que TalkBack anunciara
  control sin etiqueta.
- Añadida string today_open_planner_cd y usada como contentDescription.
- Auditoría completa de IconButtons accionables solos: todos los demás ya tienen contentDescription.

### Tests
- CI runs: 31524634470 (e714f57) success, 31525626150 (bd82841) success.
- Verificar (tests+lint+assemble) OK en ambos. Firma OK / release OK.
- 6 releases firmadas consecutivas funcionando (v3.0.12 → v3.0.17).

### Auditoría (sin cambios de estado — ya correcto)
- BackupManager.restore: valida SHA-256, versión, secciones, fecha, faltantes; IllegalArgumentException
  con mensajes claros; sin catch vacío. Backlog P2 manifiesto corrupto → REVISADO (no requiere fix).
- i18n: sin texto hardcodeado en pantallas (todo via stringResource). Backlog P2 → REVISADO.
- AppComponents/VirtualGuardian (trabajo de Jules): quadraticBezierTo correcto (no deprecado),
  LocalClipboardManager correcto (API moderna), iconos null solo en Buttons con texto (correcto).
- NoteBlockCodec.decode robusto (fallback graceful). NaturalTaskParser soporta días weekend individuales.
- OnboardingScreen tiene scroll. Planner/Tasks/Conversations/Intelligence sin card-in-card.

### Hallazgos para próximas ejecuciones
- Quedan ~17 strings sin uso adicionales (common_yes/no, ime_service_label, etc.) — P3 bajo.
- Frase compuesta "este fin de semana" no soportada en parser (P3, no pérdida datos).
- 6-variant compile check sigue OPEN (requiere CI/env Android dedicado).

### Siguiente
- Continuar ciclo interminable. Próxima mejora P2/P3 visible.

---

## Ciclo 28 — Accesibilidad (roles semánticos), strings 100% limpios, UX adjuntos — 2026-08-11T21:Z

### Objetivo
Continuar el ciclo interminable de mejora continua P2/P3. Cerrar la auditoría de
`Modifier.clickable` sin `Role` y consolidar deuda técnica de strings/UX.

### Cambios (5 commits → 5 releases firmadas consecutivas)

#### 1. Role.Button en OrdiaListItem (commit 109c14d → release v3.0.19)
- `AppComponents.kt:260` OrdiaListItem row clickable sin role → TalkBack sin hint de botón.
- Añadido `role = Role.Button`. Sin cambio visual.

#### 2. Roles semánticos en clickables restantes (commit b39fd73 → release v3.0.20)
- CaptureScreen:378 capture card → Role.Button.
- PlannerScreen:583 day cell → Role.Button.
- PlannerScreen:829 conflict toggle row (con Checkbox) → Role.Switch.
- AppComponents:147 EmptyState action text → Role.Button.
- Verificado: 0 `.clickable(` sin role en `app/src/main`. Auditoría completada.

#### 3. 10 strings huérfanas eliminadas (commit a71cf42 → release v3.0.21)
- common_yes/no, common_downloaded/not_downloaded (sin diálogos de confirmación que las usen).
- more_notes_desc/more_planner_desc (Notes/Planner no aparecen en MoreScreen).
- search_empty_prompt_*/search_field_label/search_header_subtitle (reemplazadas por
  search_palette_*/search_no_results_* en SearchScreen).
- Verificado cero `getIdentifier` dinámicos; manifests de variantes (ime_service_label,
  notification_listener_label, shortcut_*) conservados. Script recursivo sobre app/src/**:
  defined=1024 == referenced=1024, unused=0. **Tabla de strings 100% limpia.**

#### 4. Toast al no poder abrir adjunto en NoteEditor (commit cf1e4df → release v3.0.22)
- `NoteEditorScreen` tragaba `startActivity` fallido en runCatching silencioso (tap → nada).
- TaskDetailScreen ya mostraba toast. Alineado NoteEditor con patrón + string
  `note_editor_open_attachment_failed`. Consistencia UX.

#### 5. Log al fallar permiso persistente de adjunto en Capture (commit 072c252 → release v3.0.23)
- `CaptureScreen` takePersistableUriPermission en runCatching silencioso; sin traza si fallaba.
- Añadido `Log.w` en onFailure. Documentado P1 subyacente en BACKLOG (adjuntos guardan URI
  externo, no contenido copiado → solución robusta requiere copiar a filesDir + migración).

### Tests
- CI runs: 109c14d cancelled (superseded por b39fd73), b39fd73 success, a71cf42 success,
  cf1e4df success, 072c252 success. Verificar (tests+lint+assemble) OK en todos los pushados.
- Firma OK / release OK en cada push (workflow publica en cada push a la branch).
- 12 releases firmadas consecutivas funcionando (v3.0.12 → v3.0.23).

### Auditorías (sin hallazgos P0/P1 nuevos)
- **Componentes exportados (Manifest)**: MainActivity (MAIN/LAUNCHER, SEND */*, PROCESS_TEXT —
  legítimos para captura compartida), OrdiaCaptureTileService (BIND_QUICK_SETTINGS_TILE),
  ReminderResyncReceiver (TIMEZONE/TIME_SET/DATE_CHANGED del sistema), OrdiaWidgetProvider
  (APPWIDGET_UPDATE). previewAdvanced/Full: NotificationListener (BIND_NOTIFICATION_LISTENER_SERVICE),
  IME (BIND_INPUT_METHOD), FileProvider exported=false. Todos protegidos con permisos BIND del
  sistema o exported=false. Sin problema de seguridad.
- **startActivity externos**: IntelligenceActionExecutor (executeAppointment/executeCall) usan
  try/catch con fallback a executeTask — robusto. Navigation.launchCapture abre actividad propia.
  ConversationsScreen abre Settings del sistema. Sin issue.
- **catch vacíos**: 0 encontrados. runCatching con manejo o fallback en los críticos.

### Hallazgos para próximas ejecuciones
- P1 OPEN: adjuntos guardan URI externo (BACKLOG ciclo 28) — requiere sesión dedicada para
  migración a contenido interno.
- P2 OPEN: 6-variant compile check sigue pendiente (requiere env Android/CI dedicado).
- P3 OPEN: pulido visual de pantallas renovadas del workspace.

### Siguiente
- Continuar ciclo interminable. Próxima mejora P2/P3 visible (no detenerse).

---

## Ciclo 28 (cont.) — NaturalTaskParser: "fin de semana" → próximo sábado — 2026-08-11T21:4Z

### Objetivo
Cerrar el candidato P3 listado en CURRENT_STATE ("este fin de semana" no soportado en el parser).

### Cambio (1 commit)
- `5b6f714` — `NaturalTaskParser` ahora reconoce "este/el/próximo fin de semana" y "fin de semana"
  suelto → próximo sábado (hora canónica 09:00, consistente con días sueltos). Antes la frase
  quedaba sin fecha (INBOX) y "fin de semana" como residuo en el título. Hora explícita respetada
  ("el fin de semana a las 20:00" → sábado 20:00). TDD: +3 tests.

### Tests — VERIFICADO localmente
- `./gradlew :app:testPreviewSafeDebugUnitTest` (con Android SDK instalado en el agente):
  **462 tests, 0 failures, 0 errors, 0 skipped** en 48 clases.
- CI `5b6f714` success (Verificar). CI `118f020` (docs previo) cancelled por concurrencia (no fallo).

### Nota
- Android SDK (platform-tools, platforms;android-36, build-tools 35/36) se instaló en `/tmp/android-sdk`
  para poder correr los tests unitarios JVM localmente en esta sesión. `local.properties` (gitignored)
  apunta ahí. No persiste entre sesiones; la próxima ejecución que quiera correr gradle debe recrearlo.

### Siguiente
- Continuar ciclo interminable P2/P3. Candidatos: derivedStateOf/keys en LazyColumns grandes,
  BackHandler en pantallas anidadas, contraste onSurfaceVariant.

---

## Ciclo 29 — NaturalTaskParser: semanas/meses + ayer/anteayer + números escritos — 2026-08-13T13:0Z

### Objetivo
Candidato P1 del parser: "en un mes"/"en una semana" y fechas pasadas ayer/anteayer no se
parseaban → tareas olvidadas (sin recordatorio, invisibles en planificador).

### Sincronización
- `git fetch origin openhands/autonomous-ordia`; HEAD local == remoto (`e1014d5`). Sin divergencia.
- Entorno: JVM puro (sin Android SDK). `bash tools/run_domain_tests.sh` = 249 tests.

### Cambio (1 commit)
- `17f058d` — `fix(parser): parse 'en un mes/semana' + ayer/anteayer + written numbers 13-30`
  - `relativePattern` añade unidades `semanas`/`meses`; bug `meses?`→`mes(?:es)?` (singular).
  - `parseWrittenNumber` extendido: trece–veinte, veintiuno, treinta.
  - `relativeDueAt`: semanas ×7 días, meses ×30 días.
  - `date` when: añade ayer (-1) / anteayer (-2) como fechas pasadas explícitas.
  - Limpieza de título elimina tokens ayer/anteayer.
  - +11 tests.

### Tests — VERIFICADO localmente (JVM)
- `bash tools/run_domain_tests.sh` = **249 tests PASS** (238 base + 11 nuevos), 25 clases.
- Probe manual JVM: "en un mes"→2026-08-28, "en 1 mes"→2026-08-28, "dentro de un mes"→2026-08-28,
  "en una semana"→2026-08-05, "en 2 meses"→2026-09-27, "ayer a las 4 de la tarde"→ayer 16:00.
- NO VERIFICADO: gradle/lint/assemble/Android (sin Android SDK en este entorno). CI remoto
  ejecutará `Verificar` (tests+lint+assemble) en el push.

### Hallazgos para próximas ejecuciones
- Parser: "en un año"/"el año que viene" no se parsean (relativePattern sin unidad años).
- Parser: "la semana que viene"/"el mes que viene" no se parsean. "próximos días" tampoco.
- P1 OPEN: adjuntos guardan URI externo (BACKLOG) — requiere sesión dedicada.

### Commits
- `17f058d` (código + tests)
- docs(autonomy) pendiente: actualización de AI_AUTONOMY (este commit).

### Siguiente
- Continuar ciclo interminable. Candidatos parser: años, "semana/mes que viene", "próximos días".
- O P1 adjuntos URI externo si hay capacidad para sesión dedicada.

---

## Ciclo 30 — NaturalTaskParser: años + período próximo ("que viene"/"próximo X") — 2026-08-13T13:2Z

### Objetivo
Continuidad del ciclo 29: las formas más comunes de plazos largos y períodos siguientes
no se parseaban → tareas olvidadas. Probe JVM (ciclo 29 ya señaló): "en un año", "el año
que viene", "la semana que viene", "el mes que viene", "el próximo mes" → `dueAt=null`
y la frase quedaba como residuo en el título (sin recordatorio, invisible en
planificador/What Now).

### Sincronización
- `git fetch origin openhands/autonomous-ordia`; HEAD local == remoto (`acef2f7`). Sin divergencia.
- Entorno: JVM puro (sin Android SDK). `bash tools/run_domain_tests.sh` = 249 tests (ciclo 29).

### Cambio (1 commit)
- `feat(parser): parse 'en N años' + 'semana/mes/año que viene' + 'próximo X'`
  - `relativePattern` añade unidad `años`; `relativeDueAt` añade multiplicador 365 días.
  - Nuevo `nextPeriodPattern`: "el/la (semana|mes|año) que viene", "próximo/próxima
    (semana|mes|año)", "(semana|mes|año) próximo/próxima" → +1 período (semana=+7d,
    mes=+30d, año=+365d), con prioridad relativePattern > nextPeriod.
  - `effectiveRelativeDueAt` = relativeDueAt ?: nextPeriodDueAt; ambos son "días" (no
    min/hora) para combinarse con hora explícita ("el mes que viene a las 10").
  - Limpieza de título elimina la frase de período (no queda residuo "que viene").
  - +14 tests (años: 4; período próximo: 7; hora explícita: 3 incluidas en esos).

### Tests — VERIFICADO localmente (JVM)
- `bash tools/run_domain_tests.sh` = **259 tests PASS** (249 base + 10 nuevos efectivos
  de parser; +tests de título/hora), 25 clases. `tools/run_domain_checks.sh` = 25 assertions OK.
- Probe JVM (ahora): "en un año"→+365d, "en 2 años"→+730d, "dentro de un año"→+365d,
  "el año que viene"→+365d, "la semana que viene"→+7d, "el mes que viene"→+30d,
  "el próximo mes"→+30d, "la próxima semana"→+7d. Todos con título limpio.
- Probe anti-falsos-positivos: "la semana pasada", "el mes pasado", "el año pasado",
  "próximo a la puerta", "viene a visitarme" → todos `dueAt=null` (sin colisión).
- NO VERIFICADO: gradle/lint/assemble/Android (sin Android SDK). CI remoto ejecuta `Verificar`.

### Hallazgos para próximas ejecuciones
- Parser: "próximos días"/"en los próximos días" (vago, sin semana/mes/año) sigue sin
  parsearse — forma intencionalmente ambigua; decidir si merece un default (¿+3d?).
- Parser: "próximo trimestre"/"el trimestre que viene" no soportado.
- P1 OPEN: adjuntos guardan URI externo (BACKLOG) — requiere sesión dedicada.

### Commits
- `feat(parser): parse 'en N años' + 'semana/mes/año que viene' + 'próximo X'` (código + tests)
- docs(autonomy) pendiente: actualización de AI_AUTONOMY (este commit).

### Siguiente
- Continuar ciclo interminable. Candidatos parser: "próximos días" (decidir default),
  "próximo trimestre"; P1 adjuntos URI externo si hay sesión dedicada.

## Ciclo 31 — NaturalTaskParser: fix "fin de semana que viene" (regresión de período próximo) — 2026-08-13T13:4Z

### Objetivo
El `nextPeriodPattern` añadido en el ciclo 30 ("semana/mes/año que viene") coincidía
con la subcadena "semana que viene" dentro de "fin de semana que viene". Como se procesa
antes que `weekendPattern`, consumía "semana que viene" y dejaba el residuo **«fin de»**
en el título, **además** de programar la tarea +7d (período "semana") en lugar del
próximo sábado. Frase cotidísima → tarea mal fechada + título corrupto (P1).

### Sincronización
- HEAD inicial: `e467b23` (ciclo 30). `git pull --ff-only` limpio, sin divergencia.
- Entorno: JVM puro (sin Android SDK).

### Cambio
- `NaturalTaskParser.parse`: detección temprana de `weekendPattern` y borrado de `working`
  **antes** del procesamiento de `nextPeriodPattern`. El match se conserva en
  `weekendEarlyMatch` y se reutiliza como `weekendMatch` en la resolución de fecha (mismo
  valor que antes; la fecha del fin de semana se calcula igual).
- Limpieza de título: añadido borrado de residuo huérfano `\bque\s+viene\b` (queda cuando
  la fecha asociada —fin de semana o día de la semana— se consume pero la frase
  modificadora no, p.ej. "el viernes que viene" tras otros reordenamientos).
- Test de regresión: `finDeSemanaQueVieneProgramaProximoSabadoYLimpiaTitulo`
  ("Viaje fin de semana que viene" → título "Viaje", due=próximo sábado 2026-08-01).

### Tests — VERIFICADO localmente (JVM)
- Antes del fix (probe): "fin de semana que viene" → title=[fin de], due=2026-08-05 (+7d, incorrecto).
- Después del fix: title=[Viaje] (limpio), due=2026-08-01 (próximo sábado, correcto).
- `bash tools/run_domain_tests.sh` = **260 tests PASS** (259 + 1 nuevo), 25 clases.
- NO VERIFICADO: gradle/lint/assemble/Android (sin Android SDK). CI remoto ejecuta `Verificar`.

### Hallazgos para próximas ejecuciones
- Parser: "antier"/"antier" (variantes de "anteayer") no reconocidas (due=null) — pendiente.
- Parser: "próximos días"/"en los próximos días" (vago) sigue sin parsearse.
- P1 OPEN: adjuntos guardan URI externo (BACKLOG) — requiere sesión dedicada.

### Commits
- `fix(parser): 'fin de semana que viene' ya no deja residuo ni fecha errónea` (código + test)
- docs(autonomy): registro ciclo 31

### Siguiente
- Continuar ciclo interminable. Candidatos parser: "antier"; "próximos días" (decidir default);
  "próximo trimestre". P1 adjuntos URI externo si hay sesión dedicada.


## Ciclo 32 — NaturalTaskParser: "próximos días" (+3d) — 2026-08-13T13:3Z

### Objetivo
Resolver el hallazgo pendiente de los ciclos 30/31: "próximos días" (con o sin "en
los/el/las") no se parseaba → `dueAt=null` → tarea olvidada. Es la forma cotidiana y
deliberadamente vaga de "dentro de poco". Decisión de producto: default honesto +3 días
(ni IA ni azar; coincide con el sentido común de "pocos días").

### Sincronización
- HEAD inicial: `3540808` (ciclo 31, remoto).
- **STALE_RUN evitado**: existía un commit local no-pushado `04a21e8` que duplicaba los
  features del remoto (años + "semana/mes/año que viene" + "próximo X", ya en `e467b23`) y
  SOLO añadía "próximos días". Push habría sido non-ff. Se descartó el commit duplicado
  (`git reset --soft origin/openhands/autonomous-ordia` + `git checkout -- .`) y se
  reconstruyó SOLO "próximos días" sobre el HEAD remoto limpio `3540808`. Sin sobrescribir
  trabajo válido ni history compartido; sin force/reset destructivo.
- Entorno: JVM puro (sin Android SDK).

### Cambio (mínimo, reutiliza infraestructura existente)
- `NaturalTaskParser.nextPeriodPattern`: añadida alternativa al final del regex
  `(?:en\s+(?:los|el|las)?\s+)?pr[oó]ximos?\s+d[ií]as\b` (prefijo "en los/el/las" opcional).
- `nextPeriodDueAt`: añadida rama `else -> 3L` ("próximos días" no contiene semana/mes/año,
  cae al else → +3d).
- Docstring del patrón actualizado.
- Beneficios heredados (sin código nuevo): combina con hora explícita
  ("en los próximos días a las 10" → fecha +3d a las 10:00) y se elimina del título sin residuo,
  porque reutiliza el mismo `nextPeriodMatch`/`effectiveRelativeDueAt`/`relativeIsDays`.

### Tests — VERIFICADO localmente (JVM)
- Probe antes/después (now=2026-07-29): "próximos días" → antes due=null; después due=2026-08-01.
- "en los próximos días a las 10" → due=2026-08-01 time=10:00 (combina con hora). ✓
- Regresiones OK: "el mes que viene a las 8 de la mañana" → 2026-08-28 08:00 (no afectado);
  "el año que viene" → 2027-07-29; "la semana que viene" → 2026-08-05; "cada lunes" → OK.
- `bash tools/run_domain_tests.sh` = **263 tests PASS** (260 base + 3 nuevos), 25 clases.
- `bash tools/run_domain_checks.sh` = smoke 25 assertions OK.
- NO VERIFICADO: gradle/lint/assemble/Android (sin Android SDK). CI remoto ejecuta `Verificar`.

### Hallazgos para próximas ejecuciones
- Parser: "antier"/"antier" (variantes de "anteayer") no reconocidas (due=null) — pendiente.
- Parser: "próximo trimestre"/"el trimestre que viene" no soportado.
- P1 OPEN: adjuntos guardan URI externo (BACKLOG) — requiere sesión dedicada.

### Commits
- `feat(parser): parse 'próximos días' (+3d)` (código + 3 tests)
- docs(autonomy): registro ciclo 32

### Siguiente
- Continuar ciclo interminable. Candidatos parser: "antier"; "próximo trimestre".
  P1 adjuntos URI externo si hay sesión dedicada.

## Ciclo 32 (cont.) — NaturalTaskParser: "antier" (variante de "anteayer") — 2026-08-13T13:5Z

### Objetivo
Resolver el hallazgo pendiente de los ciclos 31/32: "antier" (variante coloquial
hispanoamericana de "anteayer", MX/CA/parts SA) no se parseaba → `dueAt=null` →
tarea vencida olvidada. Es la misma fecha que "anteayer" (hace dos días). Mejora
P1 atómica (evitar olvidos de fechas pasadas), continuación natural del soporte
de "anteayer"/"ayer" del ciclo 29.

### Sincronización
- HEAD inicial: `d134f2a` (ciclo 32, remoto, tras push de "próximos días").
- `git fetch origin openhands/autonomous-ordia`; remoto == HEAD local. Sin divergencia,
  sin colisión con otra ejecución.
- Entorno: JVM puro (sin Android SDK).

### Cambio (mínimo, TDD)
- `NaturalTaskParser.parse`: la rama `anteayer` del `when` de fecha ahora es
  `Regex("""(?i)\banteayer\b|\bantier\b""").containsMatchIn(working)` → misma fecha
  (`base.toLocalDate().minusDays(2)`). "antier" cae en la misma rama que "anteayer".
- Limpieza de título: añadido `\bantier\b` al regex que elimina tokens de día relativo
  (junto a `anteayer`/`ayer`/`hoy`/`mañana`/`pasado mañana`). No queda residuo en el título.
- Comentario del patrón actualizado ("antier" = variante coloquial hispanoamericana).
- Beneficios heredados (sin código nuevo): combina con hora explícita
  ("antier a las 4 de la tarde" → fecha -2d a las 16:00), igual que "ayer a las 4".

### Tests — VERIFICADO localmente (JVM)
- TDD red→green: test `antierParsesDueAtTwoDaysAgo` falló antes del fix
  (title=[Enviar correo antier], dueAt=null → NPE en `result.dueAt!!`); PASS tras fix.
- Probe (now=2026-07-29): "antier" → due=2026-07-27, título "Enviar correo" (limpio). ✓
- "antier a las 4 de la tarde" → due=2026-07-27 time=16:00 (combina con hora, no a HOY). ✓
- Regresiones OK: "anteayer" sigue → -2d; "ayer" → -1d; "próximos días" → +3d (no afectado).
- `bash tools/run_domain_tests.sh` = **265 tests PASS** (263 + 2 nuevos), 25 clases.
- `bash tools/run_domain_checks.sh` = smoke 25 assertions OK.
- NO VERIFICADO: gradle/lint/assemble/Android (sin Android SDK). CI remoto ejecuta `Verificar`.

### Hallazgos para próximas ejecuciones
- Parser: "próximo trimestre"/"el trimestre que viene" no soportado.
- P1 OPEN: adjuntos guardan URI externo (BACKLOG) — requiere sesión dedicada.

### Commits
- `feat(parser): parse 'antier' (variante de 'anteayer')` (código + 2 tests) — pendiente push
- docs(autonomy): registro ciclo 32 (cont.) — pendiente push

### Siguiente
- Continuar ciclo interminable. Candidato parser: "próximo trimestre".
  P1 adjuntos URI externo si hay sesión dedicada.

## Ciclo 32 (cont.2) — NaturalTaskParser: “próximo trimestre” / “trimestre que viene” (+90d) — 2026-08-13T13:31Z

### Objetivo
Resolver el hallazgo pendiente desde ciclos 30/31/32: “próximo trimestre” /
“el trimestre que viene” / “el próximo trimestre” no se parseaban → `dueAt=null` →
tarea olvidada (sin recordatorio ni visibilidad en planificador/What Now). Plazo
largo cotidiano (impuestos trimestrales, revisiones, informes).

### Estado del repo
- HEAD inicial: `b8b3761` (ciclo 32 cont., remoto, tras push de “antier”).
- `git fetch origin openhands/autonomous-ordia` → local == remoto (sin divergencia).
- Remote con token `https://x-access-token:${github_token}@...`.

### Cambios
- `NaturalTaskParser.nextPeriodPattern`: añadida `trimestre` como unidad en ambas
  ramas del patrón (“trimestre que viene” / “próximo trimestre”).
- `NaturalTaskParser.nextPeriodDueAt`: añadida rama `trimestre` → `90L` (3 meses ×
  30d, consistente con `mes que viene` = +30d). **Se comprueba ANTES que `mes`**
  porque la cadena “trimestre” contiene la subcadena “mes” (“tri**mes**tre”); si
  `mes` fuera primero ganaría erróneamente (+30d en vez de +90d).
- Comentario del patrón consolidado/limpiado (eliminado bloque duplicado huérfano).

### TDD
- 3 tests nuevos (`proximoTrimestreParsesDueAt`,
  `trimestreQueVieneParsesDueAt`, `proximoTrimestreRespetaHoraExplicita`).
- RED confirmado antes del fix: los 3 fallaron — dueAt=null → el título retenía el
  residuo (“Auditoría [próximo trimestre]”, “Cerrar informe [ el trimestre que viene]”).
- GREEN tras fix.

### Evidencia
- TDD red→green: 3 tests fallaron antes del fix (ComparisonFailure: título con
  residuo “próximo trimestre”/“el trimestre que viene”); PASS tras fix.
- now=2026-07-29T12:00 (America/Santo_Domingo). +90d = 2026-10-27. ✓
- “próximo trimestre a las 10” → fecha +90d time=10:00 (combina con hora). ✓
- Regresiones OK: “semana/mes/año que viene” y “próximos días” no afectados
  (cubiertos por suite existente).
- `bash tools/run_domain_tests.sh` = **268 tests PASS** (265 + 3 nuevos), 25 clases.
- `bash tools/run_domain_checks.sh` = smoke 25 assertions OK.
- NO VERIFICADO: gradle/lint/assemble/Android (sin Android SDK). CI remoto ejecuta `Verificar`.

### Hallazgos para próximas ejecuciones
- Parser: candidatos restantes — “próximo bimestre/semestre” (poco frecuente;
  evaluar si merece la pena), “a finales de mes”, “a mediados de mes”,
  “esta semana” (vs “la semana que viene”), “fin de mes”.
- P1 OPEN: adjuntos guardan URI externo (BACKLOG) — requiere sesión dedicada.

### Commits
- `feat(parser): parse 'próximo trimestre' / 'trimestre que viene' (+90d)` (código + 3 tests)
  → `44f2e9b` (pushado a remoto, fast-forward `b8b3761..44f2e9b`).
- docs(autonomy): registro ciclo 32 (cont.2) — este commit.

### Siguiente
- Continuar ciclo interminable. Candidatos parser: “a finales/mediados de mes”,
  “esta semana”, “fin de mes”. P1 adjuntos URI externo si hay sesión dedicada.

## Ciclo 32 (cont.3) — NaturalTaskParser: “fin de mes” / “a finales de mes” / “mediados de mes” — 2026-08-13T13:31Z

### Objetivo
Resolver hallazgo pendiente: el parser no entendía límites mensuales (“fin de mes”,
“a finales de mes”, “mediados de mes”) — vencimientos cotidianos (alquiler, tarjeta,
servicios). `dueAt=null` → vencimiento olvidado (sin recordatorio ni visibilidad en
planificador/What Now). La aritmética `now + millis` no calculaba “último día del mes” ni “día 15”.

### Estado del repo
- HEAD inicial: `b24758e` (ciclo 32 cont.2, remoto, tras push de “trimestre”).
- `git fetch origin openhands/autonomous-ordia` → local == remoto (sin divergencia, no STALE_RUN).
- Remote con token `https://x-access-token:${github_token}@...`.

### Cambios
- Nuevos patrones `endOfMonthPattern` (`fin(?:ales|es)? (de|del) mes`, cubre
  fin/fines/finales) y `midOfMonthPattern` (`mediados? (de|del) mes`).
- Lógica de resolución: `LocalDate.withDayOfMonth(lengthOfMonth())` (fin de mes) y
  `withDayOfMonth(15)` (mediados), rodando al mes siguiente si hoy ya es la fecha objetivo.
  Fecha absoluta vía `DateRules.toEpochMillis(target, LocalTime.of(9,0), zone)`.
- **Detección y borrado ANTES del período próximo**: “fin de mes” contiene la subcadena
  “mes” → colisionaría con “mes que viene” (residuo + fecha +30d errónea). Bloque early-detect
  junto a `weekendEarlyMatch`.
- Integrados en `effectiveRelativeDueAt` (prioridad: relativa explícita > límite de mes >
  período próximo) y `relativeIsDays=true` para combinar con hora explícita (“fin de mes a las 18”).

### TDD
- 7 tests nuevos: `finDeMesParsesDueAtUltimoDiaMesActual`, `aFinalesDeMesParsesDueAt`,
  `finDeMesRespetaHoraExplicita`, `finDeMesRuedaAProximoMesSiHoyEsUltimoDia`,
  `mediadosDeMesParsesDueAtDia15ProximoMes`, `mediadosDeMesResuelveDia15MesActualSiAunNoLlega`,
  `finDeMesNoColisionaConPeriodoProximo`.
- Red phase confirmado (7 fallaban). Green tras fix del regex (`fin(?:es)?` → `fin(?:ales|es)?`
  para cubrir “finales”): “a finales de mes”/“a finales del mes” antes no hacían match.

### Evidencia
- now=2026-07-29T12:00 (julio=31 días). “fin de mes” → 2026-07-31 09:00. “mediados de mes”
  (día 29 > 15) → 2026-08-15 09:00. “fin de mes a las 18” → 2026-07-31 18:00 (combina hora).
- “Renovar suscripción a finales del mes que viene” → title limpio + due=fin de mes (no +30d).
- Regresiones OK: “semana/mes/año que viene”, “próximos días”, “trimestre” no afectados (suite existente).
- `bash tools/run_domain_tests.sh` = **275 tests PASS** (268 + 7 nuevos), 25 clases.
- `bash tools/run_domain_checks.sh` = smoke 25 assertions OK.
- NO VERIFICADO: gradle/lint/assemble/Android (sin Android SDK). CI remoto ejecuta `Verificar`.

### Hallazgos para próximas ejecuciones
- Parser: candidatos restantes — “esta semana” (vs “la semana que viene”),
  “próximo bimestre/semestre” (poco frecuente; evaluar), “principios de mes” (día 1).
- P1 OPEN: adjuntos guardan URI externo (BACKLOG) — requiere sesión dedicada.

### Commits
- `feat(parser): parse 'fin de mes' / 'mediados de mes' / 'a finales de mes'` (código + 7 tests).
- docs(autonomy): registro ciclo 32 (cont.3) — este commit.

### Siguiente
- Continuar ciclo interminable. Candidatos parser: “esta semana”, “principios de mes”.
  P1 adjuntos URI externo si hay sesión dedicada.


---

## 2026-08-13 — Ciclo 32 (cont.4): adjuntos copiados a almacenamiento interno (P1 persistencia)

**HEAD inicial**: `bdd3dc0` (docs ciclo 32 cont.3 — fin de mes / mediados de mes)
**Branch**: `openhands/autonomous-ordia`

### Problema seleccionado (P1 — datos sagrados / persistencia)
Adjuntos (captura, note editor, task detail) guardaban el **URI externo** en `AttachmentEntity.uri`
sin copiar el contenido. El acceso dependía de `takePersistableUriPermission` (en `runCatching`
silencioso). Si el permiso falla/caduca/revoca (limpieza del sistema, app reinstalada, URI de
`MediaStore` que cambia tras reboot), el adjunto queda **inaccesible** tras reinicio y
`startActivity(ACTION_VIEW)` falla con toast misleading ("Ninguna app pudo abrir…"). Riesgo real
de pérdida percibida de datos. Ítem P1 del BACKLOG.

### Causa raíz
`OrdiaViewModel.attachCaptureIfPresent` (y los pickers de Note/Task) guardaban `attachmentUri`
directo en `AttachmentEntity.uri`. Sin copia de bytes, la persistencia del acceso quedaba delegada
a un permiso persistente frágil y silencioso ante fallos.

### Solución (mínima, robusta)
- **Nuevo `AttachmentStorage`** (`data/repository/AttachmentStorage.kt`): copia los bytes del URI
  fuente a `filesDir/attachments/<uuid><ext>` (ext deducida del displayName/mimeType) y devuelve
  un URI `FileProvider`. `delete(uri)` borra el archivo interno. `resolveForOpening(uri)` devuelve
  el URI tal cual (los FileProvider ya son válidos; legacy externo pasa sin tocar para no romper).
- **`OrdiaViewModel`**:
  - `addAttachment(ownerType, ownerId, sourceUri, displayName, mimeType, sizeBytes)`: copia con
    `attachmentStorage.import()`; si la copia falla, conserva el URI original (no pierde el adjunto).
  - `attachCaptureIfPresent` ahora importa el contenido (antes guardaba URI crudo).
  - `resolveAttachmentUri(uri)` para abrir adjuntos.
  - `deleteAttachment` borra también el archivo interno.
- **`NoteEditorScreen.kt` / `TaskDetailScreen.kt`**: usan nuevo `addAttachment`, `resolveAttachmentUri`
  al abrir; eliminado `takePersistableUriPermission` (innecesario: contenido en interno).
- **Manifest**: `FileProvider` authority `${applicationId}.attachments` + `ordia_attachment_paths.xml`
  (`files-path name="attachments" path="attachments/"`).
- **DI**: `AttachmentStorage(context)` en `AppContainer`, cableado en `OrdiaRoot` Factory.

### Archivos modificados/creados
- Creado: `app/src/main/java/com/ordia/app/data/repository/AttachmentStorage.kt`
- Creado: `app/src/main/res/xml/ordia_attachment_paths.xml`
- Modificado: `app/src/main/AndroidManifest.xml` (FileProvider)
- Modificado: `app/src/main/java/com/ordia/app/di/AppContainer.kt`
- Modificado: `app/src/main/java/com/ordia/app/ui/OrdiaRoot.kt`
- Modificado: `app/src/main/java/com/ordia/app/ui/OrdiaViewModel.kt`
- Modificado: `app/src/main/java/com/ordia/app/ui/screens/NoteEditorScreen.kt`
- Modificado: `app/src/main/java/com/ordia/app/ui/screens/TaskDetailScreen.kt`

### Tests
- `bash tools/run_domain_tests.sh` = **275 tests PASS** (25 clases).
- `bash tools/run_domain_checks.sh` = smoke 25 assertions OK.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). La copia
  de bytes, FileProvider y `ACTION_VIEW` requieren dispositivo. CI remoto ejecuta `Verificar`.

### Hallazgos para próximas ejecuciones
- **Migración de adjuntos legacy**: URIs externos antiguos ya guardados siguen funcionando vía
  `resolveAttachmentUri` (pasan tal cual). NEXT paso opcional: al abrir un adjunto legacy, intentar
  copiarlo a interno si todavía accesible (lazy migration). Riesgo: URIs ya inválidos. Evaluar.
- Parser: candidatos restantes — "esta semana", "principios de mes", "próximo bimestre/semestre".

### Commits
- `feat(persistence): copy attachments to internal storage (P1)` (código).
- docs(autonomy): registro ciclo 32 (cont.4) — este commit.

### Siguiente
- Continuar ciclo interminable. Migración lazy de adjuntos legacy si se evalúa segura;
  si no, parser "esta semana"/"principios de mes". P2/P3 rendimiento/UX.


---

## 2026-08-13 — Ciclo 33: parser “principios de mes”, “fines de semana” recurrentes, días pasados

- **HEAD inicial**: `bdd3dc0` (ciclo 32 cont.3 docs) — base local al iniciar el run.
- **Cambio de base**: al hacer `git fetch` el remoto había avanzado a `82ba021` (ciclo 32 cont.4:
  adjuntos a almacenamiento interno). Mi base local estaba **obsoleta** (STALE potencial). Stash +
  `pull --ff-only` a `82ba021` + reaplicación. Conflicto en `CURRENT_STATE.md`/`RUN_LOG.md`
  (ambos runs tocamos docs de autonomía); resuelto tomando base remota y regenerando mis ediciones
  sobre ella. Código (`NaturalTaskParser.kt`, test) y `BACKLOG.md` se reaplicaron **sin conflicto**.
- **Problema (P1 — evitar olvidos / menos fricción de captura)**: tres formas cotidianas en
  español caían a `dueAt=null` o fecha errónea:
  1. **“principios de mes”** (día 1: rentas, pagos, cierres): `startOfMonthPattern` existía como
     regex pero sin resolución → `dueAt=null` o +30d por “mes” → vencimiento olvidado.
  2. **“fines de semana” (plural)** (“cada fines de semana”/“los findes”): el patrón singular “fin
     de semana” no casaba “fines” (residuo) y no existía hábito sábado+domingo → recurrencia perdida.
  3. **“el jueves pasado” / “el último lunes” / “el martes anterior”**: `weekdayPattern` capturaba
     el día como **próximo** y “pasado” quedaba en el título → fecha **FUTURA** errónea + título sucio
     (tarea vencida mal fechada, no aparecía como atrasada en What Now). Orden inverso tampoco casaba.
- **Solución (mínima, en `NaturalTaskParser.kt`)**:
  1. Rama `startOfMonthPattern` en `monthBoundaryDueAt` (`withDayOfMonth(1)`, rueda al mes
     siguiente si hoy>1); detectado ANTES del período próximo (evita colisión con “mes que viene”).
  2. `weekendRecurrencePattern` (plural) → `RecurrenceFrequency.WEEKLY`, `days=[6,7]` (CSV “6,7”);
     consume “cada”/“los” inicial; singular sigue siendo fecha única (próximo sábado).
  3. `previousWeekdayPattern` (forward “jueves pasado”) + `previousWeekdayReversedPattern` (inverso
     “último lunes”) + función `previousWeekday()` (última ocurrencia **pasada**, excluye hoy: si
     hoy es ese día, va al de la semana anterior); detectados ANTES de `weekdayPattern`.
- **Tests**: +11 (4 principios, 2 fines-de-semana, 5 días-pasados). **286 domain tests PASS**
  (`bash tools/run_domain_tests.sh`), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room (sin Android SDK).
- **Commits (previstos)**:
  - feat(parser): “principios de mes”, “fines de semana” recurrentes, días pasados — este commit.
  - docs(autonomy): registro ciclo 33 — este commit.
- **HEAD final**: f09aee0 (feat 567193e + docs f09aee0; push a openhands/autonomous-ordia OK).

### Siguiente
- Continuar ciclo interminable. Candidatos parser: “esta semana” (vs “la semana que viene”),
  “próximo bimestre/semestre”, “próxima quincena” (+15d), “principios de semana” (lunes).
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).
- P2/P3: derivedStateOf/keys en LazyColumns grandes; BackHandler; contraste onSurfaceVariant.

---

## Ciclo 34 - 2026-08-13 (UTC)

- **Run/ciclo**: 34
- **HEAD inicial**: cdc3135 (origin/openhands/autonomous-ordia sincronizado; sin divergencia)
- **Problema seleccionado**: `NaturalTaskParser` no parseaba **"esta semana"** (plazo blando cotidiano). `dueAt=null` -> tarea olvidada (sin recordatorio, invisible en planificador/What Now); con hora explicita se fechaba en HOY por error ("esta semana a las 18" -> hoy 18:00).
- **Prioridad**: P1 (evitar olvidos, menos friccion de captura, inteligencia del parser).
- **Causa raiz**: no existia patron para "esta semana"; la palabra "semana" solo la capturaba `nextPeriodPattern` ("semana que viene"/"proxima semana" = +7d). Sin hora explicita no habia respaldo -> null. Con hora, el periodo proximo activaba el combo hoy+hora (fecha futura erronea).
- **Solucion (minima, en `NaturalTaskParser.kt`)**:
  - Nuevo `thisWeekPattern` (`esta semana` + opcional `que viene`).
  - Resuelve al **proximo domingo** (fin de semana ISO lun-dom) a las 9:00 por defecto; **hoy** si hoy es domingo (`TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)`).
  - Detectado y borrado **antes** del periodo proximo: asi "semana" no activa "semana que viene" (sigue siendo +7d) y "esta semana que viene" se limpia del titulo.
  - Integrado en `effectiveRelativeDueAt` como dias (junto a monthBoundary/nextPeriod) para combinarse con una hora explicita.
- **Tests**: +4 (`estaSemanaParsesDueAtProximoDomingo`, `estaSemanaRespetaHoraExplicita`, `estaSemanaSiHoyEsDomingoEsHoy`, `estaSemanaNoColisionaConSemanaQueViene`). **290 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room (sin Android SDK).
- **Commits (previstos)**: feat(parser): "esta semana" como plazo blando (proximo domingo); docs(autonomy): registro ciclo 34.
- **HEAD final**: (tras commit/push a openhands/autonomous-ordia).

### Siguiente
- Continuar ciclo interminable. Candidatos parser: "proximo bimestre/semestre" (evaluar frecuencia), "proxima quincena" (+15d), "principios de semana" (lunes).
- P1 adjuntos: migracion lazy de adjuntos legacy (evaluar seguridad primero).
- P2/P3: derivedStateOf/keys en LazyColumns grandes; BackHandler; contraste onSurfaceVariant.

## Ciclo 34 (cont.) - 2026-08-13 (UTC) - "principios de semana" (lunes)

- **Run/ciclo**: 34 (cont.)
- **HEAD inicial**: 769ef38 (origin/openhands/autonomous-ordia sincronizado; sin divergencia)
- **Problema seleccionado**: `NaturalTaskParser` no parseaba **"principios de semana"** / "a principios de semana" (frase cotidiana: "lo termino a principios de semana"). Caía a `dueAt=null` (tarea olvidada: sin recordatorio, invisible en planificador/What Now) o, con hora explícita, se fechaba en HOY por error.
- **Prioridad**: P1 (evitar olvidos, menos fricción de captura, inteligencia del parser).
- **Causa raíz**: no existía patrón para "principios de semana"; la palabra "semana" solo la capturaba `nextPeriodPattern` ("semana que viene"/"próxima semana" = +7d) o `thisWeekPattern` ("esta semana"). Sin hora explícita no había respaldo -> null. Con hora, el período próximo activaba el combo hoy+hora (fecha futura errónea).
- **Solución (mínima, en `NaturalTaskParser.kt`)**:
  - Nuevo `startOfWeekPattern` (`principios`/`principio` + `de`/`del` + `semana`, con opcional `a `).
  - Resuelve al **lunes más cercano en HOY o futuro** (ISO, semana lunes→domingo): si hoy es lunes, hoy; si es martes-domingo, el lunes de la semana siguiente. Como plazo blando nunca se fecha en pasado. Hora por defecto 9:00.
  - Detectado y borrado **antes** del período próximo: así "semana" no activa "semana que viene" (sigue siendo +7d).
  - Integrado en `effectiveRelativeDueAt` como días (junto a monthBoundary/thisWeek/nextPeriod) para combinarse con una hora explícita. También en `relativeIsDays`.
- **Tests**: +4 (`principiosDeSemanaParsesDueAtProximoLunes`, `principiosDeSemanaNoColisionaConSemanaQueViene`, `principiosDeSemanaRespetaHoraExplicita`, `principiosDeSemanaSiHoyEsLunesEsHoy`). **294 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room (sin Android SDK).
- **Commits**: feat(parser): "principios de semana" como plazo blando (lunes) — 01e28fc. docs(autonomy): este registro.
- **HEAD final**: (tras commit/push a openhands/autonomous-ordia).

### Siguiente
- Continuar ciclo interminable. Candidatos parser: "próximo bimestre/semestre" (evaluar frecuencia), "próxima quincena" (+15d), "mediados de semana" (miércoles), "a finales de semana" (viernes/dom).
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).
- P2/P3: derivedStateOf/keys en LazyColumns grandes; BackHandler; contraste onSurfaceVariant.

## Ciclo 35 - 2026-08-13 (UTC)

- **Run/ciclo**: 35
- **HEAD inicial**: 8146acf (origin/openhands/autonomous-ordia; tras detectar colisión con base local obsoleta 46efb3e y descartar trabajo no commiteado).
- **Problema seleccionado**: `NaturalTaskParser` no reconocía la construcción coloquial multi-palabra **"un par de"** (= 2): "en un par de días/semanas/meses". `relativePattern` sólo aceptaba números o palabras-sueltas, así que caía a `dueAt=null` → tarea **olvidada** (sin recordatorio, invisible en planificador/What Now).
- **Prioridad**: P1 (evitar olvidos, menos fricción de captura, inteligencia honesta del parser).
- **Causa raíz**: la regex de cantidad del `relativePattern` enumeraba palabras-sueltas pero no la frase "un par de" (3 tokens). Al no coincidir, el match relativo fallaba y `dueAt` quedaba null.
- **Colisión con otro run**: al iniciar, mi base local (46efb3e) estaba obsoleta; otro run había commiteado quincena/bimestre/semestre (8146acf). Yo tenía cambios locales no commiteados que incluían mi propia versión de "quincena" (redundante) + "un par de" + docs. Decisión: descartar TODO el trabajo no commiteado (`git stash`), fast-forward limpio a 8146acf, y reaplicar SOLO "un par de" (que el remoto no tenía). Sin force push, sin reset --hard, sin sobrescribir trabajo válido.
- **Solución (mínima, en `NaturalTaskParser.kt`)**:
  - `relativePattern`: añadido `un\s+par\s+de` como primera alternativa del grupo de cantidad.
  - `parseWrittenNumber`: añadido `"un par de" -> 2L`.
  - Funciona con cualquier unidad relativa y con hora explícita ("en un par de días a las 10").
- **Tests**: +4 (`unParDeDiasResuelveMasDosDias`, `unParDeSemanasResuelveMasCatorceDias`, `unParDeMesesResuelveMasSesentaDias`, `unParDeDiasConHoraExplicita`). **308 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room (sin Android SDK).
- **Commits**: feat(parser): "un par de" (= 2) en relativePattern/parseWrittenNumber. docs(autonomy): este registro.
- **HEAD final**: (tras commit/push a openhands/autonomous-ordia).

### Siguiente
- Continuar ciclo de parser: "mediados de semana" (miércoles), "a finales de semana" (viernes/dom), "un par de horas" ya cubierto.
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).
- Descubrimiento continuo: auditar otras áreas (captura, What Now, rutinas) en busca de oportunidades de producto, no solo parser.

## Ciclo 36 - 2026-08-13 (UTC)

- **Run/ciclo**: 36
- **HEAD inicial**: e4157c1 (origin/openhands/autonomous-ordia sincronizado tras push del ciclo 35 — resuelto bloqueo de auth usando `github_token` en vez de `GITHUB_TOKEN` que estaba ausente).
- **Problema seleccionado**: `NaturalTaskParser` no reconocía **"mediados de semana"** / "a mediados de semana" (frase cotidiana: "lo termino a mediados de semana"). Caía a `dueAt=null` → tarea **olvidada** (sin recordatorio, invisible en planificador/What Now).
- **Prioridad**: P1 (evitar olvidos, menos fricción de captura, inteligencia honesta del parser).
- **Causa raíz**: existían `startOfWeekPattern` ("principios de semana" = lunes) y `midOfMonthPattern` ("mediados de mes" = día 15), pero ninguna familia cubría el caso "mediados de semana". La palabra "semana" solo la capturaba `nextPeriodPattern` ("semana que viene" = +7d) o `thisWeekPattern` ("esta semana"). Sin hora explícita no había respaldo → null.
- **Solución (mínima, en `NaturalTaskParser.kt`)**:
  - Nuevo `midOfWeekPattern` (`mediados`/`mediado` + `de`/`del` + `semana`, con opcional `a `).
  - Resuelve al **miércoles más cercano en HOY o futuro** (`nextOrSame(WEDNESDAY)`): si hoy es miércoles, hoy; si no, el miércoles de esta semana o el de la siguiente. Como plazo blando nunca se fecha en pasado. Hora por defecto 9:00.
  - Detectado y borrado **antes** del período próximo: así "semana" no activa "semana que viene" (sigue siendo +7d).
  - Integrado en `effectiveRelativeDueAt` como días (junto a startOfWeek/monthBoundary/thisWeek/nextPeriod) para combinarse con una hora explícita. También en `relativeIsDays`.
  - No colisiona con "mediados de mes" (uno termina en "semana", otro en "mes").
- **Tests**: +4 (`mediadosDeSemanaSiHoyEsMiercolesEsHoy`, `mediadosDeSemanaDesdeLunesEsMiercolesMismaSemana`, `mediadosDeSemanaRespetaHoraExplicita`, `mediadosDeSemanaNoColisionaConMediadosDeMes`). **312 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room (sin Android SDK).
- **Commits**: feat(parser): "mediados de semana" (= miércoles). docs(autonomy): este registro.
- **HEAD final**: (tras commit/push a openhands/autonomous-ordia).

### Siguiente
- Continuar ciclo de parser: "a finales de semana" (evaluar ambigüedad viernes vs sábado vs domingo antes de implementar — "fin de semana" ya cubre sábado), "próxima quincena" (+15d).
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).
- Descubrimiento continuo: auditar captura, What Now, rutinas en busca de oportunidades de producto reales, no solo parser.
- Nota operativa: `GITHUB_TOKEN` ausente en este entorno; usar `github_token` para push.

## Ciclo 37 - 2026-08-13 (UTC)

- **Run/ciclo**: 37
- **HEAD inicial**: 9ac1a8b → detectada base obsoleta: otro run commiteó `7fa4056` ("a las N horas" como hora, no duración falsa). Stash de mi trabajo no commiteado, fast-forward limpio a `7fa4056`, reaplicación (`git stash pop`) sin conflictos (auto-merge en parser y test). Sin force push, sin reset --hard, sin sobrescribir trabajo válido del otro run.
- **Problema seleccionado**: `NaturalTaskParser` no reconocía **"a finales de semana"** / "finales de semana" (forma plural cotidiana análoga a "finales de mes": "lo termino a finales de semana"). `weekendPattern` solo casaba `fin de semana` (singular); "finales" no casaba y la frase quedaba como residuo en el título con `dueAt=null` → tarea **olvidada** (sin recordatorio, invisible en planificador/What Now).
- **Prioridad**: P1 (evitar olvidos, menos fricción de captura, inteligencia honesta del parser; brecha simétrica frente a `endOfMonthPattern` que sí acepta "finales de mes").
- **Causa raíz**: `weekendPattern = \b(?:este\s+|el\s+|próximo\s+)?fin\s+de\s+semana\b` exigía `fin` exacto (singular). La palabra "finales" no casaba y, al no coincidir ningún patrón, `dueAt` quedaba null. Distinción crítica: "fines de semana" (f-i-n-e-s) ya es recurrencia WEEKLY sáb+dom en `parseRecurrence`; "finales de semana" es fecha única y no debía confundirse con ella.
- **Solución (mínima, en `NaturalTaskParser.kt`)**:
  - Ampliación de `weekendPattern`: `(?:a\s+)?(?:este\s+|el\s+|próximo\s+)?(?:fin|finales)\s+de\s+semana\b`. Acepta `finales` como variante y el prefijo opcional `a ` (igual que `endOfMonthPattern`/`midOfMonthPattern`).
  - Reutiliza TODO el flujo existente: detección temprana (`weekendEarlyMatch`), borrado antes del período próximo, resolución a **próximo sábado** (igual que "fin de semana"), hora canónica 9:00, combinable con hora explícita.
  - **No** acepta `fines` (f-i-n-e-s): como `fin` va seguido de `\s+de` y en "fines" va "es" (sin espacio), no casa → la frase llega intacta a `parseRecurrence` que sigue generando WEEKLY sáb+dom. Decisión de ambigüedad viernes/sáb/dom: resolver a sábado por **consistencia** con "fin de semana" ya existente (no introducir un tercer comportamiento).
- **Tests**: +4 (`aFinalesDeSemanaProgramaProximoSabadoYLimpiaTitulo`, `finalesDeSemanaSueltoProgramaProximoSabadoYLimpiaTitulo`, `finalesDeSemanaRespetaHoraExplicita`, `finalesDeSemanaNoColisionaConRecurrenciaFinesDeSemana`). Verificación TDD: los 3 primeros fallaron antes del fix (residuo en título + dueAt null), pasaron tras ampliar el patrón. **319 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`). Coexisten con el fix del otro run ("a las N horas").
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room (sin Android SDK).
- **Commits**: feat(parser): "a finales de semana" = próximo sábado. docs(autonomy): este registro.
- **HEAD final**: (tras commit/push a openhands/autonomous-ordia).

### Siguiente
- Continuar ciclo de parser: "próxima quincena" (+15d), "próximo bimestre/semestre" (evaluar frecuencia). Buscar oportunidades de producto en otras áreas (captura, What Now, rutinas, recordatorios), no solo parser.
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero; URIs ya inválidos).
- Descubrimiento continuo: auditar recuperación de tareas olvidadas, detección de vencidas importantes, replanificación automática.
- Nota operativa: `GITHUB_TOKEN` ausente en este entorno; usar `github_token` para push.

## Ciclo 39 - 2026-08-13 (UTC) - fix: "de la mañana"/"por la mañana" NO es fecha "mañana"

- **Run/ciclo**: 39
- **HEAD inicial**: e4157c1 (ciclo 35). Base obsoleta tras dos rebases sucesivos: otro run commiteó ciclos 36/37 + 3 commits ("a las N horas", fechas imposibles, "hace N"/"la semana/el mes pasado", "a finales de semana") y luego el ciclo 38 ("fechas pasadas" + recuperación fechas imposibles). Rebase no destructivo sobre `8451597` (HEAD remoto). Auto-merge limpio en `NaturalTaskParser.kt` + test; conflictos solo en docs (CURRENT_STATE/RUN_LOG), resueltos conservando el trabajo del otro run y renumerando el mío a ciclo 39. Sin force push, sin reset --hard, sin sobrescribir trabajo válido.
- **Problema seleccionado**: `NaturalTaskParser` fechaba en MAÑANA tareas que usaban "de la mañana"/"por la mañana" como marcador de **hora** (parte del día), porque la palabra "mañana" colisionaba con el token de **fecha** "mañana". Ejemplos reales afectados:
  - "Reunión a las 9 de la mañana" → se programaba para MAÑANA 09:00 (reunión perdida HOY).
  - "Llamar a mamá por la mañana" → MAÑANA 09:00 en vez de HOY 09:00.
  - "Desayuno a las 8 de la mañana" → MAÑANA 08:00.
  La ambigüedad léxica "mañana" (fecha) vs "mañana" (parte del día) no se resolvía: el branch de fecha hacía match con la mera presencia de la palabra.
- **Prioridad**: P1 (tarea en día erróneo → reunión/recordatorio perdido el mismo día; corrige captura, evita olvidos).
- **Causa raíz**: en la rama de fecha, `Regex("""(?i)\bmañana\b""").containsMatchIn(working)` matcheaba cualquier aparición de "mañana", incluida la que forma parte de "de la mañana"/"por la mañana"/"a la mañana"/"esta mañana". Estos marcadores de hora se procesaban luego (hora canónica 09:00), pero la fecha ya había saltado a +1d.
- **Solución (mínima, en `NaturalTaskParser.kt`)**:
  - Reemplazada la rama `\bmañana\b` por `mananaAsDate(working)`: recorre todas las apariciones de "mañana" y devuelve `true` (fecha = mañana) sólo si **al menos una** NO está precedida por un marcador de parte del día (`de la ` / `por la ` / `a la ` / `esta `).
  - Así "Reunión a las 9 de la mañana" (única aparición, precedida por "de la ") → NO es fecha → se queda en HOY. "Hacer X mañana por la mañana" (primera aparición suelta) → Sí fecha → mañana. "mañana a las 9" → mañana.
  - `pasado mañana` se sigue resolviendo ANTES (rama previa, sin cambios), así que no hay regresión.
- **Tests**: +3 regresión (`deLaMananaWithoutDateStaysToday`, `porLaMananaWithoutDateStaysToday`, `mananaPorLaMananaStillResolvesTomorrow`). **336 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases (319 base remota + 3 nuevos). Smoke 25 OK.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room (sin Android SDK).
- **Commits**: `cfa29cd` fix(parser): "de/por/a la mañana" (hora) no colisiona con fecha "mañana". docs(autonomy): este registro (mismo commit).
- **HEAD final**: `cfa29cd` (push a `openhands/autonomous-ordia`, fast-forward sobre `8451597`).

### Siguiente
- Continuar descubrimiento continuo (no solo parser): auditar What Now, rutinas, captura, recordatorios, detección de vencidas.
- Parser candidatos: "próxima quincena" (+15d), manejo robusto de múltiples marcadores temporales en una frase.
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero; URIs ya inválidos).
- Nota operativa: `GITHUB_TOKEN` ausente en este entorno; usar `github_token` para push.

## Ciclo 40 - 2026-08-13 (UTC) - feat: recordatorios con números escritos y fracciones

- **Run/ciclo**: 40 (renumerado desde 38: base f2d26ba obsoleta; merge no destructivo del remoto que avanzó con ciclos 37/38/39; auto-merge limpio en parser+test, conflictos solo en docs resueltos conservando el trabajo del otro run. Sin force push, sin reset --hard).
- **HEAD inicial**: f2d26ba (origin/openhands/autonomous-ordia sincronizado; ciclo 37 ya en remoto).
- **Problema seleccionado**: `NaturalTaskParser` NO reconocía **números escritos** en recordatorios relativos ("recuérdame una/dos horas antes", "una hora antes", "treinta minutos antes") ni **fracciones** ("media hora antes", "un cuarto de hora antes"): `reminderOffsetMinutes=null` y la frase quedaba como residuo en el título. Además "media hora antes" era **robado por el patrón de duración fraccionaria** (30 min falsos como duración) y el recordatorio quedaba en null. La cita se olvidaba (sin recordatorio programado).
- **Prioridad**: P2 (evitar olvidos por recordatorio perdido; asimetría con la duración relativa "en dos horas" que sí funcionaba).
- **Causa raíz**: `reminderPatterns` solo capturaba `(\d{1,3})` (dígitos); `parseWrittenNumber` existía pero no se usaba en recordatorios. Las fracciones ("media hora") no tenían patrón propio de recordatorio, así que caían al de duración. Además el código accedía a `groupValues[2]` asumiendo 2 grupos, lo que rompía con patrones de 1 grupo (`IndexOutOfBoundsException`).
- **Solución (mínima, en `NaturalTaskParser.kt`)**:
  - `writtenAmountPattern` (dígitos o números escritos en español, simétrico a la fecha relativa) en los 2 patrones de recordatorio existentes.
  - 2 patrones nuevos de fracción con **contexto obligatorio** ("antes"/"de anticipación"/verbo) para no robar una duración real.
  - Offset vía `parseWrittenNumber`; `media hora`=30 / `cuarto de hora`=15; acceso `getOrNull(2)` seguro.
- **Tests**: +8 (`parsesWrittenAmountReminderWithVerb`, `parsesWrittenAmountReminderTwoHours`, `parsesWrittenAmountReminderThirtyMinutes`, `parsesWrittenAmountReminderWithoutVerb`, `mediaHoraAntesEsRecordatorio`, `cuartoDeHoraAntesEsRecordatorio`, `recuerdameMediaHoraDeAnticipacion`, `mediaHoraSinAntesSigueSiendoDuracion` — regresión). **344 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases (336 base remota + 8 nuevos). Smoke 25 OK.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room (sin Android SDK).
- **Commits**: `f8023fd` feat(parser): recordatorios con números escritos y fracciones; `24d31f8` docs(autonomy): registro (renumerado a 40 tras merge).
- **HEAD final**: (tras commit/push a openhands/autonomous-ordia).

### Siguiente
- Continuar ciclo de parser: rango horario sin "horas" ("clase de 9 a 11"), "la quincena" como plazo/recurrencia.
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).
- Descubrimiento continuo: auditar captura, What Now, rutinas en busca de oportunidades de producto reales, no solo parser.
- Nota operativa: usar `github_token` (en este entorno `GITHUB_TOKEN` puede estar ausente).

## Ciclo 41 - 2026-08-13 (UTC) - "los lunes miércoles y viernes" (sin coma) + plurales sábados/domingos

- **Run/ciclo**: 41 (renumerado desde 40: base `4f803a9` obsoleta; otro run commiteó ciclo 40 "recordatorios con números escritos y fracciones" + merge `91c8b9f`. Rebase no destructivo sobre `91c8b9f`; auto-merge limpio en parser+test (cambios ortogonales); conflictos solo en docs (RUN_LOG), resueltos conservando su ciclo 40 y renumerando el mío a 41. Tras push, nuevo run remoto `60007d1` ("test parser listas de días separadas por espacio", misma feature, tests complementarios): segundo rebase no destructivo sobre `60007d1`; conflicto de tests resuelto conservando los 6 tests (3 suyos + 3 míos). Sin force push, sin reset --hard).
- **HEAD inicial**: `91c8b9f` (origin/openhands/autonomous-ordia tras ciclo 40 del otro run). Base obsoleta en varios puntos a lo largo de la sesión: otros runs commitearon ciclos 36–40 ("a las N horas", fechas pasadas, recuperación de fechas imposibles, "a finales de semana", "de/por/a la mañana" vs fecha "mañana", recordatorios con números escritos y fracciones). Rebases sucesivos no destructivos; cada vez renumeré mi ciclo para no colisionar.
- **Problema seleccionado**: **Pérdida de datos silenciosa en rutinas semanales** con listas de días sin coma (forma informal natural del español). "regar plantas los lunes miércoles y viernes" → el parser solo capturaba "lunes" y perdía "miércoles" y "viernes" → la rutina se repetía UN solo día en vez de tres. El usuario creaba una rutina creyendo que cubría varios días y nunca se le recordaba en los demás. Adicionalmente, los plurales "sábados"/"domingos" no casaban (patrón usaba forma singular con `\b`) y se perdían también.
- **Prioridad**: P1 (pérdida de datos: rutinas con menos días de los que el usuario escribió; recordatorios no disparan en los días perdidos → olvidos).
- **Causa raíz**: `dayListPattern` exigía separador "," o "y" entre cada par de días. La forma informal "los lunes miércoles y viernes" (sin coma entre los dos primeros) rompía el patrón tras el primer día, capturaba solo "lunes" y dejaba "miércoles y viernes" como residuo en el título. Independientemente, "sábados"/"domingos" (plural) no casaban porque el patrón usaba `s[aá]bado|domingo` (singular) con límite `\b`.
- **Solución (mínima, en `NaturalTaskParser.kt`)**:
  - Hacer el separador entre días **opcional** en `dayListPattern`: `(?:\s*(?:,|y)?\s*(?:...))`. Como los nombres de día son palabras cerradas y específicas, admitir separador vacío solo casa cuando la palabra siguiente es otro día, sin riesgo de robar texto ajeno ("los lunes con el equipo" para en "lunes" porque "con" no es un día).
  - Aceptar **plural** de sábado/domingo: `s[aá]bados?|domingos?`.
- **Tests**: +3 de regresión (`parsesDayListWithoutCommaSeparator` → 1,3,5; `parsesDayListWithPluralSabadoDomingo` → 2,4,6; `dayListStopsAtNonDayWord` → no roba "con el comité"). Tras segundo rebase sobre `60007d1`, mis 3 tests coexisten con los 3 del otro run (misma feature, casos distintos): **350 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases (347 base remota + 3 nuevos). Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room (sin Android SDK).
- **Commits**: `fix(parser): listas de días sin coma + plurales sábados/domingos (ciclo 41)` (`8ceb8d2`), rebaseado no destructivamente primero sobre `91c8b9f` y luego sobre `60007d1` (segundo run remoto sobre la misma feature, tests complementarios). Auto-merge limpio en `NaturalTaskParser.kt`; conflicto de tests resuelto conservando los 6 tests. Conflictos solo en docs (RUN_LOG), resueltos conservando el otro ciclo 40 y renumerando el mío a 41.
- **HEAD final**: `8ceb8d2` (push OK a `origin/openhands/autonomous-ordia`: `60007d1..8ceb8d2`).

### Siguiente
- Continuar ciclo de parser: "próxima quincena" (+15d), manejo robusto de múltiples marcadores temporales en una frase.
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).
- P2/P3: derivedStateOf/keys en LazyColumns grandes; BackHandler; contraste onSurfaceVariant.


## Ciclo 42 - 2026-08-13 (UTC) - feat: rango horario sin "horas" (ambas < 13)

- **Run/ciclo**: 42 (base `8ceb8d2` = ciclo 41 remoto, ya sincronizada al inicio de la sesión; commit `0a77387` fue creado en un run anterior de esta misma sesión y ya está en el HEAD remoto).
- **HEAD inicial**: `8ceb8d2` → tras trabajo de parser, `0a77387` (feat(parser): rango horario sin unidad "horas" cuando ambas horas < 13). Push OK a `origin/openhands/autonomous-ordia`. Este run (continuación) se dedica a (a) verificar la línea base de tests y (b) actualizar la memoria permanente (CURRENT_STATE, BACKLOG, este RUN_LOG) que quedó pendiente del commit de código.
- **Problema seleccionado**: `NaturalTaskParser` NO reconocía rango horario **sin la palabra "horas"** en formato 12h ("clase de 9 a 11", "taller de 10 a 12"): `dueAt=null`, `durationMinutes=null`, rango crudo como residuo en el título. El `timeRangePattern` casaba, pero el guard lo rechazaba (exigía unidad "horas"/"hs"/"h" o alguna hora ≥ 13) para evitar falsos positivos como "comprar de 2 a 5 entradas" (cantidad, no horario).
- **Prioridad**: P2 (forma cotidiana de expresar un bloque horario; evita captura incompleta / recordatorio sin duración).
- **Causa raíz**: el guard del `rangeMatch` trataba todo rango sin unidad y ambas horas < 13 como ambiguo y lo descartaba por completo, sin distinguir si el rango abre/cierra una ventana horaria ("clase de 9 a 11") o expresa una cantidad ("de 2 a 5 entradas").
- **Solución (mínima, en `NaturalTaskParser.kt`)**: heurística honesta (no IA, no random). Un rango sin unidad y ambas horas < 13 se acepta como ventana horaria **solo si NO va seguido de un sustantivo de cantidad**: si tras el rango hay fin de cadena o un conector/preposición/puntuación ("con Juan", "y luego", ", después") se entiende como horario; si hay un sustantivo después ("entradas", "personas") se respeta como cantidad y no se consume. Restricción `end - start in 1..11` evita rangos absurdos. Así "clase de 9 a 11" → dur 120, título "Clase"; "comprar de 2 a 5 entradas" → sin duración, título intacto.
- **Tests**: +3 (`rangeWithoutUnitBothUnder13ParsesAsTimeRange`, `rangeWithoutUnitKeepsTrailingText` para preservación, y guard de conteo de ítems como regresión). **353 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases (350 base c.41 + 3 nuevos). Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room (sin Android SDK).
- **Commits**: `0a77387` feat(parser): rango horario sin unidad "horas" cuando ambas horas < 13 (push OK). Este run añade `docs(autonomy): memoria ciclo 42 (BACKLOG/CURRENT_STATE/RUN_LOG)`.
- **HEAD final**: (tras commit de docs a `openhands/autonomous-ordia`).

### Siguiente
- Continuar ciclo de parser: "la quincena" como plazo/recurrencia (día 15 y fin de mes), manejo robusto de múltiples marcadores temporales en una frase.
- Descubrimiento continuo (más allá del parser): auditar What Now, captura, rutinas, recordatorios, detección de vencidas, búsqueda universal en busca de oportunidades de producto reales.
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).
- Nota operativa: usar `github_token` para push si `GITHUB_TOKEN` no está disponible.

## Ciclo 43 - 2026-08-13 (UTC) - feat: "entre semana"/"días laborables"/"de lunes a viernes" = WEEKLY Lun–Vie

- **Run/ciclo**: 43 (renumerado: la otra ejecución concurrente reclamó los números 41 y 42 con “listas de días sin coma + plurales sábados/domingos” y “rango horario sin horas”). Procedimiento no destructivo: `git rebase` de mi commit sobre el HEAD remoto actualizado (`727e7b8`, que incluye los ciclos 38–42 de la otra ejecución); auto-merge limpio en `NaturalTaskParser.kt` + tests (cambios ortogonales: el remoto tocó listas de días/plurales y rango horario, yo la recurrencia laboral Lun–Vie). Conflictos solo en docs (`CURRENT_STATE.md`, `RUN_LOG.md`), resueltos conservando el trabajo del otro run y renumerando el mío a 43. Sin force push, sin reset --hard.
- **HEAD inicial**: `727e7b8` (origin/openhands/autonomous-ordia sincronizado tras fetch; el remoto avanzó 2 commits: docs c.42 + ampliación followers).
- **Problema seleccionado**: `NaturalTaskParser` NO generaba recurrencia para frases cotidianas de hábitos laborables: "Gimnasio entre semana", "Trabajo de lunes a viernes", "Estudiar días laborables", "Reunión los días hábiles". El parser las trataba como texto suelto → tarea única (freq=NONE) que aparece una sola vez y se olvida el resto de la semana. Peor, "de lunes a viernes" dejaba "lunes" como residuo en el título: `dayListPattern` capturaba solo "lunes" (days=[1]) y el viernes se perdía. Brecha simétrica frente a `weekendRecurrencePattern` ("fines de semana" → WEEKLY sáb+dom, c.33): el hábito Lun–Vie no tenía equivalente.
- **Prioridad**: P1 (evitar olvidos + fricción de captura; hábitos cotidianos que se perdían tras la primera ocurrencia).
- **Causa raíz**: no existía patrón para el rango "lunes a viernes" ni para el conjunto léxico "entre semana/días laborables/hábiles/de semana". El `dayListPattern` existente capturaba listas de días ("lunes, miércoles, viernes") pero no el rango "X a Y", así que "lunes a viernes" se rompía en "lunes" + residuo "a viernes".
  - `weekdayRangePattern`: nuevo patrón `(los |de )?(lunes|martes|miércoles|jueves|viernes)\s+a\s+(martes|…|domingo)` (admite prefijo `los `/`de `). Si el rango incluye viernes → `RecurrenceFrequency.WEEKLY`, `days=[1,2,3,4,5]` (hábito laboral). Resuelve a la próxima ocurrencia del primer día del rango, combinable con hora explícita.
  - `weekdaySetPattern`: variantes léxicas equivalentes → mismo WEEKLY [1-5]: `entre semana`, `días laborables`, `días hábiles`, `días de semana`, `de semana` (prefijo opcional `los `/`de `). Consumen la frase completa (título limpio).
  - **Orden de patrones crítico**: ambos se evalúan **ANTES** que `dayListPattern` para que "los lunes a viernes" sea rango (days=[1..5]) y no la lista ["lunes"] (days=[1]). El singular "fin de semana" sigue siendo fecha única (próximo sábado), sin colisión.
- **Tests**: +7 (`entreSemanaComoRecurrenciaWeekdayLunAVie`, `diasLaborablesComoRecurrenciaWeekday`, `diasHabilesConDeterminanteComoRecurrenciaWeekday`, `deLunesAViernesComoRecurrenciaWeekdayLimpiaTitulo`, `losLunesAViernesComoRangoNoLista`, `entreSemanaRespetaHoraExplicita`, `finDeSemanaSingularNoEsRecurrenciaWeekday` — regresión). **372 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases (365 base remota + 7 nuevos de este ciclo). Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Commits**: `feat(parser): "entre semana"/"de lunes a viernes" = recurrencia Lun–Vie` (rebaseado no destructivamente sobre `727e7b8`; auto-merge limpio en `NaturalTaskParser.kt`+tests; conflictos solo en docs `CURRENT_STATE.md`/`RUN_LOG.md`, resueltos conservando los ciclos 41/42 del otro run y renumerando el mío a 43). Push OK a `origin/openhands/autonomous-ordia`.
- **HEAD final**: `a934b65` (commit feat(parser) rebaseado sobre `727e7b8`; push OK a `origin/openhands/autonomous-ordia`).

### Siguiente
- Descubrimiento continuo más allá del parser: auditar What Now (detección de vencidas importantes), rutinas (re-disparo), captura (fricción), recordatorios.
- Parser candidatos: rango horario sin "horas" ("clase de 9 a 11"), "la quincena" como plazo/recurrencia, múltiples marcadores temporales en una frase.
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero; URIs ya inválidos).
- Nota operativa: usar `github_token` para push.

## Ciclo 44 - 2026-08-13 (UTC) - feat: "la quincena" como hito financiero (día 15 / fin de mes)

- **Run/ciclo**: 44 (renumerado desde 43: al hacer `git fetch` el remoto había avanzado a `a934b65` por el run paralelo que reclamó ciclo 43 con "entre semana"/"de lunes a viernes" = WEEKLY Lun–Vie. Antes de eso el remoto ya había avanzado a `30b7df8` con la recurrencia "cada quincena"/"quincenalmente" (WEEKLY x2, `97ec260`) + 3 commits de docs. Procedimiento no destructivo: stash → `merge --ff-only origin/openhands/autonomous-ordia` → stash pop (resolvió BACKLOG.md), commit, y al detectar nuevo avance remoto `git rebase origin/openhands/autonomous-ordia` (auto-merge limpio en `NaturalTaskParser.kt`+tests; conflicto solo en `RUN_LOG.md`, resuelto conservando el ciclo 43 del otro run y renumerando el mío a 44). Sin force push, sin reset --hard, sin pérdida de trabajo).
- **HEAD inicial**: `a934b65` (origin/openhands/autonomous-ordia tras rebase; `727e7b8` al inicio del run).
- **Problema seleccionado**: `NaturalTaskParser` NO reconocía "la **quincena**" como plazo temporal ("cobro de la quincena", "pago de la quincena a las 18", "primera/segunda quincena"): `dueAt=null` → el vencimiento quedaba **olvidado** (sin recordatorio ni visibilidad en What Now/planificador). Peor, "pago de la quincena a las 18" se fechaba en HOY 18:00 (día erróneo) por el parsing de hora aislada. La quincena (día 15 y fin de mes) es un hito financiero/laboreal muy común en español, simétrico a `finDeMes`/`mediadosDeMes` ya implementados. (Complementario al run paralelo `97ec260` que resolvió la **recurrencia** "cada quincena"; este run resuelve la **fecha/hito** "la quincena".)
- **Prioridad**: P2 (evita captura incompleta / recordatorio sin fecha / vencimiento olvidado en un hito muy común).
- **Causa raíz**: no existía patrón de quincena; las frases caían a `dueAt=null` o eran malinterpretadas por patrones más generales (hora aislada → hoy).
- **Solución (mínima, en `NaturalTaskParser.kt`)**: nuevo `quincenaPattern` que casa `(primera|segunda|1ra|2da)? quincena` con artículo prefijo opcional (`de la`/`de`/`la`). Resolución **honestamente determinística** (no IA, no random):
  - "primera"/"1ra quincena" → día 15; rueda al 15 del mes próximo si hoy ≥ 15.
  - "segunda"/"2da quincena" → fin de mes; rueda a fin del mes próximo si hoy es último día.
  - "la quincena"/"de la quincena" sin cualificar → **próximo hito**: día 15 si hoy<15; fin de mes si 15≤hoy<último; 15 del mes próximo si hoy=último día (consistente con `finDeMes`).
  - Combina con hora explícita ("a las 18" → 15/8 18:00, no hoy 18:00).
  - Procesado **DESPUÉS** del stripping de `nextPeriodMatch`, así "próxima quincena"/"quincena que viene" siguen como +15d y "en N quincenas" como relativo — sin regresión.
  - **Guarda anti-colisión con recurrencia**: tras integrar el run paralelo `97ec260`, el patrón robaba "quincena" de "cada quincena"/"quincenalmente"/"todas las quincenas" (rompía 2 tests de recurrencia). Se añadió `quincenaRecurrencePattern` + guarda en el match: si la frase contiene una forma de recurrencia, el patrón de hito NO consume la palabra, dejándola para `parseRecurrence` (WEEKLY x2). 384 tests PASS.
- **Tests**: +12 (`laQuincenaSinCualificarResuelveProximoHitoDia15SiAntes`, `…FinDeMesSiPosteriorAl15`, `…RuedaAl15ProximoMesSiHoyEsUltimoDia`, `…RespetaHoraExplicita`, `primeraQuincenaResuelveDia15`, `primeraQuincenaRuedaAProximoMesSiHoyPasadoEl15`, `segundaQuincenaResuelveFinDeMes`, `segundaQuincenaRuedaAProximoMesSiHoyEsUltimoDia`, `primeraQuincenaAbreviada1ra`, `segundaQuincenaAbreviada2da`, `proximaQuincenaSigueResolviendoseComoPeriodoProximo`, `quincenaNoInterfiereConEnNQuincenasRelativo`). **384 domain tests PASS** (`bash tools/run_domain_tests.sh`, 25 clases — incluye 7 de "entre semana" y los de recurrencia del run paralelo), smoke 25 OK (`tools/run_domain_checks.sh`). Probe JVM independiente verificó edge de fin de mes (hoy=31/8 → segunda quincena rueda a 30/9, primera a 15/9, sin cualificar a 15/9).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room (sin Android SDK).
- **Commits**: `feat(parser): "la quincena" como hito financiero (día 15 / fin de mes)` (rebaseado sobre `a934b65`; auto-merge limpio en `NaturalTaskParser.kt`+tests; conflicto solo en `RUN_LOG.md`, resuelto conservando el ciclo 43 del otro run).
- **HEAD final**: `d98862b` (push OK a `origin/openhands/autonomous-ordia`).

### Siguiente
- Continuar descubrimiento continuo más allá del parser: auditar What Now, captura, rutinas,
  recordatorios, detección de vencidas, búsqueda universal en busca de oportunidades de producto.
- Parser: manejo robusto de múltiples marcadores temporales en una frase.
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).

### Siguiente
- Descubrimiento continuo: auditar `GuardianCoach`, `SummaryService`, `PlanEngine`, deteccion de vencidas importantes.
- Parser: "la quincena" como hito financiero (dia 15 / fin de mes) ya resuelto por el otro run (ciclo 44).
- P1 adjuntos: migracion lazy de adjuntos legacy (evaluar seguridad primero).

---
## Ciclo 47 - 2026-08-13 (UTC) - fix(parser): "el 15" día del mes suelto con artículo

- **Run/ciclo**: 47 (renumerado desde "42 cont.3"→"46": durante el run el remoto avanzó de `e0850e6` a `a11cf48` por runs paralelos — ciclos 43/44/45/46: "entre semana"/"de lunes a viernes" `a934b65`, "la quincena" `d98862b`, listas de días bare `fc1279b`, `nextBestTask` time-aware `2ef4bfa`, guardián overdue raíz `6d0c6a4` + docs `966b799`/`a11cf48`. Procedimiento no destructivo: `git stash` del trabajo "el 15", `git pull --ff-only` a `966b799`, `git stash pop`; auto-merge limpio en `NaturalTaskParser.kt` + tests (cambios ortogonales); al push el remoto había vuelto a avanzar a `a11cf48`, `git pull --rebase`, conflicto solo en `CURRENT_STATE.md` (docs) resuelto conservando el ciclo 46 del otro run y renumerando el mío a 47. Sin force push, sin reset --hard).
- **HEAD inicial**: `e0850e6` (al inicio del run); el remoto avanzó a `966b799` durante el run.
- **Problema seleccionado (P1, parser)**: **"reunión el 15 a las 10"**, **"cita el 20"**, **"entregar el 5 a las 18"** se fechaban en HOY por error. Causa raíz: "el 15" no casaba con `numericDatePattern` (exige `DD/MM` con mes) ni con `monthNamePattern` (exige mes por nombre) → quedaba como residuo en el título; la hora suelta ("a las 10") se aplicaba entonces a HOY → la cita se programaba **hoy** en vez del día 15 (día erróneo, reunión perdida, recordatorio dispara hoy). Brecha ortogonal a "el 15 de marzo" (sí funcionaba vía `monthNameDate`) y a "el 15 de cada mes" (recurrencia mensual); faltaba el "el N" aislado.
- **Prioridad**: P1 (día erróneo de cita → reunión perdida / recordatorio en día equivocado; dato visible incorrecto en planificador y What Now).
- **Causa raíz**: ausencia de patrón para día del mes suelto con artículo; el fallback de fecha nula dejaba que la hora aislada dominara y anclara a HOY.
- **Solución (mínima, `NaturalTaskParser.kt`)**: nuevo `dayOfMonthPattern` = `(?i)\bel\s+(\d{1,2})(?:\s+del?\s+mes)?\b(?!\s*de\s+[a-záéíóúüñ])` (casa "el 15", "el 15 del mes"; el *negative lookahead* evita colisionar con "el 15 de marzo" —lo resuelve `monthNameDate`— y "el 15 de cada mes" —recurrencia mensual—). Match nuevo `dayOfMonthDate` entre `monthNameDate` y `numericDateMatch`; rama en el `when` de `date` entre ambos para que "el 15 de marzo" gane y "el 15" aislado no caiga al fallback de hoy. Reutiliza el helper existente `nextMonthlyDate(from, day)` (día N de este mes, o del siguiente si ya pasó). Limpieza del residuo "el 15" en el título (después de `numericDatePattern.replace`). Heurística honesta (no IA, no random). Validación de rango `day in 1..31`.
- **Tests**: +4 (`diaDelMesSueltoRuedaAlProximoMesSiYaPaso`, `diaDelMesSueltoSinHoraUsaMediodiaCanónico`, `diaDelMesSueltoFuturoEsteMesSeConserva`, `diaDelMesSueltoNoColisionaConFechaConMes`). Confirmado PASS. **395 domain tests PASS** (`bash tools/run_domain_tests.sh`, 391 base remota tras integrar ciclos 43–46 + 4 nuevos, 25 clases). Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `NaturalTaskParser.kt`, `NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: `cb042c7` — `fix(parser): "el 15" día del mes suelto con artículo (el 15 a las 10 → día 15, no hoy)` (incluye rebase sobre `a11cf48` + ajustes docs post-rebase).
- **HEAD final**: `cb042c7` (tras push a `origin/openhands/autonomous-ordia`).
- **Estado**: VERIFIED (JVM).

### Siguiente
- Parser: tiempo relativo con prefijo "de aquí a" / "de hoy en" / "para dentro de" (frases
  cotidianas aún no cubiertas); revisar interacción de `dayOfMonthPattern` con rango horario
  ("el 15 de 9 a 11") y con recurrencia mensual ("el 15 cada mes" — ya cubierto por `cada mes`).
- Salir del parser y auditar What Now, captura, rutinas, recordatorios, detección de vencidas.

## Ciclo 48 - 2026-08-13 (UTC) - fix(ux): planificador "Vence hoy" falso en planes de otra fecha

- **Run/ciclo**: 48 (base remota `52a4736` ciclo 47; `git pull --ff-only` sin divergencia).
- **HEAD inicial**: `52a4736` (origin/openhands/autonomous-ordia).
- **Problema seleccionado (P2, planificador/UX)**: `DayPlanner.planReason` etiquetaba como `DUE_TODAY`
  ("Vence hoy") a **toda** tarea con `dueAt`, ignorando la `date` del plan. El planificador se
  construye para la `selectedDate` del usuario (y `AutomationEngine.PLAN_DAY` usa hoy). Consecuencia:
  al abrir el plan de **mañana** u otra fecha futura, una tarea que vence ese día mostraba
  **"Vence hoy"** — urgencia falsa, confunde sobre qué vence realmente hoy. La etiqueta mentía ("hoy"
  cuando no lo era).
- **Prioridad**: P2 (UX/corrección de urgencia mostrada al usuario; sin pérdida de datos).
- **Causa raíz**: `planReason(task, now)` no recibía `date`/`zone`; el `when` mapeaba `dueAt != null`
  → `DUE_TODAY` incondicionalmente.
- **Solución (mínima, `DayPlanner.kt`)**: `planReason(task, now, date, zone)` compara la fecha de
  vencimiento con el día **real de hoy** (`DateRules.toLocalDate(now, zone)`): si coincide →
  `DUE_TODAY`; si no → nuevo `DUE_ON_DATE`. Así una tarea que vence hoy sigue `DUE_TODAY` aunque se
  vea en un plan de otra fecha (la urgencia real no cambia con la vista), y una que vence otro día
  no finge "hoy". Añadida rama UI `PlannerScreen.plannerReasonLabel` + string
  `planner_reason_due_on_date` = "Vence este día". Heurística honesta (no IA, no random). Sin nueva
  pantalla ni botón — solo precisión.
- **Tests**: +2 en `DayPlannerTest.kt`: `dueOnFuturePlanDateIsNotLabeledAsToday` (plan de mañana con
  tarea que vence mañana → `DUE_ON_DATE`, no `DUE_TODAY`), `dueTodayIsLabeledAsTodayEvenOnFuturePlanDate`
  (tarea que vence hoy vista en plan de mañana → `DUE_TODAY`). **399 domain tests PASS**
  (`bash tools/run_domain_tests.sh`, 25 clases — 393 base + 2 nuevos + 4 traídos por pull de ciclos
  45–47 ya contados en base). Smoke 25 NO ejecutado (sin `kotlinc` en PATH este run; libs presentes;
  el smoke es subconjunto del suite de dominio ya pasado).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `DayPlanner.kt`, `PlannerScreen.kt`, `strings_screens2.xml`,
  `DayPlannerTest.kt`, `AI_AUTONOMY/{CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: `1f04247` — `fix(ux): planificador no muestra "Vence hoy" en planes de otra fecha` (rebaseado sobre `8950d07` tras colisión con run paralelo IMMINENT_START; auto-merge limpio, cambios ortogonales).
- **HEAD final**: `1f04247` (tras push a `origin/openhands/autonomous-ordia`).
- **Estado**: VERIFIED (JVM). 406 domain tests PASS (393 base + 2 nuevos c.48 + 7 traídos por c.49 IMMINENT_START; rebase limpio).

### Siguiente
- Auditoría de motores no-parser: What Now (`WhatNowEngine`), captura, rutinas, recordatorios
  (reminder scheduling edge cases), detección de vencidas importantes, búsqueda universal.
- Planificador: revisar si otras etiquetas (OVERDUE, SCHEDULED_TIME) también dependen de `date` del
  plan vs hoy de forma inconsistente; evaluar mostrar la hora prevista real en conflictos.

## Ciclo 47 - 2026-08-13 (UTC) - fix(parser): "el 15" día del mes suelto con artículo

- **Run/ciclo**: 46 (base remota `2ef4bfa` ciclo 45; rama `openhands/autonomous-ordia` actualizada sin divergencia).
- **HEAD inicial**: `2ef4bfa` (origin/openhands/autonomous-ordia).
- **Problema seleccionado**: `GuardianEngine` contaba subtareas como tareas lógicas en sus agregados de progreso — el mismo doble conteo que `SummaryEngine` tuvo y se fijó en ciclo 20. `completedAll`, `completedToday`, `overdue` y `derivedExperience(completedTasks)` no filtraban `parentTaskId == null`. Consecuencia: un padre con 4 subtareas vencidas disparaba mood CONCERNED + mensaje "Hay 5 pendientes atrasados" (realidad: 1 tarea lógica); un padre con 3 subtareas completadas daba XP 48 en vez de 12. El guardia mentía al usuario sobre su propio progreso y ánimo.
- **Prioridad**: P1 (fiabilidad/datos: el guardia presenta información incorrecta sobre el estado del usuario).
- **Causa raíz**: los conteos agregados del guardia no replicaron el filtro `parentTaskId == null` que ya usan `SummaryEngine`, `GuardianCoach`, `WhatNowEngine` y `DayPlanner` para representar "tareas lógicas".
- **Solución (mínima, en `GuardianEngine.kt`)**: filtro `it.parentTaskId == null` en los 4 conteos (`completedAll`, `completedToday`, `overdue`, `derivedExperience.completedTasks`). Sin nueva pantalla, sin nueva interfaz — solo precisión.
- **Tests**: +2 en `GuardianEngineTest.kt`: `overdueCountIgnoresSubtasksToAvoidInflatedConcern` (1 padre vencido+4 subtareas → mood≠CONCERNED, mensaje sin "5"), `derivedExperienceCountsLogicalTasksNotSubtasks` (1 padre+3 subtareas completadas → 12 XP, no 48). Probe JVM independiente confirmó antes (mood=CONCERNED, XP=48) y después (mood=CURIOUS, XP=12). **392 domain tests PASS** (`bash tools/run_domain_tests.sh`, 25 clases — 390 base + 2 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `GuardianEngine.kt`, `GuardianEngineTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.

### Siguiente
- Descubrimiento continuo: auditar `WhatNowEngine`, captura, recordatorios, detección de vencidas importantes, búsqueda universal.- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).

## Ciclo 47 (run paralelo) - 2026-08-13 (UTC) - feat: nextBestTask alineado con IMMINENT_START (widget/asistente)
- **Run/ciclo**: 47.
- **HEAD inicial**: `15e170f` (remoto `966b799`, sin divergencia).
- **Problema seleccionado**: el ciclo 46 añadió detección de compromisos inminentes a `WhatNowEngine` (tarjeta What Now de `TodayScreen`), pero **NO** a `TaskRules.nextBestTask`, la heurística compartida por el **widget de inicio**, el **asistente** y el `nextTask` del ViewModel. Allí un compromiso inminente seguía cayendo en `isScheduledLater` (rank -1, último recurso): el widget sugería una tarea cualquiera de la Bandeja **mientras una reunión empezaba en 5 min**. La superficie más vista daba una respuesta menos oportuna que la pantalla principal.
- **Prioridad**: P1 (consistencia de inteligencia entre superficies; evitar olvido de compromiso inminente en widget).
- **Causa raíz**: `TaskRules.nextBestTask`/`timeRank` carecían de la rama inminente que `WhatNowEngine` ya tenía; además la lógica `isImminentStart`/`IMMINENT_WINDOW_MINUTES` estaba duplicada.
- **Solución (mínima, en `TaskRules.kt` + DRY)**: `isImminentStart(task, now)` + `IMMINENT_WINDOW_MINUTES = 15` movidos a `TaskRules` (públicos, fuente única de verdad); `WhatNowEngine.isImminentStart` reducido a un delegado, eliminando la constante duplicada. `TaskRules.timeRank` añade la rama inminente (rank 4) en el mismo orden honesto que `WhatNowEngine`: EN_CURSO > EN_PROGRESO > ATRASADA > INMINENTE > VENCE_HOY > URGENTE > ALTA > BANDEJA. Una tarea atrasada sigue ganando a un compromiso que aún no empieza. Retrocompatible: `nextBestTask(tasks)` sigue delegando con `now`/zona por defecto → `OrdiaWidgetProvider`, `AssistantEngine`, `OrdiaViewModel` compilan sin cambio. Sin nueva pantalla ni botón: misma heurística, consistente en todas las superficies.
- **Tests**: +3 (`nextBestTask_prefersImminentStartOverInbox`, `nextBestTask_startOutsideImminentWindowStaysDeprioritized`, `nextBestTask_overdueBeatsImminentStart`); las 4 pruebas de `WhatNowEngineTest` para IMMINENT_START siguen en verde (delegación sin cambio de comportamiento). **404 domain tests PASS** (`bash tools/run_domain_tests.sh`, 25 clases) tras rebase sobre los ciclos 47 "el 15" + 46 guardian del otro run (base 398 + 4 parser + 2 guardian). Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales, render real del widget (sin Android SDK).
- **Archivos modificados**: `TaskRules.kt`, `WhatNowEngine.kt`, `TaskRulesTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `f406365` (rebasado sobre `52a4736` del otro run: ciclos 47 "el 15" + 46 guardiÃ¡n; push pendiente a `origin/openhands/autonomous-ordia`).

### Siguiente
- Descubrimiento continuo: auditar `GuardianCoach`, `SummaryService`, `PlanEngine`, detección de vencidas importantes, búsqueda universal.
- Parser: manejo robusto de múltiples marcadores temporales en una frase.
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).
