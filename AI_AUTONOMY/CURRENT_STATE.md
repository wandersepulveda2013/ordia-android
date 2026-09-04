# CURRENT_STATE — Ordía (bloc de notas)

> Estado actual del producto para la automatización `openhands/autonomous-notes`.
> El estado del producto anterior a la reconstrucción está en
> `CURRENT_STATE_PRE_REBUILD.md` (histórico).

## Estado general

- **Producto:** bloc de notas minimalista (rebuild completo en `main`, commit
  `ceb1ff3`): lista de notas con pin, editor título+contenido, Room `ordia.db`.
- **Branch de trabajo:** `openhands/autonomous-notes` (creada desde `main`
  `ceb1ff3` el 2026-08-26). RUN 021 (regresiones del merge
  `8a82c78` reparadas): (1) `NoteEditorScreen` usa un único path de salida
  `finishEditing` (`onCommit` + `onBack`) para back del sistema — el back ya no
  pierde el último autosave no commiteado (`exitSaving` muerta del linaje legacy
  eliminada, sin `BackHandler` duplicado);(2) `NotesListScreen` restaura el
  diálogo de confirmación de borrado (`pendingDelete` declarado; menú "Eliminar"
  abre `DeleteNoteDialog`; confirmar borra + ofrece Undo, cancelar descarta).
  Nueva regresión Compose/Robolectric `NotesListDeleteConfirmTest` (2 tests;.
  RUN 022 (P1, BUG-009): la query de búsqueda activa
  ya no se pierde en proceso-muerte: `searchQuery` vive ahora en
  `SavedStateHandle` (inicializada desde el estado guardado al crear el
  ViewModel,y `setSearchQuery` escribe/elimina la clave), por lo que la
  recreación restaura el modo búsqueda, la query y el filtro. Regresión:
  `NotepadViewModelTest.processDeath_restoresSearchQuery`.
  Suite completa **65/65,  0 fallos,,  0 errores en las 3 variantes** (RUN 022.
  RUN 023 (P2/UX): el diálogo de confirmación de borrado ya no contradice el
  deshacer — el copy decía "no se puede deshacer" mientras que el snackbar de
  undo aparece al confirmar; ahora anuncia «Podrás deshacerlo» (strings.xml + regresión
  en `NotesListDeleteConfirmTest`).
  texto del editor (título/contenido) muestran ahora un **indicador de foco
  visible** (`focusedIndicatorColor = MaterialTheme.colorScheme.outline` en vez de
  `Transparent`) — navegación por teclado/TalkBack ya no deja el campo focalizado

  indistinguible del no focalizado; el unfocused sigue transparente (sin línea
  fantasma). Tags estables `EDITOR_TITLE_TAG`/`EDITOR_CONTENT_TAG` + regresión
  Compose `NoteEditorFocusTest` (62/62 en las  3 variantes).
  RUN 029: **la lista de notas también tiene foco visible/navegable**: cada fila
  es focuseable (`Modifier.focusable()` + `.onFocusChanged` con fondo highlight
  `secondaryContainer` @45% RoundedCornerShape(10.dp)) y etiqueta estable
  `note_row_<id>` — regresión `NotesListFocusTest` (72/72 en previewSafe).
  RUN 030: **anuncio TalkBack del estado fijado en la lista restaurado/cubierto:**
  el `stateDescription` de la fila fijada es `"Fijada"` (string cargada fuera del
  lambda no-`@Composable`; imports `semantics`/`stateDescription`), con regresión
  `pinnedNote_announcesPinnedStateDescription` (**73/73 en las  ‌3 variantes**).
  RUN 031: **regresión UI del ciclo de draft con autosave + recreación cerrada:**
  `NoteEditorRecreationTest` +1 test `recreation_afterAutosaveCreatedRow_resumesSameDraft_doesNotDuplicate`
  (nota nueva con fila creada por autosave, recreación `StateRestorationTester`,
  más escritura y "Hecho" → exactamente 1 commit, contenido acumulado, sin
  duplicado ni pérdida de texto — **74/74 en las 3 variantes**).
  RUN 032: **integridad de texto:`NoteEntity.preview` y el diálogo de borrado
  ya no parten pares sustitutos UTF-16** (helper `safeTakeChars`;el emoji que no
  cabe en el cap se descarta entero,sín `\ufffd`;+3 tests `NoteEntityPreviewTest`
  → **77/77 en las 3 variantes**).
  RUN 018: búsqueda por `LIKE` con
  comodines escapados (`NoteRepository.escapeLike` + `ESCAPE '\'`) — el texto
  tecleado se busca como literal, no como patrón SQL (regresión BUG-007 cubierta).
  RUN 019: verificación post-commit 60/60 sobre `3da47ef` + auditoría RTL
  de iconos cerrada (NEXT_TASKS #6); P0/P1 vacios; BUG-005 cerrado.
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
  debounce 800 ms. RUN 009: la sesión de draft (id + flag de nota nueva) vive en
  un `SavedStateHandle` (sobrevive a rotación/proceso-muerte) y `commitDraft`
  hace snapshot + limpieza síncrona (sin carrera con un `beginDraft` posterior).
  `MainActivity` construye el ViewModel con `NotepadViewModelFactory` +
  `createSavedStateHandle()`.
- Lista: `ui/screens/NotesListScreen.kt` — borrado con snackbar de deshacer y
  búsqueda (filtro por título/contenido; RUN 008: modo búsqueda conmutable desde
  la lupa + layout en `Column` sin solapes).

## Áreas recientemente modificadas (ejecuciones 001-008)

- `ui/NotepadViewModel.kt` (+`restore`, guardia de nota vacía en `save`; y en RUN 003
  el ciclo de draft `beginDraft`/`autosave`/`commitDraft` + `persist` compartida; en
  RUN 005 `searchQuery`/`searchResults` con `flatMapLatest`; en RUN 009 sesión de
  draft en `SavedStateHandle` + `doPersist`/`doPersistCommit`).
- `ui/screens/NotesListScreen.kt` (snackbar undo; en RUN 005 campo de búsqueda con
  contador de resultados y estado "sin resultados").
- `ui/NotepadApp.kt` (cableado de `onRestoreNote`; en RUN 003 llama a `beginDraft`
  y pasa `onAutosave`/`onCommit`; en RUN 005 sirve `searchResults` si el query no
  está en blanco).
- `ui/screens/NoteEditorScreen.kt` (`BackHandler` BUG-003; en RUN 003 cambia a
  `onAutosave`/`onCommit`, el `BackHandler` ejecuta `onCommit`; en RUN 006 el título
  pasa a `singleLine=true` y se aplanan `\n` en `onValueChange` → dato de una línea).
- `src/test/.../NotepadViewModelTest.kt` (nuevo, 18 tests tras RUN 007).
- `src/test/.../NoteEditorBackSaveTest.kt` (RUN 004 back-save; RUN 006 +test
  single-line del título).
- `src/test/.../NotesListSearchInteractiveTest.kt` (RUN 008, nuevo,,3 tests de
  regresión BUG-005 + aserción de bounds anti-solape.
- `src/test/.../NotesListAccessibilityTest.kt` (RUN 011, nuevo,,2 tests Compose/
  Robolectric de accesibilidad de la lista: rótulo de acción de la fila + pin
  describe el título + fallback sin título..
- `ui/screens/NotesListScreen.kt` (RUN 011: `NoteRow` pasa `onClickLabel`
  "Abrir nota: <título>" y el icono de pin "Fijada: <título>").
- `data/NoteDao.kt` / `NoteRepository.kt` / `ui/NotepadViewModel.kt` /
  `ui/screens/NotesListScreen.kt` (RUN 010: el pin pasa a `togglePinned(id)`
  atómico SQL; `setPinned` eliminado de DAO/repo/VM/tests).
## Fix esta ejecución
- RUN 028 (P2, UX recuperación ante fallos): el snackbar de error de
  persistencia se muestra ahora en AMBAS pantallas(lista y editor. Se subió
  el host/colector de `viewModel.persistenceError` de `NotesListScreen` a la
  raíz `NotepadApp` (`Box(Modifier.fillMaxSize())` + `SnackbarHost` inferior +
  `LaunchedEffect` recogiendo `persistenceError` → `R.string.error_persistence`);
  se eliminaron de `NotesListScreen` el param `persistenceError`, el colector
  local y los imports `Flow`/`emptyFlow` (el `SnackbarHostState` local queda
  para delete/undo). Verificado: suite completa **71/71** en las 3 variantes.
- RUN 017 (P3, tests): los  4 archivos de tests de UI intercambian el import
  deprecado `androidx.compose.ui.test.junit4.createAndroidComposeRule` por el
  actual `androidx.compose.ui.test.junit4.v2.createAndroidComposeRule`
  (`StandardTestDispatcher`, API canónica). Sin cambios de comportamiento; se
  eliminan los warnings de deprecación de la v1 legada. Verificado: suite completa
   **59/59** en las  3 variantes (`testPreviewSafe/Full/AdvancedDebugUnitTest`).

## Fix ejecución anterior
- RUN 014 (P3): fecha relativa en la lista— nuevo
  `ui/util/RelativeDate.kt`: `relativeLabel(timestampMs, now = Date())` etiqueta

  "Hoy" / "Ayer" contra el día natural local((no ventana de 24 h))) con fallback

  a `DateFormat.MEDIUM`. `NoteRow` usa `relativeLabel(note.updatedAt)` en vez de
  `DateFormat.MEDIUM`. Nuevo `RelativeDateTest` (5 tests: hoy, ayer, fallback,
  y límites exactos de medianoche hoy/ayer. Suite completa **54/54 en las  3 variantes**.

## Fix esta ejecución
- RUN 015 (P2, integridad+UX): `NotepadViewModel.saveCurrent` ahora
  salta la escritura si `title` y `content` no cambiaron((no `updatedAt` bump
  gratuito, no write de disco evitable; test de regresión
  `save_existingNoteWithUnchangedContent_doesNotRewriteUpdatedAt`)). La lista
  usa `NoteEntity.preview`(2 primeras líneas no vacías,, trim,, cap 160) en vez de
  `content.take(120)` crudo((cortaba a mitad de línea)). Nuevo `NoteEntityPreviewTest`
   (4 tests:: blank,, trim/2 líneas,, cap length,, single long line capped).
  Validación estática((sin JDK en sandbox;; `git diff --check` limpio)).

## Fix esta ejecución
- RUN 016 (P3, i18n): todos los strings visibles de las pantallas core
  (`NoteEditorScreen`, `NotesListScreen`) — títulos, placeholders, contentDescriptions,
  snackbar, menús, estados vacíos — movidos a `res/values/strings.xml`
  (23 strings nuevos) y referenciados via `stringResource(...)`. Ningún
  hardcode queda en las  2 pantallas (`grep` limpio). Mejora mantenibilidad
  (single source of truth) y prepara localización futura.
  RUN 015 heredado verificado: suite completa **59/59** en las  3 variantes
  (incluye `NoteEntityPreviewTest` 4 tests + skip-write regression).

## Fix ejecución anterior
- RUN 011 (P3, accesibilidad): `NoteRow` anuncia ahora su acción con
  `onClickLabel` "Abrir nota: <título>" (fallback "Abrir nota sin
  título"); el pin describe "Fijada: <título>" ("Fijada, sin título" para
  nota sin título). Nuevo `NotesListAccessibilityTest` (2 tests Compose/
  Robolectric: rótulo de acción + pin describe título + fallback sin título).

## Fix ejecución anterior
- RUN 012 (P2, tests): hueco de BUG-003 cerrado—nuevo
  `NoteEditorRecreationTest` (2 tests Compose/Robolectric con
   `StateRestorationTester`: recreación preserva el texto en curso antes del
  autosave y el commit posterior persiste lo tecleado, no la instantánea de la
  BD) + `NoteEditorBackSaveTest.toolbarDone_commitsAndNavigates`
  (la acción "Hecho" de la toolbar hace commit y navega como el back del sistema).
  3 tests de UI nuevos; suite → 49/49 en previewSafe y previewAdvanced.
## Fix ejecución anterior
- RUN 010: `togglePinned` pasa a ser un flip atómico SQL
  (`UPDATE notes SET pinned = NOT pinned WHERE id = :id`), eliminando la
  carrera read-modify-write del pin (antes `setPinned(id, !note.pinned)` calculaba
  en la UI sobre un snapshot que podía estar obsoleto si dos toggles ocurrían
  en ráfaga). `NotepadViewModel.togglePinned(id)` y `onTogglePin: (Long) -> Unit`.
  Tests de DAO/repo/VM ajustados a doble toggle (el net de dos toggles
  es el estado original).

## Fix ejecución anterior
- RUN 009: integridad del ciclo de draft (BUG-006, P1) — la sesión de draft
  (`draftId` + `draftWasNew`) se respalda en `SavedStateHandle` (sobrevive a
  rotación/proceso-muerte); `commitDraft` hace snapshot + limpieza síncrona y el
  coroutine aplica el contenido final sin tocar la sesión; `beginDraft` solo
  resume cuando es una nota nueva en vuelo. Elimina duplicados/cross-contaminación
  al cambiar de nota en ráfaga. +4 tests de regresión.

## Fix ejecución anterior
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

- **Última ejecución(RUN 032):** 77/77 verdes en las3 variantes —
  `testPreviewSafeDebugUnitTest`, `testPreviewFullDebugUnitTest`,
  `testPreviewAdvancedDebugUnitTest` → **77 tests, 0 fallos (11 DAO + 7 Repo
  + 28 VM [incluye `processDeath_restoresSearchQuery`, BUG-009,
   `commitDraft_existingNoteUnchanged_doesNotRewriteUpdatedAt`, y las  3 regresiones

   de persistencia resiliente `failedSave/Delete/Restore`] + 16 UI
  [incluye `NotesListDeleteConfirmTest` + `NotesListSearchInteractiveTest` 4/4
  (label accesible «Buscar notas» persistente) + `NotesListPinToggleTest` 2/2

  (pin vía menú ⋮)] +  ‌7 `NoteEntityPreviewTest` (RUN 032:+3 anti-par
  sustituto UTF‑16) + 5 `RelativeDateTest`.** Detalle en `TEST_STATUS.md`.
## Riesgo abierto (P1, BUG-010, RUN 034)

- **`commitDraft` puede perder el último texto tecleado si el storage falla en el
  commit final.** El autosave es self-healing (texto queda en el editor y reintenta);
  pero `commitDraft` cancela el autosave,**limpia la sesión de draft síncronamente**
  y lanza el write en background:si ese write falla, el editor ya navegó atrás

  y el texto no queda en ninguna nota ni en el editor. El snackbar global de
  persistencia avisa, pero no recupera el texto. Documentado en `BUGS_FOUND.md`
  BUG-010;fix mínimo propuesto (reintentar en `launchPersist` y/o retener la
  sesión hasta confirmar la escritura) en `NEXT_TASKS.md` P1 ítem 0.

## Resiliencia de persistencia (RUN 027)

- Todos los paths de escritura (`save`/`autosave`/`commitDraft`/`delete`/`restore`/
  `togglePinned`) pasan por `launchPersist {}`: un fallo de almacenamiento(disco lleno,
  error de BD) ya no puede crashear la app, emite un evento one-shot
  `persistenceError` (MutableSharedFlow, reintenta una vez dentro de
  `withContext(NonCancellable)` y si el reintento falla sigue emitiendo el evento
  recuperable — el texto queda en el editor y el siguiente autosave puede
  autocorregirse. La app raíz (`NotepadApp`) muestra snackbar «No se pudo completar la operación» en cualquiera de las dos pantallas(lista y editor; RUN 028)
  (`error_persistence`). Sin dependencias nuevas.

## Accesibilidad (RUN 024)

- El campo de búsqueda expone ahora un rótulo accesible estable «Buscar notas»
  (vía `Modifier.semantics { contentDescription = stringResource(R.string.search_notes) }`)
  que TalkBack anuncia tanto con el campo vacío como mientras se teclea; el test
  `NotesListSearchInteractiveTest` lo verifica (incluida la persistencia tras escribir).
