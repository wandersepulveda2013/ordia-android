# CURRENT_STATE — Ordía

> Actualizar AL FINAL de cada sesión autónoma.

## Estado

- **Fecha/hora (UTC)**: 2026-08-11 (sesión OpenHands 004 — autonomía nocturna, ciclos 1-4)
- **Branch de trabajo**: `jules/autonomous-ordia` (HEAD inicial `35fb204`, final `a48c5d7`)
- **main**: contiene SOLO infraestructura de orquestación (workflows), no el rebuild
- **Workflow autónomo (scheduler)**: `.github/workflows/ordia-autonomous-jules.yml` en `main` (cron `17 */2 * * *` + dispatch)
- **Auto-merge**: `.github/workflows/ordia-autonomous-merge.yml` en `main` (pull_request_target + cron `*/15 * * * *` + dispatch)

## Último trabajo realizado

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
- **Verificación JVM**: 182 tests del dominio PASS (25 clases); smoke 25 assertions OK.
- `./gradlew test/lint/assemble`: sigue NO VERIFICADO (sin Android SDK en el entorno).

## Áreas modificadas

- intelligence, privacy/IME, context (external/audit), automation, domain (parser + notes),
  ui/screens, shortcuts/quicksettings, backup, manifest/DI/datos/servicios, strings (i18n).

## Tests ejecutados

- **NO VERIFICADO (gradle/Android)**: no se ejecutó `./gradlew test`/`lint`/`assemble` (sin Android SDK).
- **VERIFICADO (JVM/kotlinc)**: `bash tools/run_domain_tests.sh` → 182 tests OK (25 clases);
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
  pendiente "de 18 a 20".)
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

- Ciclo 9 ejecutado: fix del parser "a primera hora" (no generaba recordatorio + residuo en
  título; ahora → 09:00 como fallback y título limpio). 4 tests nuevos, 182 tests OK. Continuar
  autonomía: ciclo 10 candidato a rango horario "de 18 a 20" (evaluar impacto real: ¿rango
  vs. hora única aporta utilidad? Ordía usa `dueAt` único); nueva auditoría funcional
  (captura/What Now/inteligencia/priorización) o UX (Onboarding responsive, NoteEditor
  `rememberSaveable` P2/P3, atomicidad de `saveRoutine` P3, deprecación de iconos, i18n).
  Verificación Gradle/Android pendiente.

## PR pendiente

- Ninguno (el auto-merge gestiona las PRs autónomas hacia `jules/autonomous-ordia`).

## Estado CI

- `android-ci.yml` activo en `main` y en la rama autónoma; verify corre en push/PR hacia ambas.
- Pendiente la primera ejecución del ciclo autónomo real (requiere `JULES_API_KEY`).
