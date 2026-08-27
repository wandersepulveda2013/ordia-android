# COMPLETED — Ordía (bloc de notas)

> Solo mejoras importantes completadas por la automatización
> `openhands/autonomous-notes`. Microcambios triviales no se registran.

## 2026-08-27 — Ejecución 006 (título del editor en una línea, P2 #1)

- **Título de una línea de verdad (visual + datos):** `NoteEditorScreen` ahora fija
  `singleLine=true` y, lo más importante, aplanar los saltos `\n` en el
  `onValueChange` del campo de título. El test de regresión probó que `singleLine`
  por sí solo NO impedía que un **pegado** introdujera `\n` en los datos (títulos
  multilínea invisibles, ya que la lista muestra `maxLines=1`). Ahora el dato es
  estrictamente de una línea, coherente con la lista.

## 2026-08-27 — Ejecución 005 (búsqueda de notas)

- **Búsqueda de notas (P2 #1):** nuevo campo de búsqueda que filtra por título y
  contenido. `NoteDao.observeSearch(query)` (`LIKE` case-insensitive),
  `NoteRepository.observeSearch`, el ViewModel expone `searchQuery`/`searchResults`
  (con `flatMapLatest`), y `NotesListScreen` añade el campo (icono lupa en la top
  bar, limpiar, contador de resultados y estado "sin resultados"). `NotepadApp`
  sirve `searchResults` cuando el query no está en blanco.
- **Limpieza de código:** se eliminó `distinctUntilChanged()` obsoleto (redundante
  sobre StateFlow) y se añadió `@OptIn(ExperimentalCoroutinesApi)` por `flatMapLatest`.
- **Tests:** `NotepadViewModelTest` (+3 de búsqueda: filtro por query, restaurar
  todas al limpiar, query por defecto), `NoteDaoTest` (+3 del `LIKE`). Suite:
  **35/35 verdes**. `assembleRelease` 3 variantes OK.

## 2026-08-27 — Ejecución 003 (autosave del editor)

- **Autosave debounced en el editor** (P1): el ViewModel ya no espera a "atrás/
  Hecho" para persistir. Ciclo de draft (`beginDraft`/`autosave`/`commitDraft`)
  guarda el texto tras 800 ms sin escribir, de modo que un proceso muerto o una
  actividad destruida sin saved-state ya no deja de perder el contenido.
- **Sin duplicados ni notas fantasma:** el back-save posterior reutiliza el mismo
  `draftId` (no inserta una nota nueva); se preserva la guardia de BUG-002 (nada
  se crea si no hay contenido) y se elimina la note fantasma si el usuario vació
  todo antes de salir; no se resucita una nota borrada durante la edición
  (save-after-delete).
- **`NotepadViewModelTest`** ampliado a 15 tests (+8: debounce, cancelación por
  tecleo rápido, no duplicado en back, guardia de nota en blanco, ghost
  autodestruida, save-after-delete, draft sobre nota existente).

## 2026-08-26 — Ejecución 001 (baseline + integridad de datos)

- **Baseline del producto rebuild** (bloc de notas minimalista, commit `ceb1ff3`):
  compilación `previewSafeDebug` OK; 15 tests unitarios verdes (8 DAO + 7 repo).
- **Undo de eliminación de notas** (P0, BUG-001): snackbar con "Deshacer" en la
  lista; `NotepadViewModel.restore` reinserta con el mismo id.
- **No persistir notas nuevas vacías** (P1, BUG-002): `save` ignora creaciones
  sin título ni contenido.
- **`NotepadViewModelTest`** nuevo: 5 tests (crear, actualizar conservando
  `createdAt`, ignorar nota nueva vacía, borrar+restaurar con mismo id,
  fijar/desfijar).

## 2026-08-26 — Ejecución 002 (back del sistema + integración de ejecuciones)

- **Back del sistema guarda la nota** (P0, BUG-003): `BackHandler` en
  `NoteEditorScreen` ejecuta el guardado antes de volver a la lista; antes
  cerraba la app y se perdía la edición.
- **Merge de trabajos paralelos** en `openhands/autonomous-notes` (ejecuciones
  001 y 002): conservados undo de borrado, guardia de nota vacía y BackHandler.
- **`NotepadViewModelTest`** ampliado a 7 tests (+ título en blanco con
  contenido, + no recrear nota inexistente por `existingId`).

## 2026-08-27 — Ejecución 004 (test de regresión UI del back-save)

- **Test de regresión UI para el bug P0 del back del sistema** (BUG-003):
  `NoteEditorBackSaveTest` usa Robolectric+Compose para disparar el retroceso del
  sistema y verifica que el editor hace `onCommit` de la edición antes de navegar;
  cubre el hueco de "sin tests de UI" del editor. Suite: 30/30 verdes.
