# AUTONOMOUS_HANDOFF.md — Continuidad de la auditoría/evolución autónoma de Ordía

> Este archivo permite a una ejecución futura continuar el trabajo sin repetir la investigación.
> Mantener actualizado, especialmente antes de acercarse al límite de iteraciones.

## Estado del repositorio
- Repo: `https://github.com/wandersepulveda2013/ordia-android.git`
- Branch de trabajo autónomo: `autonomous/delete-subtree-concurrency` (creado desde `main` @ `863ef8a`).
- Workspace: `/workspace/project/ordia-android`.

## Último trabajo completado
- Auditoría de la zona TaskRepository / eliminación de tareas / subtareas / concurrencia.
- Hallazgos confirmados:
  1. `TaskRepository.deletePermanently(id)` solo llamaba `dao.deleteById(id)` → subtasks huérfanos (sin FK CASCADE en `parentTaskId`, solo Index).
  2. `deleteArchivedPermanently("task", id)` en ViewModel solo cancelaba el reminder de la tarea raíz, no de sus descendientes.
  3. Attachments de tipo TASK no se limpiaban al borrar tareas (no hay FK en `attachments`).
  4. Ausencia de transacciones en el borrado → riesgo de datos inconsistentes ante fallos parciales.
  5. Sin mecanismo de serialización de mutaciones concurrentes sobre tareas.

## Cambios implementados (esta sesión)
- `app/src/main/java/com/ordia/app/data/local/Daos.kt`:
  - `TaskDao.collectSubtreeIds(rootId)`: CTE recursiva (`WITH RECURSIVE`, `UNION`) para obtener IDs del subárbol completo (incluye raíz + descendientes). `UNION` (no `UNION ALL`) previene loops infinitos ante ciclos accidentales en `parentTaskId`.
  - `TaskDao.getDirectChildren(parentId)`.
  - `TaskDao.deleteByIds(ids)`.
  - `AttachmentDao.deleteForOwner(...)` y `AttachmentDao.deleteForOwners(...)`.
- `app/src/main/java/com/ordia/app/data/repository/Repositories.kt`:
  - `TaskMutationGate` (object) con `kotlinx.coroutines.sync.Mutex` **por taskId** (`ConcurrentHashMap<Long, Mutex>`); id `0L` agrupa tareas nuevas; además variante global para borrado de subárbol.
  - `TaskRepository` ahora recibe `OrdiaDatabase` y `AttachmentDao`.
  - `TaskRepository.deleteSubtreeAndSelf(rootId, reminderCancellation)`: borra el subárbol en transacción, limpia attachments TASK, delega el cancelado de reminders al llamador (fuera de la Tx de BD).
  - `TaskRepository.subtreeIds(rootId)`.
  - `TaskRepository.deletePermanently(id)` ahora en transacción y limpia attachments de la tarea.
  - `ProjectRepository`/`NoteRepository` ahora reciben `OrdiaDatabase`+`AttachmentDao` y limpian attachments PROJECT/NOTE en `deletePermanently` (en transacción).
- `app/src/main/java/com/ordia/app/di/AppContainer.kt`: actualiza construcción de `TaskRepository`, `ProjectRepository`, `NoteRepository`.
- `app/src/main/java/com/ordia/app/ui/OrdiaViewModel.kt`:
  - Import `TaskMutationGate`.
  - `deleteArchivedPermanently("task", id)` usa `TaskMutationGate.withLock { taskRepository.deleteSubtreeAndSelf(id) { reminderScheduler.cancel(it) } }`.
  - `saveTask` y `toggleTask` envueltos en `TaskMutationGate.withLock(task.id)`.
  - `importBackup` reprograma/cancela reminders tras restaurar.
- `app/src/main/java/com/ordia/app/backup/BackupManager.kt`:
  - `importJson(raw, onTasksRestored)` invoca el callback con las tareas restauradas (fuera de la Tx) para reprogramar reminders.
- `app/src/test/java/com/ordia/app/data/repository/TaskMutationGateTest.kt`: tests de serialización (misma tarea) y concurrencia (tareas distintas).

### Bloque 2 (auditoría ampliada)
- `app/src/main/java/com/ordia/app/data/local/Daos.kt`: `TaskDao.archiveByIds`/`restoreByIds` (UPDATE IN-batch).
- `app/src/main/java/com/ordia/app/data/repository/Repositories.kt`: `TaskRepository.archiveSubtreeAndSelf` y `restoreSubtreeAndSelf` (transacción + callback de reminders fuera de la Tx).
- `app/src/main/java/com/ordia/app/ui/OrdiaViewModel.kt`: `deleteTask` archiva subárbol; `restoreArchived("task")` restaura subárbol; `importBackup` llama `reminderScheduler.cancelAll()` antes de importar.
- `app/src/main/java/com/ordia/app/reminders/ReminderScheduler.kt`: `cancelAll()` vía `cancelAllWorkByTag(TAG_REMINDERS)`.
- `app/src/main/java/com/ordia/app/reminders/ReminderActionReceiver.kt`: `ACTION_COMPLETE` envuelto en `TaskMutationGate.withLock(task.id)`, re-lee la tarea dentro del lock, cancela reminder de la tarea completada.

## Trabajo en curso
- PR #32 abierto: https://github.com/wandersepulveda2013/ordia-android/pull/32 (`autonomous/delete-subtree-concurrency` → `main`).
- Bloque 1 (eliminación/concurrencia/backup/lint): COMPLETADO y verificado.
- Bloque 2 (auditoría ampliada): COMPLETADO y verificado (B8-B10).
- Bloque 3 (auditoría de módulos restantes + test de caracterización): COMPLETADO:
  - `PreferencesRepository`: revisado, sin bug (valida rangos, maneja legacy darkMode, usa DataStore).
  - `QuickCaptureActivity`: revisado, sin bug (crea tareas nuevas, no necesita TaskMutationGate; reminder solo si dueAt).
  - `OrdiaWidgetUpdater`: revisado, sin bug.
  - BD/migraciones (v2): revisadas; mis cambios son solo `@Query` → no requieren bump ni migración.
  - Test de caracterización añadido: `RecurrenceEngineTest.daily_reminderWithoutDue_dropsReminderOnNextOccurrence` (documenta edge case reminder sin dueAt).
- Próximo: seguir con P5 (tests Room instrumentación — requieren emulador) o auditar más módulos; PR #32 ya contiene todos los fixes (B1-B10) + test nuevo.

## Pack de mejoras visuales y funcionales (BLOQUE 4 — EN CURSO)
Usuario solicitó "inmensa pack de mejoras, tanto visuales como funcionales". Trabajando sobre branch `autonomous/delete-subtree-concurrency`.

### Mejoras COMPLETADAS (compilan, 28 tests pasan, pusheadas)
- **V1** (commit f79f287): Color de acento seleccionable — 6 paletas (Oro, Salvia, Rosa, Lavanda, Océano, Terracota) en Settings; `AccentPalette` enum en `PreferencesRepository`, `OrdiaTheme(accentPalette)` overload, selector con swatches en `SettingsScreen`.
- **V2** (commit f79f287): Barra de prioridad lateral coloreada en `TaskRow` (URGENT=error, HIGH=ámbar, NORMAL=secondary, LOW=outline) vía `priorityAccent()`.
- **V3** (commit f79f287): Animación `animateContentSize` al completar/tachar título de tarea en `TaskComponents.kt`.
- **V6** (commit f79f287): Anillo de progreso del día (Canvas circular) en `TodayScreen` reemplazando stat card plana.
- **F5** (commit d737c54): Estadísticas ampliadas en `StatisticsScreen` — gráfico de barras de 30 días + distribución de pendientes por prioridad con barras coloreadas.
- **F3** (commit d737c54): Filtros por proyecto y etiqueta (chips) en `TasksScreen` además de los filtros de estado.
- **F2** (commit 27c2757): Reordenar subtareas — `TaskDao.updateSortOrder`, `TaskRepository.reorderSubtasks` (transaccional), `OrdiaViewModel.moveSubtask`, botones subir/bajar en `TaskDetailScreen`.
- **F7** (commit 27c2757): Recordatorios diarios de hábitos — `HabitReminderScheduler` (PeriodicWorkRequest diario), `HabitReminderWorker` (notificación, skip si meta cumplida), toggle + TimePicker en `HabitEditorDialog`, cableado en save/delete/restore/importBackup.
- **V4** (commit 2d4a712): Punto de color de proyecto + conteo de tareas en `ProjectsScreen`.
- **F9** (commit 2d4a712): Barra de progreso de subtareas (completadas/total) en `TaskDetailScreen`.
- **F10** (commit 2d4a712): Fecha relativa ("hace X") en preview de notas en `NotesScreen`.
- **V7** (commit 2d4a712): Badge de racha 🔥 (≥3 días) + indicador de recordatorio en `HabitsScreen`.
- **F8** (commit 258c8e7): Chips de fecha rápida (Hoy/Mañana/Semana/Bandeja) en quick-add de `TodayScreen`.
- **V5** (commit 258c8e7): Mini-calendario mensual en `PlannerScreen` con dots de carga de tareas, highlight de hoy y día seleccionado.

### Mejoras PENDIENTES (plan)
- **F11**: Widget de lista de hoy en pantalla de inicio. ← COMPLETADO (ver abajo actualización)
- **V8**: Empty states más visuales con iconos grandes. ← COMPLETADO
- **F12**: Búsqueda con highlight del término coincidente. ← COMPLETADO
- **V9**: Animación de transición entre pantallas. ← COMPLETADO
- **F13**: Exportar/compartir nota como texto. ← COMPLETADO
- **V14**: Tema dinámico (Material You / Android 12+). ← COMPLETADO

### ACTUALIZACIÓN — Lote final (commits 133759b, 33c34a6, f201a50)
- **V8** (commit 133759b): `EmptyState` ahora usa avatar 72dp + `headlineSmall` para más presencia.
- **F12** (commit 133759b): `SearchScreen` resalta el término buscado con span de color secondary en títulos/subtítulos de resultados (`highlightedTitle` con `buildAnnotatedString`+`withStyle`).
- **V9** (commit 133759b): `NavHost` con transiciones fade (enter 220ms / exit 180ms) en `Navigation.kt`.
- **F13** (commit 133759b): `NoteEditorScreen` botón compartir (ACTION_SEND) exporta título + bloques como texto plano.
- **V14** (commit 33c34a6): `AccentPalette.SYSTEM` — tema dinámico Material You (dynamicLight/DarkColorScheme) en Android 12+, fallback a Oro. Selector en Settings con icono luna. Build release OK.
- **F11** (commit f201a50): Widget de pantalla inicio muestra segunda línea con conteo de "hoy" y "atrasadas" (`widget_today` TextView). Layout XML + updater actualizados.

### Estado verificado (tras lote final)
- `:app:compileDebugKotlin` BUILD SUCCESSFUL.
- `:app:testDebugUnitTest` BUILD SUCCESSFUL (28 tests, sin FAILED).
- `:app:assembleRelease` BUILD SUCCESSFUL (APK generado).
- Build release completa OK.

### Mejoras totales completadas: 18
V1, V2, V3, V4, V5, V6, V7, V8, V9, V14 (10 visuales) + F2, F3, F5, F7, F8, F9, F10, F11, F12, F13 (10 funcionales) = 20 mejoras.

### Archivos modificados (BLOQUE 4)
- `app/src/main/java/com/ordia/app/data/preferences/PreferencesRepository.kt` (AccentPalette)
- `app/src/main/java/com/ordia/app/ui/theme/Theme.kt` (accentSwatches, OrdiaTheme overload)
- `app/src/main/java/com/ordia/app/ui/OrdiaRoot.kt` (pasar accentPalette + habitReminderScheduler)
- `app/src/main/java/com/ordia/app/ui/OrdiaViewModel.kt` (setAccentPalette, moveSubtask, habitReminderScheduler)
- `app/src/main/java/com/ordia/app/ui/screens/SettingsScreen.kt` (selector de acento)
- `app/src/main/java/com/ordia/app/ui/screens/TodayScreen.kt` (DayProgressRing, quick-add chips)
- `app/src/main/java/com/ordia/app/ui/screens/StatisticsScreen.kt` (30-day chart, priority breakdown)
- `app/src/main/java/com/ordia/app/ui/screens/TasksScreen.kt` (project/tag filters)
- `app/src/main/java/com/ordia/app/ui/screens/TaskDetailScreen.kt` (subtask reorder, progress bar)
- `app/src/main/java/com/ordia/app/ui/screens/ProjectsScreen.kt` (color dot, task count)
- `app/src/main/java/com/ordia/app/ui/screens/NotesScreen.kt` (relative time)
- `app/src/main/java/com/ordia/app/ui/screens/HabitsScreen.kt` (streak badge, reminder indicator)
- `app/src/main/java/com/ordia/app/ui/screens/PlannerScreen.kt` (month calendar)
- `app/src/main/java/com/ordia/app/ui/components/TaskComponents.kt` (priority rail, animateContentSize)
- `app/src/main/java/com/ordia/app/ui/components/EditorDialogs.kt` (habit reminder TimePicker)
- `app/src/main/java/com/ordia/app/data/local/Daos.kt` (TaskDao.updateSortOrder)
- `app/src/main/java/com/ordia/app/data/repository/Repositories.kt` (reorderSubtasks)
- `app/src/main/java/com/ordia/app/di/AppContainer.kt` (habitReminderScheduler)
- `app/src/main/java/com/ordia/app/reminders/HabitReminderScheduler.kt` (NUEVO)
- `app/src/main/java/com/ordia/app/reminders/HabitReminderWorker.kt` (NUEVO)

### Pruebas (BLOQUE 4)
- 28 tests unitarios pasan (sin regresiones).
- Compilación `:app:compileDebugKotlin` BUILD SUCCESSFUL tras cada bloque.
- No se añadieron tests para mejoras visuales (UI pura); `reorderSubtasks`/`moveSubtask` son candidatos a test instrumentado.

### Bugs encontrados (BLOQUE 4)
- Ninguno nuevo. Cambios aditivos, no tocan lógica de dominio existente.

### Decisiones arquitectónicas (BLOQUE 4)
- `AccentPalette` persistido en DataStore, aplicado solo al `secondary` del ColorScheme.
- `HabitReminderScheduler` usa `PeriodicWorkRequest` (24h) con delay inicial al próximo `reminderMinutes`.
- `reorderSubtasks` reescribe TODOS los `sortOrder` en una transacción (más robusto que swap).
- Color de proyecto vía `runCatching { Color.parseColor(colorHex) }` con fallback a secondary.

### Riesgos pendientes (BLOQUE 4)
- Recordatorios de hábitos no probados en emulador (WorkManager + notificaciones).
- `moveSubtask` lee de `uiState.value.subtasks()` que puede estar ligeramente desactualizado; aceptable para reorder manual.
- Schema Room sin bump: `updateSortOrder` es `@Query` UPDATE, no cambia schema.

### Próximo paso exacto
- **El pack de mejoras está COMPLETO (20 mejoras).** Build release verde, 28 tests pasan.
- Si se desea continuar: candidatos a mejora adicional serían: swipe-to-delete en listas, undo snackbar tras borrar, bloqueo de enfoque (Focus) con animación, soporte de gestos arrastrar en Planner, o tests instrumentados para reorderSubtasks.
- PR #32 ya contiene todos los fixes B1-B10 + las 20 mejoras visuales/funcionales.
- Antes de cualquier cambio futuro: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleRelease`.

## PR
- #32 — Fix task deletion: subtree cascade, concurrency, attachment/reminder cleanup
  https://github.com/wandersepulveda2013/ordia-android/pull/32

## Commits realizados (branch `autonomous/delete-subtree-concurrency`)
- `5f887eb` fix(tasks): delete full subtree + cleanup attachments/reminders on permanent delete
- `c94a1d5` fix(repo): clean PROJECT/NOTE attachments on permanent delete + transactions
- `c06ae21` feat(tasks): per-task TaskMutationGate serialization for save/toggle
- `304a7ef` fix(backup): re-schedule task reminders after restoring a backup
- `d48eeb4` fix(lint): read system locale observably in PlannerScreen (NonObservableLocale)
- `1896e10` docs: update AUTONOMOUS_HANDOFF.md with completed block status
- `9a3eefb` docs: record PR #32 link in AUTONOMOUS_HANDOFF.md
- `1981bd7` fix(tasks): archive/restore the whole subtree, not just the root task
- `8109d4a` fix(reminders): serialize notification-complete under TaskMutationGate
- `2d50d2b` fix(backup): cancel all existing reminders before restoring a backup
- `894780c` test(recurrence): characterize reminder drop when recurring task has no due date
- `b24e550` docs: record block 2 (B8-B10) in AUTONOMOUS_HANDOFF.md

## Pruebas ejecutadas
- `:app:compileDebugKotlin` — OK (solo warnings de deprecación preexistentes).
- `:app:testDebugUnitTest` — OK: **28 tests**, 0 fallos (incluye `TaskMutationGateTest` y nuevo `RecurrenceEngineTest` de caracterización).
- `:app:lintDebug` — OK (0 errores; 32 warnings preexistentes).
- `:app:assembleDebug` — BUILD SUCCESSFUL.
- `:app:assembleRelease` — BUILD SUCCESSFUL.
- Entorno reconstruido esta sesión: OpenJDK 21 instalado (compatible con jvmTarget=17), Android cmdline-tools + platforms;android-36 + build-tools;36.0.0 + platform-tools. `local.properties` ya apunta a `/opt/android-sdk`.

## Bugs encontrados
- B1 (CRÍTICO): subtasks huérfanos al eliminar tarea permanentemente. → CORREGIDO.
- B2 (ALTO): reminders de subtasks no cancelados al eliminar el padre. → CORREGIDO.
- B3 (MEDIO): attachments TASK huérfanos al eliminar tareas. → CORREGIDO.
- B4 (MEDIO): borrado sin transacción. → CORREGIDO.
- B5 (BAJO/MEDIO): concurrencia de mutaciones sobre la misma tarea sin serialización. → CORREGIDO (`saveTask`/`toggleTask`/borrado permanente serializados por-id con `TaskMutationGate`).
- B6 (MEDIO): `BackupManager.importJson` no reprogramaba reminders tras restaurar. → CORREGIDO.
- B7 (BAJO): 3 errores lint `NonObservableLocale` en `PlannerScreen.kt` (preexistentes) abortaban `lint`. → CORREGIDO.
- B8 (ALTO): archivar una tarea padre solo archivaba la raíz; las subtasks quedaban activas pero inaccesibles en la UI, con reminders vivos. → CORREGIDO (`archiveSubtreeAndSelf`/`restoreSubtreeAndSelf`).
- B9 (MEDIO): `ReminderActionReceiver` completaba la tarea sin `TaskMutationGate`, compitiendo con toggles de la UI (lost update). → CORREGIDO.
- B10 (MEDIO): importar backup dejaba reminders (WorkManager) huérfanos de tareas previas no presentes en el backup. → CORREGIDO (`cancelAll()` antes de importar).

## Bugs pendientes / próximos pasos
- P1: ~~Extender `TaskMutationGate` a `saveTask`/`toggleTask`~~ → HECHO.
- P2: ~~Limpieza attachments en Project/Note~~ → HECHO. Habit/Routine no gestionan attachments.
- P3: ForeignKey self-reference `parentTaskId` con `onDelete=CASCADE` + migración. Decidido NO (defensa en profundidad vs riesgo en migración); integridad garantizada a nivel de app por `deleteSubtreeAndSelf`. Revisar si conviene a futuro.
- P4: ~~Proteger `deleteByIds`/`deleteForOwners` con listas vacías~~ → Cubierto (`deleteSubtreeAndSelf` valida `isEmpty()` antes de llamarlos; no se llaman con listas vacías desde otro sitio).
- P5: Tests de instrumentación (Room) para `collectSubtreeIds` y `deleteSubtreeAndSelf` (requieren `androidx.room:room-testing` + dispositivo/emulador; no ejecutables en este entorno headless). PENDIENTE (requiere entorno de instrumentación).
- P6: ~~`BackupManager` restore reprogramar reminders~~ → HECHO.
- P7: `local.properties` confirmar que `.gitignore` lo excluye (no se commitea).
- P8: Abrir PR `autonomous/delete-subtree-concurrency` → `main` una vez confirmado por el usuario (por defecto no abrir PR sin confirmación explícita).

## Decisiones arquitectónicas
- El cancelado de reminders (WorkManager) se mantiene fuera de la transacción de BD para no acoplar WorkManager a la Tx y porque `ReminderScheduler` no es un DAO.
- `collectSubtreeIds` usa CTE recursiva en SQL (eficiente) en lugar de recursión en Kotlin (múltiples round-trips a BD). Usa `UNION` (no `UNION ALL`) para evitar loops infinitos ante ciclos.
- `TaskMutationGate` usa `Mutex` de corrutanas (no `ReentrantLock`) para ser seguro bajo suspensiones; bloqueo **por taskId** (`ConcurrentHashMap<Long, Mutex>`) para permitir concurrencia entre tareas distintas; id `0L` agrupa tareas nuevas.
- Limpieza de attachments se hace en la capa de repositorio dentro de la transacción de BD (no por FK CASCADE) para mantener el control explícito y evitar dependencia de migraciones.

## Riesgos pendientes
- Build completa verificada localmente (lint + test + assembleDebug + assembleRelease). Validar de nuevo en CI al abrir el PR.
- `local.properties` NO se commitea (específico del entorno); confirmar exclusión en `.gitignore`.
- Tests de instrumentación (P5) no ejecutables en este entorno headless.

## Cómo continuar
1. `git checkout autonomous/delete-subtree-concurrency` (branch al día con `origin`).
2. Re-ejecutar `:app:testDebugUnitTest :app:lintDebug :app:assembleRelease` para confirmar estado verde.
3. (Opcional, con confirmación del usuario) Abrir PR `autonomous/delete-subtree-concurrency` → `main`.
4. Continuar con P5 (tests Room de instrumentación) si se dispone de emulador; en caso contrario, auditar otros módulos (p. ej. `Habit` reminders sin implementar, `Routine`).

---

# SESIÓN 2 — CI Fix + In-App Update Checker (2026-08-12)

## Estado del repositorio (sesión 2)
- **Branch**: `main` (todos los cambios de CI y update checker están en main)
- **Repo**: `https://github.com/wandersepulveda2013/ordia-android.git`
- **Workspace**: `/workspace/project/ordia-android`
- **PR #32**: MERGED (deletion bug block B1-B10 + 20 mejoras visuales/funcionales)

## Último trabajo completado (sesión 2)

### CI_FIX — COMPLETADO
- **Root cause**: KSP2 (kapt→KSP migration for Room 2.8.4) reads cached
  schema JSON as empty in GitHub Actions, causing
  `JsonDecodingException: Expected start of the object '{', but had 'EOF'`.
  Local builds always succeeded.
- **Fix**: `--no-build-cache` on the CI verification step. KSP tasks now
  pass in CI. Also pinned `kotlinx-serialization-json:1.8.1` on the KSP
  classpath and switched schemaLocation to an absolute path.
- **APK path fixes**: CI workflow referenced a non-existent `previewAdvanced`
  buildType. Corrected to standard `app/build/outputs/apk/release/` and
  `app/build/outputs/apk/debug/` paths.
- **Release tagging**: Changed from `v3.0.0-build.{run_id}` to
  `v{versionName}-{versionCode}` (extracted via aapt2) so the in-app
  update checker can compare versions.
- **Result**: CI #62 — Verify ✓, Sign ✓, Publish ✓. Release created with signed APK.

### UPDATES_SECTION — COMPLETADO ✅
- **Problem**: User reported "en la app, en el apartado de actualizaciones no
  aparece" — the updates section was empty because no update mechanism existed.
- **Implementation**:
  - `UpdateChecker.kt`: queries GitHub Releases API `/releases/latest`,
    parses tag `v{versionName}-{versionCode}`, compares versionCode.
  - `UpdateInstaller.kt`: downloads APK to cacheDir with progress,
    launches system installer via FileProvider.
  - `SettingsScreen.kt`: new "Actualizaciones" section with status
    (checking / up-to-date / available / error), download button, progress.
  - `AndroidManifest.xml`: added INTERNET + REQUEST_INSTALL_PACKAGES
    permissions, registered FileProvider.
  - `file_paths.xml`: cache-path for APK sharing.
  - Version bumped: versionCode 10→11, versionName 1.0.0→3.0.0.
- **CI #66**: Verify ✓, Sign ✓, Publish ✓. Release `v3.0.0-11` published
  with signed APK (1.9 MB) at
  https://github.com/wandersepulveda2013/ordia-android/releases/tag/v3.0.0-11

## Commits (sesión 2, main branch)
1. `fc51fc4` — build: pin kotlinx-serialization-json 1.8.1 on KSP classpath
2. `e645381` — ci: disable build cache + use absolute schema path for KSP
3. `b9db764` — ci: fix release APK path (was referencing non-existent buildType)
4. `a1fb1d8` — ci: fix debug APK glob path (no flavors, direct debug/ dir)
5. `418df93` — feat: implement in-app update checker and APK installer
6. `aff5671` — docs: update AUTONOMOUS_HANDOFF.md with session 2
7. `e050c18` — ci: fix aapt2 version extraction (handle quoted values)
8. `5c65a85` — ci: add --rerun-tasks to bypass KSP incremental cache corruption

## Pruebas ejecutadas (sesión 2)
- Local clean + kspDebugKotlin + kspReleaseKotlin — BUILD SUCCESSFUL
- Local clean + test + lint + assembleDebug + assembleRelease — BUILD SUCCESSFUL
- Local assembleDebug + test + assembleRelease (with update feature) — BUILD SUCCESSFUL
- CI #62 (verify→sign→publish) — ALL PASSED
- CI #64 (verify→sign→publish) — Verify ✓, Sign ✓, Publish ✗ (aapt2 extraction bug)
- CI #65 (verify→sign→publish) — Verify ✗ (KSP incremental cache corruption)
- CI #66 (verify→sign→publish) — ALL PASSED ✅ (with --rerun-tasks)

## Bugs encontrados y corregidos (sesión 2)
1. **KSP2 empty JSON in CI**: Fixed with `--no-build-cache`.
2. **KSP2 incremental cache corruption (intermittent)**: Fixed with `--rerun-tasks`.
3. **previewAdvanced buildType reference**: Fixed paths to standard release/debug dirs.
4. **Debug APK glob path**: Fixed `*/debug/*.apk` → `debug/*.apk`.
5. **aapt2 version extraction**: Fixed regex to handle single-quoted values (`versionCode='11'`).
6. **No update mechanism in app**: Implemented full UpdateChecker + UpdateInstaller + UI.

## Próximo paso exacto (sesión 2)
1. ✅ **CI #66 passes** — release `v3.0.0-11` published.
2. ✅ **CI #71 passes** — release `v3.0.1-12` published with robust tag parser.
3. **Test update flow on device** — install signed APK v3.0.1-12, verify "Actualizaciones"
   section works. When a newer version is released (versionCode > 12), the app will show
   "Nueva versión disponible" with download button.
4. **Continue deep audit** from session 1: TaskRepository/TaskMutationGate deletion
   concurrency (already fixed in PR #32, verify no regressions).
5. **P5**: Tests de instrumentación Room para `collectSubtreeIds`/`deleteSubtreeAndSelf`
   (requieren emulador).

## Decisiones arquitectónicas (sesión 2)
- **KSP2 over KSP1**: KSP1 fails with `AbstractMethodError` on `FieldBundle$$serializer`.
  KSP2 (2.1.20-2.0.1) works but needs `--no-build-cache` in CI.
- **GitHub Releases for updates**: No Play Store. App self-checks via GitHub API
  and self-installs APKs via FileProvider.
- **Tag format `v{versionName}-{versionCode}`**: Allows app to extract and compare
  versionCode without parsing APK metadata remotely.
- **No AutoMigration**: Schema export only needed for AutoMigration (not used).
  Only manual MIGRATION_1_2 exists.

## Riesgos pendientes (sesión 2)
- **`--no-build-cache` slows CI**: ~2min → ~5min. Acceptable for reliability.
- **GitHub API rate limits**: 60/hour per IP unauthenticated. App checks only
  when SettingsScreen opens, so fine for normal usage.
- **REQUEST_INSTALL_PACKAGES**: Users may need to grant "Install unknown apps"
  manually. The installer intent will prompt for this.


---

# SESIÓN 3 — Fix del bucle autónomo atascado (2026-08-13)

## Estado del repositorio (sesión 3)
- **Branch**: `main`
- **Repo**: `https://github.com/wandersepulveda2013/ordia-android.git`

## Diagnóstico
El sistema autónomo 24/7 estaba corriendo pero atascado sin converger:
- 36 PRs abiertas, solo 4 mergedadas.
- Último run de auto-merge: PRs evaluadas: 8 / Merged: False, todas saltadas con
  "head ref 'jules/autonomous-ordia-...' no coincide con patrón Jules → SKIP".
- Muchas PRs duplicadas (7+ sobre deprecationes de iconos Compose) porque el workflow
  de Jules no veía las PRs abiertas y lanzaba sesiones nuevas cada 15 min.

## Causa raíz
El regex JULES_BRANCH_RE (idéntico en ambos workflows) esperaba ramas con prefijo
convencional (fix|feat|...) pero Jules crea ramas jules/autonomous-ordia-{timestamp}.
Ninguna rama real coincidía → el merge saltaba todo y el lanzador pensaba que no había
PRs abiertas → bucle de duplicación.

## Fix aplicado
Actualizado JULES_BRANCH_RE en ambos workflows para reconocer el formato real de Jules:
- jules/autonomous-ordia-{timestamp} (timestamp 10-20 dígitos)
- jules/autonomous-ordia (rama base)
- Mantiene compatibilidad con el patrón convencional histórico.

Archivos:
- .github/workflows/ordia-autonomous-merge.yml
- .github/workflows/ordia-autonomous-jules.yml

Test del regex: 10/10 casos correctos (ramas reales match, ramas ajenas no match).

## Efecto esperado tras el push
1. El merge workflow empezará a mergear PRs limpias (#37, #39 están CLEAN con CI verde).
2. El Jules workflow detectará las PRs abiertas y dejará de lanzar sesiones duplicadas
   cuando haya trabajo en curso (skip_ready_for_merge / skip_ci_running).
3. El sistema convergerá: las PRs limpias se integrarán y las DIRTY se quedan para que
   la próxima sesión de Jules las corrija o cierre.

## Próximo paso exacto
1. Tras pushear este fix a main, los workflows usarán el regex nuevo en su próxima ejecución.
2. Monitorear el próximo run de "Ordia Autonomous Merge" — debería mergear #37 o #39.
3. Si las PRs DIRTY no se resuelven solas tras unos ciclos, cerrar las duplicadas
   (especialmente las de deprecationes Compose, que se solapan).

## Resultado verificado (2026-08-13 13:28Z)
- Segundo fix aplicado: el combined status "pending" con total_count=0 (falso positivo de
  GitHub cuando solo hay check-runs) ya no bloquea el merge.
- Run manual del merge workflow: `PRs evaluadas: 9 / Merged: True`.
- **PR #39 MERGED** (squash) hacia jules/autonomous-ordia a las 13:28:48Z.
- Resto: #40 y #31 con Verify build:failure (CI real, legitimo); #38/#37/#34/#30/#29/#28
  con conflictos 405 (quedaron obsoletas tras merge de #39, son duplicadas de Compose).
- El sistema autónomo ahora CONVERGE: merges reales, y el launch workflow detectara las
  PRs abiertas para no lanzar sesiones duplicadas.
