# NEXT_TASKS — Ordía (rama openhands/autonomous-notes)

## P0 — Crítico
_(vacío — se conocen dos P0 menores ya corregidos; ver COMPLETED.md)_

## P1 — Alto impacto
1. **Eliminación sin deshacer**: `NotesListScreen` borra notas al instante vía menú "Eliminar", sin
   confirmación ni undo. Impacto: pérdida accidental permanente. Verificación: snackbar con "Deshacer"
   que restaura la nota + test de `NotepadViewModel` para `restore`.
2. **Sin navegación tipada**: `NotepadApp.kt` usa `selectedId: Long?` en `rememberSaveable`; no hay
   deep links ni Navigation component. Aceptable para MVP, pero impide testear navegación y restaurar
   editor tras process death con nota seleccionada. Evaluar si migrar a Navigation-Compose.

## P2 — Calidad de producto
1. **Icono deprecado**: `Icons.Outlined.InsertDriveFile` (deprecated) → usar
   `Icons.AutoMirrored.Outlined.InsertDriveFile`.
2. **Sin autosave**: solo se guarda al salir del editor; un kill del proceso con editor abierto pierde
   lo escrito desde el último estado `rememberSaveable` restaurable (en realidad survive process death
   mientras la actividad exista; el riesgo real es bajo, pero autosave debounced lo eliminaría por completo).
3. **Accesibilidad editor**: botón "Hecho" es `Text` clicable (touch target pequeño, sin rol); convertir
   a `TextButton`/`IconButton` adecuado.
4. **Código muerto**: `NoteRepository.create()` y `NoteEntity.preview()` no se usan.

## P3 — Opcional
1. Empty state de lista usa solo texto; podría reforzarse visualmente.
