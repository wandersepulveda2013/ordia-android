1. **Mejorar el sistema de eventos de UI para soportar acciones**:
   - Modificar `UiEvent.Message` en `OrdiaViewModel.kt` para aceptar un texto de acción opcional (`actionLabel: String? = null`) y un callback de acción (`onAction: (() -> Unit)? = null`).
2. **Implementar Snackbar con Acción en la UI**:
   - Modificar `OrdiaRoot.kt` para usar `snackbarHostState.showSnackbar(message = event.text, actionLabel = event.actionLabel)`.
   - Comprobar si el resultado es `SnackbarResult.ActionPerformed` y, en ese caso, invocar `event.onAction?.invoke()`.
3. **Añadir la opción "Deshacer" al archivar/borrar tareas, proyectos, notas y hábitos**:
   - En `OrdiaViewModel.kt`, cuando se llama a `deleteTask`, pasar "Deshacer" como `actionLabel` y un lambda que llame a `restoreArchived("task", task.id)`.
   - Hacer lo mismo para `deleteProject`, `deleteNote`, y `deleteHabit`.
4. **Verificar el correcto funcionamiento del Undo**:
   - Comprobar la compilación y que los tests siguen pasando.
5. **Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.**
6. **Enviar los cambios mediante la herramienta Submit**.
