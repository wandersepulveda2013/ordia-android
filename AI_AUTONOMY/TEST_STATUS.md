# TEST_STATUS — Ordía (bloc de notas)

## Suites disponibles

- `:app:testPreviewSafeDebugUnitTest` (JVM + Robolectric 4.14.1, sdk=33):
  - `NoteDaoTest` — Room in-memory (8 tests).
  - `NoteRepositoryTest` — FakeDao en memoria (7 tests).
  - `NotepadViewModelTest` — `Dispatchers.setMain(StandardTestDispatcher)` (5 tests).
- Variantes `previewFull` / `previewAdvanced`: mismo `src/test` (sin tests
  específicos de flavor por ahora).

## Último resultado

- 2026-08-26 (ejecución 001): `testPreviewSafeDebugUnitTest` → **20 tests,
  0 fallos, 0 errores** (BUILD SUCCESSFUL).

## Tests recientemente agregados

- `NotepadViewModelTest` (5): creación, actualización conservando `createdAt`,
  rechazo de nota nueva vacía, borrado+restauración con mismo id, pin.

## Flakiness

- Ninguna observada.

## Cobertura relevante / huecos conocidos

- Sin tests de UI (Compose). La navegación lista↔editor y el snackbar de
  deshacer se validan solo manualmente.
- Sin tests de proceso-muerte (`rememberSaveable` del editor).
