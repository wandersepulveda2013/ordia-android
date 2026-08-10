# CURRENT_STATE — Ordía

> Actualizar AL FINAL de cada sesión autónoma.

## Estado

- **Fecha/hora (UTC)**: 2026-08-10 (bootstrap del sistema autónomo + workflow Jules)
- **Branch**: `jules/autonomous-ordia`
- **HEAD**: `969059d` + commit de workflow Jules (ver `git log` para el hash exacto)
- **Workflow autónomo**: `.github/workflows/ordia-autonomous-jules.yml` (cron `17 */2 * * *` + dispatch)

## Último trabajo realizado

- Workflow autónomo Jules actualizado y validado (ver RUN_LOG sesión 001):
  - Rama de trabajo `jules/autonomous-ordia` (nunca `main`); cron cada 2h; failsafes por
    variable `ORDIA_AUTONOMY_ENABLED` y por archivo `AI_AUTONOMY/AUTONOMY_BYPASS`; session lock
    por PR abierta; verificación de rama contra la API de Jules; prompt maestro ampliado.
- Consolidación y publicación del rebuild de Codex (`feature/ordia-total-rebuild-2026-08-10` → `d34ffd8`):
  1. `feat(intelligence)`: elimina modelo local TFLite simulado; unifica proveedor real.
  2. `feat(privacy)`: guardián de teclado y filtro de privacidad contextual endurecidos.
  3. `feat(context)`: confirmación externa consentida y auditoría de contexto.
  4. `feat(automation)`: reglas locales explicables y reversibles.
  5. `feat(domain)`: paleta de comandos, temporizador de foco, calendario de planificador.
  6. `feat(ui)`: pantallas, navegación y estado renovados.
  7. `feat(shortcuts)`: tile de captura en Quick Settings y accesos directos.
  8. `feat(backup)`: mejora restauración y seguridad del respaldo.
  9. `feat(integration)`: manifiesto, DI, datos, servicios y strings cableados.
- Correcciones aplicadas durante la validación del rebuild:
  - `ContextPrivacyFilter` fragmentos de paquete sin punto (banca genérica como `mobilebanking`).
  - `OrdiaCaptureTileService`: `@SuppressLint("StartActivityAndCollapseDeprecated")`.
  - `TaskDetailScreen`: `stringResource` en ámbito composable en vez de `context.getString`.

## Áreas modificadas

- intelligence, privacy/IME, context (external/audit), automation, domain, ui/screens,
  shortcuts/quicksettings, backup, manifest/DI/datos/servicios, strings (i18n).

## Tests ejecutados

- `./gradlew test` → 6 variantes, todas verdes.
- `./gradlew lintPreviewSafeDebug` → verde (2 errores corregidos).
- `./gradlew assembleDebug assembleRelease` → verde (solo warnings de deprecación no bloqueantes).

## Problemas conocidos

- Warnings de deprecación no bloqueantes (ej. `Icons.Outlined.InsertDriveFile` → AutoMirrored).
- El workflow Jules necesita `jules/autonomous-ordia` visible en la API de Sources antes de
  lanzar sesiones (verificado en cada ejecución; si no aparece, la sesión NO se lanza).

## Bloqueos

- Ninguno activo.

## Siguiente tarea recomendada

- Arrancar el primer ciclo Jules: ejecutar manualmente el workflow
  `Ordia Autonomous Jules` (Actions → workflow_dispatch) tras confirmar
  `secrets.JULES_API_KEY` y la sincronización de la rama en el conector de Jules.

## PR pendiente

- Ninguno.

## Estado CI

- Pendiente de la primera ejecución del ciclo autónomo.
