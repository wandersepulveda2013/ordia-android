# Ordia Android — Memoria del repositorio

## Qué es
Ordia es una app Android local-first (Kotlin + Jetpack Compose) para organización personal:
tareas, planificación, notas, hábitos, rutinas y enfoque. Sin permiso INTERNET en el core
(excepto el update checker contra GitHub Releases). Diseño minimalista blanco/negro.

## Autonomía 24/7 (sistema existente)
El repo tiene dos workflows que forman un bucle autónomo continuo:

- `.github/workflows/ordia-autonomous-jules.yml` — cada 15 min lanza una sesión de Jules
  que trabaja sobre la rama `jules/autonomous-ordia` y abre PRs hacia esa misma rama.
  Tiene failsafes (var `ORDIA_AUTONOMY_ENABLED`, archivo `AI_AUTONOMY/AUTONOMY_BYPASS`),
  session lock y anti-loop.
- `.github/workflows/ordia-autonomous-merge.yml` — cada 15 min (y en cada PR event) hace
  auto-merge squash de PRs seguras (CI verde, no draft, no fork) hacia `jules/autonomous-ordia`.

Rama autónoma: `jules/autonomous-ordia`. NUNCA se hace push directo a `main` desde el agente.

## Formato real de ramas de Jules
Jules crea ramas: `jules/autonomous-ordia-{timestamp}` (timestamp 10-20 dígitos).
El regex `JULES_BRANCH_RE` (presente en AMBOS workflows) debe reconocer este formato.

## Builds y tests (local)
- JDK 17 (jvmTarget=17); Android SDK 36; Gradle 8.13; AGP 8.13.2.
- `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleRelease`
- KSP2 necesita `--no-build-cache --rerun-tasks` en CI (cache incremental corrupto).
- 28+ tests unitarios. Room usa KSP. Schema export solo para AutoMigration (no usado).

## Versiones / releases
- Tag format: `v{versionName}-{versionCode}` (ej: `v3.0.1-12`).
- CI publica APK firmado en GitHub Releases en cada push a main.
- In-app update checker consulta GitHub Releases API y compara versionCode.

## Estado (2026-08-13)
- main en v3.0.1-12. PR #32 merged (fix borrado subárbol + 20 mejoras visuales/funcionales).
- Handoff completo en `AUTONOMOUS_HANDOFF.md`.
- Archivos de memoria del agente en `AI_AUTONOMY/` (MISSION, CURRENT_STATE, BACKLOG, etc.).
