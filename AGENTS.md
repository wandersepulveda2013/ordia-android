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
- Tag format CANÓNICO (contrato CI↔app, ORD-040): `v3.0.<build>-code-<versionCode>`
  (ej: `v3.0.241-code-1300045001`). Debe pasar `UpdateSecurityRules.releaseTagPattern`
  `^v3\.0\.\d+-code-(\d+)$`. El `<versionCode>` se comparte EXACTO entre el APK, el tag
  y el manifiesto; lo calcula el job `verify` de forma monótona
  (`max(1_300_000_000 + run*100 + attempt, maxPublishedVersionCode + 100)`) y lo pasa a
  Gradle vía `ORDIA_VERSION_CODE` + artifact `release-contract.json` que consume `publish`.
  NUNCA publicar tags como `v3.0.0-build.<run_id>`: la app instalada los rechaza.
- Para bootstrap de la app YA instalada (flujo legacy por versionCode), el CI publica
  además `Ordia-3.0-code-<versionCode>.apk` (copia de la APK advanced firmada) + `.sha256`.
- El update checker consulta el manifiesto por variante (no la API de GitHub). La app
  instalada antigua usa el flujo por tag/asset; por eso el tag y el asset legacy importan.

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

---

## ORDÍA NOTES REBUILD (2026-08-17) — bloc de notas avanzado

Misión maestra: reconstruir ORDÍA como un bloc de notas radicalmente simple por fuera y potente por dentro. La UX visible se reconstruye; los datos y capacidades anteriores se conservan (no se borran tablas/objetos).

- Rama de trabajo: jules/notes-rebuild (NO es jules/autonomous-ordia).
- Start destination: Destination.Notes (la home es la lista de notas).
- Sin navegación inferior ni rail: el menú principal es ⋮ arriba a la izquierda.
- DB versión 9 con MIGRATION_8_9 (notes +folderId/favorite/locked/colorHex/trashed/trashedAt; nuevas tablas note_folders, note_labels, note_label_cross_ref, note_versions). Backup versión 9.
- Modelo de bloques: com.ordia.app.domain.NoteBlock + NoteBlockCodec (JSON). Párrafo, headings H1/H2/H3/subtítulo, cita, código, separador, viñetas, numeradas, checklist, tabla, imagen, archivo, link, audio, dibujo, escritura, escáner. Spans en línea (bold/italic/underline/strike/highlight/color/link).
- Editor: NoteEditorScreen — top bar (←/estado-guardado/focus/☆/⋮), título opcional, bloques sin borde, insert sheet categorizado, undo/redo, autosave 800ms.
- Splash: NotesSplash (animación de trazo, respeta reduced-motion).
- Tests: 505 unitarios pasan. Instrumented: NotesMigrationTest (8→9).

### Estado honesto de capacidades (no simular)
- REALES: texto, headings, cita, código, separador, listas, checklist, tabla, imagen (galería), archivo (attach), favorita, fijada, bloquear, duplicar, papelera, búsqueda, vistas/orden, autosave, undo/redo, info, share texto.
- RESERVADAS (bloque existe, UI muestra "próximamente", NO simular): cámara, escáner/OCR, audio/transcripción, dibujo, escritura a mano.

### Regla de oro
Una función medio hecha se marca como no disponible antes que fingirse lista (AGENTS §3).
