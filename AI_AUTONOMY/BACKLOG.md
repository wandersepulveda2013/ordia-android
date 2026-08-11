# BACKLOG — Ordía

> Inventario priorizado de mejoras y correcciones. Formato:
> `PRIORIDAD | ÁREA | PROBLEMA | EVIDENCIA | ESTADO`
> El agente mueve ítems a FIXED/VERIFIED con tests que lo demuestren.
> No añadir ideas vagas; solo problemas reales con evidencia.

## Pendientes (P0 / P1 / P2)

| PRIORIDAD | ÁREA | PROBLEMA | EVIDENCIA | ESTADO |
|-----------|------|----------|----------|--------|
| P2 | UI | `Icons.Outlined.InsertDriveFile` deprecado; usar `Icons.AutoMirrored.Outlined.InsertDriveFile` | warning de compilación en `TaskDetailScreen` | OPEN |
| P2 | i18n | Revisar coherencia de cadenas nuevas (command_palette, feedback, floating_capture, android_access) | inspección manual pendiente | OPEN |
| P2 | QA | Verificar que las 6 variantes (Safe/Full/Advanced × debug/release) compilan tras cambios | `./gradlew test` | OPEN |
| P2 | Backup | Comprobar restauración con manifiesto corrupto (escenario adverso) | revisión de `RestoreData` | OPEN |
| P3 | Rutinas | `saveRoutine` hace delete-then-reinsert de pasos sin transacción atómica; si el proceso muere a mitad, la rutina queda con pasos parciales/perdidos | `OrdiaViewModel.saveRoutine` L678-682 | OPEN |
| P3 | UX | Pulido visual de pantallas renovadas del workspace | capturas tras sesión | OPEN |

## Auditorías estáticas realizadas (sin hallazgos P0/P1)

| ÁREA | ALCANCE | EVIDENCIA | ESTADO |
|------|---------|----------|--------|
| Persistencia/Room | Entities (FK + índices), Daos (deleteSubtreeAndSelf transaccional, cascadas), OrdiaDatabase (migraciones 1-7, sin fallbackToDestructiveMigration) | inspección estática ciclo 2 | OK (no requiere fix) |
| Backup/Restore | BackupStore.replaceAll en `withTransaction` atómica (orden FK coherente), pre-restore backup, verify-after-commit + rollback, checksum SHA-256, mutex | inspección estática ciclo 2 | OK (no requiere fix) |
| Recordatorios | ReminderScheduler (enqueueUniqueWork REPLACE), TaskReminderWorker (re-lee task, filtra completed/archived/cancelled, quiet hours reschedule, POST_NOTIFICATIONS retry), ReminderActionReceiver (exported=false, mutex), ReminderResyncReceiver (TIMEZONE/TIME/DATE, futuro-only) | inspección estática ciclo 3 | OK (no requiere fix) |
| Seguridad/Manifiesto | allowBackup=false, cleartextTraffic=false, ReminderActionReceiver exported=false, ReminderResyncReceiver exported=true (requerido por system broadcasts, filtra acciones), MainActivity SEND/PROCESS_TEXT (texto capado, URI permiso en runCatching) | inspección estática ciclo 2 | OK (no requiere fix) |
| Task completion+recurrence | toggleTask bajo TaskMutationGate.mutex, RecurrenceEngine (reminder/start offset preservado, guard de avance), subtask auto-complete con logging de undo | inspección estática ciclo 2 | OK (no requiere fix) |
| Rutinas | RoutineRules (dedup wasRunToday), runRoutine (sortOrder preserva orden, undo real vía AutomationUndoRules), archive/restore. Minor: saveRoutine delete-then-reinsert no transaccional (P3) | inspección estática ciclo 4 | OK (P3 menor: atomicidad de saveRoutine) |

## Completados

| PRIORIDAD | ÁREA | PROBLEMA | EVIDENCIA | ESTADO |
|-----------|------|----------|----------|--------|
| P1 | Privacy | Fragmentos de paquete sin punto (banca genérica) no se filtraban | `ContextPrivacyFilterTest` | FIXED |
| P1 | Capture | `StartActivityAndCollapseDeprecated` en tile de Quick Settings | lint | FIXED |
| P1 | UI | `stringResource` fuera del ámbito composable en `TaskDetailScreen` | lint | FIXED |
| P1 | Parser | Fecha numérica sin año en el pasado NO rodaba al año siguiente | probe JVM; `numericDateWithoutYearInPastRollsToNextYear` | FIXED → VERIFIED (155 domain tests PASS) |
| P1 | Parser | "esta mañana/tarde/noche" no reconocidas; "esta mañana" se leía como "mañana" | probe JVM; `estaMananaIsNotMistakenForTomorrow` | FIXED → VERIFIED (155 domain tests PASS) |
| P1 | Parser | "urgente" como palabra inicial no se detectaba sin prefijo !/# | probe JVM; `leadingUrgenteSetsUrgentPriority` | FIXED → VERIFIED (155 domain tests PASS) |
| P1 | Notas | `NoteBlockCodec.decode` perdía TODOS los bloques si un único elemento del array JSON estaba malformado | probe JVM; `singleMalformedElementDoesNotDiscardValidBlocks` | FIXED → VERIFIED (155 domain tests PASS; 11 tests nuevos) |
| P2 | Parser | `NaturalTaskParser` no reconocía números escritos en tiempo relativo ("en dos horas", "dentro de tres días", "en una hora") | probe JVM: `due=null`; tests `writtenNumberRelativeHoursParsesDueAt` etc. | FIXED → VERIFIED (155 domain tests PASS; 8 tests nuevos) |
| P2 | Tests | `DomainSmoke.kt` obsoleto tras ampliar `SearchKind` | `run_domain_checks.sh` reproducido; alineado con `SearchEngineTest` | FIXED → VERIFIED (smoke 25 assertions OK) |
