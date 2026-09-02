# NEXT_TASKS - Ordia (notepad minimalista)

### P0

- Ninguna conocida.

### P1

- Persistencia | Autosave en el editor: hoy `NoteEditorScreen` guarda solo al salir (toolbar/back); si el proceso muere(raw crash, cierre del sistema) antes de salir, el texto se pierde. Para una app de notas es un riesgo real de perdida de datos. Comprobar: editar, matar la app, relanzar y recuperar el texto (o test de ViewModel con autosave.call

### P2

- Testing | UI Compose: no hay tests de flujo abrir->editar->back->persistir (la infra Robolectric esta presente; anadir `createComposeRule` donde aporte). Comprobar: `:app:testPreviewSafeDebugUnitTest` incluye los nuevos.call
- Mantenibilidad | Backlog stale: `BACKLOG.md` mezcla filas de la app pre-rebuild (tareas, habitos, update checker...) con el notepad actual; podar a solo items aplicables. Comprobar: BACKLOG solo contiene filas del notepad actual.call

### P3

- UX | Deshacer tras eliminar: tras confirmar borrado, ofrecer `Snackbar` con accion UNDO (requiere borrado suave/archivo). Comprobar: eliminar una nota y poder restaurarla desde la propia lista.call
- UX | Empty state accionable: ademas del FAB, permitir tocar el empty state para crear nota. Comprobar: tap en el empty state crea una nota nueva.call

## Nota

Solo anadir items con evidencia; mover a COMPLETED/BACKLOG con tests que lo demuestren.
