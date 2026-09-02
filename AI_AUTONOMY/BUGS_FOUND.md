# BUGS_FOUND — Ordía (bloc de notas)

> Formato: bug · impacto · reproducción · causa · estado · commit.

## BUG-008 — El merge `8a82c78` reintrodujo dos regresiones: back del editor sin commit final y borrado sin diálogo de confirmación (P1

- **Impacto:** el merge de `437e7b5` (linaje RUN 020: focus indicator, autosave por
  cambio, `onAutosave`/`onCommit`) sobre `f20856a` reintrodujo el `back` del
  editor llamado indirectamente a `onSave(...)` tras haber desplazado los params de
  `onSave` (referencia `fun exitSaving` muerta en esa rama) — el back del
  sistema perdía el último autosave no commiteado. Además `NotesListScreen`
  quedó con la rama fusionada llamando `onDeleteNote`+snackbar undo directamente
  desde el menú, y el `pendingDelete`/`AlertDialog` de confirmación (comprometido
  antes del merge en `e2b7971`, del linaje no-main) quedó huérfano: la variable no
  se declaraba, pero `pendingDelete?.let { DeleteNoteDialog(...)}` seguía en el árbol —
  borraría sin confirmar evitable antes del merge.
- **Reproducción:** (editor) escribir texto → pulsar back del sistema → volver a la
  lista → reabrir → la última escritura falta (o queda duplicada vía commit de la
  rama antigua). (borrado) fila → menú ⋮ → "Eliminar" → la nota se elimina al
  instante con snackbar, sin diálogo de confirmación.
- **Causa:** conflicto de merge no resuelto: una rama llevaba el nuevo editor
  (`onAutosave`/`onCommit`, single exit path `finishEditing`) y la otra el editor
  legacy con `onSave`+`exitSaving`; la rama combinará dejó ambas, y la lista
  combinó el flujo directo con el código residual del diálogo. `git status` no
  señaló conflicto porque los hunks vivían en contextos textuales distintos.

- **Estado:** FIXED — (1) `NoteEditorScreen` usa un único `finishEditing`
  (`onCommit(title, content)` + `onBack()`) para back del sistema, flecha de la
  toolbar y "Hecho"; se eliminó `exitSaving` muerto y el `BackHandler` duplicado.

  (2) `NotesListScreen` declara `pendingDelete` y el menú "Eliminar" abre
  el `DeleteNoteDialog`; al confirmar borra y ofrece Undo; cancelar descarta.

- **Commit:** `ef02a80` (editor) y `bdd1986` (lista/diálogo) en
  `openhands/autonomous-notes`, tras el merge `8a82c78`.
- **Test:** regresión Compose/Robolectric `NotesListDeleteConfirmTest` (2 tests:
  confirma-borrado-con-diálogo y cancelar-conserva-nota) + regresiones ya existentes
  `NoteEditorBackSaveTest` (3) y `NoteEditorRecreationTest` (2). Suite: **64/64**
  en las  3 variantes (0 fallos, 0 errores; RUN 021.
- **Verificación (RUN 025):** CERRADO — las dos rutas siguen cubiertas y verdes en
  la suite actual: `NoteEditorBackSaveTest` 3/3, `NoteEditorRecreationTest` 2/2
  y `NotesListDeleteConfirmTest` 2/2; suite completa **67/67,  ​0 fallos,  ​0 errores**
  en las  3 variantes (`--rerun-tasks`; RUN 025.

## BUG-009 — La query de búsqueda activa se perdía en proceso-muerte (P1

- **Impacto:** al morir el proceso con el modo búsqueda activo y una query
  tecleada (p. ej. por recarga del sistema o navegación entre apps),la
  lista se recreaba mostrando el campo vacío sobre la lista completa: el modo
  de búsqueda visual y el filtro aplicado se desincronizaban hasta que el usuario
  volvía a teclear (búsqueda activa pero sin filtro, o filtro perdido). Zona
  crítica de la misión (reinstanciación, rotación/proceso-muerte.
- **Reproducción:** lista → búsqueda → teclear `paella` → matar el proceso →
  recrear → la lista muestra todas las notas con el campo de búsqueda en blanco
  (el draft de edición sí sobrevive — BUG-006 — pero la query no).
- **Causa:** `NotepadViewModel` expone `searchQuery` solo en memoria
  (`MutableStateFlow("")`); a diferencia de la sesión de draft (que vive en
  `SavedStateHandle` desde RUN 009,, la query no se guardaba en el estado
  guardado del ViewModel, así que la recreación partía de una query vacía.

- **Estado:** FIXED — `searchQuery` se respalda en `SavedStateHandle`: el
  ViewModel lo inicializa desde `savedState[KEY_SEARCH_QUERY]` y
  `setSearchQuery` escribe (o elimina, si vacía, la clave. `NotesListScreen`
  ya derivaba `isSearching` de `searchQuery.isNotEmpty()`;a la recreación el
  modo búsqueda se restaura junto con la query y el filtro aplicado.

- **Commit:** RUN 022 en `openhands/autonomous-notes` (pendiente de pushear
  al cierre).
- **Test:** regresión `NotepadViewModelTest.processDeath_restoresSearchQuery`:
  un ViewModel recreado con `SavedStateHandle(mapOf(KEY_SEARCH_QUERY to "paella"))`
  restaura `searchQuery` y los `searchResults` filtrados correctamente.

## BUG-007 — Comodines de `LIKE` sin escapar distorsionaban la búsqueda (P2

- **Impacto:** al buscar, los caracteres `%` y `_` del texto tecleado actuaban como
  comodines SQL (`%` = cualquier secuencia; `_` = un carácter cualquiera): buscar
  `"_"` o `"%"` devolvía **todas** las notas, y `"a_b"` encontraba también
  `"abc"`/`"axb"`. Resultados de búsqueda incorrectos (falsos positivos) para
  cualquier consulta que contuviera esos símbolos (código, precios, rutas, etc.),
  sin opción de buscar el literal.
- **Reproducción:** listao → búsqueda → teclear `_` (o `%`) → aparecen todas las
  notas aunque ninguna contenga ese carácter.
- **Causa:** `NoteDao.observeSearch` interpolaba la query del usuario directamente
  en `LIKE '%' || :query || '%'` sin `ESCAPE`: SQLite trata `%`/`_` como
  comodines dentro del patrón, inyectando ruido en los resultados.
- **Estado:** FIXED — `NoteRepository.observeSearch` escapa `\` → `\\`, `%` → `\%`,
  `_` → `\_` antes de pasar al DAO, y el SQL de ambos `LIKE` añade `ESCAPE '\'`.
  La query mostrada en la UI permanece intacta (el escape es interno).
- **Commit:** RUN 018 en `openhands/autonomous-notes`.
- **Test:** `NoteDaoTest.observeSearch_wildcardsAreTreatedLiterally` (DAORoom
  real + `NoteRepository`: buscar `100%`, `_guion_bajo_` y `back\slash` devuelve solo
  las notas que contienen el literal; y buscar solo `%`, `_` o `\` encuentra
  solo la nota que realmente contiene ese carácter, no todas).

## BUG-006 — Duplicados/cross-contaminación por limpieza asíncrona del draft y draft en memoria (P1)

- **Impacto:** (a) salir de una nota y abrir otra en ráfaga podía (i) crear una
  nota duplicada — el autosave de la nota nueva se hacía bajo un `draftId` ya
  vacío → insert de nueva fila — o (ii) escribir el contenido de una nota dentro
  de la fila de la anterior; (b) rotación / proceso-muerte perdía el id del draft
  en memoria y el siguiente autosave insertaba una segunda nota distinta del
  mismo borrador. Zona crítica de la misión (cambio rápido entre notas,
  reinstanciación, cierre inesperado).
- **Reproducción:** (a) abrir nota A → escribir → back → abrir nota B al instante
  → escribir → puede haber 3 filas (A, A, B) o B escrita sobre A. Test de
  regresión: `beginDraft_afterCommitLaunched_switchesDraftToNewNote`, antes del
  fix `expected:<2> but was:<3>`. (b) nota nueva → escribir (autosave crea la
  fila) → rotar/recrear → escribir → duplicado; test:
  `beginDraftAgain_resumesLiveDraft_doesNotDuplicateNote` y
  `processDeath_restoresDraftId_avoidsDuplicateNote`.
- **Causa:** (a) `commitDraft` limpiaba `draftId`/`draftWasNew` dentro del
  coroutine lanzado, tras persistir: un `beginDraft` posterior podía
  intercalarse y ver el draft aún vivo (para id explícito rebind) o ya muerto
  (para nueva nota → insert duplicado). El fallo era estructural
  (uso asíncrono del estado mutable de sesión), no de temporización; de ahí que
  los tests deterministas con `StandardTestDispatcher` lo reprodujeran siempre.
  (b) el draft solo vivía en memoria del ViewModel (no se restauraba).
- **Estado:** FIXED — sesión de draft respaldada por `SavedStateHandle`;
  `commitDraft` hace snapshot + limpieza síncrona y pasa el snapshot al
  coroutine (`doPersistCommit`, que nunca toca la sesión); `beginDraft` solo hace
  resume-guard para `existingId == null` con draft en vuelo; separados
  `doPersist` (autosave, liga el draft) y `doPersistCommit` (contenido final).
- **Commit:** RUN 009 en `openhands/autonomous-notes`.
- **Test:** 4 regresiones en `NotepadViewModelTest`:
  `beginDraftAgain_resumesLiveDraft_doesNotDuplicateNote`,
  `processDeath_restoresDraftId_avoidsDuplicateNote`,
  `beginDraft_afterCommitLaunched_switchesDraftToNewNote`,
  `beginDraft_nullAfterCommitLaunched_startsFreshNewNote`.
  Antes del fix: las 2 de la carrera fallan (duplicado); tras el fix: todas verdes.
- **Re-verificado en RUN 016:** las 4 regresiones del ciclo de draft
  siguen verdes en la suite completa (59/59 en las  3 variantes; ver TEST_STATUS.md.

## BUG-001 — Eliminación de nota sin confirmación ni deshacer (P0)

- **Impacto:** un toque accidental en "Eliminar" destruye la nota de forma
  permanente e irreversible.
- **Reproducción:** lista → menú ⋮ de una nota → Eliminar. Desaparece al instante.
- **Causa:** `NotesListScreen` llamaba a `onDeleteNote` directamente; sin
  confirmación, sin undo, sin papelera.
- **Estado:** FIXED — snackbar "Nota eliminada" con acción "Deshacer" que
  reinserta la nota con el mismo `id` (`NoteRepository.save` + REPLACE conserva
  el id al ser `autoGenerate` solo para id=0).
- **Commit:** ejecución 001 en `openhands/autonomous-notes`.
- **Test:** `NotepadViewModelTest.deleteThenRestore_keepsSameIdAndContent`.

## BUG-002 — Notas vacías se persisten al salir del editor (P1)

- **Impacto:** abrir el editor nuevo y volver atrás sin escribir crea una nota en
  blanco que ensucia la lista y la base de datos.
- **Reproducción:** "+" → atrás sin teclear nada → aparece una nota vacía.
- **Causa:** `NotepadViewModel.save` insertaba siempre cuando `existingId == null`,
  sin comprobar que hubiera contenido.
- **Estado:** FIXED — `save` ignora la creación si título y contenido están en
  blanco (las notas existentes sí pueden quedar vacías: el borrado explícito es
  decisión del usuario, no se autodestruyen).
- **Commit:** ejecución 001 en `openhands/autonomous-notes`.
- **Test:** `NotepadViewModelTest.save_blankNewNote_isNotPersisted`.

## BUG-003 — Back del sistema en el editor cerraba la app y perdía la nota (P0)

- **Impacto:** estando en el editor, pulsar el back del sistema (gesto o botón)
  cerraba la app por completo en vez de volver a la lista; la nota editada no se
  guardaba y se perdía el trabajo del usuario.
- **Reproducción:** abrir una nota → escribir → back del sistema → la app se
  cierra; al reabrir, los cambios no existen.
- **Causa:** sin navegación tipada ni `BackHandler`, el único destino de la pila
  era la actividad raíz; solo el botón "atrás" de la toolbar persistía.
- **Estado:** FIXED — `NoteEditorScreen` registra un `BackHandler` que ejecuta el
  mismo guardado que la flecha de la toolbar antes de volver a la lista.
- **Commit:** ejecución 002 en `openhands/autonomous-notes`.
- **Test:** `NoteEditorBackSaveTest.systemBack_savesUncommittedEditsBeforeNavigating`
  (UI Compose/Robolectric, RUN 004) cubre el back del sistema en el editor.


## BUG-003 — El reseed del editor borraba lo escrito al recrearse la pantalla
- **Bug:** `NoteEditorScreen` tenía un `LaunchedEffect(note?.id)` que reasignaba
  `title`/`content` partiendo de la instantánea obsoleta `note` de la BD. Tras una
  recreación (rotación / proceso con estado guardado), `rememberSaveable` restaura el
  texto en curso, pero el effect lo sobrescribía con el contenido viejo de la BD —
  perdiendo los últimos caracteres aún no persistidos dentro de la ventana de debounce
  del autosave (800 ms).
- **Impacto:** alto — pérdida silenciosa de texto al girar/recrear justo tras escribir.
- **Reproducción:** abrir una nota, escribir, rotar la pantalla antes de que el autosave
  debounceado persista.
- **Causa:** reseed redundante y perjudicial desde una instantánea de BD potencialmente
  obsoleta. El seed inicial de `rememberSaveable` (note?.title/content) ya es correcto.
- **Estado:** FIXED — verificado por `NoteEditorRecreationTest` (RUN 012):
  `StateRestorationTester.emulateSavedInstanceStateRestore` + back del sistema
  preservan el texto en curso (sin persistir) y el commit posterior persiste lo
  tecleado, no la instantánea vieja de la BD.
- **Resuelto por:** eliminar el `LaunchedEffect` de reseed en `NoteEditorScreen`.
  Cobertura de regresión añadida en RUN 012 (`NoteEditorRecreationTest`, 2 tests
  Compose/Robolectric: nota persistida en edición y nota nueva en curso).
## BUG-005 — El icono de búsqueda era inalcanzable y el campo tapaba la lista (P1)

- **Impacto:** (a) la búsqueda de RUN 005 no se podía abrir desde la UI: tocar la
  lupa no hacía nada, `isSearching` se derivaba de `searchQuery.isNotBlank()` y el
  icono solo llamaba `onSearchQueryChange("")`, así que el estado nunca pasaba a
  verdadero; (b) además, al activarse la búsqueda, el campo se dibujaba ENCIMA de
  la primera fila de la lista (ambos hacían `fillMaxSize().padding()` por separado
  dentro del `Box` del Scaffold), ocultando/truncando la primera nota.
- **Reproducción:** (a) abrir la lista → tocar la lupa → no aparece el campo;
  (b) con el campo visible, la primera nota quedaba tapada por el SearchHeader.
- **Causa:** (a) el icono llamaba `onSearchQueryChange("")` en vez de conmutar un
  estado de "modo búsqueda"; el modo se infería del texto de la query, por lo que
  era imposible entrar con query vacía. Introducido en RUN 005 con la búsqueda.
  (b) estructura de layout: `SearchHeader` y `NoteList` eran hermanos en el `Box`
  del Scaffold, cada uno reaplicando `padding`.
- **Estado:** FIXED — `isSearching` pasa a ser un `rememberSaveable` explícito
  conmutado por el icono (y reseteado a false cuando la query queda en blanco al
  limpiar, `LaunchedEffect(searchQuery)`); el contenido del Scaffold se envuelve
  en una `Column` que aplica el padding de insets una sola vez y apila header +
  lista (`NoSearchResults`/`EmptyState` con `Modifier.weight(1f)`).
- **Commit:** RUN 008 en `openhands/autonomous-notes`.
- **Test:** `NotesListSearchInteractiveTest` (3 tests de regresión: abrir y
  filtrar desde la UI, toggle off, limpiar query; + aserción de bounds que el
  campo no solape la primera fila).

## BUG-004 — "Deshacer" de una nota borrada podía sobrescribir otra nota (P1)

- **Impacto:** SQLite reutiliza rowids tras un borrado (`INTEGER PRIMARY KEY`
  autoincremental). Si el id de una nota borrada quedaba libre y después se creaba
  una nota nueva reutilizando ese mismo id, pulsar "Deshacer" hacía `REPLACE`
  sobre esa nota nueva: la nota viva se perdía y la restaurada quedaba con datos
  mezclados/perdidos (_lost update_ / _ID reutilizado_, sección 15 de la misión).
- **Reproducción:** crear nota A → borrarla → crear nota B (toma el id de A) →
  "Deshacer" de A.
- **Causa:** `NotepadViewModel.restore` hacía `repo.save(note)` sin comprobar si
  el id original seguía libre; `NoteDao.insert` con `OnConflictStrategy.REPLACE`
  sobrescribía la nota viva.
- **Estado:** FIXED — `restore` comprueba `repo.get(note.id)`; si el id quedó
  libre lo reutiliza (respeta `createdAt` e id), si fue reutilizado por otra nota
  reinserta bajo un id nuevo (0 → autoGenerate) conservando título/contenido.
  Nunca sobrescribe una nota viva.
- **Commit:** ejecución 007 en `openhands/autonomous-notes`.
- **Test:** `restore_whenOriginalIdReusedByAnotherNote_reinsertsUnderFreshId`.
