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
- Bloque estable completado (B1–B5 + P1 + P2 + P6). Build y tests verdes.
- Próximo: ejecutar verificación completa estilo CI (lint + assembleDebug + assembleRelease) antes de dar por válido el bloque.

## Commits realizados (branch `autonomous/delete-subtree-concurrency`)
- `5f887eb` fix(tasks): delete full subtree + cleanup attachments/reminders on permanent delete
- `c94a1d5` fix(repo): clean PROJECT/NOTE attachments on permanent delete + transactions
- `c06ae21` feat(tasks): per-task TaskMutationGate serialization for save/toggle
- `304a7ef` fix(backup): re-schedule task reminders after restoring a backup

## Pruebas ejecutadas
- `:app:compileDebugKotlin` — OK (solo warnings de deprecación preexistentes).
- `:app:testDebugUnitTest` — OK: 27 tests, 0 fallos (incluye `TaskMutationGateTest`).
- `:app:assembleDebug` — BUILD SUCCESSFUL.
- (Pendiente: `lint` + `assembleRelease` para paridad con CI.)

## Bugs encontrados
- B1 (CRÍTICO): subtasks huérfanos al eliminar tarea permanentemente. → CORREGIDO.
- B2 (ALTO): reminders de subtasks no cancelados al eliminar el padre. → CORREGIDO.
- B3 (MEDIO): attachments TASK huérfanos al eliminar tareas. → CORREGIDO.
- B4 (MEDIO): borrado sin transacción. → CORREGIDO.
- B5 (BAJO/MEDIO): concurrencia de mutaciones sobre la misma tarea sin serialización. → PARCIAL (serializa borrado permanente de tareas vía `TaskMutationGate`; faltan otras mutaciones).

## Bugs pendientes / próximos pasos
- P1: Extender `TaskMutationGate` a `saveTask`/`toggleTask`/`archiveTask` para serializar todas las mutaciones de una misma tarea (Mutex por-id sería ideal, no global, para no serializar todo).
- P2: Aplicar el mismo patrón de limpieza de attachments a `ProjectRepository.deletePermanently`, `NoteRepository.deletePermanently`, `HabitRepository.deletePermanently` (attachments de NOTE/PROJECT no se limpian; habit_logs sí por FK CASCADE).
- P3: Considerar ForeignKey self-reference `parentTaskId` con `onDelete = CASCADE` + migración v2→v3 (recreación de tabla). Decidido NO hacerlo ahora para evitar duplicar el cascade con `deleteSubtreeAndSelf` y por riesgo en migración; la integridad se garantiza a nivel de app. Revisar si conviene para defensa en profundidad.
- P4: Validar `deleteByIds` con listas vacías (Room genera `IN ()` inválido). En `deleteSubtreeAndSelf` se guarda con `if (ids.isNotEmpty())` antes de llamar a `deleteForOwners`, pero `deleteByIds(ids)` también debe protegerse → verificar.
- P5: Añadir tests de instrumentación (Room) para `collectSubtreeIds` y `deleteSubtreeAndSelf` (requieren `androidx.room:room-testing`, ya presente en androidTest).
- P6: Auditar `BackupManager` restore: al restaurar, los reminders no se reprograman para tareas con `reminderAt`/`dueAt`.

## Decisiones arquitectónicas
- El cancelado de reminders (WorkManager) se mantiene fuera de la transacción de BD para no acoplar WorkManager a la Tx y porque `ReminderScheduler` no es un DAO.
- `collectSubtreeIds` usa CTE recursiva en SQL (eficiente) en lugar de recursión en Kotlin (múltiples round-trips a BD).
- `TaskMutationGate` usa `Mutex` de corrutinas (no `ReentrantLock`) para ser seguro bajo suspensiones.

## Riesgos pendientes
- Sin poder correr `assembleDebug`/lint aún (solo tests). Validar build completa en CI.
- `local.properties` NO debe commitearse (es específico del entorno). Confirmar que `.gitignore` lo excluye.

## Cómo continuar
1. `git checkout autonomous/delete-subtree-concurrency`.
2. Revisar `/tmp/gradle-test.log` (si aún existe) o re-ejecutar `:app:testDebugUnitTest`.
3. Si build verde: hacer commit(s) coherentes y push al branch.
4. Continuar con P1–P6 en orden de prioridad.
