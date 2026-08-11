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
- **Verificación JVM**: 155 tests del dominio PASS (25 clases); smoke 25 assertions OK.
- `./gradlew test/lint/assemble`: sigue NO VERIFICADO (sin Android SDK en el entorno).

## Áreas modificadas

- intelligence, privacy/IME, context (external/audit), automation, domain (parser + notes),
  ui/screens, shortcuts/quicksettings, backup, manifest/DI/datos/servicios, strings (i18n).

## Tests ejecutados

- **NO VERIFICADO (gradle/Android)**: no se ejecutó `./gradlew test`/`lint`/`assemble` (sin Android SDK).
- **VERIFICADO (JVM/kotlinc)**: `bash tools/run_domain_tests.sh` → 155 tests OK (25 clases);
  `bash tools/run_domain_checks.sh` → 25 assertions OK.

## Problemas conocidos

- Warnings de deprecación no bloqueantes (ej. `Icons.Outlined.InsertDriveFile` → AutoMirrored) — ver BACKLOG.
- `NoteBlocks.kt` y `TaskSnapshotCodec.kt` (dominio) dependen de `org.json` (API Android); en tests
  se sustituye por `org.json:json:20231013` real. Acoplamiento del dominio a Android, pero funcional.
- Tests de `backup`/`context`/`repositories` requieren DAOs/RoomDatabase/Context (no ejecutables en
  JVM pura sin Robolectric/Android SDK); no verificados.
- Parser: ~~números escritos en expresiones relativas ("en dos horas") no parseados (P2)~~ RESUELTO (ciclo 4).
- NoteEditor: `blocks` (mutableStateListOf) no es `rememberSaveable`; si el proceso muere dentro
  de la ventana de autosave (800 ms) se pierden los últimos cambios de bloques (el `title` sí
  sobrevive). Tradeoff de debounce, no corregido en esta sesión (P2/P3).
- El workflow Jules necesita `jules/autonomous-ordia` visible en la API de Sources antes de lanzar.
- El auto-merge requiere `secrets.JULES_API_KEY` configurado y checks exitosos.

## Bloqueos

- Ninguno activo. El único paso que requiere al humano: configurar `secrets.JULES_API_KEY` y
  arrancar el primer ciclo manual (workflow_dispatch).

## Siguiente tarea recomendada

- Continuar autonomía: Ciclo 5 = auditoría de Onboarding (caber en pantallas pequeñas, botones
  accesibles, sin scroll imposible) y responsive. Después: NoteEditor `rememberSaveable` (P2/P3),
  atomicidad de `saveRoutine` (P3), ítems P2 (deprecación de iconos, i18n, QA de variantes).
  La verificación de Gradle/Android queda pendiente hasta que exista un entorno con SDK.

## PR pendiente

- Ninguno (el auto-merge gestiona las PRs autónomas hacia `jules/autonomous-ordia`).

## Estado CI

- `android-ci.yml` activo en `main` y en la rama autónoma; verify corre en push/PR hacia ambas.
- Pendiente la primera ejecución del ciclo autónomo real (requiere `JULES_API_KEY`).
