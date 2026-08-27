# RUN_HISTORY — Ordía (bloc de notas)

> Un resumen breve por ejecución de la automatización
> `openhands/autonomous-notes`. Entradas nuevas arriba.

## RUN 008 — 2026-08-27

- **Objetivo:** P1 — arreglar la búsqueda muerta en la UI (icono sin efecto) y el
  solape del campo con la lista (BUG-005).
- **Hallazgo:** el icono de la lupa llamaba `onSearchQueryChange("")` y el "modo
  búsqueda" se infería de `searchQuery.isNotBlank()`: imposible entrar con query
  vacía → la búsqueda de RUN 005 era inalcanzable. Y al activarla, el
  `SearchHeader` y la `LazyColumn` eran hermanos en el `Box` del Scaffold (cada
  uno con `fillMaxSize().padding()`), así que el campo se dibujaba encima de la
  primera fila.
- **Cambio:** `isSearching` explícito (`rememberSaveable`, conmutado por el icono,
  apagado al limpiar la query); contenido del Scaffold envuelto en una `Column`
  que aplica el padding una vez y apila header + lista (vacíos con `weight(1f)`).
- **Tests:** `testPreviewSafeDebugUnitTest` → **40/40 verdes** (10 DAO + 7 Repo +
  2 UI + 18 ViewModel + 3 `NotesListSearchInteractiveTest`). `assembleRelease`
  3 variantes → BUILD SUCCESSFUL.
- **Commit:** `fix(notes): make search reachable from UI and stop header overlapping list`.
- **Estado:** commit creado; **falta push** al cierre (se hará al final).
- **Siguiente tarea:** candados de búsqueda resueltos; ver NEXT_TASKS (P2 #2
  confirmación de borrado / P3 accesibilidad / extender tests de UI del editor).

## RUN 007 — 2026-08-27

- **Objetivo:** P1 — hacer que el "Deshacer" de borrado sea seguro ante la
  reutilización de ids por SQLite (BUG-004).
- **Hallazgo:** `restore` llamaba a `repo.save(note)` (REPLACE) sin comprobar si el
  id original seguía libre; si otra nota lo reutilizaba, el undo pisaba la nota viva
  (_lost update_), coherente con la reutilización de rowids de SQLite.
- **Cambio:** `NotepadViewModel.restore` comprueba `repo.get(note.id)`: id libre →
  reutiliza (respeta `createdAt`/id); id reutilizado → reinserta bajo id nuevo
  (0 → autoGenerate) conservando título/contenido. Nunca sobrescribe una nota viva.
- **Tests:** `testPreviewSafeDebugUnitTest` → **37/37 verdes** (10 DAO + 7 Repo +
  2 UI + 18 ViewModel): +1 test de regresión del restore (el caso id-libre ya lo
  cubría `deleteThenRestore_keepsSameIdAndContent`).
- **Commit:** fix(notes): safe restore when original id was reused by another note.
- **Estado:** commit + push realizados.
- **Siguiente tarea:** decidir entre P2 #3 (búsqueda con acentos), test de UI de
  borrado guardado/rotación del editor, o la confirmación de borrado de fijadas;
  ver NEXT_TASKS.

## RUN 005 — 2026-08-27

- **Objetivo:** P2 — búsqueda de notas (filtro título/contenido).
- **Hallazgo:** con muchas notas no hay forma de localizar una; es la carencia de
  mayor valor pendiente (P2 #1). El flujo existente (lista → editor) no tenía
  ninguna vía de filtrado.
- **Cambio:** `observeSearch(query)` en DAO (`LIKE` case-insensitive sobre
  título/contenido), `NoteRepository.observeSearch`, el ViewModel obtiene
  `searchQuery`/`searchResults` con `flatMapLatest`, y `NotesListScreen` añade
  campo de búsqueda (icono lupa en la top bar, campo con limpiar, contador de
  resultados, estado "sin resultados"). `NotepadApp` sirve `searchResults` cuando
  el query no está en blanco. Se eliminó `distinctUntilChanged()` obsoleto
  (redundante sobre StateFlow) y se añadió `@OptIn(ExperimentalCoroutinesApi)`.
- **Tests:** `testPreviewSafeDebugUnitTest` → **35/35 verdes** (10 DAO + 7 Repo +
  17 ViewModel + 1 UI): 3 tests de búsqueda en ViewModel + 3 del DAO. `assembleRelease`
  de las 3 variantes → BUILD SUCCESSFUL.
- **Commit:** feat(notes): add note search (título/contenido).
- **Estado:** pendiente de commit/push al cierre.
- **Siguiente tarea:** decidir P2 (título largo del editor o confirmacíón de borrado
  de fijadas), o test de UI/búsqueda; ver NEXT_TASKS.

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

## 2026-08-27 — RUN 006 — título de una línea (P2 #1)
- **Objetivo:** resolver P2 #1 (título del editor no limitado en la UI minimalista).
- **Hallazgo:** `singleLine=true` es solo visual; al **pegar** texto, el `\n` entraba
  igual en los datos (lo demostró el test de regresión al fallar). La lista muestra
  títulos con `maxLines=1`, así que los saltos incrustados quedaban invisibles pero
  persistidos → anomalía de datos real.
- **Cambio:** en `NoteEditorScreen`, `singleLine=true` + aplanar `\n` en
  `onValueChange` del título (se sustituye por espacio) para que el dato sea de una
  línea de verdad, no solo la vista. Test: `titleField_isSingleLine_dropsEmbeddedNewline`.
- **Tests:** `testPreviewSafeDebugUnitTest` → 36/36 verdes (10 DAO + 7 Repo + 2 UI + 17
  VM). `assembleRelease` 3 variantes OK (3m43s).
- **Commit:** `fix(editor): enforce single-line title in data and view`.
- **Estado:** limpio; pendiente push a `origin/openhands/autonomous-notes`.
- **Siguiente tarea:** P2 #2 (confirmación de borrado — probablemente OK con undo) o
  ampliar tests de UI del editor (back tras autosave no duplica; "Hecho"/flecha hace
  commit como el back).
