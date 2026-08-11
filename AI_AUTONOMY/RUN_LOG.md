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
