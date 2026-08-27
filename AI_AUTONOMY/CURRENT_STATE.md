# CURRENT_STATE — Ordía (bloc de notas)

> Estado actual del producto para la automatización `openhands/autonomous-notes`.
> El estado del producto anterior a la reconstrucción está en
> `CURRENT_STATE_PRE_REBUILD.md` (histórico).

## Estado general

- **Producto:** bloc de notas minimalista (rebuild completo en `main`, commit
  `ceb1ff3`): lista de notas con pin, editor título+contenido, Room `ordia.db`.
- **Branch de trabajo:** `openhands/autonomous-notes` (creada desde `main`
  `ceb1ff3` el 2026-08-26).
- **Arquitectura:** `MainActivity` → `NotepadApp` (navegación lista↔editor por
  estado, sin Navigation component en uso) → `NotepadViewModel` →
  `NoteRepository` → `NoteDao` (Room v1, `exportSchema = true`).
- **Sin red:** ningún permiso INTERNET en el núcleo; flavors solo añaden
  metadatos de update checker (URLs en BuildConfig, sin código en `src/main`).

## Módulos críticos

- Persistencia: `data/NoteDatabase.kt`, `data/NoteDao.kt` (REPLACE en insert).
- Edición: `ui/screens/NoteEditorScreen.kt` — editor desacoplado de persistencia
  (`onAutosave` por cambio de texto, `onCommit` al atrás/"Hecho").
- Persistencia del editor: `ui/NotepadViewModel.kt` — ciclo de draft
  `beginDraft`/`autosave`/`commitDraft`, persistencia compartida bajo `draftId`,
  debounce 800 ms.
- Lista: `ui/screens/NotesListScreen.kt` — borrado con snackbar de deshacer.

## Áreas recientemente modificadas (ejecuciones 001-003)

- `ui/NotepadViewModel.kt` (+`restore`, guardia de nota vacía en `save`; y en RUN 003
  el ciclo de draft `beginDraft`/`autosave`/`commitDraft` + `persist` compartida).
- `ui/screens/NotesListScreen.kt` (snackbar undo).
- `ui/NotepadApp.kt` (cableado de `onRestoreNote`; en RUN 003 llama a
  `beginDraft` al abrir el editor y pasa `onAutosave`/`onCommit`).
- `ui/screens/NoteEditorScreen.kt` (`BackHandler` BUG-003; en RUN 003 cambia a
  `onAutosave`/`onCommit`, el `BackHandler` ejecuta `onCommit`).
- `src/test/.../NotepadViewModelTest.kt` (nuevo, 14 tests tras RUN 003).

## Fix esta ejecución
- Eliminado el reseed del editor (pérdida de texto al recrear).

## Riesgos abiertos

- Transición de borrado por deslizamiento o multi-selección (solo hay botón con
  snackbar de deshacer).
- Notas existentes pueden quedar vacías si el usuario las borra todo (decisión
  deliberada, no se autodestruyen).
- Sin búsqueda de notas (P2, siguiente tarea).

## Bloqueos actuales

- Ninguno. Entorno local: JDK 21 (compila con jvmTarget 17), Android SDK 36 en
  `~/android-sdk`; `./gradlew :app:testPreviewSafeDebugUnitTest` funciona.

## Estado de tests

- Última ejecución: 29/29 verdes (`testPreviewSafeDebugUnitTest`, ejecución 003).
  Detalle en `TEST_STATUS.md`.
