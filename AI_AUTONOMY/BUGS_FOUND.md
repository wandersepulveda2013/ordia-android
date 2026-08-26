# BUGS_FOUND — Registro de bugs

## RESUELTO — Back del sistema pierde la nota en edición (P0)
- **Impacto**: pérdida total de la nota escrita si el usuario usaba el back del sistema (gesto/botón)
  en el editor; la app se cerraba igual que desde la lista.
- **Reproducción**: abrir editor (nueva o existente), escribir, pulsar back del sistema → app se
  cierra, nada persistido.
- **Causa**: `NotepadApp` alterna composables con estado local; ningún `BackHandler` interceptaba el
  back del sistema en el editor, así que `onBackPressed` terminaba la activity.
- **Estado**: FIXED en `openhands/autonomous-notes` (`BackHandler` guarda y vuelve a la lista).
  Cobertura: verificación de compilación; el flujo es idéntico al de la flecha de la barra.

## RESUELTO — Notas vacías fantasma (P1/UX)
- **Impacto**: abrir el editor con "+" y salir creaba una nota vacía en la lista.
- **Causa**: `save()` insertaba siempre una nueva nota, sin validar contenido.
- **Estado**: FIXED — guard `existingId == null && title/content en blanco → no persiste`.
  Test: `save_blankNewNote_doesNotPersistGhostNote`.
