# RUN_LOG — Ordía

> Registro cronológico de sesiones autónomas (append-only, no borrar entradas).

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
- Auditoría funcional fuera del parser: persistencia/rutinas.

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
