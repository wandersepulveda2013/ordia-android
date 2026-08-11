# CURRENT_STATE — Ordía

> Actualizar AL FINAL de cada sesión autónoma.

## Modo continuo (supervisor persistente) — 2026-08-11

- **Arquitectura de continuidad real**: se añadió `tools/ordia_supervisor.py` (+ `ordia_supervisor.sh`,
  `SUPERVISOR.md`). Un proceso persistente en una máquina siempre encendida del usuario orquesta la
  Automation `Ordía Continuous Evolution` (id `b3bd3870-…`), garantizando `MAX_CONCURRENT_RUNS=1` y
  encadenando runs en **~15–40 s** (no horas).
- **Hallazgo**: el cron del automation service dispatcha ciegamente sin comprobar runs activos →
  se detectaron **2 runs concurrentes** (violaba MAX_CONCURRENT=1). El supervisor lo resuelve
  deshabilitando el cron al arrancar y rehabilitándolo al detenerse (watchdog de seguridad).
- **Timeout** de la automation subido 600→**1800 s**: los runs marcados "FAILED" por timeout
  igual commiteaban+pusheaban antes de morir (el corte era prematuro).
- **Sin supervisor**: el cron cada 15 min es modo degradado (huecos hasta 15 min, concurrencia
  ocasional). **Con supervisor**: continuidad de segundos, 1 agente. Ver `tools/SUPERVISOR.md`.

## Estado

- **Fecha/hora (UTC)**: 2026-08-11 (sesión OpenHands — autonomía, ciclo 20)
- **Branch de trabajo**: `openhands/autonomous-ordia` (base `cd80eb0`; merge de 2 runs concurrentes: SummaryEngine+SearchEngine y parser semanal)
- **main**: contiene SOLO infraestructura de orquestación (workflows), no el rebuild
- **Workflow autónomo (scheduler)**: `.github/workflows/ordia-autonomous-jules.yml` en `main` (cron `17 */2 * * *` + dispatch)
- **Auto-merge**: `.github/workflows/ordia-autonomous-merge.yml` en `main` (pull_request_target + cron `*/15 * * * *` + dispatch)

## Último trabajo realizado

- **Sesión OpenHands — Ciclo 20, Unidad 3 (Rutinas — duplicados al re-disparar tras completar)**:
  P1 corregido — `RoutineRules.wasRunToday` solo contaba tareas **pendientes** creadas hoy. Al
  completar todas las tareas de la rutina de hoy, `wasRunToday` devolvía `false` y un nuevo
  disparo de `runRoutine` (reabrir app, worker, o tap manual) volvía a añadir los pasos →
  **tareas duplicadas** en la bandeja justo cuando el usuario había sido productivo. El guardia
  anti-duplicados se derrotaba a sí mismo. Fix: una tarea creada hoy cuenta como "rutina ya
  ejecutada hoy" aun si está completada (solo archivado/cancelado no bloquean). El test
  `wasRunTodayFalseWhenTaskCompleted` codificaba el bug como deseado; se corrigió a
  `wasRunTodayTrueWhenCreatedTodayAndCompleted` + `wasRunTodayTrueWhenTodayBatchPartiallyCompleted`.
  Tests: **224 domain tests OK, smoke 25 OK.** NO VERIFICADO: gradle/Android/ViewModel real.

- **Sesión OpenHands — Ciclo 20, Unidad 2 (SummaryEngine / DayPlanner — coherencia de minutos)**:
  P2 corregido — la badge "Xm" de la pantalla Today (`SummaryEngine.remainingMinutesToday`)
  sumaba `durationMinutes` en bruto mientras `DayPlanner` coerciona cada tarea a `coerceIn(10,180)`.
  El headline y el plan del día discrepaban: una tarea de 600m mostraba "600m" pendientes pero el
  plan solo agenda 180m; una tarea con duración por defecto 5m contaba 5m cuando el plan la trataba
  como 10m. Fix: fuente única de verdad `TaskRules.plannedDuration` (constantes
  `MIN_PLAN_MINUTES=10` / `MAX_PLAN_MINUTES=180`), usada por `DayPlanner.build` y
  `SummaryEngine.summarize`. WhatNowEngine intacto (su `coerceAtLeast(10)` detecta "sigue en curso",
  interés distinto). Tests: +2 (coerce 600→180 y 5→10; coherencia plan↔resumen). **223 domain tests OK,
  smoke 25 OK.** NO VERIFICADO: gradle/lint/Android/Room.

- **Sesión OpenHands — Ciclo 20a (auditoría funcional no-parser)**: 2 bugs P1 corregidos.
  (1) **SummaryEngine** — `dailySummary` contaba `completedToday`/`completedWeek`/`dueToday`
  sobre TODAS las tareas (incluidas subtareas), inflando el resumen al completar un padre
  con subtareas (auto-completadas en cascada daban N+1 en vez de 1). Fix: filtrar
  `parentTaskId == null` en los conteos del snapshot (consistente con WhatNowEngine y
  AutomationActionPlanner). Test `summaryCountsOnlyRootTasks`. Commit `7127b7e`.
  (2) **SearchEngine** — la búsqueda "nota X" no encontraba notas cuyo contenido no
  contuviera la palabra "nota" (las notas casi nunca la incluyen). Asimetría con
  tareas/conversaciones/compromisos que ya tenían `semanticMatches`. Fix: `NOTE_TERMS` set
  + `semanticMatches` fallback para notes. Test
  `noteTypeFilterDoesNotRequireTheWordNotaInContent`. Commit `c1bab04`.
  Auditoría completa del resto de motores no-parser (RecurrenceEngine, TaskRules,
  DayPlanner, QuietHours, RoutineRules, HabitRules, GuardianEngine, LearningEngine,
  SubtaskRules, WhatNowEngine, DateRules, TaskSnapshotCodec, UniversalCaptureEngine,
  FocusTimerRules, ReminderSync, AutomationRules, AutomationEngine, AutomationUndoRules)
  **sin hallazgos P0/P1**: el trabajo previo es sólido. **222 domain tests OK, smoke 25 OK.**

- **Sesión OpenHands — Ciclo 20b (NaturalTaskParser — recurrencia semanal de varios días)**:
  P1 corregido — pérdida de datos silenciosa en rutinas semanales con varios días. La forma
  más común en español ("reunión los lunes y jueves") NO se parseaba: solo el patrón
  `cada X y Z` admitía dos días; `todos los X`, `los X` capturaban **un solo día** y dejaban
  "y jueves" como residuo en el título (probado: "reunión los lunes y jueves a las 10" →
  `title='reunión y'`, `recurrenceDays='1'` — perdía el jueves y la rutina solo repetía lunes).
  Una tarea recurrente que pierde días es una promesa rota al usuario (cita/reunión que no
  aparece en los días correctos). Fix: se unificaron los 3 patrones `weeklyDayPatterns` en
  uno solo `dayListPattern` que captura una **lista de días** separados por `,` o `y` y los
  extrae con `dayNameRegex.findAll`. Menos código, más capacidad: además de "los X y Z" ahora
  soporta listas con comas ("lunes, miércoles y viernes"). No rompe casos existentes (todos
  los viernes → 5; cada lunes y jueves → 1,4). Tests: +3 (`parsesLosWeekdaysWithY`,
  `parsesTodosLosWeekdaysWithY`, `parsesCommaDayList`). **221 domain tests OK, smoke 25 OK.**
  NO VERIFICADO: gradle/lint/Android/Room (sin Android SDK).

- **Sesión OpenHands — Ciclo 19 (NaturalTaskParser — anclaje de recurrencias de intervalo)**:
  P1 corregido — recurrencias de **intervalo** (diaria "cada día", quincenal "cada 2 semanas",
  mensual/anual sin día explícito "cada mes"/"cada año") se creaban con `dueAt=null` porque el
  bloque `when` de resolución de fecha solo anclaba recurrencias con día explícito (semanal con
  días, mensual con día del mes). Resultado: una tarea recurrente de intervalo era **invisible**
  — no aparecía en What Now/planificador, su recordatorio **nunca disparaba** (ReminderSync usa
  `reminderAt ?: dueAt`, ambos null) y se olvidaba hasta su primer completado (recién entonces
  RecurrenceEngine infería el siguiente desde `completedAt`). Simétrico al bug mensual de los
  ciclos 17 (parser) y 18 (engine). Fix: nuevo caso `recurrence.frequency != NONE ->
  base.toLocalDate()` que ancla la primera ocurrencia a la **fecha de captura** cuando ninguna
  fecha explícita se resolvió antes en el `when`. Las fechas explícitas (hoy/mañana/día de
  semana/día del mes) siguen teniendo prioridad porque se evalúan antes. Para "cada día a las 8"
  el ancla es hoy + la hora explícita. RecurrenceEngine produce la próxima ocurrencia coherente
  (hoy + intervalo). Tests: actualizados `parsesDailyRecurrence`/`parsesIntervalRecurrence`
  (de `assertNull(dueAt)` a `assertEquals(fecha de captura)`), +3 tests nuevos (diaria con hora,
  anual, intervalo con fecha explícita preserva la fecha). **218 domain tests OK, smoke 25 OK.**
  NO VERIFICADO: gradle/lint/Android/Room (sin Android SDK).

- **Sesión OpenHands — Ciclo 18 (RecurrenceEngine — anclaje mensual)**: P1 corregido — el
  avance de recurrencia mensual usaba `base.plusMonths(interval)` que **clampa** los días
  29-31 al último día del mes destino (ene 31 → feb 28), derivando el ancla de "el 31 de
  cada mes" (31→30→30…) y siendo **inconsistente** con `NaturalTaskParser.nextMonthlyDate`
  (que salta los meses sin el día). La nueva funcionalidad del ciclo 17 (parser mensual
  anclado) quedaba a medias: el parser generaba la primera fecha correcta, pero al
  completarla el engine rompía la promesa del día. Fix: `RecurrenceEngine.nextMonthly`
  ancla al `base.dayOfMonth`, busca el primer mes (desde `base+interval`) que contenga ese
  día (`YearMonth.lengthOfMonth`), conservando hora y zona. Días 1-28 (caso más común,
  "el 15 de cada mes") idéntico; solo cambian días 29-31, ahora correctos y coherentes con
  el parser. No requiere migración de esquema: el ancla se infiere del `dueAt` de la tarea
  completada. 3 tests nuevos (215 domain tests OK, smoke 25 OK). Commit `c709e26`.

- **Sesión OpenHands 004 — Ciclo 1 (NaturalTaskParser)**: 3 bugs P1 corregidos (fecha numérica
  pasada, esta noche/tarde/mañana, urgente inicial), 11 tests de regresión. Commit `fb53e8c`.
- **Sesión OpenHands 004 — Ciclo 2 (auditoría persistencia + recordatorios + seguridad)**: inspección
  estática completa de Entities/DAOs/OrdiaDatabase/BackupStore/repositories/toggleTask+RecurrenceEngine/
  ReminderScheduler/TaskReminderWorker/ReminderActionReceiver/ReminderResyncReceiver/AndroidManifest.
  **Sin hallazgos P0/P1**: el trabajo previo de Jules en estas áreas es sólido (backup atómico,
  cascadas correctas, mutex de mutación, recordatorios no exportados, quiet hours, permisos).
  Registrado en BACKLOG como auditorías OK.
- **Sesión OpenHands 004 — Ciclo 2b (NoteBlockCodec)**: P1 data-loss corregido — un elemento
  malformado en el array de bloques hacía perder TODOS los bloques. Fix: parseo por elemento,
  descartar malformados, conservar válidos. 11 tests nuevos (sin cobertura previa). Commit `2ae258a`.
  Además se añadió `tools/run_domain_tests.sh` (runner JUnit4 reutilizable).
- **Sesión OpenHands 004 — Ciclo 3 (recordatorios)**: inspección estática de ReminderScheduler/
  TaskReminderWorker/ReminderActionReceiver/ReminderResyncReceiver. Sin P0/P1 (worker re-lee
  task, filtra estados terminales, quiet hours, receiver no exportado). Commit doc `78b4ef4`.
- **Sesión OpenHands 004 — Ciclo 4 (rutinas + BUG3)**: inspección de RoutineRules/runRoutine/
  undo. Sin P0/P1 (undo real y testeado vía AutomationUndoRules; dedup wasRunToday correcto;
  orden preservado por sortOrder). Menor P3: `saveRoutine` no transaccional (registrado en
  backlog). Resuelto BUG3 (P2): parser ahora reconoce números escritos en tiempo relativo
  ("en dos horas", "dentro de tres días", "en una hora") y el introductor "dentro de".
  8 tests nuevos. Commit `a48c5d7`.
- **Sesión OpenHands 004 — Ciclo 5 (NaturalTaskParser)**: P1 corregido — "de la tarde/noche/mañana"
  como meridiem ignorado ("a las 4 de la tarde" → 04:00); "al mediodía"/"a la medianoche" y
  "esta mañana" dejaban restos en el título (orden de limpieza). 8 tests nuevos. Commit `4a20688`.
- **Sesión OpenHands 004 — Ciclo 6 (NaturalTaskParser)**: P1 corregido — contexto PM de parte del
  día NO se aplicaba a hora sin meridiem ("esta tarde a las 4" → 04:00 AM en vez de 16:00;
  "esta noche a las 9" → 09:00 AM en vez de 21:00; "mañana a la tarde" → 09:00 + "a la tarde"
  en título); "12 de la noche" → 12:00 (mediodía) en vez de 00:00 (medianoche); "de la madrugada"
  no reconocido. Fix quirúrgico: `standalonePartOfDayPattern`, `hasPartOfDayPmContext`,
  `explicitTime` emite `Pair<LocalTime,Boolean>`, contexto PM a hora sin meridiem. 6 tests
  nuevos. Commit `4f43c0b`.
- **Sesión OpenHands 004 — Ciclo 7 (NaturalTaskParser)**: "a las 24" / "24:00" no se reconocía
  como medianoche (regex de hora `2[0-3]` rechazaba 24) → `dueAt=null` (info-loss: tarea sin
  recordatorio) y basura "a las 24" en el título. Fix: regex `2[0-3]`→`2[0-4]`; `hour==24`→
  `LocalTime.MIDNIGHT` con meridiem explícito (bloquea offset PM). 3 tests nuevos. 172 tests OK.
- **Sesión OpenHands 004 — Ciclo 8 (NaturalTaskParser)**: `weekdayPattern` no capturaba prefijo
  `del`/`de` ni sufijo `que viene`/`próximo(s|a)` → residuos en título MUY comunes: "reunión del
  jueves"→"reunión del", "el viernes que viene"→"...que viene", "el miércoles próximo"→"...próximo".
  Fix: regex extendido con prefijo `el|del|de` y sufijo `que viene|próximo(s|a)`; group 1 sigue
  siendo el día (no rompe toDayOfWeek/parseRecurrence). 6 tests nuevos. 178 tests OK.
- **Sesión OpenHands 004 — Ciclo 9 (NaturalTaskParser)**: "a primera hora" (y "...de la
  mañana/madrugada") no tenía patrón → no asignaba hora (`dueAt=null`, sin recordatorio salvo
  otra fecha) y dejaba residuo "a primera hora" en el título. Frase cotidiana ("ir al dentista
  mañana a primera hora") quedaba incompleta. Fix: `primeraHoraPattern` + `primeraHoraTime`
  (09:00) como fallback de `parsedTime` (después de hora explícita y partes del día); limpieza
  del título tras `standalonePartOfDayPattern`. 4 tests nuevos. 182 tests OK.
- **Ciclo 10 (NaturalTaskParser)**: P1 — "N min antes" clasificado como duración, no recordatorio
  (recordatorio perdido). Patrón reminder #2 ampliado para aceptar abreviatura `min`/`hora`.
  2 tests nuevos. 184 tests OK.
- **Ciclo 11 (NaturalTaskParser)**: P2 — etiquetas explícitas `#cat`/`@cat` no se reconocían:
  quedaban como residuo en el título y `@trabajo` se ignoraba (categoría inferida por keywords,
  a veces mal: "Llamar a Ana @trabajo" → cat=personal). Fix: `explicitCategoryPattern`
  (construido de `categories`, solo categorías conocidas) con prioridad sobre la inferencia
  por keywords; la etiqueta reconocida se limpia del título. Etiquetas desconocidas
  (`#proyecto`) se conservan como contenido del usuario. 5 tests nuevos. 189 tests OK.
- **Ciclo 12 (NaturalTaskParser)**: P2 — "Nh" compacto ("Trabajar 2h", "Estudiar 1h") no se
  reconocía como duración: quedaba como residuo en el título y `durationMinutes=null`. Caso de
  captura rápida muy común. Fix: patrón `\b(\d{1,3})\s*(h)\b` al final de `durationPatterns`
  (el `\b` final evita casar "2horas"); detección de unidad ampliada (`h`→horas).
  4 tests nuevos (2h/1h compactos, 2horas intacto, 2h+recordatorio sin interferencia).
  193 tests OK.
- **Ciclo 13 (NaturalTaskParser)**: P2 — "urgente"/"importante" como sufijo final no fijaba
  prioridad ("Llamar mamá urgente" → NORMAL). El usuario expresa prioridad como palabra final
  en texto libre; no se detectaba para evitar falsos positivos de mitad de frase. Fix:
  `trailingPriorityPattern` (`\b(urgente|importante)\b\s*[.!?]?$`) → URGENT/HIGH, con guard
  de negación `negatedPriorityPattern` ("no es/era/fue/parece urgente") → NORMAL. La palabra
  reconocida se limpia del título. 4 tests nuevos. 197 tests OK.
- **Verificación JVM**: 197 tests del dominio PASS (25 clases); smoke 25 assertions OK.
- `./gradlew test/lint/assemble`: sigue NO VERIFICADO (sin Android SDK en el entorno).

## Áreas modificadas

- intelligence, privacy/IME, context (external/audit), automation, domain (parser + notes),
  ui/screens, shortcuts/quicksettings, backup, manifest/DI/datos/servicios, strings (i18n).

## Tests ejecutados

- **NO VERIFICADO (gradle/Android)**: no se ejecutó `./gradlew test`/`lint`/`assemble` (sin Android SDK).
- **VERIFICADO (JVM/kotlinc)**: `bash tools/run_domain_tests.sh` → 221 tests OK (25 clases);
  `bash tools/run_domain_checks.sh` → 25 assertions OK.

## Problemas conocidos

- Warnings de deprecación no bloqueantes (ej. `Icons.Outlined.InsertDriveFile` → AutoMirrored) — ver BACKLOG.
- `NoteBlocks.kt` y `TaskSnapshotCodec.kt` (dominio) dependen de `org.json` (API Android); en tests
  se sustituye por `org.json:json:20231013` real. Acoplamiento del dominio a Android, pero funcional.
- Tests de `backup`/`context`/`repositories` requieren DAOs/RoomDatabase/Context (no ejecutables en
  JVM pura sin Robolectric/Android SDK); no verificados.
- Parser: ~~números escritos en expresiones relativas ("en dos horas") no parseados (P2)~~ RESUELTO (ciclo 4).
- Parser: ~~"de la tarde/noche/mañana" como meridiem ignorado (hora AM errónea); "al mediodía"/"a la medianoche" y "esta mañana" dejaban restos en el título~~ RESUELTO (ciclo 5, 8 tests nuevos).
- Parser: ~~contexto PM de parte del día no aplicado a hora sin meridiem; "12 de la noche"=mediodía; "de la madrugada" no reconocido~~ RESUELTO (ciclo 6, 6 tests nuevos).
- Parser: casos límite menores P3 abiertos: "salir de madrugada" (sin "a las"/"a la") no reconocido;
  "a las 3.5" → ".5" suelto. Ver BACKLOG. (~~"a las 24" → null~~ RESUELTO ciclo 7.)
- Parser: residuos de título NO limpiados detectados en probe ciclo 7: "a primera hora" sin
  interpretar/limpiar; "que viene" tras fecha ("el viernes que viene"); "del" huérfano
  ("a las 3pm del jueves"); rango horario "de 18 a 20" no parseado. Candidatos a ciclo 8.
  (~~"que viene" y "del" huérfano~~ RESUELTO ciclo 8; ~~"a primera hora"~~ RESUELTO ciclo 9;
  ~~"de 18 a 20"~~ RESUELTO ciclo 16.)
- NoteEditor: `blocks` (mutableStateListOf) no es `rememberSaveable`; si el proceso muere dentro
  de la ventana de autosave (800 ms) se pierden los últimos cambios de bloques (el `title` sí
  sobrevive). Tradeoff de debounce, no corregido en esta sesión (P2/P3).
- El workflow autónomo (Jules/OpenHands) opera sobre `openhands/autonomous-ordia` en esta sesión;
  la memoria histórica referenciaba `jules/autonomous-ordia`. Auto-merge requiere
  `secrets.JULES_API_KEY` configurado y checks exitosos.

## Bloqueos

- Ninguno activo. El único paso que requiere al humano: configurar `secrets.JULES_API_KEY` y
  arrancar el primer ciclo manual (workflow_dispatch).

## Siguiente tarea recomendada

- Ciclo 12 ejecutado: fix P2 duración compacta "Nh" ("Trabajar 2h" → 120 min). Patrón
  `\b(\d)\s*h\b` + unit check "h"→horas; 4 tests nuevos, 193 OK, smoke 25 OK. Pendientes de
  la auditoría ciclo 10-11: `prioridad alta:`/`urgente`/`importante` a mitad de frase no
  fijan prioridad (P2), residuo "de" en "Reunión de 30 minutos" (P3), rango "de 18 a 20"
  → dueAt=null (P3). Continuar autonomía: prioridad a mitad de frase (P2, alto valor —
  "Llamar mamá urgente" debería ser HIGH) o nueva auditoría funcional.

  ACTUALIZACIÓN ciclo 13: fix P2 prioridad por sufijo final ("urgente"/"importante" como
  palabra final → URGENT/HIGH, con guard de negación). 4 tests nuevos, 197 OK, smoke 25 OK.
  Pendientes: residuo "de" en "Reunión de 30 minutos" (P3), rango "de 18 a 20" → dueAt=null
  (P3). Continuar: nueva auditoría funcional o siguiente P3 con valor real.

  ACTUALIZACIÓN ciclo 14: fix P2 duraciones fraccionarias sin dígitos ("media hora" → 30 min,
  "(un) cuarto de hora" → 15 min). Antes no casaban con patrones de dígitos y dejaban residuo
  en el título + `durationMinutes`=null. Patrón `fractionalDurationPattern` con guard "hora"
  para no confundir "cuarto"=habitación. 5 tests nuevos, 202 OK, smoke 25 OK. Pendientes:
  residuo "de" en "Reunión de 30 minutos" (P3), rango "de 18 a 20" → dueAt=null (P3).

  ACTUALIZACIÓN ciclo 15 (auditoría funcional no-parser — persistencia/rutinas): fix P3
  atomicidad de `saveRoutine`. El guardado de una rutina hacía delete-then-reinsert de los
  pasos SIN transacción: un crash/kill del proceso entre el `deleteStep` (por cada paso) y
  los `addStep` dejaba la rutina con pasos parciales o sin pasos → pérdida de trabajo del
  usuario. Fix: `RoutineStepDao.replaceSteps(routineId, steps)` con `@Transaction`
  (deleteByRoutine + insert por paso), expuesto en `RoutineRepository.replaceSteps`, y
  `saveRoutine` ahora construye la lista de pasos y los reemplaza atómicamente. Limpia además
  un patrón frágil (leía pasos desde `uiState` en memoria en vez de la fuente de verdad).
  202 domain tests OK, smoke 25 OK. NO VERIFICADO: integración DAO/Room requiere Android SDK.

  ACTUALIZACIÓN ciclo 16 (NaturalTaskParser — duraciones/rangos): tres bugs P3 de captura
  rápida corregidos. (1) Rango horario "de H1 a H2 [horas]" no se parseaba como duración
  ("Cita de 18 a 20" → dur=null, "Cita de 18 a 20" en título; "Clase de 18 a 20 horas" →
  dur=1200 falso, el segundo número se robaba como 20h). (2) Conector "de" antes de una
  duración numérica quedaba como residuo en el título ("Reunión de 30 minutos" → "Reunión
  de"; "Juntada de 2 horas" → "Juntada de"). Fix: `timeRangePattern` procesa primero el
  rango (dur=(H2-H1)*60) con guard anti-falso-positivo (requiere unidad final o alguna
  hora≥13, así "comprar de 2 a 5 entradas" no se toca); la duración numérica posterior ahora
  arrastra el conector "de " cuando lo precede. 7 tests nuevos, 209 OK, smoke 25 OK.
  NO VERIFICADO: gradle/lint/Android (sin Android SDK).

  ACTUALIZACIÓN ciclo 17 (NaturalTaskParser — mensual anclado): P1 corregido — recurrencia
  mensual anclada a día del mes ("el 15 de cada mes") NO se parseaba. `parseRecurrence`
  reconoce el patrón `N de/del (cada) mes(es)`, ancla la primera ocurrencia al próximo día N
  (inclusive si es hoy; avanza de mes si el día no existe) vía `nextMonthlyDate`. Fix
  lateral: `monthNameDate` se computa como fecha resuelta (no basta con que la regex "mes"
  coincida) para que "8 de la manana" no cree la falsa fecha "8 de la". 3 tests nuevos,
  212 OK, smoke 25 OK.

  ACTUALIZACIÓN ciclo 18 (RecurrenceEngine — anclaje mensual consistente con parser): P1
  corregido — el avance mensual usaba `base.plusMonths` que clampa días 29-31 (ene 31 →
  feb 28), derivando el ancla de "el 31 de cada mes" e inconsistente con el parser. Fix:
  `nextMonthly` ancla al día del mes y salta los meses que no lo contienen, conservando
  hora y zona. Días 1-28 idéntico; solo cambian 29-31, ahora correctos. 3 tests nuevos,
  215 OK, smoke 25 OK. Cierra el ciclo completo de la funcionalidad mensual del ciclo 17.

  ACTUALIZACIÓN ciclo 19 (NaturalTaskParser — anclaje de recurrencias de intervalo): P1
  corregido — recurrencias de **intervalo** sin fecha explícita (diaria "cada día",
  quincenal "cada 2 semanas", mensual/anual "cada mes"/"cada año") dejaban `dueAt=null`
  porque el `when` de resolución de fecha solo anclaba recurrencias con día explícito. La
  primera ocurrencia era invisible (no en What Now; recordatorio nunca disparaba). Fix:
  nuevo caso `recurrence.frequency != NONE -> base.toLocalDate()` al final del `when`
  ancla la primera ocurrencia a la fecha de captura, respetando fechas explícitas previas.
  Simétrico al fix mensual de los ciclos 17-18. 2 tests actualizados (de `assertNull`
  a fecha de captura) + 3 nuevos (diaria con hora, anual, intervalo con fecha explícita).
  218 OK, smoke 25 OK.

  ACTUALIZACIÓN ciclo 20 (NaturalTaskParser — recurrencia semanal de varios días): P1
  corregido — pérdida de datos silenciosa en rutinas semanales. "los lunes y jueves"
  (la forma natural más común en español) solo capturaba un día y dejaba "y jueves" en el
  título → la rutina repetía solo el primer día. Fix: unificación de los 3 patrones
  `weeklyDayPatterns` en un `dayListPattern` que captura una lista de días (separados por
  `,` o `y`) extraída con `dayNameRegex.findAll`. Menos código, más capacidad (soporta
  listas con comas). +3 tests (`parsesLosWeekdaysWithY`, `parsesTodosLosWeekdaysWithY`,
  `parsesCommaDayList`). 221 OK, smoke 25 OK.

  ACTUALIZACIÓN ciclo 20 — Unidad 2 (SummaryEngine/DayPlanner — coherencia de minutos):
  P2 de consistencia corregido. La badge "Xm" de Today sumaba `durationMinutes` en bruto
  mientras el plan del día coerciona a 10..180 → el resumen y el plan discrepaban (tarea
  de 600m mostraba 600m pero el plan solo agenda 180m; tarea por defecto 5m contaba 5m
  cuando el plan la trataba como 10m). Fix: fuente única de verdad
  `TaskRules.plannedDuration` (constantes `MIN_PLAN_MINUTES=10`/`MAX_PLAN_MINUTES=180`),
  usada por DayPlanner.build y SummaryEngine.summarize. WhatNowEngine intacto (su
  `coerceAtLeast(10)` es para detección de en-curso, interés distinto). +2 tests
  (coerce de 600→180 y 5→10; coherencia plan↔resumen en día despejado). 223 OK, smoke 25 OK.

## PR pendiente

- Ninguno (el auto-merge gestiona las PRs autónomas hacia `jules/autonomous-ordia`).

## Estado CI

- `android-ci.yml` activo en `main` y en la rama autónoma; verify corre en push/PR hacia ambas.
- Pendiente la primera ejecución del ciclo autónomo real (requiere `JULES_API_KEY`).
