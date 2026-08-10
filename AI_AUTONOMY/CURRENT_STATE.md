# CURRENT_STATE — Ordía

> Actualizar AL FINAL de cada sesión autónoma.

## Estado

- **Fecha/hora (UTC)**: 2026-08-10 (sesión de bootstrap del sistema autónomo)
- **Branch**: `jules/autonomous-ordia`
- **HEAD**: `d34ffd8` (inicio del sistema autónomo; incluye todo el rebuild de Codex consolidado)

## Último trabajo realizado

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

- Arrancar el primer ciclo Jules (workflow `ordia-autonomous-jules.yml`, branch `jules/autonomous-ordia`).

## PR pendiente

- Ninguno.

## Estado CI

- Pendiente de la primera ejecución del ciclo autónomo.
