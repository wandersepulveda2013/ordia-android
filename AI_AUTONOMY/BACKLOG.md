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
| P1 | Parser | "de la tarde/noche/mañana" como meridiem se ignoraba: "a las 4 de la tarde" → 04:00 en vez de 16:00; "al mediodía"/"a la medianoche" dejaban "al"/"a la" en el título; "esta mañana" dejaba "esta" huérfano (orden de limpieza destruía la frase) | probe JVM; 8 tests nuevos `deLaTardeAppliesPmOffset`, `alMediodiaParsesNoonAndCleanTitle`, `estaMananaCleanedFullyFromTitle` etc. | FIXED → VERIFIED (163 domain tests PASS; smoke 25 OK) |
| P1 | Parser | Contexto PM de parte del día NO se aplicaba a hora sin meridiem: "esta tarde a las 4" → 04:00 (AM); "esta noche a las 9" → 09:00 (AM); "mañana a la tarde" → 09:00 + "a la tarde" en título; "12 de la noche" → 12:00 (mediodía) en vez de 00:00; "de la madrugada" no reconocido | probe JVM `Probe2.kt`; 6 tests `estaTardeConHoraSinMeridiemAplicaPm`, `doceDeLaNocheEsMedianoche`, `deLaMadrugadaEsAmYLimpiaTitulo` etc. | FIXED → VERIFIED (169 domain tests PASS; smoke 25 OK) |
| P3 | Parser | "salir de madrugada" (sin "a las"/"a la") no reconocido; deja "de madrugada" en título, dueAt null | probe JVM | ABIERTO (caso raro) |
| P3 | Parser | "a las 24" → null (24:00 = medianoche válido pero no aceptado); "a las 3.5" → ".5" suelto en título | probe JVM | PARCIAL: "a las 24" RESUELTO ciclo 7 (172 tests OK); "a las 3.5" sigue ABIERTO |
| P3 | Parser | Residuos de título no limpiados: "que viene" tras fecha ("el viernes que viene"); "del" huérfano ("a las 3pm del jueves", "reunión del jueves") | probe JVM ciclo 7 | FIXED ciclo 8 (commit pendiente); `weekdayPattern` extendido con prefijo `el\|del\|de` y sufijo `que viene\|próximo(s\|a)`; 6 tests nuevos, 178 OK |
| P3 | Parser | "a primera hora" (sin interpretar/limpiar; queda en título, debería ser ~09:00 inicio de jornada) | probe JVM ciclo 7 | FIXED → VERIFIED ciclo 9 (`primeraHoraPattern` + `primeraHoraTime` 09:00 como fallback de `parsedTime`; limpieza de título tras `standalonePartOfDayPattern`; 4 tests nuevos, 182 OK) |
| P3 | Parser | Rango horario "de 18 a 20" no parseado (dueAt=null, title y hora nulos) | probe JVM ciclo 7 | ABIERTO (candidato a ciclo 9) |
| P1 | Parser | "N min antes" clasificado como DURACIÓN en vez de recordatorio: patrón reminder #2 `(\d)(minutos?\|horas?\|días?) antes` no aceptaba abreviatura `min`/`hora`; caía al patrón de duración `\d min` | probe JVM ciclo 10; 2 tests `parsesAbbreviatedMinBeforeAsReminder`, `parsesAbbreviatedMinBeforeAsReminderWithoutVerb` | FIXED → VERIFIED ciclo 10 (184 tests OK; smoke 25 OK) |
| P2 | Parser | ~~`#tag`/`@tag` no se limpia del título ni asigna categoría explícita (cat se infiere por keywords, ignora tag del usuario)~~ RESUELTO ciclo 11: `explicitCategoryPattern` reconoce categorías conocidas, prioridad sobre keywords, limpia título | probe JVM ciclo 10 | FIXED |
| P2 | Parser | "2h" compacto no reconocido como duración ("Trabajar 2h" → dur=null, "2h" en título) | probe JVM ciclo 10 | FIXED → VERIFIED ciclo 12 (patrón `\b(\d)\s*h\b`; unit check `h`→horas; 4 tests, 193 OK) |
| P2 | Parser | `prioridad alta:` y `urgente`/`importante` a mitad de frase no fijan prioridad ("Llamar mamá urgente" → NORMAL) | probe JVM ciclo 10 | FIXED → VERIFIED ciclo 13 (sufijo "urgente"/"importante" final → URGENT/HIGH + guard de negación "no es urgente"; 4 tests, 197 OK) |
| P2 | Parser | Duraciones fraccionarias sin dígitos ("media hora", "(un) cuarto de hora") no reconocidas: `durationMinutes`=null y residuo en el título | probe JVM ciclo 14 | FIXED → VERIFIED ciclo 14 (`fractionalDurationPattern`, guard "hora" para no confundir "cuarto"=habitación; 5 tests, 202 OK) |
| P3 | Parser | "Reunión de 30 minutos" deja residuo "de" en título | probe JVM ciclo 10 | ABIERTO |
