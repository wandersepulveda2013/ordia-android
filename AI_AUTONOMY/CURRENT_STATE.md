# CURRENT_STATE — Ordía

> Actualizar AL FINAL de cada sesión autónoma.

## Estado

- **Fecha/hora (UTC)**: 2026-08-16 (sesión 007: integración del rebuild + actualizador en `main`)
- **Branch de trabajo**: `jules/autonomous-ordia` (rebuild 3.0.0 + actualizador por manifiesto) — integrado en `main` vía merge `5c7f8a6d`
- **main**: `5c7f8a6d` (merge del rebuild, pendiente de push en esta sesión) — contiene infraestructura de orquestación + rebuild 3.0 + actualizador
- **Workflow autónomo (scheduler)**: `.github/workflows/ordia-autonomous-jules.yml` en `main` (cron + dispatch)
- **Auto-merge**: `.github/workflows/ordia-autonomous-merge.yml` en `main` (pull_request_target + cron + dispatch)

## Último trabajo realizado

Sesión 007 — **Merge del rebuild completo a `main`** (fases 28-29 de EVOLUCIÓN FINAL, autorización explícita del usuario):

1. **Análisis de divergencia**: merge-base `0059fb9e`; `origin/main` = `ba5b6eb0` (54 commits de infra + app antigua); `jules/autonomous-ordia` = `0d5ee44` (153 commits del rebuild).
2. **Backup de seguridad**: tag `backup/main-before-rebuild-merge-2026-08-16` en `ba5b6eb0`.
3. **Merge con resolución manual** (36 conflictos): app/builds/CI ganan con la rama autónoma (Kotlin 2.1.0 + KAPT ORD-036, `android-ci.yml` per-flavor); workflows de autonomía ganan con main (regex de rama real + fix de status vacío); AGENTS.md reescrito (elimina marcadores de conflicto que estaban commiteados en jules); .gitignore unión (secretos/artefactos).
4. **Funcionalidades de main recuperadas en el rebuild**: widget `hoy`/`atrasadas` (contadores) dentro del refactor `updateWidgets` + layout `widget_today`; recordatorios de hábitos (`HabitReminderScheduler`/`HabitReminderWorker`) cableados en `AppContainer`/`OrdiaViewModel` (`saveHabit`, `deleteHabit`, `restoreArchived`, `deleteArchivedPermanently`, `restoreBackup`).
5. **Eliminación de superseded**: update checker viejo por API (`com.ordia.app.update`) borrado; `TaskMutationGateTest` adaptado a la API real (mutex global).
6. **Verificado**: 3 variantes compilan, 2352 tests unitarios verdes (0 fallos), lint limpio, sin marcadores de conflicto.

## Áreas modificadas

- app/src/main/java/com/ordia/app/{di/AppContainer, ui/OrdiaViewModel, ui/OrdiaRoot, widget/OrdiaWidgetProvider, data/repository/Repositories, reminders/{HabitReminderScheduler,HabitReminderWorker}(recuperados)}, res/layout/ordia_widget.xml, AGENTS.md, .gitignore, .github/workflows, AI_AUTONOMY (RUN_LOG, CURRENT_STATE, BACKLOG, DECISIONS), docs/* (main-only, fusionados).

## Tests ejecutados

- `:app:compilePreviewSafeDebugKotlin :app:compilePreviewAdvancedDebugKotlin :app:compilePreviewFullDebugKotlin` → BUILD SUCCESSFUL.
- `:app:test{PreviewSafe,PreviewAdvanced,PreviewFull}DebugUnitTest` → BUILD SUCCESSFUL; **2352 tests, 0 fallos** (incluye `TaskMutationGateTest` 2/2 adaptado y `UpdateManifestParserTest`/`UpdateSecurityRulesTest`).
- `:app:lintPreviewSafeDebug` → 0 errores (warnings deprecación SKIP pre-existentes).

## Problemas conocidos

- Release builds salen sin firmar localmente (keystore solo en CI: `ORDIA_KEYSTORE_PATH/PASSWORD`, `ORDIA_KEY_ALIAS`, `ORDIA_KEY_ALIAS_PASSWORD`; el sign/publish del CI requiere además `ORDIA_UPDATE_KEYSTORE_*`).
- Sin dispositivo ADB conectado → verificación física del flujo de instalación (PackageInstaller, confirmación final de Android, rechazo, descarga interrumpida) pendiente de hardware.
- Advertencia kapt (cosmético, sin impacto).
- Strings viejas de check (`settings_update_*`, `update_fail_*`) quedan sin uso → warnings de lint UnusedResources cross-variante (SKIP documentado).
- Documentos `docs/*` de main (AUTO_UPDATES.md, etc.) describen partes del código pre-rebuild → candidatos a revisar/podar.

## Bloqueos

- Verificación física del actualizador: requiere conectar un dispositivo Android a este PC.
- Push de `main` a origin: si la rama remota está protegida (PR obligatoria), el push directo será rechazado → en ese caso documentar el bloqueo exacto (sin `gh` disponible en este entorno no se puede crear PR por CLI).

## Siguiente tarea recomendada

- **Push de `main`** (merge `5c7f8a6d`) y sync de `jules/autonomous-ordia` (fast-forward).
- Conectar un teléfono, instalar un release reciente (publicado por CI) y verificar: badge, diálogo opcional/mandatory, descarga con progreso, instalación con confirmación final de Android, rechazo, sin internet.
- (P2) Revisar coherencia de cadenas nuevas (updates_*).
- (P3) Revisar `docs/*` de main para eliminar descripciones pre-rebuild obsoletas.

## PR pendiente

- Ninguna activa. `jules/autonomous-ordia` == `main` tras el merge y fast-forward.

## Estado CI

- `android-ci.yml` (versión per-flavor del rebuild) activo en `main` y en la rama autónoma; verify corre en push/PR hacia ambas; sign+publish solo en push a `main`, publicando `Ordia-3.0-{safe,full,advanced}-signed.apk` + `update-manifest-<flavor>.json` + release inmutable con 9 assets.

## Sesión Actual - Mejora de UX e Integridad de Notas
- **Trabajo seleccionado**:
  - Evitar el guardado de notas vacías para prevenir acumulación en base de datos.
  - Solicitar el foco automáticamente en el editor al crear una nueva nota.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/ui/NotepadViewModel.kt`, `app/src/main/java/com/ordia/app/ui/screens/NoteEditorScreen.kt`.
- **Pruebas añadidas**: `app/src/test/java/com/ordia/app/ui/NotepadViewModelTest.kt`.
- **Verificación**: `./gradlew testPreviewSafeDebugUnitTest` y `./gradlew lintPreviewSafeDebug` resultaron exitosos.
- **Próximo candidato**: (P3) Pulido visual de pantallas renovadas del workspace.
