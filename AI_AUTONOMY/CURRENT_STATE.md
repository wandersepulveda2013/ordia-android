# CURRENT_STATE — Ordía

> Actualizar AL FINAL de cada sesión autónoma.

## Estado

- **Fecha/hora (UTC)**: 2026-08-16 (sesión 006: actualizador in-app nativo basado en manifiesto)
- **Branch de trabajo**: `jules/autonomous-ordia` (actualizador implementado; commit + push pendientes de cerrar la sesión)
- **main**: `ba5b6eb` (origin) — contiene infraestructura de orquestación + convergencia de autonomía (#39)
- **Workflow autónomo (scheduler)**: `.github/workflows/ordia-autonomous-jules.yml` en `main` (cron `17 */2 * * *` + dispatch)
- **Auto-merge**: `.github/workflows/ordia-autonomous-merge.yml` en `main` (pull_request_target + cron `*/15 * * * *` + dispatch)

## Último trabajo realizado

Sesión 006 — **Actualizador in-app nativo basado en manifiesto** (requisitos del usuario), sobre `jules/autonomous-ordia`:

1. **Feed desacoplado del agente**: `update-manifest-<flavor>.json` por variante en URL estable `https://github.com/wandersepulveda2013/ordia-android/releases/latest/download/update-manifest-<flavor>.json`; APK publicado como `Ordia-3.0-<flavor>-signed.apk` (reconcilia el naming CI/updater; los tags ya no importan al actualizador).
2. **Actualizador habilitado en TODAS las variantes** (`SELF_UPDATE_ENABLED=true`, incluido release): INTERNET + REQUEST_INSTALL_PACKAGES + FileProvider + UpdateInstallActivity + UpdateDownloadReceiver + UpdateInstallResultReceiver movidos al manifest de `main`; previewSafe deja de excluirlos (override deliberado del usuario; tradeoff Play Protect en DECISIONS).
3. **Nuevos**: `UpdateManifest.kt` (parser estricto), `OrdiaUpdateController.kt` (StateFlow check/download/install + `lastCheckAt` en SharedPreferences `ordia_updates`), `UpdateInstallResultReceiver.kt` (PackageInstaller: SUCCESS/PENDING_USER_ACTION/fallo), `UpdatesScreen.kt`.
4. **Reescrito**: `UpdateInstallActivity.kt` → `PackageInstaller.Session(MODE_FULL_INSTALL)` + `STATUS_PENDING_USER_ACTION` + permiso fuentes desconocidas con reanudación.
5. **Editados**: `UpdateSecurityRules` (expectedManifestName/expectedApkName(flavor), isTrustedLatestDownloadUrl, isTrustedApkUrl, isNewerCode, isMandatoryUpdate, rechazo de `..`), `OrdiaUpdateManager` (checkDetailed manifest-driven, Release extendido, downloadProgress/currentDownloadCode/discardCurrent), Navigation (Destination.Updates), MoreScreen (entrada + badge), SettingsScreen (navega a la pantalla), OrdiaRoot (diálogo opcional/mandatory), OrdiaApplication (check de arranque no bloqueante), build.gradle.kts (UPDATE_FLAVOR/UPDATE_MANIFEST_URL), proguard (keep workers updates), manifests.
6. **CI**: SIGN firma las 3 variantes; PUBLISH genera `update-manifest-<flavor>.json` (versionCode `1300000000 + run*100 + attempt`, idéntico a build.gradle.kts) y publica una release inmutable con 9 assets.

## Áreas modificadas

- updates/ (UpdateManifest, OrdiaUpdateController, UpdateInstallResultReceiver, UpdateInstallActivity, UpdateSecurityRules, OrdiaUpdateManager), ui/ (UpdatesScreen, MoreScreen, SettingsScreen, Navigation, OrdiaRoot), OrdiaApplication, build.gradle.kts, proguard-rules.pro, manifests (main/previewFull/previewAdvanced/previewSafe), strings (nav_updates, update_*, updates_*), CI (android-ci.yml), tests (UpdateManifestParserTest nuevo + UpdateSecurityRulesTest ampliado), AI_AUTONOMY (RUN_LOG, CURRENT_STATE, BACKLOG).

## Tests ejecutados

- `:app:compilePreview{Safe,Full,Advanced}{Debug,Release}Kotlin` → BUILD SUCCESSFUL (6 variantes).
- `:app:test{PreviewSafe,PreviewFull,PreviewAdvanced}DebugUnitTest` → BUILD SUCCESSFUL. Nuevos: `UpdateManifestParserTest` 11/11, `UpdateSecurityRulesTest` 9/9 en verde; resto de la suite (420+ tests) intacta.
- `:app:lint` (todas las variantes) → sin errores nuevos (warnings SKIP documentados).
- Errores de compilación corregidos en primera pasada: import `Release`, smart-cast de `state` delegado, `dp` en OrdiaRoot, `setAppVersionCode` eliminado, `commit(IntentSender)`, `PackageInstaller.STATUS_SUCCESS`.

## Problemas conocidos

- Release builds salen sin firmar localmente (keystore solo en CI: `ORDIA_KEYSTORE_PATH/PASSWORD`, `ORDIA_KEY_ALIAS`, `ORDIA_KEY_ALIAS_PASSWORD`).
- Sin dispositivo ADB conectado → verificación física del flujo de instalación (PackageInstaller, confirmación final de Android, rechazo, descarga interrumpida) pendiente de hardware.
- Advertencia kapt (cosmético, sin impacto).
- Strings viejas de check (`settings_update_*`, `update_fail_*`) quedan sin uso tras mover la UI → warnings de lint UnusedResources cross-variante (SKIP documentado).

## Bloqueos

- Verificación física del actualizador: requiere conectar un dispositivo Android a este PC.
- Merge a `main`: protegido; el humano decide (el agente jamás hace push/auto-merge a `main`).

## Siguiente tarea recomendada

- **Cerrar la sesión**: commit del actualizador + memoria y push de `jules/autonomous-ordia`.
- Decisión humana sobre el merge a `main` (PR/merge por persona).
- Conectar un teléfono, instalar un release reciente (publicado por CI) y verificar: badge, diálogo opcional/mandatory, descarga con progreso, instalación con confirmación final de Android, rechazo, sin internet.
- (P2) Revisar coherencia de cadenas nuevas (updates_*).

## PR pendiente

- Ninguna activa. `jules/autonomous-ordia` pendiente de push del actualizador (commit de sesión 006).

## Estado CI

- `android-ci.yml` activo en `main` y en la rama autónoma; verify corre en push/PR hacia ambas. Con el cambio de sesión 006, la publicación (sign+publish) sigue siendo solo en push a `main` y ahora publica las 3 variantes con manifiesto por flavor.
