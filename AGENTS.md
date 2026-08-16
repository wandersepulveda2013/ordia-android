# AGENTS.md — Guía para agentes de IA en Ordía

> Este archivo aplica a cualquier agente autónomo (Jules, Codex, OpenCode, otros) que
> trabaje en este repositorio. Léelo antes de tocar nada.

## Qué es

Ordia es una app Android local-first (Kotlin + Jetpack Compose) para organización personal:
tareas, planificación, notas, hábitos, rutinas y enfoque. El core no pide permiso INTERNET
(excepto `previewAdvanced` y `full`, que incluyen el update checker por manifiesto).
Diseño minimalista blanco/negro con paleta de acentos elegible por el usuario.

## 0. Lee primero

1. `AI_AUTONOMY/MISSION.md`
2. `AI_AUTONOMY/CURRENT_STATE.md`
3. `AI_AUTONOMY/BACKLOG.md`
4. `AI_AUTONOMY/DECISIONS.md`
5. `AI_AUTONOMY/RUN_LOG.md`

## 1. Reglas de rama

- El trabajo autónomo vive EXCLUSIVAMENTE en `jules/autonomous-ordia`.
- `main` es la rama de producción. Históricamente estaba protegida y un humano decidía
  cuándo integrar; la misión EVOLUCIÓN FINAL autorizó explícitamente la integración del
  rebuild completo (jules → main, merge `0d5ee44` hacia `ba5b6eb0`).
- No se hace push directo a `main` fuera de esas integraciones autorizadas; en general los
  cambios llegan vía PR/merge hacia `jules/autonomous-ordia` y desde allí a `main`.
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
- NO introducir secretos en el repo; nunca mostrar `JULES_API_KEY`, keystores ni valores similares.
- NO hacer `git push --force`, ni rebase/amend sobre ramas compartidas, ni borrar historial.
- NO tocar la rama de otra persona.

## 4. Builds, tests y releases (referencia)

- JDK 17 (jvmTarget=17); Android SDK 36; Gradle 8.13; AGP 8.9.1; Kotlin 2.1.0.
- Room usa KAPT (decisión ORD-036; KSP con Kotlin 2.1.0 embebe una versión vieja de
  kotlinx-serialization y rompe el processor de Room). No revertir a KSP sin documentar.
- Variantes: `previewSafe`, `previewAdvanced`, `full` (debug/release). Los flavors derivan
  el package name y activan capacidades (INTERNET/updater en advanced y full).
- Comandos locales: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleRelease`.
- 50+ tests unitarios. En CI, correr KSP/KAPT con `--no-build-cache --rerun-tasks`
  (cache incremental corrupto).
- El CI de `main` (`android-ci.yml`) verifica las 3 variantes, firma los APKs
  (`Ordia-3.0-{safe,full,advanced}-signed.apk`), publica `update-manifest-<flavor>.json`
  y crea la release inmutable. Requiere secrets `ORDIA_UPDATE_KEYSTORE_*`.
- Tag format: `v{versionName}-{versionCode}` (ej: `v3.0.1-12`).
- El update checker consulta el manifiesto por variante (no la API de GitHub).

## 5. Definición de terminado

Una tarea está terminada cuando:

1. Existe implementación real (no stubs).
2. La interfaz la utiliza (si es UI).
3. La persistencia/capacidad funciona de verdad.
4. Las pruebas relevantes pasan y se registran.
5. No hay errores de consola no controlados.
6. La evidencia se guarda en `RUN_LOG.md`.
7. El `CURRENT_STATE.md` se actualizó.
8. Se creó un commit descriptivo.

## 6. Nota para humanos

El sistema autónomo es experimental. Supervisa `jules/autonomous-ordia` periódicamente.
Cualquier sesión sospechosa se puede detener desactivando `ORDIA_AUTONOMY_ENABLED`
(ver `AI_AUTONOMY/SUPERVISION.md`).
