# TEST_STATUS — Ordía (bloc de notas)

## Suites disponibles

- `:app:testPreviewSafeDebugUnitTest` (JVM + Robolectric 4.14.1, sdk=33):
  - `NoteDaoTest` — Room in-memory (8 tests).
  - `NoteRepositoryTest` — FakeDao en memoria (7 tests).
  - `NotepadViewModelTest` — `Dispatchers.setMain(StandardTestDispatcher)` (15 tests).
- Variantes `previewFull` / `previewAdvanced`: mismo `src/test` (sin tests
  específicos de flavor por ahora).

## Último resultado

- 2026-08-27 (ejecución 003): `testPreviewSafeDebugUnitTest` → **29 tests,
  0 fallos, 0 errores** (BUILD SUCCESSFUL).

## Tests recientemente agregados

- `NotepadViewModelTest` avanzado con el ciclo de draft (autosave): debounce
  (nada se persiste antes de 800 ms), cancelación por tecleo rápido, no duplicado
  al hacer back tras autosave, guardia de nota nueva en blanco (autosave y commit),
  ghost autodestruida si el usuario vacía todo, save-after-delete (no resucitar),
  draft sobre nota existente que conserva `createdAt`.

## Flakiness

- Ninguna observada.

## Cobertura relevante / huecos conocidos

- Sin tests de UI (Compose). La navegación lista↔editor y el snackbar de
  deshacer se validan solo manualmente.
- Sin tests de proceso-muerte (`rememberSaveable` del editor).
