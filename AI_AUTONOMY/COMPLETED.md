# COMPLETED - Ordia (notepad minimalista)

- 2026-09-02 -- `fix(editor): save note on system back` (`55173c1`): el back del sistema guarda antes de salir del editor; evita perdida de texto al navegar atras.
- 2026-09-02 -- `feat(notes): confirm before deleting a note` (`e2b7971`): borrado requiere confirmacion (`AlertDialog` Eliminar/Cancelar.call evitar borrados accidentales.
- 2026-09-02 -- `fix(notes): skip saving empty new notes` (`4060244`): abrir nueva nota y salir sin escribir deja de ensuciar la lista con vacias.
- 2026-09-02 -- `test(notes): cover blank-note save behavior` (`227d94f`): `NotepadViewModelTest` con 4 casos (skip blank, skip whitespace, insert con title, preserve existing-cleared-blank.call
