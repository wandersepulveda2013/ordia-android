# BACKLOG — Ordía 3.0 (bloc de notas)

> Inventario priorizado. Formato: PRIORIDAD | AREA | PROBLEMA | EVIDENCIA | ESTADO.
> Solo problemas reales con evidencia. El backlog jules-era previo no aplica al rebuild.

## Pendientes

| PRIORIDAD | AREA | PROBLEMA | EVIDENCIA | ESTADO |
|-----------|------|----------|-----------|--------|
| P1 | Navegacion | Sin navegacion tipada; el back del sistema en la lista cierra la app | NotepadApp con selectedId; BackHandler solo en editor | OPEN (evaluar) |
| P1 | Integridad | Doble guardado potencial: estatico en flecha/BackHandler y DisposableEffect dispose | revision codigo; repo.update es upsert, solo bump extra de updatedAt | OPEN (por confirmar) |
| P1 | Persistencia | Save solo al salir: crash/cierre forzado pierde la edicion | editor sin autosave/debounce (ver NEXT_TASKS P1) | OPEN |
| P2 | UX | Sin exportar/importar notas | ausente en UI | OPEN |
| P3 | UX | Cerrar nota al tocar fuera; pulido visual | OPEN |

## Completados (2026-08-26)

| PRIORIDAD | AREA | PROBLEMA | EVIDENCIA | ESTADO |
|-----------|------|----------|-----------|--------|
| P0 | Integridad | Back del sistema en editor cerraba la app y perdia la nota | BackHandler guarda; compila; BUGS_FOUND.md BUG-003 | FIXED |
| P0 | UX/Integridad | Eliminacion instantanea sin Deshacer | snackbar Deshacer + restore; NotepadViewModelTest.deleteThenRestore; BUG-001 | FIXED |
| P1 | UX/Integridad | Notas vacias fantasma al abrir/salir del editor | guard save(); NotepadViewModelTest.save_blankNewNote_isNotPersisted; BUG-002 | FIXED |
