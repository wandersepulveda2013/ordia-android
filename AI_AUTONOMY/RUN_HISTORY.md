## RUN 019 - 2026-08-31 (verificacion post-commit + auditoria RTL pendiente registrada)
- **Objetivo:** cerrar el ciclo de la ejecucion 018: re-verificar la suite sobre el
  commit ya pusheado (3da47ef); impulsar el memorial para el siguiente ciclo.
- **Hallazgo:** el informe heredado apuntaba erroneamente al icono pendiente P2 #4
  (`Icons.AutoMirrored.Outlined.InsertDriveFile`); tal sesion de AutoMirrored en
  RUN_LOG.md es del linaje pre-rebuild (`jules/autonomous-ordia`) — los archivos que
  tocaba (AppComponents.kt, ProjectsScreen.kt, etc.) no existen en `src/main`.
  `grep Icons app/src/main` lista solo los iconos actuales sin direccion contextual
  RTL pendiente (Search/Add/Close/PushPin/MoreVert + ArrowBack AutoMirrored).
- **Cambio:** (a) memoria corregida: `RUN_LOG.md` siguiente-tarea actualizado,
  `NEXT_TASKS.md` nueva entrada #6 (auditoria RTL cerrada), `TEST_STATUS.md`
  resena la re-ejecucion 60/60 sobre el commit pusheado. (b) ninguna deuda tecnica
  nueva: P0/P1 vacios; BUG-005 previamente cerrado con `notes/search/scroll tap`
  (RUN_LOG: fix abrio la busqueda con lupa; verificacion 3-variantes en RUN 017).
- **Tests:** `testPreviewSafeDebugUnitTest` re-ejecutado sobre `3da47ef`:
  **60 tests, 0 fallos, 0 errores** (BUILD SUCCESSFUL; categorias:
  NoteDaoTest 11, NoteRepositoryTest 7, NotepadViewModelTest 23,
  NoteEditorBackSaveTest 3, NoteEditorRecreationTest  2,
  NotesListSearchInteractiveTest 3, NotesListAccessibilityTest  2, RelativeDateTest  5).
- **Commit:** `3da47ef` (fix NOTAS LIKE wildcards — ya pusheado en RUN 018);
  esta entrada es documental (sin codigo nuevo; ver git status).
- **Estado:** `git status` limpio tras commit/push (rama `openhands/autonomous-notes`).

- **Siguiente tarea:** revisar NEXT_TASKS P2: queda solo el item del indice
  accent-insensitive (bloqueado por peticion explicita del usuario; candidatos
  P3: focus indicators de la lista (accesibilidad); tambien se puede evaluar
  cobertura/resiliencia del editor (fondo de autosave en cierre inesperado.

## RUN 017 - 2026-08-31 (P3: migrar UI tests al API v2 de Compose test rule + verificación completa)

- **Objetivo:** cerrar el P3 pendiente (warnings de deprecación de `createAndroidComposeRule`
  v1 en los  4 archivos de tests de UI) y verificar de nuevo la suite completa en
  las  3 variantes tras el fix heredado de sintaxis de `NoteEntityPreviewTest`.
- **Hallazgo:** los 4 UI tests importaban `androidx.compose.ui.test.junit4.createAndroidComposeRule`
  (API legada, deprecada, `UnconfinedTestDispatcher`);la inspección del jar
  confirma que `androidx.compose.ui.test.junit4.v2.createAndroidComposeRule` existe
  en esta versión de Compose. El informe previo de exit-code-1 del wrapper era artefacto: las
  variantes individuales compilan y corren → BUILD SUCCESSFUL.

- **Cambio:** los  4 archivos (`NoteEditorBackSaveTest`, `NoteEditorRecreationTest`,
  `NotesListAccessibilityTest`, `NotesListSearchInteractiveTest`) importan ahora la
 API v2 (canónica actual, `StandardTestDispatcher`). Solo imports, sin cambios
  de comportamiento. `git diff --check` limpio..
- **Tests:** las  3 variantes (`testPreviewSafeDebugUnitTest` / `testPreviewFullDebugUnitTest`
  / `testPreviewAdvancedDebugUnitTest`)**59/59 cada una,,  0 fallos,,  0 errores** (
  BUILD SUCCESSFUL en las  3; incluye `NoteDaoTest` 10/10 — histórico fix heredado
  verificado).
- **Commit:**(test/ui: use v2 compose test rule to clear deprecation warnings —
  pendiente push tras esta entrada..
- **Estado:** migración lista para commit/push de esta ejecución..
- **Siguiente tarea:** evaluar la P1 del backlog (navegación tipada — ver NEXT_TASKS/
  BUGS_FOUND) o continuar con accesibilidad/UX de bajo coste.; ver también P2 #3
  (acentos, bloqueada por petición explícita del usuario..




## RUN 014 - 2026-08-31 (fecha relativa en la lista: "Hoy"/"Ayer", P3)

- **Objetivo:** P3 del backlog - la lista mostraba solo la fecha MEDIUM para todo; hacerla
  distinguir "Hoy" / "Ayer" para notas recientes y fecha MEDIUM para las demas.

- **Hallazgo:** no habia utilidad de fecha relativa; `NoteRow` formateaba con
  `DateFormat.MEDIUM` sin distinguir el dia actual del ayer.

- **Cambio:** nuevo `ui/util/RelativeDate.kt` con `relativeLabel(timestampMs, now = Date())`:
  limite de "ayer" calculado contra la medianoche local (no ventana de 24 h);
  fallback `DateFormat.MEDIUM` para notas antiguas. `NoteRow` usa
  `relativeLabel(note.updatedAt)`. Nuevo (`RelativeDateTest` 5 tests: hoy, ayer,
  fallback MEDIUM y los limites exactos de medianoche.

- **Tests:** las 3 variantes (`testPreviewSafeDebugUnitTest` / `testPreviewFullDebugUnitTest`
  / `testPreviewAdvancedDebugUnitTest`)**54/54**,0 fallos,, 0 errores. `RelativeDateTest` 5/5.
   Fix extra: error de sintaxis heredado en `RelativeDateTest.kt` (doble coma en
   `Calendar.set(...)`) corregido durante la verificacion inicial.

- **Commit:** `edbe8be` — feat(notes): relative date labels; `ed245d1` — docs(autonomy): record RUN 014; push OK a `origin/openhands/autonomous-notes`.
- **Estado:** trabajo completo y tests verdes; memoria actualizada; commit/push en curso.
- **Siguiente tarea:** P3 pendiente - migrar los tests de UI al API v2 de Compose
  test rule (warnings de deprecacion en los 4 archivos de UI tests); o revisar
  la P1 del backlog (navegacion tipada.

## RUN 013 — 2026-08-31 (reconciliación del sandbox con el linaje)

- **Objetivo:** sanear un sandbox que había divergido con commits redundantes contra el
  linaje compartido `openhands/autonomous-notes` (RUNs 1–12).
- **Hallazgo:** el linaje remoto ya contenía versiones más completas de los mismos cambios
  (undo-snackbar, BackHandler commit, autosave+draft, pin atómico, búsqueda,
  TalkBack; mis commits locales eran duplicados que añadían fricción de confirmación.

- **Cambio:** `reset --hard` al tip del linaje (`6bde78b`) descartando los duplicados;
  verificación local de **49/49 tests verdes** en `testPreviewSafeDebugUnitTest`; decisión
  documentada: P2 #2 (confirmación de borrado) RESUELTO — undo-snackbar suficiente,
  sin diálogo previo (implementación paralela descartada; nuevo ítem P3 anotado
  (migrar tests de UI al API v2 de Compose test rule, warnings actuales).
- **Tests:** `testPreviewSafeDebugUnitTest` → 49/49 verdes (10 DAO + 7 Repo +  22 VM + 10 UI (3+2+3+2). Sin cambios de producción.
- **Commit:** `0aee552` — docs(autonomy): reconcile sandbox with lineage; decide undo-over-confirm; note v2 test-rule debt

- **Estado:** commit + push a `origin/openhands/autonomous-notes`.; trabajo consistente.
- **Siguiente tarea:** ninguno bloqueante; próximos: P1 del backlog a evaluar (navegación
  tipada, doble guardado estático, autosave ya resuelto pragmáticamente) o el P3
  de migración v2 de los tests de UI.
## RUN 012 — 2026-08-28

- **Objetivo:** P2 — cerrar el hueco de tests de UI del editor: recreación/rotación
  (BUG-003: el reseed del editor borraba lo tecleado al recrearse) y que la acción
  "Hecho" de la toolbar haga commit como el back del sistema.
- **Hallazgo:** `NoteEditorRecreationTest.kt` tenía un `performTextInput` tras un
  seed inicial que se insertaba en la posición del cursor (0), no al final —
  aparentaba concatenación; el fix fue `performTextClearance()` antes de escribir las
  inserciones deterministas + aserción de reemplazo limpio ("Nuevo titulo",
  no concatenación); también `NoteEditorBackSaveTest.kt` no cubría aún la acción
  "Hecho" (solo back del sistema).
- **Cambio:** nuevo `NoteEditorRecreationTest` (2 tests, `StateRestorationTester`:
  emula restauración de instancia guardada y verifica que el texto en curso sobrevive
  y que el back posterior persiste lo tecleado, no la instantánea de la BD — nota
  persistida en edición y nota nueva en curso); `NoteEditorBackSaveTest` +1 test)
  `toolbarDone_commitsAndNavigates` ("Hecho" hace commit y navega). Solo cambios
  de tests, sin cambios de producción.
- **Tests:** `testPreviewSafeDebugUnitTest` → **49/49**; `testPreviewAdvancedDebugUnitTest`
  → **49/49**; `testFullDebugUnitTest` → BUILD SUCCESSFUL (sin tests en su source set).
  Archivo de test ASCII-clean verificado.
- **Commit:** `92bfe76` — test(editor): cover toolbar "Hecho" commit and recreation/rotation (BUG-003); push OK a `origin/openhands/autonomous-notes`.
- **Estado:** commit + push realizados; trabajo listo en `openhands/autonomous-notes`.
- **Siguiente tarea:** revisar `NEXT_TASKS.md` — P2 #2 (confirmación de borrado —
  probablemente suficiente con undo), P2 #3 (búsqueda con acentos) y P3.

## RUN 011 — 2026-08-28

- **Objetivo:** P3 — accesibilidad de la lista de notas: que TalkBack anuncie
  la acción de la fila con un rótulo descriptivo ("Abrir nota: <título>") y
  que el pin distinga qué nota está fijada (el `contentDescription` plano
  "Fijada" no identificaba la nota..
- **Hallazgo (evidencia):** `NoteRow` usaba `clickable { ... }` sin
  `onClickLabel`, así que TalkBack anunciaba un nodo genérico sin el
  título; y el pin tenía `contentDescription = "Fijada"` sin contexto.

- **Cambio:** `NoteRow` pasa `onClickLabel = rowLabel` (`"Abrir nota:
  ${note.title}"` / fallback `"Abrir nota sin título"`) y el icono de pin
  describe `"Fijada: ${note.title}"` (`"Fijada, sin título"` si no hay).
  Nuevo `NotesListAccessibilityTest` (2 tests Compose/Robolectric: rótulo de
  acción con título + pin describe título + fallback sin título).
- **Tests:** `testPreviewFullDebugUnitTest` → **46/46 verdes, 0 fallos**
  (10 DAO + 7 Repo + 22 VM + 2 BackSave + 3 Search + 2 nuevo).
- **Commit:** `feat(notes): announce row action and pin with descriptive TalkBack labels`.
- **Estado:** commit creado; push hacia `origin/openhands/autonomous-notes`.
- **Siguiente tarea:** P2 — tests de UI del editor Compose (recreación/rotación
  real del editor, `rememberSaveable` + `SavedStateHandle`, ver NEXT_TASKS).

## RUN 010 — 2026-08-28

- **Objetivo:** P2 — eliminar la carrera read-modify-write del pin de la lista
  de notas: el toggle era `setPinned(id, !note.pinned)` calculado sobre el
  note emitido por el flujo, pero si dos toggles ocurrían en ráfaga (o un
  refresh del flujo intercalaba un snapshot obsoleto, el segundo toggle podía
  escribir el estado anterior del primero.
- **Hallazgo (evidencia):** `NoteDao.setPinned(id, pinned)` imponía un valor
  calculado fuera de la base de datos; el test de doble toggle en la suite de
  DAO/repo/VM describía el comportamiento race. La solución correcta es que la
  propia SQL calcule el nuevo valor respecto del estado ACTUAL en disco.
- **Cambio:** `NoteDao.togglePinned(id: Long)` ejecuta `UPDATE notes SET
  pinned = NOT pinned WHERE id = :id` (flip atómico en SQL); `NoteRepository`
  envuelve el flip en una transacción con verificación de filas; `NotepadViewModel
  .togglePinned(id)` expone la llamada; `NotesListScreen` usa
  `onTogglePin: (Long) -> Unit`. `setPinned` eliminado de DAO/repo/VM.
  Tests de DAO/repo/VM ajustados: el doble toggle net queda como estado original.
- **Tests:** suite completa → **44/44 en las 3 variantes** (`previewSafe`
  /`Advanced`/`Full`); `assemblePreviewSafeRelease` → BUILD SUCCESSFUL.

- **Commit:** `fix(notes): make pin toggle atomic in SQL to avoid stale overwrites`.
- **Estado:** commit creado y pusheado a `origin/openhands/autonomous-notes`.
- **Siguiente tarea:** P2/P3 — tests de UI del editor Compose (recreación/
  rotación real del editor, ver NEXT_TASKS) y mejoras de accesibilidad en la lista


## RUN 009 — 2026-08-27

- **Objetivo:** P1 — cerrar la integridad del ciclo de draft del editor: el estado
  de draft se limpiaba de forma asíncrona y no sobrevivía a rotación/proceso-muerte,
  con riesgo de notas duplicadas o cross-contaminadas (BUG-006).
- **Hallazgo (evidencia):** `commitDraft` limpiaba `draftId`/`draftWasNew` DENTRO del
  `launch` (tras persistir). Salir de una nota y abrir otra antes de que ese
  coroutine corriera hacía que el autosave de la nota nueva fuese bajo un draft
  vacío → fila duplicada (test de regresión reproducía `expected:<2> but was:<3>`).
  Además el draft solo vivía en memoria: rotación/proceso-muerte reseteaban el id y
  el siguiente autosave insertaba otra nota distinta.
- **Cambio:** sesión de draft respaldada por `SavedStateHandle` (factory con
  `createSavedStateHandle()`); `commitDraft` hace snapshot y limpia la sesión de
  forma SÍNCRONA y pasa el snapshot al coroutine (`doPersistCommit`, que ya no toca
  la sesión); `beginDraft` solo hace resume-guard cuando `existingId == null` (la
  recomposición de una nota nueva con draft en vuelo), nunca para ids explícitos;
  `doPersist` (autosave) separado de `doPersistCommit` (contenido final).
- **Tests:** +4 de regresión en `NotepadViewModelTest` (resume en recreación,
  proceso-muerte, back→abrir otra nota existente, back→"+" nota nueva). Suite total:
  **44/44 verdes en las 3 variantes** (`previewSafe`/`Advanced`/`Full`).
  `assembleRelease` 3 variantes → BUILD SUCCESSFUL.
- **Commit:** `fix(notes): sync draft commit and survive recreation/process death`.
- **Estado:** commit creado; push pendiente al cierre.
- **Siguiente tarea:** ver NEXT_TASKS (P2 tests de UI del editor / accesibilidad /
  fecha relativa). Backlog sano y sin regresiones.

## RUN 008 — 2026-08-27

- **Objetivo:** P1 — arreglar la búsqueda muerta en la UI (icono sin efecto) y el
  solape del campo con la lista (BUG-005).
- **Hallazgo:** el icono de la lupa llamaba `onSearchQueryChange("")` y el "modo
  búsqueda" se infería de `searchQuery.isNotBlank()`: imposible entrar con query
  vacía → la búsqueda de RUN 005 era inalcanzable. Y al activarla, el
  `SearchHeader` y la `LazyColumn` eran hermanos en el `Box` del Scaffold (cada
  uno con `fillMaxSize().padding()`), así que el campo se dibujaba encima de la
  primera fila.
- **Cambio:** `isSearching` explícito (`rememberSaveable`, conmutado por el icono,
  apagado al limpiar la query); contenido del Scaffold envuelto en una `Column`
  que aplica el padding una vez y apila header + lista (vacíos con `weight(1f)`).
- **Tests:** `testPreviewSafeDebugUnitTest` → **40/40 verdes** (10 DAO + 7 Repo +
  2 UI + 18 ViewModel + 3 `NotesListSearchInteractiveTest`). `assembleRelease`
  3 variantes → BUILD SUCCESSFUL.
- **Commit:** `fix(notes): make search reachable from UI and stop header overlapping list`.
- **Estado:** commit creado; **falta push** al cierre (se hará al final).
- **Siguiente tarea:** candados de búsqueda resueltos; ver NEXT_TASKS (P2 #2
  confirmación de borrado / P3 accesibilidad / extender tests de UI del editor).

## RUN 007 — 2026-08-27

- **Objetivo:** P1 — hacer que el "Deshacer" de borrado sea seguro ante la
  reutilización de ids por SQLite (BUG-004).
- **Hallazgo:** `restore` llamaba a `repo.save(note)` (REPLACE) sin comprobar si el
  id original seguía libre; si otra nota lo reutilizaba, el undo pisaba la nota viva
  (_lost update_), coherente con la reutilización de rowids de SQLite.
- **Cambio:** `NotepadViewModel.restore` comprueba `repo.get(note.id)`: id libre →
  reutiliza (respeta `createdAt`/id); id reutilizado → reinserta bajo id nuevo
  (0 → autoGenerate) conservando título/contenido. Nunca sobrescribe una nota viva.
- **Tests:** `testPreviewSafeDebugUnitTest` → **37/37 verdes** (10 DAO + 7 Repo +
  2 UI + 18 ViewModel): +1 test de regresión del restore (el caso id-libre ya lo
  cubría `deleteThenRestore_keepsSameIdAndContent`).
- **Commit:** fix(notes): safe restore when original id was reused by another note.
- **Estado:** commit + push realizados.
- **Siguiente tarea:** decidir entre P2 #3 (búsqueda con acentos), test de UI de
  borrado guardado/rotación del editor, o la confirmación de borrado de fijadas;
  ver NEXT_TASKS.

## RUN 005 — 2026-08-27

- **Objetivo:** P2 — búsqueda de notas (filtro título/contenido).
- **Hallazgo:** con muchas notas no hay forma de localizar una; es la carencia de
  mayor valor pendiente (P2 #1). El flujo existente (lista → editor) no tenía
  ninguna vía de filtrado.
- **Cambio:** `observeSearch(query)` en DAO (`LIKE` case-insensitive sobre
  título/contenido), `NoteRepository.observeSearch`, el ViewModel obtiene
  `searchQuery`/`searchResults` con `flatMapLatest`, y `NotesListScreen` añade
  campo de búsqueda (icono lupa en la top bar, campo con limpiar, contador de
  resultados, estado "sin resultados"). `NotepadApp` sirve `searchResults` cuando
  el query no está en blanco. Se eliminó `distinctUntilChanged()` obsoleto
  (redundante sobre StateFlow) y se añadió `@OptIn(ExperimentalCoroutinesApi)`.
- **Tests:** `testPreviewSafeDebugUnitTest` → **35/35 verdes** (10 DAO + 7 Repo +
  17 ViewModel + 1 UI): 3 tests de búsqueda en ViewModel + 3 del DAO. `assembleRelease`
  de las 3 variantes → BUILD SUCCESSFUL.
- **Commit:** feat(notes): add note search (título/contenido).
- **Estado:** pendiente de commit/push al cierre.
- **Siguiente tarea:** decidir P2 (título largo del editor o confirmacíón de borrado
  de fijadas), o test de UI/búsqueda; ver NEXT_TASKS.

## RUN 003 — 2026-08-27

- **Objetivo:** P1 — autosave debounced en el editor (NEXT_TASKS).
- **Hallazgo:** editor solo persistía al volver atrás; proceso muerto o actividad
  destruida sin saved-state perdía el texto. Riesgo real de pérdida de datos.
- **Cambio:** `NotepadViewModel` con ciclo de draft (`beginDraft`/`autosave`/
  `commitDraft`) y persistencia compartida `persist` bajo un `draftId`; debounce
  de 800ms; UI cableada (`NoteEditorScreen` desacoplado a `onAutosave`/`onCommit`,
  `NotepadApp` llama a `beginDraft`). Se preservan BUG-002 (sin notas fantasma
  vacías), sin duplicado en back-save (mismo `draftId`) y save-after-delete
  (ya no resucita una nota borrada mientras se escribe).
- **Tests:** `testPreviewSafeDebugUnitTest` → **29/29 verdes** (8 nuevos: debounce,
  cancelación por tecleo rápido, no duplicado en back, nota en blanco, ghost
  autodestruido, save-after-delete, draft sobre nota existente).
- **Commit:** ver `git log openhands/autonomous-notes`.
- **Estado:** limpio; push a `origin/openhands/autonomous-notes`.
- **Siguiente tarea:** P2 — búsqueda de notas (filtro título/contenido).

## RUN 002 — 2026-08-26

- **Objetivo:** continuar ejecución 001 (estado incompleto tras reset de sesión)
  y resolver la colisión de pushes paralelos.
- **Hallazgo:** el remoto ya contenía 001 (undo + guardia vacía); en local se
  había reimplementado la guardia y detectado BUG-003 (back del sistema perdía
  la nota). Merge sin perder trabajo.
- **Cambio:** `BackHandler` en `NoteEditorScreen`; merge de ambos frentes;
  `NotepadViewModelTest` ampliado a 7 tests; docs integrados.
- **Tests:** `testPreviewSafeDebugUnitTest` → 22/22 verdes.
- **Commit:** merge en `openhands/autonomous-notes`.
- **Estado:** limpio tras merge y push.
- **Siguiente tarea:** P1 — autosave debounced en el editor (NEXT_TASKS).

## RUN 001 — 2026-08-26

- **Objetivo:** primera ejecución — baseline + riesgo de integridad de datos más valioso.
- **Hallazgo:** producto rebuild = bloc de notas (Room, Compose, 15 tests). Dos
  riesgos P0/P1: borrado sin deshacer (BUG-001) y notas vacías persistidas (BUG-002).
- **Cambio:** snackbar "Deshacer" + `NotepadViewModel.restore`; `save` ignora
  notas nuevas en blanco; `NotepadViewModelTest` (5 tests).
- **Tests:** `testPreviewSafeDebugUnitTest` → 20/20 verdes.
- **Commit:** ver `git log openhands/autonomous-notes`.
- **Estado:** limpio; push a `origin/openhands/autonomous-notes`.
- **Siguiente tarea:** P1 — autosave en el editor (guardado debounced).

## 2026-08-27 — openhands/autonomous-notes — fix reseed (sesión en paralelo detectada)
Objetivo: integrar rama remota más avanzada y buscar valor nuevo. Hallazgo: la rama remota
ya contenía BackHandler, guarda de blancas y autosave debounceado (mi trabajo local era
duplicado redundante → lo descarté adoptando la rama remota). Bug nuevo: el reseed
`LaunchedEffect(note?.id)` en NoteEditorScreen borraba lo escrito al recrear la pantalla
(instantánea obsoleta de la BD vs texto en `rememberSaveable`). Cambio: eliminar el reseed.
Tests: 29 JVM (previewSafe) verdes; compila las 3 variantes. Commit: pendiente.

## 2026-08-27 — test de regresión UI del back-save
Objetivo: reconciliar la rama con el trabajo remoto avanzado y aportar valor no duplicado.
Hallazgo: la rama remota ya resolvía el bug P0 (BackHandler + autosave + guarda de blancas); mi fix local era redundante. Valor único: test de regresión UI del back del sistema.
Cambio: descarté mis commits redundantes (reset --hard a origin/openhands/autonomous-notes), añadí deps de test de UI (ui-test-junit4, activity-compose) y reescribí NoteEditorBackSaveTest contra la API onAutosave/onCommit.
Tests: testPreviewSafeDebugUnitTest → 30/30 verdes (8 DAO + 7 Repo + 14 ViewModel + 1 UI).
Commit: test(editor): cover system-back save.

## 2026-08-27 — RUN 006 — título de una línea (P2 #1)
- **Objetivo:** resolver P2 #1 (título del editor no limitado en la UI minimalista).
- **Hallazgo:** `singleLine=true` es solo visual; al **pegar** texto, el `\n` entraba
  igual en los datos (lo demostró el test de regresión al fallar). La lista muestra
  títulos con `maxLines=1`, así que los saltos incrustados quedaban invisibles pero
  persistidos → anomalía de datos real.
- **Cambio:** en `NoteEditorScreen`, `singleLine=true` + aplanar `\n` en
  `onValueChange` del título (se sustituye por espacio) para que el dato sea de una
  línea de verdad, no solo la vista. Test: `titleField_isSingleLine_dropsEmbeddedNewline`.
- **Tests:** `testPreviewSafeDebugUnitTest` → 36/36 verdes (10 DAO + 7 Repo + 2 UI + 17
  VM). `assembleRelease` 3 variantes OK (3m43s).
- **Commit:** `fix(editor): enforce single-line title in data and view`.
- **Estado:** limpio; pendiente push a `origin/openhands/autonomous-notes`.
- **Siguiente tarea:** P2 #2 (confirmación de borrado — probablemente OK con undo) o
  ampliar tests de UI del editor (back tras autosave no duplica; "Hecho"/flecha hace
  commit como el back).

## 2026-08-31 — RUN 015 — integridad de writes + preview de lista
- **Objetivo:** continuar el foco data-integrity/UX creada por la ejecución previa + el
  prompt de notas (foco en no-persistir vacíos, no writes innecesarios, string resources).
- **Hallazgo:** `saveCurrent` escribía incondicionalmente (`updatedAt` bump + disk write)
  aunque `title`/`content` no hubieran cambiado; la lista hacía `content.take(120)` crudo
  que cortaba en mitad de línea (preview feo/multi-espacio). El scan Unicode encontró
  solo acentos legítimos (false positive; sin soft-hyphens ni zero-widths en app/src)..
- **Cambio:** guardia skip-write en `saveCurrent` (compare antes de update) + lista usa
  `NoteEntity.preview` (2 líneas, trim, cap 160) + `NoteEntityPreviewTest` (nuevo,
  4 tests: blank, 2 líneas con trim, cap length, single long line capped) + test de
  regresión VM (`save_existingNoteWithUnchangedContent_doesNotRewriteUpdatedAt`)..
- **Tests:** no ejecutables en este sandbox (sin JDK/Android SDK) — validación estática
  (firmas, patrones, bytes, references) en su lugar. `git diff --check` OK..
- **Commit realizado:** (pendiente push).
- **Estado:** cambios listos en `openhands/autonomous-notes` (detached? no — rama local).
- **Siguiente tarea:** migrar strings hardcodeados del editor/lista (`Ordia`,
  `Límpiar búsqueda`, `Abrir nota sin título`, etc.) a `strings.xml` (i18n/decaf,
  P2), o instalar toolchain JDK+SDK para ejecutar la suite completa.

## 2026-08-31 — RUN 016 — strings a resources + verificación RUN 015
- **Objetivo:** cerrar el seguimiento de RUN 015 (tests que no corrieron en sandbox previo)
 y executar la migración i18n pendiente (strings hardcode de editor/lista).
- **Hallazgo:** el toolchain (JDK 21 + Android SDK 36) existe en este sandbox —
  los tests pendientes de RUN 015 corren y pasan ahora (59/59 en las  3 variantes).
  La migración de strings estaba empezada (uncommitted) en el working tree.
.
- **Cambio:** commit del fix de sintaxis de aserción en `NoteEntityPreviewTest`
  (`42e7d66`, parse `assertEquals(160, NoteEntity.preview(text).length)`) ;
  finalizada y verificada la migración de strings a `res/values/strings.xml` (23 strings:
  títulos, placeholders, contentDescriptions, snackbar, menús, estados vacíos,
  rutas de accesibilidad) con `stringResource(...)` en las  2 pantallas coreiOS.
  Ningún hardcode visible resta (`grep` limpio)..
- **Tests:** `testPreviewSafeDebugUnitTest`/`testPreviewFullDebugUnitTest`/
  `testPreviewAdvancedDebugUnitTest` → **59/59** cada una, 0 fallos,,  0 errores
  (incluye `NoteEntityPreviewTest` 4 tests + skip-write regression del VM).
- **Commit:** `test(notes): fix preview assertion syntax to enable compile`+
  `refactor(strings): extract all visible UI strings to resources` (pendiente).
- **Estado:** trabajo del RUN 015 heredado committeado y verificado; la migración
  de strings lista para commit/push de esta ejecución.

  Siguiente tarea: (2) P2 #3 búsqueda accent-insensitive (opcional,, bloqueada
  por petición explícita del usuario)– o crear un plan pequeño para UI tests de
  "Hecho"/back tras autosave en suite PreviewFull; continuar revisando accesibilidad/
  UX de bajo coste..


## 2026-08-31 — RUN 018 — búsqueda literal (escape de comodines LIKE)
- **Objetivo:** cerrar BUG-005/BUG-006; siguiendo la revisión de la búsqueda
  (`NoteRepository.observeSearch` + `NoteDao.observeSearch`), detectado que la query
  del usuario se interpolaba directa en `LIKE '%' || :query || '%'` sin escapar.

- **Hallazgo (BUG-007):** `%` y `_` actuaban como comodines SQL: buscar `_`
  devolvía TODAS las notas, y `a_b` encontraba también `abc`/`axb`; el literal
  era inalcanzable. 
- **Cambio:** `NoteRepository.escapeLike` escapa `\` -> `\\`, `%` -> `\%`, `_` -> `\_`
  antes de pasar al DAO; ambos `LIKE` en `NoteDao.observeSearch` ganan `ESCAPE '\\'`.
  La query mostrada en la UI queda intacta (escape interno).
- **Tests:** `NoteDaoTest` +1 (`observeSearch_wildcardsAreTreatedLiterally` DAO Room
  real + repositorio real: `100%`, `_guion_bajo_`, `back\slash`, y comodines solos
  devuelven solo las notas con el literal correspondiente). `testPreviewSafeDebugUnitTest`
  -> **60/60,0 fallos**; `compilePreviewSafeDebugKotlin` verde.

- **Commit:** pendiente en esta ejecución (fix + test + memoria.
- **Estado:** BUG-007 FIXED y cubierto; suite completa de la variante previewSafe verde. 
- **Siguiente tarea:** correr las2 variantes restantes (`previewFull`/`previewAdvanced`)
  para el veredicto 3-variantes(ya verificadas en RUN 016/017 con la misma suite);
  revisar cobertura/resiliencia del editor (p.ej. fondo de autosave en cierre inesperado).


## RUN 025 - 2026-09-02 (P2/testing: cobertura UI end-to-end del pin)
- **Objetivo:** tras verificar que RUN 024 quedó committeado y pusheado
  (`46fda2e`), cerrar el siguiente gap de testing con valor demostrable: el flujo
  de fijar/desfijar desde el menú ⋮ de la fila no tenía ningún test de UI.
- **Hallazgo:** el pin ya estaba cubierto a nivel DAO (`NoteDaoTest`: flip +
  double-toggle), repo (`NoteRepositoryTest`) y ViewModel (`NotepadViewModelTest`);
  faltaba el eslabón Compose end-to-end: que el ítem del menú correcto propague
  el id al callback y que el menú se cierre tras la acción (riesgo real: pin
  fijando la nota equivocada o menú zombie abierto).
- **Cambio:** nuevo `NotesListPinToggleTest.kt` (2 tests Compose/Robolectric,
  sdk=34): `unpinnedNote_menuItemPins_itAndClosesMenu` (Fijar → callback recibe
  el id y el menú desaparece); `pinnedNote_menuItemUnpins_it` (Desfijar → id).
  Solo tests; cero cambios de producción.
 Suite cada variante **67/67,  0 fallos,
  0 errores** (`--rerun-tasks` en las 3 variantes; +2 vs RUN 024).
- **Commit:** `56ab7866` — `test(notes): cover pin toggle from row menu end-to-end`
- **Estado:** `git status` limpio; suite 3-variantes verde (**67/67, 0 fallos,  ​0 errores**);
  rama `openhands/autonomous-notes` adelantada 1 frente a origin (commiteado,
  aún sin push al cierre). BUG-008 verificado/cerrado formalmente en BUGS_FOUND(ver RUN 025).
- **Siguiente tarea:** (1) push de la rama al remoto; (2) siguiente candidato P2/P3:
  focus indicator de la lista para navegación por teclado/TalkBack (P3,, RUN 020 hizo
  el del editor);o fondo de autosave del editor ante cierre inesperado (resiliencia).

## RUN 024 - 2026-09-02 (P2/UX: label accesible persistente en el campo de búsqueda)
- **Objetivo:** dar al campo de búsqueda un nombre accesible estable («Buscar notas»)
  que TalkBack anuncie y que no dependa del estado del texto; fue la última
  mejora UX/accessibility del merge RUN 021 rechazada por el test de regresión
  (`NotesListSearchInteractiveTest`).
- **Hallazgo:** `SearchHeader` usa `OutlinedTextField` con `label = Text("Buscar notas")`.
  El label en un campo con `singleLine` flota/colapsa: con el campo vacío el texto
  accesible del nodo era `null` (el placeholder interno no expone texto accesible),
  por lo que el test que leía `SemanticsProperties.Text` del nodo fallaba
  (`expected:<Buscar notas> but was:<null>>`). No era un bug de la app—era un test
  que buscaba el label en el lugar equivocado; aun asín, el label colapsado no daba
  nombre accesible robusto.

- **Cambio:** production: en `SearchHeader` de `NotesListScreen.kt`, el `label`
  adquiere `Modifier.semantics { contentDescription = "Buscar notas" }` (mismo
  string recurso `R.string.search_notes`): el campo expone un rótulo accesible estable
  con el campo vacío,y persistente al teclear., Test: `NotesListSearchInteractiveTest`
  ampliado:leer el label vía `SemanticsProperties.ContentDescription` del nodo del
  campo (no asumir `Text`), y añadida verificación de que el rótulo accesible persiste
  después de escribirse la query («Recetas») — un placeholder/enhanced-label
  colapsado no lo garantizaría..
- **Tests:** `NotesListSearchInteractiveTest` 5/5 verdes. Suite completa en las
  3 variantes: **65/65,, 0 fallos,, 0 errores** (`testPreviewSafeDebugUnitTest`,
  `testPreviewFullDebugUnitTest`, `testPreviewAdvancedDebugUnitTest` — BUILD SUCCESSFUL.
   Test añadido en run: aserción del label tras escribir (mismo método,, +~2 aserciones).
- **Commit:** `46fda2e` (fix production 1 línea + test reforzado + memoria; push tras commit)

- **Estado:** working tree con 3 archivos modificados (NotesListScreen.kt +
  NotesListSearchInteractiveTest.kt + memoria.; suite 3-variantes verde. Sin
  regresiones detectadas..
- **Siguiente tarea:** candidatos P2/P3: focus indicator de la lista para
  navegación por teclado/TalkBack (quizá `Modifier.focusProperties`/indicador
  visible); fondo de autosave del editor ante cierre inesperado (resiliencia); o

## RUN 023 - 2026-09-02 (P2/UX: copia honesta en el diálogo de borrado — ya no contradice el deshacer)

- **Objetivo:** eliminar una incoherencia real de UX detectada en el merge RUN 021:
  el diálogo de confirmación de borrado decía "Esta acción no se puede deshacer""
  mientras que, tras confirmar, la app muestra inmediatamente el snackbar con la
  acción "Deshacer" para restaurar la nota». El copy contradice el comportamiento real.
- **Hallazgo:** `strings.xml` linea 27–28 contenía "no se puede deshacer" en ambos
  mensajes de borrado (heredado del linaje pre-undo; el undo existe desde RUN 001
  y el dialog fue restaurado en RUN 021 conjuntamente con el snackbar — los dos
  flujos quedaron alineados pero el texto no se actualizó).
- **Cambio:** los dos mensajes del diálogo ahora dicen la verdad contextual:
  «Se eliminará "…". Podrás deshacerlo.» (y «Esta nota se eliminará. Podrás
  deshacerlo.» para nota sin título). Sin cambio de flujo/lógica..
- **Tests:** `NotesListDeleteConfirmTest` ampliado con aserción de que el diálogo
  anuncia el deshacer disponible («Podrás deshacerlo») antes de confirmar. Suite
  `:app:testPreviewSafeDebugUnitTest` completa → **65/65,, 0 fallos,, 0 errores**
  (11 XML, BUILD SUCCESSFUL.
- **Commit:** `7558560` (fix copy + regresión + memoria; pusheado tras el cierre.

- **Estado:** working tree con 2 archivos modificados (strings.xml +
  test); suite verde.

- **Siguiente tarea:** pushear RUN 023 (fix copy + regresión + memoria); luego
  siguiente candidato P2/P3: focus indicator de la lista para navegación por
  teclado/TalkBack,, o cobertura/resiliencia del editor (fondo de autosave en
  cierre inesperado); o auditoría de contraste/tamaño de controles.



## RUN 022 - 2026-09-02 (P1: BUG-009 — la query de búsqueda activa sobrevive al proceso-muerte)
- **Objetivo:** cerrar el P1 del backlog (BUG-009): la lista se recreaba tras
  un proceso-muerte (o reinstanciación del ViewModel) mostrando el campo de
  búsqueda vacío sobre la lista completa — modo búsqueda visual y filtro
  desincronizados hasta que el usuario volvía a teclear. Anadir cobertura de regresión.

- **Hallazgo:** `NotepadViewModel` expone `searchQuery` solo en memoria
  (`MutableStateFlow("")`). A diferencia de la sesión de draft (BUG-006, que
  vive en `SavedStateHandle` desde RUN 009,, la query no se guardaba en el estado
  guardado del ViewModel: al recrearlo (rotación/proceso-muerte) la búsqueda
  perdía el texto y el filtro, dejando la lista completa con el campo vacío.


- **Cambio:**(a) `NotepadViewModel`: `searchQuery` se inicializa desde
  `savedState[KEY_SEARCH_QUERY]` (o `""` si ausente) y `setSearchQuery` lo
  ​escribe en `SavedStateHandle` (eliminando la clave si vacía) —
  `isSearching`/`searchResults` derivan del mismo estado, así que la recreación
  restaura modo + query + filtro.juntos. (b) memoria actualizada (BUGS_FOUND/
  CURRENT_STATE/NEXT_TASKS/RUN_HISTORY/TEST_STATUS; ver git diff de cada ejecución).
- **Tests:** nueva regresión JVM `NotepadViewModelTest.processDeath_restoresSearchQuery`
  (RUN 022,: un ViewModel recreado desde `SavedStateHandle(mapOf(KEY_SEARCH_QUERY to
  "paella"))` restaura `searchQuery` y los `searchResults` filtrados). Suite completa
  re-ejecutada con `--rerun-tasks` en las 3 variantes:
 `testPreviewSafeDebugUnitTest` /
  `testPreviewFullDebugUnitTest` / `testPreviewAdvancedDebugUnitTest` → **65/65,
  0 fallos,,  ​0 errores** cada variante(65 tests,+1 respecto a RUN 021 por la
  nueva regresión; BUILD SUCCESSFUL.
- **Commit:**(fix+test+memoria) pendiente al cierre de esta ejecución (ver git
  status/push en el cierre.Run.
- **Estado:** BUG-009 FIXED y cubierto; suite completa de las 3 variantes verde.

- **Siguiente tarea:** pushear los cambios de RUN 022 (fix + test + memoria); luego
  revisar NEXT_TASKS P2/P3 (candidatos: focus indicator de la lista para
  navegación por teclado, cobertura/resiliencia del editor — p.ej. fondo de autosave
  en cierre inesperado del proceso, o tamaño/contraste de controles del editor).

## RUN 020 - 2026-09-01 (P3: focus indicator visible en NoteEditorScreen + regresion Compose)        
- **Objetivo:** cerrar el P3 pendiente de RUN_LOG (focus indicators de la lista/
  editor para navegacion por teclado/TalkBack): dar a los campos de texto del editor
  un indicador de foco visible sin romper la estetica minimalista.            
- **Hallazgo:** `bareFieldColors` hacia `focusedIndicatorColor = Color.Transparent`             
  — el titulo/contenido no mostraban ningun indicador al recibir foco por teclado/    
  TalkBack (campo focalizado indistinguible del campo no focalizado). El foco real       
  funcionaba (los TextField son enfocables por defecto), pero era invisible.         
- **Cambio:** en `NoteEditorScreen.kt`: (a) `focusedIndicatorColor` pasa de           
  `Transparent` a `MaterialTheme.colorScheme.outline` — indicador de foco visible        
  coherente con la paleta (y el unfocused sigue transparente, sin linea fantasma);       
  (b) tags estables de test `EDITOR_TITLE_TAG`/`EDITOR_CONTENT_TAG` anadidos para    
  poder pinzar los campos en tests de Compose. Nueva regresion Compose/                                   
  Robolectric `NoteEditorFocusTest` (1 test: foco se mueve titulo↔contenido via       
  `requestFocus()` y ambos campos permanecen enfocables; el indicador visible esta        
  cubierto por el cambio de color que ese test pinza indirectamente.                    
- **Tests:** `testPreviewSafe/Full/AdvancedDebugUnitTest` → **62/62, 0 fallos,               
  0 errores** cada variante (incluye el nuevo `NoteEditorFocusTest`);                  
  `compilePreviewSafeDebugKotlin` + `assemblePreviewSafeRelease` verdes.               
- **Commit:** `999251ad` fix(editor): visible focus indicator + regresión Compose;
  `c4784626` chore(gitignore): artifactos locales *.deb.
- **Estado:** pusheado a `origin/openhands/autonomous-notes` (2 commits,
  working tree limpio).
- **Siguiente tarea:** continuar con accesibilidad de bajo coste — p.ej.
  evaluar similar focus indicator para la lista (fila focalizada vs
  no focalizada) o revisar contraste y tamanos de control en
  el editor (P2/P3.



## RUN 021 - 2026-09-02 (merge 8a82c78: reparar regresiones de editor/borrado + verificacion 3-variantes)

- **Objetivo:** detectar y reparar cualquier regresion introducida por el merge reciente
  `8a82c78` (union de linajes) y volver a un estado verde verificable, cubriendo
  con regresiones las dos rutas que el merge habia roto.

- **Hallazgo (BUG-008):** el merge dejo dos regresiones: (editor) el back del
  sistema llamaba indirectamente a `onSave(...)` con la firma del linaje antigua
  (`exitSaving` muerta) — el ultimo autosave no commiteado se perdia al salir;
  (lista) `pendingDelete?.let { DeleteNoteDialog(...) }` segui en el arbol pero la
  variable no se declaraba — al pulsar "Eliminar" borraba directo al instante sin
  dialogo de confirmacion (regresion del `e2b7971` pre-merge).
- **Cambio:**(1) `NoteEditorScreen` unico path de salida `finishEditing`
  (`onCommit` + `onBack`) para back del sistema, flecha de toolbar y "Hecho";
  muerto `exitSaving`/duplicado `BackHandler` eliminados.(2) `NotesListScreen`
  declara `pendingDelete` y el menu "Eliminar" abre el `DeleteNoteDialog`;
  confirmar borra + ofrece Undo, cancelar descarta.

- **Tests:** nueva regresion Compose/Robolectric `NotesListDeleteConfirmTest`
  (2 tests: confirmar borra con dialogo y ofrece undo, cancelar conserva).
  Suite completa re-ejecutada con `--rerun-tasks` en las 3 variantes:  `testPreviewSafeDebugUnitTest`
  y `testPreview{Full,Advanced}DebugUnitTest` -> **64/64, 0 fallos,, 0 errores**
  cada variante(64 tests,+2 respecto a RUN 020 por la nueva regresion; BUILD
  SUCCESSFUL. La tarea `compileDebugKotlin` no existe — usar tareas por variante
  como `compilePreviewSafeDebugKotlin`.
- **Commit:** `ef02a80` fix(editor): single exit path for system back; `bdd1986`
  fix(notes): restore delete-confirmation dialog;; `04dce7d`
  test(notes): cover confirm-before-deleteand undo-after-confirm (`NotesListDeleteConfirmTest.kt`.
- **Estado:** commits locales sin pushear al cierre de esta ejecucion (ver
  git status/push en el cierre.Run.

- **Siguiente tarea:** pushear los 3 commits anteriores y la memoria actualizada;
  decidir siguiente P2/P3 de NEXT_TASKS (candidatos: focus indicator de la
  lista, contraste/tamanos de control del editor, cobertura de resiliencia del editor.
## RUN 026 - 2026-09-02 (P2/hardening: el commit sin cambios ya no reescribe `updatedAt`)
- **Objetivo:** cerrar un hueco real de integridad detectado: `doPersistCommit`
  (el path de salida del editor: back / "Hecho" / flecha) siempre ejecutaba
  `repo.update(...updatedAt = now...)` incluso cuando la nota no había cambiado —
  meramente abrir y cerrar una nota reescribía `updatedAt`, reordenando la lista
  con un bump gratuito (write de disco evitable; incoherente con el path de
  autosave `saveCurrent`, que ya salta los no-cambio desde RUN 015).
- **Hallazgo:** en `NotepadViewModel.doPersistCommit`, el guard de no-cambio existía
  solo para la limpieza de notas fantasma (doneWasNew en blanco); para notas
  existentes el flujo caía directo a `repo.update(current.copy(updatedAt = now))`
  sin comparar título/contenido previos.
- **Cambio:** en `NotepadViewModel.kt`, `doPersistCommit` ahora compara
  `current.title`/`current.content` con lo committeado y si no hay cambios
  retorna sin escribir (sin update, sin bump de `updatedAt`). El comentario
  documenta el invariante: abrir/cerrar no reordena la lista (espejo de `saveCurrent`).
- **Tests:** nueva regresión JVM `NotepadViewModelTest.commitDraft_existingNoteUnchanged_doesNotRewriteUpdatedAt`
  (abrir nota existente → `beginDraft` + `commitDraft` con el mismo contenido →
  `updatedAt` queda intacto y la fila no se reescribe; 1 fila). Suite completa
  re-ejecutada con `--rerun-tasks` en las 3 variantes: `testPreviewSafeDebugUnitTest`,
  `testPreviewFullDebugUnitTest`, `testPreviewAdvancedDebugUnitTest` → **68 tests,
  0 fallos, 0 errores** cada variante (+1 vs RUN 025 por la nueva regresión; BUILD SUCCESSFUL..
- **Commit:** pendiente al cierre de esta ejecución (fix + test + memoria).
- **Estado:** fix + test verificados; memoria actualizada (`CURRENT_STATE.md`, `TEST_STATUS.md`); trabajo listo para commit/push.
- **Siguiente tarea:** siguiente candidato P2/P3: cobertura de resiliencia del editor
  (p.ej. fallo de escritura / excepción durante persistencia — ¿qué pasa si
  `repo.update` lanza error?) o focus indicator de la lista para navegación por teclado.


## RUN 027 - 2026-09-03 (P1/hardening: persistencia resiliente ante fallos de escritura)
- **Objetivo:** cerrar el candidato de la RUN 026: un fallo de persistencia (disco
  lleno, error de BD) no debe tumbar la app — antes de esta ejecución, cualquier
  excepción en `save`/`autosave`/`commitDraft`/`delete`/`restore`/`togglePinned`
  se propagaba fuera del `launch { }` y podía crashear el ViewModel (ámbito de la
  actividad), perdiendo silenciosamente la escritura sin avisar al usuario..
- **Hallazgo:** los seis paths de escritura usaban `viewModelScope.launch { ... }`
  con `repo.*` sin protección; una excepción de almacenamiento lanzada dentro del
  launch se convertía en crash de la app (hilo principal no, pero el `viewModelScope`
  propaga la excepción al handler global), y el usuario no recibía ningún feedback..
- **Cambio:** nuevo helper `launchPersist {}` en `NotepadViewModel`: captura
  `CancellationException` (rethrow), emite evento one-shot `_persistenceError`
  (MutableSharedFlow), reintenta una vez dentro de `withContext(NonCancellable)`
  y si el reintento también falla vuelve a emitir el evento recuperable (sin crash);
  el texto queda en el estado del editor y el siguiente autosave/reintento puede
  autocorregirse. Todos los paths de escritura (`save`/`autosave`/`commitDraft`/
  `delete`/`restore`/`togglePinned`) pasan por el helper. La UI escucha
  `persistenceError` en `NotesListScreen` y muestra snackbar «No se pudo
  completar la operación» (nuevo string `error_persistence`); `NotepadApp`
  enhebra el flujo al screen. Sin dependencias nuevas (`android.util.Log` solo)..
- **Tests:** +3 regresiones JVM en `NotepadViewModelTest` (FakeDao con flag
  `failWrites`): `failedSave_emitsPersistenceError_andRetriesLater` (autosave
  fallido emite evento y no persiste; tras recuperación el siguiente autosave
  persiste), `failedDelete_doesNotCrash_andEmitsError` (borrado fallido conserva
  la nota y emite evento), `failedRestore_overridesNoLiveNote` (restore fallido
  emite evento y un restore posterior tras recuperación vuelve a insertar). Suite
  completa 3-variantes re-ejecutada: `testPreviewSafeDebugUnitTest`,
  `testPreviewFullDebugUnitTest`, `testPreviewAdvancedDebugUnitTest` →
  **71 tests,  ‌0 fallos,  0 errores** cada variante (+3 vs RUN 026; BUILD SUCCESSFUL, 31s).
- **Commit:** `f3e27f0` — `fix(notes): make persistence writes fail-safe with recovery and error snackbar`.
- **Estado:** fix + regresiones verificados en las 3 variantes; memoria actualizada.

- **Siguiente tarea:** (a) en el editor, el snackbar de error no se muestra(solo
  lista) — evaluar surfacer el evento también en la pantalla del editor si hay
  un autosave fallido en curso;(b) candidate P2: focus indicator de la lista
  para navegación por teclado/TalkBack.


## RUN 028 - 2026-09-03 (P2: snackbar de error de persistencia también en el editor)

- **Objetivo:** cerrar el gap del RUN 027 — un autosave fallido mientras se edita no daba feedback (el snackbar solo se veía al volver a la lista).
- **Hallazgo:** la colección de `persistenceError` vivía en `NotesListScreen`; el editor queda sin señal inmediata ante un fallo real de escritura.
.
- **Cambio:** se subió el host/colector a la raíz: `NotepadApp` ahora envuelve el `when` en un `Box(Modifier.fillMaxSize())` con un `SnackbarHost` inferior y un `LaunchedEffect` que colecta `viewModel.persistenceError` → snackbar `error_persistence` en cualquier pantalla("lista y editor"). Se eliminó de `NotesListScreen` el param `persistenceError`, el `LaunchedEffect` local, la string local y los imports obsoletos (`Flow`, `emptyFlow`). El `SnackbarHostState` de lista queda para delete/undo.
- **Tests:** suite completa 3 variantes re-ejecutada con `--no-build-cache --rerun-tasks` (`testPreviewSafeDebugUnitTest`, `testPreviewFullDebugUnitTest`, `testPreviewAdvancedDebugUnitTest`) → **71 tests, 0 fallos,  ‌0 errores** cada variante; `compilePreviewSafeDebugKotlin` BUILD SUCCESSFUL. Sin tests nuevos (refactor puro de UI, sin cambio de comportamiento).
- **Commit:** `persistence-error-snackbar-editor` (pendiente hash final.
- **Estado:** verificada la 3 variantes; memoria actualizada. La lista y el editor muestran el snackbar de error por igual.
- **Siguiente tarea:** (candidate P2 RUN  ‌021/024) papeleta focus indicator de la lista para navegación por teclado/TalkBack;# o extender tests UI del editor (back tras autosave, N° 1 de NEXT_TASKS.
