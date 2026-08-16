# CURRENT_STATE — Ordía

> Actualizar AL FINAL de cada sesión autónoma.

## Estado

- **Fecha/hora (UTC)**: 2026-08-15 (mega-evolución integral, fases 1-7)
- **Branch de trabajo**: `jules/autonomous-ordia` (HEAD `9e594a3`)
- **main**: `d5b3b60` — contiene SOLO infraestructura de orquestación (workflows), no el rebuild
- **Workflow autónomo (scheduler)**: `.github/workflows/ordia-autonomous-jules.yml` en `main` (cron `17 */2 * * *` + dispatch)
- **Auto-merge**: `.github/workflows/ordia-autonomous-merge.yml` en `main` (pull_request_target + cron `*/15 * * * *` + dispatch)

## Último trabajo realizado

Mega-evolución integral de la app (7 fases, 9 commits sobre `jules/autonomous-ordia`):

1. **`cc4c115` refactor(nav)**: navegación a 3 primitivas (Hoy/Buscar/Más) con FAB único de captura.
2. **`417ee33` feat(capture)**: captura universal en Hoy (detecta eventos sin confundirlos con tareas) y parser natural más robusto.
3. **`833a50f` feat(whatnow)**: priorización unificada (`TaskRules.schedulingComparator`) y explicación dinámica de cada sugerencia de "Qué hago ahora" (`WhatNowSuggestion.detail`, en español, por estados: en curso/atrasada/vence hoy/urgente/alta prioridad/bandeja/programada).
4. **`c482ef8` feat(guardian)**: card de insight del Guardián en Hoy, descartable (clave estable `eyebrow|taskId|title`), con dedup contra la sugerencia What Now y colores por tono.
5. **`2d95e0c` feat(unify)**: conversión reversible nota↔tarea (archiva la fuente, conserva título/detalle/proyecto; accesible desde TaskDetail y NoteEditor).
6. **`0a37d4b` fix(integrity)` + `1585bf3` chore(schema)**: borrado de tarea con su subárbol en una transacción (`TaskTree.collectIds`), `ConversationDao.clearAll()` atómico, migración Room 7→8 con 3 índices (`automation_log.type+createdAt`, `captures.resultId`, `notes.pinned+updatedAt`) y esquema `8.json` exportado.
7. **`9e594a3` refactor(cleanup)**: iconos AutoMirrored (ArrowForward/ArrowBack/InsertDriveFile/Send/FormatListBulleted) y poda de 58 cadenas muertas verificada (0 sin uso residual).

## Áreas modificadas

- ui/screens (Today, TaskDetail, NoteEditor, Projects, ProjectDetail), ui/navigation, ui/components, domain (TaskRules, WhatNowEngine, GuardianCoach, NoteTaskConverter), data/local (Daos, Entities, TaskTree, OrdiaDatabase v8), data/repository (ConversationRepository.clearAll), strings (prune 58).

## Tests ejecutados

- `:app:testPreviewSafeDebugUnitTest` → suite completa verde (incluye `TaskTreeTest`, `TaskRulesTest`, `WhatNowEngineTest`, `GuardianCoachTest`, `NoteTaskConverterTest`, `Ordia3IntegrityTest`).
- Matriz de 6 variantes: `assemblePreviewSafeDebug`, `assemblePreviewSafeRelease`, `assemblePreviewFullDebug`, `assemblePreviewFullRelease`, `assemblePreviewAdvancedDebug`, `assemblePreviewAdvancedRelease` → los 6 APK generados (release unsigned, sin firmar por ausencia de keystore local).
- `lintPreviewSafeDebug` → BUILD SUCCESSFUL (115 warnings + 1 info, 0 errores; los 4 `UnusedResources` son falsos positivos cross-variante: IME/notificaciones/update_paths viven en otros flavors).

## Problemas conocidos

- Release builds salen sin firmar (no hay keystore en el entorno); la firma la gestiona el workflow de publicación en CI.
- Advertencia `Support for language version 2.0+ in kapt is in Alpha` (cosmético, kapt cae a 1.9).
- El workflow Jules necesita `jules/autonomous-ordia` visible en la API de Sources antes de lanzar sesiones.
- El auto-merge requiere que las PRs de Jules tengan checks exitosos; si `secrets.JULES_API_KEY` no está configurado, el scheduler no lanza sesiones.

## Bloqueos

- Ninguno activo. El único paso que requiere al humano: configurar `secrets.JULES_API_KEY` y arrancar el primer ciclo manual (workflow_dispatch).

## Siguiente tarea recomendada

- (P2) Backup: comprobar restauración con manifiesto corrupto (`RestoreData`).
- (P3) Pulido visual de pantallas renovadas del workspace.
- Revisión humana de los 9 commits de la mega-evolución en `jules/autonomous-ordia` y decisión sobre si avanzan a `main`.

## PR pendiente

- Ninguno (el auto-merge gestiona las PRs autónomas hacia `jules/autonomous-ordia`).

## Estado CI

- `android-ci.yml` activo en `main` y en la rama autónoma; verify corre en push/PR hacia ambas.
