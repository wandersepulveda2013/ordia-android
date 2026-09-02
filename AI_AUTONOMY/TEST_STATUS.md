# TEST_STATUS - Ordia (notepad minimalista)

## Suites

- `app/src/test/java/com/ordia/app/ui/NotepadViewModelTest` (nuevo 2026-09-02.call
- `app/src/test/java/com/ordia/app/data/NoteDaoTest`
- `app/src/test/java/com/ordia/app/data/NoteRepositoryTest`

## Ultimo resultado (2026-09-02

- `:app:testPreviewSafeDebugUnitTest` -> BUILD SUCCESSFUL
- `:app:testPreviewFullDebugUnitTest` -> BUILD SUCCESSFUL
- `:app:testPreviewAdvancedDebugUnitTest` -> BUILD SUCCESSFUL
- Total: 19 tests por flavor,0 fallos
- `:app:assembleRelease` -> BUILD SUCCESSFUL (3 APKs.call

## Fallos conocidos / flakiness

- Sin evidencia de fallos ni flakiness.

## Cobertura relevante

- `NotepadViewModel.save()`: skip de nuevas vacias, insert, update de existente dejada vacia; cubierto por `NotepadViewModelTest`.
