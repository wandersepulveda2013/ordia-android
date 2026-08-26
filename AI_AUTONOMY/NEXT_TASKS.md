# NEXT_TASKS — Ordía (bloc de notas)

> Cola priorizada para la automatización `openhands/autonomous-notes`.
> Solo problemas reales con evidencia; nada genérico.

## P0 — Críticos

_(vacío tras la ejecución 001: eliminación sin deshacer y notas vacías quedaron resueltos)_

## P1 — Alto impacto

1. **Edición sin autosave.** El editor solo persiste al pulsar atrás/"Hecho". Si el
   proceso muere o la actividad se destruye sin saved-state, el texto se pierde.
   _Comprobar:_ teclear texto, `onStop` + destrucción, reabrir → el contenido debe
   estar en la base de datos. Solución razonable: guardado debounced en el
   ViewModel (evitar crear nota hasta que haya contenido; cuidar la carrera
   save-after-delete).

## P2 — Calidad de producto

1. **Sin búsqueda.** Con muchas notas no hay forma de localizar una. Un campo de
   búsqueda que filtre por título/contenido (consulta SQL `LIKE` o filtrado en
   memoria) mejoraría mucho la utilidad. _Comprobar:_ test de repositorio/DAO.
2. **`NoteEditorScreen`: título largo.** `TextField` de título de una línea no
   está limitado; cosmetico. Revisar `singleLine`/`maxLines` deseado.
3. **Confirmación antes de borrar nota fijada** u otros borrados de alto valor:
   evaluar si el snackbar de deshacer es suficiente (probablemente sí).

## P3 — Mejoras opcionales

1. Fecha relativa en la lista ("hoy", "ayer") en vez de solo fecha media.
2. Accesibilidad: `contentDescription` más ricos (incluir título de la nota en la
   fila), estados de focus.
