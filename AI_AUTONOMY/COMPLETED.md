# COMPLETED — Ordía (bloc de notas)

> Solo mejoras importantes completadas por la automatización
> `openhands/autonomous-notes`. Microcambios triviales no se registran.

## 2026-08-26 — Ejecución 001 (baseline + integridad de datos)

- **Baseline del producto rebuild** (bloc de notas minimalista, commit `ceb1ff3`):
  compilación `previewSafeDebug` OK; 15 tests unitarios verdes (8 DAO + 7 repo).
- **Undo de eliminación de notas** (P0, BUG-001): snackbar con "Deshacer" en la
  lista; `NotepadViewModel.restore` reinserta con el mismo id.
- **No persistir notas nuevas vacías** (P1, BUG-002): `save` ignora creaciones
  sin título ni contenido.
- **`NotepadViewModelTest`** nuevo: 5 tests (crear, actualizar conservando
  `createdAt`, ignorar nota nueva vacía, borrar+restaurar con mismo id,
  fijar/desfijar).

## 2026-08-26 — Ejecución 002 (back del sistema + integración de ejecuciones)

- **Back del sistema guarda la nota** (P0, BUG-003): `BackHandler` en
  `NoteEditorScreen` ejecuta el guardado antes de volver a la lista; antes
  cerraba la app y se perdía la edición.
- **Merge de trabajos paralelos** en `openhands/autonomous-notes` (ejecuciones
  001 y 002): conservados undo de borrado, guardia de nota vacía y BackHandler.
- **`NotepadViewModelTest`** ampliado a 7 tests (+ título en blanco con
  contenido, + no recrear nota inexistente por `existingId`).
