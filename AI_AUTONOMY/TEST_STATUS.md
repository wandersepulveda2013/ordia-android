# TEST_STATUS — Ordía (bloc de notas)

## Suites disponibles

- `:app:testPreviewSafeDebugUnitTest` (JVM + Robolectric, sdk=34 para los de UI):
  - `NoteDaoTest` — Room in-memory (10 tests).
  - `NoteRepositoryTest` — FakeDao en memoria (7 tests).
  - `NotepadViewModelTest` — `Dispatchers.setMain(StandardTestDispatcher)` (22 tests,
  RUN 009: +4 del ciclo de draft — resume en recreación, proceso-muerte, carrera
  commit→beginDraft hacia otra nota, y nota nueva tras back).
  - `NoteEditorBackSaveTest` — UI Compose/Robolectric (2 tests).
  - `NotesListSearchInteractiveTest` — UI Compose/Robolectric (3 tests, RUN 008).
- Variantes `previewFull` / `previewAdvanced`: mismo `src/test` (sin tests
  específicos de flavor por ahora).

## Último resultado

- 2026-08-27 (ejecución 009): `testPreviewSafeDebugUnitTest` → **44 tests,
  0 fallos, 0 errores** (BUILD SUCCESSFUL); `testPreviewAdvancedDebugUnitTest`
  → **44/44**; `testPreviewFullDebugUnitTest` → **44/44**. `assembleRelease`
  3 variantes → OK. Añadidas 4 regresiones del ciclo de draft (BUG-006):
  resume tras recreación, restauración de la sesión tras proceso-muerte,
  carrera commit→abrir otra nota existente, y nota nueva tras back. Pre-fix
  ambas regresiones de carrera fallaban (duplicado, `expected:<2> but was:<3>`).
- 2026-08-27 (ejecución 003): `testPreviewSafeDebugUnitTest` → **29 tests,
  0 fallos, 0 errores** (BUILD SUCCESSFUL).
- 2026-08-27 (ejecución 004): `testPreviewSafeDebugUnitTest` → **30 tests,
  0 fallos, 0 errores** (BUILD SUCCESSFUL). Añadido test de UI para el editor.
- 2026-08-27 (ejecución 005): `testPreviewSafeDebugUnitTest` → **35 tests,
  0 fallos, 0 errores** (BUILD SUCCESSFUL). Añadida la búsqueda de notas
  (P2) + `@OptIn` por `flatMapLatest`. `assembleRelease` 3 variantes OK.
- 2026-08-27 (ejecución 006): `testPreviewSafeDebugUnitTest` → **36 tests,
  0 fallos, 0 errores** (BUILD SUCCESSFUL). Añadido test de regresión del
  título de una línea. `assembleRelease` 3 variantes OK.
- 2026-08-27 (ejecución 007): `testPreviewSafeDebugUnitTest` → **37 tests,
  0 fallos, 0 errores** (BUILD SUCCESSFUL). Añadido test de regresión del undo
  (BUG-004): `restore_whenOriginalIdReusedByAnotherNote_reinsertsUnderFreshId`
  (el caso de id libre lo cubre `deleteThenRestore_keepsSameIdAndContent`).
- 2026-08-27 (ejecución 008): `testPreviewSafeDebugUnitTest` → **40 tests,
  0 fallos, 0 errores** (BUILD SUCCESSFUL). Añadido `NotesListSearchInteractiveTest`
  (3 tests, BUG-005): la lupa abre el campo y filtra, toggle off sale de búsqueda,
  limpiar la query restaura la lista; + aserción de bounds (el campo no solapa la
  primera fila). `assembleRelease` 3 variantes OK.

## Tests recientemente agregados

- `NotepadViewModelTest` RUN 009 (+4, BUG-006, ciclo de draft):
  `beginDraftAgain_resumesLiveDraft_doesNotDuplicateNote` (recomposición de una
  nota nueva cuyo autosave ya creó fila → no duplica), `processDeath_restoresDraftId_avoidsDuplicateNote`
  (sesión de draft restaurada desde `SavedStateHandle`), `beginDraft_afterCommitLaunched_switchesDraftToNewNote`
  (carrera commit→abrir otra nota existente: el rebind prevalece, sin
  cross-contaminación) y `beginDraft_nullAfterCommitLaunched_startsFreshNewNote`
  (carrera commit→"+": la nota nueva se crea fresca, sin volver al draft anterior).
  Antes del fix, las de carrera fallaban con filas duplicadas.
- `NotesListSearchInteractiveTest` (RUN 008, nuevo): regresión UI de BUG-005 —
  el icono de búsqueda era un "no-op" porque `isSearching` se derivaba del texto
  de la query y el icono solo llamaba `onSearchQueryChange("")`. Verifica el
  flujo real: abrir desde la lupa con query vacía, filtrar tecleando, salir del
  modo y restauración de la lista al limpiar; además comprueba por `boundsInRoot`
  que el `SearchHeader` termina por encima de la primera fila de la lista
  (regresión de layout).
- `NotepadViewModelTest` avanzado con el ciclo de draft (autosave): debounce
  (nada se persiste antes de 800 ms), cancelación por tecleo rápido, no duplicado
  al hacer back tras autosave, guardia de nota nueva en blanco (autosave y commit),
  ghost autodestruida si el usuario vacía todo, save-after-delete (no resucitar),
  draft sobre nota existente que conserva `createdAt`.
- `NoteEditorBackSaveTest` (NUEVO, Robolectric+Compose): regresión UI del bug P0
  de pérdida de datos — el retroceso del sistema en el editor debe hacer `onCommit`
  de la edición en curso antes de navegar. Verifica back-save, autosave y navegación.
  RUN 006: `titleField_isSingleLine_dropsEmbeddedNewline` — pegar `\n` en el título
  no debe persistir títulos multilínea (dato aplanado).
- `NotepadViewModelTest` RUN 007 (+1, undo seguro BUG-004): reinsertar bajo id nuevo
  cuando otra nota reutilizó el id del borrado (no sobrescribir la nota viva).
- `NoteDaoTest`/`NotepadViewModelTest` (búsqueda, P2): filters por título y
  contenido case-insensitive (DAO, 3 tests), filtro por query en el ViewModel,
  restaurar todas al limpiar y query por defecto (ViewModel, 3 tests).

## Flakiness

- Ninguna observada.

## Cobertura relevante / huecos conocidos

- Primer test de UI (Compose) añadido: `NoteEditorBackSaveTest` (back del sistema
  en el editor). La navegación lista↔editor y el snackbar de deshacer se siguen
  validando principalmente de forma manual.
- Proceso-muerte del editor cubierto parcialmente desde RUN 009: la sesión de
  draft del ViewModel (`draftId`/`draftWasNew`) se restaura desde
  `SavedStateHandle` con test de regresión a nivel ViewModel. Sigue sin UI test
  Compose que ejercite `rememberSaveable` del editor + recreación real de
  actividad.
