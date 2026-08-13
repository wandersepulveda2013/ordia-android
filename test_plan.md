1. **Mejorar Snackbar de eliminación ("Undo")**:
   - Implementar un mecanismo en `OrdiaViewModel` para posponer el borrado real (o mostrar el Snackbar con opción de deshacer). En realidad las tareas se "archivan" cuando se eliminan de la lista normal.
   - Analizar si el borrado de tareas (`deleteTask` de `OrdiaViewModel`) actualmente es un archivado o borrado permanente.
   - En `OrdiaRoot`, soportar `SnackbarResult.ActionPerformed` para disparar el "Deshacer".
