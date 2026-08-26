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
- Edición: `ui/screens/NoteEditorScreen.kt` — persiste solo al volver atrás
  (sin autosave todavía; P1 en `NEXT_TASKS.md`).
- Lista: `ui/screens/NotesListScreen.kt` — borrado con snackbar de deshacer.

## Áreas recientemente modificadas (ejecución 001)

- `ui/NotepadViewModel.kt` (+`restore`, guardia de nota vacía en `save`).
- `ui/screens/NotesListScreen.kt` (snackbar undo).
- `ui/NotepadApp.kt` (cableado de `onRestoreNote`).
- `src/test/.../NotepadViewModelTest.kt` (nuevo).

## Riesgos abiertos

- Edición sin autosave: pérdida de texto si el proceso muere sin saved-state
  (P1, ver `NEXT_TASKS.md`).
- Sin búsqueda de notas (P2).

## Bloqueos actuales

- Ninguno. Entorno local: JDK 21 (compila con jvmTarget 17), Android SDK 36 en
  `~/android-sdk`; `./gradlew :app:testPreviewSafeDebugUnitTest` funciona.

## Estado de tests

- Última ejecución: 20/20 verdes (`testPreviewSafeDebugUnitTest`). Detalle en
  `TEST_STATUS.md`.
