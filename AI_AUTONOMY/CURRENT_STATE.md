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
- Lista: `ui/screens/NotesListScreen.kt` — borrado con snackbar de deshacer y
  búsqueda (filtro por título/contenido).

## Áreas recientemente modificadas (ejecuciones 001-006)

- `ui/NotepadViewModel.kt` (+`restore`, guardia de nota vacía en `save`; y en RUN 003
  el ciclo de draft `beginDraft`/`autosave`/`commitDraft` + `persist` compartida; en
  RUN 005 `searchQuery`/`searchResults` con `flatMapLatest`).
- `ui/screens/NotesListScreen.kt` (snackbar undo; en RUN 005 campo de búsqueda con
  contador de resultados y estado "sin resultados").
- `ui/NotepadApp.kt` (cableado de `onRestoreNote`; en RUN 003 llama a `beginDraft`
  y pasa `onAutosave`/`onCommit`; en RUN 005 sirve `searchResults` si el query no
  está en blanco).
- `ui/screens/NoteEditorScreen.kt` (`BackHandler` BUG-003; en RUN 003 cambia a
  `onAutosave`/`onCommit`, el `BackHandler` ejecuta `onCommit`; en RUN 006 el título
  pasa a `singleLine=true` y se aplanan `\n` en `onValueChange` → dato de una línea).
- `src/test/.../NotepadViewModelTest.kt` (nuevo, 17 tests tras RUN 005).
- `src/test/.../NoteEditorBackSaveTest.kt` (RUN 004 back-save; RUN 006 +test
  single-line del título).

## Fix esta ejecución
- RUN 007: undo seguro ante reutilización de ids (BUG-004) — `restore` reutiliza el
  id original solo si sigue libre; si otra nota lo reutilizó, inserta bajo id nuevo
  y nunca sobrescribe una nota viva.

## Riesgos abiertos

- Transición de borrado por deslizamiento o multi-selección (solo hay botón con
  snackbar de deshacer).
- Notas existentes pueden quedar vacías si el usuario las borra todo (decisión
  deliberada, no se autodestruyen).
- La búsqueda no normaliza acentos (p. ej. "café" no encuentra "cafe"); aceptado
  de momento, ver NEXT_TASKS P2 #3.

## Bloqueos actuales

- Ninguno. Entorno local: JDK 21 (compila con jvmTarget 17), Android SDK 36 en
  `~/android-sdk`; `./gradlew :app:testPreviewSafeDebugUnitTest` funciona.

## Estado de tests

- Última ejecución: 37/37 verdes (`testPreviewSafeDebugUnitTest`, RUN 007):
  10 DAO + 7 Repo + 2 UI + 18 ViewModel. `assembleRelease` 3 variantes OK.
  Detalle en `TEST_STATUS.md`.
