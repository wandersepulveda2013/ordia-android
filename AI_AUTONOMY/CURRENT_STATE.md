# CURRENT_STATE — Ordía

> Actualizar AL FINAL de cada sesión autónoma.

## Estado

- **Fecha/hora (UTC)**: 2026-08-10 (corrección de infraestructura del sistema autónomo)
- **Branch de trabajo**: `jules/autonomous-ordia` (HEAD `cc1a1e3`)
- **main**: `d5b3b60` — contiene SOLO infraestructura de orquestación (workflows), no el rebuild
- **Workflow autónomo (scheduler)**: `.github/workflows/ordia-autonomous-jules.yml` en `main` (cron `17 */2 * * *` + dispatch)
- **Auto-merge**: `.github/workflows/ordia-autonomous-merge.yml` en `main` (pull_request_target + cron `*/15 * * * *` + dispatch)

## Último trabajo realizado

- Corrección de infraestructura (ver RUN_LOG sesión 002):
  - `origin/main` actualizado a `d5b3b60` con la versión definitiva de los 3 workflows:
    - `ordia-autonomous-jules.yml`: cron 2h, `preferred = "jules/autonomous-ordia"`, failsafes
      (variable + bypass), session lock robusto (sessions API + PRs + checks), anti-loop,
      contexto de PRs fallidas, `requirePlanApproval: False`, `automationMode: AUTO_CREATE_PR`.
    - `ordia-autonomous-merge.yml` (NUEVO): auto-merge squash seguro hacia la rama autónoma con
      12 guardas; guard clause explícito contra `main`; logging + comentario post-merge.
    - `android-ci.yml` (NUEVO en main): CI para push/PR hacia `main` y `jules/autonomous-ordia`.
  - Verificado con `git show origin/main:...` que el cron real es `17 */2 * * *` y que NO existe
    camino automático `* → main`.
- (Histórico) Workflow Jules v1 y consolidación del rebuild de Codex (ver sesiones 000-001).
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
- (Sesión actual): Fix a vulnerabilidad de crash en `BackupManager` por manifiesto corrupto en validación de settings.
- (Sesión actual): Reemplazo de Iconos Outlined deprecados a AutoMirrored.

## Áreas modificadas

- intelligence, privacy/IME, context (external/audit), automation, domain, ui/screens, backup, tests,
  shortcuts/quicksettings, backup, manifest/DI/datos/servicios, strings (i18n).

## Tests ejecutados

- `./gradlew test` → 6 variantes, todas verdes.
- `./gradlew lintPreviewSafeDebug` → verde (2 errores corregidos).
- `./gradlew assembleDebug assembleRelease` → verde (solo warnings de deprecación no bloqueantes).

## Problemas conocidos

- Warnings de deprecación no bloqueantes menores restantes.
- El workflow Jules necesita `jules/autonomous-ordia` visible en la API de Sources antes de
  lanzar sesiones (verificado en cada ejecución; si no aparece, la sesión NO se lanza).
- El auto-merge requiere que las PRs de Jules tengan checks exitosos; si `secrets.JULES_API_KEY`
  no está configurado o el conector no ve la rama, el scheduler no lanza sesiones (no falla el job).

## Bloqueos

- Ninguno activo. El único paso que requiere al humano: configurar `secrets.JULES_API_KEY` y
  arrancar el primer ciclo manual (workflow_dispatch).

## Siguiente tarea recomendada

- Arrancar el primer ciclo Jules: ejecutar manualmente el workflow
  `Ordia Autonomous Jules` (Actions → workflow_dispatch) tras confirmar
  `secrets.JULES_API_KEY` y la sincronización de la rama en el conector de Jules.
  Observar después `Ordia Autonomous Merge` (workflow_dispatch o cron `*/15`).

## PR pendiente

- Ninguno (el auto-merge gestiona las PRs autónomas hacia `jules/autonomous-ordia`).

## Estado CI

- `android-ci.yml` activo en `main` y en la rama autónoma; verify corre en push/PR hacia ambas.
- Pendiente la primera ejecución del ciclo autónomo real (requiere `JULES_API_KEY`).
