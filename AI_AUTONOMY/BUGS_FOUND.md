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
- **Test:** validado por compilación y comportamiento; pendiente test de UI de
  Compose (hueco conocido en `TEST_STATUS.md`).


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
- **Estado:** FIXED (pendiente de confirmar commit).
- **Resuelto por:** eliminar el `LaunchedEffect` de reseed en `NoteEditorScreen`.
  Pendiente: añadir un UI test Compose (androidTest) que verifique que la rotación
  preserva el texto sin persistir.
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
