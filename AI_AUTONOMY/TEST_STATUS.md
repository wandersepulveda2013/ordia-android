# TEST_STATUS — Ordía (bloc de notas)

## Suites disponibles

- `:app:testPreviewSafeDebugUnitTest` (JVM + Robolectric, sdk=34 para los de UI):
  - `NoteDaoTest` — Room in-memory (10 tests).
  - `NoteRepositoryTest` — FakeDao en memoria (7 tests).
  - `NotepadViewModelTest` — `Dispatchers.setMain(StandardTestDispatcher)` (22 tests,
  RUN 009: +4 del ciclo de draft — resume en recreación, proceso-muerte, carrera
  commit→beginDraft hacia otra nota, y nota nueva tras back).
  - `NoteEditorBackSaveTest` — UI Compose/Robolectric (3 tests; RUN 012
    añade `toolbarDone_commitsAndNavigates` — "Hecho" hace commit y navega).
  - `NoteEditorRecreationTest` — UI Compose/Robolectric (2 tests, RUN 012):
    regresión de BUG-003 — la recreación (rotación/proceso-muerte, via
    `StateRestorationTester`) preserva el texto en curso sin persistir y el commit
    posterior persiste lo tecleado, no la instantánea vieja.

  - `NotesListSearchInteractiveTest` — UI Compose/Robolectric (3 tests, RUN 008).
- `NotesListAccessibilityTest` — UI Compose/Robolectric (2 tests, RUN 011).
- `RelativeDateTest` — unit tests puros (5 tests, RUN 014: "Hoy"/"Ayer"
  con límite de día natural local y fallback MEDIUM; incluye los dos límites
  exactos de medianoche).
- Variantes `previewFull` / `previewAdvanced`: mismo `src/test` (sin tests
  específicos de flavor por ahora).

## Último resultado

- 2026-08-31 (ejecución 016, strings + RUN 015 heredado): verificado en este
  sandbox → las  3 variantes (`testPreviewSafeDebugUnitTest` / `testPreviewFullDebugUnitTest`
  / `testPreviewAdvancedDebugUnitTest`)**59 tests,,  0 fallos,,  0 errores** (BUILD
  SUCCESSFUL). Incluye los 4 tests de `NoteEntityPreviewTest` y el test de
  regresión del skip-write (ambos de RUN 015,, que no pudieron ejecutarse en el
  sandbox anterior por falta de JDK/Android SDK) — ahora verificados.
- 2026-08-31 (ejecución 014, fecha relativa): verificado en el sandbox
  → las 3 variantes (`testPreviewSafeDebugUnitTest` / `testPreviewFullDebugUnitTest`
  / `testPreviewAdvancedDebugUnitTest`)**54 tests,  0 fallos,, 0 errores** (BUILD
  SUCCESSFUL). Añadido `RelativeDateTest` (5 tests, P3: "Hoy"/"Ayer" con
  límite de día natural local (medianoche local)y fallback MEDIUM; incluye los dos
  límites exactos de medianoche). fuente `RelativeDate.kt` + `NotesListScreen`.
- 2026-08-31 (ejecución 013, reconciliación): `testPreviewSafeDebugUnitTest`
  replanteado y verificado en el sandbox → **49 tests,  0 fallos,  0 errores** (BUILD
  SUCCESSFUL, 33s). Sin cambios de producción; decisión documentada en
  `NEXT_TASKS.md` P2 #2 (undo-snackbar suficiente, sin diálogo de confirmación).
  Deuda anotada: warnings de deprecación de `createAndroidComposeRule` v1 en
  los 4 archivos de tests de UI (P3).
- 2026-08-28 (ejecución 012): `testPreviewSafeDebugUnitTest` → **49 tests,
  0 fallos, 0 errores** (BUILD SUCCESSFUL; `testPreviewAdvancedDebugUnitTest`
  → **49/49**. Añadidos `NoteEditorRecreationTest` (2 tests Compose/Robolectric
  de regresión de BUG-003: recreación preserva lo tecleado sin persistir; el commit
  posterior persiste lo tecleado, no la instantánea de la BD — nota persistida y nota
  nueva en curso) y `NoteEditorBackSaveTest.toolbarDone_commitsAndNavigates`
  (la acción "Hecho" de la toolbar hace commit y navega igual que el back del sistema).
  `testFullDebugUnitTest` → BUILD SUCCESSFUL (sin tests en su source set).
- 2026-08-28 (ejecución 011): `testPreviewFullDebugUnitTest` → **46 tests,
  0 fallos,, 0 errores** (BUILD SUCCESSFUL. Añadido `NotesListAccessibilityTest`
  (2 tests Compose/Robolectric, P3):la fila expone `onClickLabel` con
  "Abrir nota: <título>" (y fallback "Abrir nota sin título"`; el pin
  describe "Fijada: <título>". Fuente: `NoteRow` en `NotesListScreen.kt`.).

- 2026-08-28 (ejecución 010): `testPreviewSafeDebugUnitTest` → **44 tests,
  0 fallos, 0 errores** (BUILD SUCCESSFUL); `testPreviewAdvancedDebugUnitTest`
  → **44/44**; `testPreviewFullDebugUnitTest` → **44/44**. `assemblePreviewSafeRelease`
  → BUILD SUCCESSFUL. `togglePinned` refactorizado a flip atómico SQL
  (`UPDATE notes SET pinned = NOT pinned WHERE id = :id`) — sin estado
  intermedio `setPinned(id, pinned)`; tests de DAO/repo/VM ajustados a doble
  toggle (el net de dos toggles == estado original).

- 2026-08-27 (ejecución 009): `testPreviewSafeDebugUnitTest` → **44 tests,
  0 fallos, 0 errores** (BUILD SUCCESSFUL); `testPreviewAdvancedDebugUnitTest`
  → **44/44**; `testPreviewFullDebugUnitTest` → **44/44**. `assembleRelease`
  3 variantes → OK. Añadidas 4 regresiones del ciclo de draft (BUG-006):
  resume tras recreación, restauración de la sesión tras proceso-muerte,
  carrera commit→abrir otra nota existente, y nota nueva tras back. Pre-fix
  ambas regresiones de carrera fallaban (duplicado, `expected:<2> but was:<3>`).
- 2026-08-27 (ejecución 003): `testPreviewSafeDebugUnitTest` → **29 tests,
  0 fallos, 0 errores** (BUILD SUCCESSFUL).
- 2026-08-27 (ejecución 004): `testPreviewSafeDebugUnitTest` → **30 tests,
  0 fallos, 0 errores** (BUILD SUCCESSFUL). Añadido test de UI para el editor.
- 2026-08-27 (ejecución 005): `testPreviewSafeDebugUnitTest` → **35 tests,
  0 fallos, 0 errores** (BUILD SUCCESSFUL). Añadida la búsqueda de notas
  (P2) + `@OptIn` por `flatMapLatest`. `assembleRelease` 3 variantes OK.
- 2026-08-27 (ejecución 006): `testPreviewSafeDebugUnitTest` → **36 tests,
  0 fallos, 0 errores** (BUILD SUCCESSFUL). Añadido test de regresión del
  título de una línea. `assembleRelease` 3 variantes OK.
- 2026-08-27 (ejecución 007): `testPreviewSafeDebugUnitTest` → **37 tests,
  0 fallos, 0 errores** (BUILD SUCCESSFUL). Añadido test de regresión del undo
  (BUG-004): `restore_whenOriginalIdReusedByAnotherNote_reinsertsUnderFreshId`
  (el caso de id libre lo cubre `deleteThenRestore_keepsSameIdAndContent`).
- 2026-08-27 (ejecución 008): `testPreviewSafeDebugUnitTest` → **40 tests,
  0 fallos, 0 errores** (BUILD SUCCESSFUL). Añadido `NotesListSearchInteractiveTest`
  (3 tests, BUG-005): la lupa abre el campo y filtra, toggle off sale de búsqueda,
  limpiar la query restaura la lista; + aserción de bounds (el campo no solapa la
  primera fila). `assembleRelease` 3 variantes OK.

## Tests recientemente agregados

- `RelativeDateTest` (RUN 014, nuevo, P3: fecha relativa en la lista —
  "Hoy"/"Ayer" contra el día natural local y fallback `DateFormat.MEDIUM` para
  notas más antiguas. Verifica `today_timestamp_isLabeledHoy`,
  `yesterday_timestamp_isLabeledAyer`, `olderTimestamp_fallsBackToMediumDate`
  y los dos límites exactos de medianoche de hoy/ayer).
- `NotesListAccessibilityTest` (RUN 011, nuevo, P3: accesibilidad de la lista —
  la fila expone `onClickLabel` "Abrir nota: <título>" (o "Abrir nota
  sin título"`; el pin describe "Fijada: <título>"` y la nota no fijada no
  expone pin. Verifica además que la acción sigue abriendo la nota.

- `NotepadViewModelTest` RUN 009 (+4, BUG-006, ciclo de draft):
  `beginDraftAgain_resumesLiveDraft_doesNotDuplicateNote` (recomposición de una
  nota nueva cuyo autosave ya creó fila → no duplica), `processDeath_restoresDraftId_avoidsDuplicateNote`
  (sesión de draft restaurada desde `SavedStateHandle`), `beginDraft_afterCommitLaunched_switchesDraftToNewNote`
  (carrera commit→abrir otra nota existente: el rebind prevalece, sin
  cross-contaminación) y `beginDraft_nullAfterCommitLaunched_startsFreshNewNote`
  (carrera commit→"+": la nota nueva se crea fresca, sin volver al draft anterior).
  Antes del fix, las de carrera fallaban con filas duplicadas.
- `NotesListSearchInteractiveTest` (RUN 008, nuevo): regresión UI de BUG-005 —
  el icono de búsqueda era un "no-op" porque `isSearching` se derivaba del texto
  de la query y el icono solo llamaba `onSearchQueryChange("")`. Verifica el
  flujo real: abrir desde la lupa con query vacía, filtrar tecleando, salir del
  modo y restauración de la lista al limpiar; además comprueba por `boundsInRoot`
  que el `SearchHeader` termina por encima de la primera fila de la lista
  (regresión de layout).
- `NotepadViewModelTest` avanzado con el ciclo de draft (autosave): debounce
  (nada se persiste antes de 800 ms), cancelación por tecleo rápido, no duplicado
  al hacer back tras autosave, guardia de nota nueva en blanco (autosave y commit),
  ghost autodestruida si el usuario vacía todo, save-after-delete (no resucitar),
  draft sobre nota existente que conserva `createdAt`.
- `NoteEditorBackSaveTest` (NUEVO, Robolectric+Compose): regresión UI del bug P0
  de pérdida de datos — el retroceso del sistema en el editor debe hacer `onCommit`
  de la edición en curso antes de navegar. Verifica back-save, autosave y navegación.
  RUN 006: `titleField_isSingleLine_dropsEmbeddedNewline` — pegar `\n` en el título
  no debe persistir títulos multilínea (dato aplanado).
- `NotepadViewModelTest` RUN 007 (+1, undo seguro BUG-004): reinsertar bajo id nuevo
  cuando otra nota reutilizó el id del borrado (no sobrescribir la nota viva).
- `NoteDaoTest`/`NotepadViewModelTest` (búsqueda, P2): filters por título y
  contenido case-insensitive (DAO, 3 tests), filtro por query en el ViewModel,
  restaurar todas al limpiar y query por defecto (ViewModel, 3 tests).

## Flakiness

- Ninguna observada.

## Cobertura relevante / huecos conocidos

- Primer test de UI (Compose) añadido: `NoteEditorBackSaveTest` (back del sistema
  en el editor). La navegación lista↔editor y el snackbar de deshacer se siguen
  validando principalmente de forma manual.
- Proceso-muerte del editor cubierto parcialmente desde RUN 009: la sesión de
  draft del ViewModel (`draftId`/`draftWasNew`) se restaura desde
  `SavedStateHandle` con test de regresión a nivel ViewModel. Sigue sin UI test
  Compose que ejercite `rememberSaveable` del editor + recreación real de
  actividad.

## Run 015 — nota de ejecución (**VERIFICADA en RUN 016**)

- Este sandbox carece de JDK 17 y Android SDK (no `java` en `PATH`, no `/usr/lib/jvm`,
  no `$ANDROID_HOME`); `./gradlew` falla con `exec: java: not found`. La suite completa
  (54/54 en las  3 variantes al cierre de RUN 014) **no se pudo re-ejecutar**.
- Añadidos sin ejecutar: `NoteEntityPreviewTest` (4 tests, puro JUnit/Kotlin:
 blank,
 2 líneas trim, cap length, single long line capped) y
  `NotepadViewModelTest.save_existingNoteWithUnchangedContent_doesNotRewriteUpdatedAt`
  (regresión del skip-write en `saveCurrent`). Ambos siguen el estilo de tests existentes;
validación estática OK (`firmas`, `patrones`, `git diff --check``); falta correrlos con
  JDK+SDK instalados.

- **Siguiente:** instalar el toolchain (JDK 17 + Android SDK 36) en un sandbox con
  red y correr `:app:testDebugUnitTest` + `assembleRelease` 3 variantes; o
  ejecutar la suite en CI.
