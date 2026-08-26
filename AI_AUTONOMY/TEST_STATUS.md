# TEST_STATUS

## Suites disponibles
- `NoteDaoTest` (8) — orden PIN primero, CRUD, toggle pin, clear.
- `NoteRepositoryTest` (7) — creación con timestamps, actualización, eliminación, pin, sort.
- `NotepadViewModelTest` (5) — guard de nota en blanco, inserción, actualización con bump de
  `updatedAt`, no-recreación tras eliminación.

## Último resultado (2026-08-26, previewSafeDebug)
- **20/20 PASS**, 0 skipped, 0 failures, 0 errors.

## Flakiness
- Ninguna observada. En CI, correr con `--no-build-cache --rerun-tasks` (cache corrupto conocido).

## Comando local
`./gradlew :app:testPreviewSafeDebugUnitTest`
