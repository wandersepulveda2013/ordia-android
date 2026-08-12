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

## Trabajo en curso
- PR #32 abierto: https://github.com/wandersepulveda2013/ordia-android/pull/32 (`autonomous/delete-subtree-concurrency` → `main`).
- Bloque de eliminación/concurrencia/backup/lint: COMPLETADO y verificado.
- Iniciando siguiente bloque de auditoría: módulos `Habit` (reminders no implementados) y `Routine`.

## PR
- #32 — Fix task deletion: subtree cascade, concurrency, attachment/reminder cleanup
  https://github.com/wandersepulveda2013/ordia-android/pull/32

## Commits realizados (branch `autonomous/delete-subtree-concurrency`)
- `5f887eb` fix(tasks): delete full subtree + cleanup attachments/reminders on permanent delete
- `c94a1d5` fix(repo): clean PROJECT/NOTE attachments on permanent delete + transactions
- `c06ae21` feat(tasks): per-task TaskMutationGate serialization for save/toggle
- `304a7ef` fix(backup): re-schedule task reminders after restoring a backup
- `d48eeb4` fix(lint): read system locale observably in PlannerScreen (NonObservableLocale)

## Pruebas ejecutadas
- `:app:compileDebugKotlin` — OK (solo warnings de deprecación preexistentes).
- `:app:testDebugUnitTest` — OK: 27 tests, 0 fallos (incluye `TaskMutationGateTest`: serialización + concurrencia).
- `:app:lintDebug` — OK (0 errores; 32 warnings preexistentes).
- `:app:assembleDebug` — BUILD SUCCESSFUL.
- `:app:assembleRelease` — BUILD SUCCESSFUL.

## Bugs encontrados
- B1 (CRÍTICO): subtasks huérfanos al eliminar tarea permanentemente. → CORREGIDO.
- B2 (ALTO): reminders de subtasks no cancelados al eliminar el padre. → CORREGIDO.
- B3 (MEDIO): attachments TASK huérfanos al eliminar tareas. → CORREGIDO.
- B4 (MEDIO): borrado sin transacción. → CORREGIDO.
- B5 (BAJO/MEDIO): concurrencia de mutaciones sobre la misma tarea sin serialización. → CORREGIDO (`saveTask`/`toggleTask`/borrado permanente serializados por-id con `TaskMutationGate`).
- B6 (MEDIO): `BackupManager.importJson` no reprogramaba reminders tras restaurar. → CORREGIDO.
- B7 (BAJO): 3 errores lint `NonObservableLocale` en `PlannerScreen.kt` (preexistentes) abortaban `lint`. → CORREGIDO.

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

