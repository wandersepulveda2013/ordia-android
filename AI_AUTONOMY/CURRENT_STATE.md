# CURRENT_STATE — Ordía

> Actualizar AL FINAL de cada sesión autónoma.

## Estado

- **Fecha/hora (UTC)**: 2026-08-11 (sesión OpenHands 004 — autonomía nocturna, ciclo 1: parser)
- **Branch de trabajo**: `jules/autonomous-ordia` (HEAD inicial `35fb204`)
- **main**: contiene SOLO infraestructura de orquestación (workflows), no el rebuild
- **Workflow autónomo (scheduler)**: `.github/workflows/ordia-autonomous-jules.yml` en `main` (cron `17 */2 * * *` + dispatch)
- **Auto-merge**: `.github/workflows/ordia-autonomous-merge.yml` en `main` (pull_request_target + cron `*/15 * * * *` + dispatch)

## Último trabajo realizado

- **Sesión OpenHands 004 (autonomía nocturna) — Ciclo 1: NaturalTaskParser**. Se auditó el parser
  con un probe JVM reproducible y se encontraron 4 bugs reales (3 P1 + 1 P2):
  - BUG1 (P1): fecha numérica sin año en el pasado NO rodaba al año siguiente (inconsistente con
    fechas con nombre de mes). Provocaba tareas con fecha pasada → recordatorio nunca dispara.
  - BUG2 (P1): "esta mañana/tarde/noche" no reconocidas; además "esta mañana" se interpretaba como
    "el día de mañana" (contiene "mañana").
  - BUG4 (P1): "urgente" como palabra inicial no se detectaba como prioridad sin prefijo !/#.
  - BUG3 (P2, todavía OPEN): números escritos en expresiones relativas ("en dos horas").
  - Se corrigieron BUG1/2/4 con fix mínimo y 11 tests de regresión. Commit `fb53e8c`.
- **Verificación JVM**: 136 tests del dominio PASS (125 previos + 11 nuevos); smoke 25 assertions OK.
- `./gradlew test/lint/assemble`: sigue NO VERIFICADO (sin Android SDK en el entorno).

## Áreas modificadas

- intelligence, privacy/IME, context (external/audit), automation, domain (parser), ui/screens,
  shortcuts/quicksettings, backup, manifest/DI/datos/servicios, strings (i18n).

## Tests ejecutados

- **NO VERIFICADO (gradle/Android)**: no se ejecutó `./gradlew test`/`lint`/`assemble` (sin Android SDK).
- **VERIFICADO (JVM/kotlinc)**: `bash tools/run_domain_checks.sh` → 25 assertions OK;
  136 tests unitarios del dominio OK (incl. 11 nuevos de regresión del parser).

## Problemas conocidos

- Warnings de deprecación no bloqueantes (ej. `Icons.Outlined.InsertDriveFile` → AutoMirrored) — ver BACKLOG.
- `NoteBlocks.kt` y `TaskSnapshotCodec.kt` (dominio) dependen de `org.json` (API Android); en tests
  se sustituye por `org.json:json:20231013` real. Acoplamiento del dominio a Android, pero funcional.
- Tests de `backup`/`context`/`repositories` requieren DAOs/RoomDatabase/Context (no ejecutables en
  JVM pura sin Robolectric/Android SDK); no verificados.
- Parser: números escritos en expresiones relativas ("en dos horas") no parseados (P2, OPEN).
- El workflow Jules necesita `jules/autonomous-ordia` visible en la API de Sources antes de lanzar.
- El auto-merge requiere `secrets.JULES_API_KEY` configurado y checks exitosos.

## Bloqueos

- Ninguno activo. El único paso que requiere al humano: configurar `secrets.JULES_API_KEY` y
  arrancar el primer ciclo manual (workflow_dispatch).

## Siguiente tarea recomendada

- Continuar autonomía: Ciclo 2 = auditoría estática de persistencia (Room: cascadas, índices,
  transacciones, N+1, restore atómico). Después recordatorios end-to-end, notas, rutinas.
  BUG3 (números escritos en parser) queda como P2 abierto para más adelante.

## PR pendiente

- Ninguno (el auto-merge gestiona las PRs autónomas hacia `jules/autonomous-ordia`).

## Estado CI

- `android-ci.yml` activo en `main` y en la rama autónoma; verify corre en push/PR hacia ambas.
- Pendiente la primera ejecución del ciclo autónomo real (requiere `JULES_API_KEY`).
