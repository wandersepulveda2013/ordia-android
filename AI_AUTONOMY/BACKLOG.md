# BACKLOG — Ordía

> Inventario priorizado de mejoras y correcciones. Formato:
> `PRIORIDAD | ÁREA | PROBLEMA | EVIDENCIA | ESTADO`
> El agente mueve ítems a FIXED/VERIFIED con tests que lo demuestren.
> No añadir ideas vagas; solo problemas reales con evidencia.

## Pendientes (P0 / P1 / P2)

| PRIORIDAD | ÁREA | PROBLEMA | EVIDENCIA | ESTADO |
|-----------|------|----------|----------|--------|
| P2 | UI | `Icons.Outlined.InsertDriveFile` deprecado; usar `Icons.AutoMirrored.Outlined.InsertDriveFile` | warning de compilación en `TaskDetailScreen` | FIXED |
| P2 | i18n | Revisar coherencia de cadenas nuevas (command_palette, feedback, floating_capture, android_access, updates_*) | inspección manual pendiente | OPEN |
| P3 | UX | Pulido visual de pantallas renovadas del workspace | capturas tras sesión | OPEN |
| P0 | Updater | Actualizador in-app nativo basado en manifiesto (check sin bloquear arranque, progreso, verificación SHA-256/package/versionCode/firma, PackageInstaller + confirmación final, Ajustes → Actualizaciones, badge, feed desacoplado del agente) | sesión 006: `UpdateManifestParserTest` 11/11 + `UpdateSecurityRulesTest` 9/9 + 6 variantes compilan + lint 0 errores; CI publica `update-manifest-<flavor>.json` + `Ordia-3.0-<flavor>-signed.apk`; integrado en `main` (sesión 007, merge `5c7f8a6d`) | VERIFIED — código/test/CI; falta verificación física en dispositivo ADB (bloqueada por hardware) |
| P0 | Convergencia | Integrar el rebuild 3.0 + actualizador de `jules/autonomous-ordia` en `main` sin perder funcionalidad de main | sesión 007: merge `5c7f8a6d`, 36 conflictos resueltos, widget `hoy`/`atrasadas` + recordatorios de hábitos recuperados, update checker viejo eliminado, 2352 tests verdes, lint 0 errores | VERIFIED — pendiente push de `main` y fast-forward de la rama autónoma |
| P3 | QA | Lint: 115 warnings no bloqueantes (UseKtx 42, PluralsCandidate 31, GradleDependency 7, ModifierParameter 7…) | `lint-results-previewSafeDebug.xml` | FIXED — 0 errores, 95 warnings (Fase 18, commit `fbacf74`) |

## Completados

| PRIORIDAD | ÁREA | PROBLEMA | EVIDENCIA | ESTADO |
|-----------|------|----------|----------|--------|
| P2 | Notes | Borradores vacíos se guardan en la DB y el editor no hace autofocus | Notas sin texto guardadas + fricción de usuario | FIXED |
| P0 | What Now | Explicar por qué se sugiere cada tarea en "Qué hago ahora" | `WhatNowSuggestion.detail` + `WhatNowEngineTest` | FIXED |
| P0 | Priorización | Priorizador duplicado entre `DayPlanner` y `WhatNowEngine` | `TaskRules.schedulingComparator` único + tests | FIXED |
| P0 | Guardianes | `guardianInsight` se computaba pero no se mostraba en ninguna UI | card dismissible en Today + `GuardianCoachTest` | FIXED |
| P0 | Unificación | Notas y tareas no convertibles entre sí | `NoteTaskConverter` + UI en TaskDetail/NoteEditor + tests | FIXED |
| P0 | Integridad | Borrar tarea padre podía dejar subtareas huérfanas | `TaskDao.delete` transaccional con `TaskTree.collectIds` + `TaskTreeTest` | FIXED |
| P0 | Integridad | `ConversationDao` no tenía borrado atómico de datos completos | `clearAll()` `@Transaction` + test de cobertura | FIXED |
| P0 | Integridad | Consultas repetidas sin índices (log de automatización, capturas, notas) | migración Room v8 + 3 índices + esquema exportado | FIXED |
| P2 | Backup | Comprobar restauración con manifiesto corrupto (escenario adverso) | `BackupManagerTest`: nonObjectRootJson, wrongFormatField, nonIntegerVersion, nonNumericCreatedAt, collectionWithWrongType, preferencesWithWrongType | VERIFIED |
| P1 | Privacy | Fragmentos de paquete sin punto (banca genérica) no se filtraban | `ContextPrivacyFilterTest` | FIXED |
| P1 | Capture | `StartActivityAndCollapseDeprecated` en tile de Quick Settings | lint | FIXED |
| P1 | UI | `stringResource` fuera del ámbito composable en `TaskDetailScreen` | lint | FIXED |
| P2 | UI | `Icons.Outlined.InsertDriveFile` (y ArrowForward/ArrowBack/Send/FormatListBulleted) deprecados | warnings de compilación → AutoMirrored | FIXED |
| P2 | i18n | 65 cadenas sin ninguna referencia en Kotlin/XML/variantes/tests | análisis automático → poda de 58 strings + 7 plurals verificados en uso vía `R.plurals.*` | FIXED |
| P2 | QA | Verificar que las 6 variantes (Safe/Full/Advanced × debug/release) compilan tras cambios | `assemble` ×6 → 6 APK generados | FIXED |
