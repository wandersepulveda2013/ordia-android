# CURRENT_STATE - Ordia (notepad minimalista)

> Actualizar al final de cada sesion autonoma.

## Estado

- Fecha/hora (UTC): 2026-09-02
- Branch de trabajo: `openhands/autonomous-notes` (base: `ceb1ff3` "feat: rebuild Ordia as a minimalist notepad")
- App: notepad local-first minimalista Compose + Room; sin permisos especiales; tema papel (blanco/negro).

## Arquitectura

- data: `NoteEntity`, `NoteDao`, `NoteDatabase`, `NoteRepository` (Room/KAPT).
- ui: `NotepadApp`, `NotesListScreen`, `NoteEditorScreen`, `NotepadViewModel`, `NotepadViewModelFactory`
- Flavors: `previewSafe` / `previewFull` / `previewAdvanced`

## Ultimas mejoras (2026-09-02)

- Back del sistema en editor guarda y navega (`BackHandler` + `exitSaving()`)
- Borrado de nota confirmado con `AlertDialog` (Eliminar/Cancelar.call
- Notas nuevas vacias no se guardan (`save()` skip si `existingId == null` y titulo+contenido vacios`).
- Tests nuevos: `NotepadViewModelTest` (blank-skip + existing-blank-preserve + insert.call

## Estado de tests

- `test{PreviewSafe,PreviewFull,PreviewAdvanced}DebugUnitTest` -> BUILD SUCCESSFUL (19 tests por flavor,0 fallos.call
- `assembleRelease` -> BUILD SUCCESSFUL (3 APKs.call

## Riesgos abiertos

- Verificacion manual en dispositivo ADB pendiente (no hay hardware conectado; UX/instalacion no probadas fisicamente.call
- El editor guarda solo al salir; un cierre/terminacion del proceso antes de salir pierde el texto (candidato P1, ver `NEXT_TASKS.md`.call
- `BACKLOG.md` contiene filas historicas de la app pre-rebuild que no aplican al notepad actual; candidatas a poda (P2.call

## Siguiente tarea

Ver `NEXT_TASKS.md`.
