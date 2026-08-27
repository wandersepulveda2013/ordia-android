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

## 2026-08-27 — openhands/autonomous-notes — fix reseed (sesión en paralelo detectada)
Objetivo: integrar rama remota más avanzada y buscar valor nuevo. Hallazgo: la rama remota
ya contenía BackHandler, guarda de blancas y autosave debounceado (mi trabajo local era
duplicado redundante → lo descarté adoptando la rama remota). Bug nuevo: el reseed
`LaunchedEffect(note?.id)` en NoteEditorScreen borraba lo escrito al recrear la pantalla
(instantánea obsoleta de la BD vs texto en `rememberSaveable`). Cambio: eliminar el reseed.
Tests: 29 JVM (previewSafe) verdes; compila las 3 variantes. Commit: pendiente.

## 2026-08-27 — test de regresión UI del back-save
Objetivo: reconciliar la rama con el trabajo remoto avanzado y aportar valor no duplicado.
Hallazgo: la rama remota ya resolvía el bug P0 (BackHandler + autosave + guarda de blancas); mi fix local era redundante. Valor único: test de regresión UI del back del sistema.
Cambio: descarté mis commits redundantes (reset --hard a origin/openhands/autonomous-notes), añadí deps de test de UI (ui-test-junit4, activity-compose) y reescribí NoteEditorBackSaveTest contra la API onAutosave/onCommit.
Tests: testPreviewSafeDebugUnitTest → 30/30 verdes (8 DAO + 7 Repo + 14 ViewModel + 1 UI).
Commit: test(editor): cover system-back save.
