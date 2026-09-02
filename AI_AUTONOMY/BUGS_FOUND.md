# BUGS_FOUND - Ordia (notepad minimalista)

| Bug | Impacto | Reproduccion | Causa | Estado |
|------|---------|-------------|--------|--------|
| Back del sistema en editor descartaba el texto editado | perdida de datos al navegar atras | editar nota, Android back sin usar flecha/Hecho | `NoteEditorScreen` solo guardaba desde toolbar; sin `BackHandler` | FIXED -- `55173c1` (`BackHandler` + `exitSaving()`) |
| Borrado inmediato sin confirmacion | borrado accidental irreversible | menu, Eliminar borraba al instante | `NoteRow` llamaba `onDeleteNote` directo | FIXED -- `e2b7971` (`AlertDialog` de confirmacion.call) |
| Notas nuevas vacias en la lista | ruido/clutter de notas vacias | nueva nota, salir sin escribir | `save()` insertaba siempre | FIXED -- `4060244` (skip si `existingId == null` y titulo+contenido vacios.call; test `NotepadViewModelTest`) |
