# TEST_STATUS — Ordía (bloc de notas)

## Suites disponibles

- `:app:testPreviewSafeDebugUnitTest` (JVM + Robolectric 4.14.1, sdk=33):
  - `NoteDaoTest` — Room in-memory (10 tests).
  - `NoteRepositoryTest` — FakeDao en memoria (7 tests).
  - `NotepadViewModelTest` — `Dispatchers.setMain(StandardTestDispatcher)` (17 tests).
- Variantes `previewFull` / `previewAdvanced`: mismo `src/test` (sin tests
  específicos de flavor por ahora).

## Último resultado

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

## Tests recientemente agregados

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
- Sin tests de proceso-muerte (`rememberSaveable` del editor) más allá del back-save.
