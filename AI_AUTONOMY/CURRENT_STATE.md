# CURRENT_STATE — Ordía

> Actualizar AL FINAL de cada sesión autónoma.

## Estado

- **Fecha/hora (UTC)**: 2026-08-15 (fases 19-27: a11y, resiliencia, seguridad, limpieza, versionado, matriz, merge)
- **Branch de trabajo**: `jules/autonomous-ordia` (HEAD `d114304`, ahead 23 de `origin/jules/autonomous-ordia`)
- **main**: `ba5b6eb` (origin) — contiene infraestructura de orquestación + convergencia de autonomía (#39)
- **Workflow autónomo (scheduler)**: `.github/workflows/ordia-autonomous-jules.yml` en `main` (cron `17 */2 * * *` + dispatch)
- **Auto-merge**: `.github/workflows/ordia-autonomous-merge.yml` en `main` (pull_request_target + cron `*/15 * * * *` + dispatch)

## Último trabajo realizado

Misión "ORDÍA — EVOLUCIÓN FINAL + PRODUCCIÓN + MAIN + APK PARA MI MÓVIL" (fases 19-27 sobre `jules/autonomous-ordia`):

1. **`174ae3d` feat(a11y)** (Fase 19): accesibilidad — etiquetas, roles y estado de selección en botones, listas, switches, checkboxes y calendario. 412 tests verdes.
2. **`c0ca77b` + `6baa934`** (Fase 20): resiliencia de backup — 6 tests de manifiesto corrupto (nonObjectRootJson, wrongFormatField, nonIntegerVersion, nonNumericCreatedAt, collectionWithWrongType, preferencesWithWrongType). 418 tests verdes. BACKLOG marcado.
3. **`97bfa28`** (Fase 21): seguridad — `ReminderResyncReceiver` a `exported="false"`; auditoría sin hallazgos (sin secretos, HTTPS + SHA-256, `FLAG_IMMUTABLE`, `allowBackup=false`, FileProvider/ExternalConfirmationReceiver protegidos).
4. **Fases 22-24**: limpieza verificada (0 duplicados, 0 strings muertos, 7 plurals en uso, 4 layouts usados), versionado `versionCode=1300000000` / `versionName=3.0.0-preview-safe` confirmado con aapt2, matriz de 6 variantes ensamblada.
5. **Fase 25-26**: APK debug firmado con cert Android Debug (instalable, verificado con apksigner). Sin dispositivo ADB conectado → instalación pendiente de hardware.
6. **`d114304` merge**: sincronización con `origin/jules/autonomous-ordia` (sesiones ORDIA-AGENT-0001..0005: puente, iconos AutoMirrored #39, actions SHA-pinned, verificación variantes #58). Conflictos resueltos preservando fases 19-21 (se restauró la cadena `today_what_now_action`). **420 tests verdes, lint 0 errores/95 warnings.**

## Áreas modificadas

- ui/screens (Today, TaskDetail, NoteEditor, Conversations, TaskDetail), ui/components (AppComponents), backup (BackupManager, BackupManagerTest, BackupSecurityRules), seguridad (AndroidManifest: ReminderResyncReceiver), strings (restaurada `today_what_now_action`), docs AI_AUTONOMY (BACKLOG, RUN_LOG).

## Tests ejecutados

- `:app:testPreviewSafeDebugUnitTest` → **420 tests, 0 fallos, 0 errores** (tras el merge).
- `:app:compilePreviewSafeDebugKotlin` → BUILD SUCCESSFUL tras el merge.
- Matriz de 6 variantes: `assemble{PreviewSafe,PreviewFull,PreviewAdvanced}{Debug,Release}` → 6 APK regenerados tras el merge (debug 35.21 MB firmados con Android Debug; release unsigned 2.5-2.56 MB).
- `lintPreviewSafeDebug` → 0 errores, 95 warnings (los restantes son SKIP documentado: UseKtx 42, PluralsCandidate 31, GradleDependency 7, ModifierParameter 7, UnusedResources 4 falsos positivos cross-variante, UnusedAttribute 3, KaptUsageInsteadOfKsp 1).

## Problemas conocidos

- Release builds salen sin firmar (no hay keystore en el entorno); la firma la gestiona el workflow de publicación en CI (`ORDIA_KEYSTORE_PATH/PASSWORD`, `ORDIA_KEY_ALIAS`, `ORDIA_KEY_ALIAS_PASSWORD`).
- Advertencia `Support for language version 2.0+ in kapt is in Alpha` (cosmético, kapt cae a 1.9).
- Sin dispositivo Android conectado al entorno → la instalación/verificación en hardware de la Fase 26 queda pendiente (APK listo y firmado).

## Bloqueos

- Fase 26 (instalación en teléfono): requiere conectar un dispositivo Android a este PC.
- Fases 27-30 (main): requieren autorización humana para el merge a `main` (protegido; el agente jamás hace push/auto-merge a `main`).

## Siguiente tarea recomendada

- **Push** de `jules/autonomous-ordia` (HEAD `d114304`) a origin (misma rama, sin tocar `main`).
- Decisión humana sobre el merge a `main` (vía PR/merge por persona).
- Conectar un teléfono para la Fase 26 y entregar el APK `app/build/outputs/apk/previewSafe/debug/app-previewSafe-debug.apk`.
- (P3) Pulido visual de pantallas renovadas del workspace.

## PR pendiente

- Ninguna activa. `jules/autonomous-ordia` está ahead 23 de origin; el merge de sync `d114304` está en local.

## Estado CI

- `android-ci.yml` activo en `main` y en la rama autónoma; verify corre en push/PR hacia ambas.
