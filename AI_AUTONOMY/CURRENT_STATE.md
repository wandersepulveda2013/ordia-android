# CURRENT_STATE — Ordía

> Actualizar AL FINAL de cada sesión autónoma.

## Estado

- **Fecha/hora (UTC)**: 2026-08-11 (sesión OpenHands 003 — audit+fix dominio)
- **Branch de trabajo**: `jules/autonomous-ordia` (HEAD inicial `ecd6151`)
- **main**: contiene SOLO infraestructura de orquestación (workflows), no el rebuild
- **Workflow autónomo (scheduler)**: `.github/workflows/ordia-autonomous-jules.yml` en `main` (cron `17 */2 * * *` + dispatch)
- **Auto-merge**: `.github/workflows/ordia-autonomous-merge.yml` en `main` (pull_request_target + cron `*/15 * * * *` + dispatch)

## Último trabajo realizado

- **Sesión OpenHands 003 (esta ejecución)**: auditoría y fix de la verificación estática de dominio.
  - Se descubrió que `tools/run_domain_checks.sh` (smoke de dominio) ESTABA ROTO: `DomainSmoke.kt`
    comparaba `SearchKind.entries.toSet()` (7 kinds tras ampliar `SearchKind` con CONVERSATION,
    COMMITMENT, AUTOMATION) pero el smoke solo alimenta 4 listas (tasks/proyectos/notas/hábitos),
    así que el assertion "Universal search failed" siempre fallaba. El test unitario
    `SearchEngineTest` ya usaba el set correcto de 4 kinds core; el smoke quedó obsoleto.
  - Fix: alinear el smoke con `SearchEngineTest` (set explícito de TASK/PROJECT/NOTE/HABIT).
  - Verificación JVM (sin Android SDK, usando kotlinc + JUnit4 + stubs de Room/Preferences):
    `bash tools/run_domain_checks.sh` → 25 assertions OK; 125 tests del dominio OK
    (DateRules, DayPlanner, FocusTimer, Guardian, Habit, NaturalTaskParser, Onboarding,
    PlannerCalendar, QuietHours, Recurrence, ReminderSync, Routine, Search, Subtask,
    Summary, TaskRules, TaskSnapshotCodec, UniversalCapture, WhatNow, CommandPalette, etc.).
  - Se añadieron stubs JVM (`tools/domain-smoke/PreferenceStubs.kt`) para compilar los tests
    del dominio que dependen de `data.preferences` (DataStore no disponible fuera de Android).

## Áreas modificadas

- intelligence, privacy/IME, context (external/audit), automation, domain, ui/screens,
  shortcuts/quicksettings, backup, manifest/DI/datos/servicios, strings (i18n).

## Tests ejecutados

- **NO VERIFICADO (gradle/Android)**: no se ejecutó `./gradlew test`/`lint`/`assemble` en esta
  sesión (sin Android SDK en el entorno). El estado "6 variantes verdes" corresponde a sesiones
  previas de Jules y NO fue reproducido aquí.
- **VERIFICADO (JVM/kotlinc)**: `bash tools/run_domain_checks.sh` → 25 assertions OK; 125 tests
  unitarios del dominio OK con JUnit4 (compilados con kotlinc 2.1.20 + stubs Room/Preferences).

## Problemas conocidos

- Warnings de deprecación no bloqueantes (ej. `Icons.Outlined.InsertDriveFile` → AutoMirrored) — ver BACKLOG.
- `NoteBlocks.kt` y `TaskSnapshotCodec.kt` (dominio) dependen de `org.json` (API Android); en tests
  se sustituye por `org.json:json:20231013` real. Acoplamiento del dominio a Android, pero funcional.
- Tests de `backup`/`context`/`repositories` requieren DAOs/RoomDatabase/Context (no ejecutables en
  JVM pura sin Robolectric/Android SDK); no verificados en esta sesión.
- El workflow Jules necesita `jules/autonomous-ordia` visible en la API de Sources antes de lanzar.
- El auto-merge requiere `secrets.JULES_API_KEY` configurado y checks exitosos.

## Bloqueos

- Ninguno activo. El único paso que requiere al humano: configurar `secrets.JULES_API_KEY` y
  arrancar el primer ciclo manual (workflow_dispatch).

## Siguiente tarea recomendada

- Profundizar la auditoría de persistencia (Room: cascadas, índices, transacciones, N+1) y de
  recordatorios/WorkManager con un entorno que tenga Android SDK (gradle lint/assemble), ya que
  esos tests no son ejecutables en JVM pura. Priorizar los ítems P0/P1 del BACKLOG (backup adverso,
  restauración con manifiesto corrupto). La verificación de dominio (esta sesión) ya está verde.

## PR pendiente

- Ninguno (el auto-merge gestiona las PRs autónomas hacia `jules/autonomous-ordia`).

## Estado CI

- `android-ci.yml` activo en `main` y en la rama autónoma; verify corre en push/PR hacia ambas.
- Pendiente la primera ejecución del ciclo autónomo real (requiere `JULES_API_KEY`).
