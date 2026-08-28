# NEXT_TASKS — Ordía (bloc de notas)

> Cola priorizada para la automatización `openhands/autonomous-notes`.
> Solo problemas reales con evidencia; nada genérico.

## P0 — Críticos

_(vacío tras la ejecución 001: eliminación sin deshacer y notas vacías quedaron resueltos)_

## P1 — Alto impacto

_(vacío; RUN 009 resolvió BUG-006: integridad del ciclo de draft — duplicados y
cross-contaminación al cambiar de nota en ráfaga y pérdida del draft en
rotación/proceso-muerte — ver BUGS_FOUND.md. RUN 008 resolvió BUG-005.)_

## P2 — Calidad de producto

1. **`NoteEditorScreen`: título largo.** RESUELTO en RUN 006 — título de una línea
   (visual + datos): `singleLine=true` y aplanado de `\n` en `onValueChange` del
   título para no persistir títulos multilínea. Cobertura: test de UI de regresión.
2. **Confirmación antes de borrar nota fijada** u otros borrados de alto valor:
   evaluar si el snackbar de deshacer es suficiente (probablemente sí). RUN 007:
   el deshacer ya es seguro ante reutilización de ids (BUG-004): reinserta bajo id
   nuevo si el original fue reutilizado, nunca sobrescribe una nota viva.
3. **Búsqueda con acentos/tilde.** La búsqueda actual usa `LIKE` case-insensitive
   de Room que NO normaliza acentos (`café` no encuentra `cafe`). Si el usuario
   español lo pide, valorar normalizar (columna normalizada o coincidencias
   multi-plantilla). De momento queda como mejora opcional consciente.

## P3 — Mejoras opcionales
1. Fecha relativa en la lista ("hoy", "ayer") en vez de solo fecha media.
2. Accesibilidad de la lista: RESUELTO en RUN 011 — la fila anuncia su acción ("Abrir nota: <título>" / "Abrir nota sin título") y el pin describe la nota fijada ("Fijada: <título>"). Pendiente opcional: estados de foco visibles para navegación por teclado/TalkBack (focus indicators).

## 2026-08-27 — P2 adicional (tras ejecución 004; actualizado en RUN 012)

1. **Extender tests de UI del editor**: cubrir que el back tras autosave no duplica
   la nota (regresión del ciclo de draft)y que "Hecho"/flecha vuelve hacen commit
   igual que el back del sistema.
   _Estado RUN 012:_ el ciclo de draft queda cubierto a nivel ViewModel con 4
   regresiones (recreación, proceso-muerte, dos carreras commit→beginDraft;y el
   **UI test Compose de recreación/rotación** quedó añadido (`NoteEditorRecreationTest`,
   2 tests con `StateRestorationTester`, regresión de BUG-003: nota persistida en
   edición y nota nueva en curso). La acción "Hecho" de la toolbar también quedó
   cubierta (`NoteEditorBackSaveTest.toolbarDone_commitsAndNavigates`: hace commit y
   navega igual que el back del sistema). RESUELTO — 3 tests de UI nuevos.
   _Comprobar:_ `testPreviewSafeDebugUnitTest` → 49/49 verdes.
