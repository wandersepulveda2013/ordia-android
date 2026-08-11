# AGENTS.md — Guía para agentes de IA en Ordía

> Este archivo aplica a cualquier agente autónomo (Jules, Codex, OpenCode, otros) que
> trabaje en este repositorio. Léelo antes de tocar nada.

## 0. Lee primero

1. `AI_AUTONOMY/MISSION.md`
2. `AI_AUTONOMY/CURRENT_STATE.md`
3. `AI_AUTONOMY/BACKLOG.md`
4. `AI_AUTONOMY/DECISIONS.md`
5. `AI_AUTONOMY/RUN_LOG.md`

## 1. Reglas de rama

- El trabajo autónomo vive EXCLUSIVAMENTE en `jules/autonomous-ordia`.
- `main` está protegido: jamás se hace push/auto-merge a `main` desde un agente.
- Los cambios se integran mediante PR a `jules/autonomous-ordia` (o commit directo en esa rama
  si el entorno lo permite), y un humano decide si llegan a `main`.
- No eliminar ramas remotas.

## 2. Cómo proceder (ciclo corto)

1. Lee `CURRENT_STATE.md` y `BACKLOG.md`.
2. Revisa `git log --oneline -5` y `git status` para saber dónde estás.
3. Toma UN ítem del backlog (P0 > P1 > P2 > P3) o una mejora evidente de estabilidad/integridad.
4. Haz el cambio mínimo, con tests si aplica.
5. Ejecuta las pruebas pertinentes (6 variantes si tocas código compartido).
6. Revisa tu diff; crea un commit pequeño y descriptivo.
7. Actualiza `BACKLOG.md` (marca FIXED/VERIFIED con evidencia), `CURRENT_STATE.md` y `RUN_LOG.md`.

## 3. Prohibiciones

- NO simular capacidades (IA, backup, descargas, éxito). Todo debe ser real y verificable.
- NO inventar resultados; documenta exactamente lo probado.
- NO eliminar tests para esconder fallos; no comentar tests para lograr verde.
- NO introducir secretos en el repo; nunca mostrar `JULES_API_KEY` ni valores similares.
- NO hacer `git push --force`, ni rebase/amend sobre ramas compartidas, ni borrar historial.
- NO tocar `main` ni la rama de otra persona.

## 4. Definición de terminado

Una tarea está terminada cuando:

1. Existe implementación real (no stubs).
2. La interfaz la utiliza (si es UI).
3. La persistencia/capacidad funciona de verdad.
4. Las pruebas relevantes pasan y se registran.
5. No hay errores de consola no controlados.
6. La evidencia se guarda en `RUN_LOG.md`.
7. El `CURRENT_STATE.md` se actualizó.
8. Se creó un commit descriptivo.

## 5. Nota para humanos

El sistema autónomo es experimental. Supervisa `jules/autonomous-ordia` periódicamente.
Cualquier sesión sospechosa se puede detener desactivando `ORDIA_AUTONOMY_ENABLED`
(ver `AI_AUTONOMY/SUPERVISION.md`).

## 6. Verificación sin Android SDK (entorno JVM puro)

Cuando el entorno NO tenga Android SDK (gradle inutilizable), la verificación del dominio
es reproducible con kotlinc + JUnit4 + stubs:

- Instalar: OpenJDK 21, kotlinc 2.1.20, junit-4.13.2, hamcrest-core-1.3, kotlin-stdlib-2.1.20,
  org.json:json:20231013, kotlinx-coroutines-core-jvm 1.10.2, kotlinx-coroutines-test-jvm 1.10.2.
- `bash tools/run_domain_checks.sh` → compila `RoomStubs.kt` + subconjunto del dominio y corre
  el smoke (25 assertions). Es la baseline mínima de dominio.
- Para correr TODOS los tests del dominio: compilar `tools/domain-smoke/RoomStubs.kt`,
  `tools/domain-smoke/PreferenceStubs.kt`, `app/src/main/java/com/ordia/app/data/local/Entities.kt`,
  `app/src/main/java/com/ordia/app/data/local/TaskTree.kt`, `app/src/main/java/com/ordia/app/domain/*.kt`
  y `app/src/test/java/com/ordia/app/domain/*.kt` con kotlinc (-cp las jars anteriores) y ejecutar
  `org.junit.runner.JUnitCore <testes>` → 125 tests.
- LIMITACIÓN: tests de `backup`, `context`, `repositories`, `ime`, UI y DAOs requieren
  DAOs/RoomDatabase/Context (Android). No son ejecutables en JVM pura; marcar NO VERIFICADO.
