# CURRENT_STATE — Estado actual del producto

## Resumen general

Rebuild completo de la app (commit ceb1ff3, main). Producto: bloc de notas minimalista
(Room + Jetpack Compose, MVVM). Funciones reales: listar, fijar (pin), editar, eliminar notas.
Los docs jules-era previos en AI_AUTONOMY/ (MISSION, DECISIONS, RUN_LOG, SUPERVISION,
CODE_OF_CONDUCT) son historicos del rebuild anterior y ya no describen el codigo vigente.

## Arquitectura importante

- data/: NoteEntity (Room), NoteDao, NoteDatabase, NoteRepository.
- ui/: NotepadApp (alterna lista/editor via selectedId + rememberSaveable), NotepadViewModel
  (save/togglePin/delete/clearAll; guard de nota en blanco), screens NotesListScreen y
  NoteEditorScreen (BackHandler guarda al salir).
- Sin navegacion tipada; el back del sistema dentro del editor se intercepta y guarda.

## Areas modificadas esta sesion (2026-08-26)

- NoteEditorScreen.kt: BackHandler (P0: antes el back del sistema cerraba la app y perdia notas).
- NotepadViewModel.kt: guard de nota en blanco + existingId explicito (evita notas fantasma).
- NotepadViewModelTest.kt: 5 tests nuevos.
- AI_AUTONOMY/: memoria completa segun protocolo openhands/autonomous-notes.

## Riesgos abiertos (detalle en NEXT_TASKS.md)

- P1: eliminacion instantanea sin deshacer.
- P1: sin navegacion tipada (conmutacion por estado local).
- P1: posible doble guardado por DisposableEffect + flecha (por confirmar).

## Estado de build/tests (2026-08-26)

- compilePreview{Safe,Advanced,Full}DebugKotlin -> BUILD SUCCESSFUL.
- testPreviewSafeDebugUnitTest -> 20/20 PASS (8 DAO + 7 Repo + 5 ViewModel).

## Bloqueos

- Ninguno activo. (Advertencia kapt language-version 2.0 -> fallback 1.9: cosmetica.)
