# NEXT_TASKS — Ordía (bloc de notas)

> Cola priorizada para la automatización `openhands/autonomous-notes`.
> Solo problemas reales con evidencia; nada genérico.

## P0 — Críticos

_(vacío tras la ejecución 001: eliminación sin deshacer y notas vacías quedaron resueltos)_

## P1 — Alto impacto

_(vacío; RUN 022 resolvió BUG-009: la query de búsqueda activa se perdía en
proceso-muerte — ahora vive en `SavedStateHandle` como la sesión de draft
(ver BUGS_FOUND.md. RUN 009 resolvió BUG-006: integridad del ciclo de draft —
duplicados y cross-contaminación al cambiar de nota en ráfaga y pérdida del
draft en rotación/proceso-muerte. RUN 008 resolvió BUG-005.)_

## P2 — Calidad de producto

0. **Confirmación de borrado desde el menú ⋮: RESTAURADA en RUN 021.**
   El merge `8a82c78` había reintroducido el flujo directo borrar+undo sin el
   diálogo (el `AlertDialog` de confirmación quedó huérfano: `pendingDelete?.let`
   seguía en el árbol pero la variable ya no se declaraba). Restaurado en
   `bdd1986` (menú → diálogo → confirmar borra + ofrece Undo; cancelar descarta))
   con strings externalizadas. Regresión: `NotesListDeleteConfirmTest` (2 tests).
   Nota: el ítem 2 de abajo (decisión RUN 013: undo sobre confirmación) sigue
   siendo la política del linaje; al merge-la restauración del diálogo preexistente
   del `e2b7971` (pre-merge) no revierte esa decisión —se recupera el flujo
   que el merge había roto, sin cambiar el diseño acordado y los dos flujos quedan
   alineados (diálogo de confirmación + undo tras confirmar).

2. **Escape de comodines en búsqueda:** **RESUELTO en RUN 018** — los
   caracteres `%`/`_` ya no actúan como comodines SQL en la búsqueda: la query
   se escapa (`\`->`\\`,`%`->`\%`,`_`->`\_`) antes de pasar al DAO, los
   `LIKE` usan `ESCAPE` y los comodines solos encuentran el literal (cubierto
   por `observeSearch_wildcardsAreTreatedLiterally`, ver BUG-007).

3. **`NoteEditorScreen`: título largo.** RESUELTO en RUN 006 — título de una línea
   (visual + datos): `singleLine=true` y aplanado de `\n` en `onValueChange` del
   título para no persistir títulos multilínea. Cobertura: test de UI de regresión.
4. **Confirmación antes de borrar nota fijada** u otros borrados de alto valor:
   RESUELTO — el snackbar de deshacer es suficiente y NO se añade un diálogo de
   confirmación previa. RUN 013: evaluación concluyente con base en el diseño del
   linaje (RUN 007, BUG-004: el deshacer reinserta bajo id nuevo si el original
   fue reutilizado — nunca sobrescribe una nota viva) y en una implementación
   paralela descartada que añadía un `AlertDialog` previo: doble protección =
   fricción (dos pasos para la acción más común) sin valor real frente al undo,
   y el patrón Material recomienda undo sobre confirmaciones destructivas. El
   `togglePinned` actual es atómico SQL (RUN 010), así que el riesgo de la
   fijada es el mismo que cualquier nota — cubierto por el undo.
5. **Búsqueda con acentos/tilde — SOLO bajo petición explícita del usuario.**
   La búsqueda actual usa `LIKE` case-insensitive de Room que NO normaliza acentos
   (`café` no encuentra `cafe`). Resuelto el núcleo: búsqueda por título/contenido
   en RUN 013. El índice accent-insensitive queda **opt-in**, solo si el usuario
   lo pide explícitamente; mientras tanto, permanecerá documentado, no implementado.

## P3 — Mejoras opcionales
1. Fecha relativa en la lista: **RESUELTO en RUN 014** — `RelativeDate.kt`
   (`relativeLabel(timestampMs, now = Date())`) etiqueta "Hoy" / "Ayer"
   (límite = día natural local, no ventana de 24 h) con fallback a fecha
   MEDIUM para días anteriores. La lista usa `relativeLabel(note.updatedAt)`
   en vez de `DateFormat.MEDIUM`. Cobertura: `RelativeDateTest` (5 tests:
   hoy, ayer, fallback MEDIUM, y los dos límites exactos de medianoche).
2. Accesibilidad de la lista: RESUELTO en RUN 011 — la fila anuncia su acción
   ("Abrir nota: <título>" / "Abrir nota sin título") y el pin describe la nota
   fijada ("Fijada: <título>"). **Focus indicators RESUELTOS en RUN 029**: cada
   fila es un nodo focuseable independiente (`Modifier.focusable()` + fondo highlight
   al recibir foco, etiqueta estable `note_row_<id>`, regresión Compose/Robolectric
   `NotesListFocusTest`). La lista y el editor tienen ahora foco visible y
   navegable por teclado/TalkBack.


## 2026-08-27 — P2 adicional (tras ejecución 004; actualizado en RUN 012)

1. **Extender tests de UI del editor**: cubrir que el back tras autosave no duplica
   la nota (regresión del ciclo de draft)y que "Hecho"/flecha vuelve hacen commit
   igual que el back del sistema.
   _Estado RUN 012:_ el ciclo de draft queda cubierto a nivel ViewModel con 4
   regresiones (recreación, proceso-muerte, dos carreras commit→beginDraft;y el
   **UI test Compose de recreación/rotación** quedó añadido (`NoteEditorRecreationTest`,
   2 tests con `StateRestorationTester`, regresión de BUG-003: nota persistida en
   edición y nota nueva en curso). La acción "Hecho" de la toolbar también quedó
   cubierta (`NoteEditorBackSaveTest.toolbarDone_commitsAndNavigates`: hace commit y
   navega igual que el back del sistema). RESUELTO — 3 tests de UI nuevos.
   _Comprobar:_ `testPreviewSafeDebugUnitTest` → 49/49 verdes.
   _Estado RUN 031:_ **cerrado por completo** — +1 regresión UI
   `recreation_afterAutosaveCreatedRow_resumesSameDraft_doesNotDuplicate`
   (el caso: la nota nueva ya tiene fila por autosave + recreación: ni
   se duplica ni se pierde texto;  ‌74/74 en las 3 variantes). Este ítem
   queda **RESUELTO** (3+1 tests de UI nuevos acumulados).
3. **Migrar strings hardcodeados a `strings.xml`**: **RESUELTO en RUN 016** —
   todos los strings visibles de `NoteEditorScreen` y `NotesListScreen` (títulos,
   placeholders, contentDescriptions, snackbar, menús, estados vacíos, rutas de
   accesibilidad) movidos a `res/values/strings.xml` (23 strings) y referenciados
   via `stringResource(...)`; ningún hardcode resta en las dos pantallas (`grep` limpio).
   Verificado: build + `testPreviewSafeDebugUnitTest` verdes. Pendiente opcional: localizaciones.
4. **Migrar tests de UI al API v2 de Compose test rule**: **RESUELTO en RUN 017** —
   los 4 archivos de tests de UI (`NoteEditorBackSaveTest`, `NoteEditorRecreationTest`,
   `NotesListAccessibilityTest`, `NotesListSearchInteractiveTest`) importan ahora
   `androidx.compose.ui.test.junit4.v2.createAndroidComposeRule` (API canónica actual,
   StandardTestDispatcher en vez de UnconfinedTestDispatcher). Sin cambios de comportamiento:
   las 3 variantes verdes **59/59** tras la migración (ver TEST_STATUS.md).
5. **Ejecución 015 resuelto (registro):** skip-write en `saveCurrent` y preview
   de lista mejorado (`NoteEntity.preview`) — ver `COMPLETED.md` RUN 015; tests
   **verificados en RUN 016**: 59/59 en las tres variantes (ver TEST_STATUS.md).
   **RUN 032:** la preview ahora es segura con emoji en el corte: `NoteEntity.preview`
   y el título del diálogo de borrado usan `safeTakeChars` (no parten pares sustitutos
   UTF-16;el emoji que no cabe se descarta entero,sín `\ufffd`;+3 tests
   `NoteEntityPreviewTest`;vérificado **77/77 en las 3 variantes**).

6. **Commit sin cambios ya no reescribe `updatedAt` (RUN 026):** abrir una nota
   y pulsar Hecho/back/sin editar ya no ejecuta un `repo.update` innecesario —
   meramente abrir/cerrar ya no reordena la lista ni escribe en disco. El guard
   de no-cambio de `doPersistCommit` ahora espeja el de `saveCurrent` (RUN 015).
   Regresión: `NotepadViewModelTest.commitDraft_existingNoteUnchanged_doesNotRewriteUpdatedAt`.
8. ~~Editor: surfacer el fallo de persistencia también en la pantalla de edición~~
   **FIXED/VERIFIED (RUN 028):** el snackbar «No se pudo completar la operación»
   se muestra ahora en AMBAS pantallas (lista y editor): el `SnackbarHost`
   y el colector de `viewModel.persistenceError` se subieron a `NotepadApp`
   (raíz, `Box(Modifier.fillMaxSize())` + `SnackbarHost` inferior); se
   eliminó la colección local antigua de `NotesListScreen` (`persistenceError`
   param, `LaunchedEffect`, imports `Flow`/`emptyFlow`). Verificación: un
   autosave fallido emite `persistenceError` y la app raíz muestra el snackbar
   sobre cualquier pantalla, sin crashear y conservando el texto en curso.
7. **Auditar iconos deprecados restantes en `src/main` (RUN 018):** los iconos que
   conservan direccionalidad contextual (búsqueda, pin, menú ⋮, adición) no
   requieren AutoMirrored aún; la sesión histórica de auto-mirroring en RUN_LOG.md
   tocó archivos del linaje pre-rebuild que ya no existen en `src/main` (`AppComponents.kt`,
   `ProjectsScreen.kt`, etc.). Punto de verificación: `grep -rn "Icons" app/src/main`
   lista solo `Outlined.Search/Add/Close/PushPin/MoreVert` + `AutoMirrored.Outlined.ArrowBack`.
   No hay acción pendiente salvo cuando la dirección contextual de Search/Close lo requiera en RTL.
