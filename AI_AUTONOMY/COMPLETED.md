# COMPLETED — Ordía (bloc de notas)

> Solo mejoras importantes completadas por la automatización.

> `openhands/autonomous-notes`. Microcambios triviales no se registran.


## 2026-09-02 — Ejecución 021 (reparar regresiones del merge `8a82c78`, P1)

- **Regresiones del merge reparadas**: el merge combinó dos implementaciones
  divergentes del editor y de la lista, reintroduciendo dos P1: (1) el back
  del sistema del editor perdía el último autosave no commiteado (`exitSaving` del
  linaje legacy llamaba `onSave` con la firma antigua);(2) "Eliminar" del
  menú ⋮ borraba al instante sin diálogo de confirmación (`pendingDelete` quedó
  declarada sólo en el otro linaje). `NoteEditorScreen` usa ahora un único path
  de salida `finishEditing` (`onCommit`+`onBack`) y `NotesListScreen` usa
  `pendingDelete`→`DeleteNoteDialog` (confirmar borra + ofrece Undo; cancelar
  descarta). Strings del diálogo externalizadas a `strings.xml`.

- **Tests**: nueva regresión Compose/Robolectric `NotesListDeleteConfirmTest`
  (2 tests: confirmar-borrado-con-diálogo y cancelar-conserva) + la regresión
  del editor ya cubierta por `NoteEditorBackSaveTest` (3). Suite completa
  **64/64,  0 fallos, 0 errores en las 3 variantes** (`testPreview{Safe,Full,Advanced}DebugUnitTest`,
  re-ejecutada con `--rerun-tasks` en RUN 021.


##2026-08-31 — Ejecución 014 (fecha relativa en la lista:"Hoy"/"Ayer", P3)
- **La lista distingue ahora "Hoy" y "Ayer"** — nuevo `RelativeDate.kt`
  (`fun relativeLabel(timestampMs: Long, now: Date = Date()): String`):
  el límite de "ayer" se calcula contra el **día natural local** (medianoche
  local, no ventana de 24 h), con fallback a `DateFormat.MEDIUM` para
  notas más antiguas. `NotesListScreen` (`NoteRow`) usa `relativeLabel(note.updatedAt)`
  en vez de `DateFormat.MEDIUM`. Cobertura: `RelativeDateTest` (5 tests:
  hoy, ayer, fallback MEDIUM,, y límites exactos de medianoche de hoy/ayer).


## 2026-08-28 — Ejecución 012 (tests de UI del editor: recreación/rotación BUG-003 y "Hecho" hace commit; P2)

- **Hueco de cobertura cerrado:** el UI test Compose de recreación/rotación del
  editor — pendiente desde BUG-003 (reseed) y la P2 de extender tests de UI —
  quedó añadido con `NoteEditorRecreationTest` (2 tests Compose/Robolectric).
  Usa `StateRestorationTester.emulateSavedInstanceStateRestore()` para simular
  rotación/proceso-muerte tras teclear sin esperar el debounce (800 ms, del autosave); y
  verifica que (a)el texto en curso se preserva y no se sobrescribe con la instantánea
  vieja de la BD;y (b)el commit posterior (back del sistema,persiste lo tecleado,
  no el contenido viejo — cubriendo nota persistida en edición y nota nueva en curso.
- **"Hecho" de la toolbar cubierto:** `NoteEditorBackSaveTest.toolbarDone_commitsAndNavigates`
  verifica que la acción "Hecho" hace commit y navega a la lista igual que el back del
  sistema (hasta ahora solo se cubría el back del sistema).
- **Tests:** suite completa → **49/49 verdes en `previewSafe` y `previewAdvanced`**;
  `testFullDebugUnitTest` BUILD SUCCESSFUL (sin tests en su source set).

##2026-08-28 — Ejecución 011 (accesibilidad de la lista: rótulos para TalkBack, P3)

- **La fila de nota anuncia ahora su acción con el título:** el `clickable` de
  `NoteRow` pasa `onClickLabel` ("Abrir nota: <título>" o "Abrir nota
  sin título" si no hay título), de modo que TalkBack anuncia una acción
  descriptiva en vez del nodo genérico. El pin incluye el título
  ("Fijada: <título>" / "Fijada, sin título") para distinguir qué nota está fijada..
- **Tests:** nuevo `NotesListAccessibilityTest` (2 tests Compose/Robolectric):
  la fila expone el rótulo de la acción con el título y el pin describe la nota
  fijada (y la nota no fijada no expone pin); fallback para nota sin título. Suite
  completa → **46/46 en `previewFull`** (sin regresiones).

##2026-08-28 — Ejecución 010 (el pin de la lista conmuta de forma atómica, P2)

- **Carrera read-modify-write del pin eliminada:** el pin se conmutaba con
  `setPinned(id, !note.pinned)` — valor calculado en la UI sobre un snapshot del
  flujo,, con riesgo de que dos toggles en ráfaga (o un refresh intercalado)
  sobrescribieran el estado con un valor obsoleto. `NoteDao.togglePinned(id: Long)`
  ejecuta ahora `UPDATE notes SET pinned = NOT pinned WHERE id = :id` — flip
  atómico calculado por SQLite respecto del estado actual en disco, con transacción
  y verificación de filas en el repo. El VM expone `togglePinned(id)` y la lista
  recibe un callback `(Long) -> Unit`, sin dependencia de snapshots del flujo;
  `setPinned` eliminado de DAO/repo/VM.
- **Tests:** tests de DAO/repo/VM ajustados a doble toggle (el net de dos toggles == estado original>;
  suite completa → **44/44 en las 3 variantes**; `assemblePreviewSafeRelease` OK.



## 2026-08-27 — Ejecución 009 (integridad del ciclo de draft del editor, P1)

- **Duplicados / cross-contaminación al cambiar de nota en ráfaga (BUG-006):**
  se eliminó la carrera entre `commitDraft` (persistencia lanzada) y un
  `beginDraft` posterior. `commitDraft` ahora captura un snapshot y limpia la
  sesión SÍNCRONAMENTE, pasando ese snapshot al coroutine
  (`doPersistCommit`); `beginDraft` ya no puede ver un draft a medio limpiar.
  Cierre de la nota anterior y apertura de otra existente o de una nota nueva
  funcionan en cualquier orden, sin notas duplicadas ni escrituras cruzadas.
- **El draft sobrevive a rotación/proceso-muerte:** la sesión de draft
  (`draftId` + `draftWasNew`) vive ahora en un `SavedStateHandle`, de modo que un
  ViewModel recreado restaura el id y el siguiente autosave actualiza la nota
  original (no inserta una copia). `NotepadViewModelFactory` expone
  `createSavedStateHandle()` y `MainActivity` lo usa vía `viewModel(factory=...)`.
- **Tests:** +4 regresiones en `NotepadViewModelTest` (recreación, proceso-muerte,
  dos carreras commit→beginDraft). Pre-fix las 2 de carrera fallaban con filas
  duplicadas. Suite: **44/44 verdes en las 3 variantes**; `assembleRelease` 3
  variantes OK.

## 2026-08-27 — Ejecución 008 (búsqueda utilizable + layout sin solape, P1)

- **BUG-005 (a): la búsqueda era inalcanzable desde la UI.** El icono de la
  toolbar llamaba `onSearchQueryChange("")` y `isSearching` se derivaba de
  `searchQuery.isNotBlank()`, así que nunca podía entrar en modo búsqueda con
  query vacía. Ahora `isSearching` es un `rememberSaveable` explícito conmutado
  por el icono y apagado cuando se limpia la query. Detallado en
  `BUGS_FOUND.md`.
- **BUG-005 (b): el campo de búsqueda tapaba la primera nota.** `SearchHeader` y
  `NoteList` eran hermanos en el `Box` del Scaffold, cada uno con
  `fillMaxSize().padding()`; al activar la búsqueda el campo se dibujaba encima
  de la primera fila. El contenido se envuelve ahora en una `Column` con el
  padding de insets aplicado una vez (estados vacíos con `Modifier.weight(1f)`).
- **Tests:** 40/40 verdes (`NotesListSearchInteractiveTest` +3: abrir/filtrar,
  toggle off, limpiar query; aserción de bounds anti-solape). `assembleRelease`
  de las 3 variantes → OK.

## 2026-08-27 — Ejecución 007 (undo seguro ante reutilización de ids, P1)

- **"Deshacer" ya no puede sobrescribir una nota viva (BUG-004):**
  `NotepadViewModel.restore` verificaba directamente `repo.save(note)` (REPLACE),
  con riesgo de pisar una nota que hubiera reutilizado el id del borrado (SQLite
  reutiliza rowids). Ahora `restore` comprueba `repo.get(note.id)`: si el id sigue
  libre lo reutiliza (respeta `createdAt`/id); si fue reutilizado por otra nota,
  reinserta bajo un id nuevo conservando título/contenido. Nunca sobrescribe una
  nota viva (lost update / save-after-reuse).
- **Tests:** +1 en `NotepadViewModelTest`
  (`restore_whenOriginalIdReusedByAnotherNote_reinsertsUnderFreshId`; el caso de
  id libre ya lo cubre `deleteThenRestore_keepsSameIdAndContent`). Suite: **37/37
  verdes**.

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

## 2026-08-31 — Ejecución 015 (integridad de writes + preview de lista)





- **Sin writes innecesarios al guardar sin cambios** (P2): `NotepadViewModel.saveCurrent`
  ahora compara `title`/`content` con la fila persistida y devuelve `true` sin tocar
  `updatedAt` ni escribir si no hay cambios reales. Protege contra `updatedAt` bumps
  gratuitos y writes de disco evitable en el camino autosave/commit (integridad,
  menos wear/stale-write).
- **Preview de lista de 2 líneas** (P2, UX): la lista ahora usa
  `NoteEntity.preview` (primeras dos líneas no vacías, trim, cap 160) en vez de
  `content.take(120)` crudo que cortaba en mitad de línea y mostraba espacios.
