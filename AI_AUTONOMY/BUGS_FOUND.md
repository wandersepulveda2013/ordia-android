# BUGS_FOUND — Ordía (bloc de notas)

> Formato: bug · impacto · reproducción · causa · estado · commit.

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
