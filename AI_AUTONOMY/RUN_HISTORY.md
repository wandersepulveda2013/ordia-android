# RUN_HISTORY — Ordía (bloc de notas)

> Un resumen breve por ejecución de la automatización
> `openhands/autonomous-notes`. Entradas nuevas arriba.

## RUN 003 — 2026-08-27

- **Objetivo:** P1 — autosave debounced en el editor (NEXT_TASKS).
- **Hallazgo:** editor solo persistía al volver atrás; proceso muerto o actividad
  destruida sin saved-state perdía el texto. Riesgo real de pérdida de datos.
- **Cambio:** `NotepadViewModel` con ciclo de draft (`beginDraft`/`autosave`/
  `commitDraft`) y persistencia compartida `persist` bajo un `draftId`; debounce
  de 800ms; UI cableada (`NoteEditorScreen` desacoplado a `onAutosave`/`onCommit`,
  `NotepadApp` llama a `beginDraft`). Se preservan BUG-002 (sin notas fantasma
  vacías), sin duplicado en back-save (mismo `draftId`) y save-after-delete
  (ya no resucita una nota borrada mientras se escribe).
- **Tests:** `testPreviewSafeDebugUnitTest` → **29/29 verdes** (8 nuevos: debounce,
  cancelación por tecleo rápido, no duplicado en back, nota en blanco, ghost
  autodestruido, save-after-delete, draft sobre nota existente).
- **Commit:** ver `git log openhands/autonomous-notes`.
- **Estado:** limpio; push a `origin/openhands/autonomous-notes`.
- **Siguiente tarea:** P2 — búsqueda de notas (filtro título/contenido).

## RUN 002 — 2026-08-26

- **Objetivo:** continuar ejecución 001 (estado incompleto tras reset de sesión)
  y resolver la colisión de pushes paralelos.
- **Hallazgo:** el remoto ya contenía 001 (undo + guardia vacía); en local se
  había reimplementado la guardia y detectado BUG-003 (back del sistema perdía
  la nota). Merge sin perder trabajo.
- **Cambio:** `BackHandler` en `NoteEditorScreen`; merge de ambos frentes;
  `NotepadViewModelTest` ampliado a 7 tests; docs integrados.
- **Tests:** `testPreviewSafeDebugUnitTest` → 22/22 verdes.
- **Commit:** merge en `openhands/autonomous-notes`.
- **Estado:** limpio tras merge y push.
- **Siguiente tarea:** P1 — autosave debounced en el editor (NEXT_TASKS).

## RUN 001 — 2026-08-26

- **Objetivo:** primera ejecución — baseline + riesgo de integridad de datos más valioso.
- **Hallazgo:** producto rebuild = bloc de notas (Room, Compose, 15 tests). Dos
  riesgos P0/P1: borrado sin deshacer (BUG-001) y notas vacías persistidas (BUG-002).
- **Cambio:** snackbar "Deshacer" + `NotepadViewModel.restore`; `save` ignora
  notas nuevas en blanco; `NotepadViewModelTest` (5 tests).
- **Tests:** `testPreviewSafeDebugUnitTest` → 20/20 verdes.
- **Commit:** ver `git log openhands/autonomous-notes`.
- **Estado:** limpio; push a `origin/openhands/autonomous-notes`.
- **Siguiente tarea:** P1 — autosave en el editor (guardado debounced).
